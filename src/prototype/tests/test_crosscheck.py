"""V1 cross-check against the optional colour-science package.

Skipped automatically when colour-science is not installed.
"""

import sys

import numpy as np
import pytest

from anivision import cli
from anivision.colorspace import (
    D65_WHITE_XYZ,
    SRGB_TO_XYZ,
    linear_to_srgb,
    srgb_to_linear,
)
from anivision.spectra import WAVELENGTHS, cie_cmf
from anivision.variants import DEUTERANOPIA_VIENOT_1999 as V0
from anivision.variants import DOG_V2 as V2
from anivision.variants import get_variant, machado_deuteranopia

colour = pytest.importorskip("colour")


def frob(a, b):
    return float(np.linalg.norm(a - b))


def test_v1_is_machado_deuteranopia_and_keeps_grays():
    v1 = get_variant("V1")
    assert v1 is machado_deuteranopia()
    np.testing.assert_allclose(
        v1[0], [0.367322, 0.860646, -0.227968], atol=1e-6
    )  # published Machado et al. (2009) severity-1.0 row
    np.testing.assert_allclose(v1.sum(axis=1), 1.0, atol=1e-6)  # M @ white == white
    assert np.linalg.svd(v1)[1][-1] < 1e-3  # numerically rank 2: a dichromat


def test_v1_sits_between_the_variants_closer_to_v2_than_v0():
    v1 = get_variant("v1")
    # Measured 2026-11-12: |V1-V2| = 0.098, |V1-V0| = 0.294, |V0-V2| = 0.327.
    assert frob(v1, V2) < 0.15
    assert 0.2 < frob(v1, V0) < 0.4
    assert frob(v1, V2) < frob(v1, V0)


def test_v1_has_the_negative_red_term_like_v2():
    # The 475 nm anchor gives V2 a negative blue->red coefficient; Machado's matrix has one too.
    assert get_variant("v1")[0, 2] < 0
    assert V2[0, 2] < 0
    assert V0[0, 2] == 0


def test_srgb_transfer_matches_colour():
    x = np.linspace(0.0, 1.0, 257)
    np.testing.assert_allclose(srgb_to_linear(x), colour.models.eotf_sRGB(x), atol=1e-9)
    np.testing.assert_allclose(linear_to_srgb(x), colour.models.eotf_inverse_sRGB(x), atol=1e-9)


def test_srgb_primaries_matrix_and_white_match_colour():
    cs = colour.RGB_COLOURSPACES["sRGB"]
    # colour uses the 4-decimal IEC 61966-2-1 matrix; ours is the unrounded one derived from the
    # chromaticities, so they differ by up to ~2e-4 (e.g. 0.950304 vs 0.9505).
    np.testing.assert_allclose(SRGB_TO_XYZ, cs.matrix_RGB_to_XYZ, atol=3e-4)
    white = colour.xy_to_XYZ(cs.whitepoint)
    # Ours is the conventional sRGB D65 white (Z = 1.08883); xy = (0.3127, 0.3290) gives 1.089058.
    np.testing.assert_allclose(D65_WHITE_XYZ, white, atol=3e-4)


def test_cie_cmf_fit_matches_colour_tables():
    ref = colour.MSDS_CMFS["CIE 1931 2 Degree Standard Observer"]
    wl = WAVELENGTHS
    table = np.asarray(ref.align(colour.SpectralShape(wl[0], wl[-1], 1)).values)
    err = np.abs(cie_cmf(wl) - table).max(axis=0)
    assert np.all(err < 0.03), err  # measured max error: x 0.015, y 0.008, z 0.024


def test_cli_v1_end_to_end(tmp_path):
    from PIL import Image

    src, dst = tmp_path / "in.png", tmp_path / "out.png"
    Image.fromarray(np.full((4, 4, 3), (200, 40, 30), dtype=np.uint8)).save(src)
    assert cli.main([str(src), str(dst), "--variant", "v1"]) == 0
    assert dst.exists()


def test_cli_v1_reports_missing_colour_science(tmp_path, monkeypatch, capsys):
    machado_deuteranopia.cache_clear()
    monkeypatch.setitem(sys.modules, "colour.blindness", None)  # import now raises ImportError
    try:
        with pytest.raises(SystemExit) as exc:
            cli.main(["in.png", str(tmp_path / "out.png"), "--variant", "v1"])
        assert exc.value.code == 2
        assert "colour-science" in capsys.readouterr().err
    finally:
        machado_deuteranopia.cache_clear()
