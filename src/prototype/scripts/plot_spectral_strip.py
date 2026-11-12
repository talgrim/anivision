"""Plot the spectral strip: human vs V0 / V1 / V2, plus the yellow-blue coordinate.

Writes out/spectral_strip.png and out/spectral_strip.md, and prints the gray crossings.

Usage (from src/prototype):  PYTHONPATH=. .venv/bin/python scripts/plot_spectral_strip.py
"""

from __future__ import annotations

from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402

from anivision.colorspace import linear_to_srgb  # noqa: E402
from anivision.derive import derive  # noqa: E402
from anivision.metrics import linear_rgb_to_lab  # noqa: E402
from anivision.profiles import DOG  # noqa: E402
from anivision.spectral_strip import (  # noqa: E402
    STRIP_WAVELENGTHS,
    neutral_crossing_nm,
    render_strip,
    true_spectrum_strip,
)
from anivision.variants import DEUTERANOPIA_VIENOT_1999 as V0  # noqa: E402
from anivision.variants import machado_deuteranopia  # noqa: E402

OUT = Path(__file__).resolve().parent.parent / "out"


def main() -> int:
    wl = STRIP_WAVELENGTHS
    d = derive()
    rows = [("Human (as shown on the display)", render_strip(None), None)]
    rows.append(("V0  Viénot deuteranope", render_strip(V0), neutral_crossing_nm(render_strip(V0))))
    try:
        v1 = machado_deuteranopia()
        rows.append(("V1  Machado 2009", render_strip(v1), neutral_crossing_nm(render_strip(v1))))
    except ImportError:
        print("colour-science not installed: V1 row skipped")
    strip_v2 = render_strip(d.matrix)
    rows.append(("V2  dog, through the display model", strip_v2, neutral_crossing_nm(strip_v2)))
    strip_true = true_spectrum_strip(d)
    rows.append(("V2  dog, true spectral lights", strip_true, neutral_crossing_nm(strip_true)))

    fig, (ax_strip, ax_b) = plt.subplots(
        2, 1, figsize=(11, 7.2), gridspec_kw={"height_ratios": [len(rows) * 0.55, 2.4]}, sharex=True
    )
    n = len(rows)
    for i, (label, strip, _) in enumerate(rows):
        img = linear_to_srgb(np.clip(strip, 0, 1))[None, :, :]
        ax_strip.imshow(img, extent=(wl[0], wl[-1], n - i - 1, n - i), aspect="auto", interpolation="nearest")
    ax_strip.set_ylim(0, n)
    ax_strip.set_yticks([n - i - 0.5 for i in range(n)], [r[0] for r in rows], fontsize=9)
    for nm, text in ((DOG.s_peak_nm, "S cone 429"), (DOG.l_peak_nm, "L cone 555")):
        ax_strip.axvline(nm, color="white", lw=0.8, ls=":")
        ax_strip.text(nm, n + 0.08, text, ha="center", va="bottom", fontsize=8)
    ax_strip.axvline(d.neutral_point_nm, color="black", lw=1.2)
    ax_strip.text(d.neutral_point_nm, -0.12, f"analytic neutral point {d.neutral_point_nm:.1f} nm", ha="center", va="top", fontsize=8)
    ax_strip.set_title("Monochromatic lights through each variant (approximation of dog cone signals)", fontsize=11, pad=22)

    for label, strip, crossing in rows:
        b = linear_rgb_to_lab(strip)[:, 2]
        (line,) = ax_b.plot(wl, b, lw=1.6, label=label if crossing is None else f"{label}: gray at {crossing:.1f} nm")
        if crossing is not None:
            ax_b.axvline(crossing, color=line.get_color(), lw=0.8, ls="--")
    ax_b.axhline(0, color="gray", lw=0.8)
    ax_b.axvspan(470, 490, color="0.85", zorder=0, label="target 480 ± 10 nm")
    ax_b.set_xlabel("wavelength (nm)")
    ax_b.set_ylabel("CIELAB b*  (blue < 0 < yellow)")
    ax_b.set_xlim(wl[0], wl[-1])
    ax_b.legend(fontsize=7.5, loc="lower right")
    fig.tight_layout()

    OUT.mkdir(exist_ok=True)
    fig.savefig(OUT / "spectral_strip.png", dpi=130)

    lines = ["| strip | gray crossing (nm) |", "|---|---|"]
    lines += [f"| {label} | {'-' if c is None else f'{c:.1f}'} |" for label, _, c in rows]
    lines.append(f"| analytic neutral point (cone signals vs white) | {d.neutral_point_nm:.1f} |")
    text = "\n".join(lines)
    (OUT / "spectral_strip.md").write_text(text + "\n", encoding="utf-8")
    print(text)
    print(f"wrote {OUT / 'spectral_strip.png'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
