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

Open for Phase 3: which anchor ships (475 nm per the plan vs the blue/yellow plane).

### Caveats found along the way

- Only white is matched to D65. The Gaussian primaries do not reproduce the real sRGB primaries'
  chromaticities, so the model is a rough stand-in for a real display.
- The neutral point is computed for a true monochromatic light against the display white. The
  spectral-strip check in Phase 3 renders the stimulus through the display model instead, so
  its gray crossing may land slightly away from 486.7 nm.

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
