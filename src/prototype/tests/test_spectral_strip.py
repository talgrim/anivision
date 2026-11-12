import numpy as np
import pytest

from anivision.derive import derive
from anivision.metrics import linear_rgb_to_lab
from anivision.spectral_strip import (
    STRIP_WAVELENGTHS,
    display_stimulus,
    neutral_crossing_nm,
    render_strip,
    true_spectrum_strip,
)
from anivision.variants import DEUTERANOPIA_VIENOT_1999 as V0
from anivision.variants import DOG_V2 as V2


def b_star(strip, nm):
    return linear_rgb_to_lab(strip)[int(nm - STRIP_WAVELENGTHS[0])][2]


def test_display_stimulus_is_in_gamut_and_normalised():
    rgb = display_stimulus()
    assert rgb.shape == (len(STRIP_WAVELENGTHS), 3)
    assert rgb.min() >= 0.0 and rgb.max() == pytest.approx(1.0)
    np.testing.assert_allclose(rgb.min(axis=1), 0.0, atol=1e-12)  # least white added: one channel is 0
    np.testing.assert_allclose(rgb.max(axis=1), 1.0, atol=1e-12)
    np.testing.assert_array_equal(render_strip(None), rgb)


def test_true_spectrum_strip_crosses_gray_at_the_analytic_neutral_point():
    d = derive()
    crossing = neutral_crossing_nm(true_spectrum_strip(d))
    assert crossing == pytest.approx(d.neutral_point_nm, abs=0.5)  # measured 486.85 vs 486.70
    assert abs(crossing - 480.0) <= 10.0  # the project's target, 480 +/- 10 nm


def test_true_spectrum_strip_goes_blue_then_gray_then_yellow():
    strip = true_spectrum_strip(derive())
    assert b_star(strip, 440) < -40 and b_star(strip, 470) < -40  # blue side
    assert b_star(strip, 520) > 40 and b_star(strip, 580) > 40  # yellow side


@pytest.mark.parametrize("name, matrix", [("v0", V0), ("v2", V2)])
def test_strip_through_display_goes_blue_then_gray_then_yellow(name, matrix):
    strip = render_strip(matrix)
    assert b_star(strip, 440) < -40 and b_star(strip, 470) < -40
    assert b_star(strip, 560) > 40 and b_star(strip, 600) > 40
    # Measured 2026-11-12: V0 499.9 nm, V2 498.0 nm (see NOTES.md).
    assert 494.0 <= neutral_crossing_nm(strip) <= 504.0


def test_display_crossing_sits_above_the_analytic_neutral_point():
    # The display model gives narrow spectral lines the cone catch of broad Gaussian primaries,
    # which moves the visible gray crossing about 10 nm toward longer wavelengths.
    d = derive()
    shift = neutral_crossing_nm(render_strip(d.matrix)) - d.neutral_point_nm
    assert 5.0 < shift < 20.0
