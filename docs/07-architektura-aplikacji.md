# 7. Architektura aplikacji

**Nawigacja:** ⬅️ [6. Podstawy Androida i Kotlina](06-podstawy-android-kotlin.md) · [📚 Spis treści](README.md) · [8. Potok GPU i shadery](08-potok-gpu.md) ➡️

---

Ścieżka kodu: `src/android/app/src/main/java/com/anivision/app/`. W skrócie: `SRC`.

Zakładamy, że znasz podstawy z [dok. 6](06-podstawy-android-kotlin.md).

## 7.1. Widok z lotu ptaka

```
MainActivity                           ← punkt wejścia (jedna Activity)
 └─ AniVisionTheme                     ← kolory i czcionki
     └─ CameraPermissionGate           ← "czy mam zgodę na aparat?"
         └─ CameraScreen               ← główny ekran (prawie cały stan aplikacji)
             ├─ CameraPreview          ← CameraX + efekt GPU
             ├─ VisionModeToggle, SplitDivider, EffectControls ...
             ├─ ShutterButton, GalleryButton, SettingsButton, AboutButton
             ├─ ImportedPhotoScreen    ← obejrzenie zdjęcia z galerii
             ├─ PerspectiveSettingsDialog
             └─ AboutScreen
```

Pakiety i ich odpowiedzialność:

| Pakiet | Odpowiada za | Zależy od Androida? |
|--------|-------------|---------------------|
| `camera/` | Uprawnienie do aparatu, podgląd z CameraX | tak |
| `photo/` | Robienie, dekodowanie, filtrowanie, zapis, udostępnianie zdjęć, podpisy | tak |
| `ui/` | Ekrany i kontrolki (Compose), preferencje | tak |
| `ui/theme/` | Kolory, motyw, typografia | tak |
| `vision/` | **Logika widzenia**: profile zwierząt, ustawienia, wzory rozmycia i nocy | **nie** (czysty Kotlin) |
| `vision/gl/` | Rysowanie na GPU: OpenGL, shadery, renderer | tak (OpenGL, EGL) |

Taki podział jest zgodny z zasadami SOLID, o które dba projekt (m.in. **jedna odpowiedzialność** na plik): logika w `vision/` jest niezależna od Androida, więc daje się testować na zwykłym komputerze bez telefonu.

