import numpy as np
import pytest
from PIL import Image

from anivision import cli
from anivision.colorspace import linear_to_srgb, srgb_to_linear
from anivision.simulate import apply_matrix, count_out_of_gamut, simulate
from anivision.variants import DEUTERANOPIA_VIENOT_1999 as V0
from anivision.variants import get_variant


def px(*rgb):
    """A 1x1 uint8 image."""
    return np.array([[rgb]], dtype=np.uint8)


def test_identity_roundtrip_uint8_and_dtype():
    v = np.arange(256, dtype=np.uint8)
    img = np.stack([v, v[::-1], np.roll(v, 50)], axis=-1).reshape(16, 16, 3)
    res = simulate(img, np.eye(3))
    assert res.image.dtype == np.uint8
    np.testing.assert_array_equal(res.image, img)
    assert res.out_of_gamut_pixels == 0
    assert res.total_pixels == 256


def test_uint16_and_float_dtypes():
    img16 = np.full((2, 2, 3), 30000, dtype=np.uint16)
    assert simulate(img16, np.eye(3)).image.dtype == np.uint16
    imgf = np.full((2, 2, 3), 0.4, dtype=np.float32)
    out = simulate(imgf, np.eye(3)).image
    assert out.dtype == np.float64
    np.testing.assert_allclose(out, 0.4, atol=1e-12)


def test_matrix_must_be_3x3():
    with pytest.raises(ValueError):
        apply_matrix(np.zeros((1, 3)), np.eye(2))


def test_count_out_of_gamut():
    lin = np.array([[[0.5, 0.5, 0.5], [0.9, 0.9, 0.9]]])
    assert count_out_of_gamut(apply_matrix(lin, 2 * np.eye(3))) == 1  # only 0.9 -> 1.8
    assert count_out_of_gamut(apply_matrix(lin, -np.eye(3))) == 2
    assert count_out_of_gamut(lin) == 0


def test_clipping_policy():
    # Red maps to a slightly negative blue channel under V0: clipped and counted.
    res = simulate(px(255, 0, 0), V0)
    assert res.out_of_gamut_pixels == 1
    assert res.image.min() >= 0 and res.image[0, 0, 2] == 0
    # Green stays inside the gamut.
    assert simulate(px(0, 255, 0), V0).out_of_gamut_pixels == 0


def test_v0_matrix_properties():
    np.testing.assert_allclose(V0.sum(axis=1), 1.0, atol=1e-12)  # M @ white == white
    assert abs(np.linalg.det(V0)) < 1e-9  # rank 2
    np.testing.assert_allclose(V0 @ V0, V0, atol=1e-12)  # projection
    assert get_variant("V0") is V0
    with pytest.raises(ValueError):
        get_variant("nope")


def test_v0_matches_vienot_1999_derivation():
    # Matrices as printed in Vienot, Brettel & Mollon (1999), Eq. 4-5.
    rgb_to_lms = np.array(
        [
            [17.8824, 43.5161, 4.11935],
            [3.45565, 27.1554, 3.86714],
            [0.0299566, 0.184309, 1.46709],
        ]
    )
    deuteranope = np.array([[1.0, 0.0, 0.0], [0.494207, 0.0, 1.24827], [0.0, 0.0, 1.0]])
    rebuilt = np.linalg.inv(rgb_to_lms) @ deuteranope @ rgb_to_lms
    np.testing.assert_allclose(V0, rebuilt, atol=1e-5)  # V0 keeps five decimals


def test_v0_keeps_grays_neutral():
    g = np.arange(256, dtype=np.uint8)
    gray = np.stack([g, g, g], axis=-1)[None]
    res = simulate(gray, V0)
    assert np.abs(res.image.astype(int) - gray.astype(int)).max() <= 1
    assert res.out_of_gamut_pixels == 0


def test_v0_collapses_red_green_but_keeps_blue_yellow():
    colors = np.array(
        [[(200, 30, 30), (60, 150, 40), (230, 120, 30), (140, 85, 40)]], dtype=np.uint8
    )
    out = simulate(colors, V0).image
    np.testing.assert_array_equal(out[..., 0], out[..., 1])  # no red-green axis left

    blue_yellow = np.array([[(0, 70, 200), (240, 210, 0)]], dtype=np.uint8)
    b, y = simulate(blue_yellow, V0).image[0].astype(float) / 255
    assert np.linalg.norm(b - y) > 0.5  # still clearly different


