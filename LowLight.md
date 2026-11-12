# Low-light boost: design note

Step 4 of the Android plan. Read `Python.md` (colour maths) and `Android.md` (app plan) first.

## What the feature is

An optional **night boost** that lifts the dark parts of the image while leaving highlights alone.
It is the shadow lift already prototyped in `src/prototype/anivision/lowlight.py` (Phase 4 in
`src/prototype/NOTES.md`), ported to the GPU pipeline and given a switch and a strength slider.

It is **artistic, not physiological**, and the app must not describe it as what a dog sees.

**Cat:** the cat view carries the suggested lift (gamma 0.85) *always*, as an artistic stand-in for
cats' better dim-light vision (tapetum lucidum, which is not modelled). The user's strength
slider does not stack with it: the effective gamma is the smaller of the two. The dog view has no
built-in lift, so "off" still means off there.

## Why it is only a shadow lift

What is known about the dog eye, stated at the level I am confident of:

- The dog retina is dominated by rods, which work at low light. Cones, which give the two-colour
  vision modelled in `Python.md`, are a minority.
- Dogs have a tapetum lucidum, a reflective layer behind the retina that sends light back through
  the photoreceptors for a second chance to be absorbed. This raises sensitivity in dim light.
- Rods carry one signal, so at very low light vision is colourless, and acuity is lower.

(The size of the sensitivity gain and the rod proportion are quoted differently by different
sources, and I have not verified any figure here against a primary source, so the app uses none.)

What a phone frame cannot give us:

- **Absolute scene luminance.** Auto-exposure normalises every frame, so a dim room and a bright
  room can produce similar pixel values. We cannot tell which scotopic or mesopic regime the dog
  would be in, so we cannot switch from cone to rod vision based on the image.
- **The dog's own adaptation state.**

So a physiological model (blend toward a rod response, drop the colour, lose acuity) would be
invented numbers dressed up as science. Decision: **do not model rod vision in v1.**

What can be said honestly: a more sensitive eye makes dark regions of a scene relatively easier to
see. Lifting the shadows of the picture is an artistic stand-in for that. It is applied in linear
light before the colour matrix, so the dichromat colour step still gets the final say on hue.

## The maths (unchanged from the prototype)

```
Y     = clamp(dot(luma, rgb), 1e-6, 1)        luma = Rec.709 luminance weights, linear light
scale = Y < 1 ? Y^(gamma - 1) : 1
rgb  *= min(scale, max(1, 1 / max(r, g, b)))  the cap keeps every channel at or below 1
```

- `gamma` in (0, 1]; 1 is off. Prototype constants: suggested 0.85, strongest 0.7 (0.55 looks hazy).
- Colour ratios are kept, so hue and saturation do not change. Black stays black.
- Without the cap, pure blue at gamma 0.85 scaled to 1.48 and was clipped after the matrix, which
  shifted its hue (found in review, fixed in the prototype).

Pipeline order: decode sRGB, blur, **shadow lift**, matrix, clamp, encode.

## Android decisions

- **Which pixels:** the filtered side only. The Normal side of a split, and the whole Normal view,
  are untouched (the same rule as the blur and the matrix).
- **Where in the shader:** in the final pass (the colour pass, or the vertical blur pass), after the
  blur and before the matrix. The horizontal blur pass is not touched.
- **Strength control:** one slider mapped to gamma, 1.0 (off) to 0.7 (strongest). It is a separate
  switch from the blur, hidden by default, following `android-blur-switch`.
  Slider default when switched on: 0.85, the prototype's suggestion.
- **Photos:** gallery import and captured photos use the same shader through `StillImageFilter`, so
  the saved image matches the preview. A split photo keeps its divider and captions as before.
- **No frame-rate pin.** Whether to pin the preview to 30 fps depends on the daylight fps check
  (Step 0 of the plan) and is a separate decision. This feature does not need it.

## Testing

- Python: lift is already covered in the prototype; add golden vectors for it to the export so the
  GPU can be checked against the same numbers.
- JVM: `VisionShadersTest` checks that the uniform and the lift code are present.
- Instrumented: `GpuGoldenVectorTest` renders the golden inputs with the lift on, checks the split
  (Normal side unchanged), and `StillImageFilterTest` checks that a photo matches the preview.

## Not done (later, if ever)

Scotopic colour loss and extra blur at low light, tied to real scene luminance. This would need a
light-level estimate (for example from exposure metadata) and is a separate research question.
