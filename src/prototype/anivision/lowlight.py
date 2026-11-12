"""Optional shadow lift. Artistic, not physiological.

Raises dark regions by applying a gamma to luminance while keeping each pixel's colour ratios,
so hue and saturation are unchanged. ``gamma = 1`` is off; smaller values lift more.
"""

from __future__ import annotations

import numpy as np

from .gamut import LUMA

# Chosen by eye (scripts/lowlight_sweep.py, NOTES.md): 0.85 opens up shadows naturally, 0.7 is the
# strongest lift before blacks look veiled, 0.55 is hazy. The lift is off (gamma 1) unless asked for.
SUGGESTED_GAMMA = 0.85
STRONGEST_GAMMA = 0.7


def lift_shadows(linear_rgb: np.ndarray, gamma: float) -> np.ndarray:
    """Scale each linear-RGB pixel by ``Y**gamma / Y`` (Y is its luminance in [0, 1])."""
    if not 0.0 < gamma <= 1.0:
        raise ValueError(f"gamma must be in (0, 1], got {gamma}")
    rgb = np.asarray(linear_rgb, dtype=np.float64)
    if gamma == 1.0:
        return rgb.copy()
    y = np.clip(rgb @ LUMA, 1e-6, None)[..., None]  # floor avoids 0/0; black stays black
    return rgb * np.where(y < 1.0, y ** (gamma - 1.0), 1.0)
