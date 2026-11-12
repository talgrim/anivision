# Anivision: Python Prototype Plan

Companion to `Android.md`. This prototype is built **first**: it settles the color math and the look on still images before any shader or camera code is written.

## 1. Context

### Purpose

Produce a validated, parameterized "dog vision" transform and hand it to the Android app as constants and test vectors:

- a **3x3 matrix** acting on linear RGB (the colour part)
- an **acuity blur** recipe (the spatial part)
- **golden test vectors** (input RGB to expected output RGB) so the Android shader can be checked against the prototype

### Science background

Dogs are **dichromats** with two cone types:

| Cone | Peak λ | Notes |
|---|---|---|
| S | ~429 nm | blue-violet, close to the human S cone |
| L/M | ~555 nm | yellow-green, close to the human L cone |

Consequences:

- Blue and yellow are distinguishable. Red, orange and green collapse to yellowish/brownish/gray.
- The **neutral point** (a monochromatic light that looks achromatic) is around **480 nm**, versus ~498 nm for human deuteranopes. Stock deuteranopia matrices are therefore close but not exact.
- Acuity is roughly 20/75, so fine detail is about 3.75x coarser than for a human.

Reference literature (verify exact citations when writing the README):

- Neitz, Geist & Jacobs (1989), *Color vision in the dog*, Visual Neuroscience. Cone peaks and neutral point.
- Jacobs (1993), review of mammalian color vision.
- Govardovskii et al. (2000), *In search of the visual pigment template*. Used for the cone sensitivity curves.
- Viénot, Brettel & Mollon (1999) and Brettel, Viénot & Mollon (1997). Dichromat simulation method.

### Caveat to keep in all outputs

This simulates what the dog's **cones capture**. It is not what a dog subjectively experiences, which is unknowable. Label results "approximation".

## 2. Approach: three transform variants, compared

| ID | Variant | Purpose |
|---|---|---|
| V0 | **Stock deuteranopia matrix** (Viénot-style) | Cheap baseline. The "quick MVP" the Android app can ship first. |
| V1 | **Cross-check**: Machado/Viénot/Brettel implementations from an existing library, if one is installed (e.g. `colour-science` or `daltonlens`) | Sanity-check V0 and my own code. Optional dependency. |
| V2 | **Dog-specific derivation** from cone sensitivities (the main deliverable) | The matrix that actually models canine cones. |

### V2: derivation

**Step 1. Cone sensitivities.** Build normalized spectral sensitivity curves for λmax = 429 and 555 nm using the Govardovskii A1 nomogram (alpha band plus beta band), sampled 380-720 nm at 1-5 nm.

Optional refinement: multiply by an ocular-media (lens) transmission curve, since dog lenses absorb more short-wave light than human ones. Make this a flag. Ignore it until the base result works.

**Step 2. Display primaries.** Real phone spectra are unknown, so model the sRGB primaries as spectral distributions:

- Approximation: Gaussians centered at about 610 / 550 / 465 nm, with a configurable width.
- Scale so the sum of the three equals the D65 white.
- Run a **sensitivity analysis**: shift the centers by ±10 nm and check how much the final matrix moves.

**Step 3. Dog cone catch per primary.** Integrate primary spectrum times cone sensitivity. This gives a **2x3 matrix C** that maps linear RGB to the dog's (S, L/M) signal.

**Step 4. Re-render onto an RGB display.** A 2-channel signal must be shown on a 3-channel display, so the output color must lie on a chosen 2D plane in RGB space. Choose the plane through **white `w`** and an **anchor color `a`**:

```
M = [w  a] · ([C·w  C·a])^-1 · C
```

Properties:

- `M` is a projection onto span{w, a}. Gray stays gray (`M·w = w`) and `M·M = M` (idempotent).
- `det(M) ≈ 0` (rank 2).
- `M` does not change if `a` is rescaled. Only its direction matters.
- Colors that differ only along ker(C), the direction the dog cannot see, map to the same output. This is exactly the red-green collapse.

**Anchor choice.** Following Viénot/Brettel, use the linear-sRGB coordinates of the **475 nm** monochromatic stimulus (from CIE 1931 color matching functions). Negative components are fine here, because the anchor only defines the plane. Test the 575 nm anchor and a hue-based alternative, and record how much the choice changes the results.

