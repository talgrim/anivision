"""Gamut policies for colours the display cannot show after a colour matrix.

``clip``        clamp each channel to [0, 1]. Simple and what the first prototype did, but it
                shifts hue and lightness of saturated colours.
``desaturate``  move the colour toward the gray of the same luminance, just far enough to fit.
                Keeps luminance and chromaticity direction; only saturation drops.
"""

from __future__ import annotations

import numpy as np

from .colorspace import SRGB_TO_XYZ

GAMUT_POLICIES = ("clip", "desaturate")
LUMA = SRGB_TO_XYZ[1]  # Y row: luminance of linear sRGB


def desaturate_to_gamut(rgb: np.ndarray) -> np.ndarray:
    """Pull each out-of-gamut linear-RGB pixel toward its equal-luminance gray until it fits.

    In-gamut pixels are returned unchanged. If the luminance itself is outside [0, 1] it is
    clamped first (the pixel then ends at white or black).
    """
    rgb = np.asarray(rgb, dtype=np.float64)
    y = np.clip(rgb @ LUMA, 0.0, 1.0)[..., None]
    d = rgb - y
    with np.errstate(divide="ignore", invalid="ignore"):
        t_hi = np.where(rgb > 1.0, (1.0 - y) / d, np.inf)  # channel above 1: d > 0
        t_lo = np.where(rgb < 0.0, y / (y - rgb), np.inf)  # channel below 0: d < 0
    t = np.minimum(np.minimum(t_hi, t_lo).min(axis=-1, keepdims=True), 1.0)
    return np.clip(y + t * d, 0.0, 1.0)


def fit_to_gamut(rgb: np.ndarray, policy: str = "clip") -> np.ndarray:
    """Apply a gamut policy to linear RGB; the result lies in [0, 1]."""
    if policy == "clip":
        return np.clip(rgb, 0.0, 1.0)
    if policy == "desaturate":
        return desaturate_to_gamut(rgb)
    raise ValueError(f"unknown gamut policy {policy!r}; available: {list(GAMUT_POLICIES)}")
