"""Animal parameter sets (only DOG for now; a CAT profile can be added later)."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Profile:
    """A dichromat described by its two cone peak wavelengths (nm)."""

    name: str
    s_peak_nm: float  # short-wavelength cone
    l_peak_nm: float  # long/medium-wavelength cone
    expected_neutral_nm: float | None = None  # literature value, for validation only


# Dog: S ~429 nm, L/M ~555 nm, neutral point ~480 nm
# (Neitz, Geist & Jacobs 1989; to be re-checked against the papers).
DOG = Profile(name="dog", s_peak_nm=429.0, l_peak_nm=555.0, expected_neutral_nm=480.0)
