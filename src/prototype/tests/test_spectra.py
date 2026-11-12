import numpy as np
import pytest

from anivision import spectra as sp
from anivision.colorspace import D65_WHITE_XYZ


@pytest.mark.parametrize("lmax", [429.0, 450.0, 500.0, 530.0, 555.0])
def test_nomogram_peaks_at_requested_lmax_and_is_normalised(lmax):
    fine = np.arange(300.0, 750.0, 0.1)
    y = sp.govardovskii_a1(fine, lmax)
    assert abs(fine[np.argmax(y)] - lmax) <= 1.0
    assert y.max() == pytest.approx(1.0, abs=1e-9)
    assert y.min() >= 0.0
    # Independent of the sampling grid: the 1 nm grid sees the same curve.
    y1 = sp.govardovskii_a1(sp.WAVELENGTHS, lmax)
    assert y1.max() <= 1.0 + 1e-9


@pytest.mark.parametrize("lmax", [429.0, 555.0])
def test_nomogram_is_smooth_and_falls_monotonically_after_the_peak(lmax):
    y = sp.govardovskii_a1(sp.WAVELENGTHS, lmax)
    assert np.abs(np.diff(y, 2)).max() < 0.01  # no kinks on a 1 nm grid
    peak = int(np.argmax(y))
    assert np.all(np.diff(y[peak:]) <= 1e-9)


@pytest.mark.parametrize("lmax", [429.0, 555.0])
def test_alpha_band_alone_is_unimodal(lmax):
    y = sp.govardovskii_a1(sp.WAVELENGTHS, lmax, include_beta=False)
    peak = int(np.argmax(y))
    assert np.all(np.diff(y[: peak + 1]) >= -1e-9)
    assert np.all(np.diff(y[peak:]) <= 1e-9)


def test_beta_band_only_adds_sensitivity():
    wl = sp.WAVELENGTHS
    with_beta = sp.govardovskii_a1(wl, 555.0)
    alpha_only = sp.govardovskii_a1(wl, 555.0, include_beta=False)
    # Each curve is normalised to its own peak, so allow a tiny rescaling.
    assert np.all(with_beta >= alpha_only - 1e-3)
    assert with_beta[0] > alpha_only[0] + 0.1  # the broad beta bump shows at 380 nm


@pytest.mark.parametrize(
    "wl, xyz",
    [  # tabulated CIE 1931 2 degree values
        (450, (0.3362, 0.0380, 1.7721)),
        (500, (0.0049, 0.3230, 0.2720)),
        (555, (0.5121, 1.0000, 0.0057)),
        (600, (1.0622, 0.6310, 0.0008)),
        (650, (0.2835, 0.1070, 0.0000)),
    ],
)
def test_cie_cmf_matches_tabulated_values(wl, xyz):
    got = sp.cie_cmf(np.array([float(wl)]))[0]
    np.testing.assert_allclose(got, xyz, atol=0.02)


def _white_xyz(spds, wl):
    return sp.cie_cmf(wl).T @ spds.sum(axis=0) * np.mean(np.diff(wl))


@pytest.mark.parametrize("wl", [sp.WAVELENGTHS, np.arange(380.0, 721.0, 5.0)])
def test_primaries_sum_to_d65_white(wl):
    spds = sp.primary_spds(wl)
    assert spds.shape == (3, len(wl))
    assert np.all(spds >= 0)
    np.testing.assert_allclose(_white_xyz(spds, wl), D65_WHITE_XYZ, rtol=1e-9)


def test_primary_peaks_follow_centers_and_sigma_is_configurable():
    spds = sp.primary_spds(centers=(620.0, 540.0, 450.0), sigma=(15.0, 25.0, 10.0))
    peaks = sp.WAVELENGTHS[np.argmax(spds, axis=1)]
    np.testing.assert_allclose(peaks, [620, 540, 450], atol=1.0)
    np.testing.assert_allclose(_white_xyz(spds, sp.WAVELENGTHS), D65_WHITE_XYZ, rtol=1e-9)
