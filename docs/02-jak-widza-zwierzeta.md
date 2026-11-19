# 2. Jak widzą ludzie, psy i koty

**Nawigacja:** ⬅️ [1. O projekcie](01-o-projekcie.md) · [📚 Spis treści](README.md) · [3. Podstawy obrazu cyfrowego](03-podstawy-obrazu-cyfrowego.md) ➡️

---

Ten dokument nie wymaga żadnej wiedzy z biologii. Wyjaśniamy tylko to, co jest potrzebne do zrozumienia projektu.

## 2.1. Czym jest światło i kolor?

Światło widzialne to fale elektromagnetyczne. Długość fali mierzymy w **nanometrach (nm)**. Oko człowieka reaguje mniej więcej na zakres **400–700 nm**:

```
400 nm        450         500         550         600         650     700 nm
fiolet ─ niebieski ─ błękitny ─ zielony ─ żółty ─ pomarańczowy ─ czerwony
```

**Kolor nie istnieje w świecie zewnętrznym – powstaje w układzie wzrokowym.** Fala o długości 600 nm nie jest „pomarańczowa" sama z siebie; pomarańczowe jest nasze wrażenie, powstałe z porównania, jak mocno pobudziła różne receptory w oku.

## 2.2. Budowa oka – tylko to, co potrzebne

Światło wpada przez soczewkę i pada na **siatkówkę** (łac. *retina*) – cienką warstwę z tyłu oka, wyłożoną komórkami światłoczułymi, czyli **fotoreceptorami**. Są dwa rodzaje:

| | Czopki (ang. *cones*) | Pręciki (ang. *rods*) |
|---|---|---|
| Do czego służą | Widzenie w jasnym świetle, **rozróżnianie kolorów**, szczegóły | Widzenie w półmroku i w nocy |
| Ile rodzajów | Kilka (różnią się czułością na długość fali) | Jeden |
| Czy dają kolor | **Tak**, przez porównanie sygnałów różnych czopków | **Nie** – jeden sygnał, „jasno/ciemno" |
| Czułość na słabe światło | Mała | Duża |

### Dlaczego trzeba porównywać czopki?

Pojedynczy czopek wysyła tylko jedną liczbę: „jak mocno zostałem pobudzony". Ta liczba zależy zarówno od **koloru** światła, jak i od jego **jasności**. Czopek czuły na 555 nm zareaguje tak samo na słabe światło o długości 555 nm i na mocniejsze światło o długości 600 nm. Z jednego czopka nie da się odgadnąć koloru.

Dopiero **porównanie dwóch lub trzech rodzajów czopków** pozwala wyodrębnić kolor. Przykład: jeśli czopek „długofalowy" reaguje kilka razy mocniej niż „krótkofalowy" – światło jest raczej żółtawo-czerwone; jeśli odwrotnie – raczej niebieskie.

## 2.3. Czułość czopków i „krzywe dzwonowe"

Każdy rodzaj czopka ma swoją **krzywą czułości spektralnej** – mówi, jak mocno czopek reaguje na światło o danej długości fali. Krzywa jest podobna do dzwonu, ze szczytem przy pewnej długości fali, zwanym **maksimum czułości** (ang. *peak*, w kodzie `lmax`).

```
czułość
  1 |       S          L
    |      /‾\        /‾‾\
    |     /   \      /    \
    |    /     \    /      \
  0 |___/_______\__/________\____
      400  450  500  550  600  650  nm
```

Krzywe są szerokie i nakładają się na siebie – światło o 500 nm pobudza jednocześnie czopek S i L, tylko w różnym stopniu.

W projekcie kształt krzywej opisujemy gotowym wzorem (tzw. *nomogram Govardovskiego*): podajesz tylko położenie szczytu, a wzór daje całą krzywą.

## 2.4. Człowiek: trzy rodzaje czopków (trichromat)

Człowiek ma trzy rodzaje czopków:

