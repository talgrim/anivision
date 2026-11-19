# 11. Słownik pojęć

**Nawigacja:** ⬅️ [10. Testy, budowanie i wydanie](10-testy-budowanie-wydanie.md) · [📚 Spis treści](README.md)

---

Alfabetyczna lista terminów użytych w dokumentacji. Odnośniki prowadzą do miejsca, gdzie pojęcie jest wyjaśnione szerzej.

## A

**Acuity (ostrość wzroku)** – zdolność rozróżniania drobnych szczegółów. U człowieka ok. 30 cykli na stopień; u psa ok. 8. [Dok. 2](02-jak-widza-zwierzeta.md), [4](04-matematyka-widzenia-zwierzat.md).

**Activity** – „ekran" / punkt wejścia aplikacji Androida z cyklem życia. W AniVision jedna: `MainActivity`. [Dok. 6](06-podstawy-android-kotlin.md).

**adb** (*Android Debug Bridge*) – narzędzie wiersza poleceń do komunikacji z telefonem (instalacja, logi). [Dok. 10](10-testy-budowanie-wydanie.md).

**AGP** (*Android Gradle Plugin*) – wtyczka Gradle, która potrafi zbudować aplikację Androida.

**Anchor (kotwica)** – kolor definiujący płaszczyznę, na której rzutowany jest „psi" kolor; tu: światło 475 nm. [Dok. 4](04-matematyka-widzenia-zwierzat.md).

**APK / AAB** – plik instalacyjny aplikacji / paczka dla sklepu Google Play.

## B

**Bitmap** – obraz w pamięci (siatka pikseli). Duże bitmapy zwalnia się jawnie przez `recycle()`. [Dok. 9](09-zdjecia.md).

**BOM** (*Bill of Materials*) – plik pinujący spójne wersje biblioteki (tu: Compose).

**BuildConfig** – stałe generowane podczas budowania (np. numer wersji, flaga `DEBUG`).

## C

**CameraX** – biblioteka Google upraszczająca obsługę aparatu. [Dok. 7](07-architektura-aplikacji.md).

**Clamp / clip** – ścięcie wartości do przedziału (tu: 0–1). [Dok. 3](03-podstawy-obrazu-cyfrowego.md).

**Composable** – funkcja z adnotacją `@Composable`, opisująca fragment interfejsu w Jetpack Compose. [Dok. 6](06-podstawy-android-kotlin.md).

**Coroutine (współprogram)** – mechanizm Kotlina do pisania kodu asynchronicznego w stylu sekwencyjnym. [Dok. 6](06-podstawy-android-kotlin.md).

**CIE XYZ** – przestrzeń kolorów opisująca kolor jako reakcję „standardowego obserwatora". [Dok. 3](03-podstawy-obrazu-cyfrowego.md).

**CIEDE2000 (ΔE)** – miara różnicy kolorów odpowiadająca temu, jak ją odbiera człowiek.

**Cone (czopek)** – fotoreceptor odpowiadający za widzenie barw. [Dok. 2](02-jak-widza-zwierzeta.md).

**cpd** (*cycles per degree*) – cykle na stopień; jednostka ostrości wzroku (ile par jasny-ciemny pasek mieści się w jednym stopniu).

## D

**data class** – klasa Kotlina służąca głównie do przechowywania danych (automatyczne `equals`, `copy`, `toString`).

**Debounce** – opóźnianie reakcji, aż zmiany ustaną (import zdjęcia: 250 ms po ostatniej zmianie suwaka).

**Deuteranopia** – ślepota barw u człowieka (brak czopków M). Symulacje deuteranopii są punktem odniesienia dla widzenia psa.

**Dichromat** – organizm z dwoma rodzajami czopków (pies, kot).

**dp** – jednostka długości niezależna od gęstości ekranu.

## E

**EGL** – warstwa łącząca OpenGL z systemem okien (tworzenie kontekstu i powierzchni). [Dok. 8](08-potok-gpu.md).

**EXIF** – metadane w JPEG, m.in. orientacja zdjęcia. [Dok. 9](09-zdjecia.md).

**Exaggeration (wyolbrzymienie)** – mnożnik siły rozmycia ponad wartość fizyczną.

**Extension function (funkcja rozszerzająca)** – funkcja „dopisana" do istniejącego typu w Kotlinie.

## F

**Fragment shader** – program GPU uruchamiany dla każdego piksela, zwraca jego kolor. [Dok. 8](08-potok-gpu.md).

**Framebuffer** – cel rysowania: ekran lub tekstura.

## G

**Gamma** – (1) nieliniowe kodowanie jasności w plikach obrazów (sRGB); (2) parametr nocnego boostu (mniej = mocniej). [Dok. 3](03-podstawy-obrazu-cyfrowego.md), [4](04-matematyka-widzenia-zwierzat.md).

**Gamut** – zbiór kolorów możliwych do wyświetlenia na danym ekranie.

**Gaussa rozmycie** – uśrednianie z wagami w kształcie krzywej dzwonowej; parametr σ (sigma).

**GLSL** – język, w którym pisze się shadery OpenGL.

