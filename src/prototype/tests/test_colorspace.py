import numpy as np
import pytest

from anivision import colorspace as cs


def test_srgb_known_values():
    assert cs.srgb_to_linear(0.0) == 0.0
    assert cs.srgb_to_linear(1.0) == pytest.approx(1.0)
    # sRGB 0.5 -> ~0.2140 linear; the toe is linear below 0.04045.
    assert cs.srgb_to_linear(0.5) == pytest.approx(0.21404114, abs=1e-6)
    assert cs.srgb_to_linear(0.04) == pytest.approx(0.04 / 12.92)
    assert cs.linear_to_srgb(0.0031308) == pytest.approx(0.04045, abs=1e-5)


def test_srgb_roundtrip_float():
    x = np.linspace(-0.5, 1.5, 2001)  # includes out-of-range values
    np.testing.assert_allclose(cs.linear_to_srgb(cs.srgb_to_linear(x)), x, atol=1e-12)
    np.testing.assert_allclose(cs.srgb_to_linear(cs.linear_to_srgb(x)), x, atol=1e-12)


def test_srgb_is_monotonic():
    x = np.linspace(0, 1, 1001)
    assert np.all(np.diff(cs.srgb_to_linear(x)) > 0)


def test_uint8_roundtrip_all_values():
    img = np.arange(256, dtype=np.uint8)
    lin = cs.srgb_to_linear(cs.to_float(img))
    back = cs.from_float(cs.linear_to_srgb(lin), np.uint8)
    np.testing.assert_array_equal(back, img)


def test_uint16_roundtrip():
    img = np.arange(0, 65536, 257, dtype=np.uint16)
    np.testing.assert_array_equal(cs.from_float(cs.to_float(img), np.uint16), img)


def test_to_float_ranges_and_dtype_errors():
    assert cs.to_float(np.uint8(255)) == 1.0
    assert cs.to_float(np.uint16(65535)) == 1.0
    assert cs.to_float(np.float32(0.25)) == 0.25
    with pytest.raises(TypeError):
        cs.to_float(np.array([1, 2, 3], dtype=np.int32))


def test_from_float_clips():
    out = cs.from_float(np.array([-0.2, 0.0, 0.5, 1.0, 1.7]), np.uint8)
    np.testing.assert_array_equal(out, [0, 0, 128, 255, 255])


def test_xyz_roundtrip_and_white():
    rgb = np.random.default_rng(0).random((10, 4, 3))
    np.testing.assert_allclose(cs.xyz_to_linear_rgb(cs.linear_rgb_to_xyz(rgb)), rgb, atol=1e-12)
    # White maps to D65 with Y = 1 and back to (1, 1, 1).
    white = cs.linear_rgb_to_xyz(np.ones(3))
    assert white[1] == pytest.approx(1.0, abs=1e-6)
    np.testing.assert_allclose(white, cs.D65_WHITE_XYZ)
    np.testing.assert_allclose(white / white[1], [0.95047, 1.0, 1.08883], atol=1e-4)
