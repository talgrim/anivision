# 10. Testy, budowanie i wydanie

**Nawigacja:** ⬅️ [9. Zdjęcia: aparat, galeria, zapis](09-zdjecia.md) · [📚 Spis treści](README.md) · [11. Słownik pojęć](11-slownik.md) ➡️

---

## 10.1. Wymagania

| Czego potrzebujesz | Uwagi |
|--------------------|-------|
| **Android Studio** (najnowsza stabilna) | Zawiera JDK, menedżer SDK, emulator |
| **Android SDK Platform 37**, Build-Tools, Platform-Tools | Instalowane z Android Studio (SDK Manager). Plik `local.properties` z `sdk.dir` generuje się automatycznie i jest ignorowany przez git |
| **JDK** | Starsze notatki mówią o JDK 17; plik `gradle-daemon-jvm.properties` żąda toolchaina 25, który Gradle pobiera automatycznie (wtyczka foojay) – przy pierwszym budowaniu potrzebny jest internet |
| **Gradle** | Nie instaluj – wrapper (`gradlew`, wersja 9.6.0) jest w repozytorium |
| **Telefon z Androidem 10+ i OpenGL ES 3.0** | **Zdecydowanie zalecany** fizyczny telefon z włączonym debugowaniem USB. Emulatorowy aparat słabo nadaje się do oceny kolorów. Projektowy telefon testowy: Android 11 (API 30) |
| **Python 3.10+** | Tylko do prototypu |

Uwaga środowiskowa: repozytorium leży na dysku Windows (`C:\Users\Shared\RiderProjects\anivision`). Android Studio i Gradle uruchamia się po stronie Windows; WSL służy do gita i prototypu Pythona. W WSL nie ma `java` w PATH, więc budowanie Androida należy robić z Windowsa.

## 10.2. Budowanie i uruchamianie

Polecenia wydajesz w katalogu `src/android/`. Na Linuksie/macOS: `./gradlew`, w Windows PowerShell także `./gradlew` lub `gradlew.bat`.

| Polecenie | Efekt |
|-----------|-------|
| `./gradlew assembleDebug` | Buduje debugowy APK: `app/build/outputs/apk/debug/` |
| `./gradlew installDebug` | Buduje i instaluje na podłączonym telefonie |
| `./gradlew assembleRelease` | Buduje wydaniowy APK zoptymalizowany przez R8 (patrz niżej) |
| `./gradlew bundleRelease` | Buduje AAB (paczka dla Google Play) |
| `./gradlew test` (lub `testDebugUnitTest`) | Testy na komputerze, bez urządzenia |
| `./gradlew connectedDebugAndroidTest` | Testy na urządzeniu/emulatorze |

**Najprościej w Android Studio:** otwórz katalog `src/android/`, wybierz urządzenie z listy, kliknij ▶ Run.

Przydatne polecenia diagnostyczne (`adb` to narzędzie linii poleceń do rozmowy z telefonem):

```bash
adb devices                           # czy telefon jest widoczny?
adb install -r app-release.apk        # instalacja
adb uninstall com.anivision.app       # odinstalowanie
adb logcat -s AniVisionFps            # FPS w buildzie debug
adb logcat | grep AndroidRuntime      # szukanie awarii
```

## 10.3. Testy – ogólny obraz

Są dwa rodzaje testów Androida:

| Rodzaj | Gdzie | Gdzie działają | Zalety |
|--------|-------|----------------|--------|
| **Jednostkowe (JVM)** | `app/src/test/` | Na komputerze | Szybkie, nie wymagają telefonu |
| **Instrumentalne** | `app/src/androidTest/` | Na telefonie/emulatorze | Mogą używać prawdziwego GPU, aparatu, interfejsu |

Cel całości: pilnować, żeby **to, co liczy aplikacja, było zgodne z prototypem** i żeby kod nie „rozjechał się" po zmianach.

### Testy jednostkowe (JVM)

Katalog roboczy to `app/`, więc pliki prototypu czytane są ze ścieżki `../../prototype/export/*` (pomocnik `ExportFiles.kt`).

