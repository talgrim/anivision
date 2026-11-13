# 9. Zdjęcia: aparat, galeria, zapis

**Nawigacja:** ⬅️ [8. Potok GPU i shadery](08-potok-gpu.md) · [📚 Spis treści](README.md) · [10. Testy, budowanie i wydanie](10-testy-budowanie-wydanie.md) ➡️

---

Pakiet: `SRC/photo/` oraz fragmenty `SRC/vision/gl/` i `SRC/ui/CameraScreen.kt`. Przeczytaj najpierw [dok. 8](08-potok-gpu.md).

## 9.1. Dwie ścieżki, jedna zasada

Są dwa źródła zdjęć do przefiltrowania:

1. **Zdjęcie zrobione aparatem** w aplikacji,
2. **Zdjęcie wybrane z galerii**.

W obu przypadkach obraz przechodzi przez **ten sam kod GPU** (`VisionRenderer`) co podgląd na żywo. Dzięki temu zapisane zdjęcie jest zgodne z podglądem. To zasada nadrzędna: **nie ma drugiej implementacji filtra**.

## 9.2. Dlaczego zdjęcia nie pochodzą z podglądu?

Można by „zrobić zrzut" przetworzonego podglądu, ale miałby rozdzielczość ekranu. Zamiast tego aplikacja robi **zwykłe zdjęcie w pełnej jakości** (`ImageCapture` z CameraX, plik JPEG), a potem przepuszcza je przez filtr GPU **poza ekranem**. To nie jest wynik efektu CameraX – efekt dotyczy tylko podglądu (`CameraEffect.PREVIEW`).

## 9.3. Robienie zdjęcia: `PhotoCapturer`

```kotlin
class PhotoCapturer(context, imageCapture, store, filterer, captions)

suspend fun capture(settings, displayRotation, leftLabel, rightLabel): Uri
```

Kroki:

1. Utwórz plik tymczasowy w katalogu cache aplikacji.
2. Ustaw `imageCapture.targetRotation` (zgodnie z obrotem ekranu).
3. `takePicture(file)` – owinięte w `suspendCancellableCoroutine`, żeby API z callbackiem stało się funkcją `suspend`.
4. **Jeśli `settings.isPassThrough`** (nic widocznego nie jest filtrowane, np. cały ekran to „Human") – JPEG jest kopiowany do galerii bez zmian.
5. W przeciwnym razie:
   - `filterer.render(file, settings)` → przefiltrowana bitmapa,
   - `captions.draw(bitmap, split, leftLabel, rightLabel)` → podpisy,
   - `store.saveBitmap(bitmap)` → zapis.
6. W bloku `finally`: usuń plik tymczasowy i zwolnij bitmapę (`recycle`).

Zdjęcie kadruje się zgodnie z `ViewPort` ustawionym przy podglądzie, więc ma dokładnie te proporcje, które użytkownik widział na ekranie.

## 9.4. Dekodowanie: `PhotoDecoder`

Zdjęcie z telefonu może mieć 12 megapikseli lub więcej – wczytanie go w całości zużyłoby sporo pamięci. Dekoder:

1. **Pass 1** – czyta tylko wymiary (`inJustDecodeBounds = true`), nie alokując pikseli.
2. **Pass 2** – dekoduje z `inSampleSize`: potęga dwójki mówiąca „wczytaj co n-ty piksel". Wybiera największą potęgę, przy której dłuższy bok nadal wynosi co najmniej `maxLongEdge` (domyślnie 4096).
3. **Pass 3** – czyta orientację EXIF (`ExifInterface`).

Każdy przebieg otwiera **nowy strumień** (dlatego `decode` przyjmuje funkcję `open: () -> InputStream`, a nie sam strumień).

### EXIF: obrót zdjęcia

Aparat zazwyczaj zapisuje piksele „tak jak leżał sensor", a sposób, w jaki trzymano telefon, dopisuje w metadanych EXIF (np. „obróć o 90°"). Bitmapa po dekodowaniu **nie jest** obracana automatycznie; `PhotoDecoder` zwraca ją razem z `rotationDegrees` (0/90/180/270 zgodnie z ruchem wskazówek zegara). Obrót uwzględnia dopiero filtr GPU. Orientacje lustrzane są traktowane jak zwykłe obroty.

