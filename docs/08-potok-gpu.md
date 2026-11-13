# 8. Potok GPU i shadery

**Nawigacja:** ⬅️ [7. Architektura aplikacji](07-architektura-aplikacji.md) · [📚 Spis treści](README.md) · [9. Zdjęcia: aparat, galeria, zapis](09-zdjecia.md) ➡️

---

To najtrudniejsza część projektu, więc zaczynamy od zera. Pliki: `SRC/vision/gl/`.

## 8.1. Po co GPU?

Podgląd z aparatu ma ok. 2 miliony pikseli, a klatek jest 30 na sekundę – czyli 60 milionów pikseli do przetworzenia **w każdej sekundzie**. Procesor (CPU) robi rzeczy po kolei (kilka rdzeni). Karta graficzna (GPU) ma **setki lub tysiące małych rdzeni**, które liczą jednocześnie. Zadania takie jak „dla każdego piksela pomnóż kolor przez macierz" są idealne dla GPU.

## 8.2. OpenGL ES i shadery – słowniczek

| Pojęcie | Wyjaśnienie |
|---------|-------------|
| **OpenGL ES 3.0** | Interfejs do programowania GPU w telefonach (ES = Embedded Systems). Minimum wymagane przez aplikację |
| **Shader** | Mały program uruchamiany na GPU. Piszemy go w języku **GLSL** |
| **Vertex shader** | Wylicza położenie wierzchołków figury (tu: jednego prostokąta na cały ekran) |
| **Fragment shader** | Uruchamiany **dla każdego piksela** powstającego obrazu; zwraca jego kolor |
| **Program** | Połączona para vertex + fragment shader |
| **Tekstura** | Obraz w pamięci GPU, który shader może odczytywać |
| **Uniform** | Parametr dla shadera, ustawiany z zewnątrz i taki sam dla wszystkich pikseli (np. macierz, sigma) |
| **Atrybut** | Dane przypisane do wierzchołka (np. jego współrzędne) |
| **Framebuffer** | Cel, do którego rysujemy: ekran albo tekstura pośrednia |
| **Pbuffer** | Powierzchnia poza ekranem (do renderowania zdjęć w tle) |
| **Viewport** | Prostokąt obszaru rysowania |
| **Scissor test** | Ograniczenie rysowania do wycinka (prostokąta) |
| **EGL** | Warstwa łącząca OpenGL z systemem okien Androida (tworzenie kontekstu i powierzchni) |
| **Kontekst GL** | „Stan" OpenGL powiązany z wątkiem; bez niego nie można rysować |

### Jak GPU rysuje pełnoekranowy obraz?

Rysujemy **jeden prostokąt** z czterech wierzchołków (`GL_TRIANGLE_STRIP`) pokrywający cały ekran. Dla każdego piksela prostokąta GPU uruchamia fragment shader – i w nim liczymy kolor. Taki trik nazywa się „full-screen quad".

## 8.3. Droga klatki – cały potok

```
Sensor aparatu
   │
   ▼
CameraX (backend camera2) produkuje klatki (YUV) do powierzchni, którą MY dostarczamy
   │
   ▼
VisionSurfaceProcessor.onInputSurface: Surface(SurfaceTexture(renderer.cameraTextureId))
   │  "klatka gotowa" → drawFrame() na wątku GL
   ▼
VisionRenderer.draw():  fragment shader czyta teksturę "external OES"
   │
   ▼
Powierzchnia wyjściowa (output Surface z CameraX) → PreviewView → ekran
```

### Tekstura zewnętrzna (`GL_TEXTURE_EXTERNAL_OES`)

Klatki z aparatu są w formacie **YUV**, nie RGB. Android udostępnia specjalny rodzaj tekstury (`samplerExternalOES`), którego GPU czyta i **automatycznie przelicza YUV → RGB**. W shaderze wymaga to deklaracji `#extension GL_OES_EGL_image_external : require`.

### `VisionSurfaceProcessor` – szczegóły

Implementuje interfejs `SurfaceProcessor` z CameraX. CameraX wywołuje:

- `onInputSurface(request)` – „oto zgłoszenie: daj mi powierzchnię, do której mam wysyłać klatki",
- `onOutputSurface(surfaceOutput)` – „oto powierzchnia, na którą masz narysować wynik".

Cały kod działa na jednym wątku `AniVisionGL` (`HandlerThread`). Jego `executor` to `Executor { handler.post(it) }`. Kontekst EGL i renderer powstają na tym wątku w `init`. Nie potrzeba żadnych blokad, bo wszystko dzieje się sekwencyjnie na jednym wątku.

