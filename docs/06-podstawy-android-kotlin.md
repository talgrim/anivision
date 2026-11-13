# 6. Podstawy Androida i Kotlina

**Nawigacja:** ⬅️ [5. Prototyp w Pythonie](05-prototyp-python.md) · [📚 Spis treści](README.md) · [7. Architektura aplikacji](07-architektura-aplikacji.md) ➡️

---

Ten dokument jest dla osoby, która **nigdy nie pisała aplikacji na Androida ani kodu w Kotlinie**. Tłumaczymy tylko to, co występuje w AniVision, na przykładach z tego projektu.

## 6.1. Czym jest aplikacja na Androida?

Aplikacja na Androida to **paczka (plik APK)** zawierająca:

- skompilowany kod,
- zasoby (teksty, ikony, kolory),
- plik **AndroidManifest.xml** – deklarację: „jak się nazywam, jakich uprawnień potrzebuję, który ekran uruchomić jako pierwszy".

System Android uruchamia aplikację jako osobny proces, daje jej własną, odizolowaną pamięć i pyta użytkownika o zgodę na wrażliwe zasoby (np. aparat).

### Poziomy API

Każda wersja Androida ma numer API. Projekt deklaruje:

| Ustawienie | Wartość | Znaczenie |
|------------|---------|-----------|
| `minSdk` | **29** (Android 10) | Najstarszy system, na którym zadziała. Wybrany m.in. dlatego, że od API 29 zapis zdjęć do galerii nie wymaga uprawnienia do pamięci (*scoped storage*) |
| `targetSdk` | 37 | Wersja, pod którą aplikacja była testowana i dostosowana |
| `compileSdk` | 37 | Wersja API, z którą kompiluje się kod |

## 6.2. Język Kotlin w pigułce

Kotlin to język programowania zaprojektowany m.in. przez firmę JetBrains, od 2019 roku preferowany przez Google do tworzenia aplikacji na Androida. Działa na maszynie wirtualnej Javy (JVM); kod w katalogu `java/` to właśnie pliki `.kt` (nazwa katalogu to historyczny zwyczaj).

### Zmienne i typy

```kotlin
val nazwa = "AniVision"        // val = wartość stała (nie można przypisać drugi raz)
var licznik = 0                // var = zmienna (można zmienić)
val ułamek: Float = 0.5f       // typ można podać jawnie; f oznacza Float
```

Kotlin ma **wnioskowanie typów** – zwykle nie trzeba pisać typu, kompilator sam go odgadnie.

### Funkcje

```kotlin
fun dodaj(a: Int, b: Int): Int {
    return a + b
}

fun dodaj2(a: Int, b: Int) = a + b      // krótka forma (jedno wyrażenie)
```

Argumenty mogą mieć **wartości domyślne** i być przekazywane **po nazwie**:

```kotlin
// z pliku Acuity.kt (uproszczone)
fun sigmaPx(longEdgePx: Int, exaggeration: Float = 1f, acuityRatio: Float = 3.75f): Float { ... }

sigmaPx(1920)                                   // użyje wartości domyślnych
sigmaPx(1920, acuityRatio = 3.5f)               // nazwany argument
```

### Null-safety: koniec z „NullPointerException"

W Kotlinie typ **domyślnie nie może być pusty (`null`)**. Jeśli chcesz dopuścić brak wartości, dodajesz `?`:

```kotlin
val profil: VisionProfile? = null        // może być brak profilu
val macierz = profil?.linearMatrixColumnMajor() ?: IDENTITY_3
```

- `?.` – „wywołaj, jeśli nie null, w przeciwnym razie wynik null",
- `?:` – „operator Elvisa": „jeśli lewa strona jest null, użyj prawej".

**W projekcie `null` ma znaczenie:** `VisionProfile? = null` oznacza „człowiek" (obraz bez filtra). `blurExaggeration: Float? = null` oznacza „rozmycie wyłączone".

### Klasy i `data class`

```kotlin
// z VisionSettings.kt (uproszczone)
data class VisionSettings(
    val left: VisionProfile? = null,
    val right: VisionProfile? = null,
    val blurExaggeration: Float? = null,
    val split: Float = 0f,
    val shadowGamma: Float? = null,
)
```

`data class` to klasa przeznaczona głównie do **przechowywania danych**. Kompilator sam dopisuje `equals`, `hashCode`, `toString` oraz `copy(...)` – np. `ustawienia.copy(split = 0.3f)` tworzy kopię ze zmienioną jedną wartością.

Wewnątrz klasy można mieć **właściwości obliczane**:

