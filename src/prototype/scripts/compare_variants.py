"""Compare the colour-transform variants on the test charts and photos.

Writes to out/:
  compare_charts.png, compare_photos.png   contact sheets: original | V0 | V1 | V2 | V2b
  compare_metrics.md                       out-of-gamut %, CIEDE2000 and matrix distances

V2b is an analysis-only variant: V2 re-anchored on the blue/yellow plane (anchor = sRGB blue),
used to judge the anchor choice. V1 (Machado 2009) is skipped if colour-science is missing.

Usage (from src/prototype):  PYTHONPATH=. .venv/bin/python scripts/compare_variants.py
"""

from __future__ import annotations

import argparse
from itertools import combinations
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from anivision.cli import load_rgb
from anivision.colorspace import srgb_to_linear, to_float
from anivision.derive import derive
from anivision.metrics import frobenius, image_delta_e
from anivision.simulate import simulate
from anivision.variants import DEUTERANOPIA_VIENOT_1999 as V0
from anivision.variants import DOG_V2, machado_deuteranopia

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "out"
TILE_W, TILE_H = 340, 255
BG = (28, 28, 28)


def build_variants() -> dict[str, np.ndarray]:
    variants = {"V0": V0}
    try:
        variants["V1"] = machado_deuteranopia()
    except ImportError:
        print("colour-science not installed: V1 skipped")
    variants["V2"] = DOG_V2
    variants["V2b"] = derive(anchor=np.array([0.0, 0.0, 1.0])).matrix
    return variants


CONE_CATCH = derive().cone_catch  # 2x3: linear sRGB -> dog (S, L) signals, display-primaries model


def cone_signal_error(original: np.ndarray, simulated: np.ndarray) -> np.ndarray:
    """Per-pixel distance between the dog (S, L) cone signals of two sRGB images.

    Signals are divided by white's, so 1.0 means "as large as the white signal". An ideal
    dichromat simulation keeps them equal (error 0). This uses the same display model as V2,
    so for V2 it mostly measures what clipping destroys; for V0 and V1 it measures how far
    they are from dog-cone equivalence.
    """
    white = CONE_CATCH @ np.ones(3)
    a = srgb_to_linear(to_float(original)) @ CONE_CATCH.T / white
    b = srgb_to_linear(to_float(simulated)) @ CONE_CATCH.T / white
    return np.linalg.norm(a - b, axis=-1)


def load_small(path: Path, max_edge: int) -> np.ndarray:
    img = load_rgb(str(path))
    h, w = img.shape[:2]
    if max(h, w) > max_edge:
        scale = max_edge / max(h, w)
        pil = Image.fromarray(img).resize((round(w * scale), round(h * scale)), Image.LANCZOS)
        img = np.asarray(pil)
    return img


