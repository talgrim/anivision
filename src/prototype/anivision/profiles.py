"""Animal parameter sets (DOG and CAT)."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Profile:
    """A dichromat described by its two cone peak wavelengths (nm) and its acuity."""

    name: str
    s_peak_nm: float  # short-wavelength cone
    l_peak_nm: float  # long/medium-wavelength cone
    expected_neutral_nm: float | None = None  # literature value, for validation only
    acuity_ratio: float = 3.75  # animal/human acuity ratio on a 30 cycles/degree human scale
    # Artistic traits, built into the animal's view (see LowLight.md): a shadow-lift gamma that is
    # always applied (1.0 = none) and extra blur towards the frame edges (0.0 = none).
    shadow_gamma: float = 1.0
    peripheral_blur: float = 0.0


# Dog: S ~429 nm, L/M ~555 nm, neutral point ~480 nm, acuity ~20/75 (8 cycles/degree)
# (Neitz, Geist & Jacobs 1989; to be re-checked against the papers).
DOG = Profile(name="dog", s_peak_nm=429.0, l_peak_nm=555.0, expected_neutral_nm=480.0, acuity_ratio=3.75)

# Cat: S ~450 nm, L/M ~555 nm. Approximate and NOT verified against primary papers: the peaks come
# from the project notes (Python.md) and ganglion-cell recordings (~450 / ~550 nm), and the acuity
# from a behavioural result of ~8-9 cycles/degree (30 / 8.5 = 3.5; Caves, Brandley & Johnsen 2018
# put cats at roughly 1/7 of human acuity). No neutral point is asserted: none was sourced.
# The traits are artistic stand-ins with no sourced values: cats see better in dim light (tapetum
# lucidum), so the cat view always carries the suggested shadow lift (0.85), and its acuity falls
# off towards the edges of the frame (blur doubles in the corners).
CAT = Profile(
    name="cat",
    s_peak_nm=450.0,
    l_peak_nm=555.0,
    acuity_ratio=3.5,
    shadow_gamma=0.85,
    peripheral_blur=1.0,
)
