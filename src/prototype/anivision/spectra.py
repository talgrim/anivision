"""Spectral building blocks: cone sensitivities, CIE 1931 CMFs, display primaries.

All spectra are sampled on a wavelength grid in nm (default 380-720 nm, 1 nm).
"""

from __future__ import annotations

import numpy as np

from .colorspace import D65_WHITE_XYZ

WAVELENGTHS = np.arange(380.0, 721.0, 1.0)

# Display primaries modelled as Gaussians (R, G, B centres and sigma, in nm).
DEFAULT_PRIMARY_CENTERS = (610.0, 550.0, 465.0)
DEFAULT_PRIMARY_SIGMA = 20.0


# ----------------------------------------------------------------- cone curves
def govardovskii_a1(wl: np.ndarray, lmax: float, include_beta: bool = True) -> np.ndarray:
    """Normalised A1-pigment spectral sensitivity (Govardovskii et al. 2000).

    Alpha band plus (optionally) the beta band, scaled so the peak is 1.
    """
    wl = np.asarray(wl, dtype=np.float64)

    def raw(lam: np.ndarray) -> np.ndarray:
        x = lmax / lam
        a = 0.8795 + 0.0459 * np.exp(-((lmax - 300.0) ** 2) / 11940.0)
        alpha = 1.0 / (
            np.exp(69.7 * (a - x)) + np.exp(28.0 * (0.922 - x)) + np.exp(-14.9 * (1.104 - x)) + 0.674
        )
        if not include_beta:
            return alpha
        lam_mid = 189.0 + 0.315 * lmax
        b = -40.5 + 0.195 * lmax
        return alpha + 0.26 * np.exp(-(((lam - lam_mid) / b) ** 2))

    peak = raw(np.arange(250.0, 800.0, 0.1)).max()  # independent of the sampling grid
    return raw(wl) / peak


# ------------------------------------------------------------- CIE 1931 2 deg CMFs
def _lobe(wl: np.ndarray, mu: float, s1: float, s2: float) -> np.ndarray:
    sigma = np.where(wl < mu, s1, s2)
    return np.exp(-0.5 * ((wl - mu) / sigma) ** 2)


def cie_cmf(wl: np.ndarray) -> np.ndarray:
    """CIE 1931 colour matching functions, shape ``(len(wl), 3)`` (xbar, ybar, zbar).

    Analytic multi-lobe fit of Wyman, Sloan & Shirley (2013), accurate to
    about 1% of the tabulated values.
    """
    wl = np.asarray(wl, dtype=np.float64)
    x = 1.056 * _lobe(wl, 599.8, 37.9, 31.0) + 0.362 * _lobe(wl, 442.0, 16.0, 26.7) - 0.065 * _lobe(
        wl, 501.1, 20.4, 26.2
    )
    y = 0.821 * _lobe(wl, 568.8, 46.9, 40.5) + 0.286 * _lobe(wl, 530.9, 16.3, 31.1)
    z = 1.217 * _lobe(wl, 437.0, 11.8, 36.0) + 0.681 * _lobe(wl, 459.0, 26.0, 13.8)
    return np.stack([x, y, z], axis=-1)


# -------------------------------------------------------------- display primaries
def primary_spds(
    wl: np.ndarray = WAVELENGTHS,
    centers: tuple[float, float, float] = DEFAULT_PRIMARY_CENTERS,
    sigma: float | tuple[float, float, float] = DEFAULT_PRIMARY_SIGMA,
) -> np.ndarray:
    """Spectral power distributions of the R, G, B primaries, shape ``(3, len(wl))``.

    Each primary is a Gaussian. The three scale factors are solved so that
    R + G + B (all channels at 1) has exactly the tristimulus of D65 white
    (Y = 1) under the CIE 1931 observer. Only white is matched, not the
    individual primaries' chromaticities.
    """
    wl = np.asarray(wl, dtype=np.float64)
    sigmas = np.broadcast_to(np.asarray(sigma, dtype=np.float64), (3,))
    shapes = np.stack(
        [np.exp(-0.5 * ((wl - c) / s) ** 2) for c, s in zip(centers, sigmas)]
    )  # (3, N)

    step = float(np.mean(np.diff(wl))) if len(wl) > 1 else 1.0
    xyz_per_shape = cie_cmf(wl).T @ shapes.T * step  # (3 XYZ, 3 primaries)
    scales = np.linalg.solve(xyz_per_shape, D65_WHITE_XYZ)
    if np.any(scales <= 0):
        raise ValueError(f"primaries cannot reproduce white with positive power: {scales}")
    return shapes * scales[:, None]
