"""How much does the V2 matrix move when the assumed display primaries change?

Also compares anchor choices against the baseline and against V0.
Prints Markdown tables and saves them to out/primaries_sensitivity.md.

Usage: python scripts/primaries_sensitivity.py
"""

from __future__ import annotations

from pathlib import Path

import numpy as np

from anivision.derive import DEFAULT_ANCHOR_NM, derive
from anivision.spectra import DEFAULT_PRIMARY_CENTERS, DEFAULT_PRIMARY_SIGMA
from anivision.variants import DEUTERANOPIA_VIENOT_1999 as V0

BASE_CENTERS = DEFAULT_PRIMARY_CENTERS
BASE_SIGMA = DEFAULT_PRIMARY_SIGMA


def shifted(delta, which):
    c = list(BASE_CENTERS)
    for i in which:
        c[i] += delta
    return tuple(c)


def primaries_cases():
    names = "RGB"
    cases = [("baseline (610/550/465, sigma 20)", BASE_CENTERS, BASE_SIGMA)]
    for i, n in enumerate(names):
        for d in (-10, +10):
            cases.append((f"{n} centre {d:+d} nm", shifted(d, [i]), BASE_SIGMA))
    for d in (-10, +10):
        cases.append((f"all centres {d:+d} nm", shifted(d, [0, 1, 2]), BASE_SIGMA))
    for s in (10.0, 15.0, 30.0, 40.0):
        cases.append((f"sigma {s:g} nm (all)", BASE_CENTERS, s))
    return cases


def md_table(header, rows):
    out = ["| " + " | ".join(header) + " |", "|" + "|".join("---" for _ in header) + "|"]
    out += ["| " + " | ".join(r) + " |" for r in rows]
    return "\n".join(out)


def main() -> int:
    base = derive()
    rows = []
    for name, centers, sigma in primaries_cases():
        t = derive(centers=centers, sigma=sigma)
        d = t.matrix - base.matrix
        rows.append(
            [
                name,
                f"{t.neutral_point_nm:.1f}",
                f"{np.linalg.norm(d):.4f}",
                f"{np.abs(d).max():.4f}",
                f"{np.linalg.norm(t.matrix - V0):.3f}",
            ]
        )
    table1 = md_table(
        ["case", "neutral pt (nm)", "‖ΔM‖_F vs baseline", "max |ΔM|", "‖M−V0‖_F"], rows
    )

    rows = []
    anchors = [
        (f"{DEFAULT_ANCHOR_NM:g} nm monochromatic (default)", DEFAULT_ANCHOR_NM),
        ("575 nm monochromatic", 575.0),
        ("sRGB blue primary (0,0,1)", np.array([0.0, 0.0, 1.0])),
        ("sRGB yellow (1,1,0): same plane as blue", np.array([1.0, 1.0, 0.0])),
    ]
    for name, a in anchors:
        t = derive(anchor=a)
        rows.append(
            [
                name,
                f"{np.linalg.norm(t.matrix - base.matrix):.4f}",
                f"{np.linalg.norm(t.matrix - V0):.4f}",
                " ".join(f"{v:+.3f}" for v in t.anchor_rgb / np.abs(t.anchor_rgb).max()),
            ]
        )
    table2 = md_table(["anchor", "‖M−M_475‖_F", "‖M−V0‖_F", "anchor rgb (scaled)"], rows)

    text = (
        "## Primaries sensitivity (475 nm anchor)\n\n"
        + table1
        + "\n\n## Anchor choice (baseline primaries)\n\n"
        + table2
        + "\n"
    )
    print(text)
    out = Path(__file__).resolve().parent.parent / "out"
    out.mkdir(exist_ok=True)
    (out / "primaries_sensitivity.md").write_text(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
