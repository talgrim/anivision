# 5. Prototyp w Pythonie

**Nawigacja:** ⬅️ [4. Matematyka widzenia zwierząt](04-matematyka-widzenia-zwierzat.md) · [📚 Spis treści](README.md) · [6. Podstawy Androida i Kotlina](06-podstawy-android-kotlin.md) ➡️

---

Katalog: `src/prototype/`. To miejsce, w którym powstały wszystkie liczby używane przez aplikację. Nie jest potrzebny do uruchomienia aplikacji na telefonie, ale jest potrzebny, jeśli chcesz:

- zrozumieć, skąd wzięła się macierz,
- zmienić parametry (np. szczyt czopka kota),
- zobaczyć efekt na zdjęciu na komputerze.

## 5.1. Czym jest Python w tym projekcie?

Python to język programowania wysokiego poziomu, popularny w obliczeniach naukowych. Używamy go z trzema bibliotekami:

- **NumPy** – szybkie obliczenia na tablicach liczb (obrazy to tablice; macierze też),
- **Pillow** – wczytywanie i zapisywanie obrazów,
- **Matplotlib** – wykresy,
- **pytest** – testy.

(Opcjonalnie `colour-science` – tylko do porównania z cudzą implementacją wariantu V1.)

Wymagany jest Python 3.10+.

## 5.2. Uruchomienie

```bash
cd src/prototype
python -m venv .venv                         # środowisko wirtualne: izolowane biblioteki
.venv/bin/pip install -r requirements.txt    # instalacja bibliotek
.venv/bin/pip install -r requirements-extra.txt   # opcjonalnie, tylko do wariantu V1
.venv/bin/python -m pytest -q                # testy
```

Przetworzenie zdjęcia przez „psie oczy":

```bash
PYTHONPATH=. .venv/bin/python -m anivision wejscie.jpg wyjscie.png --variant v2
```

Z rozmyciem (4× wyolbrzymione) i rozjaśnieniem cieni:

```bash
PYTHONPATH=. .venv/bin/python -m anivision wejscie.jpg wyjscie.png \
    --variant v2 --acuity --exaggeration 4 --shadow-lift 0.85
```

Opcje wiersza poleceń (`cli.py`):

| Opcja | Znaczenie |
|-------|-----------|
| `--variant {v0,v1,v2,cat}` | Którą macierz zastosować (domyślnie `v2`) |
| `--acuity` | Włącza rozmycie (domyślnie wyłączone) |
| `--exaggeration X` | Wyolbrzymienie rozmycia (domyślnie 1.0) |
| `--hfov D` | Pole widzenia aparatu w stopniach (domyślnie 70) |
| `--acuity-ratio R` | Nadpisanie ostrości zwierzęcia |
| `--periphery X` | Rozmycie obwodowe |
| `--gamut {clip,desaturate}` | Strategia dla kolorów spoza ekranu |
| `--shadow-lift GAMMA` | Rozjaśnianie cieni (1.0 = wyłączone) |
| `--no-traits` | Ignoruje wbudowane cechy zwierzęcia (rozjaśnienie, rozmycie obwodowe) |

Program drukuje na stderr informację typu: `v2: N/TOTAL pixels (x%) out of gamut, clipped, blur sigma S px`.

## 5.3. Biblioteka `anivision/` – moduły po kolei

Moduły układają się warstwowo: od podstaw (kolor) do całości (symulacja).

```
colorspace  ─┐
spectra     ─┼→ derive ─→ variants ─→ simulate ─→ cli
profiles    ─┘               ↑          ↑
                           acuity    lowlight, gamut
                  metrics, spectral_strip, export  (narzędzia pomocnicze)
```

### `colorspace.py` – przestrzenie kolorów

Funkcje `srgb_to_linear`, `linear_to_srgb`, macierz `SRGB_TO_XYZ` i biały punkt D65. Obsługuje obrazy jako liczby zmiennoprzecinkowe 0–1 oraz `uint8`/`uint16`.

### `spectra.py` – widma

