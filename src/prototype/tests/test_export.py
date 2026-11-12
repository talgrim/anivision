"""Phase 5 export: layout of the matrices in each snippet, golden vectors, and staleness."""

import json
import re
from pathlib import Path

import numpy as np
import pytest

from anivision.acuity import AcuityParams, sigma_px
from anivision.colorspace import linear_to_srgb
from anivision.gamut import LUMA
from anivision.lowlight import STRONGEST_GAMMA, SUGGESTED_GAMMA
from anivision.export import (
    DEFAULT_VARIANT,
    EXPORT_CONSTANTS,
    EXPORT_PROFILES,
    EXPORT_VARIANTS,
    glsl_snippet,
    golden_document,
    kotlin_snippet,
    matrices,
    matrix_document,
)

GEN = {"git_commit": "abc123", "git_dirty": False}
EXPORT_DIR = Path(__file__).resolve().parent.parent / "export"
FLOAT = r"-?\d+(?:\.\d+)?(?:e[-+]?\d+)?"


def _floats(text: str) -> list[float]:
    return [float(v) for v in re.findall(FLOAT, text)]


def _kotlin_array(snippet: str, name: str) -> np.ndarray:
    body = re.search(rf"val {name} = floatArrayOf\(([^)]*)\)", snippet)
    assert body, f"{name} not found"
    return np.array([float(v.rstrip("f")) for v in body.group(1).split(",")])


def _glsl_matrix(snippet: str, name: str) -> np.ndarray:
    """Rebuild the matrix the way GLSL does: mat3 arguments fill columns first."""
    body = re.search(rf"const mat3 {name} = mat3\((.*?)\);", snippet, re.S)
    assert body, f"{name} not found"
    args = _floats(re.sub(r"//[^\n]*", "", body.group(1)))
    assert len(args) == 9
    return np.array(args).reshape(3, 3).T  # 3 consecutive values = one column


@pytest.mark.parametrize("name", EXPORT_VARIANTS)
def test_kotlin_layouts(name):
    m = matrices()[name]
    kt = kotlin_snippet(GEN)
    const = EXPORT_CONSTANTS[name]
    np.testing.assert_allclose(_kotlin_array(kt, f"{const}_ROW_MAJOR").reshape(3, 3), m, rtol=1e-6, atol=1e-7)
    col = _kotlin_array(kt, f"{const}_COLUMN_MAJOR")
    np.testing.assert_allclose(col.reshape(3, 3).T, m, rtol=1e-6, atol=1e-7)
    cm = _kotlin_array(kt, f"{const}_COLOR_MATRIX_4X5").reshape(4, 5)
    np.testing.assert_allclose(cm[:3, :3], m, rtol=1e-6, atol=1e-7)
    np.testing.assert_array_equal(cm[:3, 3:], 0.0)
    np.testing.assert_array_equal(cm[3], [0, 0, 0, 1, 0])


@pytest.mark.parametrize("name", EXPORT_VARIANTS)
def test_glsl_matrix_is_column_major(name):
    np.testing.assert_allclose(
        _glsl_matrix(glsl_snippet(GEN), EXPORT_CONSTANTS[name]), matrices()[name], rtol=1e-6, atol=1e-7
    )


def test_kotlin_sigma_helper_matches_python():
    kt = kotlin_snippet(GEN)
    consts = {n: float(v) for n, v in re.findall(r"const val (ACUITY_\w+) = (" + FLOAT + ")f", kt)}
    k, ratio, hfov = consts["ACUITY_K"], consts["ACUITY_RATIO"], consts["ACUITY_HFOV_DEG"]
    for edge in (640.0, 1920.0):
        for ex in (1.0, 4.0):
            kotlin = k * (ratio / 60 / 2) * (edge / hfov) * ex
            assert kotlin == pytest.approx(sigma_px(edge, AcuityParams(exaggeration=ex)), rel=1e-6)


def _shader_pipeline_f32(srgb: np.ndarray, m: np.ndarray) -> np.ndarray:
    """The GLSL dogView() written out in float32, using only what the snippet exports."""
    c = (srgb / np.float32(255)).astype(np.float32)
    lin = np.where(
        c <= 0.04045, c / np.float32(12.92), ((c + np.float32(0.055)) / np.float32(1.055)) ** np.float32(2.4)
    ).astype(np.float32)
    out = np.clip(lin @ m.T.astype(np.float32), 0.0, 1.0)
    enc = np.where(
        out <= 0.0031308, out * np.float32(12.92), np.float32(1.055) * out ** np.float32(1 / 2.4) - np.float32(0.055)
    )
    return np.rint(enc * np.float32(255)).astype(np.int32)