```kotlin
val isPassThrough: Boolean
    get() = (split >= 1f || right == null) && (split <= 0f || left == null)
```

### `enum class`

Typ wyliczeniowy – zamknięty zbiór wartości, które mogą mieć własne dane:

```kotlin
// z Perspective.kt (uproszczone)
enum class Perspective(val label: Int, val profile: VisionProfile?, val wideAngle: Boolean = false) {
    HUMAN(R.string.perspective_human, null),
    DOG(R.string.perspective_dog, AnimalVision.DOG),
    CAT(R.string.perspective_cat, AnimalVision.CAT, wideAngle = true),
}
```

Jedna zmienna typu `Perspective` to dokładnie jedno z: `HUMAN`, `DOG`, `CAT`.

### `object` i `companion object`

`object` tworzy **singleton** – jedyną instancję, bez konieczności tworzenia obiektu. W projekcie: `Acuity`, `AnimalVision`, `NightBoost`, `VisionShaders`. Użycie: `Acuity.sigmaPx(...)`.

`companion object` to odpowiednik „statycznych" składowych klasy (stałe, funkcje fabrykujące): `PerspectivePair.DEFAULT`, `VisionSettings.MIN_SHADOW_GAMMA`.

`const val` to stała znana już przy kompilacji.

### Funkcje rozszerzające (extension functions)

Pozwalają „dopisać" metodę do istniejącego typu, bez dziedziczenia:

```kotlin
// ViewSettings.kt – dopisujemy metody do enum ViewMode (uproszczone)
fun ViewMode.showsFilter(pair: PerspectivePair): Boolean = ...
```

Wywołanie: `mode.showsFilter(pair)` – wygląda tak, jakby `showsFilter` była częścią `ViewMode`.

### Lambdy i funkcje jako wartości

Funkcję można przekazać jako argument:

```kotlin
// EffectToggle: gdy użytkownik przełączy, wywołaj podaną funkcję
onCheckedChange: (Boolean) -> Unit
```

Typ `(Boolean) -> Unit` czytamy: „funkcja biorąca `Boolean` i nic nie zwracająca (`Unit` ≈ void)".

**Lambda** to zapis funkcji w miejscu użycia: `{ nowaWartość -> blur = nowaWartość }`. Jeśli lambda jest ostatnim argumentem, można ją wyciągnąć za nawias – tzw. *trailing lambda*:

```kotlin
setContent {          // to jest lambda przekazana jako argument
    AniVisionTheme { ... }
}
```

W projekcie często spotkasz typ `() -> VisionSettings` – „funkcja bez argumentów zwracająca ustawienia" – wywoływaną **co klatkę**, aby zawsze mieć najnowsze ustawienia (patrz [dok. 7](07-architektura-aplikacji.md)).

### `when` – lepszy `switch`

```kotlin
val deg = when (orientation) {
    ORIENTATION_ROTATE_90 -> 90
    ORIENTATION_ROTATE_180 -> 180
    ORIENTATION_ROTATE_270 -> 270
    else -> 0
}
```

`when` jest wyrażeniem – zwraca wartość.

### Funkcje zakresu: `apply`, `let`, `also`, `use`

```kotlin
HandlerThread("AniVisionGL").apply { start() }   // utwórz i od razu skonfiguruj
profil?.let { zrobCosZ(it) }                      // jeśli nie null, użyj jako `it`
strumien.use { ... }                              // wykonaj i automatycznie zamknij
```

### Walidacja: `require`, `check`

```kotlin
require(acuityRatio > 0f) { "acuityRatio must be positive" }   // sprawdza argumenty; rzuca wyjątek
```

### Destrukturyzacja

```kotlin
val (width, height) = StillGeometry.outputSize(...)   // rozpakowanie pary na dwie zmienne
```

### Szablony napisów

```kotlin
val nazwa = "AniVision_${data}.jpg"       // wstawia wartość zmiennej do napisu
val shader = """ ... $COLOUR_FUNCTIONS ... """   // """ = napis wielolinijkowy
```

W ten sposób kod GLSL jest zapisany jako zwykły tekst w Kotlinie ([dok. 8](08-potok-gpu.md)).

### Modyfikatory widoczności

`private` – widoczne tylko w pliku/klasie, `internal` – widoczne w całym module (`EglCore`, `ShaderProgram`), brak modyfikatora = publiczne.

## 6.3. Współprogramy (coroutines) – dlaczego aplikacja się nie zacina?

Na Androidzie istnieje jeden **wątek główny (UI thread)**, który rysuje ekran i obsługuje dotyk. Jeśli wykonasz na nim dłuższą operację (zapis zdjęcia, dekodowanie, odpowiedź aparatu), interfejs zamarznie. Dlatego długie rzeczy robi się „w tle".