def test_matrix_applied_in_linear_light():
    # A uniform 0.5 scale in linear light is NOT 0.5 in sRGB code values.
    res = simulate(px(200, 200, 200), 0.5 * np.eye(3))
    expected = linear_to_srgb(0.5 * srgb_to_linear(200 / 255)) * 255
    assert abs(int(res.image[0, 0, 0]) - round(float(expected))) <= 1


def test_cli_end_to_end(tmp_path):
    src, dst = tmp_path / "in.png", tmp_path / "out.png"
    Image.fromarray(np.random.default_rng(1).integers(0, 256, (8, 12, 3), dtype=np.uint8)).save(src)
    assert cli.main([str(src), str(dst), "--variant", "v0"]) == 0
    with Image.open(dst) as im:
        assert im.size == (12, 8)
        assert im.mode == "RGB"


def test_cli_defaults_to_the_shipped_variant():
    from anivision.variants import DOG_V2

    args = cli.build_parser().parse_args(["in.png", "out.png"])
    assert args.variant == "v2"
    np.testing.assert_array_equal(get_variant(args.variant), DOG_V2)


def test_cli_cat_variant_uses_the_cat_acuity_by_default(tmp_path, capsys):
    src, dst = tmp_path / "in.png", tmp_path / "out.png"
    Image.fromarray(np.random.default_rng(2).integers(0, 256, (40, 60, 3), dtype=np.uint8)).save(src)

    def blur_sigma(*extra):
        assert cli.main([str(src), str(dst), "--acuity", *extra]) == 0
        return float(capsys.readouterr().err.split("blur sigma ")[1].split(" px")[0])

    cat = blur_sigma("--variant", "cat")
    assert cat == pytest.approx(blur_sigma("--variant", "v2", "--acuity-ratio", "3.5"), abs=0.01)
    assert cat < blur_sigma("--variant", "v2")  # the cat resolves slightly finer detail than the dog


def test_cli_acuity_gamut_and_lift_flags(tmp_path, capsys):
    src, dst = tmp_path / "in.png", tmp_path / "out.png"
    Image.fromarray(np.random.default_rng(0).integers(0, 256, (40, 60, 3), dtype=np.uint8)).save(src)
    args = [str(src), str(dst), "--variant", "v2", "--acuity", "--exaggeration", "3",
            "--gamut", "desaturate", "--shadow-lift", "0.8"]
    assert cli.main(args) == 0
    err = capsys.readouterr().err
    assert "blur sigma" in err and "desaturated" in err
    assert dst.exists()


def test_cli_rejects_bad_parameters(tmp_path):
    src = tmp_path / "in.png"
    Image.fromarray(np.zeros((4, 4, 3), dtype=np.uint8)).save(src)
    with pytest.raises(SystemExit) as exc:
        cli.main([str(src), str(tmp_path / "o.png"), "--shadow-lift", "1.5"])
    assert exc.value.code == 2
    with pytest.raises(SystemExit):
        cli.main([str(src), str(tmp_path / "o.png"), "--acuity", "--hfov", "-5"])


def test_cli_reports_a_missing_input_without_a_traceback(tmp_path, capsys):
    with pytest.raises(SystemExit) as exc:
        cli.main([str(tmp_path / "missing.png"), str(tmp_path / "o.png")])
    assert exc.value.code == 2
    assert "not found" in capsys.readouterr().err


def test_cli_reports_a_file_that_is_not_an_image(tmp_path, capsys):
    src = tmp_path / "notes.png"
    src.write_text("this is not an image")
    with pytest.raises(SystemExit) as exc:
        cli.main([str(src), str(tmp_path / "o.png")])
    assert exc.value.code == 2
    assert "cannot read" in capsys.readouterr().err


def test_cli_reports_an_unwritable_output(tmp_path, capsys):
    src = tmp_path / "in.png"
    Image.fromarray(np.zeros((4, 4, 3), dtype=np.uint8)).save(src)
    with pytest.raises(SystemExit) as exc:
        cli.main([str(src), str(tmp_path / "no-such-folder" / "o.png")])
    assert exc.value.code == 2
    assert "cannot write" in capsys.readouterr().err
