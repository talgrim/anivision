"""Spectral strip: how monochromatic lights look on the display, before and after a variant.

A monochromatic light lies outside the sRGB gamut (negative red for blue-green
wavelengths), so it cannot be shown directly. :func:`display_stimulus` shows it
the standard way: add the least white that brings every channel to >= 0, then
scale so the brightest channel is 1. Adding white leaves the ratio of the two
dog cone signals relative to white's unchanged, so the wavelength where a
variant renders gray is the same as for the true light.
"""

from __future__ import annotations

import numpy as np

from .derive import WHITE, DerivedTransform, cone_sensitivities, monochromatic_rgb
from .metrics import chroma, linear_rgb_to_lab
from .profiles import DOG, Profile

STRIP_WAVELENGTHS = np.arange(420.0, 681.0, 1.0)
# Window searched for the gray crossing: between the dog's S (429 nm) and L (555 nm) peaks.
NEUTRAL_SEARCH_NM = (440.0, 560.0)


def display_stimulus(wavelengths: np.ndarray = STRIP_WAVELENGTHS) -> np.ndarray:
    """Linear sRGB ``(N, 3)`` in [0, 1]: each monochromatic light as shown on the display."""
    rgb = np.stack([monochromatic_rgb(w) for w in np.asarray(wavelengths, dtype=np.float64)])
    rgb = rgb + np.maximum(-rgb.min(axis=1), 0.0)[:, None]  # add white until min channel is 0
    return rgb / rgb.max(axis=1, keepdims=True)


def render_strip(matrix: np.ndarray | None, wavelengths: np.ndarray = STRIP_WAVELENGTHS) -> np.ndarray:
    """Linear sRGB ``(N, 3)`` of the strip after ``matrix`` (``None`` = identity), clipped to [0, 1]."""
    rgb = display_stimulus(wavelengths)
    if matrix is not None:
        rgb = rgb @ np.asarray(matrix, dtype=np.float64).T
    return np.clip(rgb, 0.0, 1.0)


def true_spectrum_strip(
    transform: DerivedTransform,
    profile: Profile = DOG,
    wavelengths: np.ndarray = STRIP_WAVELENGTHS,
) -> np.ndarray:
    """Linear sRGB ``(N, 3)``: true monochromatic lights as the V2 plane would render them.

    Uses the dog's cone signals of the real spectral line (not of a display stimulus), so
    it tests the derivation itself. Each wavelength is scaled so its brightest cone signal,
    relative to white's, equals 1, then mapped as ``[w a] ([C w, C a])^-1 signal``.
    """
    sens = cone_sensitivities(profile, np.asarray(wavelengths, dtype=np.float64))  # (2, N)
    c = transform.cone_catch
    white_signal = c @ WHITE
    rel = sens / white_signal[:, None]
    rel = rel / rel.max(axis=0)
    basis = np.stack([WHITE, transform.anchor_rgb], axis=1)  # 3x2
    signals = np.stack([white_signal, c @ transform.anchor_rgb], axis=1)  # 2x2
    out = basis @ np.linalg.inv(signals) @ (rel * white_signal[:, None])  # (3, N)
    return np.clip(out.T, 0.0, 1.0)


def neutral_crossing_nm(
    strip_rgb: np.ndarray,
    wavelengths: np.ndarray = STRIP_WAVELENGTHS,
    window: tuple[float, float] = NEUTRAL_SEARCH_NM,
) -> float:
    """Wavelength (nm) inside ``window`` where a rendered strip is least chromatic.

    ``strip_rgb`` is linear sRGB ``(N, 3)`` (from :func:`render_strip` or
    :func:`true_spectrum_strip`). Parabolic refinement through the minimum of CIELAB chroma.
    """
    wl = np.asarray(wavelengths, dtype=np.float64)
    c = chroma(linear_rgb_to_lab(strip_rgb))
    idx = np.nonzero((wl >= window[0]) & (wl <= window[1]))[0]
    k = idx[np.argmin(c[idx])]
    if 0 < k < len(wl) - 1:  # vertex of the parabola through three points
        y0, y1, y2 = c[k - 1], c[k], c[k + 1]
        denom = y0 - 2.0 * y1 + y2
        if denom > 0:
            return float(wl[k] + 0.5 * (y0 - y2) / denom * (wl[1] - wl[0]))
    return float(wl[k])