- **Czułość czopków**: nomogram Govardovskiego i in. (2000) – wzór, który dla zadanego szczytu `lmax` daje całą krzywą (część „alfa" i część „beta").
- **Funkcje dopasowania barw CIE 1931**: przybliżenie analityczne Wymana, Sloana i Shirleya (2013). Opisują „standardowego obserwatora" – sposób, w jaki człowiek odbiera widmo.
- **Widma diod ekranu**: trzy Gaussy (610/550/465 nm, σ = 20 nm), skalowane tak, żeby biel = D65.
- Siatka długości fal: 380–720 nm co 1 nm.

### `derive.py` – wyprowadzanie macierzy

Funkcje:
- `cone_catch_matrix` – macierz C (2×3), całki z iloczynu *czułość × widmo diody*,
- `projection_matrix` – wzór `M = [w a]·([C·w C·a])⁻¹·C`,
- `monochromatic_rgb(475)` – kolor kotwiczny,
- `neutral_point` – szukanie punktu neutralnego (przejście przez zero różnicy sygnałów S/L względem bieli).

Odrzuca kotwice, których zwierzę nie odróżni od bieli (`ValueError`).

### `profiles.py` – zwierzęta

```python
@dataclass(frozen=True)
class Profile:
    name; s_peak_nm; l_peak_nm; expected_neutral_nm=None
    acuity_ratio: float = 3.75
    shadow_gamma: float = 1.0     # artystyczne; 1.0 = brak
    peripheral_blur: float = 0.0  # artystyczne

DOG = Profile("dog", s_peak_nm=429.0, l_peak_nm=555.0, expected_neutral_nm=480.0, acuity_ratio=3.75)
CAT = Profile("cat", s_peak_nm=450.0, l_peak_nm=555.0, acuity_ratio=3.5, shadow_gamma=0.85, peripheral_blur=1.0)
```

`@dataclass(frozen=True)` to python'owy sposób na „klasę tylko z danymi, której nie można zmienić po utworzeniu". Są tylko dwa zwierzęta; „człowiek" jest wyłącznie punktem odniesienia.

### `variants.py` – warianty macierzy

Identyfikatory: `v0`, `v1`, `v2`, `cat`. Każdy to macierz 3×3 działająca na liniowym sRGB (patrz [dok. 4](04-matematyka-widzenia-zwierzat.md)).

### `simulate.py` – cały potok na obrazie

```python
linear  = srgb_to_linear(to_float(arr))
if acuity is not None: linear, sigma = blur_for_image(linear, acuity)
if shadow_gamma != 1.0: linear = lift_shadows(linear, shadow_gamma)
mapped  = apply_matrix(linear, matrix)            # linear @ M.T
n_bad   = count_out_of_gamut(mapped)
encoded = linear_to_srgb(fit_to_gamut(mapped, gamut))
```

To bezpośredni odpowiednik shadera w aplikacji.

### `acuity.py` – rozmycie

Implementacja rozmycia Gaussa przez **transformatę Fouriera (FFT)**: zamiast uśredniać sąsiadów, mnoży się widmo obrazu przez funkcję `exp(−2π²σ²(fx²+fy²))`. Jest to dokładne, a koszt nie zależy od sigmy. Brzegi odbijane lustrzanie (żeby nie było czarnej ramki). Liczy się w świetle liniowym.

Dodatkowo `sigma_px(...)` (wzór z [dok. 4](04-matematyka-widzenia-zwierzat.md)) i rozmycie obwodowe.

### `lowlight.py` – rozjaśnianie cieni

Funkcja `lift_shadows(rgb, gamma)` – wzór z [dok. 4](04-matematyka-widzenia-zwierzat.md). Stałe: `SUGGESTED_GAMMA = 0.85`, `STRONGEST_GAMMA = 0.7`.

### `gamut.py` – strategie wychodzenia poza gamut

`clip` (domyślna) i `desaturate`. Plus stała `LUMA` – wagi jasności `[0.2127, 0.7152, 0.0722]`.

### `metrics.py` – miary

CIELAB, własna implementacja **CIEDE2000** (zgodna z 34 przykładami testowymi Sharma i in.), norma Frobeniusa (odległość między macierzami: pierwiastek z sumy kwadratów różnic wszystkich elementów).

### `spectral_strip.py` – test światłami jednobarwnymi

