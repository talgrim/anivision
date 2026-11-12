"""Export the colour pipeline as data for the Android app (Phase 5).

Everything here is a pure function of the prototype's own modules, so the exported matrix,
acuity recipe and golden vectors cannot drift from the code. ``scripts/export_constants.py``
writes the result to ``export/``.

Matrix conventions (the usual source of Android bugs):

* The matrix acts on **linear** sRGB column vectors: ``out = M @ rgb``. Python and the JSON file
  store it **row-major** (``M[row][col]``).
* GLSL ``mat3(...)`` takes its nine arguments **column-major**, and ``M * v`` is the same product.
* ``glUniformMatrix3fv(..., transpose = false, ...)`` also expects **column-major** data.
* ``android.graphics.ColorMatrix`` is 4x5 **row-major** and works on **gamma-encoded** 0-255
  values, so it is only an approximation (fine for the MVP, wrong for the final pipeline).
"""

from __future__ import annotations

import numpy as np

from .acuity import (
    DEFAULT_EXAGGERATION,
    DEFAULT_K,
    HUMAN_CUTOFF_CPD,
    MAX_EXAGGERATION,
    MTF_AT_CUTOFF,
    AcuityParams,
    sigma_px,
)
from .colorspace import linear_to_srgb, srgb_to_linear
from .derive import DEFAULT_ANCHOR_NM, derive
from .gamut import LUMA
from .lowlight import STRONGEST_GAMMA, SUGGESTED_GAMMA
from .profiles import DOG
from .simulate import GAMUT_TOLERANCE, apply_matrix, simulate
from .spectra import DEFAULT_PRIMARY_CENTERS, DEFAULT_PRIMARY_SIGMA
from .variants import get_variant

SCHEMA_VERSION = 1
DEFAULT_VARIANT = "v2"
EXPORT_VARIANTS = ("v2", "v0")  # default first; the Android code reads the matrix as data
GOLDEN_SEED = 20261112
RANDOM_GOLDEN_COUNT = 40
SIGMA_EXAMPLE_EDGES = (640, 1080, 1440, 1920)

CAVEAT = (
    "Approximation of what a dog's cones capture, not of what a dog subjectively experiences. "
    "Assumes sRGB input and a modelled display (Gaussian primaries); see src/prototype/README.md."
)

VARIANT_DESCRIPTIONS = {
    "v2": "Dog-specific projection derived from Govardovskii cone sensitivities (S 429 / L 555 nm), "
    "475 nm anchor. Shipped default (NOTES.md, Phase 3 decision).",
    "v0": "Vienot, Brettel & Mollon (1999) deuteranopia matrix for linear sRGB. Fallback.",
}

# Skin tones are common reference values (not measured), spread from light to dark.
_NAMED_GOLDEN: tuple[tuple[str, tuple[int, int, int]], ...] = (
    ("black", (0, 0, 0)),
    ("white", (255, 255, 255)),
    ("red", (255, 0, 0)),
    ("green", (0, 255, 0)),
    ("blue", (0, 0, 255)),
    ("cyan", (0, 255, 255)),
    ("magenta", (255, 0, 255)),
    ("yellow", (255, 255, 0)),
    *((f"gray-{v}", (v, v, v)) for v in (16, 32, 64, 96, 128, 160, 192, 224)),
    ("skin-1", (255, 224, 196)),
    ("skin-2", (255, 205, 148)),
    ("skin-3", (241, 194, 125)),
    ("skin-4", (224, 172, 105)),
    ("skin-5", (198, 134, 66)),
    ("skin-6", (141, 85, 36)),
    ("skin-7", (92, 54, 26)),
    ("orange", (255, 165, 0)),
    ("brown", (139, 69, 19)),
    ("olive", (128, 128, 0)),
    ("grass", (86, 125, 70)),
    ("dark-green", (0, 100, 0)),
    ("sky", (135, 206, 235)),
    ("navy", (0, 0, 128)),
    ("purple", (128, 0, 128)),
    ("pink", (255, 192, 203)),
    ("tennis-ball", (223, 255, 79)),
)