**Golden vectors (wektory wzorcowe)** – tabela „kolor wejściowy → oczekiwany wynik" wygenerowana przez prototyp i używana w testach. [Dok. 5](05-prototyp-python.md).

**GPU** – procesor graficzny (karta graficzna).

**Gradle** – system budowania. [Dok. 6](06-podstawy-android-kotlin.md).

## H

**HandlerThread** – wątek z kolejką zadań; w projekcie `AniVisionGL`.

## I

**Intent** – „zamiar": komunikat do systemu lub innej aplikacji.

## J

**Jetpack Compose** – nowoczesna biblioteka do deklaratywnego budowania interfejsu na Androidzie.

## K

**Kotlin** – język programowania używany do aplikacji. [Dok. 6](06-podstawy-android-kotlin.md).

**Kontekst GL** – stan OpenGL powiązany z wątkiem; bez niego nie można rysować.

## L

**Lambda** – funkcja zapisana w miejscu użycia: `{ x -> x + 1 }`.

**Lifecycle (cykl życia)** – fazy życia komponentu (utworzony, widoczny, zatrzymany, zniszczony).

**Linear light (światło liniowe)** – liczby proporcjonalne do ilości światła; w nich wykonuje się obliczenia. [Dok. 3](03-podstawy-obrazu-cyfrowego.md).

**Logcat** – dziennik zdarzeń systemu Android (`adb logcat`).

## M

**Macierz 3×3** – tabela dziewięciu liczb zamieniająca kolor RGB na inny kolor RGB.

**MAR** (*minimum angle of resolution*) – najmniejszy rozróżnialny kąt.

**MediaStore** – systemowa baza mediów (zdjęcia, wideo); w niej zapisujemy zdjęcia.

**Modifier** – łańcuch ozdobników opisujących rozmiar, układ i zachowanie elementów Compose.

**MTF** – funkcja przenoszenia kontrastu; mówi, jak bardzo wzór danej częstotliwości jest tłumiony przez rozmycie.

## N

**Neutral point (punkt neutralny)** – długość fali, którą dichromat widzi jako szarość.

**Null-safety** – gwarancja Kotlina, że zmienna bez znaku `?` nie może być pusta.

## O

**OES (external texture)** – specjalny rodzaj tekstury dla klatek z aparatu/wideo; GPU sam konwertuje YUV na RGB.

**OpenGL ES** – interfejs programowania GPU w urządzeniach mobilnych.

## P

**Pass-through** – tryb, w którym obraz nie jest filtrowany (i można użyć oryginału).

**Pbuffer** – powierzchnia renderowania poza ekranem.

**Perspective** – widok: `HUMAN`, `DOG`, `CAT`.

**Pręcik (rod)** – fotoreceptor do widzenia przy słabym świetle; bez rozróżniania barw.

**Prototyp** – część w Pythonie, gdzie powstała matematyka.

## R

**R8** – narzędzie zmniejszające i zaciemniające kod w buildzie wydaniowym.

**Rekompozycja** – ponowne wykonanie funkcji composable po zmianie stanu.

**RGB** – trzy składowe koloru: czerwona, zielona, niebieska.

## S

**Scissor test** – ograniczenie rysowania do prostokąta; używane do podziału ekranu.

**Shader** – program uruchamiany na GPU.

**SharedPreferences** – mały plik klucz–wartość; trwała pamięć aplikacji.

**Sigma (σ)** – szerokość rozmycia Gaussa w pikselach.

**SOLID** – zbiór zasad projektowania kodu obiektowego (m.in. jedna odpowiedzialność na klasę).

**sRGB** – standard kodowania kolorów używany w zdjęciach i na ekranach.

**State hoisting (podnoszenie stanu)** – trzymanie stanu u rodzica, a w dzieciach tylko wartości i callbacki.

**Surface / SurfaceTexture** – powierzchnia, na którą wpływają klatki lub na której rysujemy.

**suspend** – modyfikator funkcji, która może „czekać" bez blokowania wątku.

## T

**Tapetum lucidum** – lustrzana warstwa za siatkówką zwiększająca czułość na słabe światło u psów i kotów.

**Tekstura** – obraz w pamięci GPU.

**Toast** – krótki komunikat na ekranie.

**Trichromat** – organizm z trzema rodzajami czopków (człowiek).

## U

**Uniform** – parametr shadera ustawiany z zewnątrz, taki sam dla wszystkich pikseli.

**Uri** – „adres" zasobu (pliku, zdjęcia) w systemie.

## V

**Vertex shader** – program GPU liczący położenie wierzchołków.

**Viewport** – prostokąt obszaru rysowania.

**V0 / V1 / V2 / cat** – warianty macierzy kolorów. [Dok. 4](04-matematyka-widzenia-zwierzat.md).

## W

**Współprogram** – patrz *Coroutine*.

## Z

**Zasoby (`res/`)** – teksty, ikony, motywy aplikacji, wskazywane przez `R.string.xxx` itd.

**Zoom** – przybliżenie aparatu; współczynnik < 1 uruchamia obiektyw ultraszeroki.

---

**Nawigacja:** ⬅️ [10. Testy, budowanie i wydanie](10-testy-budowanie-wydanie.md) · [📚 Spis treści](README.md)