def tile(img: np.ndarray, caption: str) -> Image.Image:
    pil = Image.fromarray(img)
    pil.thumbnail((TILE_W, TILE_H - 18))
    t = Image.new("RGB", (TILE_W, TILE_H), BG)
    t.paste(pil, ((TILE_W - pil.width) // 2, 0))
    ImageDraw.Draw(t).text((6, TILE_H - 17), caption, fill=(230, 230, 230), font=ImageFont.load_default(size=13))
    return t


def contact_sheet(rows: list[tuple[str, np.ndarray, dict]], columns: list[str]) -> Image.Image:
    head = 26
    sheet = Image.new("RGB", (TILE_W * (len(columns) + 1), head + TILE_H * len(rows)), BG)
    d = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=15)
    for c, name in enumerate(["original"] + columns):
        d.text((8 + c * TILE_W, 5), name, fill=(255, 255, 255), font=font)
    for r, (name, original, sims) in enumerate(rows):
        y = head + r * TILE_H
        sheet.paste(tile(original, name), (0, y))
        for c, col in enumerate(columns):
            res = sims[col]
            sheet.paste(tile(res.image, f"{col}: {res.out_of_gamut_fraction:.1%} clipped"), ((c + 1) * TILE_W, y))
    return sheet


def md_table(header: list[str], rows: list[list[str]]) -> str:
    out = ["| " + " | ".join(header) + " |", "|" + "|".join("---" for _ in header) + "|"]
    return "\n".join(out + ["| " + " | ".join(r) + " |" for r in rows])


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--max-edge", type=int, default=900, help="downscale images to this long edge")
    args = ap.parse_args(argv)

    variants = build_variants()
    names = list(variants)
    groups = {
        "charts": sorted((ROOT / "samples" / "charts").glob("*.png")),
        "photos": sorted((ROOT / "samples" / "photos").glob("*.jpg")),
    }
    OUT.mkdir(exist_ok=True)

    per_image: dict[str, dict] = {}  # image -> {group, clip{v}, de_orig{v}, de_pair{(a,b)}}
    for group, paths in groups.items():
        rows = []
        for path in paths:
            img = load_small(path, args.max_edge)
            sims = {n: simulate(img, m) for n, m in variants.items()}
            rows.append((path.stem, img, sims))
            de_pair = {(a, b): image_delta_e(sims[a].image, sims[b].image) for a, b in combinations(names, 2)}
            per_image[f"{group}/{path.stem}"] = {
                "group": group,
                "clip": {n: sims[n].out_of_gamut_fraction for n in names},
                "de_orig": {n: image_delta_e(img, sims[n].image) for n in names},
                "cone_err": {n: cone_signal_error(img, sims[n].image) for n in names},
                "de_pair": de_pair,
            }
        contact_sheet(rows, names).save(OUT / f"compare_{group}.png")
        print(f"wrote out/compare_{group}.png ({len(rows)} images)")

    md: list[str] = ["# Variant comparison (generated by scripts/compare_variants.py)", ""]
    md += [
        "Variants: " + ", ".join(names) + ". V2b = V2 anchored on the sRGB blue/yellow plane (analysis only).",
        "CIEDE2000 is computed between the 8-bit simulated images, i.e. what is displayed, after clipping.",
        "",
    ]

    md += ["## Matrix distances (Frobenius)", ""]
    md.append(
        md_table(
            [""] + names,
            [[a] + [f"{frobenius(variants[a], variants[b]):.3f}" for b in names] for a in names],
        )
    )

    md += ["", "## Out-of-gamut pixels (clipped), per image", ""]
    md.append(
        md_table(
            ["image"] + names,
            [[k] + [f"{v['clip'][n]:.1%}" for n in names] for k, v in per_image.items()],
        )
    )

    def stats(de: np.ndarray) -> str:
        return f"{de.mean():.2f} / {np.percentile(de, 99):.1f} / {de.max():.1f}"

    md += ["", "## Change from the original (CIEDE2000, mean / p99 / max), per image", ""]
    md.append(
        md_table(
            ["image"] + names,
            [[k] + [stats(v["de_orig"][n]) for n in names] for k, v in per_image.items()],
        )
    )

    def cone_stats(e: np.ndarray) -> str:
        return f"{e.mean():.3f} / {np.percentile(e, 99):.2f}"

    md += ["", "## Dog cone-signal error vs the original (relative to white; mean / p99), per image", ""]
    md.append(
        md_table(
            ["image"] + names,
            [[k] + [cone_stats(v["cone_err"][n]) for n in names] for k, v in per_image.items()],
        )
    )
    cone_rows = []
    for group in ["charts", "photos"]:
        sel = [v for v in per_image.values() if v["group"] == group]
        cone_rows.append(
            [group]
            + [f"{np.mean([v['cone_err'][n].mean() for v in sel]):.3f} / {np.mean([np.percentile(v['cone_err'][n], 99) for v in sel]):.2f}" for n in names]
        )
    md += ["", "Mean over images of (mean / p99):", ""]
    md.append(md_table(["set"] + names, cone_rows))

    md += ["", "## Difference between variants (CIEDE2000, mean / p99 / max), per image", ""]
    pairs = list(combinations(names, 2))
    md.append(
        md_table(
            ["image"] + [f"{a} vs {b}" for a, b in pairs],
            [[k] + [stats(v["de_pair"][p]) for p in pairs] for k, v in per_image.items()],
        )
    )

    md += ["", "## Test-set summary (mean of per-image means / mean of p99 / worst max)", ""]
    summary_rows = []
    for group in ["charts", "photos", "all"]:
        sel = [v for v in per_image.values() if group == "all" or v["group"] == group]
        for p in pairs:
            means = [v["de_pair"][p].mean() for v in sel]
            p99s = [np.percentile(v["de_pair"][p], 99) for v in sel]
            maxes = [v["de_pair"][p].max() for v in sel]
            summary_rows.append([group, f"{p[0]} vs {p[1]}", f"{np.mean(means):.2f}", f"{np.mean(p99s):.1f}", f"{np.max(maxes):.1f}"])
    md.append(md_table(["set", "pair", "mean ΔE00", "p99 ΔE00", "max ΔE00"], summary_rows))

    md += ["", "## Mean clipped fraction by set", ""]
    clip_rows = []
    for group in ["charts", "photos"]:
        sel = [v for v in per_image.values() if v["group"] == group]
        clip_rows.append([group] + [f"{np.mean([v['clip'][n] for v in sel]):.1%}" for n in names])
    md.append(md_table(["set"] + names, clip_rows))

    text = "\n".join(md) + "\n"
    (OUT / "compare_metrics.md").write_text(text, encoding="utf-8")
    print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