def _row_major(m: np.ndarray) -> list[float]:
    return [float(v) for v in np.asarray(m, dtype=np.float64).reshape(-1)]


def _column_major(m: np.ndarray) -> list[float]:
    return [float(v) for v in np.asarray(m, dtype=np.float64).T.reshape(-1)]


def _color_matrix_4x5(m: np.ndarray) -> list[float]:
    """android.graphics.ColorMatrix layout: 4 rows (R, G, B, A) of 5 values, row-major."""
    rows = [[*map(float, m[i]), 0.0, 0.0] for i in range(3)]
    rows.append([0.0, 0.0, 0.0, 1.0, 0.0])
    return [v for row in rows for v in row]


def _literal(v: float) -> str:
    """Float32-exact literal: 9 significant digits, always with a '.' or exponent."""
    s = f"{v:.9g}"
    return s if any(ch in s for ch in ".e") else s + ".0"


def _fmt(values: list[float]) -> str:
    """Kotlin literals (``f`` suffix)."""
    return ", ".join(_literal(v) + "f" for v in values)


def _glsl_fmt(values: list[float]) -> str:
    return ", ".join(_literal(v) for v in values)


def matrices() -> dict[str, np.ndarray]:
    return {name: get_variant(name) for name in EXPORT_VARIANTS}


# --------------------------------------------------------------------------- matrix.json


def matrix_document(generator: dict) -> dict:
    """The ``export/matrix.json`` contents. ``generator`` records the git commit and dirty flag."""
    mats = matrices()
    d = derive()
    variants = {
        name: {"description": VARIANT_DESCRIPTIONS[name], "matrix": mats[name].tolist()}
        for name in EXPORT_VARIANTS
    }
    variants["v2"]["derivation"] = {
        "s_peak_nm": DOG.s_peak_nm,
        "l_peak_nm": DOG.l_peak_nm,
        "anchor_nm": DEFAULT_ANCHOR_NM,
        "display_primary_centers_nm": list(DEFAULT_PRIMARY_CENTERS),
        "display_primary_sigma_nm": DEFAULT_PRIMARY_SIGMA,
        "neutral_point_nm": d.neutral_point_nm,
    }
    return {
        "schema_version": SCHEMA_VERSION,
        "caveat": CAVEAT,
        "generator": generator,
        "convention": {
            "space": "linear sRGB (D65), matrix acts on column vectors: out = M @ rgb",
            "json_layout": "row-major, matrix[row][col]",
            "glsl_mat3_layout": "column-major constructor arguments",
            "gl_uniform_matrix3fv": "column-major data with transpose = false",
        },
        "pipeline": [
            "decode sRGB -> linear",
            "acuity blur (optional, linear light)",
            "shadow lift (optional)",
            "matrix",
            "gamut fit: clamp each channel to [0, 1]",
            "encode linear -> sRGB",
        ],
        "gamut": {"policy": "clip", "tolerance": GAMUT_TOLERANCE},
        "profiles": {
            "dog": {
                "default_variant": DEFAULT_VARIANT,
                "variants": variants,
            }
        },
        "acuity": acuity_document(),
        "shadow_lift": shadow_lift_document(),
    }


def acuity_document() -> dict:
    defaults = AcuityParams()
    return {
        "enabled_by_default": False,  # the app shows a switch, off by default
        "blur_space": "linear light",
        "edges": "mirror",
        "kernel": "Gaussian, isotropic; truncate at 3 sigma or more",
        "formula": {
            "cutoff_cpd": "human_cutoff_cpd / acuity_ratio",
            "sigma_px": "k * (acuity_ratio / 60 / 2) * (long_edge_px / hfov_deg) * exaggeration",
            "k": "2 * sqrt(2 * ln(1 / mtf_at_cutoff)) / pi",
        },
        "human_cutoff_cpd": HUMAN_CUTOFF_CPD,
        "mtf_at_cutoff": MTF_AT_CUTOFF,
        "k": DEFAULT_K,
        "acuity_ratio": defaults.acuity_ratio,
        "hfov_deg": defaults.hfov_deg,
        "hfov_applies_to": "the long edge of the frame",
        "default_exaggeration": DEFAULT_EXAGGERATION,
        "max_exaggeration": MAX_EXAGGERATION,
        "sigma_px_examples": [
            {
                "long_edge_px": edge,
                "exaggeration": ex,
                "sigma_px": sigma_px(edge, AcuityParams(exaggeration=ex)),
            }
            for edge in SIGMA_EXAMPLE_EDGES
            for ex in (1.0, DEFAULT_EXAGGERATION * 4.0, MAX_EXAGGERATION)
        ],
        "linear_light_check": {
            "note": "a 50/50 black-white checker blurred to a flat value encodes to this sRGB value",
            "srgb_8bit": int(np.rint(linear_to_srgb(0.5) * 255.0)),
        },
    }


