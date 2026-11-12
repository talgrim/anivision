"""sRGB <-> linear light, XYZ, and 8/16-bit/float image handling.

Conventions: images are arrays with the colour channel last (``(..., 3)``).
"Float" images are float64 in [0, 1]; integer images are uint8 (0-255) or
uint16 (0-65535). Linear/sRGB conversion uses the IEC 61966-2-1 piecewise curve.
"""

from __future__ import annotations

import numpy as np

# Linear sRGB (D65) -> CIE XYZ, IEC 61966-2-1. Rows produce X, Y, Z.
SRGB_TO_XYZ = np.array(
    [
        [0.4124564, 0.3575761, 0.1804375],
        [0.2126729, 0.7151522, 0.0721750],
        [0.0193339, 0.1191920, 0.9503041],
    ]
)
XYZ_TO_SRGB = np.linalg.inv(SRGB_TO_XYZ)

# D65 white point (XYZ, Y = 1), as the sum of the sRGB primaries.
D65_WHITE_XYZ = SRGB_TO_XYZ @ np.ones(3)


def to_float(img: np.ndarray) -> np.ndarray:
    """Convert uint8 / uint16 / float image data to float64 in [0, 1].

    Float input is returned as float64 and is not clipped, so out-of-range
    values survive for callers that need to count them.
    """
    arr = np.asarray(img)
    if arr.dtype == np.uint8:
        return arr.astype(np.float64) / 255.0
    if arr.dtype == np.uint16:
        return arr.astype(np.float64) / 65535.0
    if np.issubdtype(arr.dtype, np.floating):
        return arr.astype(np.float64)
    raise TypeError(f"unsupported image dtype: {arr.dtype}")


def from_float(img: np.ndarray, dtype=np.uint8) -> np.ndarray:
    """Clip a float image to [0, 1] and quantise to uint8 / uint16 (rounding),
    or return it as the given float dtype."""
    arr = np.clip(np.asarray(img, dtype=np.float64), 0.0, 1.0)
    if dtype == np.uint8:
        return np.rint(arr * 255.0).astype(np.uint8)
    if dtype == np.uint16:
        return np.rint(arr * 65535.0).astype(np.uint16)
    if np.issubdtype(np.dtype(dtype), np.floating):
        return arr.astype(dtype)
    raise TypeError(f"unsupported target dtype: {dtype}")


def srgb_to_linear(c: np.ndarray) -> np.ndarray:
    """Decode sRGB-encoded values (float, nominally [0, 1]) to linear light.

    The curve is extended to the whole real line by odd symmetry so that
    out-of-range inputs (e.g. from a wide-gamut matrix) round-trip.
    """
    c = np.asarray(c, dtype=np.float64)
    a = np.abs(c)
    lin = np.where(a <= 0.04045, a / 12.92, ((a + 0.055) / 1.055) ** 2.4)
    return np.sign(c) * lin


def linear_to_srgb(c: np.ndarray) -> np.ndarray:
    """Encode linear-light values to sRGB (inverse of :func:`srgb_to_linear`)."""
    c = np.asarray(c, dtype=np.float64)
    a = np.abs(c)
    enc = np.where(a <= 0.0031308, a * 12.92, 1.055 * a ** (1 / 2.4) - 0.055)
    return np.sign(c) * enc


def linear_rgb_to_xyz(rgb: np.ndarray) -> np.ndarray:
    """Linear sRGB ``(..., 3)`` -> CIE XYZ (D65, Y of white = 1)."""
    return np.asarray(rgb, dtype=np.float64) @ SRGB_TO_XYZ.T


def xyz_to_linear_rgb(xyz: np.ndarray) -> np.ndarray:
    """CIE XYZ ``(..., 3)`` -> linear sRGB (may fall outside [0, 1])."""
    return np.asarray(xyz, dtype=np.float64) @ XYZ_TO_SRGB.T