### Rysowanie jednej klatki (`drawFrame`)

1. `texture.updateTexImage()` – **zawsze**, nawet jeśli nic nie rysujemy: konsumuje klatkę. Inaczej aparat czekałby na wolny bufor i zamarł.
2. `texture.getTransformMatrix(textureMatrix)`, potem `surfaceOutput.updateTransformMatrix(outputMatrix, textureMatrix)` – łączy obrót sensora, kadrowanie i lustro w jedną macierz 4×4 `uTexMatrix`.
3. `egl.makeCurrent(eglSurface)`, `renderer.draw(outputMatrix, w, h, settings())`.
4. `egl.swap(surface, texture.timestamp)` – wymiana buforów (pokazanie klatki) z zachowaniem znacznika czasu.
5. Błędy trafiają do `onError`, który ustawia `failed = true` i uruchamia ekran błędu z ponowieniem.

### Cykl życia: co gdy zablokujesz telefon?

Zablokowanie ekranu zamyka powierzchnię wyjściową. `swap` zwraca wtedy `false` (EGL zgłasza `EGL_BAD_SURFACE` / `EGL_BAD_NATIVE_WINDOW`). **To nie jest błąd** – klatka jest po prostu porzucana. CameraX później poda nową powierzchnię. Gdy `CameraPreview` znika z kompozycji, `LaunchedEffect` jest anulowany, aparat odpinany i `release()` zwalnia wszystkie zasoby GL, po czym kończy wątek (`quitSafely`).

## 8.4. `EglCore` – założenie kontekstu OpenGL

Zanim cokolwiek narysujemy, musimy mieć kontekst. `EglCore`:

1. pobiera wyświetlacz EGL,
2. wybiera konfigurację RGBA8888, GLES 3, obsługującą okna i pbuffery,
3. tworzy kontekst,
4. tworzy **pbuffer 1×1** i robi go bieżącym – dzięki temu kontekst istnieje, zanim pojawi się właściwe okno,
5. udostępnia: `createWindowSurface(Surface)`, `createPbufferSurface(w,h)`, `makeCurrent`, `makeDefaultCurrent`, `swap(surface, timestampNs)`, `destroySurface`, `release`.

Wszystko musi być wołane z jednego wątku GL.

## 8.5. `VisionShaders` – kod GLSL

Kod shaderów jest zapisany jako **teksty w Kotlinie** (plik `VisionShaders.kt`). Jeden szablon fragment shadera jest kompilowany w trzech wersjach (za pomocą `#define`):

| Wariant | Źródło | Wyjście | Do czego |
|---------|--------|---------|----------|
| `COLOUR` | kamera (OES) | kolor końcowy | gdy rozmycie jest niepotrzebne: jeden przebieg |
| `BLUR_HORIZONTAL` | kamera (OES) | światło liniowe | **pierwszy** przebieg rozmycia (poziomo) |
| `BLUR_VERTICAL` | tekstura pośrednia | kolor końcowy | **drugi** przebieg rozmycia (pionowo) + rozjaśnienie + macierz + kodowanie sRGB |

### Vertex shader

```glsl
attribute vec2 aPosition;
varying vec2 vUv;
void main() {
    vUv = aPosition * 0.5 + 0.5;               // [-1,1] → [0,1]: współrzędne tekstury
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
```

Rogi prostokąta mają współrzędne od −1 do 1; `vUv` (od 0 do 1) trafia do fragment shadera jako „gdzie w obrazie jestem".

### Funkcje kolorów

```glsl
vec3 srgbToLinear(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}
vec3 linearToSrgb(vec3 c) {
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}
```

To wzory z [dok. 3](03-podstawy-obrazu-cyfrowego.md) zapisane „bez `if`" – `step(próg, x)` zwraca 0 lub 1, `mix(a, b, t)` interpoluje między `a` i `b`. GPU nie lubi rozgałęzień, więc wybór wzoru robi się arytmetycznie.

### Rozjaśnianie cieni

```glsl
const vec3 LUMA = vec3(0.2126729, 0.7151522, 0.072175);
vec3 shadowLift(vec3 rgb, float gamma) {
    float y = clamp(dot(LUMA, rgb), 1.0e-6, 1.0);
    float peak = max(max(rgb.r, rgb.g), max(rgb.b, 1.0e-6));
    return rgb * min(pow(y, gamma - 1.0), max(1.0 / peak, 1.0));
}
```