def shadow_lift_document() -> dict:
    return {
        "enabled_by_default": False,
        "artistic": True,
        "formula": "rgb * Y^(gamma - 1) where Y = clamp(dot(luma, rgb), 1e-6, 1); unchanged when Y >= 1",
        "luma": [float(v) for v in LUMA],
        "off_gamma": 1.0,
        "suggested_gamma": SUGGESTED_GAMMA,
        "strongest_gamma": STRONGEST_GAMMA,
    }


# --------------------------------------------------------------------------- snippets


def _header(comment: str, generator: dict) -> str:
    commit = generator.get("git_commit") or "unknown"
    dirty = " (working tree had uncommitted changes)" if generator.get("git_dirty") else ""
    return (
        f"{comment} Generated by scripts/export_constants.py at commit {commit}{dirty}. Do not edit.\n"
        f"{comment} {CAVEAT}\n"
    )


def kotlin_snippet(generator: dict) -> str:
    out = [_header("//", generator)]
    for name, m in matrices().items():
        const = f"DOG_{name.upper()}"
        out.append(
            f"/** {VARIANT_DESCRIPTIONS[name]}\n"
            f" *  3x3, ROW-major, acts on LINEAR sRGB column vectors: out = M * rgb. */\n"
            f"val {const}_ROW_MAJOR = floatArrayOf({_fmt(_row_major(m))})\n\n"
            f"/** Same matrix, COLUMN-major: glUniformMatrix3fv(location, 1, false, {const}_COLUMN_MAJOR, 0). */\n"
            f"val {const}_COLUMN_MAJOR = floatArrayOf({_fmt(_column_major(m))})\n\n"
            f"/** android.graphics.ColorMatrix (4x5, row-major). Operates on GAMMA-ENCODED 0-255 values, so\n"
            f" *  it only approximates the linear-light pipeline: MVP use only. */\n"
            f"val {const}_COLOR_MATRIX_4X5 = floatArrayOf({_fmt(_color_matrix_4x5(m))})\n"
        )
    a, s = acuity_document(), shadow_lift_document()
    out.append(
        "// Acuity blur (linear light, Gaussian). Off by default in the app.\n"
        f"const val ACUITY_K = {a['k']:.9g}f\n"
        f"const val ACUITY_RATIO = {a['acuity_ratio']:.9g}f\n"
        f"const val ACUITY_HFOV_DEG = {a['hfov_deg']:.9g}f  // along the frame's long edge\n"
        f"const val ACUITY_DEFAULT_EXAGGERATION = {a['default_exaggeration']:.9g}f\n"
        f"const val ACUITY_MAX_EXAGGERATION = {a['max_exaggeration']:.9g}f\n\n"
        "/** Gaussian sigma in pixels for a frame whose long edge is [longEdgePx] pixels. */\n"
        "fun acuitySigmaPx(longEdgePx: Float, exaggeration: Float = ACUITY_DEFAULT_EXAGGERATION): Float =\n"
        "    ACUITY_K * (ACUITY_RATIO / 60f / 2f) * (longEdgePx / ACUITY_HFOV_DEG) * exaggeration\n\n"
        "// Shadow lift (artistic, off by default): rgb *= Y^(gamma - 1), Y = luma(rgb) clamped to [1e-6, 1].\n"
        f"const val SHADOW_LIFT_OFF_GAMMA = {s['off_gamma']:.9g}f\n"
        f"const val SHADOW_LIFT_SUGGESTED_GAMMA = {s['suggested_gamma']:.9g}f\n"
        f"const val SHADOW_LIFT_STRONGEST_GAMMA = {s['strongest_gamma']:.9g}f\n"
    )
    return "\n".join(out)


