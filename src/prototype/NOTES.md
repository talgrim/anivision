# Prototype notes

All results simulate what a dog's cones capture. They are an **approximation**, not what a
dog subjectively experiences.

## Phase 2: V2 derivation results

Setup: dog cones S 429 nm / L 555 nm (Govardovskii A1, alpha + beta band), display primaries
modelled as Gaussians at 610 / 550 / 465 nm (sigma 20 nm) scaled so R+G+B has the D65 white
tristimulus, 475 nm anchor, 1 nm grid 380-720 nm. CIE 1931 CMFs come from the Wyman et al. (2013)
analytic fit (within ~0.02 of the tabulated values at the checked wavelengths).

Baseline neutral point: **486.7 nm** (target ~480 nm, tolerance +/-10 nm).

Baseline matrix (linear sRGB, acts on column vectors):

```
[ 0.32542  0.90757 -0.23300]
[ 0.24160  0.67495  0.08345]
[-0.00108  0.00145  0.99963]
```

Verified by tests: `M w = w`, `det M = 0`, `M M = M`, `C M = C` (the dog cannot tell `M x` from
`x`), invariance to the anchor's scale, and `M` annihilates ker(C).

### Primaries sensitivity (475 nm anchor)

`‖M-V0‖` is the Frobenius distance to the stock deuteranopia matrix.

| case | neutral pt (nm) | ‖ΔM‖_F vs baseline | max abs ΔM | ‖M-V0‖_F |
|---|---|---|---|---|
| baseline (610/550/465, sigma 20) | 486.7 | 0.0000 | 0.0000 | 0.327 |
| R centre -10 nm | 486.7 | 0.1717 | 0.1084 | 0.296 |
| R centre +10 nm | 486.7 | 0.1190 | 0.0751 | 0.393 |
| G centre -10 nm | 486.8 | 0.1432 | 0.0907 | 0.294 |
| G centre +10 nm | 486.7 | 0.2327 | 0.1469 | 0.477 |
| B centre -10 nm | 485.1 | 0.0881 | 0.0556 | 0.374 |
| B centre +10 nm | 488.2 | 0.1768 | 0.1116 | 0.297 |
| all centres -10 nm | 485.3 | 0.2342 | 0.1480 | 0.309 |
| all centres +10 nm | 488.1 | 0.1480 | 0.0935 | 0.413 |
| sigma 10 nm (all) | 487.6 | 0.0738 | 0.0467 | 0.365 |
| sigma 15 nm (all) | 487.2 | 0.0443 | 0.0281 | 0.349 |
| sigma 30 nm (all) | 485.6 | 0.1519 | 0.0963 | 0.294 |
| sigma 40 nm (all) | 484.6 | 0.4115 | 0.2612 | 0.397 |

Reading: the neutral point is stable (484.6-488.2 nm), but the matrix is **not** insensitive to
the assumed primaries. A +/-10 nm shift moves it by 0.09-0.23 (Frobenius), the same order as the
V0-vs-V2 gap (0.33). Wide primaries (sigma 40) move it by 0.41. So V2 vs V0 differences are within
what the unknown phone display spectra could produce.

### Anchor choice (baseline primaries)

| anchor | ‖M-M_475‖_F | ‖M-V0‖_F |
|---|---|---|
| 475 nm monochromatic (default) | 0.0000 | 0.3271 |
| 575 nm monochromatic | 0.0788 | 0.4046 |
| sRGB blue primary (0,0,1) | 0.3141 | 0.0653 |
| sRGB yellow (1,1,0): same plane as blue | 0.3141 | 0.0653 |

Reading: the anchor (which 2D plane the output lives on) matters more than the cone curves.
V0's output satisfies R = G, i.e. it lies on span{white, blue}, so anchoring V2 on blue or yellow
reproduces V0 to within 0.065. The 475 nm anchor has a negative red component, so saturated blues
get a negative red channel and are clipped: V2 clips 74% of the blue/yellow chart and 29% of the
hue wheel, against 37% and 18% for V0, and the hue wheel shows a visible kink in the blue region.
Natural-looking colours (the textured ball scene, skin, foliage) clip almost nothing.

Resolved in Phase 3 (see below): the 475 nm anchor ships.

### Caveats found along the way

- Only white is matched to D65. The Gaussian primaries do not reproduce the real sRGB primaries'
  chromaticities, so the model is a rough stand-in for a real display.
- The neutral point is computed for a true monochromatic light against the display white. The
  Phase 3 spectral strip, which renders the stimulus through the display model, turns gray at
  about 498 nm instead (see "Phase 3" below).

## Source checks (2026-11-12)