To dosłownie wzór z [dok. 4](04-matematyka-widzenia-zwierzat.md) (`dot` – iloczyn skalarny, tu: ważona suma, czyli jasność). `1.0e-6` zapobiega dzieleniu przez zero dla czerni.

### Odczyt piksela z kamery

```glsl
vec3 fetchLinear(vec2 uv) {
    uv = clamp(uv, 0.0, 1.0);
#ifdef SOURCE_OES
    vec3 camera = texture2D(uTexture, (uTexMatrix * vec4(uv, 0.0, 1.0)).xy).rgb;
    return srgbToLinear(clamp(camera, 0.0, 1.0));   // clamp: pow() z liczby ujemnej daje NaN
#else
    return texture2D(uTexture, uv).rgb;             // pośrednia tekstura jest już liniowa
#endif
}
```

- `uTexMatrix` koryguje obrót i lustro,
- `clamp(camera, 0, 1)` – po konwersji YUV→RGB mogą wyjść wartości lekko ujemne; podniesione do potęgi dałyby NaN (nieliczbę),
- `uv = clamp(uv, 0, 1)` – to właśnie „clamp-to-edge" przy rozmyciu na krawędziach.

### Rozmycie Gaussa

```glsl
vec3 blurred(vec2 uv) {
    vec2 fromCentre = (vUv - 0.5) * uSize;
    float r2 = dot(fromCentre, fromCentre) / (0.25 * dot(uSize, uSize));  // 0 w środku, 1 w rogach
    float sigma = uSigma * (1.0 + uPeriphery * r2);          // rozmycie obwodowe (kot)
    float radius = min(ceil(3.0 * sigma), float(MAX_RADIUS));  // MAX_RADIUS = 32
    float k = -0.5 / (sigma * sigma);
    vec3 sum = fetchLinear(uv);
    float weightSum = 1.0;
    for (int i = 1; i <= MAX_RADIUS; i++) {
        float fi = float(i);
        if (fi > radius) break;
        float w = exp(k * fi * fi);                    // waga Gaussa
        vec2 offset = uStep * fi;
        sum += w * (fetchLinear(uv + offset) + fetchLinear(uv - offset));
        weightSum += 2.0 * w;
    }
    return sum / weightSum;
}
```

Co się tu dzieje:

1. Obliczamy `r2` – kwadrat względnej odległości od środka (0…1).
2. Sigma piksela = bazowa × (1 + obwodowa × `r2`). Dla psa `uPeriphery = 0`, więc sigma jest stała.
3. Promień = 3σ (wtedy 99,7% wag Gaussa jest uwzględnione), ale nie więcej niż 32 próbki w każdą stronę (`MAX_RADIUS`).
4. Suma ważona: środek (waga 1) plus pary próbek po obu stronach, z wagami `exp(−i²/2σ²)`.
5. Dzielimy przez sumę wag (normalizacja).

`uStep` to `(1/szerokość, 0)` w przebiegu poziomym i `(0, 1/wysokość)` w pionowym – to rozmycie **rozdzielne** (patrz [dok. 3](03-podstawy-obrazu-cyfrowego.md)). Limit 32 próbek tłumaczy, dlaczego suwak rozmycia kończy się na 3,5×.

### Funkcja `main`

```glsl
void main() {
#ifdef BLUR
    vec3 linear = blurred(vUv);
#else
    vec3 linear = fetchLinear(vUv);
#endif
#ifdef COLOUR_OUT
    vec3 shown = uColorMatrix * shadowLift(linear, uShadowGamma);   // rozmycie → boost → macierz
    gl_FragColor = vec4(linearToSrgb(clamp(shown, 0.0, 1.0)), 1.0); // clamp = polityka gamutu
#else
    gl_FragColor = vec4(linear, 1.0);                               // przebieg poziomy: zachowaj światło liniowe
#endif
}
```

`uColorMatrix` jest typu `mat3` i ładowana **kolumnami** (`glUniformMatrix3fv(..., transpose = false, ...)`) – stąd `linearMatrixColumnMajor()`. W GLSL zapis `macierz * wektor` oznacza mnożenie macierzowe.

Test `VisionShadersTest` sprawdza (na tekście, bez GPU), że funkcje kolorów i `shadowLift` są w shaderze **dosłownie takie same** jak w `animal_vision.glsl` z prototypu, że rozjaśnianie jest dopiero w ostatnim przebiegu i że w kodzie nie ma `uSplit` (podział robi renderer, nie shader).