def glsl_snippet(generator: dict) -> str:
    out = [_header("//", generator)]
    out.append(
        "// GLSL mat3(...) takes its arguments COLUMN-major. Each block shows the row-major matrix.\n"
        "// Use as: vec3 outLinear = DOG_V2 * inLinear;   (declare `precision highp float;` in the shader)\n"
    )
    for name, m in matrices().items():
        rows = "\n".join("//   [" + "  ".join(f"{v: .7f}" for v in row) + " ]" for row in m)
        cols = _column_major(m)
        const = f"DOG_{name.upper()}"
        out.append(
            f"// {name}: {VARIANT_DESCRIPTIONS[name]}\n{rows}\n"
            f"const mat3 {const} = mat3(\n"
            f"    {_glsl_fmt(cols[0:3])},  // column 0\n"
            f"    {_glsl_fmt(cols[3:6])},  // column 1\n"
            f"    {_glsl_fmt(cols[6:9])}); // column 2\n"
        )
    out.append(
        "vec3 srgbToLinear(vec3 c) {\n"
        "    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));\n"
        "}\n\n"
        "vec3 linearToSrgb(vec3 c) {\n"
        "    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));\n"
        "}\n\n"
        "// Colour step only (no blur, no shadow lift). Gamut policy: clamp each channel.\n"
        "vec3 dogView(vec3 srgb, mat3 m) {\n"
        "    vec3 linear = clamp(m * srgbToLinear(srgb), 0.0, 1.0);\n"
        "    return linearToSrgb(linear);\n"
        "}\n"
    )
    return "\n".join(out)


# --------------------------------------------------------------------------- golden vectors


def golden_inputs() -> list[tuple[str, tuple[int, int, int]]]:
    """Named colours plus seeded random sRGB samples (8-bit)."""
    rng = np.random.default_rng(GOLDEN_SEED)
    random_rgb = rng.integers(0, 256, size=(RANDOM_GOLDEN_COUNT, 3))
    return [*_NAMED_GOLDEN, *((f"random-{i:02d}", tuple(int(v) for v in c)) for i, c in enumerate(random_rgb))]


def golden_document(generator: dict) -> dict:
    inputs = golden_inputs()
    rgb = np.array([c for _, c in inputs], dtype=np.uint8).reshape(1, -1, 3)
    mats = matrices()
    results = {name: simulate(rgb, m).image[0] for name, m in mats.items()}
    unclipped = {
        name: apply_matrix(srgb_to_linear(rgb.astype(np.float64) / 255.0), m)[0]
        for name, m in mats.items()
    }
    vectors = []
    for i, (label, c) in enumerate(inputs):
        entry: dict = {"label": label, "srgb": list(c)}
        for name in mats:
            lin = unclipped[name][i]
            entry[name] = {
                "srgb": [int(v) for v in results[name][i]],
                "linear_unclipped": [round(float(v), 8) for v in lin],
                "clipped": bool(((lin < -GAMUT_TOLERANCE) | (lin > 1.0 + GAMUT_TOLERANCE)).any()),
            }
        vectors.append(entry)
    return {
        "schema_version": SCHEMA_VERSION,
        "caveat": CAVEAT,
        "generator": generator,
        "description": (
            "Colour step only: decode sRGB, multiply by the matrix, clamp each channel to [0, 1], "
            "encode, round to 8 bits. No blur, no shadow lift."
        ),
        "tolerance_8bit": {
            "recommended": 1,
            "note": "per channel, for 32-bit float shader math; unverified on a device, widen if "
            "the GPU uses reduced precision",
        },
        "seed": GOLDEN_SEED,
        "vectors": vectors,
    }