## 7.2. `MainActivity.kt`

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()                 // rysuj pod paskiem stanu i nawigacji
        setContent {                       // "oto interfejs w Compose"
            AniVisionTheme {
                CameraPermissionGate {
                    CameraScreen()
                }
            }
        }
    }
}
```

To cały punkt wejścia. `onCreate` jest wywoływane przez system przy starcie. `setContent` przekazuje funkcję composable opisującą całą aplikację. Drzewo: motyw → bramka uprawnień → ekran aparatu.

## 7.3. Pakiet `camera/`

### `CameraPermissionGate.kt`

`@Composable fun CameraPermissionGate(content: @Composable () -> Unit)` – pokazuje `content()` dopiero wtedy, gdy aplikacja ma uprawnienie `CAMERA`. W przeciwnym razie wyświetla `MessageScreen` w jednym z trzech stanów:

1. **Jeszcze nie pytano** – wyjaśnienie i przycisk „Allow camera".
2. **Odmówiono, ale można zapytać ponownie** – przycisk ponowienia.
3. **Zablokowano („nie pytaj więcej")** – przycisk „Open app settings", który otwiera ustawienia aplikacji w systemie.

Technicznie:
- `granted` – zwykły `remember`, `requested` – `rememberSaveable` (przeżywa obrót),
- `rememberLauncherForActivityResult(RequestPermission())` – wywołuje systemowe okno,
- `LifecycleEventEffect(ON_RESUME)` – po powrocie z ustawień sprawdza uprawnienie jeszcze raz,
- funkcje rozszerzające `Context.hasCameraPermission()`, `Context.canAskAgain()`, `Context.openAppSettings()`, `tailrec Context.findActivity()` (rekurencyjnie rozpakowuje `ContextWrapper`, żeby dojść do `Activity`).

### `CameraPreview.kt`

Łączy **CameraX** z naszym efektem GPU.

CameraX to biblioteka Google, która upraszcza pracę z aparatem: zamiast niskopoziomowego Camera2 mówisz „chcę podgląd" (`Preview`), „chcę robić zdjęcia" (`ImageCapture`), a biblioteka dba o cykl życia i różnice między telefonami. Pojęcia:

- **Use case** – jedno zadanie aparatu (podgląd, zdjęcie).
- **`ProcessCameraProvider`** – obiekt wiążący use case'y z cyklem życia ekranu.
- **`PreviewView`** – klasyczny widok Androida wyświetlający podgląd; w Compose osadzany przez `AndroidView`.
- **`CameraEffect`/`SurfaceProcessor`** – „wtyczka", która może przechwycić klatki z aparatu, przetworzyć je (tu: na GPU) i oddać dalej.

Co robi `LaunchedEffect(previewView, lifecycleOwner, lensFacing, attempt)`:

1. pobiera `ProcessCameraProvider`,
2. tworzy `Preview`, nasz `VisionSurfaceProcessor` i (tylko w buildzie debug) `FrameStats` (log FPS pod tagiem `AniVisionFps`),
3. składa `UseCaseGroup` z podglądu, efektu `VisionEffect(processor)`, opcjonalnie `ImageCapture` i `ViewPort`,
4. wywołuje `bindToLifecycle(...)`,
5. `awaitCancellation()` – czeka. Gdy ekran zniknie, współprogram jest anulowany i w `finally` następuje `unbind` oraz `processor.release()`.

`ViewPort` jest ważny: dzięki niemu zdjęcie kadruje się dokładnie tak, jak widać na podglądzie (bez niego zdjęcie miałoby inne proporcje).

Drugi `LaunchedEffect(camera, wideAngle)` ustawia zoom: dla widoku kota `min(minZoomRatio, 1f)` – współczynnik poniżej 1 wybiera ultra-szeroki obiektyw, jeśli telefon go ma.

Błędy (`failed = true`) pokazują `MessageScreen` z przyciskiem ponowienia; ponowienie zwiększa `attempt`, co restartuje efekt.

## 7.4. Pakiet `vision/` – serce domeny (czysty Kotlin)

### `VisionProfile.kt`

```kotlin
class VisionProfile(
    val id: String,
    rowMajor: FloatArray,            // 9 liczb: macierz 3×3 wierszami
    val acuityRatio: Float = 3.75f,
    val shadowGamma: Float = 1f,
    val peripheralBlur: Float = 0f,
)
```

Opis „jak widzi dane zwierzę". `init` waliduje dane (`require`). Metody: `linearMatrix()` (kopia, wierszami), `linearMatrixColumnMajor()` (dla GPU), `applyLinear(r, g, b)` (mnożenie na CPU, do testów).

### `AnimalVision.kt` (`object`)

Trzymacz profili:

- `DOG_V2` (domyślny `DOG`): macierz z tabeli w [dok. 4](04-matematyka-widzenia-zwierzat.md), `acuityRatio = 3.75`,
- `DOG_V0`: zapasowa macierz Viénota,
- `CAT_V2` (domyślny `CAT`): `acuityRatio = 3.5`, `shadowGamma = 0.85`, `peripheralBlur = 1.0`.

Komentarz w pliku: **nie edytuj ręcznie** – wygeneruj eksport w prototypie i przepisz tablice. Test pilnuje zgodności z `matrix.json`.

### `VisionSettings.kt`

Opisuje, co zrobić z *następną klatką* (uproszczony kod klasy jest w [dok. 6](06-podstawy-android-kotlin.md), sekcja 6.2):

| Pole | Znaczenie |
|------|-----------|
| `left` | Profil po lewej stronie separatora (`null` = człowiek) |
| `right` | Profil po prawej stronie |
| `blurExaggeration` | Siła rozmycia; `null` = wyłączone |
| `split` | Położenie separatora jako ułamek szerokości: 0 = cały obraz to „prawa", 1 = cały obraz to „lewa" |
| `shadowGamma` | Nocny boost; `null` = wyłączony |

- `isPassThrough` – prawda, gdy żadna widoczna część nie jest filtrowana (można użyć surowego obrazu).
- `shadowGammaFor(profile)` – zwraca `min(boost użytkownika, boost profilu)`, ograniczone do `[0.5, 1]`; dla `null` – 1.

### `Acuity.kt` (`object`)

Wzór na sigmę (patrz [dok. 4](04-matematyka-widzenia-zwierzat.md)): `sigmaPx(longEdgePx, exaggeration = 1f, acuityRatio = 3.75f)`. Stałe: `HFOV_DEG = 70`, `HUMAN_CUTOFF_CPD = 30`, `MTF_AT_CUTOFF = 0.1`. Mnożnik ograniczony do 0…8.

### `NightBoost.kt` (`object`)

Zamienia siłę suwaka `s` (0…1) na gamma: `gammaFor(s) = 1 − s·(1 − 0.7)`. Stałe: `SUGGESTED_GAMMA = 0.85`, `STRONGEST_GAMMA = 0.7`, `MIN_STRENGTH = 0.1`, `MAX_STRENGTH = 1`, `DEFAULT_STRENGTH ≈ 0.5`.

### `ColorPipeline.kt`

Wersja **referencyjna na CPU** kroku kolorowego (dekodowanie sRGB → macierz → obcięcie → kodowanie → zaokrąglenie do 8 bitów): `ColorPipeline.projectSrgb8(profile, r, g, b)`. Używana wyłącznie w testach – jako „drugi niezależny wykonawca", z którym porównujemy wynik GPU.

## 7.5. Pakiet `vision/gl/` – karta graficzna

Szczegółowo w [dok. 8](08-potok-gpu.md). Tu tylko lista:

| Plik | Rola |
|------|------|
| `EglCore.kt` | Tworzy kontekst OpenGL (EGL): wyświetlacz, konfigurację, kontekst, powierzchnie; obsługuje wymianę buforów |
| `ShaderProgram.kt` | Kompiluje i linkuje shadery; dostęp do atrybutów i uniformów |
| `VisionShaders.kt` | Kod GLSL jako teksty Kotlina |
| `VisionRenderer.kt` | Rysuje jedną klatkę: podział na dwie strony, wybór przebiegów |
| `VisionSurfaceProcessor.kt` | Implementacja `SurfaceProcessor` z CameraX; wątek GL, cykl życia |
| `VisionEffect.kt` | `CameraEffect` – podłącza procesor tylko do podglądu |
| `StillImageFilter.kt` | To samo co renderer, ale dla pojedynczego zdjęcia (poza ekranem) |
| `StillGeometry.kt` | Obliczanie rozmiaru wyniku z uwzględnieniem obrotu i limitu |
| `FrameStats.kt` | Mierzy FPS (debug) |

## 7.6. Pakiet `photo/`

Szczegółowo w [dok. 9](09-zdjecia.md).

| Plik | Rola |
|------|------|
| `PhotoCapturer.kt` | Cały proces robienia zdjęcia |
| `PhotoDecoder.kt` | Wczytanie JPEG z ograniczeniem rozmiaru i odczytem obrotu EXIF |
| `PhotoFilterer.kt` | Dekodowanie + filtr GPU → gotowa bitmapa |
| `PhotoStore.kt` | Zapis do galerii (MediaStore) |
| `PhotoSharing.kt` | Udostępnianie przez systemowe okno |
| `SplitCaptions.kt` | Rysowanie linii podziału i podpisów na zapisanym zdjęciu |

## 7.7. Pakiet `ui/`

### Model widoków

Trzy proste typy opisują, co użytkownik widzi:

- **`Perspective`** (`enum`): `HUMAN` (profil `null`), `DOG`, `CAT` (z `wideAngle = true`).
- **`PerspectivePair`** (`data class`): para `(left, right)` – „co jest pod lewym, a co pod prawym przyciskiem". Reguła: **obie strony nie mogą być takie same** (`init` rzuca wyjątek). Metody: `withLeft`, `withRight` (zwracają `this`, jeśli zmiana stworzyłaby duplikat), `canSetLeft/Right`, `encode()` → `"HUMAN,DOG"`, `decode()`, `DEFAULT = HUMAN|DOG`, `PairSaver` (jak zapisać parę w `rememberSaveable`).
- **`ViewMode`** (`enum`): `LEFT`, `SPLIT`, `RIGHT` – co aktualnie pokazuje ekran.

`ViewSettings.kt` łączy to w gotowe `VisionSettings`:

```kotlin
buildVisionSettings(mode, pair, splitFraction, blurExaggeration, shadowGamma): VisionSettings
```

Mapowanie: `LEFT` → `split = 1`, `RIGHT` → `split = 0`, `SPLIT` → pozycja separatora. Dodatkowe funkcje rozszerzające: `visiblePerspectives`, `showsFilter` (czy widać jakikolwiek filtrowany widok), `wantsWideAngle`.

### `PerspectivePreferences.kt`

Jedyna trwała pamięć aplikacji. **SharedPreferences** (plik `anivision`, klucz `perspectives`). `load()` zwraca zapisaną parę lub domyślną; `save(pair)` zapisuje asynchronicznie (`apply()`). Wszystko inne (tryb, pozycja separatora, ustawienia efektów) po ponownym uruchomieniu wraca do domyślnych.

### Ekrany i kontrolki

| Plik | Rola |
|------|------|
| `CameraScreen.kt` | **Główny ekran, ok. 280 linii.** Trzyma niemal cały stan, składa pozostałe elementy, obsługuje import z galerii |
| `ImportedPhotoScreen.kt` | Pełnoekranowy podgląd przefiltrowanego zdjęcia z galerii; przyciski Close / Save / Share; separator zgłasza zmianę dopiero po puszczeniu palca (każda zmiana to ponowny render na GPU) |
| `AboutScreen.kt` | Przewijana strona: zastrzeżenie, opis koloru, rozmycia, nocy, kota i psa, prywatność, numer wersji. `BackHandler` obsługuje przycisk „wstecz" |
| `MessageScreen.kt` | Wielokrotnego użytku: tytuł + wiadomość + opcjonalny przycisk |
| `VisionModeToggle.kt` | Trzy przyciski segmentowe: [lewy \| Split \| prawy] |
| `SplitDivider.kt` | Przeciągany separator. Pas dotyku 48 dp, linia 2 dp, uchwyt 36 dp ze strzałkami ◂▸. Zakres 5%–95%. Pozycja jest przekazywana jako lambda i czytana przy układaniu (`Modifier.offset { }`), więc przeciąganie **nie wymusza rekompozycji** |
| `EffectControls.kt` | Dwa przełączniki (rozmycie, noc) + suwaki, gdy włączone |
| `EffectToggle.kt` | Okrągły przełącznik z ikoną |
| `BlurSlider.kt` | Suwak 1,0×–3,5×, domyślnie 3,0× |
| `NightBoostSlider.kt` | Suwak 10%–100% |
| `ShutterButton.kt` | Spust migawki (76 dp, kółko ładowania podczas pracy) |
| `GalleryButton.kt`, `SettingsButton.kt`, `AboutButton.kt` | Okrągłe przyciski ikon |
| `PerspectiveSettingsDialog.kt` | Okno „Views": dwie grupy przycisków radiowych (lewy / prawy przycisk: Human, Dog, Cat); opcja zajęta po drugiej stronie jest wyszarzona |
| `theme/Color.kt`, `Theme.kt`, `Type.kt` | Motyw (jasny/ciemny wg systemu, bez dynamicznych kolorów) |

Teksty interfejsu są w `res/values/strings.xml` **po angielsku**; brak polskiej lokalizacji.

## 7.8. Funkcje aplikacji z perspektywy użytkownika

**Start:** bramka uprawnień → ekran aparatu. Domyślnie para `Human | Dog`, tryb `RIGHT` (od razu widok „psi" na całym ekranie), rozmycie i noc wyłączone, separator na 50%.

**Przyciski [Lewy | Split | Prawy]**: trzy tryby. W `SPLIT` pojawia się przeciągany separator.

**Zębatka (ustawienia):** zmiana przyporządkowania lewego i prawego przycisku do: Human / Dog / Cat (zawsze różne). Możliwe jest dowolne z 6 uporządkowanych par.

**Kot:** trzeci widok. Macierz podobna do psiej, dlatego dodano elementy artystyczne (patrz [dok. 4](04-matematyka-widzenia-zwierzat.md)): wbudowane rozjaśnienie cieni, rozmycie rosnące ku brzegom (gdy rozmycie jest włączone) i najszerszy kąt widzenia.

**Rozmycie** (ikona rozmycia): wyłączone domyślnie. Po włączeniu pojawia się suwak 1,0×–3,5×. Dotyczy tylko stron przefiltrowanych, nigdy „Human".

**Nocny boost** (ikona księżyca): wyłączony domyślnie; suwak 10–100%. Opisany w aplikacji jako artystyczny, nie model widzenia nocnego.

**Kontrolki efektów** są widoczne tylko wtedy, gdy na ekranie jest przynajmniej jeden widok nie-Human.

**Migawka:** zapisuje zdjęcie z filtrem. **Galeria:** import. **„i":** ekran „O aplikacji".

## 7.9. Zarządzanie stanem

W projekcie **nie ma ViewModel, Flow ani biblioteki nawigacji**. Stan jest „podniesiony" do jednej dużej funkcji `CameraScreen`. To uproszczenie sprawdza się przy tej skali.

Mechanizmy:

| Mechanizm | Przykłady |
|-----------|-----------|
| `remember { mutableStateOf(...) }` | `saving`, `imported`, `importBusy` (CameraScreen); `granted` (bramka uprawnień) |
| `rememberSaveable` | `mode`, `splitFraction`, `blur`, `blurStrength`, `nightBoost`, `nightStrength`, `showSettings`, `showAbout`, `requested` |
| `rememberSaveable(stateSaver = ...)` | para widoków (`PairSaver`) – własny typ |
| `LaunchedEffect(pair) { prefs.save(pair) }` | zapis par widoków przy zmianie |
| `DisposableEffect(shown)` | `recycle()` bitmapy po jej wymianie |
| lambda `() -> VisionSettings` | przekazanie ustawień wątkowi GPU |

### Jak ustawienia docierają do GPU bez restartu aparatu?

W `CameraScreen` jest funkcja lokalna `currentSettings()`, która z aktualnych wartości stanu buduje `VisionSettings`. Przekazywana jest do `CameraPreview` jako lambda `settings: () -> VisionSettings`. Wątek GPU **wywołuje ją przy każdej klatce**. Ponieważ odczytuje ona stan Compose, przesunięcie separatora albo suwaka działa **od następnej klatki** – bez rekompozycji ekranu i bez ponownego podpinania aparatu. `rememberUpdatedState(settings)` zapewnia, że długo żyjąca lambda zawsze widzi najnowszą wersję.

## 7.10. Wątki w aplikacji

| Wątek | Co na nim działa |
|-------|------------------|
| **Główny (UI)** | Compose, callbacki CameraX (`takePicture`), współprogramy z `rememberCoroutineScope` |
| **`AniVisionGL`** (`HandlerThread`) | Cały podgląd na GPU: kontekst EGL, renderer, każde rysowanie klatki |
| **`Dispatchers.IO`** | Pliki, MediaStore, dekodowanie bitmap |
| **`AniVisionStill`** (jednowątkowy executor) | Filtr GPU pojedynczego zdjęcia, z własnym kontekstem EGL |

Ważne: operacje OpenGL muszą być wykonywane na **tym samym wątku**, na którym utworzono kontekst – stąd dedykowany wątek, a nie współprogram na losowym wątku.

---

**Nawigacja:** ⬅️ [6. Podstawy Androida i Kotlina](06-podstawy-android-kotlin.md) · [📚 Spis treści](README.md) · [8. Potok GPU i shadery](08-potok-gpu.md) ➡️
