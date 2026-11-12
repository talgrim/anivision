import math

import numpy as np
import pytest

from anivision.acuity import (
    DEFAULT_EXAGGERATION,
    DEFAULT_K,
    MAX_EXAGGERATION,
    MTF_AT_CUTOFF,
    AcuityParams,
    blur_for_image,
    gaussian_blur,
    sigma_px,
)
from anivision.colorspace import linear_to_srgb
from anivision.simulate import simulate

IDENTITY = np.eye(3)


def test_k_follows_the_mtf_criterion_and_baseline_sigma_is_a_pixel_or_two():
    assert DEFAULT_K == pytest.approx(2 * math.sqrt(2 * math.log(1 / MTF_AT_CUTOFF)) / math.pi)
    assert DEFAULT_K == pytest.approx(1.366, abs=1e-3)
    # 1920 px over 70 deg = 27.4 px/deg; MAR 3.75 arcmin = 0.0625 deg.
    assert sigma_px(1920) == pytest.approx(1.17, abs=0.01)  # "a pixel or two", as Python.md expects
    assert sigma_px(640) < 0.5  # an unexaggerated preview-sized frame is essentially unblurred


def test_sigma_scaling_laws():
    base = sigma_px(1000)
    assert sigma_px(2000) == pytest.approx(2 * base)  # more pixels per degree
    assert sigma_px(1000, AcuityParams(acuity_ratio=7.5)) == pytest.approx(2 * base)
    assert sigma_px(1000, AcuityParams(hfov_deg=35.0)) == pytest.approx(2 * base)
    assert sigma_px(1000, AcuityParams(exaggeration=4.0)) == pytest.approx(4 * base)
    assert sigma_px(1000, AcuityParams(exaggeration=0.0)) == 0.0
    assert AcuityParams(acuity_ratio=3.75).cutoff_cpd == pytest.approx(8.0)  # dog ~8 cpd vs human 30


@pytest.mark.parametrize("bad", [dict(acuity_ratio=0), dict(hfov_deg=-1), dict(exaggeration=-0.1), dict(k=0)])
def test_invalid_params_rejected(bad):
    with pytest.raises(ValueError):
        AcuityParams(**bad)


def test_blur_leaves_constant_images_alone_and_keeps_shape():
    img = np.full((37, 53, 3), 0.42)
    out = gaussian_blur(img, 4.0)
    assert out.shape == img.shape
    np.testing.assert_allclose(out, img, atol=1e-12)  # mirrored edges: no dark border


def test_blur_zero_sigma_is_a_copy_and_validates_shape():
    img = np.random.default_rng(0).random((8, 9, 3))
    out = gaussian_blur(img, 0.0)
    np.testing.assert_array_equal(out, img)
    assert out is not img
    with pytest.raises(ValueError):
        gaussian_blur(img[..., 0], 1.0)


def test_impulse_response_is_a_gaussian_of_the_requested_sigma():
    n, sigma = 161, 3.0
    img = np.zeros((n, n, 1))
    img[n // 2, n // 2, 0] = 1.0
    out = gaussian_blur(img, sigma)[..., 0]
    assert out.sum() == pytest.approx(1.0, abs=1e-6)  # energy preserved
    x = np.arange(n) - n // 2
    row = out.sum(axis=0)
    assert np.sqrt((row * x**2).sum()) == pytest.approx(sigma, rel=0.01)
    assert out.min() > -1e-9  # no ringing


def test_sinusoid_is_attenuated_by_the_gaussian_mtf():
    n, sigma, cycles = 256, 2.5, 20  # f = 20/256 cycles per pixel, integer cycles: no leakage
    x = np.arange(n)
    img = np.tile(0.5 + 0.4 * np.sin(2 * np.pi * cycles * x / n), (n, 1))[..., None]
    out = gaussian_blur(img, sigma)[..., 0]
    f = cycles / n
    expected = math.exp(-2 * math.pi**2 * sigma**2 * f**2)
    centre = out[n // 2, 64:192]  # away from the mirrored edges, where the sinusoid is not continued
    amplitude = (centre.max() - centre.min()) / 2
    assert amplitude / 0.4 == pytest.approx(expected, rel=0.02)


def test_contrast_at_the_dogs_cutoff_frequency_is_the_chosen_mtf():
    width, p = 2048, AcuityParams()
    s = sigma_px(width, p)
    f_px = p.cutoff_cpd / (width / p.hfov_deg)  # cycles per pixel at 8 cpd
    assert math.exp(-2 * math.pi**2 * s**2 * f_px**2) == pytest.approx(MTF_AT_CUTOFF, rel=1e-6)


def test_blur_runs_in_linear_light():
    # 1-pixel black/white checker: heavy blur averages to 0.5 in *linear* light = sRGB 0.735.
    img = (np.indices((64, 64)).sum(axis=0) % 2).astype(float)[..., None].repeat(3, axis=2)
    res = simulate(np.clip(linear_to_srgb(img), 0, 1), IDENTITY, acuity=AcuityParams(exaggeration=50.0))
    assert res.image.mean() == pytest.approx(0.735, abs=0.01)  # float in, float out; gamma-space blur gives 0.5


def test_blur_for_image_uses_the_long_edge():
    wide, tall = np.zeros((100, 400, 3)), np.zeros((400, 100, 3))
    assert blur_for_image(wide)[1] == pytest.approx(blur_for_image(tall)[1])
    assert blur_for_image(wide)[1] == pytest.approx(sigma_px(400))


def test_simulate_reports_sigma_and_blurs_detail():
    img = np.zeros((120, 200, 3), dtype=np.uint8)
    img[:, ::2] = 255  # vertical 1-px stripes
    sharp = simulate(img, IDENTITY)
    soft = simulate(img, IDENTITY, acuity=AcuityParams(exaggeration=8.0))
    assert sharp.blur_sigma_px == 0.0 and soft.blur_sigma_px > 0.0
    assert soft.image.std() < 0.2 * sharp.image.std()


def test_recommended_exaggeration_range():
    assert AcuityParams().exaggeration == DEFAULT_EXAGGERATION == 1.0  # the physical baseline
    assert MAX_EXAGGERATION == 8.0
    # At the maximum, a 1920 px frame is blurred with sigma ~9 px, a 640 px preview with ~3 px.
    top = AcuityParams(exaggeration=MAX_EXAGGERATION)
    assert sigma_px(1920, top) == pytest.approx(9.4, abs=0.1)
    assert sigma_px(640, top) == pytest.approx(3.1, abs=0.1)
