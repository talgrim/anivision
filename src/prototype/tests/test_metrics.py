import numpy as np
import pytest

from anivision.metrics import (
    chroma,
    delta_e_2000,
    frobenius,
    image_delta_e,
    linear_rgb_to_lab,
    srgb_to_lab,
)

# Sharma, Wu & Dalal (2005), Table 1: 34 test pairs (Lab1, Lab2, expected CIEDE2000).
SHARMA = [
    ((50, 2.6772, -79.7751), (50, 0, -82.7485), 2.0425),
    ((50, 3.1571, -77.2803), (50, 0, -82.7485), 2.8615),
    ((50, 2.8361, -74.0200), (50, 0, -82.7485), 3.4412),
    ((50, -1.3802, -84.2814), (50, 0, -82.7485), 1.0),
    ((50, -1.1848, -84.8006), (50, 0, -82.7485), 1.0),
    ((50, -0.9009, -85.5211), (50, 0, -82.7485), 1.0),
    ((50, 0, 0), (50, -1, 2), 2.3669),
    ((50, -1, 2), (50, 0, 0), 2.3669),
    ((50, 2.49, -0.001), (50, -2.49, 0.0009), 7.1792),
    ((50, 2.49, -0.001), (50, -2.49, 0.0010), 7.1792),
    ((50, 2.49, -0.001), (50, -2.49, 0.0011), 7.2195),
    ((50, 2.49, -0.001), (50, -2.49, 0.0012), 7.2195),
    ((50, -0.001, 2.49), (50, 0.0009, -2.49), 4.8045),
    ((50, -0.001, 2.49), (50, 0.0010, -2.49), 4.8045),
    ((50, -0.001, 2.49), (50, 0.0011, -2.49), 4.7461),
    ((50, 2.5, 0), (50, 0, -2.5), 4.3065),
    ((50, 2.5, 0), (73, 25, -18), 27.1492),
    ((50, 2.5, 0), (61, -5, 29), 22.8977),
    ((50, 2.5, 0), (56, -27, -3), 31.9030),
    ((50, 2.5, 0), (58, 24, 15), 19.4535),
    ((50, 2.5, 0), (50, 3.1736, 0.5854), 1.0),
    ((50, 2.5, 0), (50, 3.2972, 0), 1.0),
    ((50, 2.5, 0), (50, 1.8634, 0.5757), 1.0),
    ((50, 2.5, 0), (50, 3.2592, 0.3350), 1.0),
    ((60.2574, -34.0099, 36.2677), (60.4626, -34.1751, 39.4387), 1.2644),
    ((63.0109, -31.0961, -5.8663), (62.8187, -29.7946, -4.0864), 1.2630),
    ((61.2901, 3.7196, -5.3901), (61.4292, 2.2480, -4.9620), 1.8731),
    ((35.0831, -44.1164, 3.7933), (35.0232, -40.0716, 1.5901), 1.8645),
    ((22.7233, 20.0904, -46.6940), (23.0331, 14.9730, -42.5619), 2.0373),
    ((36.4612, 47.8580, 18.3852), (36.2715, 50.5065, 21.2231), 1.4146),
    ((90.8027, -2.0831, 1.4410), (91.1528, -1.6435, 0.0447), 1.4441),
    ((90.9257, -0.5406, -0.9208), (88.6381, -0.8985, -0.7239), 1.5381),
    ((6.7747, -0.2908, -2.4247), (5.8714, -0.0985, -2.2286), 0.6377),
    ((2.0776, 0.0795, -1.1350), (0.9033, -0.0636, -0.5514), 0.9082),
]


def test_ciede2000_matches_sharma_test_pairs():
    a = np.array([p[0] for p in SHARMA], dtype=float)
    b = np.array([p[1] for p in SHARMA], dtype=float)
    expected = np.array([p[2] for p in SHARMA])
    np.testing.assert_allclose(delta_e_2000(a, b), expected, atol=6e-5)  # table has 4 decimals


def test_ciede2000_is_symmetric_zero_on_identity_and_handles_broadcasting():
    rng = np.random.default_rng(0)
    a = np.stack([rng.uniform(5, 95, 200), rng.uniform(-80, 80, 200), rng.uniform(-80, 80, 200)], -1)
    b = a[::-1]
    np.testing.assert_allclose(delta_e_2000(a, b), delta_e_2000(b, a), atol=1e-10)
    np.testing.assert_array_equal(delta_e_2000(a, a), 0.0)
    assert delta_e_2000(a, np.array([50.0, 0.0, 0.0])).shape == (200,)  # broadcast one colour


def test_ciede2000_neutral_axis_has_no_nans():
    gray_a = np.array([[50.0, 0.0, 0.0], [0.0, 0.0, 0.0]])
    gray_b = np.array([[60.0, 0.0, 0.0], [100.0, 0.0, 0.0]])
    d = delta_e_2000(gray_a, gray_b)
    assert np.all(np.isfinite(d)) and np.all(d > 0)


def test_lab_of_white_black_and_primaries():
    np.testing.assert_allclose(linear_rgb_to_lab(np.ones(3)), [100.0, 0.0, 0.0], atol=1e-6)
    np.testing.assert_allclose(linear_rgb_to_lab(np.zeros(3)), [0.0, 0.0, 0.0], atol=1e-9)
    # Standard sRGB D65 values (e.g. Lindbloom's calculator), to within the matrix rounding.
    np.testing.assert_allclose(srgb_to_lab(np.array([255, 0, 0], dtype=np.uint8)), [53.24, 80.09, 67.20], atol=0.1)
    np.testing.assert_allclose(srgb_to_lab(np.array([0, 255, 0], dtype=np.uint8)), [87.73, -86.18, 83.18], atol=0.1)
    np.testing.assert_allclose(srgb_to_lab(np.array([0, 0, 255], dtype=np.uint8)), [32.30, 79.19, -107.86], atol=0.1)


def test_chroma_and_gray_has_zero_chroma():
    gray = srgb_to_lab(np.array([[10, 10, 10], [128, 128, 128], [250, 250, 250]], dtype=np.uint8))
    np.testing.assert_allclose(chroma(gray), 0.0, atol=1e-3)
    assert chroma(np.array([0.0, 3.0, 4.0])) == pytest.approx(5.0)


def test_image_delta_e_and_frobenius():
    img = np.random.default_rng(1).integers(0, 256, (6, 5, 3), dtype=np.uint8)
    np.testing.assert_array_equal(image_delta_e(img, img), 0.0)
    assert image_delta_e(img, 255 - img).mean() > 10
    with pytest.raises(ValueError):
        image_delta_e(img, img[:, :4])
    assert frobenius(np.eye(3), np.zeros((3, 3))) == pytest.approx(np.sqrt(3))
