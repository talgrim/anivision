# Anivision Python prototype

A still-image simulator of canine (and feline) colour vision. It settles the colour maths and the acuity
recipe before the Android app (`../../Android.md`) is built, and exports the results as data
(`export/`).

> **This is an approximation.** It models what a dog's two cone types capture, using published
> cone peaks and a modelled display. It is **not** what a dog subjectively experiences, and the
> result depends on assumptions listed under [Limitations](#limitations).

## What it does

Dogs are dichromats (S cone about 429 nm, L cone about 555 nm). Reds, oranges and greens collapse
towards yellow-brown, blues and yellows stay distinct, grays stay gray. The pipeline, in order:

1. decode sRGB to linear light
2. optional acuity blur (a Gaussian sized from the dog's coarser acuity, in linear light)
3. optional shadow lift (artistic, not physiological)
4. a 3x3 matrix in linear sRGB
5. gamut fit (clamp each channel)
6. encode back to sRGB

## Setup and use

```bash
cd src/prototype
python -m venv .venv && .venv/bin/pip install -r requirements.txt      # numpy, pillow, matplotlib, pytest
.venv/bin/pip install -r requirements-extra.txt                         # optional: colour-science, for V1
.venv/bin/python -m pytest -q

PYTHONPATH=. .venv/bin/python -m anivision in.jpg out.png --variant v2
PYTHONPATH=. .venv/bin/python -m anivision in.jpg out.png --variant v2 --acuity --exaggeration 4 --shadow-lift 0.85
```

Scripts (run from `src/prototype` with `PYTHONPATH=.`, outputs go to the gitignored `out/`):
`compare_variants.py`, `plot_spectral_strip.py`, `primaries_sensitivity.py`, `gamut_policies.py`,
`acuity_sweep.py`, `lowlight_sweep.py`, `fetch_samples.py`, and `export_constants.py` (Phase 5).
`NOTES.md` has every number and decision with its reasoning.

## Variants

| id | what | status |
|---|---|---|
| V2 | Projection derived from dog cone sensitivities, 475 nm anchor, Gaussian display primaries | **default** |
| V0 | Viénot, Brettel & Mollon (1999) deuteranopia matrix | fallback |
| V1 | Machado, Oliveira & Fernandes (2009) deuteranopia matrix (optional `colour-science`) | cross-check only |

All three keep the dog's cone signals to within about 1.4% of white, so V2 is **not** shown to be
more accurate than V0. They differ in which two-dimensional plane of colours is displayed, which
is a convention. V2 was chosen because it is dog-specific, agrees with the independently built V1
(mean CIEDE2000 1.15), keeps blues blue, and clips less on photos (0.9% vs 1.3%). See `NOTES.md`,
Phase 3.

## Android hand-off (`export/`)

Regenerate with `PYTHONPATH=. .venv/bin/python scripts/export_constants.py`; `tests/test_export.py`
fails if the committed files are stale.

| file | content |
|---|---|
| `matrix.json` | V2 and V0 matrices, derivation parameters, acuity and shadow-lift recipes, generating git commit |
| `golden_vectors.json` | 73 sRGB colours (primaries, grays, skin tones, 40 seeded random) with expected 8-bit output per variant |
| `AnimalVisionConstants.kt.txt` | Kotlin `floatArrayOf` constants (row-major, column-major, 4x5 `ColorMatrix`) and `acuitySigmaPx()` |
| `animal_vision.glsl` | GLSL `mat3` constants (column-major arguments), sRGB helpers and `dogView()` |

Layout traps: the matrix acts on **linear** sRGB column vectors; GLSL `mat3(...)` and
`glUniformMatrix3fv(..., false, ...)` are **column-major**; `ColorMatrix` is 4x5 row-major and
works on gamma-encoded values (MVP approximation only). Acuity sigma must be computed from the
real frame's long edge. In the app the blur is a switch, hidden by default; its on-state default
exaggeration is 1.0 (physical), with 8.0 as the maximum.

## Limitations

- Only white is matched to D65. The Gaussian primaries (610 / 550 / 465 nm, sigma 20 nm) do not
  reproduce real sRGB primaries or a real phone display. A +/-10 nm shift of the primaries moves
  the matrix by 0.09-0.23 (Frobenius), the same order as the V0-V2 gap (0.33).
- The neutral point is 486.7 nm analytically and 486.9 nm for true spectral lights, but about
  498 nm for lights rendered through the display model.
- The 475 nm anchor is human-dichromat practice, not dog data. Brettel's two half-plane form
  (475 and 575 nm anchors) would move the matrix by only 0.079; it is not implemented.
- Saturated blues leave the sRGB gamut under V2 (74% of the blue/yellow chart clips); natural
  photos clip little. Plain clamping beat the chroma-preserving alternative.
- The acuity baseline (a Gaussian with 10% contrast at the 8 cycles/degree cutoff) is a modelling
  choice, and the 20/75 acuity figure comes from the project plan, not from a source I checked.
- Camera input is assumed to be sRGB; phones may apply tone mapping or a wide gamut.
- Only dog and cat profiles exist (no human profile: "human" is the unfiltered image). The cat's
  peaks, acuity and its two artistic traits (shadow lift 0.85, peripheral blur) are approximate and
  not verified against primary papers. The lens transmission of the eye is not modelled.

## Sources

Checked against the source text:
- Viénot, F., Brettel, H., Mollon, J. D. (1999). Digital video colourmaps for checking the
  legibility of displays by dichromats. *Color Research & Application* 24(4):243-252. The
  RGB-to-LMS and dichromat matrices were read from the paper; V0 is derived from them (3.5e-6).

Constants checked only through a secondary source or a numerical check:
- Govardovskii, V. I., Fyhrquist, N., Reuter, T., Kuzmin, D. G., Donner, K. (2000). In search of
  the visual pigment template. *Visual Neuroscience* 17(4):509-528. The A1 template constants match
  those quoted in Stavenga, D. G. (2010), *J Comp Physiol A* 196:869-878. The primary paper was not read.
- Wyman, C., Sloan, P.-P., Shirley, P. (2013). Simple analytic approximations to the CIE XYZ
  color matching functions. *J. Computer Graphics Techniques* 2(2):1-11. Compared with the CIE 1931
  2-degree table from CVRL (max error 0.015 / 0.008 / 0.024 for x, y, z).
- Machado, G. M., Oliveira, M. M., Fernandes, L. A. F. (2009). A physiologically-based model for
  simulation of color vision deficiency. *IEEE TVCG* 15(6):1291-1298, via `colour-science`.
- Sharma, G., Wu, W., Dalal, E. N. (2005). The CIEDE2000 color-difference formula. *Color Research
  & Application* 30(1):21-30. The implementation matches all 34 test pairs.
- IEC 61966-2-1 (sRGB) for the transfer curve and primaries.

Cited from memory and **not re-checked**:
- Neitz, J., Geist, T., Jacobs, G. H. (1989). Color vision in the dog. *Visual Neuroscience*
  3(2):119-125. Source of the 429 / 555 nm cone peaks and the ~480 nm neutral point.
- Jacobs, G. H. (1993). The distribution and nature of colour vision among the mammals.
  *Biological Reviews* 68(3):413-471.
- Brettel, H., Viénot, F., Mollon, J. D. (1997). Computerized simulation of color appearance for
  dichromats. *J. Opt. Soc. Am. A* 14(10):2647-2655. Source of the 475 nm anchor.

Test photos are CC0 images from Wikimedia Commons, listed in `samples/SOURCES.md`.