**Step 5. Validation checks:**

- **Neutral point:** find the wavelength where the dog's chromatic signal equals white's. It should land near 480 nm. If not, the cone curves or primaries are wrong.
- **Spectral strip:** render monochromatic stimuli 400-700 nm through `M`. Expected progression: violet/blue, then gray near 480 nm, then yellow, then dark brownish yellow towards red.
- Compare V2 against V0 numerically (Frobenius norm of the matrix difference) and perceptually (mean CIEDE2000 on the test set).

## 3. Spatial and other effects

### Acuity blur

- A 20/75 eye has a minimum angle of resolution of about 3.75 arcmin versus 1 arcmin for 20/20.
- Convert to a Gaussian sigma in pixels: `sigma_px = k · (MAR_deg / 2) · (image_width_px / camera_hfov_deg)`.
- Parameters: `acuity_ratio` (default 3.75), `hfov_deg` (default ~70 for a phone main camera), and `exaggeration` (default 1).
- **Expect the effect to be subtle**, only a pixel or two on a typical photo, because the photo is already resolution-limited. The Android slider will need an exaggeration range, and this prototype should find a sensible one.
- Blur in **linear** RGB.

### Low-light lift (optional, last)

A mild shadow lift, e.g. a gamma on luminance. Mark as artistic, not physiological.

Design, limits and the Android port are in `LowLight.md`.

### Out of scope for v1

UV sensitivity (dog lenses block most UV), flicker fusion/motion, field of view and eye placement, and rod-dominated vision.

## 4. Project layout

```
src/prototype/
├── requirements.txt              # numpy, pillow, matplotlib, pytest; colour-science (optional)
├── anivision/
│   ├── __init__.py
│   ├── colorspace.py             # sRGB <-> linear, XYZ, 8/16-bit and float handling
│   ├── spectra.py                # Govardovskii nomogram, CIE 1931 CMFs, primary SPDs
│   ├── derive.py                 # C matrix, projection M, neutral-point finder
│   ├── simulate.py               # apply M to images (linear light), clipping policy
│   ├── acuity.py                 # Gaussian blur from acuity ratio / FOV
│   ├── profiles.py               # DOG (and later CAT) parameter sets
│   └── cli.py                    # python -m anivision in.jpg out.jpg --variant v2
├── scripts/
│   ├── make_test_charts.py       # synthetic test images
│   ├── compare_variants.py       # contact sheet: original | V0 | V2 | (V1)
│   ├── plot_spectral_strip.py    # human vs dog rendering of 400-700 nm
│   └── export_constants.py       # writes matrix.json, Kotlin and GLSL snippets, test vectors
├── samples/                      # test charts plus a few licence-clean photos
├── out/                          # generated results (gitignored)
├── export/                       # matrix.json, golden_vectors.json (committed)
└── tests/
    ├── test_colorspace.py
    ├── test_derive.py
    └── test_simulate.py
```

Use a venv (`src/prototype/.venv`, gitignored) and Python 3.10+.

## 5. Test material

**Synthetic charts** (generated by script, no licensing issues):

- sRGB primaries and secondaries as patches
- red ball on green grass (flat colors), plus a version with texture and noise
- hue wheel / saturated hue ramp
- neutral gray ramp (must stay neutral)
- blue and yellow patches (must stay distinguishable)
- red vs green vs brown/orange patch set (should become nearly indistinguishable)
- skin-tone and foliage patches

**Real photos:** 5-10 images covering a park/grass scene, a red toy on grass, flowers, sky, a street scene, and indoors. Use only public-domain/CC0 images or the user's own photos, and record the source and licence in `samples/SOURCES.md`.

## 6. Steps

### Phase 0: Setup
1. Create `src/prototype/`, a venv, and `requirements.txt`.
2. Add `colorspace.py` with sRGB/linear conversion and tests (round-trip, known values).

### Phase 1: Baseline (V0)
1. `simulate.py` applies a 3x3 matrix in linear light to an image, with a clipping policy (clip to [0, 1], and count out-of-gamut pixels).
2. Hard-code the stock deuteranopia matrix.
3. `cli.py` and `make_test_charts.py`. Run on the charts and sample photos.
4. **Checkpoint:** a visible red/green collapse, with grays unchanged. This alone is enough for the Android MVP, so export V0 constants early.

