"""Generate synthetic test charts into samples/charts/ (deterministic, no licensing).

Usage: python scripts/make_test_charts.py [out_dir]
All colours are sRGB code values.
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

CELL_W, CELL_H = 160, 120
SEED = 20240601


# --------------------------------------------------------------------- helpers
def _font(size: int = 13):
    return ImageFont.load_default(size=size)


def _label_color(rgb) -> tuple[int, int, int]:
    luma = 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]
    return (0, 0, 0) if luma > 140 else (255, 255, 255)


def patch_sheet(patches, cols: int, cell=(CELL_W, CELL_H)) -> Image.Image:
    """Grid of flat colour patches; ``patches`` is a list of (label, (r, g, b))."""
    rows = -(-len(patches) // cols)
    cw, ch = cell
    img = Image.new("RGB", (cols * cw, rows * ch), (128, 128, 128))
    d = ImageDraw.Draw(img)
    font = _font()
    for i, (label, rgb) in enumerate(patches):
        x, y = (i % cols) * cw, (i // cols) * ch
        d.rectangle([x, y, x + cw - 1, y + ch - 1], fill=tuple(rgb))
        d.text((x + 6, y + ch - 20), label, fill=_label_color(rgb), font=font)
    return img


def hsv_to_rgb(h, s, v) -> np.ndarray:
    """Vectorised HSV (h in [0, 1)) -> sRGB floats in [0, 1], shape (..., 3)."""
    h, s, v = np.broadcast_arrays(np.asarray(h, float), np.asarray(s, float), np.asarray(v, float))
    i = np.floor(h * 6.0).astype(int) % 6
    f = h * 6.0 - np.floor(h * 6.0)
    p, q, t = v * (1 - s), v * (1 - f * s), v * (1 - (1 - f) * s)
    choices = [(v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q)]
    out = np.zeros(h.shape + (3,))
    for k, (r, g, b) in enumerate(choices):
        m = i == k
        out[m] = np.stack([r[m], g[m], b[m]], axis=-1)
    return out


def to_image(arr01: np.ndarray) -> Image.Image:
    return Image.fromarray(np.rint(np.clip(arr01, 0, 1) * 255).astype(np.uint8))


def smooth_noise(rng, h, w, scale) -> np.ndarray:
    """Zero-mean unit-ish noise with features about ``scale`` pixels wide."""
    low = rng.normal(size=(max(2, h // scale), max(2, w // scale))).astype(np.float32)
    big = np.asarray(Image.fromarray(low).resize((w, h), Image.BICUBIC))
    return big / (big.std() + 1e-9)


# ---------------------------------------------------------------------- charts
def chart_primaries():
    return patch_sheet(
        [
            ("red 255,0,0", (255, 0, 0)),
            ("green 0,255,0", (0, 255, 0)),
            ("blue 0,0,255", (0, 0, 255)),
            ("cyan", (0, 255, 255)),
            ("magenta", (255, 0, 255)),
            ("yellow", (255, 255, 0)),
            ("white", (255, 255, 255)),
            ("gray 128", (128, 128, 128)),
            ("black", (0, 0, 0)),
        ],
        cols=3,
    )


def _ball_scene(textured: bool, rng) -> Image.Image:
    w, h = 640, 480
    horizon = 170
    sky, grass, ball = (135, 180, 235), (60, 140, 40), (200, 30, 30)
    arr = np.zeros((h, w, 3))
    arr[:horizon] = np.array(sky) / 255
    arr[horizon:] = np.array(grass) / 255

    yy, xx = np.mgrid[0:h, 0:w]
    cx, cy, r = 330, 330, 70
    dist = np.hypot(xx - cx, yy - cy)
    inside = dist <= r
    ball_rgb = np.broadcast_to(np.array(ball) / 255, (h, w, 3)).copy()

    if textured:
        # Multi-scale luminance noise on the grass, plus fine blade-like streaks.
        lum = 1 + 0.10 * smooth_noise(rng, h, w, 3) + 0.14 * smooth_noise(rng, h, w, 12)
        streaks = np.asarray(
            Image.fromarray(rng.normal(size=(h // 2, w // 24)).astype(np.float32)).resize(
                (w, h), Image.BILINEAR
            )
        )
        lum = lum + 0.08 * streaks / (streaks.std() + 1e-9)
        arr[horizon:] *= lum[horizon:, :, None]
        arr[:horizon] *= (1 + 0.03 * smooth_noise(rng, h, w, 40))[:horizon, :, None]
        # Spherical shading with a highlight offset towards the upper left.
        hl = np.hypot(xx - (cx - 25), yy - (cy - 28)) / r
        shade = np.clip(1.15 - 0.55 * hl, 0.45, 1.2)
        ball_rgb = ball_rgb * shade[..., None] * (1 + 0.05 * smooth_noise(rng, h, w, 2))[..., None]
        # Soft contact shadow under the ball.
        sh = np.hypot((xx - (cx + 20)) / (r * 1.25), (yy - (cy + r - 8)) / 18)
        arr[horizon:] *= (1 - 0.45 * np.clip(1 - sh, 0, 1))[horizon:, :, None]

    arr[inside] = ball_rgb[inside]
    return to_image(arr)


def chart_hue_wheel():
    size = 520
    yy, xx = np.mgrid[0:size, 0:size]
    c = (size - 1) / 2
    dx, dy = xx - c, c - yy
    rad = np.hypot(dx, dy) / (size * 0.5 - 4)
    hue = (np.arctan2(dy, dx) / (2 * np.pi)) % 1.0
    rgb = hsv_to_rgb(hue, np.clip(rad, 0, 1), np.ones_like(hue))  # white centre -> saturated rim
    rgb[rad > 1] = 0.5
    wheel = to_image(rgb)

    # Saturated hue ramps (full value, then darker) underneath.
    n = np.linspace(0, 1, size, endpoint=False)
    ramp_hi = np.tile(hsv_to_rgb(n, 1.0, 1.0)[None], (40, 1, 1))
    ramp_lo = np.tile(hsv_to_rgb(n, 1.0, 0.55)[None], (40, 1, 1))
    out = Image.new("RGB", (size, size + 80), (128, 128, 128))
    out.paste(wheel, (0, 0))
    out.paste(to_image(ramp_hi), (0, size))
    out.paste(to_image(ramp_lo), (0, size + 40))
    return out


def chart_gray_ramp():
    w = 768
    smooth = np.tile(np.linspace(0, 1, w)[None, :, None], (140, 1, 3))
    img = Image.new("RGB", (w, 140 + 100), (128, 128, 128))
    img.paste(to_image(smooth), (0, 0))
    steps = [round(i * 255 / 15) for i in range(16)]
    d = ImageDraw.Draw(img)
    font = _font(11)
    sw = w // 16
    for i, v in enumerate(steps):
        d.rectangle([i * sw, 140, (i + 1) * sw - 1, 239], fill=(v, v, v))
        d.text((i * sw + 4, 220), str(v), fill=_label_color((v, v, v)), font=font)
    return img


def chart_blue_yellow():
    return patch_sheet(
        [
            ("blue 0,70,200", (0, 70, 200)),
            ("sky blue", (60, 120, 230)),
            ("light blue", (130, 180, 255)),
            ("gray ref", (128, 128, 128)),
            ("yellow 240,210,0", (240, 210, 0)),
            ("light yellow", (255, 235, 80)),
            ("dark yellow", (200, 170, 0)),
            ("violet", (110, 60, 200)),
        ],
        cols=4,
    )


def chart_red_green_brown():
    return patch_sheet(
        [
            ("red 200,30,30", (200, 30, 30)),
            ("orange-red", (220, 60, 40)),
            ("orange", (230, 120, 30)),
            ("brown", (140, 85, 40)),
            ("olive", (130, 130, 40)),
            ("green 60,150,50", (60, 150, 50)),
            ("light green", (90, 170, 60)),
            ("dark green", (30, 100, 30)),
        ],
        cols=4,
    )


def chart_skin_foliage():
    return patch_sheet(
        [
            ("skin 1", (255, 224, 196)),
            ("skin 2", (241, 194, 155)),
            ("skin 3", (224, 172, 131)),
            ("skin 4", (198, 134, 66)),
            ("skin 5", (141, 85, 36)),
            ("skin 6", (92, 56, 28)),
            ("foliage 1", (34, 85, 34)),
            ("foliage 2", (60, 120, 40)),
            ("foliage 3", (85, 140, 50)),
            ("foliage 4", (120, 160, 60)),
            ("foliage 5", (70, 100, 40)),
            ("foliage 6", (150, 170, 80)),
        ],
        cols=6,
    )


def main(argv=None) -> int:
    argv = sys.argv[1:] if argv is None else argv
    out_dir = Path(argv[0]) if argv else Path(__file__).resolve().parent.parent / "samples" / "charts"
    out_dir.mkdir(parents=True, exist_ok=True)

    charts = {
        "primaries": chart_primaries(),
        "red_ball_flat": _ball_scene(False, np.random.default_rng(SEED)),
        "red_ball_textured": _ball_scene(True, np.random.default_rng(SEED)),
        "hue_wheel": chart_hue_wheel(),
        "gray_ramp": chart_gray_ramp(),
        "blue_yellow": chart_blue_yellow(),
        "red_green_brown": chart_red_green_brown(),
        "skin_foliage": chart_skin_foliage(),
    }
    for name, img in charts.items():
        img.save(out_dir / f"{name}.png")
        print(f"wrote {out_dir / (name + '.png')}  {img.size[0]}x{img.size[1]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