| Test | Co sprawdza |
|------|-------------|
| `ColorPipelineGoldenTest` | CPU-owy krok kolorowy daje wyniki z `golden_vectors.json` (≥70 wektorów, tolerancja z pliku, pokrywa v2, v0, cat); round-trip sRGB dla 256 wartości; biel i czerń zachowane; ujemna czerwień z czystego niebieskiego jest obcinana do 0 |
| `VisionProfilesMatchExportTest` | Macierze w Kotlinie (DOG_V2, DOG_V0, CAT_V2) = `matrix.json` (tolerancja 1e-6); macierz kolumnowa to transpozycja; cechy zwierząt (ostrość, gamma, rozmycie obwodowe) zgodne; kopie obronne; walidacja |
| `AcuityTest`, `AcuityPerAnimalTest` | Sigma zgodna ze wszystkimi 12 przykładami z eksportu; granice; skalowanie wg zwierzęcia |
| `NightBoostTest` | Stałe zgodne z eksportem; domyślna siła daje gamma 0,85; monotoniczność |
| `VisionSettingsTest` | Logika `isPassThrough`; `shadowGammaFor` (silniejsze wygrywa, brak sumowania, dolny limit 0,5) |
| `VisionShadersTest` | **Test shaderów bez GPU – na tekście**: funkcje kolorów i `shadowLift` są dosłownie jak w eksporcie; rozjaśnianie po rozmyciu i przed macierzą, tylko w ostatnim przebiegu; deklaracja OES; brak `uSplit`; wzór rozmycia obwodowego |
| `StillGeometryTest`, `FrameStatsTest` | Arytmetyka rozmiarów; raportowanie FPS na syntetycznych znacznikach czasu |
| `PhotoDecoderTest`, `PhotoStoreTest` | `sampleSize`, mapowanie EXIF; nazwa pliku i strefa czasowa |
| `PerspectivePairTest`, `ViewSettingsTest` | Brak duplikatów po stronach; encode/decode; mapowanie trybu na separator; zoom szeroki tylko dla kota |
| `AboutStringsTest` | Czyta `strings.xml` jako tekst i pilnuje sformułowań o przybliżeniu („approximation", „not a model" itd.) |

### Testy instrumentalne

| Test | Co sprawdza |
|------|-------------|
| `GpuGoldenVectorTest` | **Najważniejszy.** Prawdziwe shadery na prawdziwym GPU: każdy wektor wzorcowy podany jako jednolity kolor przez ścieżkę tekstury zewnętrznej do pbuffera i porównany z 8-bitowym wynikiem prototypu. Do tego: tryb przepuszczania, rozmycie nie zmienia jednolitego koloru, podział lewo=normalnie/prawo=pies, każda strona ma własny profil, rozjaśnianie kota, brak sumowania boostu, rozmycie obwodowe mocniejsze na brzegu |
| `StillImageFilterTest` | Karta czterech ćwiartek (czerwona, zielona, niebieska, żółta) – błędy orientacji od razu widać. Obroty o 90°/180°, zgodność z CPU, podział z profilami, zmniejszanie z zachowaniem proporcji (80×60 → 40×30), rozmycie krawędzi |
| `PhotoPipelineTest` | JPEG z obrotem EXIF, redukcja przy dekodowaniu, zapis do MediaStore (nazwa `AniVision_...`, ścieżka `Pictures/AniVision`), wynik pionowy 100×200 |
| `SplitCaptionsTest` | Sprawdzanie pikseli: pigułki, linia, podpisy ze strzałką przy krawędzi, brak rysowania dla 0 i 1 |
| `AboutScreenTest` | Test interfejsu Compose (`createComposeRule`): widoczność zastrzeżenia i wersji, przycisk Close |
| `ExampleInstrumentedTest` | Szablonowy test nazwy pakietu |

### Wektory wzorcowe: system bezpieczeństwa całego projektu

```
        Python (prototyp)
              │ generuje
              ▼
   golden_vectors.json ──┬──→ test_export.py              (Python vs GLSL w float32)
                         ├──→ ColorPipelineGoldenTest     (Kotlin na CPU)
                         └──→ GpuGoldenVectorTest         (prawdziwy GPU!)
```

Ten sam zestaw kolorów jest sprawdzany w trzech niezależnych miejscach. Jeśli ktoś pomyli się w przepisaniu liczby, zmieni kolejność wierszy/kolumn macierzy lub źle zmodyfikuje shader – któryś test to wykryje (tolerancja: ±1 z 255 na kanał).

### Czego testy NIE pokrywają

- **Rozmycia** w wektorach wzorcowych (sprawdzają je osobne testy własności: jednolity kolor niezmieniony, krawędź zmiękczona).
- Wyglądu na realnych scenach – tu liczy się ocena „na oko".