@pytest.mark.parametrize("name", EXPORT_VARIANTS)
def test_shader_math_from_snippet_reproduces_golden_vectors(name):
    m = _glsl_matrix(glsl_snippet(GEN), EXPORT_CONSTANTS[name])
    doc = golden_document(GEN)
    tol = doc["tolerance_8bit"]["recommended"]
    inputs = np.array([v["srgb"] for v in doc["vectors"]], dtype=np.float32)
    expected = np.array([v[name]["srgb"] for v in doc["vectors"]])
    assert np.abs(_shader_pipeline_f32(inputs, m) - expected).max() <= tol


def test_golden_vectors_cover_the_required_cases():
    doc = golden_document(GEN)
    vecs = doc["vectors"]
    assert 50 <= len(vecs) <= 100
    labels = {v["label"] for v in vecs}
    assert {"black", "white", "red", "green", "blue", "skin-1", "skin-7"} <= labels
    assert sum(label.startswith("gray-") for label in labels) >= 5
    assert sum(label.startswith("random-") for label in labels) >= 30
    for v in vecs:
        assert all(0 <= c <= 255 for c in v["srgb"] + v["v2"]["srgb"] + v["v0"]["srgb"])
    grays = [v for v in vecs if v["label"].startswith("gray-")]
    for v in grays + [x for x in vecs if x["label"] in ("black", "white")]:
        for name in EXPORT_VARIANTS:
            assert max(abs(a - b) for a, b in zip(v[name]["srgb"], v["srgb"])) <= 1  # grays stay put
    assert any(v["v2"]["clipped"] for v in vecs) and any(not v["v2"]["clipped"] for v in vecs)


def _shadow_lift_f32(lin: np.ndarray, luma: np.ndarray, gamma: float) -> np.ndarray:
    """The GLSL shadowLift() written out in float32."""
    f = np.float32
    y = np.clip(lin @ luma, f(1e-6), f(1.0))[..., None]
    peak = np.maximum(lin.max(axis=-1, keepdims=True), f(1e-6))
    return lin * np.minimum(y ** f(gamma - 1.0), np.maximum(f(1.0) / peak, f(1.0)))


def _glsl_luma(snippet: str) -> np.ndarray:
    body = re.search(r"const vec3 LUMA = vec3\(([^)]*)\);", snippet)
    assert body, "LUMA not found"
    return np.array(_floats(body.group(1)), dtype=np.float32)


def test_glsl_exports_the_shadow_lift():
    glsl = glsl_snippet(GEN)
    assert "vec3 shadowLift(vec3 rgb, float gamma)" in glsl
    np.testing.assert_allclose(_glsl_luma(glsl), LUMA, rtol=1e-6)


def test_shader_shadow_lift_reproduces_golden_vectors():
    glsl = glsl_snippet(GEN)
    doc = golden_document(GEN)
    lift = doc["shadow_lift_vectors"]
    m = _glsl_matrix(glsl, EXPORT_CONSTANTS[lift["variant"]]).astype(np.float32)
    luma = _glsl_luma(glsl)
    inputs = np.array([v["srgb"] for v in doc["vectors"]], dtype=np.float32)
    c = inputs / np.float32(255)
    lin = np.where(c <= 0.04045, c / np.float32(12.92), ((c + np.float32(0.055)) / np.float32(1.055)) ** np.float32(2.4))
    lin = lin.astype(np.float32)
    assert [g["gamma"] for g in lift["gammas"]] == [SUGGESTED_GAMMA, STRONGEST_GAMMA]
    for entry in lift["gammas"]:
        out = np.clip(_shadow_lift_f32(lin, luma, entry["gamma"]) @ m.T, 0.0, 1.0)
        enc = np.where(out <= 0.0031308, out * np.float32(12.92), np.float32(1.055) * out ** np.float32(1 / 2.4) - np.float32(0.055))
        got = np.rint(enc * np.float32(255)).astype(np.int32)
        assert np.abs(got - np.array(entry["srgb"])).max() <= doc["tolerance_8bit"]["recommended"]


def test_shadow_lift_vectors_are_aligned_and_brighten_the_dark_ones():
    doc = golden_document(GEN)
    vecs = doc["vectors"]
    strongest = doc["shadow_lift_vectors"]["gammas"][1]["srgb"]
    for entry in doc["shadow_lift_vectors"]["gammas"]:
        assert len(entry["srgb"]) == len(vecs)
        assert all(0 <= c <= 255 for px in entry["srgb"] for c in px)
    by_label = {v["label"]: (v, out) for v, out in zip(vecs, strongest)}
    black, black_out = by_label["black"]
    white, white_out = by_label["white"]
    assert black_out == black["v2"]["srgb"] and white_out == white["v2"]["srgb"]
    dark, dark_out = by_label["gray-32"]
    assert sum(dark_out) > sum(dark["v2"]["srgb"])