- **S** (ang. *short*) – krótkie fale, niebiesko-fioletowe (szczyt ok. 420–440 nm),
- **M** (*medium*) – średnie fale, zielone (szczyt ok. 530 nm),
- **L** (*long*) – długie fale, żółto-czerwone (szczyt ok. 560 nm).

Z trzech liczb (sygnały S, M, L) mózg buduje wszystkie kolory, jakie znamy. To dlatego ekrany wystarczy zbudować z **trzech** kolorów (czerwony, zielony, niebieski – RGB): trzy suwaki wystarczą, żeby wywołać dowolne wrażenie barwy u człowieka.

Takie widzenie nazywamy **trichromatycznym** (z gr. „trzy kolory").

## 2.5. Pies i kot: dwa rodzaje czopków (dichromaci)

Psy i koty mają tylko **dwa** rodzaje czopków:

| | Czopek S (krótkie fale) | Czopek L (długie/średnie fale) |
|---|---|---|
| **Pies** | szczyt ok. **429 nm** (fiolet/niebieski) | szczyt ok. **555 nm** (żółto-zielony) |
| **Kot** | szczyt ok. **450 nm** (niebieski) | szczyt ok. **555 nm** |

Takie widzenie to **dichromatyzm** („dwa kolory"). Skutek: mózg dostaje tylko **dwie liczby** zamiast trzech. Wszystkie kolory, które dają te same dwie liczby, wyglądają **identycznie**.

### Konsekwencje dla kolorów

- **Niebieski i żółty** są dla psa rozróżnialne (to główna oś, wzdłuż której widzi różnice).
- **Czerwony, pomarańczowy, żółtozielony i zielony** zlewają się w odcienie żółtawo-oliwkowe. Dla psa czerwona piłka na zielonej trawie nie jest „kontrastowa" kolorystycznie (pies odróżnia je głównie po jasności i zapachu).
- **Szary** pozostaje szary.
- Istnieje jedna długość fali, przy której oba czopki dają taki sam sygnał jak światło białe – ten kolor widzi jako szary. To **punkt neutralny** (ang. *neutral point*). Dla psa: ok. **480–487 nm** (błękit-turkus). Dla człowieka z deuteranopią (rodzaj ślepoty barw) jest to ok. 498 nm.

### Skąd te liczby?

Dane o psie pochodzą z pracy Neitz, Geist i Jacobs (1989), „Color vision in the dog". Dane o kocie są przybliżone i **nie zostały zweryfikowane** z oryginalnymi publikacjami (autorzy projektu przyznają to wprost w kodzie). Dlatego macierz „kota" wyszła bardzo podobna do macierzy „psa".

## 2.6. Analogia z ludzką ślepotą barw

Dichromatyzm występuje też u ludzi – to potoczna „ślepota barw". Osoby z **deuteranopią** nie mają czopków M, więc widzą świat mniej więcej tak, jak pies. Dlatego do projektu wykorzystano **gotowe, znane macierze symulujące deuteranopię** jako punkt odniesienia:

- **V0** – macierz Viénota, Brettela i Mollona (1999),
- **V1** – macierz Machado, Oliveiry i Fernandesa (2009).

Ale oko psa jest nieco inne (inne maksima czułości, inny punkt neutralny), więc projekt wylicza **własną macierz „V2"** na podstawie krzywych czułości psa. To właśnie V2 jest używana w aplikacji; V0 to wariant zapasowy (fallback), V1 służy tylko do kontroli.

## 2.7. Ostrość wzroku (acuity)

Drugi aspekt widzenia to **szczegółowość**. Mierzy się ją jako najmniejszy kąt, pod jakim oko jeszcze rozróżnia dwa osobne punkty (ang. *minimum angle of resolution*, MAR).

- Człowiek z „idealnym wzrokiem" (20/20): MAR = **1 minuta kątowa** (1/60 stopnia).
- Równoważnie: potrafi rozróżnić wzór z **30 cykli na stopień** (cpd – *cycles per degree*; jeden cykl to jeden jasny i jeden ciemny pasek).
- **Pies**: ostrość ok. **20/75**, czyli około **3,75 razy gorsza** niż u człowieka. Rozróżnia wzory do ok. **8 cpd**.
- **Kot**: około **3,5 razy gorsza** niż u człowieka (ok. 8,5 cpd) – ta wartość jest przybliżona.

Intuicyjnie: to, co dla nas jest wyraźnym szczegółem (napis z kilku metrów), dla psa jest rozmytą plamą. Dlatego w aplikacji ostrość symulujemy **rozmyciem** obrazu (patrz [dok. 3](03-podstawy-obrazu-cyfrowego.md), [4](04-matematyka-widzenia-zwierzat.md)).

Uwaga: liczba „20/75" pochodzi z planu projektu, a nie ze źródła sprawdzonego przez autorów (mówi o tym README prototypu).

## 2.8. Widzenie w nocy: pręciki i tapetum lucidum

W oku psa i kota dominują **pręciki** (czopków jest mniej). Dzięki temu zwierzęta lepiej widzą w półmroku. Dodatkowo mają **tapetum lucidum** (łac. „jasna warstwa") – lustrzaną warstwę za siatkówką. Odbija ona światło, które przeszło przez siatkówkę, z powrotem, dając fotoreceptorom „drugą szansę" złapania fotonu. Stąd bierze się też świecenie oczu psów i kotów w świetle latarki.

Skutki w nocy:

- większa czułość na słabe światło,
- obraz **bezbarwny** (pręciki dają jeden sygnał) i mniej ostry.

### Co z tego modelujemy?

**Prawie nic – i to jest uczciwa decyzja.** Autorzy uznali, że:

- telefon nie podaje bezwzględnej jasności sceny (automatyczna ekspozycja „wyrównuje" jasność każdej klatki),
- ilościowe dane o czułości są podawane różnie w różnych źródłach, a autorzy żadnego nie zweryfikowali,
- zatem pełny model pręcikowy byłby „wymyślonymi liczbami udającymi naukę".

Zamiast tego jest **nocny boost** – prosty, *artystyczny* zabieg rozjaśniający cienie (patrz [dok. 4](04-matematyka-widzenia-zwierzat.md)). Aplikacja nie twierdzi, że tak widzi pies w nocy.

## 2.9. Czego aplikacja NIE modeluje

Autorzy wprost wymieniają uproszczenia:

- **Soczewka i ośrodki optyczne oka** (soczewka psa pochłania więcej światła krótkofalowego),
- **ultrafiolet** (część zwierząt go widzi; u psa soczewka blokuje większość UV),
- **migotanie i ruch** (zwierzęta szybciej postrzegają zmiany),
- **pole widzenia i ustawienie oczu** (jedynie dla kota prosty zabieg: szerszy obraz przez zoom, patrz [dok. 7](07-architektura-aplikacji.md)),
- **widzenie pręcikowe** i tapetum,
- **subiektywne wrażenie** – to, jak pies „przeżywa" kolor, jest nieznane.

Walidacja opiera się na znanych ograniczeniach (maksima czopków, punkt neutralny), a nie na „prawdziwym obrazie psa", bo takiego wzorca nie ma.

## 2.10. Podsumowanie

| | Człowiek | Pies | Kot |
|---|---|---|---|
| Rodzaje czopków | 3 (S, M, L) | 2 (S ≈ 429 nm, L ≈ 555 nm) | 2 (S ≈ 450 nm, L ≈ 555 nm) |
| Nazwa | trichromat | dichromat | dichromat |
| Ostrość względem człowieka | 1× | ≈ 3,75× gorsza (≈ 8 cpd) | ≈ 3,5× gorsza (≈ 8,5 cpd) |
| Noc | słaba | lepsza (pręciki, tapetum) | lepsza (pręciki, tapetum) |
| Profil w aplikacji | brak filtra | macierz „V2" (+ zapasowa „V0") | macierz „cat" + dodatki artystyczne |

---

**Nawigacja:** ⬅️ [1. O projekcie](01-o-projekcie.md) · [📚 Spis treści](README.md) · [3. Podstawy obrazu cyfrowego](03-podstawy-obrazu-cyfrowego.md) ➡️
