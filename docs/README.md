# Dokumentacja projektu AniVision

Witaj! Ta dokumentacja tłumaczy **od zera** wszystko, co dzieje się w projekcie AniVision – aplikacji na Androida, która pokazuje obraz z aparatu telefonu tak, jak (w przybliżeniu) widziałby go pies albo kot.

Zakładamy, że czytelnik:

- nigdy nie tworzył aplikacji na Androida,
- nie napisał ani jednej linijki w języku Kotlin,
- nie wie nic o przetwarzaniu obrazu (piksele, kolory, rozmycie, shadery),
- nie wie, jak zbudowane jest oko człowieka, psa i kota.

Dlatego każdy dokument zaczyna od podstaw i dopiero potem przechodzi do szczegółów projektu. Jeśli trafisz na nieznane słowo, zajrzyj do [słownika](11-slownik.md).

## Jak czytać tę dokumentację

Dokumenty są ułożone tak, żeby czytać je po kolei, ale każdy jest na tyle samodzielny, że możesz wskoczyć do wybranego tematu.

| # | Dokument | O czym jest | Dla kogo |
|---|----------|-------------|----------|
| 1 | [O projekcie](01-o-projekcie.md) | Co robi aplikacja, jak jest zorganizowane repozytorium, jak cała idea składa się w całość | Wszyscy – zacznij tutaj |
| 2 | [Jak widzą ludzie, psy i koty](02-jak-widza-zwierzeta.md) | Budowa oka, czopki i pręciki, dwa vs trzy rodzaje czopków, ostrość wzroku, widzenie w nocy | Wszyscy, szczególnie bez wiedzy biologicznej |
| 3 | [Podstawy obrazu cyfrowego](03-podstawy-obrazu-cyfrowego.md) | Piksel, RGB, gamma, światło liniowe, macierze, rozmycie | Wszyscy, szczególnie bez wiedzy o grafice |
| 4 | [Matematyka widzenia zwierząt](04-matematyka-widzenia-zwierzat.md) | Skąd bierze się macierz 3×3, jak liczone jest rozmycie (ostrość) i rozjaśnianie cieni | Chcesz zrozumieć „co dokładnie robi program z kolorami" |
| 5 | [Prototyp w Pythonie](05-prototyp-python.md) | Biblioteka `anivision`, skrypty, eksport stałych do aplikacji | Chcesz uruchamiać eksperymenty lub zmieniać liczby |
| 6 | [Podstawy Androida i Kotlina](06-podstawy-android-kotlin.md) | Czym jest aplikacja na Androida, Gradle, manifest, Activity, Jetpack Compose, współprogramy, kluczowe konstrukcje Kotlina | Nigdy nie pisałeś aplikacji na Androida |
| 7 | [Architektura aplikacji](07-architektura-aplikacji.md) | Pakiet po pakiecie: co robi każdy plik Kotlina, jak działa stan ekranu | Chcesz czytać lub zmieniać kod aplikacji |
| 8 | [Potok GPU i shadery](08-potok-gpu.md) | OpenGL, EGL, shadery, droga klatki z aparatu na ekran, podział ekranu | Chcesz zrozumieć najtrudniejszą część |
| 9 | [Zdjęcia: aparat, galeria, zapis](09-zdjecia.md) | Robienie zdjęć, import z galerii, EXIF, zapis i udostępnianie | Chcesz zrozumieć ścieżkę nieruchomych obrazów |
| 10 | [Testy, budowanie i wydanie](10-testy-budowanie-wydanie.md) | Jak zbudować i uruchomić aplikację, jakie są testy, podpisywanie APK | Chcesz uruchomić projekt |
| 11 | [Słownik pojęć](11-slownik.md) | Alfabetyczna lista terminów | Do podglądania w trakcie czytania |

## Ścieżki czytania

- **„Chcę tylko zrozumieć pomysł"** → 1, 2, potem wstęp z 3 i pierwsza połowa 4.
- **„Chcę zrozumieć obraz i kolory, ale nie Androida"** → 2, 3, 4, 5.
- **„Chcę dołączyć do rozwoju aplikacji"** → 1, 6, 7, 8, 9, 10 (a 3 i 4 w razie potrzeby).
- **„Chcę zmienić liczby opisujące widzenie zwierząt"** → 4, 5, a potem sekcja o synchronizacji w 10.

## Ważne zastrzeżenie (powtarzane też w samej aplikacji)

AniVision to **przybliżenie**. Psy i koty są dichromatami (mają dwa rodzaje czopków). Aplikacja symuluje *informację o kolorze*, którą te dwa rodzaje czopków mogą zebrać. **Nie pokazuje tego, co zwierzę subiektywnie przeżywa** – tego nie wie nikt, bo nie możemy zapytać psa, jak mu się to „jawi". Część liczb (np. ostrość wzroku kota, „rozjaśnianie nocy") jest świadomie artystyczna albo niepewna; zawsze zaznaczamy to w dokumentacji.

## Konwencje w dokumentacji

- Ścieżki plików podawane są względem katalogu głównego repozytorium (np. `src/android/app/...`).
- Fragmenty kodu są często **uproszczone**, żeby ułatwić zrozumienie; pełny, aktualny kod znajdziesz w repozytorium.
- Liczby (macierze, sigmy) pochodzą z plików `src/prototype/export/` i mogą się zmienić po ponownym wygenerowaniu eksportu – źródłem prawdy jest zawsze kod.

## Istniejące, starsze dokumenty w repozytorium

W katalogu głównym leżą też notatki projektowe, pisane po angielsku, w trakcie tworzenia projektu:

- `README.md` – krótki opis (jego część o stanie Androida jest nieaktualna),
- `Python.md` – plan prototypu,
- `Android.md` – plan aplikacji,
- `LowLight.md` – projekt „rozjaśniania nocy",
- `src/prototype/NOTES.md` – wszystkie liczby i uzasadnienia decyzji z prototypu,
- `src/android/RELEASING.md` – jak wydać aplikację.

Niniejsza dokumentacja jest ich uzupełnieniem: tłumaczy wszystko od podstaw, po polsku.
