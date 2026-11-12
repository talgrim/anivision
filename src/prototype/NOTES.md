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
- The Wyman CMF fit and the Govardovskii constants were written from memory. The tests check
  peaks, normalisation and tabulated CMF values, but the constants still need re-checking
  against the papers before the README cites them.
