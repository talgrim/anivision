"""The colour-transform variants under comparison (see Python.md section 2).

Every variant is a 3x3 matrix that acts on *linear* sRGB column vectors:
``out = M @ rgb_linear``.
"""

from __future__ import annotations

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

# V2: dog-specific derivation from cone sensitivities (475 nm anchor, default
# Gaussian primaries). See derive.py.
DOG_V2 = derive().matrix

VARIANTS: dict[str, np.ndarray] = {
    "v0": DEUTERANOPIA_VIENOT_1999,
    "v2": DOG_V2,
}


def get_variant(name: str) -> np.ndarray:
    """Return the 3x3 matrix for a variant id (case-insensitive)."""
    try:
        return VARIANTS[name.lower()]
    except KeyError:
        raise ValueError(f"unknown variant {name!r}; available: {sorted(VARIANTS)}") from None
