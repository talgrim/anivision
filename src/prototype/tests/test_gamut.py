import numpy as np
import pytest

from anivision.colorspace import SRGB_TO_XYZ
from anivision.gamut import GAMUT_POLICIES, desaturate_to_gamut, fit_to_gamut
from anivision.lowlight import STRONGEST_GAMMA, SUGGESTED_GAMMA, lift_shadows
from anivision.simulate import simulate
from anivision.variants import DOG_V2

LUMA = SRGB_TO_XYZ[1]
OUT_OF_GAMUT = np.array(
    [[1.2, -0.1, 0.4], [-0.3, 0.2, 1.1], [0.9, 0.9, -0.05], [1.3, 0.6, 0.1], [0.0, 0.5, 1.3]]
)


@pytest.mark.parametrize("policy", GAMUT_POLICIES)
def test_in_gamut_colours_are_untouched(policy):
    rng = np.random.default_rng(0)
    rgb = rng.random((50, 3))
    np.testing.assert_allclose(fit_to_gamut(rgb, policy), rgb, atol=1e-12)
    gray = np.linspace(0, 1, 11)[:, None].repeat(3, axis=1)
    np.testing.assert_allclose(fit_to_gamut(gray, policy), gray, atol=1e-12)


def test_clip_is_a_channel_clamp():
    np.testing.assert_array_equal(fit_to_gamut(OUT_OF_GAMUT, "clip"), np.clip(OUT_OF_GAMUT, 0, 1))


def test_desaturate_fits_the_gamut_and_keeps_luminance():
    out = desaturate_to_gamut(OUT_OF_GAMUT)
    assert out.min() >= 0.0 and out.max() <= 1.0
    y_in = OUT_OF_GAMUT @ LUMA
    ok = (y_in >= 0) & (y_in <= 1)
    assert ok.all()  # these samples have valid luminance
    np.testing.assert_allclose(out @ LUMA, y_in, atol=1e-9)  # luminance preserved


def test_desaturate_only_moves_toward_the_equal_luminance_gray():
    out = desaturate_to_gamut(OUT_OF_GAMUT)
    y = (OUT_OF_GAMUT @ LUMA)[:, None]
    d_in, d_out = OUT_OF_GAMUT - y, out - y
    t = (d_out * d_in).sum(axis=1) / (d_in * d_in).sum(axis=1)
    assert np.all((t >= 0) & (t < 1))  # a pure scaling of the chroma part
    np.testing.assert_allclose(d_out, t[:, None] * d_in, atol=1e-9)  # same direction, so same hue
    # and it is the *largest* fit: some channel now touches 0 or 1
    assert np.all(np.isclose(out.min(axis=1), 0, atol=1e-9) | np.isclose(out.max(axis=1), 1, atol=1e-9))


def test_desaturate_handles_extreme_luminance():
    out = desaturate_to_gamut(np.array([[1.5, 1.4, 1.6], [-0.2, -0.1, -0.3]]))
    np.testing.assert_allclose(out, [[1, 1, 1], [0, 0, 0]], atol=1e-12)


def test_unknown_policy_rejected():
    with pytest.raises(ValueError, match="unknown gamut policy"):
        fit_to_gamut(OUT_OF_GAMUT, "magic")


def test_simulate_gamut_policy_changes_only_out_of_gamut_pixels():
    blue = np.zeros((4, 4, 3), dtype=np.uint8)
    blue[..., 2] = 255  # saturated blue: V2 sends it out of gamut (negative red)
    blue[:2] = (120, 130, 125)  # a gray half that stays in gamut
    clip = simulate(blue, DOG_V2, gamut="clip")
    desat = simulate(blue, DOG_V2, gamut="desaturate")
    assert clip.out_of_gamut_pixels == desat.out_of_gamut_pixels == 8
    np.testing.assert_array_equal(clip.image[:2], desat.image[:2])  # gray half identical
    assert not np.array_equal(clip.image[2:], desat.image[2:])


# ------------------------------------------------------------------ shadow lift
def test_lift_gamma_one_is_identity_and_validates():
    rgb = np.random.default_rng(1).random((20, 3))
    np.testing.assert_array_equal(lift_shadows(rgb, 1.0), rgb)
    for bad in (0.0, -0.5, 1.2):
        with pytest.raises(ValueError):
            lift_shadows(rgb, bad)


def test_lift_raises_gray_by_the_gamma_and_keeps_black_and_white():
    g = np.linspace(0.0, 1.0, 11)
    gray = g[:, None].repeat(3, axis=1)
    out = lift_shadows(gray, 0.5)
    np.testing.assert_allclose(out[:, 0], np.sqrt(g), atol=1e-9)
    assert out[0].tolist() == [0.0, 0.0, 0.0] and out[-1].tolist() == [1.0, 1.0, 1.0]


def test_lift_preserves_colour_ratios_and_only_brightens():
    rgb = np.array([[0.05, 0.02, 0.01], [0.3, 0.1, 0.2], [0.02, 0.04, 0.09]])
    out = lift_shadows(rgb, 0.7)
    assert np.all(out >= rgb)
    np.testing.assert_allclose(out / out[:, [1]], rgb / rgb[:, [1]], rtol=1e-9)  # same hue and saturation


def test_simulate_shadow_lift_brightens_dark_pixels():
    dark = np.full((3, 3, 3), 20, dtype=np.uint8)
    plain = simulate(dark, np.eye(3)).image
    lifted = simulate(dark, np.eye(3), shadow_gamma=0.6).image
    assert lifted.mean() > plain.mean() + 10


def test_recommended_gamma_range_is_a_valid_mild_lift():
    rgb = np.array([[0.02, 0.02, 0.02]])
    for g in (SUGGESTED_GAMMA, STRONGEST_GAMMA):
        assert 0.0 < g < 1.0
        assert lift_shadows(rgb, g)[0, 0] > rgb[0, 0]
    assert STRONGEST_GAMMA < SUGGESTED_GAMMA
