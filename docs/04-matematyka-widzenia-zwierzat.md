# 4. Matematyka widzenia zwierząt

**Nawigacja:** ⬅️ [3. Podstawy obrazu cyfrowego](03-podstawy-obrazu-cyfrowego.md) · [📚 Spis treści](README.md) · [5. Prototyp w Pythonie](05-prototyp-python.md) ➡️

---

W tym dokumencie pokazujemy, **co dokładnie** program robi z obrazem. Są tu trzy niezależne efekty:

1. **Kolor** – zamiana trzech informacji o kolorze na dwie (macierz 3×3),
2. **Ostrość** – rozmycie obrazu o odpowiednio dobraną sigmę,
3. **Noc** – rozjaśnianie cieni (efekt artystyczny).

Wymagana wiedza: [dok. 2](02-jak-widza-zwierzeta.md) i [dok. 3](03-podstawy-obrazu-cyfrowego.md).

## 4.1. Kolejność operacji w potoku

```
piksel sRGB
   │ 1. sRGB → światło liniowe
   ▼
 [2. rozmycie Gaussa]            (opcjonalne, "ostrość")
   ▼
 [3. rozjaśnienie cieni]         (opcjonalne, "nocny boost"; kot ma własne)
   ▼
 4. mnożenie przez macierz 3×3   ("kolor zwierzęcia")
   ▼
 5. obcięcie do 0–1 (clamp)      ("gamut")
   ▼
 6. światło liniowe → sRGB
   ▼
 piksel na ekranie
```

Dlaczego taka kolejność?

- Rozmycie i macierz są operacjami *liniowymi*, więc kolejność między nimi nie zmienia wyniku (prototyp zaznacza to w komentarzach). W aplikacji rozmycie jest robione najpierw, a macierz dopiero w ostatnim przebiegu (patrz [dok. 8](08-potok-gpu.md)).
- Rozjaśnianie cieni skaluje piksel na podstawie jego jasności wejściowej, więc musi być **przed** macierzą (macierz zmienia jasność i barwę).
- Obcięcie do 0–1 dopiero na końcu, bo wcześniej nie wolno tracić informacji (ujemne wartości po drodze mają jeszcze sens matematyczny).

## 4.2. Kolor: skąd bierze się macierz 3×3

To najciekawsza część. Cel: dla każdego koloru RGB wyznaczyć, **co czują dwa czopki zwierzęcia**, a potem pokazać to na ekranie RGB.

### Krok A: model ekranu – jakie światło emituje piksel?

Komputerowy kolor `(R, G, B)` to rozkaz „zapal czerwoną diodę na R, zieloną na G, niebieską na B". Żeby policzyć, co zobaczy czopek, musimy wiedzieć, **jakie widmo** (rozkład energii po długościach fal) emituje każda z diod. Telefonów mamy tysiące rodzajów i ich widm nie znamy, więc **zakładamy model**: każda dioda świeci wąskim „dzwonem" (rozkładem Gaussa):

| Dioda | Środek | Szerokość (sigma) |
|-------|--------|-------------------|
| czerwona | 610 nm | 20 nm |
| zielona | 550 nm | 20 nm |
| niebieska | 465 nm | 20 nm |

Trzy współczynniki skali są tak dobrane, aby pełna biel (R=G=B=1) dawała dokładnie biel D65 (jasność Y = 1).

### Krok B: macierz „połowu światła" C (2×3)

Dla każdego rodzaju czopka (S i L) liczymy **całkę** (sumę po wszystkich długościach fal 380–720 nm) z iloczynu: *czułość czopka × widmo diody*. Wynik to tabela 2×3:

```
        R        G        B
S  [ 0.00000  0.00208  0.46327 ]   ← czopek S widzi głównie niebieską diodę
L  [ 0.23649  0.66140  0.24673 ]   ← czopek L widzi głównie zieloną, trochę czerwoną i niebieską
```

(Wartości dla psa; dla kota czopek S ma inny szczyt, więc jego wiersz jest inny.)

Mnożenie `C × (R, G, B)` daje **dwie liczby**: sygnał czopka S i sygnał czopka L. To jest cała informacja o kolorze, którą dostaje mózg psa.

### Krok C: z dwóch liczb z powrotem do trzech

Ekran wymaga trzech liczb RGB. Trzeba więc wymyślić *jakiś* kolor RGB, który wywoła u psa **te same** dwa sygnały. Takich kolorów jest nieskończenie wiele (bo trzy niewiadome, dwa równania). Wybieramy jeden, narzucając, żeby wynik leżał na **płaszczyźnie** rozpiętej przez dwa kolory:

- **biel** `w` (żeby szary zostawał szary),
- **kolor kotwiczny** `a` (anchor) – tu: czyste światło monochromatyczne o długości **475 nm**.

Wzór na macierz:

