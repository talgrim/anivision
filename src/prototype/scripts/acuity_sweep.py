"""Render the acuity blur at several exaggeration values to pick a default and a maximum.

Writes out/acuity_sweep_crops.png (100% crops, columns = exaggeration) and
out/acuity_sweep_full.png (whole frames), plus out/acuity_sweep.md with the numbers.
All images go through V2 with the physical baseline sigma times the exaggeration.

Usage (from src/prototype):  PYTHONPATH=. .venv/bin/python scripts/acuity_sweep.py
"""

from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from anivision.acuity import AcuityParams, sigma_px
from anivision.cli import load_rgb
from anivision.simulate import simulate
from anivision.variants import DOG_V2

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "out"
EXAGGERATIONS = [0.0, 1.0, 2.0, 4.0, 8.0, 16.0]
CROP_PHOTOS = {"dog": (700, 330), "vegetables": (700, 400), "portrait-hat": (560, 300)}  # (x, y) of a 420x300 crop
FULL_PHOTO = "apples"
CROP_W, CROP_H = 420, 300
FONT = ImageFont.load_default(size=14)


def run(img: np.ndarray, e: float) -> np.ndarray:
    return simulate(img, DOG_V2, acuity=AcuityParams(exaggeration=e) if e > 0 else None).image


def label(im: Image.Image, text: str) -> Image.Image:
    out = Image.new("RGB", (im.width, im.height + 20), (28, 28, 28))
    out.paste(im, (0, 20))
    ImageDraw.Draw(out).text((4, 2), text, fill=(235, 235, 235), font=FONT)
    return out


def main() -> int:
    OUT.mkdir(exist_ok=True)
    tiles = []
    for name, (x, y) in CROP_PHOTOS.items():
        img = load_rgb(str(ROOT / "samples" / "photos" / f"{name}.jpg"))
        x = min(x, img.shape[1] - CROP_W)
        y = min(y, img.shape[0] - CROP_H)
        row = []
        for e in EXAGGERATIONS:
            crop = Image.fromarray(run(img, e)).crop((x, y, x + CROP_W, y + CROP_H))
            row.append(label(crop, f"{name}  x{e:g}" if e else f"{name}  no blur"))
        tiles.append(row)
    cw, ch = tiles[0][0].size
    sheet = Image.new("RGB", (cw * 3, ch * 2 * len(tiles)), (28, 28, 28))
    for r, row in enumerate(tiles):
        for c, t in enumerate(row):
            sheet.paste(t, ((c % 3) * cw, (r * 2 + c // 3) * ch))
    sheet.save(OUT / "acuity_sweep_crops.png")

    img = load_rgb(str(ROOT / "samples" / "photos" / f"{FULL_PHOTO}.jpg"))
    full = []
    for e in EXAGGERATIONS:
        im = Image.fromarray(run(img, e))
        im.thumbnail((420, 420))
        full.append(label(im, f"{FULL_PHOTO}  x{e:g}" if e else f"{FULL_PHOTO}  no blur"))
    fw, fh = full[0].size
    sheet = Image.new("RGB", (fw * 3, fh * 2), (28, 28, 28))
    for i, t in enumerate(full):
        sheet.paste(t, ((i % 3) * fw, (i // 3) * fh))
    sheet.save(OUT / "acuity_sweep_full.png")

    p = AcuityParams()
    lines = [
        "| exaggeration | sigma @1920 px | sigma @1440 px | sigma @1080 px | sigma @640 px | contrast kept at 8 cpd |",
        "|---|---|---|---|---|---|",
    ]
    for e in EXAGGERATIONS[1:]:
        q = AcuityParams(exaggeration=e)
        kept = math.exp(-2 * math.pi**2 * sigma_px(1920, q) ** 2 * (p.cutoff_cpd / (1920 / p.hfov_deg)) ** 2)
        lines.append(
            f"| x{e:g} | {sigma_px(1920, q):.2f} | {sigma_px(1440, q):.2f} | {sigma_px(1080, q):.2f} | {sigma_px(640, q):.2f} | {kept:.2g} |"
        )
    text = "\n".join(lines) + "\n"
    (OUT / "acuity_sweep.md").write_text(text, encoding="utf-8")
    print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
