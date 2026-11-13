# 3. Podstawy obrazu cyfrowego

**Nawigacja:** ⬅️ [2. Jak widzą ludzie, psy i koty](02-jak-widza-zwierzeta.md) · [📚 Spis treści](README.md) · [4. Matematyka widzenia zwierząt](04-matematyka-widzenia-zwierzat.md) ➡️

---

Ten dokument tłumaczy pojęcia z grafiki komputerowej, które są niezbędne do zrozumienia reszty. Jeśli wiesz, czym jest piksel i RGB, możesz przeskoczyć do sekcji o gamma (3.3) – to najczęstsze źródło nieporozumień.

## 3.1. Obraz to tablica liczb

Zdjęcie na komputerze to **siatka pikseli**. Każdy piksel ma kolor zapisany jako trzy liczby: **R** (czerwony), **G** (zielony), **B** (niebieski) – składowe, które ekran łączy, świecąc trzema maleńkimi diodami.

- W formacie **8-bitowym** każda składowa to liczba od **0** do **255**. `(255, 0, 0)` to czysta czerwień, `(0, 0, 0)` czerń, `(255, 255, 255)` biel, `(128, 128, 128)` szary.
- W obliczeniach często używa się liczb zmiennoprzecinkowych od **0.0** do **1.0** (wartość 8-bitowa dzielona przez 255).

Obraz 1920×1080 to ponad 2 miliony pikseli, a więc ponad 6 milionów liczb. Dlatego każde przetwarzanie obrazu na żywo wymaga mocy – stąd karta graficzna (GPU) w tym projekcie.

## 3.2. Dlaczego RGB wystarcza człowiekowi (a psu nie jest potrzebne)?

Bo człowiek ma trzy rodzaje czopków (patrz [dok. 2](02-jak-widza-zwierzeta.md)). Ekran, mieszając trzy kolory, może pobudzić trzy rodzaje czopków w dowolnych proporcjach. Dla psa wystarczyłyby **dwa** „suwaki", ale ekran i tak ma trzy – więc obraz „psi" też pokazujemy w RGB, tyle że taki, w którym trzy liczby są od siebie zależne (bo niosą tylko dwie niezależne informacje).

## 3.3. Gamma: dlaczego nie wolno liczyć bezpośrednio na liczbach z pliku

To najważniejsza i najbardziej zaskakująca rzecz.

### Problem

Liczba `128` w pliku JPEG **nie oznacza** „połowy jasności fizycznej". Oko ludzkie jest bardzo czułe na różnice w ciemnych tonach, a mało czułe w jasnych. Dlatego w plikach obrazów stosuje się **kodowanie gamma** (standard **sRGB**): wartości w pliku są „ściśnięte" tak, żeby więcej liczb przypadało na ciemne tony. To oszczędza bity, ale oznacza, że liczba w pliku jest **nieliniowo** związana z faktyczną ilością światła.

Przykład: piksel o wartości sRGB `188` z 255 emituje tylko **około połowę** światła, które emituje piksel `255`. Piksel `128` emituje około 21% światła.

### Dwie reprezentacje