## 10.4. Wydanie (release)

Źródło: `src/android/RELEASING.md`.

### Krok 1: utwórz klucz podpisu (raz)

Każdy APK musi być podpisany. Klucz tworzysz narzędziem `keytool` z Android Studio:

```
keytool -genkeypair ... -alias anivision -keyalg RSA -keysize 4096 -validity 10000
```

Plik klucza (`*.jks`) musi leżeć **poza repozytorium** albo być ignorowany przez git (jest w `.gitignore`). **Zgubienie klucza oznacza, że nigdy nie wydasz aktualizacji** tej aplikacji.

### Krok 2: `keystore.properties`

Skopiuj `keystore.properties.example` do `keystore.properties` (ignorowany przez git) i uzupełnij cztery pola:

```
storeFile=../../anivision-release.jks     # ścieżka względem src/android
storePassword=...
keyAlias=anivision
keyPassword=...
```

### Krok 3: zbuduj

`./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`. Bez `keystore.properties` zbudowany zostanie `app-release-unsigned.apk`, którego telefony nie zainstalują.

### Krok 4: przetestuj na prawdziwym telefonie

Lista kontrolna z `RELEASING.md`: widoki Human/Dog/Cat i Split, rozmycie, nocny boost, zdjęcia, import, Save/Share, ekran About, obrót ekranu, wejście w tło i powrót.

Debugowy i wydaniowy APK są podpisane różnymi kluczami, więc **przed instalacją drugiego** wykonaj `adb uninstall com.anivision.app`.

### R8 i reguła „keep"

W wersji wydaniowej włączony jest **R8**: narzędzie, które zmniejsza i zaciemnia kod (zmienia nazwy klas na `a`, `b`, ...). Czasem psuje to rzeczy, które używają nazw przez refleksję. Wcześniejsza wersja wydaniowa padała przy starcie z błędem `IllegalAccessError: Class kotlin.collections.d extended by class lq is inaccessible` – R8 przenosił prywatne części biblioteki standardowej Kotlina między pakietami. Naprawia to plik `app/src/main/keepRules/rules.keep`:

```
-keepnames class kotlin.** { *; }
```

Jeśli wersja wydaniowa dziwnie się zachowuje, tymczasowo ustaw w `app/build.gradle.kts` `optimization { enable = false }`. Problemy z R8 pojawiają się **tylko w wersji release**.

### Krok 5 (opcjonalnie): większa grupa

`./gradlew bundleRelease` tworzy AAB do wgrania do Google Play Console na ścieżkę wewnętrzną. Odbiorcy potrzebują Androida 10+ i OpenGL ES 3.0; przy instalacji z pliku APK muszą pozwolić na „instalowanie z nieznanych źródeł".

## 10.5. Jak zmienić liczby widzenia (przepływ pracy)

Gdy chcesz np. zmienić położenie szczytu czopka kota:

1. Edytuj `src/prototype/anivision/profiles.py`.
2. Uruchom testy prototypu (`pytest`) – część się zmieni (np. wartości punktu neutralnego).
3. **Zatwierdź** zmiany w kodzie (inaczej eksport zapisze flagę „dirty").
4. Wygeneruj eksport: `PYTHONPATH=. .venv/bin/python scripts/export_constants.py`.
5. Przepisz tablice z `AnimalVisionConstants.kt.txt` do `AnimalVision.kt` (oraz stałe do `Acuity.kt`/`NightBoost.kt`, jeśli się zmieniły), a jeśli zmieniły się funkcje GLSL – także do `VisionShaders.kt`.
6. Uruchom `./gradlew test` – testy zgodności z eksportem dadzą znać, czego brakuje.
7. Uruchom `./gradlew connectedDebugAndroidTest` na telefonie – GPU musi dać te same kolory.

## 10.6. Przepływ pracy w gicie (konwencje projektu)

- Praca w gałęziach `feature/*`, pull requesty zawsze do **`develop`**; `main`/`master` tylko do wydań.
- Commity w formacie **Conventional Commits** (`feat(android): ...`, `test(android): ...`, `docs: ...`).
- Testy w osobnym commicie od kodu; commity możliwie atomowe.

---

**Nawigacja:** ⬅️ [9. Zdjęcia: aparat, galeria, zapis](09-zdjecia.md) · [📚 Spis treści](README.md) · [11. Słownik pojęć](11-slownik.md) ➡️
