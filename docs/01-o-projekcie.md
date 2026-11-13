# 1. O projekcie

**Nawigacja:** [📚 Spis treści](README.md) · [2. Jak widzą ludzie, psy i koty](02-jak-widza-zwierzeta.md) ➡️

---

## 1.1. Co robi aplikacja?

AniVision to aplikacja na telefon z Androidem. Uruchamiasz ją, kierujesz aparat na świat, a ona na żywo pokazuje:

- **obraz normalny** (taki, jaki widzi człowiek),
- **obraz „psi"**,
- **obraz „kocie"**,
- albo **podzielony ekran** – po lewej jedno, po prawej drugie, z przesuwanym palcem separatorem.

Możesz też zrobić zdjęcie (zapisuje się w galerii w katalogu `Pictures/AniVision`), wybrać istniejące zdjęcie z galerii i obejrzeć je „oczami zwierzęcia", a potem zapisać lub udostępnić.

Dwa opcjonalne efekty, domyślnie wyłączone:

- **Rozmycie (ostrość wzroku)** – zwierzęta widzą mniej szczegółów niż człowiek, więc obraz jest rozmywany.
- **Nocny boost** – rozjaśnia cienie (artystyczna namiastka tego, że zwierzęta lepiej widzą w półmroku).

## 1.2. Skąd się bierze „psi" obraz – idea w trzech zdaniach

1. Człowiek ma **trzy** rodzaje receptorów koloru w oku (czopki), pies i kot mają **dwa**. Dlatego rozróżniają mniej kolorów – np. czerwień i zieleń wyglądają dla nich podobnie.
2. Wiedząc, jak czułe na światło są te dwa rodzaje czopków, można policzyć, **jaką informację o kolorze** oko zwierzęcia dostaje z danego piksela, a potem pokazać ją na zwykłym ekranie jako zwykły kolor RGB.
3. Ta operacja to – po uproszczeniu – **pomnożenie koloru każdego piksela przez macierz 3×3**, wykonane na karcie graficznej 30 razy na sekundę (szczegóły w [dokumencie 4](04-matematyka-widzenia-zwierzat.md) i [8](08-potok-gpu.md)).

## 1.3. Dlaczego są dwie części: Python i Android?

Repozytorium ma dwie główne części:

```
anivision/
├── docs/                  ← ta dokumentacja
├── README.md, Python.md, Android.md, LowLight.md   ← starsze notatki (EN)
└── src/
    ├── prototype/         ← PROTOTYP w Pythonie (matematyka, eksperymenty)
    └── android/           ← APLIKACJA na Androida (Kotlin)
```

**Najpierw był prototyp w Pythonie.** Powód: łatwiej i szybciej jest dopracować matematykę i „wygląd" na zwykłych zdjęciach na komputerze niż od razu pisać kod shadera działający na telefonie. Prototyp:

- wylicza macierz kolorów 3×3 dla psa i kota,
- określa, jak rozmywać obraz, żeby oddać gorszą ostrość wzroku,
- określa, jak rozjaśniać cienie,
- zapisuje wszystkie te liczby do plików w `src/prototype/export/`,
- zapisuje też **wektory wzorcowe (ang. *golden vectors*)** – tabelę „kolor wejściowy → oczekiwany kolor wyjściowy".

**Potem powstała aplikacja**, która te liczby przepisuje do Kotlina i GLSL. Testy automatyczne sprawdzają, czy aplikacja daje **te same wyniki** co prototyp. Dzięki temu nie ma ryzyka, że ktoś przepisał liczbę z błędem.

```
Python (prototyp)                       Android (aplikacja)
─────────────────                       ───────────────────
derive.py: liczy macierz     ─┐
acuity.py: wzór na rozmycie   │ export/  ┌─→ AnimalVision.kt  (macierze w Kotlinie)
lowlight.py: rozjaśnianie     ├─────────→├─→ Acuity.kt        (wzór na sigmę)
                              │          ├─→ VisionShaders.kt (kod GLSL)
golden_vectors.json  ─────────┘          └─→ testy: "czy wyniki się zgadzają?"
```

## 1.4. Użyte technologie (bez paniki – wszystko wytłumaczymy)

| Technologia | Do czego służy w projekcie | Gdzie wyjaśniona |
|-------------|----------------------------|------------------|
| **Kotlin** | Język programowania, w którym napisana jest aplikacja | [Dok. 6](06-podstawy-android-kotlin.md) |
| **Jetpack Compose** | Biblioteka do budowania interfejsu (przyciski, suwaki, ekrany) | [Dok. 6](06-podstawy-android-kotlin.md), [7](07-architektura-aplikacji.md) |
| **CameraX** | Biblioteka do obsługi aparatu | [Dok. 7](07-architektura-aplikacji.md), [8](08-potok-gpu.md) |
| **OpenGL ES 3.0 + GLSL** | Rysowanie na karcie graficznej; shadery to małe programy liczące kolor każdego piksela | [Dok. 8](08-potok-gpu.md) |
| **Gradle** | System budowania aplikacji (kompilacja, zależności, podpisywanie) | [Dok. 6](06-podstawy-android-kotlin.md), [10](10-testy-budowanie-wydanie.md) |
| **Python + NumPy** | Prototyp: obliczenia macierzowe i eksperymenty na obrazach | [Dok. 5](05-prototyp-python.md) |

## 1.5. Mapa repozytorium

### `src/prototype/` – Python