`DecodedPhoto(bitmap, rotationDegrees)` to prosta klasa przenosząca te dwie rzeczy.

## 9.5. Filtr GPU poza ekranem: `PhotoFilterer` i `StillImageFilter`

`PhotoFilterer.render(file | open, settings): Bitmap`:

1. dekodowanie na `Dispatchers.IO` (limit 4096 px na dłuższym boku),
2. `StillImageFilter.filter(bitmap, rotationDegrees, settings)`,
3. zwolnienie bitmapy źródłowej.

Wywołujący jest właścicielem wyniku (musi go zwolnić).

### `StillImageFilter.filter` krok po kroku

Działa na osobnym wątku `AniVisionStill` (jeden wątek, `withContext(thread.asCoroutineDispatcher())`).

1. Tworzy **nowy** `EglCore` z pbufferem 1×1 – każde wywołanie ma własny kontekst i nie przechowuje stanu.
2. Wylicza rozmiar wyniku: `StillGeometry.outputSize(w, h, rotation, maxLongEdge)`:
   - zamienia szerokość i wysokość dla obrotów 90°/270°,
   - zmniejsza proporcjonalnie, jeśli dłuższy bok przekracza limit (`min(4096, GL_MAX_TEXTURE_SIZE)`),
   - nigdy nie zwraca zerowego boku.
3. Tworzy pbuffer o tym rozmiarze i `VisionRenderer`.
4. Używa **atrapy aparatu** `BitmapCamera`: tworzy `SurfaceTexture` podpiętą do tekstury zewnętrznej renderera, owija ją w `Surface`, zakłada `lockCanvas`, wypełnia na czarno, **rysuje bitmapę** z obrotem i skalą, `unlockCanvasAndPost`, po czym `updateTexImage()`. Dzięki temu zdjęcie wchodzi do renderera tą samą drogą co klatka z aparatu.
5. Ustawia macierz tekstury `textureMatrix * FLIP_V` – dodatkowe odbicie w pionie (`v → 1 − v`), bo `glReadPixels` zwraca najpierw dolny wiersz.
6. Wywołuje **dokładnie to samo** `renderer.draw(texMatrix, w, h, settings)` co podgląd.
7. `glReadPixels` do bufora → `Bitmap` przez `copyPixelsFromBuffer`.
8. W `finally` zwalnia wszystkie obiekty GL.

## 9.6. Podpisy na zdjęciach z podziałem: `SplitCaptions`

Gdy zdjęcie powstało w trybie podzielonym, trzeba zaznaczyć, która strona co oznacza. `SplitCaptions.draw(bitmap, split, leftLabel, rightLabel)` rysuje na bitmapie (`Canvas`):

- cienką białą **linię podziału** w pozycji `szerokość × split`,
- ciemną zaokrągloną „pigułkę" z białym pogrubionym tekstem (np. „Human") w lewym górnym rogu lewej połowy i prawym górnym rogu prawej połowy.

Jeśli podpis nie mieści się w swojej połowie (separator blisko krawędzi), przechodzi na **drugą** połowę, przy separatorze, ze strzałką (`◂ Human` albo `Dog ▸`). Podpis nigdy nie przekracza linii podziału. Gdy `split <= 0` lub `>= 1`, nic nie jest rysowane (cała bitmapa to jeden widok). Rozmiary są ułamkami krótszego boku (tekst 0,025, margines 0,02, linia 0,004).

Teksty pochodzą z zasobów (`getString(pair.left.label)`), więc pasują do wybranej pary.

## 9.7. Zapis do galerii: `PhotoStore`

```kotlin
suspend fun saveJpeg(now, write: (OutputStream) -> Unit): Uri
suspend fun saveBitmap(bitmap, quality = 95): Uri
```

Obie działają na `Dispatchers.IO`.