```
M = [ w  a ] · ( [ C·w  C·a ] )⁻¹ · C
```

W prostych słowach:

1. `C` zamienia RGB na dwa sygnały czopków.
2. `( [C·w C·a] )⁻¹` zamienia te dwa sygnały na „ile bieli i ile kotwicy" trzeba wziąć, żeby je uzyskać.
3. `[w a]` składa z tego kolor RGB: `ile_bieli · w + ile_kotwicy · a`.

Wynikiem jest macierz `M` (3×3). Dla psa (wariant V2):

```
      R        G        B
R' [  0.32542   0.90757  −0.23300 ]
G' [  0.24160   0.67495   0.08345 ]
B' [ −0.00108   0.00145   0.99963 ]
```

### Co gwarantuje taka konstrukcja? (sprawdzone testami)

| Własność | Znaczenie |
|----------|-----------|
| `M · w = w` | Biel (i każdy szary) się nie zmienia |
| `M · M = M` | Powtórne zastosowanie nic nie zmienia (rzutowanie) |
| `C · M = C` | Pies **nie odróżni** koloru wejściowego od wyjściowego – zachowujemy dokładnie jego dwa sygnały |
| rząd(M) = 2, det ≈ 0 | Dwie niezależne informacje zamiast trzech |
| Suma wierszy = 1 | To samo co pierwszy punkt |

Czyli: *oryginalny* i *przekształcony* kolor wywołują w oku psa **identyczną reakcję**; różni się tylko to, co widzi człowiek patrzący na ekran.

### Co z kotwicą 475 nm?

Dlaczego akurat 475 nm? To praktyka z symulacji ślepoty barw u ludzi (Brettel, Viénot i Mollon, 1997). **Nie ma danych dla psa**, które by to potwierdzały – to konwencja. Autorzy sprawdzili: zmiana kotwicy na 575 nm zmienia macierz o 0,079 (norma Frobeniusa), a kotwica w czystym niebieskim sRGB – o 0,314. Wniosek z `NOTES.md`: *kotwica ma większe znaczenie niż krzywe czopków*. Dlatego wybór V2 nie jest twierdzeniem o „dokładności", tylko o wygodzie i spójności.

Kotwica 475 nm ma w sRGB **ujemną składową czerwoną** `(−0.224, 0.113, 1.090)`. To nie błąd (kotwica tylko wyznacza płaszczyznę), ale powód, dla którego bardzo nasycone błękity po przekształceniu wypadają poza gamut i muszą być obcięte.

### Wariant V0 i V1

| Wariant | Co to | Rola |
|---------|-------|------|
| **V0** | Macierz deuteranopii Viénota, Brettela, Mollona (1999). Wiersze 1 i 2 są identyczne – czerwień i zieleń zlewają się w jedną oś | zapasowy (fallback) |
| **V1** | Macierz Machado i in. (2009) z biblioteki `colour-science` | tylko porównanie |
| **V2** | Własna, wyliczona z krzywych czopków psa | **domyślny dla psa** |
| **cat** | To samo wyprowadzenie co V2 z szczytem czopka S = 450 nm | domyślny dla kota |

Dlaczego V2, skoro nie jest „dokładniejsza" od V0? Powody z `NOTES.md`:

1. Jest zbudowana z danych psa i spełnia `C·M = C` dokładnie w modelu.
2. Niezależnie zbudowana V1 leży blisko niej (ΔE 1,15), a V0 jest odstająca (ΔE ok. 4).
3. Mniej obcina kolorów na zdjęciach (0,9% vs 1,3%).
4. Mniej zaburza oś niebiesko-żółtą.

### Test na światłach jednobarwnych (spectral strip)

Walidacja macierzy: przepuszczamy przez nią światła jednobarwne od 420 do 680 nm. Oczekiwany efekt dla psa: **niebieski → szary w okolicy 480 nm → żółty**. Wynik:

- dla „prawdziwego" światła spektralnego: szary w **486,9 nm** (analitycznie 486,7 nm),
- dla światła pokazanego przez model ekranu: ok. 498 nm – ta rozbieżność ok. 11 nm jest skutkiem uproszczonego modelu ekranu; w realnych, szerokopasmowych scenach dotyczy głównie nasyconych błękitów-zieleni.

Powyżej ok. 560 nm każda długość fali daje ten sam nasycony żółty, bo oba czopki leżą już „po tej samej stronie" i różnią się tylko intensywnością.

### Wartości dla kota

```
M_cat =
[  0.32865  0.90323  −0.23188 ]
[  0.24255  0.67367   0.08378 ]
[ −0.00672  0.00905   0.99768 ]
```

Prawie identyczna z psią – bo szczyty czopków (429 vs 450 nm) są blisko i model jest przybliżony. Punkt neutralny kota: 499,18 nm (nie ma źródła do porównania).