Renderuje światła 420–680 nm przez macierz i szuka miejsca, gdzie kolor jest szary (minimum chromy CIELAB). Patrz [dok. 4](04-matematyka-widzenia-zwierzat.md).

### `export.py` – most do aplikacji

Generuje cztery pliki w `export/` (opis poniżej).

## 5.4. Skrypty (`scripts/`)

Uruchamiaj z `src/prototype` z `PYTHONPATH=.`; wyniki trafiają do `out/` (ignorowane przez git).

| Skrypt | Co robi |
|--------|---------|
| `make_test_charts.py` | Tworzy 8 syntetycznych wykresów testowych w `samples/charts/` (m.in. koło barw, szarości, niebiesko-żółty) |
| `fetch_samples.py` | Pobiera 10 zdjęć z Wikimedia Commons (tylko licencja CC0), zapisuje pochodzenie w `samples/SOURCES.md` |
| `compare_variants.py` | Arkusze porównawcze oryginał / V0 / V1 / V2 i tabele metryk |
| `plot_spectral_strip.py` | Pasek widmowy: człowiek vs warianty |
| `primaries_sensitivity.py` | Jak bardzo macierz zależy od założonych widm diod i kotwicy |
| `gamut_policies.py` | Porównanie `clip` i `desaturate` |
| `acuity_sweep.py` | Rozmycie dla wyolbrzymień 0, 1, 2, 4, 8, 16 |
| `lowlight_sweep.py` | Rozjaśnianie dla gamma 1.0, 0.85, 0.7, 0.55 |
| `export_constants.py` | **Generuje pliki dla aplikacji** |

## 5.5. Katalog `export/` – kontrakt z aplikacją

```bash
cd src/prototype
PYTHONPATH=. .venv/bin/python scripts/export_constants.py
```

| Plik | Zawartość | Kto go używa |
|------|-----------|--------------|
| `matrix.json` | Źródło prawdy: macierze (dog v2/v0, cat), parametry ostrości, rozjaśniania, konwencje, uwagi, zapisany commit gita | Testy aplikacji porównują z nim stałe w Kotlinie |
| `golden_vectors.json` | 73 kolory wejściowe i oczekiwane wyjścia (dla v2, v0, cat; też przy rozjaśnieniu 0,85 i 0,7) | Testy JVM i instrumentalne |
| `animal_vision.glsl` | Fragmenty shadera: `srgbToLinear`, `linearToSrgb`, `LUMA`, `shadowLift`, `dogView`, stałe `mat3` | Test sprawdza, że `VisionShaders.kt` zawiera je dosłownie |
| `AnimalVisionConstants.kt.txt` | Stałe `floatArrayOf(...)` w Kotlinie | **Kopiowane ręcznie** do `AnimalVision.kt` (rozszerzenie `.txt` – celowo nie jest kompilowany) |