**Współprogramy** to wygodny sposób pisania kodu asynchronicznego tak, jakby był zwykły, sekwencyjny:

```kotlin
suspend fun capture(...): Uri {          // `suspend` = funkcja może się "zawiesić", nie blokując wątku
    val plik = utworzTymczasowy()
    takePicture(plik)                     // czeka na aparat, bez blokowania wątku głównego
    val bitmapa = filterer.render(plik)   // ciężka robota na innym wątku
    return store.saveBitmap(bitmapa)
}
```

Najważniejsze pojęcia, które wystąpią w kodzie:

| Pojęcie | Znaczenie |
|---------|-----------|
| `suspend fun` | Funkcja, która może „czekać" bez blokowania wątku. Wolno ją wywołać tylko z innej funkcji `suspend` lub z współprogramu |
| `scope.launch { ... }` | Uruchamia współprogram (np. po kliknięciu przycisku) |
| `withContext(Dispatchers.IO) { ... }` | Przenosi blok na wątek przeznaczony do operacji dyskowych |
| `delay(250)` | Czeka 250 ms, nie blokując wątku |
| `suspendCancellableCoroutine` | Zamienia klasyczne API z callbackiem (np. `takePicture`) na `suspend fun` |
| `awaitCancellation()` | „Czekaj aż mnie anulują" – używane, by zachować aktywny stan do momentu zniknięcia ekranu |
| `CancellationException` | Wyjątek oznaczający anulowanie – zawsze trzeba go przepuścić dalej |

## 6.4. Jetpack Compose – budowanie interfejsu

### Dawny sposób vs Compose

