"""Command line: ``python -m anivision in.jpg out.jpg --variant v0``."""

from __future__ import annotations

import argparse
import sys

import numpy as np
from PIL import Image, ImageOps

from .simulate import simulate
from .variants import VARIANTS, get_variant


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
            "Simulate what a dog's cones capture. This is an approximation, "
            "not what a dog subjectively experiences."
        ),
    )
    p.add_argument("input", help="input image")
    p.add_argument("output", help="output image (format from extension)")
    p.add_argument(
        "--variant",
        default="v0",
        choices=sorted(VARIANTS),
        help="colour transform variant (default: %(default)s)",
    )
    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    result = simulate(load_rgb(args.input), get_variant(args.variant))
    save_rgb(args.output, result.image)
    print(
        f"{args.variant}: {result.out_of_gamut_pixels}/{result.total_pixels} pixels "
        f"({result.out_of_gamut_fraction:.2%}) out of gamut, clipped",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
