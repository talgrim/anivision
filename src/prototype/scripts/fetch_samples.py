"""Download the CC0 test photos from Wikimedia Commons into samples/photos/.

Each file's licence is re-read from the Commons API and anything that is not CC0 is
refused. Images are fetched as Commons thumbnails requested at 1600 px (Commons rounds this up to a
standard size, 1920 px in practice; the originals are huge),
converted to sRGB if they carry another ICC profile, and saved as JPEG. Provenance goes to
samples/SOURCES.md.

Run from src/prototype:  PYTHONPATH=. .venv/bin/python scripts/fetch_samples.py
"""

from __future__ import annotations

import io
import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image, ImageCms

ROOT = Path(__file__).resolve().parent.parent
PHOTOS = ROOT / "samples" / "photos"
API = "https://commons.wikimedia.org/w/api.php"
UA = {"User-Agent": "anivision-prototype/0.1 (test-photo fetch script)"}
WIDTH = 1600

# slug -> (Commons file title, why it is in the set)
PHOTOS_TO_FETCH = {
    "apples": ("File:Tree with red apples in Barkedal 4.jpg", "red fruit against green leaves"),
    "poppy": ("File:Papaver rhoeas 2025 - Papaver rhoeas.jpg", "saturated red flower on green"),
    "strawberries": ("File:Strawberries in a tray.jpg", "red and green in one close-up"),
    "autumn": ("File:Colorful autumn forest.jpg", "red foliage, green leaves and blue sky"),
    "portrait-hat": ("File:Smiling woman (Unsplash).jpg", "skin tones, red lips, patterned scarf on a green background"),
    "portrait-pink": ("File:Smiling woman pink shirt.jpg", "dark skin tones and a pink top on white"),
    "dog": ("File:Dog on dirt road.jpg", "brown dog on a dirt road"),
    "meadow": ("File:Meadow in Niederfinow 2021-07-17 17.jpg", "green meadow and blue sky"),
    "vegetables": ("File:Colorful winter Vegetables -01.jpg", "many hues side by side"),
    "orange-buoy": ("File:Orange buoy in water.jpg", "orange against blue water"),
}


def api(params: dict) -> dict:
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json"})
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.load(r)


def strip_html(s: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", s)).strip()


def srgb_bytes(img: Image.Image) -> Image.Image:
    """Return an sRGB RGB image, converting from an embedded ICC profile if present."""
    icc = img.info.get("icc_profile")
    if icc:
        try:
            src = ImageCms.ImageCmsProfile(io.BytesIO(icc))
            if "sRGB" not in ImageCms.getProfileDescription(src):
                img = ImageCms.profileToProfile(img.convert("RGB"), src, ImageCms.createProfile("sRGB"))
        except Exception as exc:  # unreadable profile: assume sRGB, say so
            print(f"  warning: could not apply ICC profile ({exc}); assuming sRGB")
    return img.convert("RGB")


def main() -> None:
    PHOTOS.mkdir(parents=True, exist_ok=True)
    rows = []
    for slug, (title, why) in PHOTOS_TO_FETCH.items():
        print(slug, "<-", title)
        data = api(
            {
                "action": "query",
                "titles": title,
                "prop": "imageinfo",
                "iiprop": "url|size|extmetadata",
                "iiurlwidth": WIDTH,
                "iiextmetadatafilter": "LicenseShortName|LicenseUrl|Artist|Credit",
            }
        )
        page = next(iter(data["query"]["pages"].values()))
        info = page["imageinfo"][0]
        meta = info["extmetadata"]
        licence = meta.get("LicenseShortName", {}).get("value", "")
        if licence != "CC0":
            raise SystemExit(f"{title}: licence is {licence!r}, not CC0; refusing to download")
        req = urllib.request.Request(info["thumburl"], headers=UA)
        with urllib.request.urlopen(req, timeout=120) as r:
            img = srgb_bytes(Image.open(io.BytesIO(r.read())))
        out = PHOTOS / f"{slug}.jpg"
        img.save(out, quality=92, optimize=True)
        rows.append(
            {
                "slug": slug,
                "title": title,
                "page": info["descriptionurl"],
                "author": strip_html(meta.get("Artist", {}).get("value", "unknown")),
                "licence": licence,
                "licence_url": meta.get("LicenseUrl", {}).get("value", ""),
                "orig": f"{info['width']}x{info['height']}",
                "size": f"{img.width}x{img.height}",
                "why": why,
            }
        )
        time.sleep(2)  # be polite to the API

    lines = [
        "# Sample photo sources",
        "",
        "All photos are CC0 files from Wikimedia Commons, resized to about 1920 px wide JPEGs",
        "(converted to sRGB where a profile was embedded). Regenerate with",
        "`scripts/fetch_samples.py`, which refuses any file whose Commons licence is not CC0.",
        "",
        "| File | Why it is here | Commons page | Author (as listed) | Licence | Original -> saved |",
        "|---|---|---|---|---|---|",
    ]
    for r in rows:
        lines.append(
            f"| photos/{r['slug']}.jpg | {r['why']} | [{r['title']}]({r['page']}) | "
            f"{r['author']} | [{r['licence']}]({r['licence_url']}) | {r['orig']} -> {r['size']} |"
        )
    (ROOT / "samples" / "SOURCES.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} photos and samples/SOURCES.md")


if __name__ == "__main__":
    main()