Pliki zawierają hash commita, z którego powstały. Zasada: *najpierw zatwierdź zmiany w kodzie, potem wygeneruj eksport* (w przeciwnym razie flaga „dirty" w pliku mówi, że eksport nie odpowiada żadnemu commitowi).

### Wektory wzorcowe (golden vectors)

Tabela 73 kolorów: czarny, biały, 6 kolorów podstawowych/dodatkowych, 8 szarości, 7 tonów skóry, 10 nazwanych kolorów (pomarańcz, brąz, oliwka, trawa, niebo, ...), 40 losowych (ziarno 20261112). Dla każdego: wejściowy `srgb`, oraz dla każdego wariantu oczekiwany `srgb` (8-bit), wartość liniowa przed obcięciem i znacznik `clipped`. Zalecana tolerancja: **±1** na kanał (8-bit).

Pokrywają **tylko krok kolorowy** (dekodowanie, macierz, obcięcie, kodowanie, zaokrąglenie) i rozjaśnianie. Rozmycia nie pokrywają.

### Pułapki z orientacją macierzy

Przypomnienie z [dok. 3](03-podstawy-obrazu-cyfrowego.md): Python/JSON – wierszami; GLSL `mat3` – kolumnami. Plik `.kt.txt` zawiera obie wersje (`ROW_MAJOR`, `COLUMN_MAJOR`).

## 5.6. Testy prototypu (`tests/`)

Uruchamiane poleceniem `pytest`. Co sprawdzają:

| Plik | Sprawdza |
|------|----------|
| `test_colorspace.py` | Przekształcenia sRGB↔liniowe, obsługa typów, XYZ |
| `test_spectra.py` | Że nomogram ma szczyt dokładnie w `lmax`; zgodność CIE z tabelami; suma widm = biel |
| `test_derive.py` | Własności macierzy: `M·w=w`, rząd 2, `M·M=M`, `C·M=C`; punkt neutralny w ±10 nm od 480 |
| `test_simulate.py` | Potok; V0 zgodne z wyprowadzeniem z artykułu Viénota (3,5e-6); CLI |
| `test_acuity.py` | `k` ≈ 1,366; sigma 1,17 px dla 1920 px; kontrast na granicy = 10%; blur w świetle liniowym |
| `test_gamut.py` | Strategie gamutu; rozjaśnianie (np. gamma 1 = bez zmian, proporcje kolorów) |
| `test_metrics.py` | CIEDE2000 względem wzorców |
| `test_spectral_strip.py` | Przejście przez szarość w oczekiwanym miejscu |
| `test_crosscheck.py` | Porównanie z biblioteką `colour-science` (pomijane, jeśli jej nie ma) |
| `test_export.py` | **Strażnik mostu**: parsuje wygenerowany Kotlin i GLSL, symuluje shader w float32 i porównuje z wektorami wzorcowymi; sprawdza, czy zatwierdzony eksport nie jest nieaktualny |

## 5.7. Wnioski z eksperymentów (skrót z NOTES.md)

- Wszystkie warianty zachowują sygnały czopków psa z dokładnością do ok. 1,4% bieli. V2 **nie została wykazana jako dokładniejsza** niż V0.
- Biblioteka kolorów (V1) i V2 są do siebie blisko (ΔE 1,15); V0 odstaje (ΔE ≈ 4 względem V2).
- Wybór `clip` zamiast `desaturate` – patrz [dok. 3](03-podstawy-obrazu-cyfrowego.md).
- Ostrość: wyolbrzymienie 1.0 jako wartość fizyczna, maks. 8.0.

## 5.8. Bibliografia

**Sprawdzone w źródle:**
- Viénot, F., Brettel, H., Mollon, J. D. (1999). *Digital video colourmaps for checking the legibility of displays by dichromats.* Color Research & Application 24(4).

**Sprawdzone pośrednio / numerycznie:**
- Govardovskii, V. I. i in. (2000). *In search of the visual pigment template.* Visual Neuroscience 17(4).
- Wyman, C., Sloan, P.-P., Shirley, P. (2013). *Simple analytic approximations to the CIE XYZ color matching functions.* JCGT 2(2).
- Machado, G. M., Oliveira, M. M., Fernandes, L. A. F. (2009). *A physiologically-based model for simulation of color vision deficiency.* IEEE TVCG 15(6).
- Sharma, G., Wu, W., Dalal, E. N. (2005). *The CIEDE2000 color-difference formula.* Color Research & Application 30(1).
- IEC 61966-2-1 (sRGB).

**Cytowane z pamięci, niezweryfikowane ponownie:**
- Neitz, J., Geist, T., Jacobs, G. H. (1989). *Color vision in the dog.* Visual Neuroscience 3(2). Źródło szczytów 429/555 nm.
- Jacobs, G. H. (1993). *The distribution and nature of colour vision among the mammals.* Biological Reviews 68(3).
- Brettel, H., Viénot, F., Mollon, J. D. (1997). *Computerized simulation of color appearance for dichromats.* JOSA A 14(10). Źródło kotwicy 475 nm.
- Caves, Brandley & Johnsen (2018) – ostrość kota (wzmianka w komentarzu w kodzie).

---

**Nawigacja:** ⬅️ [4. Matematyka widzenia zwierząt](04-matematyka-widzenia-zwierzat.md) · [📚 Spis treści](README.md) · [6. Podstawy Androida i Kotlina](06-podstawy-android-kotlin.md) ➡️