```
src/prototype/
├── README.md, NOTES.md        opis i dziennik decyzji (EN)
├── requirements.txt           biblioteki Pythona (numpy, pillow, matplotlib, pytest)
├── anivision/                 biblioteka
│   ├── colorspace.py          sRGB ↔ światło liniowe ↔ XYZ
│   ├── spectra.py             krzywe czułości czopków, funkcje CIE, widma ekranu
│   ├── derive.py              wyliczanie macierzy 3×3
│   ├── profiles.py            profile zwierząt: pies (DOG), kot (CAT)
│   ├── variants.py            warianty macierzy V0, V1, V2, kot
│   ├── simulate.py            cały potok na obrazie
│   ├── acuity.py              rozmycie (ostrość wzroku)
│   ├── lowlight.py            rozjaśnianie cieni (nocny boost)
│   ├── gamut.py               co robić z kolorami spoza ekranu
│   ├── metrics.py             miary różnicy kolorów (CIEDE2000)
│   ├── spectral_strip.py      test na światłach jednobarwnych 400–700 nm
│   ├── export.py              generowanie plików dla Androida
│   └── cli.py                 interfejs wiersza poleceń
├── scripts/                   skrypty: wykresy, porównania, eksport
├── tests/                     testy pytest
├── samples/                   zdjęcia i wykresy testowe
└── export/                    pliki przekazywane do aplikacji
    ├── matrix.json                  główne źródło prawdy (macierze i parametry)
    ├── golden_vectors.json          73 kolory testowe z oczekiwanymi wynikami
    ├── animal_vision.glsl           kod shadera (fragmenty kopiowane do aplikacji)
    └── AnimalVisionConstants.kt.txt stałe w Kotlinie do skopiowania
```

### `src/android/` – aplikacja

```
src/android/
├── settings.gradle.kts, build.gradle.kts   konfiguracja budowania
├── gradle/libs.versions.toml               lista bibliotek i ich wersji
├── RELEASING.md                            jak wydać aplikację
└── app/                                    jedyny moduł aplikacji
    ├── build.gradle.kts                    konfiguracja modułu
    └── src/
        ├── main/                           kod i zasoby aplikacji
        │   ├── AndroidManifest.xml         „dowód osobisty" aplikacji
        │   ├── java/com/anivision/app/     kod Kotlin (tak, folder zwie się „java")
        │   │   ├── MainActivity.kt         punkt wejścia
        │   │   ├── camera/                 aparat i uprawnienia
        │   │   ├── photo/                  zdjęcia: robienie, dekodowanie, zapis
        │   │   ├── ui/                     ekrany i kontrolki (Compose)
        │   │   └── vision/                 logika widzenia + gl/ (karta graficzna)
        │   └── res/                        zasoby: teksty, ikony, motywy
        ├── test/                           testy działające na komputerze
        └── androidTest/                    testy działające na telefonie
```

## 1.6. Przepływ danych – całość na jednym obrazku

```
        ┌──────────── PODGLĄD NA ŻYWO ─────────────┐
        │                                          │
Aparat ─→ CameraX ─→ klatka (tekstura na GPU)      │
                          │                        │
                          ▼                        │
                 VisionRenderer (OpenGL)           │
         ┌────────────────┴─────────────────┐      │
         │ lewa strona: profil lewy          │     │
         │ prawa strona: profil prawy        │     │
         │ dla każdej: [rozmycie] → [boost] │     │
         │             → macierz 3×3 → sRGB  │     │
         └────────────────┬─────────────────┘      │
                          ▼                        │
                  ekran (PreviewView)              │
        └──────────────────────────────────────────┘

        ┌────────────── ZDJĘCIE ───────────────────┐
Migawka ─→ zwykłe JPEG z aparatu ─→ dekodowanie    │
          ─→ TEN SAM renderer, ale poza ekranem    │
          ─→ podpisy „Human | Dog" ─→ zapis JPEG   │
        └──────────────────────────────────────────┘
```

Najważniejsza obserwacja: **podgląd i zdjęcie używają tego samego kodu rysującego**, więc to, co widzisz na ekranie, zgadza się z tym, co zapisze się w galerii.

## 1.7. Uwagi o starszych notatkach

Przy pisaniu tej dokumentacji znaleziono w starszych notatkach (`README.md`, `Android.md`, `Python.md`, `src/prototype/README.md`) kilka nieaktualnych miejsc: opis Androida jako „pustej aplikacji", katalogi `render/` i `assets/shaders/`, które nie istnieją, tylko profil psa, JDK 17 zamiast toolchaina z `gradle-daemon-jvm.properties`. Zostały poprawione. Kilka faktów warto jednak zapamiętać, bo wynikają z kodu:

- Shadery są tekstami (stałymi) w pliku `vision/gl/VisionShaders.kt`, a nie osobnymi plikami `.glsl`.
- Wersja MVP na `ColorMatrixColorFilter` została zastąpiona potokiem GPU; kodu `ColorMatrix` już nie ma.
- W aplikacji **nie ma profilu „człowiek"** jako macierzy. „Człowiek" to brak filtra (`null`), czyli nietknięty obraz z aparatu.
- Plik `Android.md` zachowuje opis fazy 3 jako zapis pierwotnego planu, z adnotacją, że została zastąpiona.

---

**Nawigacja:** [📚 Spis treści](README.md) · [2. Jak widzą ludzie, psy i koty](02-jak-widza-zwierzeta.md) ➡️
