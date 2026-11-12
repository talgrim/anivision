"""Apply a 3x3 colour matrix to an image in linear light.

Pipeline: decode sRGB -> linear, multiply by the matrix, clip to [0, 1]
(counting out-of-gamut pixels first), re-encode to sRGB.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from .colorspace import from_float, linear_to_srgb, srgb_to_linear, to_float

# Tolerance so that rounding noise (e.g. white mapping to 1.0000000000000002)
# is not reported as out of gamut.
GAMUT_TOLERANCE = 1e-6


@dataclass(frozen=True)
class SimResult:
    """Simulated image plus how many pixels had to be clipped."""

    image: np.ndarray
    out_of_gamut_pixels: int
    total_pixels: int

    @property
    def out_of_gamut_fraction(self) -> float:
        return self.out_of_gamut_pixels / self.total_pixels if self.total_pixels else 0.0


def apply_matrix(linear_rgb: np.ndarray, matrix: np.ndarray) -> np.ndarray:
    """Multiply every ``(..., 3)`` linear-RGB pixel by ``matrix`` (no clipping)."""
    m = np.asarray(matrix, dtype=np.float64)
    if m.shape != (3, 3):
        raise ValueError(f"matrix must be 3x3, got {m.shape}")
    return np.asarray(linear_rgb, dtype=np.float64) @ m.T


def count_out_of_gamut(linear_rgb: np.ndarray) -> int:
    """Number of pixels with any channel outside [0, 1] (beyond tolerance)."""
    arr = np.asarray(linear_rgb)
    bad = (arr < -GAMUT_TOLERANCE) | (arr > 1.0 + GAMUT_TOLERANCE)
    return int(np.count_nonzero(bad.any(axis=-1)))


def simulate(img: np.ndarray, matrix: np.ndarray) -> SimResult:
    """Run ``img`` (sRGB uint8 / uint16 / float, ``(..., 3)``) through ``matrix``.

    The output has the input's integer dtype, or float64 for float input.
    """
    arr = np.asarray(img)
    out_dtype = arr.dtype if arr.dtype in (np.uint8, np.uint16) else np.float64

    linear = srgb_to_linear(to_float(arr))
    mapped = apply_matrix(linear, matrix)
    n_bad = count_out_of_gamut(mapped)
    clipped = np.clip(mapped, 0.0, 1.0)
    encoded = linear_to_srgb(clipped)

    return SimResult(
        image=from_float(encoded, out_dtype),
        out_of_gamut_pixels=n_bad,
        total_pixels=int(np.prod(arr.shape[:-1])),
    )