## 4.3. Ostrość: ile rozmywać?

### Pojęcia

- **cpd** (cykle na stopień): ile par jasny+ciemny pasek mieści się w jednym stopniu kąta widzenia.
- **MAR** (minimum angle of resolution): najmniejszy rozróżnialny kąt; MAR = 1/(2·cpd).
- **acuity_ratio**: ile razy MAR zwierzęcia jest większy niż człowieka.
  - człowiek: 30 cpd (MAR = 1′),
  - pies: **3,75** → 30/3,75 = **8 cpd**, MAR = 3,75′,
  - kot: **3,5** → ≈ 8,5 cpd.

### Idea

Człowiek rozdziela wzory aż do 30 cpd. Pies tylko do 8 cpd. Co dzieje się z wzorami drobniejszymi? Zlewają się. To dokładnie robi rozmycie Gaussa – tłumi drobne wzory, zostawiając grube.

### Wzór na sigmę

```
px_na_stopień = dłuższy_bok_w_pikselach / pole_widzenia_w_stopniach   (pole = 70°)
MAR_w_stopniach = acuity_ratio / 60
sigma_px = k · (MAR_w_stopniach / 2) · px_na_stopień · wyolbrzymienie
k = 2·√(2·ln(10)) / π ≈ 1.366
```

**Skąd `k`?** Rozmycie Gaussa o szerokości σ osłabia wzór o częstotliwości f do kontrastu `exp(−2π²σ²f²)` (tzw. MTF). Chcemy, żeby przy granicznej częstotliwości zwierzęcia (8 cpd dla psa) kontrast spadł do **10%**. Rozwiązanie tego równania na σ daje właśnie `k`. To **wybór autorów** (kryterium 10%), nie fakt biologiczny: inna wartość zmieniłaby tylko siłę bazową.

### Przykład liczbowy (pies, zdjęcie 1920 px w dłuższym boku)

```
px_na_stopień   = 1920 / 70 = 27.43
MAR             = 3.75 / 60 = 0.0625°   → połowa = 0.03125°
0.03125 × 27.43 = 0.857
0.857 × 1.366   = 1.17 px   ← sigma
```

Czyli rozmycie o promieniu ok. 1 piksela. Niewiele! To prawdziwe, fizyczne rozmycie dla zdjęcia z telefonu – ledwo zauważalne.

### Dlaczego jest „wyolbrzymienie" (exaggeration)?

Na ekranie telefonu fizyczne rozmycie jest mało widoczne, więc dodano mnożnik. Obserwacje autorów (z `NOTES.md`):

| Mnożnik | Efekt |
|---------|-------|
| ×1 | Delikatne zmiękczenie drobnych tekstur |
| ×2 | Wyraźne zmiękczenie, kształty zachowane |
| ×4 | Zdecydowanie rozmyte |
| ×8 | Mocno rozmyte, ale scena rozpoznawalna |
| ×16 | Zniszczona treść |

W aplikacji suwak „Blur" ma zakres **1,0× – 3,5×**, domyślnie **3,0×** po włączeniu (1,0 uznano za zbyt subtelne). Górne ograniczenie 3,5 wynika z limitu shadera (maks. 32 próbki na stronę) – powyżej ok. 3,7× na zdjęciu 4096 px rozmycie przestaje rosnąć. Samo rozmycie jest **domyślnie wyłączone**. Wzór w kodzie ogranicza mnożnik do `0…8`.

### Przykładowe sigmy (pies)

| wyolbrzymienie | 1920 px | 1440 px | 1080 px | 640 px |
|---|---|---|---|---|
| ×1 | 1,17 | 0,88 | 0,66 | 0,39 |
| ×2 | 2,34 | 1,76 | 1,32 | 0,78 |
| ×4 | 4,68 | 3,51 | 2,63 | 1,56 |
| ×8 | 9,37 | 7,03 | 5,27 | 3,12 |

Sigma zależy od rozdzielczości, bo liczy się w pikselach: im więcej pikseli, tym większy promień potrzebny, żeby objąć ten sam kąt.

**Uwaga:** odniesienie do *dłuższego boku* sprawia, że ten sam obraz w pionie i poziomie dostaje takie samo rozmycie.

### Rozmycie obwodowe (tylko kot, efekt artystyczny)

Ostrość spada wraz z oddalaniem się od środka siatkówki. Dla kota dodano rozmycie rosnące ku brzegom:

```
sigma(r) = sigma · (1 + peripheral · r²)
r² = 0 w środku kadru, 1 w rogach
```

Dla kota `peripheral = 1`, więc w rogach rozmycie jest 2× mocniejsze. Dla psa `peripheral = 0`. Wartość nie ma źródła (autorzy wprost to przyznają).