## 8.6. `VisionRenderer` – jak powstaje obraz z podziałem

`VisionRenderer.draw(texMatrix, width, height, settings)` rysuje jedną klatkę. Składniki: tekstura kamery, trzy programy shaderów (COLOUR, BLUR_HORIZONTAL, BLUR_VERTICAL), bufor wierzchołków prostokąta, a leniwie – framebuffer z teksturą **RGBA16F** (liczby zmiennoprzecinkowe połówkowej precyzji) do wyniku pośredniego rozmycia. Gdy takiego framebuffera nie da się utworzyć (`blurUnsupported`), rozmycie jest po cichu pomijane.

### Podział ekranu z użyciem `scissor`

Shader **nie zna** podziału. Zamiast tego renderer rysuje obraz **dwa razy**, za każdym razem ograniczając rysowanie do wycinka:

```kotlin
val splitPx = (settings.split.coerceIn(0f, 1f) * width).roundToInt()
glEnable(GL_SCISSOR_TEST)
if (splitPx > 0)     drawSide(settings.left,  0,       splitPx,         ...)
if (splitPx < width) drawSide(settings.right, splitPx, width - splitPx, ...)
glDisable(GL_SCISSOR_TEST)
```

Viewport pozostaje cały czas całym kadrem, więc każdy piksel ma prawdziwe współrzędne `vUv`. Scissor ucina tylko fragment, który faktycznie trafia na ekran. Dzięki temu lewa strona to „lewy fragment pełnego obrazu przepuszczonego przez profil lewy", a prawa – „prawy fragment pełnego obrazu przepuszczonego przez profil prawy". Obraz nie jest ściskany ani przesuwany.

### Parametry dla każdej strony

- macierz kolorów: `profile?.linearMatrixColumnMajor() ?: IDENTITY_3`,
- gamma cieni: `settings.shadowGammaFor(profile)`,
- obwodowe rozmycie: `profile?.peripheralBlur`,
- sigma: `Acuity.sigmaPx(max(w, h), exaggeration, profile.acuityRatio)` – 0 dla strony „człowiek" lub gdy rozmycie jest wyłączone.

### Wybór liczby przebiegów

- Jeśli `sigma · (1 + periphery) > 0.2 px` **i** da się utworzyć framebuffer połówkowy → **dwa przebiegi**:
  1. `BLUR_HORIZONTAL`: odczyt z kamery, rozmycie poziome, zapis liniowego światła do tekstury RGBA16F.
  2. `BLUR_VERTICAL`: odczyt z tej tekstury, rozmycie pionowe, rozjaśnienie, macierz, sRGB → na ekran.
- W przeciwnym razie → **jeden przebieg** `COLOUR`: kamera → liniowe → rozjaśnienie → macierz → sRGB.

Strona „człowiek" bez nocy i bez rozmycia też przechodzi przez shader `COLOUR` z macierzą jednostkową; wynik różni się od wejścia najwyżej o 1 poziom z 255 (sprawdza to test `passThroughKeepsTheCameraColour`).

Dlaczego tekstura pośrednia jest liniowa i zmiennoprzecinkowa? Żeby nie tracić precyzji: zapis w 8 bitach spowodowałby pasmowanie (banding) w ciemnych tonach. W shaderze `precision highp sampler2D` zapobiega przycinaniu precyzji.

## 8.7. `FrameStats`

Debugowe narzędzie mierzące liczbę klatek na sekundę i czas rysowania: co sekundę zgłasza np. `30.0 fps, draw avg 3.2 ms / max 7.5 ms (30 frames)`. Włączane tylko w buildzie debug. Odczyt: `adb logcat -s AniVisionFps`.

## 8.8. Podsumowanie potoku GPU

```
 klatka z kamery (tekstura OES, YUV→RGB robi GPU)
        │
        ├── [strona lewa: scissor]  ──┐
        │                             │   dla każdej strony:
        └── [strona prawa: scissor] ──┤    1. sRGB → liniowe
                                      │    2. [rozmycie poziome → tekstura pośrednia → rozmycie pionowe]
                                      │    3. rozjaśnianie cieni (gamma)
                                      │    4. mnożenie przez macierz 3×3
                                      │    5. clamp 0–1
                                      │    6. liniowe → sRGB
                                      ▼
                                   ekran
```

---

**Nawigacja:** ⬅️ [7. Architektura aplikacji](07-architektura-aplikacji.md) · [📚 Spis treści](README.md) · [9. Zdjęcia: aparat, galeria, zapis](09-zdjecia.md) ➡️
