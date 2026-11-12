"""Render the optional shadow lift at several gammas (artistic, not physiological).

Writes out/lowlight_sweep.png. Columns are gamma 1.0 (off), 0.85, 0.7, 0.55; all through V2.

Usage (from src/prototype):  PYTHONPATH=. .venv/bin/python scripts/lowlight_sweep.py
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from anivision.cli import load_rgb
from anivision.simulate import simulate
from anivision.variants import DOG_V2

ROOT = Path(__file__).resolve().parent.parent
GAMMAS = [1.0, 0.85, 0.7, 0.55]
PHOTOS = ["portrait-hat", "apples", "orange-buoy"]


def main() -> int:
    tiles = []
    for name in PHOTOS:
        img = load_rgb(str(ROOT / "samples" / "photos" / f"{name}.jpg"))
        row = []
        for g in GAMMAS:
            res = simulate(img, DOG_V2, shadow_gamma=g)
            im = Image.fromarray(res.image)
            im.thumbnail((380, 300))
            t = Image.new("RGB", (380, 322), (28, 28, 28))
            t.paste(im, (0, 20))
            ImageDraw.Draw(t).text((4, 2), f"{name}  gamma {g:g}" + ("  (off)" if g == 1 else ""), fill=(235, 235, 235), font=ImageFont.load_default(size=14))
            row.append((t, res))
        tiles.append(row)
    sheet = Image.new("RGB", (380 * len(GAMMAS), 322 * len(PHOTOS)), (28, 28, 28))
    for r, row in enumerate(tiles):
        for c, (t, _) in enumerate(row):
            sheet.paste(t, (c * 380, r * 322))
    out = ROOT / "out"
    out.mkdir(exist_ok=True)
    sheet.save(out / "lowlight_sweep.png")
    print("mean output brightness (sRGB 0-255):")
    for name, row in zip(PHOTOS, tiles):
        print(f"  {name:13s}", "  ".join(f"g{g:g}: {np.mean(res.image):5.1f}" for g, (_, res) in zip(GAMMAS, row)))
    print(f"wrote {out / 'lowlight_sweep.png'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