All three constant sets that were written from memory have now been checked.

- **V0 (Viénot, Brettel & Mollon 1999, Color Res Appl 24:243-252).** Read from the paper's PDF
  text: the RGB-to-LMS matrix (17.8824 43.5161 4.11935 / 3.45565 27.1554 3.86714 /
  0.0299566 0.184309 1.46709) and the deuteranope matrix (rows `1 0 0`, `0.494207 0 1.24827`,
  `0 0 1`) in Eq. 5. `inv(RGB2LMS) @ D @ RGB2LMS` matches `DEUTERANOPIA_VIENOT_1999` to
  3.5e-6 (the code keeps five decimals). `tests/test_simulate.py` now pins this. The paper does
  not print the final RGB-to-RGB matrix, so the five-decimal values are derived, not copied.
- **Govardovskii et al. 2000 (Vis Neurosci 17:509-528).** I did not reach the paper itself.
  The constants match Stavenga 2010 (J Comp Physiol A 196:869-878, PMC2962788), which quotes
  them: A=69.7, B=28, C=-14.9, D=0.674, b=0.922, c=1.104,
  a=0.8795+0.0459 exp(-(lmax-300)^2/11940); beta band A=0.26, peak 189+0.315 lmax, width
  -40.5+0.195 lmax. That page writes x=(lambda/lmax)-1 (its equation is an image), but the code
  uses x=lmax/lambda. A numeric check settles it: the raw alpha band equals 1.0008 at x=1 and the
  normalised curve peaks exactly at lmax for 430, 481, 555 and 700 nm. The stated form of x is a
  transcription quirk of that page, not a code bug. The equation image itself is unconfirmed.
- **Wyman, Sloan & Shirley 2013 CMF fit.** The lobe constants were not re-read, but the fit was
  compared with the CIE 1931 2-degree table from CVRL (`ciexyz31_1.csv`, 380-720 nm, 1 nm).
  Maximum absolute errors: x 0.015, y 0.008, z 0.024 (z peaks at 1.78, so about 1.3%). RMS 0.003
  to 0.007. The sums over the grid agree to within 0.1% (106.8 for all three). That is more than
  accurate enough for this prototype.

## V1 cross-check (colour-science, 2026-11-12)

V1 is the Machado, Oliveira & Fernandes (2009) deuteranopia matrix (severity 1.0) from the
optional `colour-science` package (0.4.7). The library has no Viénot implementation, so it
cannot cross-check V0 directly (V0 was checked against the paper instead, see above). V1 is
still a useful independent reference: a different construction of the same two-cone idea.

```
V1 = [[ 0.36732  0.86065 -0.22797]
      [ 0.28009  0.67250  0.04741]
      [-0.01182  0.04294  0.96888]]
```

- Rows sum to 1 (grays stay gray) and the matrix is numerically rank 2. It is not an exact
  projection (`V1 @ V1 - V1` reaches 0.036), unlike V0 and V2.
- Frobenius distances: **‖V1−V2‖ = 0.098**, ‖V1−V0‖ = 0.294, ‖V0−V2‖ = 0.327.
  V1 is much closer to V2 than to V0.
- V1 and V2 both have a negative blue-to-red term in the first row (-0.228 and -0.233). V0 has
  none. So the 475 nm anchor's negative red is not a quirk of my derivation: an independently
  built physiological model lands on almost the same matrix.
- Caveat: Machado's matrix models a human deuteranope (human cone fundamentals), not a dog.
  Treat it as evidence about the *structure* of a physiological derivation, not as proof that
  V2's dog-specific numbers are right.
- The same tests also compare `colorspace.py` and `cie_cmf` with the library: the sRGB transfer
  functions agree to 1e-9; the RGB-to-XYZ matrix and D65 white differ by up to 2e-4 because
  `colour` uses the 4-decimal IEC matrix and xy-derived white, and ours is the unrounded
  matrix with the conventional Z = 1.08883.
- `tests/test_crosscheck.py` is skipped when `colour-science` is absent. The CLI accepts
  `--variant v1` and exits with an install hint if the package is missing.
- Consequence for the Phase 3 anchor decision: V1 supports keeping the 475 nm anchor, since
  an independently derived matrix shares V2's negative-red structure and its clipping.

## Phase 3: compare and choose (2026-11-12)

Run `scripts/compare_variants.py` and `scripts/plot_spectral_strip.py`; outputs go to `out/`
(gitignored). Test set: 8 synthetic charts + 10 CC0 photos (`samples/SOURCES.md`). Variants:
V0 (Viénot), V1 (Machado 2009), V2 (dog, 475 nm anchor) and **V2b** (analysis only: V2 anchored
on the sRGB blue/yellow plane). CIEDE2000 (own implementation, matches all 34 Sharma et al. test
pairs and `colour`) is computed between the 8-bit simulated images, after clipping.