1. Wstawia wiersz do `MediaStore` z `RELATIVE_PATH = Pictures/AniVision` i **`IS_PENDING = 1`** (plik „w trakcie zapisu" – inne aplikacje jeszcze go nie widzą).
2. Zapisuje dane do strumienia.
3. Ustawia `IS_PENDING = 0`, dzięki czemu zdjęcie pojawia się w galerii.
4. Jeśli coś się nie powiedzie – usuwa wiersz (nie zostaje uszkodzony wpis).

Nazwa pliku: `AniVision_yyyyMMdd_HHmmss_SSS.jpg` (data, czas, milisekundy). Jakość JPEG: 95.

Po udanym zapisie użytkownik widzi komunikat `Toast` „Saved to Pictures/AniVision" (lub „The photo could not be saved.").

## 9.8. Udostępnianie: `PhotoSharing`

```kotlin
fun sharePhoto(context, uri, title)
```

Buduje `Intent.ACTION_SEND` z typem `image/jpeg`, `EXTRA_STREAM` i `FLAG_GRANT_READ_URI_PERMISSION` (tymczasowe prawo odczytu dla aplikacji docelowej), owinięty w `Intent.createChooser` – systemowe okno wyboru aplikacji. **Zdjęcie jest najpierw zapisywane, potem udostępniane.**

## 9.9. Import z galerii (w `CameraScreen`)

1. Przycisk galerii uruchamia **systemowy Photo Picker** (`PickVisualMedia`, tylko obrazy). Nie jest potrzebne żadne uprawnienie. Wybrane `Uri` trafia do `importSource`.
2. `LaunchedEffect(importSource, importSettings)` renderuje zdjęcie ponownie **za każdym razem**, gdy zmieni się źródło *lub* jakiekolwiek ustawienie (tryb, para widoków, separator, rozmycie, noc). Po pierwszym renderze efekt czeka **250 ms** (`IMPORT_RENDER_DEBOUNCE_MS`) – to *debounce*: przeciąganie suwaka nie wywołuje renderu przy każdym ticku. Gdy klucz zmieni się w trakcie oczekiwania, efekt jest restartowany, a poprzednie `delay` – anulowane.
3. `filterer.render({ contentResolver.openInputStream(uri) }, settings)` – przechodzi przez to samo dekodowanie, EXIF, GPU i `SplitCaptions` co zdjęcie z aparatu. Funkcja przekazuje *fabrykę strumieni*, bo dekoder otwiera strumień trzy razy.
4. Wynik (`imported: Bitmap?`) pokazuje `ImportedPhotoScreen`.
5. **Save** zapisuje raz (`importedUri` zapamiętuje wynik, więc późniejsze **Share** nie tworzy duplikatu). **Share** najpierw zapisuje, potem otwiera okno udostępniania. **Close** zeruje `importSource` i `imported`.
6. `DisposableEffect(shown) { onDispose { shown?.recycle() } }` zwalnia starą bitmapę. Wartość jest przechwycona w `shown`, żeby nie zwolnić za nową bitmapę.
7. W razie błędu: `Toast` „That image could not be opened."

W `ImportedPhotoScreen` separator zgłasza nową pozycję dopiero **po puszczeniu palca** (`onDragStopped`), bo każda zmiana oznacza ponowny render na GPU (w przeciwieństwie do podglądu na żywo, gdzie render jest tani i ciągły).

## 9.10. Podsumowanie ścieżki zdjęcia

```
Migawka ─→ ImageCapture (JPEG w cache) ──┐
Galeria ─→ Photo Picker (Uri) ───────────┤
                                         ▼
                              PhotoDecoder (rozmiar, subsampling, EXIF)
                                         ▼
                              StillImageFilter (pbuffer + VisionRenderer)
                                         ▼
                              SplitCaptions (linia + etykiety)
                                         ▼
                      PhotoStore ─→ galeria (Pictures/AniVision)
                      PhotoSharing ─→ okno "Udostępnij"
```

---

**Nawigacja:** ⬅️ [8. Potok GPU i shadery](08-potok-gpu.md) · [📚 Spis treści](README.md) · [10. Testy, budowanie i wydanie](10-testy-budowanie-wydanie.md) ➡️
