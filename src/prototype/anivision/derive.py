"""Derive the dog colour transform from cone sensitivities (variant V2).

Steps (Python.md section 2):
  C  = cone catch of the display primaries, a 2x3 matrix  (linear RGB -> S, L)
  M  = [w a] . ([C w, C a])^-1 . C        (3x3 projection onto span{w, a})
where w is white and a an anchor colour. M leaves grays alone, is idempotent,
has rank 2, and sends everything the dog cannot tell apart (ker C) to the
same output.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from .colorspace import xyz_to_linear_rgb
from .profiles import DOG, Profile
from .spectra import (
    DEFAULT_PRIMARY_CENTERS,
    DEFAULT_PRIMARY_SIGMA,
    WAVELENGTHS,
    cie_cmf,
    govardovskii_a1,
    primary_spds,
)

WHITE = np.ones(3)
DEFAULT_ANCHOR_NM = 475.0


@dataclass(frozen=True)
class DerivedTransform:
    matrix: np.ndarray  # 3x3, acts on linear RGB column vectors
    cone_catch: np.ndarray  # C, 2x3 (rows: S, L)
    anchor_rgb: np.ndarray  # linear sRGB anchor used (may have negative parts)
    neutral_point_nm: float


def cone_sensitivities(profile: Profile = DOG, wl: np.ndarray = WAVELENGTHS) -> np.ndarray:
    """Rows: S and L cone sensitivity on ``wl``, shape ``(2, len(wl))``."""
    return np.stack(
        [govardovskii_a1(wl, profile.s_peak_nm), govardovskii_a1(wl, profile.l_peak_nm)]
    )


def cone_catch_matrix(
    profile: Profile = DOG,
    wl: np.ndarray = WAVELENGTHS,
    centers: tuple[float, float, float] = DEFAULT_PRIMARY_CENTERS,
    sigma: float | tuple[float, float, float] = DEFAULT_PRIMARY_SIGMA,
) -> np.ndarray:
    """2x3 matrix C: integral of (cone sensitivity x primary spectrum)."""
    step = float(np.mean(np.diff(wl)))
    return cone_sensitivities(profile, wl) @ primary_spds(wl, centers, sigma).T * step


def monochromatic_rgb(wavelength_nm: float) -> np.ndarray:
    """Linear sRGB coordinates of a monochromatic stimulus (negative parts allowed)."""
    xyz = cie_cmf(np.array([float(wavelength_nm)]))[0]
    return xyz_to_linear_rgb(xyz)


def projection_matrix(C: np.ndarray, anchor: np.ndarray, white: np.ndarray = WHITE) -> np.ndarray:
    """``M = [w a] ([C w, C a])^-1 C``; only the direction of ``anchor`` matters."""
    basis = np.stack([white, anchor], axis=1)  # 3x2
    signals = np.stack([C @ white, C @ anchor], axis=1)  # 2x2
    if abs(np.linalg.det(signals)) < 1e-9 * np.linalg.norm(signals) ** 2:
        raise ValueError("anchor is (nearly) indistinguishable from white for these cones")
    return basis @ np.linalg.inv(signals) @ C


def neutral_point(C: np.ndarray, profile: Profile = DOG, wl: np.ndarray = WAVELENGTHS) -> float:
    """Wavelength (nm) of the monochromatic light whose cone-signal ratio equals white's.

    Searched between the two cone peaks, where S/L falls monotonically.
    """
    s, l = cone_sensitivities(profile, wl)
    s_white, l_white = C @ WHITE
    diff = s * l_white - l * s_white  # > 0 where the stimulus looks "bluer" than white
    inside = (wl >= profile.s_peak_nm) & (wl <= profile.l_peak_nm)
    idx = np.nonzero(inside)[0]
    d = diff[idx]
    crossings = np.nonzero((d[:-1] > 0) & (d[1:] <= 0))[0]
    if len(crossings) == 0:
        raise ValueError("no neutral point between the cone peaks")
    i = idx[crossings[0]]
    frac = diff[i] / (diff[i] - diff[i + 1])
    return float(wl[i] + frac * (wl[i + 1] - wl[i]))


def derive(
    profile: Profile = DOG,
    anchor: float | np.ndarray = DEFAULT_ANCHOR_NM,
    centers: tuple[float, float, float] = DEFAULT_PRIMARY_CENTERS,
    sigma: float | tuple[float, float, float] = DEFAULT_PRIMARY_SIGMA,
    wl: np.ndarray = WAVELENGTHS,
) -> DerivedTransform:
    """Full V2 derivation. ``anchor`` is a wavelength in nm or a linear-RGB 3-vector."""
    C = cone_catch_matrix(profile, wl, centers, sigma)
    a = monochromatic_rgb(anchor) if np.isscalar(anchor) else np.asarray(anchor, dtype=np.float64)
    return DerivedTransform(
        matrix=projection_matrix(C, a),
        cone_catch=C,
        anchor_rgb=a,
        neutral_point_nm=neutral_point(C, profile, wl),
    )
