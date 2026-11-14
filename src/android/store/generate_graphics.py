"""Generate the Google Play store graphics: the 512x512 icon and the 1024x500 feature graphic.

    python -m venv .venv && .venv/bin/pip install pillow numpy
    .venv/bin/python generate_graphics.py

Outputs icon-512.png and feature-graphic-1024x500.png next to this script. The icon repeats the
launcher icon (drawable/ic_launcher_*.xml). The feature graphic shows one made-up scene twice, as
a person sees it (left) and through the app's dog colour matrix (right, from
src/prototype/export/matrix.json), so it matches what the app does.
"""

import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
MATRIX_JSON = HERE.parent.parent / "prototype" / "export" / "matrix.json"
FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
    "/mnt/c/Windows/Fonts/arialbd.ttf",
]
SS = 3  # supersampling factor for smooth edges

NAVY = (0x1E, 0x2A, 0x44)
EYE = (0xF4, 0xF1, 0xEA)
IRIS_LEFT = (0xD2, 0x45, 0x3B)
IRIS_RIGHT = (0xA8, 0x9A, 0x52)
PUPIL = (0x14, 0x14, 0x14)


def font(size: int) -> ImageFont.FreeTypeFont:
    for path in FONT_CANDIDATES:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    raise SystemExit("No bold font found; add one to FONT_CANDIDATES.")


def bezier(p0, p1, p2, steps=48):
    return [
        (
            (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t**2 * p2[0],
            (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t**2 * p2[1],
        )
        for t in (i / steps for i in range(steps + 1))
    ]


def draw_eye(draw: ImageDraw.ImageDraw, cx: float, cy: float, size: float) -> None:
    """The launcher icon's eye. `size` is the edge of the 108-unit icon canvas in pixels."""
    k = size / 108

    def at(x, y):
        return (cx + (x - 54) * k, cy + (y - 54) * k)

    outline = bezier(at(22, 54), at(54, 24), at(86, 54)) + bezier(at(86, 54), at(54, 84), at(22, 54))
    draw.polygon(outline, fill=EYE)
    box = [*at(41, 41), *at(67, 67)]
    draw.pieslice(box, 90, 270, fill=IRIS_LEFT)
    draw.pieslice(box, -90, 90, fill=IRIS_RIGHT)
    draw.ellipse([*at(48, 48), *at(60, 60)], fill=PUPIL)


def make_icon() -> Image.Image:
    size = 512
    img = Image.new("RGB", (size * SS, size * SS), NAVY)
    draw_eye(ImageDraw.Draw(img), size * SS / 2, size * SS / 2, size * SS)
    return img.resize((size, size), Image.LANCZOS)


def make_scene(width: int, height: int) -> Image.Image:
    """A flat, made-up garden: sky, hills, a red ball, a blue ball and yellow flowers."""
    w, h = width * SS, height * SS
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):  # sky gradient
        t = y / h
        d.line([(0, y), (w, y)], fill=(int(120 + 60 * t), int(180 + 40 * t), int(235 + 10 * t)))
    d.ellipse([w * 0.86, h * 0.08, w * 0.94, h * 0.08 + w * 0.08], fill=(255, 236, 140))  # sun
    hill = [(0, h * 0.62)] + [
        (x, h * 0.58 + h * 0.05 * np.sin(x / w * 5.5)) for x in np.linspace(0, w, 80)
    ] + [(w, h), (0, h)]
    d.polygon(hill, fill=(70, 150, 60))
    d.rectangle([0, h * 0.78, w, h], fill=(60, 135, 52))  # lawn in front
    # A red and a blue ball on each side of the split (the centre is covered by the title card), so
    # both the red-to-khaki shift and the blue that stays blue show up on the dog side as well.
    red, blue = h * 0.20, h * 0.17
    for x in (0.03, 0.76):
        d.ellipse([w * x, h * 0.60, w * x + red, h * 0.60 + red], fill=(214, 40, 40))
        d.ellipse([w * x + red * 0.2, h * 0.60 + red * 0.15, w * x + red * 0.42, h * 0.60 + red * 0.37],
                  fill=(240, 120, 110))
    for x in (0.145, 0.895):
        d.ellipse([w * x, h * 0.66, w * x + blue, h * 0.66 + blue], fill=(40, 90, 220))
    rng = np.random.default_rng(7)  # yellow and orange flowers
    for _ in range(26):
        fx, fy = rng.uniform(0.03, 0.97) * w, rng.uniform(0.80, 0.97) * h
        r = h * rng.uniform(0.012, 0.022)
        colour = (250, 210, 40) if rng.random() < 0.6 else (240, 140, 30)
        d.ellipse([fx - r, fy - r, fx + r, fy + r], fill=colour)
    return img.resize((width, height), Image.LANCZOS)


def dog_view(img: Image.Image) -> Image.Image:
    """Apply the app's dog matrix in linear light: decode sRGB, multiply, clip, encode."""
    matrix = np.array(json.loads(MATRIX_JSON.read_text())["profiles"]["dog"]["variants"]["v2"]["matrix"])
    srgb = np.asarray(img, dtype=np.float64) / 255
    linear = np.where(srgb <= 0.04045, srgb / 12.92, ((srgb + 0.055) / 1.055) ** 2.4)
    out = np.clip(linear @ matrix.T, 0, 1)
    enc = np.where(out <= 0.0031308, out * 12.92, 1.055 * out ** (1 / 2.4) - 0.055)
    return Image.fromarray(np.round(enc * 255).astype(np.uint8))


def make_feature_graphic() -> Image.Image:
    width, height = 1024, 500
    scene = make_scene(width, height)
    dog = dog_view(scene)
    img = scene.copy()
    img.paste(dog.crop((width // 2, 0, width, height)), (width // 2, 0))

    big = img.resize((width * SS, height * SS), Image.LANCZOS)
    d = ImageDraw.Draw(big, "RGBA")
    d.rectangle([width * SS // 2 - 2 * SS, 0, width * SS // 2 + 2 * SS, height * SS], fill=(255, 255, 255, 230))

    # Centre card with the icon, the name and the tagline.
    card = [width * 0.5 - 250, 70, width * 0.5 + 250, 400]
    shadow = Image.new("RGBA", big.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([c * SS for c in card], radius=28 * SS, fill=(0, 0, 0, 120))
    big = Image.alpha_composite(big.convert("RGBA"), shadow.filter(ImageFilter.GaussianBlur(14 * SS)))
    d = ImageDraw.Draw(big, "RGBA")
    d.rounded_rectangle([c * SS for c in card], radius=28 * SS, fill=NAVY + (255,))
    draw_eye(d, width * SS / 2, 165 * SS, 150 * SS)
    d.text((width * SS / 2, 275 * SS), "AniVision", font=font(70 * SS), fill=(241, 245, 249), anchor="mm")
    d.text((width * SS / 2, 340 * SS), "See the world as a dog might", font=font(27 * SS),
           fill=(203, 213, 225), anchor="mm")
    return big.convert("RGB").resize((width, height), Image.LANCZOS)


if __name__ == "__main__":
    make_icon().save(HERE / "icon-512.png", optimize=True)
    make_feature_graphic().save(HERE / "feature-graphic-1024x500.png", optimize=True)
    print("wrote icon-512.png and feature-graphic-1024x500.png in", HERE)