### Phase 2: Dog derivation (V2)
1. `spectra.py`: nomogram, CIE CMFs, primary SPDs. Tests: curves peak at the requested λmax, are normalized, and are smooth.
2. `derive.py`: build C, compute M, find the neutral point.
3. Tests: `M·w ≈ w`; `det(M) ≈ 0`; `M @ M ≈ M`; scale-invariance of the anchor; neutral point within about ±10 nm of 480.
4. Primaries-sensitivity analysis (±10 nm shifts, width changes), logged as a table.

### Phase 3: Compare and choose
1. `compare_variants.py`: contact sheets for charts and photos. Save to `out/`.
2. `plot_spectral_strip.py`: human vs V0 vs V2.
3. Metrics: matrix difference V0-V2, mean and max CIEDE2000 on the test set.
4. Decide which matrix ships. Record the decision and reasoning in `src/prototype/NOTES.md`.

### Phase 4: Acuity and extras
1. `acuity.py` with the parameters from section 3, applied in linear light.
2. Pick default and maximum exaggeration values by eye on the photo set.
3. Optional low-light lift.

### Phase 5: Export for Android
`export_constants.py` writes:
- `export/matrix.json`: the 3x3 matrix, parameters used, and the git commit of the code that generated it
- a Kotlin `floatArrayOf(...)` snippet and a GLSL `mat3` snippet (watch the **column-major** order in GLSL and `ColorMatrix` conventions)
- `export/golden_vectors.json`: 50-100 input sRGB colors with expected output sRGB, covering primaries, grays, skin tones, and random samples
- the acuity recipe (sigma formula and defaults)

Then update `Android.md` Phases 3-4 to point at these files.

## 7. Done criteria

- [x] `pytest` passes: color-space round trip, projection properties, neutral point near 480 nm (analytic 486.7 nm, inside the 480 ±10 nm tolerance)
- [x] Red/green/orange/brown patches are clearly collapsed, blue vs yellow stay distinct, grays stay neutral (checked by eye on the charts and photos, see `NOTES.md` Phase 3)
- [x] The spectral strip shows blue, then gray near 480 nm, then yellow (gray at 486.9 nm for true spectral lights; about 498 nm when the lights are rendered through the display model, see `NOTES.md`)
- [x] V0 vs V2 comparison is documented, with a chosen default (V2 ships, V0 is the fallback)
- [x] `export/` contains the matrix, snippets and golden vectors
- [x] `README` states the "approximation" caveat and cites the sources

## 8. Risks and assumptions

| Risk | Mitigation |
|---|---|
| Unknown real display spectra | Gaussian approximation plus sensitivity analysis. If the matrix moves little, it's fine. |
| Plane/anchor choice is partly a convention, not physiology | Document it, compare 475/575 nm anchors, and keep V0 as a fallback. |
| Camera output is already processed (white balance, tone mapping, wide gamut on some phones) | Assume sRGB. Note it for the Android phase. |
| No ground truth for "what a dog sees" | Validate against known constraints (cone peaks, neutral point) and published dog-vision renderings, not against perception. |
| GLSL/Android matrix layout bugs (row vs column major) | Golden vectors test the actual shader output. |
| Literature numbers misremembered | Re-check peaks (429 / 555 nm) and the neutral point (~480 nm) against the cited papers before finalizing. |

## 9. Open questions

1. Is it worth adding the optional `colour-science` dependency for the V1 cross-check, or keep dependencies to numpy and pillow?
   *Resolved:* optional extra (`requirements-extra.txt`); V1 is Machado 2009 deuteranopia.
2. Which photos should be used as test material? Your own, or public-domain images?
   *Resolved:* 10 CC0 photos from Wikimedia Commons (`samples/SOURCES.md`).
3. Should the cat profile (S ~450 nm, L/M ~555 nm) be derived now, since the pipeline makes it nearly free, or deferred?
   *Resolved:* deferred; `profiles.py` has only the dog.
4. Do you want the lens-transmission refinement in v1, or only if V2 vs V0 differences look significant?
   *Resolved:* deferred.
