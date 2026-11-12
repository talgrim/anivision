"""Write the Android hand-off files to export/ (Phase 5).

  export/matrix.json              matrices, acuity recipe, shadow lift, generating commit
  export/golden_vectors.json      sRGB in -> sRGB out (and unclipped linear) for both variants
  export/AnimalVisionConstants.kt.txt   Kotlin floatArrayOf snippets + acuity helper
  export/animal_vision.glsl       GLSL mat3 constants (column-major) + sRGB helpers

Re-run after any change to the matrices or the acuity/shadow constants; tests/test_export.py
fails when the committed files are stale. Commit the code first, then export, so the recorded
commit is the one that produced the numbers.

Usage (from src/prototype):  PYTHONPATH=. .venv/bin/python scripts/export_constants.py
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path

from anivision.export import glsl_snippet, golden_document, kotlin_snippet, matrix_document

ROOT = Path(__file__).resolve().parent.parent
# Code that determines the exported numbers; changes here make the export "dirty".
SOURCES = ["anivision", "scripts/export_constants.py"]


def _git(*args: str) -> str | None:
    try:
        res = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=True)
    except (OSError, subprocess.CalledProcessError):
        return None
    return res.stdout.strip()


def generator_info() -> dict:
    commit = _git("rev-parse", "HEAD")
    status = _git("status", "--porcelain", "--", *SOURCES)
    return {
        "script": "src/prototype/scripts/export_constants.py",
        "git_commit": commit,
        "git_dirty": bool(status) if status is not None else None,
    }


def write_json(path: Path, doc: dict) -> None:
    text = json.dumps(doc, indent=2, ensure_ascii=False)
    # Keep flat number arrays on one line so the files stay readable and diff well.
    text = re.sub(r"\[\s+([^\[\]{}]*?)\s+\]", lambda m: "[" + re.sub(r"\s*\n\s*", " ", m.group(1)) + "]", text)
    path.write_text(text + "\n", encoding="utf-8", newline="\n")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out-dir", type=Path, default=ROOT / "export")
    args = ap.parse_args()

    out: Path = args.out_dir
    out.mkdir(parents=True, exist_ok=True)
    gen = generator_info()

    write_json(out / "matrix.json", matrix_document(gen))
    write_json(out / "golden_vectors.json", golden_document(gen))
    (out / "AnimalVisionConstants.kt.txt").write_text(kotlin_snippet(gen), encoding="utf-8", newline="\n")
    (out / "animal_vision.glsl").write_text(glsl_snippet(gen), encoding="utf-8", newline="\n")

    dirty = " (uncommitted changes in the sources)" if gen["git_dirty"] else ""
    print(f"wrote 4 files to {out} from commit {gen['git_commit']}{dirty}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