Dawniej interfejs opisywało się w plikach XML i modyfikowało imperatywnie („znajdź przycisk i zmień mu tekst"). **Jetpack Compose** to nowoczesne podejście **deklaratywne**: opisujesz funkcjami, *jak ma wyglądać interfejs dla bieżącego stanu*, a Compose sam dba o odświeżenie, gdy stan się zmieni.

### `@Composable`

```kotlin
@Composable
fun EffectToggle(
    @DrawableRes icon: Int,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        shape = CircleShape,
        color = if (checked) colors.primary else colors.surface.copy(alpha = 0.85f),
        ...
    ) {
        Icon(painter = painterResource(icon), contentDescription = contentDescription, ...)
    }
}
```

To jest prawdziwy kod z projektu (`ui/EffectToggle.kt`) – okrągły przycisk-przełącznik. Co tu widać:

- `@Composable` – adnotacja oznaczająca „ta funkcja opisuje fragment interfejsu". Funkcje composable **nic nie zwracają** – „emitują" elementy interfejsu.
- **Stan przychodzi z zewnątrz**: `checked` (czy włączony) i `onCheckedChange` (co zrobić po kliknięciu). Przełącznik sam niczego nie pamięta – to *podnoszenie stanu* (*state hoisting*): komponent jest prosty i wielokrotnego użytku, a stan trzyma rodzic.
- Wygląd zależy od stanu: `if (checked) colors.primary else ...`.

### Modifiers

`Modifier` to łańcuch ozdobników opisujących rozmiar, odstępy, zachowanie:

```kotlin
Modifier.fillMaxSize().padding(16.dp)
```

`dp` to „piksele niezależne od gęstości ekranu" – 1 dp wygląda podobnie wielkie na każdym telefonie.

### Podstawowe elementy układu

| Element | Do czego |
|---------|----------|
| `Box` | Elementy jeden na drugim (np. podgląd aparatu i przyciski na nim) |
| `Column` | Elementy jeden pod drugim |
| `Row` | Elementy obok siebie |
| `Spacer` | Pusty odstęp |
| `Surface` | Powierzchnia z tłem/kształtem |
| `Text`, `Icon`, `Button`, `Slider` | Gotowe kontrolki Material 3 |
| `AndroidView` | „Most" do klasycznych widoków Androida – w projekcie dla `PreviewView` (podgląd aparatu) |

### Stan w Compose

To najważniejszy pomysł w Compose: **interfejs jest funkcją stanu**. Gdy stan się zmienia, Compose wykonuje funkcje composable ponownie (*rekompozycja*).

```kotlin
var blur by rememberSaveable { mutableStateOf(false) }
```

- `mutableStateOf(false)` – tworzy obserwowalną zmienną; Compose „wie", gdzie jest czytana, i odświeża tylko to.
- `remember { ... }` – zapamiętaj wartość między rekompozycjami (inaczej byłaby tworzona od nowa).
- `rememberSaveable { ... }` – jak `remember`, ale **przeżywa obrót ekranu** (i wyłączenie procesu przez system).
- `by` – delegacja właściwości: zamiast `blur.value` piszesz po prostu `blur`.
- `mutableFloatStateOf` / `mutableIntStateOf` – warianty dla liczb (bez pakowania w obiekt).

### Efekty uboczne

Funkcja composable może być wywoływana wiele razy, więc nie wolno w niej robić „akcji" (zapis, otwieranie aparatu) bezpośrednio. Do tego służą **efekty**:

| Efekt | Zastosowanie w projekcie |
|-------|--------------------------|
| `LaunchedEffect(klucze) { ... }` | Uruchom współprogram, a gdy zmienią się klucze – anuluj i uruchom ponownie. Np. `LaunchedEffect(pair) { prefs.save(pair) }` – zapis ustawień przy zmianie pary widoków; w `CameraPreview` – podpięcie aparatu |
| `DisposableEffect(klucz) { ... onDispose { ... } }` | Coś, co trzeba posprzątać (np. `bitmap.recycle()`) |
| `LifecycleEventEffect(ON_RESUME)` | Reaguj na powrót do aplikacji (np. po zmianie uprawnień w ustawieniach) |
| `rememberUpdatedState(x)` | Daj długo żyjącemu kodowi (wątek GPU) zawsze **najnowszą** wersję `x` |
| `rememberCoroutineScope()` | Uzyskaj zakres do `scope.launch { }` z callbacków przycisków |

### Motyw

`AniVisionTheme { ... }` (`ui/theme/`) ustawia kolory i typografię dla wszystkiego w środku. Paleta: granat `#1E2A44` i krem `#F4F1EA` z ikony aplikacji, jasna i ciemna wersja zależnie od ustawienia systemu. **Dynamiczne kolory Material You są celowo wyłączone**, bo kontrolki leżą na żywym obrazie z aparatu i mają wyglądać tak samo na każdym telefonie.

## 6.5. Najważniejsze pojęcia Androida (spotkasz je w kodzie)

| Pojęcie | Wyjaśnienie | Gdzie w projekcie |
|---------|-------------|-------------------|
| **Activity** | „Ekran" aplikacji / punkt wejścia z cyklem życia (utworzenie, wznowienie, zatrzymanie, zniszczenie) | `MainActivity` – jedyna |
| **Cykl życia (lifecycle)** | System może w każdej chwili zatrzymać aplikację (blokada ekranu, inny program); komponenty muszą to przeżyć | `CameraPreview`, `CameraPermissionGate` |
| **Manifest** | Plik deklaracji aplikacji | `AndroidManifest.xml` |
| **Uprawnienia** | Zgoda użytkownika na wrażliwe zasoby; aplikacja prosi o `CAMERA` | `CameraPermissionGate` |
| **Zasoby (`res/`)** | Teksty, ikony, motywy; odwołujesz się do nich przez `R.string.nazwa`, `R.drawable.nazwa` | `strings.xml`, `drawable/` |
| **Intent** | „Zamiar" – komunikat do systemu/innej aplikacji (np. „udostępnij to zdjęcie", „otwórz ustawienia") | `PhotoSharing.kt`, `CameraPermissionGate` |
| **ActivityResultContracts** | Nowoczesny sposób na „uruchom coś i odbierz wynik" (okno uprawnień, wybieracz zdjęć) | `CameraPermissionGate`, `CameraScreen` |
| **SharedPreferences** | Mały plik na pary klucz–wartość (jedyna trwała pamięć w aplikacji) | `PerspectivePreferences.kt` |
| **MediaStore / ContentResolver / Uri** | System zarządzania mediami: zapisujesz zdjęcie przez systemową bazę, a `Uri` jest „adresem" pliku | `PhotoStore.kt` |
| **Bitmap** | Obraz w pamięci; duże obiekty zwalnia się jawnie przez `recycle()` | `photo/` |
| **Handler / HandlerThread / Executor** | Własny wątek roboczy i kolejka zadań | wątek GPU |
| **Surface / SurfaceTexture** | Powierzchnia, na którą wpływają klatki (z aparatu) lub na której rysuje się obraz | `vision/gl/` |
| **EXIF** | Metadane w pliku JPEG, m.in. o obrocie zdjęcia | `PhotoDecoder.kt` |
| **Toast** | Krótki komunikat na ekranie ("Zapisano") | `CameraScreen` |
| **BuildConfig** | Stałe wygenerowane podczas budowania (np. `VERSION_NAME`, `DEBUG`) | `AboutScreen`, `CameraPreview` |

### Scoped storage: zapis zdjęcia bez uprawnienia do pamięci

Od Androida 10 aplikacja nie dostaje swobodnego dostępu do plików użytkownika. Zamiast tego prosi system (`MediaStore`) o utworzenie wpisu w `Pictures/AniVision` i dostaje do niego strumień do zapisu. Nie jest potrzebne żadne dodatkowe uprawnienie – dlatego `minSdk = 29`.

### Wybieracz zdjęć

Import z galerii używa systemowego **Photo Picker** (`PickVisualMedia`): użytkownik wybiera zdjęcie w oknie systemowym, a aplikacja dostaje tylko to jedno zdjęcie – znów bez uprawnień.

## 6.6. Gradle – system budowania

**Gradle** to narzędzie, które z kodu i zasobów buduje gotową aplikację: pobiera biblioteki, kompiluje Kotlin, pakuje zasoby, podpisuje APK, uruchamia testy. Konfiguruje się go plikami `.gradle.kts` (skrypty w Kotlinie).

### Pliki konfiguracyjne

| Plik | Rola |
|------|------|
| `settings.gradle.kts` | Nazwa projektu (`AniVision`), lista modułów (tylko `:app`), skąd pobierać biblioteki i wtyczki |
| `build.gradle.kts` (główny) | Deklaruje wtyczki (Android Application, Kotlin Compose) bez ich stosowania |
| `app/build.gradle.kts` | **Właściwa konfiguracja**: `applicationId`, wersje SDK, funkcje (Compose, BuildConfig), podpisywanie, zależności |
| `gradle/libs.versions.toml` | **Katalog wersji** – jedno miejsce z listą bibliotek i ich wersji; w skryptach odwołujesz się jako `libs.nazwa` |
| `gradle.properties` | Ustawienia Gradle (pamięć 2 GB, cache konfiguracji) |
| `gradle/wrapper/gradle-wrapper.properties` | Wersja Gradle (9.6.0). `gradlew` pobiera i uruchamia właściwą wersję – nie musisz jej instalować |
| `gradle/gradle-daemon-jvm.properties` | Wymagany JDK (toolchain 25, pobierany automatycznie) |
| `keystore.properties` | (nie w repo!) dane klucza do podpisywania wersji wydaniowej |

### Zależności (biblioteki)

| Biblioteka | Wersja | Po co |
|------------|--------|-------|
| Android Gradle Plugin | 9.5.1 | Buduje APK |
| Kotlin + wtyczka Compose | 2.2.10 | Język i kompilator Compose |
| `androidx.core:core-ktx` | 1.19.1 | Rozszerzenia Kotlina dla frameworku Androida |
| `androidx.lifecycle:lifecycle-runtime-ktx` / `-compose` | 2.11.0 | Cykl życia, współprogramy (tu pochodzi też `kotlinx.coroutines`) |
| `androidx.activity:activity-compose` | 1.13.0 | `ComponentActivity`, `setContent`, launchery wyników, `BackHandler` |
| `androidx.compose:compose-bom` | 2026.02.01 | „BOM" – jedna wersja pinująca spójnie wszystkie biblioteki Compose |
| Compose `ui`, `material3`, ... | z BOM | Interfejs i komponenty Material 3 |
| `androidx.camera:camera-core/camera2/lifecycle/view` | 1.6.2 | CameraX: podgląd, zdjęcia, efekty, `PreviewView` |
| `junit` | 4.13.2 | Testy jednostkowe |
| `org.json:json` | 20240303 | Prawdziwy parser JSON dla testów na komputerze (androidowy jest tam atrapą) |
| `androidx.test.ext:junit`, `espresso-core`, Compose `ui-test` | – | Testy na urządzeniu |

**BOM** (*Bill of Materials*): zamiast ustalać wersję każdej biblioteki Compose osobno, podajesz jedną wersję BOM, a ona dobiera spójne wersje.

Nie ma w projekcie: ViewModel, DataStore, Navigation, Hilt (wstrzykiwania zależności), biblioteki wideo – celowo prosta architektura.

### Typy budowania

- **debug** – do codziennej pracy, podpisywany automatycznym kluczem debug, ma dodatkowe logowanie (FPS).
- **release** – do rozdawania: włączona jest optymalizacja **R8** (zmniejszanie i zaciemnianie kodu), podpis własnym kluczem (patrz [dok. 10](10-testy-budowanie-wydanie.md)).

---

**Nawigacja:** ⬅️ [5. Prototyp w Pythonie](05-prototyp-python.md) · [📚 Spis treści](README.md) · [7. Architektura aplikacji](07-architektura-aplikacji.md) ➡️
