"""The colour-transform variants under comparison (see Python.md section 2).

Every variant is a 3x3 matrix that acts on *linear* sRGB column vectors:
``out = M @ rgb_linear``.
"""

from __future__ import annotations

import functools
import warnings

import numpy as np

# V0: stock deuteranopia matrix, Vienot, Brettel & Mollon (1999), for linear
# sRGB (D65). Rows 1 and 2 are equal, so red and green collapse onto one
# axis; each row sums to 1, so grays stay gray (M @ white = white).
# Checked against the paper (Eq. 4-5: RGB->LMS matrix and the deuteranope
# matrix) by rebuilding inv(RGB2LMS) @ D @ RGB2LMS; see NOTES.md and
# tests/test_simulate.py::test_v0_matches_vienot_1999_derivation.
DEUTERANOPIA_VIENOT_1999 = np.array(
    [
        [0.29275, 0.70725, 0.0],
        [0.29275, 0.70725, 0.0],
        [-0.02234, 0.02234, 1.0],
    ]
)

from .derive import derive  # noqa: E402  (after V0 so the module reads top-down)
from .profiles import CAT, DOG, Profile  # noqa: E402

# V2: dog-specific derivation from cone sensitivities (475 nm anchor, default
# Gaussian primaries). See derive.py.
DOG_V2 = derive().matrix.copy()

# CAT: the same derivation (475 nm anchor, default primaries) with the cat's cone peaks.
CAT_V2 = derive(CAT).matrix.copy()

# The matrices are shared module state: make them read-only so a caller cannot change what every
# later simulate() uses.
DEUTERANOPIA_VIENOT_1999.flags.writeable = False
DOG_V2.flags.writeable = False
CAT_V2.flags.writeable = False

VARIANTS: dict[str, np.ndarray] = {
    "v0": DEUTERANOPIA_VIENOT_1999,
    "v2": DOG_V2,
    "cat": CAT_V2,
}

# Every id get_variant() understands. V1 is optional: it needs colour-science.
VARIANT_IDS = ("v0", "v1", "v2", "cat")

# The animal whose acuity goes with each variant. V0 and V1 model a human deuteranope and have
# always been paired with the dog's acuity.
VARIANT_PROFILES: dict[str, Profile] = {"v0": DOG, "v1": DOG, "v2": DOG, "cat": CAT}


def acuity_ratio_for(variant: str) -> float:
    """The acuity ratio that goes with a variant id (case-insensitive)."""
    return VARIANT_PROFILES[variant.lower()].acuity_ratio


@functools.cache
def machado_deuteranopia() -> np.ndarray:
    """V1: Machado, Oliveira & Fernandes (2009) deuteranopia matrix (severity 1.0).

    Taken from the optional ``colour-science`` package as an independent
    cross-check of V0 and V2. It models a human deuteranope, not a dog, so it
    is a reference point rather than a candidate to ship.
    """
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")  # colour warns that SciPy is absent
            from colour.blindness import matrix_cvd_Machado2009

            matrix = np.array(matrix_cvd_Machado2009("Deuteranomaly", 1.0), dtype=np.float64)
            matrix.flags.writeable = False
            return matrix
    except ImportError as exc:
        raise ImportError(
            "variant v1 needs the optional colour-science package: "
            "pip install -r requirements-extra.txt"
        ) from exc


def get_variant(name: str) -> np.ndarray:
    """Return the 3x3 matrix for a variant id (case-insensitive)."""
    key = name.lower()
    if key == "v1":
        return machado_deuteranopia()
    try:
        return VARIANTS[key]
    except KeyError:
        raise ValueError(f"unknown variant {name!r}; available: {list(VARIANT_IDS)}") from None