def test_golden_vectors_are_deterministic():
    assert golden_document(GEN) == golden_document(GEN)


def test_matrix_document_contents():
    doc = matrix_document(GEN)
    dog = doc["profiles"]["dog"]
    assert dog["default_variant"] == DEFAULT_VARIANT == "v2"
    assert set(dog["variants"]) == {"v2", "v0"}
    cat = doc["profiles"]["cat"]
    assert cat["default_variant"] == "cat" and set(cat["variants"]) == {"cat"}
    assert (dog["acuity_ratio"], cat["acuity_ratio"]) == (3.75, 3.5)
    assert cat["variants"]["cat"]["derivation"]["s_peak_nm"] == 450.0
    assert set(EXPORT_VARIANTS) == {v for p in EXPORT_PROFILES.values() for v in p.variants}
    for animal, profile in doc["profiles"].items():
        for name, m in matrices().items():
            if name in profile["variants"]:
                np.testing.assert_array_equal(profile["variants"][name]["matrix"], m)
    ac = doc["acuity"]
    assert ac["enabled_by_default"] is False and ac["default_exaggeration"] == 1.0
    assert ac["k"] == pytest.approx(1.366, abs=1e-3)
    for ex in ac["sigma_px_examples"]:
        want = sigma_px(ex["long_edge_px"], AcuityParams(exaggeration=ex["exaggeration"]))
        assert ex["sigma_px"] == pytest.approx(want)
    assert ac["linear_light_check"]["srgb_8bit"] == int(np.rint(linear_to_srgb(0.5) * 255))
    assert doc["shadow_lift"]["enabled_by_default"] is False


def _without_generator(doc: dict) -> dict:
    return {k: v for k, v in doc.items() if k != "generator"}


def _assert_same(actual, expected, path="doc"):
    """Structural equality where floats only need to agree to 1e-9 (numpy builds differ in the last digits)."""
    if isinstance(expected, dict):
        assert isinstance(actual, dict) and actual.keys() == expected.keys(), path
        for key in expected:
            _assert_same(actual[key], expected[key], f"{path}.{key}")
    elif isinstance(expected, list):
        assert isinstance(actual, list) and len(actual) == len(expected), path
        for i, (a, e) in enumerate(zip(actual, expected)):
            _assert_same(a, e, f"{path}[{i}]")
    elif isinstance(expected, float):
        assert actual == pytest.approx(expected, rel=1e-9, abs=1e-9), path
    else:
        assert actual == expected, path


@pytest.mark.skipif(not (EXPORT_DIR / "matrix.json").exists(), reason="export/ not generated")
def test_committed_export_is_not_stale():
    """Fails when the matrices or recipes changed but scripts/export_constants.py was not re-run."""
    for fname, doc in (("matrix.json", matrix_document(GEN)), ("golden_vectors.json", golden_document(GEN))):
        on_disk = json.loads((EXPORT_DIR / fname).read_text(encoding="utf-8"))
        try:
            _assert_same(_without_generator(on_disk), _without_generator(json.loads(json.dumps(doc))))
        except AssertionError as exc:
            raise AssertionError(f"export/{fname} is stale (run scripts/export_constants.py): {exc}") from exc
    for fname, text in (("AnimalVisionConstants.kt.txt", kotlin_snippet(GEN)), ("animal_vision.glsl", glsl_snippet(GEN))):
        on_disk = (EXPORT_DIR / fname).read_text(encoding="utf-8")
        strip = lambda t: "\n".join(t.splitlines()[1:])  # noqa: E731  (first line names the commit)
        assert strip(on_disk) == strip(text), f"export/{fname} is stale: run scripts/export_constants.py"


def test_cat_acuity_is_exported_for_the_android_sigma_helper():
    kotlin = kotlin_snippet(GEN)
    assert "const val CAT_ACUITY_RATIO = 3.5f" in kotlin
    assert "const val DOG_ACUITY_RATIO = 3.75f" in kotlin
    assert "acuityRatio: Float = ACUITY_RATIO" in kotlin


def test_cat_traits_are_exported():
    doc = matrix_document(GEN)
    assert doc["profiles"]["cat"]["shadow_gamma"] == 0.85 and doc["profiles"]["cat"]["peripheral_blur"] == 1.0
    assert doc["profiles"]["dog"]["shadow_gamma"] == 1.0 and doc["profiles"]["dog"]["peripheral_blur"] == 0.0
    assert "r2" in doc["acuity"]["peripheral_blur"] and "inherent" in doc["shadow_lift"]
    kotlin = kotlin_snippet(GEN)
    for line in ("CAT_SHADOW_GAMMA = 0.85f", "CAT_PERIPHERAL_BLUR = 1f", "DOG_SHADOW_GAMMA = 1f"):
        assert f"const val {line}" in kotlin