### Matrix distances (Frobenius)

|  | V0 | V1 | V2 | V2b |
|---|---|---|---|---|
| V0 | 0 | 0.294 | 0.327 | 0.065 |
| V1 | 0.294 | 0 | 0.098 | 0.296 |
| V2 | 0.327 | 0.098 | 0 | 0.314 |

### Image differences between variants (CIEDE2000, all 18 images)

| pair | mean | p99 | max (worst image) |
|---|---|---|---|
| V0 vs V2 | 4.00 | 7.1 | 14.8 (blue/yellow chart) |
| V0 vs V1 | 3.34 | 6.1 | 12.9 |
| V1 vs V2 | 1.15 | 2.9 | 7.0 |
| V0 vs V2b | 0.64 | 2.4 | 7.4 |
| V2 vs V2b | 3.88 | 6.9 | 14.6 |

On the photos alone, V0 vs V2 is 3.19 mean / 6.1 p99 / 12.2 max; on the charts 5.02 / 8.3 / 14.8.
For scale, the mean change from the original image to a variant ranges from 3.7 to 23.9 across
the coloured images (0 for the gray ramp).

### Clipping (pixels outside the sRGB gamut after the matrix, clipped per channel)

| set | V0 | V1 | V2 | V2b |
|---|---|---|---|---|
| charts, mean | 11.2% | 14.6% | 18.4% | 9.2% |
| photos, mean | 1.3% | 1.3% | 0.9% | 0.5% |
| blue/yellow chart | 37.2% | 49.3% | 73.9% | 37.2% |
| worst photo | 5.1% (orange buoy) | 4.0% | 3.1% (autumn) | 1.6% |

### Does the output keep the dog's cone signals?

Relative error of the (S, L) cone signals, output vs input, divided by white's signal (mean over
images of the per-image mean): charts V0 0.007, V1 0.014, V2 0.003, V2b 0.001; photos V0 0.003,
V1 0.005, V2 0.001, V2b 0.001. **Every variant keeps the dog's cone signals to within about
1.4% of white.** (Dog cones, S 429 / L 555 nm, are close to the human deuteranope's, so V0 is
already nearly dog-equivalent.) The measure shares V2's display model, so it is a consistency
check, not independent validation.

Consequence: the variants are **not** separated by physiological accuracy. They differ in which
2D plane of colours they display, and that is a convention (the dog cannot tell the outputs from
the input). V2 vs V0 is mostly an anchor effect: V2b reproduces V0 to ΔE 0.64.

### Appearance: where the variants differ

- Red/green: all four collapse them to the same olive and yellow tones.
- Blues: V0 and V2b push blues toward violet (their output has R = G). V1 and V2 keep blues
  close to the original. On the blue/yellow chart the mean change from the original is 6.5 ΔE
  for V0 and 6.5 for V2b, but 3.7 for V1 and 4.0 for V2.
- Saturated blues pay for this in V1 and V2: the 475 nm plane has a negative red coefficient,
  so highly saturated blues leave the gamut and clip (table above). The hue wheel shows a
  visible kink in the blue region. Natural photos clip little (V2 0.9% on average, at most 3.1%).

### Spectral strip (`out/spectral_strip.png`)

All variants render blue, then gray, then yellow. Gray crossings (least CIELAB chroma):

