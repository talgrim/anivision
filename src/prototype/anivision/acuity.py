"""Acuity blur: a Gaussian whose width follows the dog's coarser visual acuity.

Recipe (applied to *linear* RGB):

  f_c      = 30 cycles/deg / acuity_ratio         cutoff frequency (30 cpd is 20/20, 1 arcmin MAR)
  MAR      = acuity_ratio arcmin                   minimum angle of resolution
  sigma    = k * (MAR / 2) * px_per_degree * exaggeration       [pixels]
  px/deg   = long_edge_px / hfov_deg

``k`` makes the blur's contrast transfer fall to ``MTF_AT_CUTOFF`` at the cutoff
frequency. A Gaussian of width sigma has MTF exp(-2 pi^2 sigma^2 f^2); setting it to m at
f = 1/(2 MAR) gives sigma = MAR * sqrt(2 ln(1/m)) / pi, i.e. k = 2 sqrt(2 ln(1/m)) / pi.
With m = 0.1 that is k = 1.366. ``exaggeration`` scales sigma on top of that physical baseline.

The field of view refers to the image's long edge, so portrait and landscape photos from the
same camera get the same blur in pixels per degree.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np

HUMAN_CUTOFF_CPD = 30.0  # 20/20 vision: 1 arcmin MAR = 30 cycles per degree
MTF_AT_CUTOFF = 0.1
DEFAULT_K = 2.0 * math.sqrt(2.0 * math.log(1.0 / MTF_AT_CUTOFF)) / math.pi

# Chosen by eye on the photo set (scripts/acuity_sweep.py, NOTES.md): 1 is the physical baseline,
# which is only a faint softening on a ~2000 px image; 8 is about as far as a scene stays
# recognisable (16 destroys the content).
DEFAULT_EXAGGERATION = 1.0
MAX_EXAGGERATION = 8.0


@dataclass(frozen=True)
class AcuityParams:
    acuity_ratio: float = 3.75  # dog ~20/75 versus human 20/20
    hfov_deg: float = 70.0  # field of view along the long edge (phone main camera)
    exaggeration: float = DEFAULT_EXAGGERATION  # multiplier on the physical sigma
    k: float = DEFAULT_K

    def __post_init__(self) -> None:
        if self.acuity_ratio <= 0 or self.hfov_deg <= 0 or self.exaggeration < 0 or self.k <= 0:
            raise ValueError(f"invalid acuity parameters: {self}")

    @property
    def mar_arcmin(self) -> float:
        return self.acuity_ratio * 1.0

    @property
    def cutoff_cpd(self) -> float:
        return HUMAN_CUTOFF_CPD / self.acuity_ratio


def sigma_px(long_edge_px: float, params: AcuityParams = AcuityParams()) -> float:
    """Gaussian sigma in pixels for an image whose long edge is ``long_edge_px`` wide."""
    px_per_deg = long_edge_px / params.hfov_deg
    mar_deg = params.mar_arcmin / 60.0
    return params.k * (mar_deg / 2.0) * px_per_deg * params.exaggeration


def gaussian_blur(img: np.ndarray, sigma: float) -> np.ndarray:
    """Gaussian blur of a float ``(H, W, C)`` image, exact in the frequency domain.

    Edges are mirrored (``symmetric`` padding by 5 sigma) so no dark borders appear. The result
    is float64 and is not clipped. ``sigma <= 0`` returns a copy.
    """
    arr = np.asarray(img, dtype=np.float64)
    if arr.ndim != 3:
        raise ValueError(f"expected (H, W, C), got shape {arr.shape}")
    if sigma <= 0.0:
        return arr.copy()
    pad = int(math.ceil(5.0 * sigma))
    padded = np.pad(arr, ((pad, pad), (pad, pad), (0, 0)), mode="symmetric")
    hp, wp = padded.shape[:2]
    fy = np.fft.fftfreq(hp)[:, None]
    fx = np.fft.rfftfreq(wp)[None, :]
    transfer = np.exp(-2.0 * math.pi**2 * sigma**2 * (fx**2 + fy**2))
    spectrum = np.fft.rfft2(padded, axes=(0, 1)) * transfer[..., None]
    out = np.fft.irfft2(spectrum, s=(hp, wp), axes=(0, 1))
    return out[pad : pad + arr.shape[0], pad : pad + arr.shape[1]]


def blur_for_image(
    linear_rgb: np.ndarray, params: AcuityParams = AcuityParams()
) -> tuple[np.ndarray, float]:
    """Blur linear RGB with the acuity recipe; returns ``(blurred, sigma_px)``."""
    long_edge = max(linear_rgb.shape[0], linear_rgb.shape[1])
    s = sigma_px(long_edge, params)
    return gaussian_blur(linear_rgb, s), s