W prototypie Pythona jest to realizowane przez 5 jednolitych rozmyć mieszanych liniowo. W shaderze – przez liczenie sigmy osobno dla każdego piksela (patrz [dok. 8](08-potok-gpu.md)).

## 4.4. Noc: rozjaśnianie cieni

### Czym jest, a czym nie

**Nie** jest modelem widzenia pręcikowego. To prosta funkcja, która **rozjaśnia ciemne obszary**, zostawiając jasne prawie bez zmian. Autorzy oznaczają ją jako **artystyczną**.

### Wzór

Dla piksela w świetle liniowym `rgb` i parametru `gamma` (z przedziału 0,5…1, gdzie 1 = bez zmiany, mniej = mocniej):

```
Y        = 0.2127·R + 0.7152·G + 0.0722·B       (jasność piksela)
skala    = Y^(gamma − 1)                        (>1 dla Y<1, bo wykładnik ujemny)
skala    = min(skala, 1 / max(R,G,B))           (żeby żadna składowa nie przekroczyła 1)
wynik    = rgb · skala
```

Przykład: piksel o jasności Y = 0,1 przy gamma = 0,85: `0.1^(−0.15) ≈ 1,41` – piksel jest rozjaśniony 1,41×. Piksel o Y = 0,9: `0.9^(−0.15) ≈ 1,016` – prawie bez zmian. Czerń (Y→0) pozostaje czernią, bo mnożymy czerń.

Kluczowe: **ta sama skala** mnoży R, G i B, więc **proporcje kolorów** (odcień, nasycenie) zostają. Zmienia się tylko jasność.

### Dlaczego jest „limit 1/max"?

Wykryty w przeglądzie kodu błąd: czysty niebieski przy gamma = 0,85 był mnożony przez 1,48, a przy 0,7 przez 2,2 – wartość >1 była potem ścinana po macierzy i zmieniała odcień. Limit gwarantuje, że żadna składowa nie wyjdzie poza 1.

### Wartości

| gamma | Opis |
|-------|------|
| 1,0 | wyłączone |
| 0,85 | „sugerowane" – naturalne otwarcie cieni |
| 0,7 | najsilniejsze dopuszczone przed zamgleniem czerni |
| 0,55 | już mgliste (poza zakresem aplikacji) |

W aplikacji suwak „Night boost" ma siłę `s` z zakresu 0,1…1,0, zamienianą na gamma = `1 − s·0,3`. Domyślna siła ≈ 0,5 daje dokładnie gamma = 0,85.

### Kot ma wbudowane rozjaśnienie

Profil kota zawsze stosuje gamma = **0,85** (artystyczna namiastka tapetum). Gdy użytkownik włączy też nocny boost, **nie sumujemy** ich, tylko bierzemy mocniejsze: `min(gamma_użytkownika, gamma_kota)`, z dolnym limitem 0,5. Pies nie ma wbudowanego, więc „wyłączone" znaczy wyłączone.

Boost działa **tylko na przefiltrowaną stronę** – strona „Human" w widoku podzielonym zostaje nietknięta.

## 4.5. Podsumowanie: co każdy profil robi

| | Człowiek | Pies | Kot |
|---|---|---|---|
| Macierz | brak (identyczność) | V2 (V0 jako zapas) | cat |
| Ostrość (`acuity_ratio`) | – (nie rozmywany) | 3,75 | 3,5 |
| Rozmycie obwodowe | – | 0 | 1,0 (artystyczne) |
| Wbudowany nocny boost | – | brak (1,0) | gamma 0,85 (artystyczne) |
| Zoom aparatu | normalny | normalny | najszerszy (ultra-szeroki obiektyw, jeśli jest) |

## 4.6. Znane ograniczenia (powtórzenie z dokumentacji prototypu)

1. **Model ekranu**: dopasowana jest tylko biel, nie realne widma diod telefonu. Zmiana środków o ±10 nm zmienia macierz o 0,09–0,23 (norma Frobeniusa) – tyle samo co różnica między V0 a V2.
2. **Kotwica**: konwencja, nie dane psa.
3. **Gamut**: nasycone błękity są obcinane.
4. **Ostrość**: wybór kryterium 10% to decyzja modelowa; liczba 20/75 pochodzi z planu.
5. **Aparat**: zakładamy, że daje sRGB; telefony mogą stosować własne mapowanie tonów.
6. **Nie modelujemy**: soczewki, UV, pręcików i tapetum, migotania, pola widzenia.
7. **Brak wzorca**: nie wiemy, co faktycznie widzi pies – walidujemy względem znanych ograniczeń.

---

**Nawigacja:** ⬅️ [3. Podstawy obrazu cyfrowego](03-podstawy-obrazu-cyfrowego.md) · [📚 Spis treści](README.md) · [5. Prototyp w Pythonie](05-prototyp-python.md) ➡️