| | sRGB (zakodowane, „gamma") | Liniowe (ang. *linear light*) |
|---|---|---|
| Co oznacza liczba | Wartość „percepcyjna", taka jak w pliku | Proporcjonalna do ilości światła |
| Do czego służy | Zapis i wyświetlanie | **Obliczenia fizyczne** (mieszanie, rozmywanie, macierze) |

### Wzory (standard IEC 61966-2-1)

Z sRGB do liniowego (`c` w zakresie 0–1):

```
jeśli c ≤ 0.04045:   liniowe = c / 12.92
w przeciwnym razie:  liniowe = ((c + 0.055) / 1.055) ^ 2.4
```

Z liniowego z powrotem do sRGB:

```
jeśli liniowe ≤ 0.0031308:   c = liniowe × 12.92
w przeciwnym razie:          c = 1.055 × liniowe^(1/2.4) − 0.055
```

Dla małych wartości to prosta proporcja, dla większych – potęga ok. 2,4 (stąd potoczna nazwa „gamma 2.2").

### Dlaczego to ważne w tym projekcie?

Gdybyś rozmył czarno-biały wzór szachownicy w sRGB, dostałbyś szarość `0.5` (czyli `128`). Fizycznie poprawnie – uśredniając *światło* – powinieneś dostać `188`. Różnica jest widoczna gołym okiem. Dlatego **wszystkie operacje w projekcie** (rozmycie, mnożenie przez macierz, rozjaśnianie cieni) są robione na **świetle liniowym**:

```
piksel sRGB ──→ do liniowego ──→ [rozmycie] ──→ [boost] ──→ [macierz] ──→ do sRGB ──→ ekran
```

Test w projekcie potwierdza to liczbowo: czarno-biała szachownica po rozmyciu w świetle liniowym daje po zakodowaniu wartość ok. **188** (0,735 w skali 0–1), a nie 128.

## 3.4. Kolor jako wektor i macierz 3×3

Kolor piksela to trzy liczby, czyli **wektor** `(R, G, B)`. **Macierz 3×3** to tabelka dziewięciu liczb, która zamienia jeden wektor na inny:

```
┌ R' ┐   ┌ a b c ┐   ┌ R ┐
│ G' │ = │ d e f │ × │ G │
└ B' ┘   └ g h i ┘   └ B ┘
```

czyli:

```
R' = a·R + b·G + c·B
G' = d·R + e·G + f·B
B' = g·R + h·G + i·B
```

Każda nowa składowa to **ważona suma** starych. To jedna z najprostszych i najtańszych operacji w grafice – GPU wykonuje ją na milionach pikseli bez wysiłku.

### Przykład: macierz identycznościowa

```
┌ 1 0 0 ┐
│ 0 1 0 │   → nic nie zmienia: R'=R, G'=G, B'=B
└ 0 0 1 ┘
```

Tak działa „widok człowieka" w aplikacji: obraz nietknięty.

### Przykład: macierz „psia" (V2, uproszczona)

```
┌  0.325  0.908  −0.233 ┐
│  0.242  0.675   0.083 │
└ −0.001  0.001   0.9996┘
```

Czerwień `(1, 0, 0)` (liniowo) → `(0.325, 0.242, −0.001)` – czyli po przeliczeniu na sRGB 8-bit: `(154, 135, 0)`, oliwkowy brąz. Zieleń `(0, 1, 0)` → `(0.908, 0.675, 0.001)` → `(244, 214, 5)`, żółć. **Czerwień i zieleń stały się podobnymi odcieniami żółci** – dokładnie o to chodziło.

### Dwie ważne własności tych macierzy

1. **Suma każdego wiersza wynosi 1.** Wtedy dla szarego `(s, s, s)` dostajemy `(s, s, s)` – szary pozostaje szary.
2. **Macierz ma rząd 2**, nie 3 (wyznacznik ≈ 0). To matematyczny odpowiednik dichromatyzmu: z trzech niezależnych informacji robią się dwie. Nie da się „cofnąć" takiej operacji – różne kolory wejściowe dają ten sam kolor wyjściowy.

### Kolejność w macierzy: wiersze i kolumny

Ten szczegół jest źródłem wielu błędów w programowaniu graficznym:

- W Pythonie i w pliku JSON macierz jest zapisana **wierszami** (ang. *row-major*): najpierw cały pierwszy wiersz, itd.
- W GLSL (język shaderów) konstruktor `mat3(...)` przyjmuje liczby **kolumnami** (ang. *column-major*): pierwsze trzy liczby to pierwsza kolumna.
- Dlatego w aplikacji istnieją dwie funkcje: `linearMatrix()` (wierszami) i `linearMatrixColumnMajor()` (kolumnami, dla GPU). Test pilnuje, że jedna jest transpozycją drugiej.

## 3.5. Gamut: kolory, których ekran nie potrafi pokazać

**Gamut** to zbiór kolorów, które dany ekran jest w stanie wyświetlić. W sRGB każda składowa musi mieścić się w przedziale 0–1.

Wynik mnożenia przez macierz bywa spoza tego zakresu: np. `−0.2` albo `1.3`. Takiego „ujemnego światła" nie ma jak wyświetlić. Co robić? Trzeba **dopasować** wynik do gamutu. Rozważane były dwie strategie:

| Strategia | Co robi | Zaleta | Wada |
|-----------|---------|--------|------|
| **clip** (obcięcie) | Każdą składową z osobna ścina do 0–1 | Prosta, szybka | Lekko zmienia odcień i jasność mocno nasyconych kolorów |
| **desaturate** | Przybliża kolor do szarości o tej samej jasności, aż się zmieści | Zachowuje jasność i odcień | Traci nasycenie; czysty niebieski robi się stalowy |

Autorzy porównali je liczbowo (miara ΔE, patrz niżej) i wybrali **clip**. W shaderze to po prostu `clamp(wartość, 0.0, 1.0)`.

Jak często to się zdarza? Na zwykłych zdjęciach średnio ok. 0,9% pikseli wypada poza gamut (najgorszy przypadek: 3,1%). Na sztucznych wykresach z nasyconymi błękitami – nawet 74%.

## 3.6. Rozmycie (konwolucja, rozmycie Gaussa)

### Intuicja

Rozmycie to uśrednianie każdego piksela z jego sąsiadami. Im większy promień uśredniania, tym mocniejsze rozmycie.

### Rozmycie Gaussa

W rozmyciu **Gaussa** sąsiedzi mają różne wagi: im bliżej piksela, tym ważniejszy. Wagi układają się w krzywą dzwonową (rozkład normalny):

```
waga
 |       ▄█▄
 |     ▄█████▄
 |  ▃▆█████████▆▃
 +----------------→ odległość od piksela
 -3σ    0     +3σ
```

Parametr **σ (sigma)** to „szerokość dzwonu" mierzona w pikselach. Mała sigma (0,5 px) – ledwo widoczne zmiękczenie. Duża sigma (10 px) – mocne rozmazanie.

### Rozmycie rozdzielne (separowalne)

Rozmycie Gaussa 2D można zastąpić dwoma rozmyciami 1D: najpierw w poziomie, potem w pionie. Zamiast sąsiadów w kwadracie `n × n` (n² próbek na piksel) wystarczy `2n` próbek. To ogromna oszczędność, więc aplikacja robi **dwa przebiegi** (poziomy i pionowy) – patrz [dok. 8](08-potok-gpu.md).

### Skąd sigma wynika z ostrości wzroku?

Opisane w [dok. 4](04-matematyka-widzenia-zwierzat.md): ostrość wzroku zwierzęcia, wyrażona w stopniach kątowych, jest zamieniana na piksele przy znanym polu widzenia kamery (70°).

## 3.7. Szum i krawędzie (drobne, ale ważne)

Podczas rozmywania na krawędzi obrazu brakuje sąsiadów. Są dwa popularne rozwiązania:

- **lustrzane odbicie** (ang. *mirror*) – prototyp w Pythonie tak robi,
- **powtórzenie skrajnego piksela** (ang. *clamp-to-edge*) – tak robi shader na GPU.

Różnica jest niewidoczna w praktyce.

## 3.8. Różnica kolorów: ΔE i CIEDE2000

Jak policzyć, **o ile** dwa kolory się różnią w sposób odczuwalny dla człowieka? Odległość w RGB się do tego nie nadaje (równe odległości w RGB nie dają równych różnic w odbiorze). Używa się specjalnej przestrzeni **CIELAB**, w której odległość odpowiada wrażeniu, i miary **ΔE (CIEDE2000)**: wartość ok. 1 to różnica ledwo zauważalna, kilka – wyraźna.

W prototypie służy to do porównywania wariantów macierzy i strategii gamutu (np. „V0 vs V2: średnio ΔE 4,0").

## 3.9. XYZ i przestrzenie kolorów – krótko

Żeby przejść od RGB do „widzenia czopkami", potrzebny jest sposób opisania koloru niezależny od konkretnego ekranu. Służy do tego przestrzeń **CIE XYZ** (1931), opisująca kolor jako reakcję „standardowego obserwatora". Prototyp używa jej wewnętrznie (macierz liniowe sRGB → XYZ, biały punkt **D65** – standardowe światło dzienne). Dla użytkownika aplikacji to szczegół; dla zrozumienia matematyki – [dok. 4](04-matematyka-widzenia-zwierzat.md).

## 3.10. Piksel na GPU: shader i tekstura

Dwa pojęcia, które pojawią się w [dok. 8](08-potok-gpu.md):

- **Tekstura** – obraz zapisany w pamięci karty graficznej.
- **Shader fragmentów** – mały program, który GPU uruchamia **osobno dla każdego piksela** wynikowego obrazu, równolegle (tysiące naraz). Dostaje współrzędne piksela, odczytuje teksturę, liczy kolor i go zwraca. Właśnie tam żyje mnożenie przez macierz.

## 3.11. Podsumowanie pojęć

| Pojęcie | W jednym zdaniu |
|---------|-----------------|
| Piksel | Jeden punkt obrazu, trzy liczby RGB |
| sRGB / gamma | Sposób zapisu jasności „skompresowany" pod ludzkie oko |
| Światło liniowe | Liczby proporcjonalne do ilości światła; tu robimy obliczenia |
| Macierz 3×3 | Tabela 9 liczb zamieniająca kolor na kolor |
| Gamut | Zakres kolorów możliwych do wyświetlenia |
| Clamp / clip | Ścięcie wartości do 0–1 |
| Sigma | Siła rozmycia Gaussa w pikselach |
| ΔE | Miara różnicy dwóch kolorów odczuwalnej przez człowieka |

---

**Nawigacja:** ⬅️ [2. Jak widzą ludzie, psy i koty](02-jak-widza-zwierzeta.md) · [📚 Spis treści](README.md) · [4. Matematyka widzenia zwierząt](04-matematyka-widzenia-zwierzat.md) ➡️
