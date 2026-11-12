import numpy as np
import pytest

from anivision import derive as dv
from anivision.profiles import DOG
from anivision.variants import DOG_V2, get_variant

W = np.ones(3)


@pytest.fixture(scope="module")
def t():
    return dv.derive()


def test_white_is_a_fixed_point(t):
    np.testing.assert_allclose(t.matrix @ W, W, atol=1e-12)


def test_matrix_has_rank_two(t):
    assert abs(np.linalg.det(t.matrix)) < 1e-12
    assert np.linalg.matrix_rank(t.matrix, tol=1e-9) == 2


def test_matrix_is_idempotent(t):
    np.testing.assert_allclose(t.matrix @ t.matrix, t.matrix, atol=1e-12)


@pytest.mark.parametrize("scale", [2.5, 0.01, -0.7])
def test_anchor_scale_invariance(t, scale):
    scaled = dv.derive(anchor=t.anchor_rgb * scale)
    np.testing.assert_allclose(scaled.matrix, t.matrix, atol=1e-10)


def test_output_lies_on_the_white_anchor_plane(t):
    rng = np.random.default_rng(0)
    out = rng.random((50, 3)) @ t.matrix.T
    plane_normal = np.cross(W, t.anchor_rgb)
    np.testing.assert_allclose(out @ plane_normal, 0.0, atol=1e-12)


def test_colours_differing_only_along_kernel_of_C_collapse(t):
    C = t.cone_catch
    kernel = np.linalg.svd(C)[2][-1]  # direction of rank-deficiency: the unseen axis
    np.testing.assert_allclose(C @ kernel, 0.0, atol=1e-12)
    np.testing.assert_allclose(t.matrix @ kernel, 0.0, atol=1e-12)
    x = np.array([0.3, 0.5, 0.2])
    np.testing.assert_allclose(t.matrix @ (x + 0.4 * kernel), t.matrix @ x, atol=1e-12)


def test_projection_preserves_cone_signals(t):
    """The dog cannot tell M·x from x: C·M = C."""
    np.testing.assert_allclose(t.cone_catch @ t.matrix, t.cone_catch, atol=1e-12)


def test_neutral_point_near_480nm(t):
    assert abs(t.neutral_point_nm - DOG.expected_neutral_nm) <= 10.0


def test_neutral_point_is_where_ratio_matches_white(t):
    s, l = dv.cone_sensitivities(DOG, np.array([t.neutral_point_nm]))
    s_w, l_w = t.cone_catch @ W
    assert s[0] / l[0] == pytest.approx(s_w / l_w, rel=5e-3)


def test_cone_catch_is_nonnegative_s_sees_blue_and_l_sees_green_most(t):
    C = t.cone_catch
    assert np.all(C >= 0)
    assert np.argmax(C[0]) == 2  # S cone: blue primary
    assert np.argmax(C[1]) == 1  # L cone: green primary


def test_anchor_choice_changes_matrix_modestly_and_575_is_valid():
    base = dv.derive(anchor=475.0).matrix
    other = dv.derive(anchor=575.0).matrix
    assert 0 < np.linalg.norm(other - base) < 0.5
    np.testing.assert_allclose(other @ W, W, atol=1e-12)


def test_anchor_the_dog_cannot_tell_from_white_is_rejected(t):
    kernel = np.linalg.svd(t.cone_catch)[2][-1]
    for bad in (W, 2 * W, W + 0.3 * kernel):  # same cone signals ratio as white
        with pytest.raises(ValueError):
            dv.derive(anchor=bad)


def test_monochromatic_rgb_has_negative_red_at_475():
    a = dv.monochromatic_rgb(475.0)
    assert a[0] < 0 < a[2]  # outside the sRGB gamut: fine, it only defines the plane


def test_v2_is_registered_as_a_variant():
    assert get_variant("v2") is DOG_V2
    np.testing.assert_allclose(DOG_V2, dv.derive().matrix)


def test_shared_variant_matrices_are_read_only():
    for name in ("v0", "v2"):
        matrix = get_variant(name)
        assert not matrix.flags.writeable
        with pytest.raises(ValueError):
            matrix[0, 0] = 0.0
