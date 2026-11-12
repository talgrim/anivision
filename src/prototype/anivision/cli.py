"""Command line: ``python -m anivision in.jpg out.jpg [--variant v0|v1|v2|cat]`` (default v2)."""

from __future__ import annotations

import argparse
import sys

import numpy as np
from PIL import Image, ImageOps

from .acuity import DEFAULT_EXAGGERATION, MAX_EXAGGERATION, AcuityParams
from .gamut import GAMUT_POLICIES
from .simulate import simulate
from .variants import VARIANT_IDS, acuity_ratio_for, get_variant


def load_rgb(path: str) -> np.ndarray:
    """Load an image as a uint8 ``(H, W, 3)`` sRGB array (EXIF rotation applied)."""
    with Image.open(path) as im:
        im = ImageOps.exif_transpose(im)
        return np.asarray(im.convert("RGB"))


def save_rgb(path: str, arr: np.ndarray) -> None:
    Image.fromarray(arr).save(path, quality=95)


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="python -m anivision",
        description=(
            "Simulate what a dog's or cat's cones capture. This is an approximation, "
            "not what the animal subjectively experiences."
        ),
    )
    p.add_argument("input", help="input image")
    p.add_argument("output", help="output image (format from extension)")
    p.add_argument(
        "--variant",
        default="v2",
        choices=list(VARIANT_IDS),
        help="colour transform variant (default: %(default)s)",
    )
    p.add_argument("--acuity", action="store_true", help="also apply the animal's acuity blur")
    p.add_argument(
        "--exaggeration",
        type=float,
        default=DEFAULT_EXAGGERATION,
        help=f"multiplier on the physical acuity blur width (with --acuity; default %(default)s, "
        f"suggested maximum {MAX_EXAGGERATION:g})",
    )
    p.add_argument("--hfov", type=float, default=70.0, help="camera field of view along the long edge, degrees")
    p.add_argument(
        "--acuity-ratio",
        type=float,
        default=None,
        help="animal/human acuity ratio (dog 20/75 -> 3.75); default: the chosen variant's animal",
    )
    p.add_argument(
        "--gamut",
        default="clip",
        choices=list(GAMUT_POLICIES),
        help="how out-of-gamut colours are fitted (default: %(default)s)",
    )
    p.add_argument(
        "--shadow-lift",
        type=float,
        default=1.0,
        metavar="GAMMA",
        help="artistic shadow lift: luminance gamma in (0, 1], 1 = off (suggested 0.85, strongest 0.7)",
    )
    return p


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        matrix = get_variant(args.variant)
    except ImportError as exc:  # v1 without colour-science
        parser.error(str(exc))
    try:
        image = load_rgb(args.input)
    except FileNotFoundError:
        parser.error(f"input image not found: {args.input}")
    except OSError as exc:  # unreadable file or a format Pillow cannot identify
        parser.error(f"cannot read {args.input} as an image: {exc}")
    try:
        ratio = args.acuity_ratio if args.acuity_ratio is not None else acuity_ratio_for(args.variant)
        acuity = AcuityParams(ratio, args.hfov, args.exaggeration) if args.acuity else None
        result = simulate(
            image,
            matrix,
            acuity=acuity,
            shadow_gamma=args.shadow_lift,
            gamut=args.gamut,
        )
    except ValueError as exc:
        parser.error(str(exc))
    try:
        save_rgb(args.output, result.image)
    except (OSError, ValueError) as exc:  # missing folder, unknown extension, no permission
        parser.error(f"cannot write {args.output}: {exc}")
    fit = "clipped" if args.gamut == "clip" else "desaturated to fit"
    blur = f", blur sigma {result.blur_sigma_px:.2f} px" if args.acuity else ""
    print(
        f"{args.variant}: {result.out_of_gamut_pixels}/{result.total_pixels} pixels "
        f"({result.out_of_gamut_fraction:.2%}) out of gamut, {fit}{blur}",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
