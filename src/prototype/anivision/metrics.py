"""Image and matrix difference metrics: CIELAB, CIEDE2000 and Frobenius distance.

CIEDE2000 follows Sharma, Wu & Dalal (2005), "The CIEDE2000 colour-difference
formula: implementation notes, supplementary test data, and mathematical
observations", Color Res. Appl. 30(1):21-30. It is implemented here directly
(not imported from a library) and checked against that paper's test pairs.
"""

from __future__ import annotations

import numpy as np

from .colorspace import D65_WHITE_XYZ, linear_rgb_to_xyz, srgb_to_linear, to_float

_DELTA = 6.0 / 29.0


def xyz_to_lab(xyz: np.ndarray, white: np.ndarray = D65_WHITE_XYZ) -> np.ndarray:
    """CIE XYZ ``(..., 3)`` -> CIELAB, relative to ``white`` (defaults to sRGB D65)."""
    t = np.asarray(xyz, dtype=np.float64) / np.asarray(white, dtype=np.float64)
    f = np.where(t > _DELTA**3, np.cbrt(t), t / (3.0 * _DELTA**2) + 4.0 / 29.0)
    fx, fy, fz = f[..., 0], f[..., 1], f[..., 2]
    return np.stack([116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz)], axis=-1)


def linear_rgb_to_lab(rgb: np.ndarray) -> np.ndarray:
    """Linear sRGB ``(..., 3)`` -> CIELAB (D65)."""
    return xyz_to_lab(linear_rgb_to_xyz(rgb))


def srgb_to_lab(img: np.ndarray) -> np.ndarray:
    """sRGB image (uint8, uint16 or float in [0, 1]) -> CIELAB (D65)."""
    return linear_rgb_to_lab(srgb_to_linear(to_float(np.asarray(img))))


def chroma(lab: np.ndarray) -> np.ndarray:
    """CIELAB chroma C* = hypot(a*, b*)."""
    lab = np.asarray(lab, dtype=np.float64)
    return np.hypot(lab[..., 1], lab[..., 2])


def delta_e_2000(
    lab1: np.ndarray, lab2: np.ndarray, kL: float = 1.0, kC: float = 1.0, kH: float = 1.0
) -> np.ndarray:
    """CIEDE2000 colour difference between two CIELAB arrays of shape ``(..., 3)``."""
    lab1 = np.asarray(lab1, dtype=np.float64)
    lab2 = np.asarray(lab2, dtype=np.float64)
    L1, a1, b1 = lab1[..., 0], lab1[..., 1], lab1[..., 2]
    L2, a2, b2 = lab2[..., 0], lab2[..., 1], lab2[..., 2]

    c_bar = (np.hypot(a1, b1) + np.hypot(a2, b2)) / 2.0
    c_bar7 = c_bar**7
    g = 0.5 * (1.0 - np.sqrt(c_bar7 / (c_bar7 + 25.0**7)))
    a1p, a2p = (1.0 + g) * a1, (1.0 + g) * a2
    c1p, c2p = np.hypot(a1p, b1), np.hypot(a2p, b2)
    h1p = np.degrees(np.arctan2(b1, a1p)) % 360.0  # arctan2(0, 0) = 0, as the paper requires
    h2p = np.degrees(np.arctan2(b2, a2p)) % 360.0

    d_lp = L2 - L1
    d_cp = c2p - c1p
    chroma_product = c1p * c2p
    zero = chroma_product == 0.0
    dh = h2p - h1p
    dh = np.where(dh > 180.0, dh - 360.0, np.where(dh < -180.0, dh + 360.0, dh))
    dh = np.where(zero, 0.0, dh)
    d_hp = 2.0 * np.sqrt(chroma_product) * np.sin(np.radians(dh / 2.0))

    l_bar = (L1 + L2) / 2.0
    c_barp = (c1p + c2p) / 2.0
    h_sum = h1p + h2p
    h_bar = np.where(
        np.abs(h1p - h2p) <= 180.0,
        h_sum / 2.0,
        np.where(h_sum < 360.0, (h_sum + 360.0) / 2.0, (h_sum - 360.0) / 2.0),
    )
    h_bar = np.where(zero, h_sum, h_bar)

    t = (
        1.0
        - 0.17 * np.cos(np.radians(h_bar - 30.0))
        + 0.24 * np.cos(np.radians(2.0 * h_bar))
        + 0.32 * np.cos(np.radians(3.0 * h_bar + 6.0))
        - 0.20 * np.cos(np.radians(4.0 * h_bar - 63.0))
    )
    d_theta = 30.0 * np.exp(-(((h_bar - 275.0) / 25.0) ** 2))
    c_barp7 = c_barp**7
    r_c = 2.0 * np.sqrt(c_barp7 / (c_barp7 + 25.0**7))
    s_l = 1.0 + 0.015 * (l_bar - 50.0) ** 2 / np.sqrt(20.0 + (l_bar - 50.0) ** 2)
    s_c = 1.0 + 0.045 * c_barp
    s_h = 1.0 + 0.015 * c_barp * t
    r_t = -np.sin(np.radians(2.0 * d_theta)) * r_c

    tl = d_lp / (kL * s_l)
    tc = d_cp / (kC * s_c)
    th = d_hp / (kH * s_h)
    return np.sqrt(tl**2 + tc**2 + th**2 + r_t * tc * th)


def frobenius(a: np.ndarray, b: np.ndarray) -> float:
    """Frobenius norm of ``a - b`` (matrix distance)."""
    return float(np.linalg.norm(np.asarray(a, dtype=np.float64) - np.asarray(b, dtype=np.float64)))


def image_delta_e(img_a: np.ndarray, img_b: np.ndarray) -> np.ndarray:
    """Per-pixel CIEDE2000 between two sRGB images of the same shape."""
    if np.shape(img_a) != np.shape(img_b):
        raise ValueError(f"shape mismatch: {np.shape(img_a)} vs {np.shape(img_b)}")
    return delta_e_2000(srgb_to_lab(img_a), srgb_to_lab(img_b))