| strip | gray crossing (nm) |
|---|---|
| V2, true spectral lights (rendered straight from the dog's cone signals) | 486.9 |
| analytic neutral point | 486.7 |
| V2, lights as shown on the display model | 498.0 |
| V0 and V1, lights as shown on the display model | 499.9 |

- The 486.9 nm crossing confirms the derivation end to end. It is within the 480 ±10 nm target.
- A camera pixel for a narrow-band light is a human-metameric RGB. The model then treats it as
  the broad Gaussian primaries, whose dog cone catch is not that of the real narrow line. So
  through the display model the visible crossing sits about 11 nm higher. Over every case in the
  primaries table (all centres ±10 nm, sigma 10-40 nm) it stays within 495-507 nm, against
  484.6-488.2 nm for the analytic value. It is a systematic effect of the model, not noise.
  The done criterion "gray near 480 nm" therefore holds for true spectral lights (486.9 nm) and
  not for display-rendered ones (498 nm). Real scenes are broadband, so this mostly affects
  saturated cyan-green colours.
- In the true-spectrum row every wavelength above about 560 nm is the same saturated yellow:
  with two cones, lights beyond the L peak differ only in intensity, and each is scaled so its
  strongest cone signal equals white's.

### Decision: ship V2 (475 nm anchor) as the dog default; keep V0 as the fallback

Why V2:
1. It is derived from the dog's cone sensitivities with a documented chain, and `C M = C` holds
   exactly in the model (checked by tests).
2. An independently built physiological matrix (V1, Machado 2009) lands close to it (ΔE 1.15,
   Frobenius 0.098), and both keep blues blue. V0, the outlier, comes from a simplification
   whose output plane is R = G.
3. On real photos it clips least of the three (0.9% vs 1.3% for V0).
4. It disturbs the blue/yellow axis less (4.0 vs 6.5 ΔE on the blue/yellow chart).

What this decision does not claim:
- V2 is **not** shown to be more accurate than V0. Both keep the dog's cone signals; the plane is
  a convention. The 475 nm anchor comes from human-dichromat practice (Brettel, Viénot & Mollon
  1997, quoted from memory and not re-checked); there is no dog data behind it.
- V2 depends on the Gaussian display model. A ±10 nm primaries shift moves the matrix by
  0.09-0.23, comparable to the V0-V2 gap, so V2 and V0 are within the uncertainty of the
  display spectra.
- The ΔE gap between V0 and V2 on photos is modest (3.2 mean, 6.1 p99): visible, not dramatic.

Keep V0 selectable (`--variant v0`) and export both matrices in Phase 5. The Android code should
take the matrix as data so the default can change without a code change.

### Follow-ups this surfaced

- Saturated blues clip under V2. Try a gentler gamut policy (for example scaling toward white or
  compressing chroma instead of clipping each channel) in Phase 4, and mirror it in the shader.
- Brettel et al. use two half-planes (475 nm on the blue side, 575 nm on the yellow side); V2
  uses a single plane. The 575 nm anchor moves the matrix by only 0.079, so this is low priority.
- The display model remains the largest unknown. Real phone primaries would tighten V2.

## Phase 4: acuity, gamut policy and shadow lift (2026-11-12)

Pipeline order in `simulate()`: decode sRGB -> linear, **acuity blur**, **shadow lift**,
colour matrix, gamut fit, encode. The first three are linear (or a per-pixel scale), so they
commute with the matrix and nothing is clipped before the blur.

### Acuity blur (`anivision/acuity.py`, `scripts/acuity_sweep.py`)

```
cutoff       f_c   = 30 cpd / acuity_ratio                 (dog: 8 cycles/deg)
sigma_px     = k * (acuity_ratio / 60 / 2) * (long_edge_px / hfov_deg) * exaggeration
k            = 2 sqrt(2 ln(1/m)) / pi = 1.366   for m = 0.1
```

- The plan's formula leaves `k` unspecified. I fixed it with an explicit criterion: the Gaussian's
  contrast transfer `exp(-2 pi^2 sigma^2 f^2)` falls to **m = 0.1 at the dog's cutoff frequency**.
  A test checks this at 8 cpd. A different m only changes the baseline; `exaggeration` scales on top.
- Defaults: `acuity_ratio` 3.75, `hfov_deg` 70 (along the **long edge**, so portrait and landscape
  frames from one camera get the same blur per degree), `exaggeration` 1.
- Blur is done in linear light by an FFT Gaussian with mirrored edges (exact, same cost for any sigma;
  numpy only). A test confirms a black/white checker averages to sRGB 0.735, not 0.5.
- The viewer's own eye blur is not subtracted (it depends on viewing distance, which is unknown).

Sigma in pixels (baseline x1 and the exaggeration steps I looked at):

| exaggeration | 1920 px | 1440 px | 1080 px | 640 px | contrast kept at 8 cpd |
|---|---|---|---|---|---|
| x1 | 1.17 | 0.88 | 0.66 | 0.39 | 10% |
| x2 | 2.34 | 1.76 | 1.32 | 0.78 | 0.01% |
| x4 | 4.68 | 3.51 | 2.63 | 1.56 | ~0 |
| x8 | 9.37 | 7.03 | 5.27 | 3.12 | ~0 |
| x16 | 18.74 | 14.05 | 10.54 | 6.25 | ~0 |

Looked at by eye (100% crops of the 1920 px photos, plus whole frames; `out/acuity_sweep_*.png`):
x1 is a faint softening of fine texture (gravel, hair strands), nearly invisible on a whole frame,
as `Python.md` predicted. x2 clearly softens texture while shapes and features stay intact. x4 is
unmistakably blurry vision, with the dog and the face still readable. x8 is heavy: facial features
become blobs but the scene is still recognisable. x16 destroys the content.

**Product context (from the user):** the Android app will have a switch to show or hide the blur,
**hidden by default**. So the blur is an explicit opt-in, and the exaggeration default only applies
once someone turns it on. The CLI matches: no blur unless `--acuity` is given.

**Chosen: default exaggeration 1.0 (the physical baseline), maximum 8.0.** At preview sizes the
default is barely visible (sigma 0.4-0.7 px at 640-1080 px). Because the blur is now behind a
switch, someone who turns it on probably expects to see it, so an on-state default of roughly 3-4
is worth considering (open; a one-number change in `acuity.py`). The Android code must
compute sigma from the actual frame's long edge using the formula above, not hard-code pixels.

### Gamut policy (`anivision/gamut.py`, `scripts/gamut_policies.py`)

Phase 3 flagged that V2 clips saturated blues, and suggested a gentler policy. I tried
`desaturate`: pull an out-of-gamut colour toward the gray of equal luminance, just far enough to
fit. Scored against the unclipped colour the matrix asked for, on the out-of-gamut pixels:

| policy | pixels | mean dE00 | mean dL* | median hue error | median chroma kept |
|---|---|---|---|---|---|
| clip (per channel) | 338,827 | 3.35 | 1.37 | 2.2 deg | 99% |
| desaturate | 338,827 | 4.49 | 0.00 | 0.3 deg | 84% |

`desaturate` keeps lightness and hue but gives up chroma, and CIEDE2000 penalises that more than
`clip`'s small lightness and hue shifts. Visually it is worse on saturated colours: pure blue turns
into a dim steel blue and pure yellow into pale cream. On the photos the two are indistinguishable.

**Chosen: keep `clip` as the default.** This reverses the Phase 3 follow-up. `desaturate` stays
available (`--gamut desaturate`) so the comparison can be rerun, but it is not recommended. The
Android shader should simply clamp each channel.

### Shadow lift (`anivision/lowlight.py`, `scripts/lowlight_sweep.py`)

Artistic, not physiological, and off by default. A gamma on luminance that keeps colour ratios
(`rgb * Y^(gamma-1)`), so hue and saturation are unchanged and black stays black. By eye on dark
and mid-tone photos: gamma 0.85 opens up shadows naturally, 0.7 is the strongest lift before the
blacks look veiled, 0.55 is hazy. **Suggested 0.85, strongest 0.7.**

### CLI

`--acuity [--exaggeration X --hfov D --acuity-ratio R]`, `--gamut clip|desaturate`,
`--shadow-lift GAMMA`; the status line reports the blur sigma in pixels.

## Phase 5: export for Android (2026-11-12)

`scripts/export_constants.py` (logic in `anivision/export.py`) writes `export/`:
`matrix.json`, `golden_vectors.json`, `AnimalVisionConstants.kt.txt`, `animal_vision.glsl`.
Both V2 (default) and V0 (fallback) are exported, so the app can switch matrix without a code
change. `matrix.json` records the generating git commit and whether the sources were dirty.

- **Layouts.** JSON and Python are row-major. GLSL `mat3(...)` arguments and
  `glUniformMatrix3fv(..., false, ...)` are column-major. `ColorMatrix` is 4x5 row-major on
  gamma-encoded values (MVP only). `tests/test_export.py` rebuilds the matrix from each snippet
  with the convention of its platform and checks it equals the Python matrix.
- **Golden vectors.** 73 sRGB colours (8 primaries/secondaries and black/white, 8 grays, 7 skin
  tones, 10 other named colours, 40 seeded random) with the expected 8-bit output and unclipped
  linear value for both variants. They cover the colour step only (decode, matrix, clamp, encode),
  not blur or shadow lift. A float32 numpy re-implementation of the shader maths, fed only from
  the exported GLSL constants, reproduces them within 1 per channel. The GLSL itself was **not
  compiled or run** here; that is the Android phase's job.
- **Acuity recipe** is exported as parameters (`k`, `acuity_ratio`, `hfov_deg`, exaggeration
  default 1.0 and maximum 8.0) plus a Kotlin `acuitySigmaPx()`, with `enabled_by_default: false`.
  Decision (from the user): the on-state default stays at the physical 1.0 even though that is only
  0.4-0.7 px at preview sizes; the app can expose exaggeration as a slider.
- **Staleness guard.** `test_committed_export_is_not_stale` fails if the matrices or recipes change
  and the export was not regenerated. Commit the code first, then regenerate, so the recorded
  commit is the one that produced the numbers.
