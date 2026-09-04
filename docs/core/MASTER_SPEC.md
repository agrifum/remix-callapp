# ZLECENIE — Production Specification v1.0

**Data specyfikacji:** 3 września 2026  
**Platforma:** Android  
**Typ dystrybucji:** prywatna / sideload  
**Architektura:** local-first, bez synchronizacji chmurowej  
**Główny cel:** maksymalnie szybkie przekształcenie rozmowy telefonicznej w notatkę, zadanie, klienta lub zlecenie, z minimalną liczbą decyzji podczas rozmowy.

---

# 1. Zasady nadrzędne

1. Aplikacja nie zastępuje dialera ani aplikacji SMS.
2. Chmurka aplikacji pojawia się podczas każdej aktywnej rozmowy telefonicznej.
3. Podstawowym działaniem podczas rozmowy jest zapisanie krótkiej notatki.
4. Numer nie jest klientem, dopóki użytkownik nie oznaczy go jako `Klient`.
5. Klient ma dokładnie:
   - jeden podstawowy numer telefonu,
   - jeden aktualny adres.
6. Klient może mieć wiele zleceń.
7. Klient może mieć więcej niż jedno aktywne zlecenie.
8. Zlecenia tego samego klienta powinny docelowo różnić się terminem.
9. AI:
   - nie podejmuje działań,
   - nie tworzy zleceń,
   - nie wysyła SMS,
   - nie zmienia zatwierdzonych danych,
   - służy wyłącznie do ekstrakcji danych z SMS i aktualizacji krótkiego podsumowania.
10. Wszystkie dane biznesowe są przechowywane lokalnie.
11. Brak Firebase Auth, Firestore, CRM online i synchronizacji między urządzeniami.
12. Funkcje dodatkowe nigdy nie mogą blokować podstawowych funkcji rozmowa → notatka → zlecenie.

---

# 2. Stos technologiczny

## Platforma

- Kotlin
- JVM 17
- `minSdk = 31`
- `compileSdk = 36`
- `targetSdk = 36`

Android 16 odpowiada API 36 i jest obecnym stabilnym SDK; oficjalna konfiguracja Google dla Androida 16 używa `compileSdk` i `targetSdk` 36. citeturn863512search1turn863512search6

## UI głównej aplikacji

- Jetpack Compose
- Material 3
- Navigation 3 stable
- edge-to-edge
- ViewModel
- StateFlow
- unidirectional data flow

Navigation 3 jest obecnie stabilną biblioteką Compose-first; linia stabilna to 1.1.x. citeturn134831search4

## Overlay rozmowy

**Nie używać Compose jako pierwszego wyboru.**

Overlay należy wykonać jako mały natywny Android View/XML obsługiwany przez:

- `WindowManager`
- `TYPE_APPLICATION_OVERLAY`
- `CallOverlayService`

Powód: minimalizacja problemów z `LifecycleOwner`, IME, focus oraz ComposeView działającym bez Activity.

Reszta aplikacji pozostaje w Compose.

## Dane

- Room 3
- KSP
- Kotlin Coroutines
- Flow

Room 3 jest Kotlin-first, KSP-only dla generowania kodu i coroutine-first dla operacji asynchronicznych. Aktualna dokumentacja używa gałęzi 3.0.x. citeturn200933search0turn200933search8

## Dependency Injection

- Hilt

## Preferencje

- Preferences DataStore

## Prace w tle

- WorkManager

## AI

- Firebase AI Logic
- model klasy Gemini Flash-Lite
- structured output `application/json`
- schema wymuszająca typy danych

Firebase AI Logic oficjalnie pozwala wymusić odpowiedź JSON według response schema. citeturn134831search1turn134831search2

## Nie używać

- React Native
- Flutter
- Firebase Firestore
- Firebase Authentication
- Google Calendar REST API
- Google Maps SDK
- Google Routes API w v1
- Navigation SDK
- Retrofit, jeśli nie jest potrzebny poza bibliotekami
- backendu
- agentów AI
- function calling AI

---

# 3. Główna nawigacja aplikacji

Dolny pasek zawiera tylko:

### Połączenia
Filtrowany systemowy rejestr połączeń.

### Zlecenia
Aktywne, zakończone, zamknięte i archiwalne zlecenia.

### Zadania
Notatki oznaczone jako zadania.

Pozostałe funkcje są dostępne z menu:

- Usługi
- Szablony SMS
- Ustawienia
- Kosz

---

# 4. EKRAN — Połączenia

Aplikacja ma dostęp do pełnego systemowego CallLog, ale **nie kopiuje całej historii do Room**.

Na ekranie aplikacji widoczne są tylko połączenia numerów, które spełniają co najmniej jeden warunek:

- numer jest przypisany do klienta,
- numer posiada przynajmniej jedną niezarchiwizowaną lub archiwalną notatkę.

Pozostałe numery są ukryte.

## Element wpisu

- nazwa klienta/kontaktu lub numer,
- numer telefonu,
- data i godzina,
- przychodzące / wychodzące / nieodebrane,
- czas rozmowy,
- oznaczenie `Klient`, jeśli dotyczy,
- ikona notatek,
- `Oddzwoń` dla nieodebranych.

`Oddzwoń` używa `ACTION_DIAL`, nie bezpośredniego `ACTION_CALL`.

Nie wymaga `CALL_PHONE`.

## Kliknięcie wpisu

Jeśli numer jest klientem:

→ Karta klienta.

Jeśli numer posiada tylko notatki:

→ Karta numeru.

---

# 5. EKRAN — Karta numeru

Dla numeru, który nie jest jeszcze klientem.

Zawiera:

- numer,
- nazwę z Android Contacts, jeśli istnieje,
- `Zadzwoń`,
- `SMS`,
- aktywne notatki,
- zadania,
- archiwalne notatki,
- filtrowane połączenia tego numeru,
- `Oznacz jako klient`.

Nie zawiera:

- zleceń,
- analizy SMS,
- AI,
- adresu,
- NIP.

---

# 6. EKRAN — Klient

Klient jest trwałym rekordem.

Zamknięcie ostatniego zlecenia **nie usuwa statusu klienta**.

## Nagłówek

- nazwa klienta,
- numer,
- automatyczne tagi,
- status liczby aktywnych zleceń.

## Szybkie działania

- Zadzwoń
- SMS
- Nawiguj
- Nowe zlecenie

## Dane klienta

Edytowalne:

- Nazwa wyświetlana
- Imię
- Nazwisko
- NIP
- Miasto
- Dzielnica
- Ulica
- Numer budynku
- Numer lokalu
- Kod pocztowy
- Dodatkowe informacje

Telefon jest jeden i pozostaje kluczem klienta.

## Analiza SMS

Pole:

`Analiza SMS: Domyślnie / Włączona / Wyłączona`

Globalne WYŁ. zawsze ma pierwszeństwo.

## Sekcje

### Aktywne zlecenia
Wszystkie aktualnie aktywne.

### Aktywne notatki
Niezarchiwizowane.

### Zadania

### Historia zleceń
Zakończone i zamknięte.

### Historia połączeń

### Archiwalne notatki

Pełna treść SMS **nie jest wyświetlana**.

---

# 7. Automatyczne tagi

Tagi nie są osobną encją.

Są generowane z istniejących danych.

Możliwe tagi:

- miasto,
- dzielnica,
- ulica,
- usługi aktywnych zleceń.

Przykład:

`Warszawa` `Mokotów` `Puławska` `Szerszenie`

DataStore:

`showClientTags = true/false`

Wyłączenie tagów:

- nie kasuje danych,
- tylko ukrywa ich prezentację.

Brak ręcznego tworzenia tagów w v1.

---

# 8. OVERLAY — podstawowy widok podczas rozmowy

Overlay pojawia się dopiero, gdy rozmowa faktycznie trwa (`OFFHOOK`).

Nie musi być widoczny podczas samego dzwonienia.

## Widok domyślny

Największym elementem jest pole notatki.

Schemat:

```text
┌─────────────────────────────┐
│ Jan Kowalski / 501...       │
│                             │
│ Poprzednia notatka...       │
│                             │
│ NOTATKA                     │
│ [_______________________]   │
│                             │
│ [ Klient ]                  │
│                             │
│ [ Zapisz ] [ Do zadań ]     │
└─────────────────────────────┘
```

## Poprzednia notatka

Jeżeli istnieje jedna aktywna:

→ pokazujemy ją skróconą.

Jeżeli istnieje kilka:

→ pokazujemy najnowszą oraz:

`+ N wcześniejszych`

Zarchiwizowanych notatek nigdy nie pokazujemy w overlay.

---

# 9. Overlay — tryb Klient

Po wybraniu `Klient` numer zostaje oznaczony jako kandydat do zapisania jako klient.

Jeżeli Android Contacts zna numer:

→ proponowana jest nazwa kontaktu.

Jeśli nie:

→ automatyczna nazwa:

`Klient 501 234 567`

Nie pytamy podczas rozmowy o:

- imię,
- nazwisko,
- NIP,
- adres.

## Po zaznaczeniu Klient

Pojawia się kompaktowa sekcja:

`Utwórz zlecenie`

Przełącznik jest konieczny, aby rozróżnić:

- zapisanie numeru jako klient,
- stworzenie aktywnego zlecenia.

To jedna świadoma dodatkowa decyzja, która zapobiega przypadkowemu powstawaniu zleceń.

Po aktywowaniu `Utwórz zlecenie` wysuwają się:

### Usługa

Duże przyciski:

`Osy` `Szerszenie` `Krety` `Inne`

Bez dropdown.

### Wstępny dzień

`Dziś` `Jutro` + najbliższe dni tygodnia.

### Wstępna godzina

Prosty picker HH:MM.

Wszystkie trzy pola są opcjonalne dla pierwszego aktywnego zlecenia.

---

# 10. Overlay — istniejący klient

Jeżeli numer jest już klientem:

`Klient ✓`

nie wymaga ponownej klasyfikacji.

Overlay nadal domyślnie pozostaje prosty:

- poprzednia notatka,
- nowa notatka,
- Zapisz,
- Do zadań.

Dostępna jest opcja:

`+ Nowe zlecenie`

Dopiero jej użycie rozwija pola usługi / dnia / godziny.

Nie tworzymy nowego zlecenia przy każdym połączeniu istniejącego klienta.

---

# 11. Autosave overlay

Każda zmiana pola notatki jest automatycznie zapisywana jako `CallDraftEntity`.

Debounce:

około 400–700 ms.

## Jeśli aplikacja lub overlay zostanie ubity

Treść nie ginie.

## Jeśli rozmowa zakończy się bez naciśnięcia Zapisz

Jeżeli istnieje tekst notatki:

→ zostaje automatycznie zachowany jako zwykła notatka.

Nie zostają automatycznie wykonane:

- stworzenie klienta,
- stworzenie zlecenia,
- stworzenie zadania.

Te działania wymagają świadomego zatwierdzenia.

---

# 12. Zapisz vs Do zadań

## Zapisz

Zapisuje notatkę.

Jeżeli zaznaczono `Klient`:

→ zapisuje również klienta.

Jeżeli dodatkowo włączono `Utwórz zlecenie`:

→ tworzy aktywne zlecenie.

## Do zadań

Jedna akcja wykonuje transakcję:

1. zapis notatki,
2. stworzenie TaskEntity powiązanego z tą notatką.

Nie wymagamy dodatkowego checkboxa.

Jeżeli notatka jest pusta:

→ przycisk jest nieaktywny.

---

# 13. EKRAN — Zlecenia

Domyślnie widok:

### Aktywne

Filtry:

- Aktywne
- Zakończone
- Zamknięte
- Archiwalne

Każda karta pokazuje:

- klient,
- tagi,
- usługę,
- termin,
- cenę, jeśli istnieje,
- status,
- oczekującą propozycję AI, jeśli istnieje.

## Szybkie akcje

- Zadzwoń
- SMS
- Nawiguj
- Zakończ

Bez otwierania pełnego formularza.

---

# 14. Wielokrotne zaznaczanie zleceń

Długie przytrzymanie aktywuje selection mode.

Możliwe operacje zależą od wybranych statusów.

### Aktywne
- Zakończ
- Archiwizuj
- Usuń

### Zakończone
- Zamknij
- Archiwizuj
- Usuń

### Zamknięte
- Archiwizuj
- Usuń

### Mieszane statusy
Pokazujemy tylko wspólne bezpieczne działania:

- Archiwizuj
- Usuń

Usuwanie wymaga jednego potwierdzenia.

---

# 15. Kosz

`Usuń` nie kasuje danych fizycznie.

Ustawia:

`deletedAt`

Element trafia do Kosza.

Retention:

30 dni.

Możliwe:

- Przywróć
- Usuń trwale

Po 30 dniach WorkManager może fizycznie usunąć rekord.

---

# 16. EKRAN — Pełne zlecenie

Ten ekran może być znacznie bardziej szczegółowy niż overlay.

## Sekcja klient

- klient,
- telefon,
- adres,
- tagi,
- Zadzwoń,
- SMS,
- Nawiguj.

## Sekcja usługa

- przyciski usług,
- cena.

Wybranie usługi pobiera domyślną cenę.

Cena pozostaje edytowalna.

## Sekcja terminu

- termin wstępny,
- dokładna data,
- dokładna godzina,
- status potwierdzenia.

## Potwierdzenie kalendarza

`Potwierdź termin i dodaj do kalendarza`

Nie tworzymy wydarzenia automatycznie bez zatwierdzenia.

## Informacje dodatkowe

- ręczna notatka,
- dodatkowe informacje,
- krótkie podsumowanie SMS.

## AI

Sekcja widoczna tylko, gdy istnieją propozycje.

Np.:

`Wykryto nowy termin`

Aktualnie:
`04.09 16:00`

Nowa informacja:
`05.09 około 17:00`

`Akceptuj` `Ignoruj`

---

# 17. Statusy zlecenia

Podstawowy enum:

```text
ACTIVE
COMPLETED
CLOSED
```

Archiwizacja jest osobnym Boolean.

Usunięcie jest osobnym `deletedAt`.

## ACTIVE

- bieżące zlecenie,
- AI SMS może działać,
- termin może być modyfikowany,
- podsumowanie SMS może być aktualizowane.

## COMPLETED

Zlecenie wykonane / czas realizacji minął.

AI zostaje natychmiast wyłączone.

Podsumowanie SMS zostaje zamrożone.

## CLOSED

Ręcznie zamknięte zlecenie.

Nie jest już częścią bieżącej pracy.

AI wyłączone.

---

# 18. Automatyczne zakończenie +24 h

WorkManager pilnuje czasu.

Anchor:

1. `confirmedStartAt`, jeśli istnieje,
2. w przeciwnym razie preliminary date + time,
3. jeżeli istnieje tylko data → koniec wybranego dnia,
4. brak daty → brak automatycznego zakończenia.

Warunek:

`now >= anchor + 24 h`

→ `ACTIVE → COMPLETED`

Zmiana terminu:

- anuluje poprzedniego Workera,
- planuje nowego.

Oprócz WorkManager przy każdym uruchomieniu aplikacji wykonywany jest `JobStatusReconciler`, aby naprawić status, jeśli system opóźnił pracę w tle.

---

# 19. Wznawianie zlecenia

Można aktywować zlecenie:

- COMPLETED,
- CLOSED.

Po wznowieniu:

- status → ACTIVE,
- otwiera się nowy `JobAnalysisWindow`,
- SMS-y z okresu pomiędzy zakończeniem a wznowieniem **nie są analizowane**.

Stary termin może pozostać widoczny, ale jeżeli jest już przeszły:

- nie planujemy na nim automatycznego zakończenia,
- oczekujemy nowego terminu lub jego potwierdzenia.

---

# 20. Nowe zlecenie istniejącego klienta

Nowe zlecenie automatycznie kopiuje:

- klienta,
- adres klienta,
- ostatnią usługę,
- ostatnią cenę.

Nie kopiuje:

- terminu,
- SMS summary,
- notatek konkretnego starego zlecenia.

Termin zawsze rozpoczyna się pusty.

---

# 21. Kilka aktywnych zleceń klienta

Dozwolone.

Docelowo powinny różnić się terminem.

Nie tworzymy twardego UNIQUE constraint w bazie, ponieważ:

- terminy mogą chwilowo być puste,
- AI może uzupełnić taki sam termin dla kilku pustych zleceń.

Zamiast tego stosujemy soft validation.

Jeżeli dwa aktywne zlecenia mają identyczny termin:

→ ostrzeżenie:

`Klient ma inne aktywne zlecenie w tym samym terminie.`

Nie blokujemy zapisu danych AI.

Przed finalnym potwierdzeniem terminu/kalendarza wymagamy rozwiązania konfliktu.

---

# 22. EKRAN — Usługi

Lista:

- nazwa,
- domyślna cena,
- aktywna / nieaktywna.

Przykład:

```text
Osy             200 zł
Szerszenie       250 zł
Krety            300 zł
```

## Formularz usługi

- Nazwa
- Domyślna cena
- Aktywna

Usługi użyte historycznie nie są kasowane fizycznie.

Można je dezaktywować.

Zlecenie przechowuje snapshot:

- nazwy usługi,
- ceny.

Zmiana cennika nie zmienia historii.

---

# 23. EKRAN — Zadania

Task powstaje z notatki.

Statusy:

```text
OPEN
DONE
```

Dodatkowo:

- `isArchived`
- `deletedAt`

Karta:

- tekst notatki,
- numer / klient,
- data powstania,
- Zadzwoń,
- Oznacz wykonane.

Nie dodajemy rozbudowanego systemu priorytetów, kategorii ani projektów.

---

# 24. Notatki

Każda notatka zachowuje surową treść.

AI **nigdy nie analizuje notatek**.

Notatka może być:

- aktywna,
- archiwalna,
- w koszu.

## Archiwizacja

`isArchived = true`

Powoduje:

- brak notatki w overlay,
- brak jej jako aktywnej przy numerze,
- pozostawienie w historii.

Odarchiwizowanie:

`isArchived = false`

---

# 25. SMS — zasada prywatności

Aplikacja nie posiada ekranu historii SMS.

Nie przechowuje całej skrzynki SMS.

Nie pokazuje treści SMS użytkownikowi.

SMS jest jedynie chwilowym wejściem do:

- ekstrakcji adresu,
- ekstrakcji terminu,
- wykrycia dodatkowej informacji kontaktowej,
- aktualizacji krótkiego podsumowania.

---

# 26. Globalna analiza SMS

DataStore:

```text
smsAnalysisGlobalEnabled: Boolean
```

Jeżeli false:

- aplikacja nie odczytuje treści SMS dla AI,
- nie wykonuje wywołań modelu,
- ustawienie klienta nie może tego nadpisać.

---

# 27. Analiza SMS per klient

Enum:

```text
INHERIT
ENABLED
DISABLED
```

Effective:

```text
global == false → OFF

global == true + client DISABLED → OFF

global == true + client INHERIT → ON

global == true + client ENABLED → ON
```

Dodatkowy warunek:

klient musi mieć co najmniej jedno `ACTIVE` zlecenie.

---

# 28. Okna analizy SMS

To kluczowy element architektury.

Każdy okres, w którym AI może analizować SMS dla konkretnego zlecenia, jest osobnym rekordem:

`JobAnalysisWindowEntity`

Przykład:

```text
Zlecenie utworzone:
START 03.09 14:10
END   05.09 18:00

Wznowione:
START 10.09 09:20
END   ...
```

SMS z 07.09:

**nigdy nie jest analizowany dla tego zlecenia.**

Nowe zlecenie utworzone 12.09:

nie analizuje żadnych SMS sprzed 12.09.

---

# 29. Trigger SMS

`SMS_RECEIVED` służy wyłącznie jako trigger.

Po otrzymaniu SMS:

1. normalizujemy numer,
2. sprawdzamy, czy istnieje Client,
3. sprawdzamy ACTIVE jobs,
4. sprawdzamy global/client AI setting.

Jeżeli nie:

→ treść nie jest odczytywana dla AI.

Jeżeli tak:

→ Worker analizuje bieżący SMS.

Nie wykonujemy Gemini API bezpośrednio wewnątrz BroadcastReceiver.

---

# 30. AI — wejście

AI otrzymuje tylko:

- treść jednego nowego SMS,
- czas otrzymania,
- lokalną datę/czas,
- timezone,
- aktualny adres klienta lub informację, że jest pusty,
- ID aktywnych zleceń,
- terminy aktywnych zleceń,
- istniejące krótkie podsumowanie każdego aktywnego zlecenia.

Nie otrzymuje:

- pełnej historii SMS,
- historii połączeń,
- notatek,
- innych klientów,
- książki kontaktowej,
- poprzednich zamkniętych zleceń.

---

# 31. AI — structured output

Odpowiedź ma strukturę logicznie równoważną:

```json
{
  "addressCandidate": {
    "city": null,
    "district": null,
    "street": null,
    "buildingNumber": null,
    "unitNumber": null,
    "postalCode": null,
    "confidence": "HIGH"
  },
  "termCandidate": {
    "date": null,
    "time": null,
    "qualifier": "EXACT",
    "confidence": "HIGH"
  },
  "additionalContactInfo": null,
  "jobSummaries": [
    {
      "jobId": "...",
      "updatedSummary": "..."
    }
  ]
}
```

Dozwolone `qualifier`:

- EXACT
- AROUND
- AFTER
- BEFORE
- UNKNOWN

Każde `jobId` musi zostać zweryfikowane przez aplikację.

Nieznane ID jest ignorowane.

---

# 32. AI — adres

## Klient nie ma adresu

Jeżeli:

- adres jest kompletny wystarczająco do użycia,
- confidence = HIGH,

aplikacja może automatycznie uzupełnić brakujące dane klienta.

Pokazuje jedynie małą informację:

`Uzupełniono adres na podstawie SMS.`

## Klient ma już adres

AI **nigdy go nie zmienia**.

Jeżeli wykryto inny:

tworzymy `AiSuggestionEntity`.

UI:

`Wykryto nową informację o adresie.`

`Zmień` / `Ignoruj`

---

# 33. Adres klienta i snapshot zlecenia

Klient ma jeden aktualny adres.

Każde zlecenie przechowuje również snapshot adresu.

Przy utworzeniu:

`Job.addressSnapshot = Client.address`

Jeżeli klient nie miał jeszcze adresu, a zostaje on uzupełniony podczas ACTIVE job:

→ wszystkie aktywne zlecenia z pustym snapshotem otrzymują adres.

Jeżeli użytkownik później zmieni adres klienta:

→ aktualizujemy aktywne zlecenia.

Nie zmieniamy:

- COMPLETED,
- CLOSED.

Dzięki temu historia pozostaje poprawna.

---

# 34. AI — termin

Dla każdego aktywnego zlecenia:

## Brak terminu

AI może automatycznie uzupełnić termin wstępny.

Nie tworzy automatycznie Calendar event.

## Termin już istnieje

AI nie nadpisuje.

Tworzy propozycję:

`Wykryto nową informację o terminie.`

Użytkownik:

`Aktualizuj` / `Ignoruj`

Jeśli zaakceptowana zmiana dotyczy potwierdzonego wydarzenia Calendar:

→ Calendar event jest aktualizowany.

---

# 35. Kilka aktywnych zleceń + SMS

Jeżeli klient ma dwa aktywne zlecenia, a SMS zawiera termin:

- każde zlecenie z pustym terminem może dostać tę wartość,
- zlecenie z istniejącym terminem nie jest zmieniane,
- dla niego powstaje propozycja aktualizacji.

Jeżeli oba były puste:

→ oba mogą chwilowo otrzymać ten sam termin.

Aplikacja później pokaże konflikt do rozwiązania.

---

# 36. AI — podsumowanie SMS

Podsumowanie istnieje **wyłącznie dla aktywnego zlecenia**.

Model otrzymuje:

- obecne krótkie podsumowanie danego Job,
- nową wiadomość.

Zwraca aktualne podsumowanie.

Docelowy limit:

około 300–500 znaków.

Podsumowanie może zawierać np.:

`Klient prosi o wjazd od strony Brzeskiej. Na posesji jest pies. Kontaktować się przed przyjazdem.`

Nie powinno powtarzać:

- adresu,
- terminu,

jeżeli znajdują się już w osobnych polach, chyba że kontekst jest istotny.

Po `COMPLETED` lub `CLOSED`:

podsumowanie jest zamrożone.

---

# 37. AI — dodatkowe dane kontaktowe

Jeżeli SMS zawiera np.:

`Na miejscu będzie Anna, tel. 500...`

nie tworzymy drugiego numeru klienta.

AI może zaproponować dopisanie:

`Kontakt na miejscu: Anna, tel. ...`

do:

`Client.additionalInfo`

Nie zmieniamy podstawowego numeru klienta.

---

# 38. AI — fail closed

Jeżeli:

- brak internetu,
- model zwróci błędny JSON,
- schema validation nie przejdzie,
- model zwróci sprzeczne dane,
- confidence jest niski,

aplikacja:

- niczego nie zmienia,
- nie usuwa danych,
- nie blokuje działania,
- może oznaczyć analizę jako pominiętą.

Retry jest dozwolony tylko, jeśli zlecenie nadal kwalifikuje się do analizy.

Po zakończeniu/zamknięciu:

oczekujące analizy są porzucane.

---

# 39. Ponowny kontakt po zakończeniu zlecenia

Warunki:

- klient nie ma żadnego ACTIVE zlecenia,
- posiada przynajmniej jedno ostatnie COMPLETED/CLOSED,
- występuje nowe przychodzące połączenie lub SMS.

Tworzymy:

`ReengagementEventEntity`

Na liście zleceń / kliencie pojawia się:

`Klient skontaktował się po zakończeniu zlecenia.`

Opcje:

- Wznów zlecenie
- Nowe zlecenie
- Ignoruj

Treść SMS nie musi być analizowana, żeby wykryć sam fakt kontaktu.

---

# 40. Wznów vs Nowe

## Wznów

- stary Job → ACTIVE,
- nowy JobAnalysisWindow startuje teraz,
- zachowujemy stare dane,
- SMS z przerwy jest ignorowany.

## Nowe

Tworzymy nowy Job.

Prefill:

- klient,
- adres,
- usługa,
- cena.

Puste:

- termin,
- podsumowanie SMS.

## Ignoruj

Zamyka alert.

Klient pozostaje zwykłym zapisanym klientem bez aktywnego zlecenia.

---

# 41. SMS button

Każda karta klienta i Job ma:

`SMS`

Użycie:

`ACTION_SENDTO`
`smsto:NUMBER`

Otwieramy domyślną aplikację SMS.

Nie potrzebujemy `SEND_SMS`.

---

# 42. Szablony SMS

Maksymalnie zalecane:

5–8 aktywnych szablonów.

Użytkownik może:

- utworzyć,
- edytować,
- zmienić kolejność,
- wyłączyć,
- usunąć.

Dostępne zmienne:

```text
{name}
{date}
{time}
{service}
{price}
{address}
{arrival_time}
{travel_time}
```

Przykład:

`Dzień dobry, jestem w drodze. Przewidywany przyjazd około {arrival_time}.`

Szablon jedynie przygotowuje treść.

Użytkownik zatwierdza wysłanie w aplikacji SMS.

---

# 43. Nawigacja

Przycisk:

`Nawiguj`

otwiera Google Maps przez Intent:

`google.navigation:q=<address>`

Nie używamy:

- Maps SDK,
- Routes API,
- własnej mapy,
- lokalizacji wewnątrz aplikacji.

---

# 44. ETA z Google Maps

Opcjonalna funkcja v1.

`NotificationListenerService` obserwuje wyłącznie powiadomienia:

`com.google.android.apps.maps`

i próbuje odczytać:

- pozostały czas,
- przewidywaną godzinę przyjazdu,
- dystans.

NotificationListenerService jest oficjalnym Android API do odbierania zdarzeń publikacji/usunięcia powiadomień i wymaga deklaracji `BIND_NOTIFICATION_LISTENER_SERVICE`. citeturn282355search5

Parser ETA jest best-effort.

Nie może być krytyczną zależnością aplikacji.

---

# 45. Manualny fallback ETA

Jeżeli ETA nie zostanie odczytane:

**nie pokazujemy komunikatu o GPS.**

Nie prosimy o location permission.

Pokazujemy małe okno:

### Przewidywana godzina przyjazdu

Picker:

`HH : MM`

np.:

`16 : 37`

Aplikacja oblicza:

```text
travel_time = arrival_time - current_time
```

i udostępnia oba pola szablonom SMS:

- `{arrival_time}`
- `{travel_time}`

---

# 46. Calendar

Używamy Android Calendar Provider.

CalendarContract przechowuje m.in. tytuł, lokalizację, początek i koniec wydarzenia. citeturn200933search4turn200933search6

Nie używamy OAuth ani Google Calendar API.

## Utworzenie

Tylko po ręcznym potwierdzeniu terminu.

## Zmiana terminu

Jeśli istnieje `calendarEventId`:

→ update.

## Usunięcie zlecenia

Przy miękkim usunięciu Job:

→ Calendar event jest usuwany, jeżeli należał do tego Job.

## Czas trwania v1

Stała wartość wewnętrzna:

60 minut.

Nie pokazujemy dodatkowego pola czasu trwania w overlay.

Można to później uczynić konfigurowalne.

---

# 47. Room entities

## ClientEntity

```text
id: String UUID PK
phoneKey: String UNIQUE
phoneDisplay: String

displayName: String
nameSource: AUTO | CONTACT | MANUAL

firstName: String?
lastName: String?
nip: String?

city: String?
district: String?
street: String?
buildingNumber: String?
unitNumber: String?
postalCode: String?

additionalInfo: String?

smsAnalysisMode: INHERIT | ENABLED | DISABLED

createdAt: Long
updatedAt: Long
```

---

## NoteEntity

```text
id: String UUID PK
phoneKey: String

rawText: String

source:
CALL | MANUAL

sourceCallDirection:
INCOMING | OUTGOING | null

sourceCallAt: Long?

isArchived: Boolean
deletedAt: Long?

createdAt: Long
updatedAt: Long
```

Index:

`phoneKey, isArchived, createdAt`

---

## TaskEntity

```text
id: String UUID PK
noteId: String FK

status: OPEN | DONE
isArchived: Boolean
deletedAt: Long?

createdAt: Long
completedAt: Long?
```

---

## ServiceEntity

```text
id: String UUID PK

name: String
defaultPriceMinor: Long?

isActive: Boolean
sortOrder: Int

createdAt: Long
updatedAt: Long
```

Cena zapisywana w groszach.

---

## JobEntity

```text
id: String UUID PK
clientId: String FK

serviceId: String?
serviceNameSnapshot: String?
priceMinor: Long?

preliminaryDateEpochDay: Long?
preliminaryTimeMinute: Int?
preliminaryTimeQualifier:
EXACT | AROUND | AFTER | BEFORE | UNKNOWN

confirmedStartAt: Long?

addressCitySnapshot: String?
addressDistrictSnapshot: String?
addressStreetSnapshot: String?
addressBuildingSnapshot: String?
addressUnitSnapshot: String?
addressPostalCodeSnapshot: String?

manualNotes: String?
additionalInfo: String?
smsSummary: String?

status:
ACTIVE | COMPLETED | CLOSED

isArchived: Boolean
deletedAt: Long?

calendarEventId: Long?

predictedArrivalAt: Long?
etaSource:
MAPS_NOTIFICATION | MANUAL | null
etaUpdatedAt: Long?

createdAt: Long
updatedAt: Long
completedAt: Long?
closedAt: Long?
reopenedAt: Long?
```

Indexes:

- `clientId`
- `status`
- `isArchived`
- `deletedAt`
- `confirmedStartAt`

---

## JobAnalysisWindowEntity

```text
id: String UUID PK
jobId: String FK

startedAt: Long
endedAt: Long?

reason:
CREATED | REOPENED

lastAnalyzedSmsAt: Long?
```

Dla ACTIVE Job:

dokładnie jedno otwarte okno.

---

## AiSuggestionEntity

```text
id: String UUID PK

clientId: String
targetJobId: String?

type:
ADDRESS_CHANGE
TERM_CHANGE
ADDITIONAL_CONTACT_INFO

proposedValueJson: String

sourceSmsAt: Long

status:
PENDING
ACCEPTED
IGNORED

createdAt: Long
resolvedAt: Long?
```

---

## SmsTriggerEntity

Nie przechowuje treści SMS.

```text
id: String UUID PK

clientId: String
senderPhoneKey: String
receivedAt: Long

state:
PENDING | PROCESSED | DISCARDED | FAILED

attemptCount: Int
createdAt: Long
```

Worker odczytuje konkretną wiadomość z systemowego SMS Provider tylko wtedy, gdy analiza nadal jest dozwolona.

---

## ReengagementEventEntity

```text
id: String UUID PK

clientId: String
jobId: String

source:
INCOMING_CALL | INCOMING_SMS

occurredAt: Long

status:
PENDING | RESUMED | NEW_JOB | IGNORED
```

Maksymalnie jeden `PENDING` na klienta.

---

## SmsTemplateEntity

```text
id: String UUID PK

name: String
body: String

isActive: Boolean
sortOrder: Int

createdAt: Long
updatedAt: Long
```

---

## CallDraftEntity

```text
callSessionId: String PK

phoneKey: String

noteText: String

markAsClient: Boolean
createJob: Boolean

serviceId: String?
preliminaryDateEpochDay: Long?
preliminaryTimeMinute: Int?

taskRequested: Boolean

createdAt: Long
updatedAt: Long
```

Po prawidłowym commit:

rekord jest usuwany.

---

# 48. Preferencje DataStore

```text
smsAnalysisGlobalEnabled: Boolean = true
showClientTags: Boolean = true

preferredCalendarId: Long?

mapsEtaParsingEnabled: Boolean = true

onboardingCompleted: Boolean
```

Nie przechowujemy w DataStore danych biznesowych.

---

# 49. System telefonii

Podstawowe komponenty:

```text
CallScreeningService
CallStateMonitor
CallOverlayService
PhoneNumberNormalizer
CallLogRepository
ContactLookupRepository
```

## CallScreeningService

Służy do uzyskania numeru i kierunku połączenia.

Incoming call musi zostać natychmiast przepuszczony.

Nie wykonujemy tutaj:

- Room queries blokujących thread,
- AI,
- sieci,
- ciężkich operacji.

Android wymaga odpowiedzi dla incoming call w ciągu 5 sekund. citeturn134831search0

## TelephonyCallback

Służy do:

- RINGING
- OFFHOOK
- IDLE

`CallStateListener` jest dostępny od API 31 i wymaga `READ_PHONE_STATE`. citeturn282355search6

## OFFHOOK

→ uruchom overlay.

## IDLE

→ zakończ overlay.

---

# 50. Overlay foreground service

Podczas aktywnej rozmowy uruchamiamy krótko żyjący Foreground Service.

Android 14+ wymaga zadeklarowania typu FGS; dla niestandardowego przypadku właściwym typem jest `specialUse`, wraz z `FOREGROUND_SERVICE_SPECIAL_USE` i opisem subtype w manifeście. citeturn282355search0turn282355search1

Service żyje tylko podczas rozmowy.

Nie jest całodobowym service.

---

# 51. Uprawnienia — wymagane

## Core telefonii

```text
READ_PHONE_STATE
READ_CALL_LOG
```

## Kontakty

```text
READ_CONTACTS
```

## Overlay

```text
SYSTEM_ALERT_WINDOW
```

## Foreground service

```text
FOREGROUND_SERVICE
FOREGROUND_SERVICE_SPECIAL_USE
```

## Powiadomienia

```text
POST_NOTIFICATIONS
```

## Kalendarz

```text
READ_CALENDAR
WRITE_CALENDAR
```

## SMS AI

```text
READ_SMS
RECEIVE_SMS
```

`READ_SMS` jest obecnie hard-restricted permission i musi zostać allowlistowane przez instalator. Ponieważ aplikacja jest prywatna, konfiguracja instalacji może zostać wykonana poza ograniczeniami Google Play, ale rdzeń aplikacji musi nadal działać bez SMS AI, jeśli permission nie jest dostępne. citeturn200933search1turn200933search3

## AI

```text
INTERNET
```

---

# 52. Uprawnienia — NIE wymagane

Nie deklarować:

```text
RECORD_AUDIO
CALL_PHONE
SEND_SMS
ACCESS_FINE_LOCATION
ACCESS_COARSE_LOCATION
WRITE_CONTACTS
QUERY_ALL_PACKAGES
```

Nie ma dyktowania głosowego.

Nie wykonujemy własnej lokalizacji.

Nie wysyłamy SMS automatycznie.

---

# 53. Role i specjalne dostępy

## ROLE_CALL_SCREENING

Jednorazowo przy konfiguracji.

CallScreeningService może być wybrany przez użytkownika jako aplikacja call-screening bez przejmowania roli domyślnego dialera. citeturn134831search0

## Draw over other apps

Sprawdzamy `Settings.canDrawOverlays()`.

## Notification access

Opcjonalny.

Potrzebny wyłącznie do automatycznego ETA.

Brak dostępu:

→ manualny picker godziny przyjazdu.

Nie pokazujemy uporczywych komunikatów.

---

# 54. Onboarding

Nie bombardować użytkownika wszystkimi permission dialogs jednocześnie.

## Etap podstawowy

- Call screening role
- READ_PHONE_STATE
- overlay
- notifications
- READ_CALL_LOG
- READ_CONTACTS

## Gdy pierwszy raz używany jest Calendar

→ Calendar permissions.

## Gdy użytkownik włączy SMS AI

→ READ/RECEIVE SMS.

## Gdy włączy automatyczne ETA

→ Notification access.

Każdy moduł ma działać fail-safe.

---

# 55. Phone number normalization

Każdy numer przechodzi przez `PhoneNumberNormalizer`.

Przykłady:

```text
+48 501 234 567
0048 501234567
501234567
```

muszą wskazywać ten sam `phoneKey`.

Polski numer bez prefiksu:

→ normalizacja z `+48`.

Nie używać numeru wyświetlanego jako primary key.

---

# 56. Transakcje krytyczne

Następujące akcje wykonywać w jednej Room transaction:

### Save overlay

- Note
- Client, jeśli zaznaczony
- Job, jeśli createJob
- JobAnalysisWindow
- Task, jeśli Do zadań
- usunięcie CallDraft

### Accept AI address

- Client address
- update pustych ACTIVE job snapshots
- AiSuggestion accepted

### Accept AI term

- Job term
- Calendar update, jeśli dotyczy
- reschedule AutoCompleteWorker
- AiSuggestion accepted

---

# 57. Główne przepływy

## FLOW A — zwykła rozmowa

```text
OFFHOOK
→ overlay
→ użytkownik wpisuje notatkę
→ autosave draft
→ Zapisz
→ NoteEntity
→ IDLE
→ overlay znika
```

---

## FLOW B — notatka jako zadanie

```text
rozmowa
→ notatka
→ Do zadań
→ NoteEntity
→ TaskEntity OPEN
```

---

## FLOW C — nowy klient

```text
rozmowa
→ notatka
→ Klient
→ nazwa z Contacts lub auto
→ Zapisz
→ ClientEntity
→ NoteEntity
```

Brak aktywnego Job, jeśli `Utwórz zlecenie` nie zostało włączone.

---

## FLOW D — nowe zlecenie podczas rozmowy

```text
Klient
→ Utwórz zlecenie
→ opcjonalna usługa
→ opcjonalny dzień
→ opcjonalna godzina
→ Zapisz
→ Client
→ Note
→ Job ACTIVE
→ JobAnalysisWindow START
```

---

## FLOW E — incoming SMS aktywnego klienta

```text
SMS_RECEIVED
→ normalize sender
→ Client?
→ ACTIVE job?
→ global AI enabled?
→ client AI enabled?
→ TAK
→ SmsTrigger
→ Worker
→ read one SMS
→ Gemini structured extraction
→ validate JSON
→ apply safe updates / suggestions
```

---

## FLOW F — incoming SMS bez aktywnego Job

```text
SMS_RECEIVED
→ Client
→ no ACTIVE job
→ AI NIE CZYTA TREŚCI
→ jeśli istnieje completed/closed job:
   ReengagementEvent
```

---

## FLOW G — AI wykrywa pusty termin

```text
SMS
→ term HIGH confidence
→ Job.term empty
→ wypełnij preliminary term
→ nie twórz Calendar
→ pokaż małą informację
```

---

## FLOW H — AI wykrywa nowy termin

```text
SMS
→ Job already has term
→ AiSuggestion
→ badge na Job
→ użytkownik Akceptuj
→ update term
→ update Calendar
→ reschedule +24h worker
```

---

## FLOW I — automatyczne zakończenie

```text
scheduled time +24h
→ AutoCompleteWorker
→ ACTIVE?
→ tak
→ COMPLETED
→ close JobAnalysisWindow
→ freeze summary
```

---

## FLOW J — klient kontaktuje się później

```text
incoming call/SMS
→ client has no ACTIVE jobs
→ recent COMPLETED/CLOSED exists
→ ReengagementEvent
→ UI:
   Wznów / Nowe / Ignoruj
```

---

## FLOW K — nowe zlecenie po poprzednim

```text
Nowe zlecenie
→ copy:
   client
   address
   service
   price
→ term EMPTY
→ summary EMPTY
→ ACTIVE
→ new JobAnalysisWindow
```

---

## FLOW L — Nawiguj

```text
Nawiguj
→ Google Maps Intent
→ navigation starts
→ notification parser
→ ETA found?
   TAK → store temporary ETA
   NIE → small HH:MM picker
→ SMS template can use ETA
```

---

# 58. Stabilność

Aplikacja musi działać poprawnie, gdy:

- brak internetu,
- AI niedostępne,
- READ_SMS niedostępne,
- Calendar permission odmówione,
- Notification Listener wyłączony,
- Google Maps zmieni format powiadomienia,
- proces aplikacji zostanie zabity,
- overlay zostanie odtworzony,
- telefon zostanie zrestartowany.

Żaden z powyższych przypadków nie może uniemożliwić:

- zapisu notatki,
- utworzenia klienta,
- utworzenia zlecenia,
- korzystania z historii.

---

# 59. Prywatność i zakres wysyłania danych do AI

Do Firebase AI Logic wysyłamy tylko minimalne dane potrzebne do ekstrakcji.

Nigdy:

- całej książki kontaktowej,
- całej historii SMS,
- całego rejestru połączeń,
- wszystkich klientów,
- notatek rozmów.

Raw SMS nie jest zapisywany w Room.

W bazie pozostają jedynie:

- wyekstrahowane pola,
- krótkie summary,
- propozycje AI,
- metadata triggera.

---

# 60. UI / UX

## Material 3

- spokojny, funkcjonalny design,
- jasny/dark mode,
- duże touch targets,
- minimum dekoracji,
- maksymalna czytelność.

## Overlay

Priorytet:

**jedna ręka + rozmowa w toku.**

Dlatego:

- żadnych dropdownów,
- żadnych modalnych formularzy wieloetapowych,
- żadnego wpisywania NIP/adresu podczas rozmowy,
- duże chips/buttons,
- najważniejsze opcje dostępne bez scrollowania.

## Pełna aplikacja

Może być bardziej szczegółowa.

---

# 61. Pozycja overlay

Domyślnie:

górna część ekranu, poniżej systemowego status bara.

Nie zakrywamy głównych przycisków dialera:

- mute,
- speaker,
- end call.

Overlay powinien mieścić się maksymalnie w około 70% wysokości ekranu po rozwinięciu.

---

# 62. Focus i klawiatura overlay

Panel domyślnie nie powinien przejmować focus dialera.

Po tapnięciu pola Notatka:

- WindowManager aktualizuje flagi,
- EditText otrzymuje focus,
- pokazuje się IME.

Po zapisaniu/wyjściu:

→ overlay wraca do non-focusable.

To trzeba testować szczególnie na HyperOS/Xiaomi.

---

# 63. Package structure

```text
app/
├── core/
│   ├── model/
│   ├── util/
│   ├── phone/
│   └── time/
│
├── data/
│   ├── database/
│   ├── entity/
│   ├── dao/
│   ├── repository/
│   └── preferences/
│
├── system/
│   ├── calls/
│   ├── overlay/
│   ├── contacts/
│   ├── sms/
│   ├── calendar/
│   └── navigation/
│
├── ai/
│   ├── model/
│   ├── schema/
│   ├── validator/
│   └── SmsExtractionEngine
│
├── feature/
│   ├── calls/
│   ├── numberdetail/
│   ├── client/
│   ├── jobs/
│   ├── jobdetail/
│   ├── tasks/
│   ├── services/
│   ├── templates/
│   ├── trash/
│   └── settings/
│
└── workers/
    ├── AutoCompleteJobWorker
    ├── SmsAnalysisWorker
    └── TrashCleanupWorker
```

---

# 64. Interfejs AI jako wymienna warstwa

```text
interface SmsExtractionEngine
```

Implementacje:

```text
FakeSmsExtractionEngine
FirebaseSmsExtractionEngine
```

Google AI Studio ma najpierw wygenerować i przetestować aplikację z Fake.

Firebase implementacja zostaje podłączona dopiero po przeniesieniu projektu do Android Studio.

Dzięki temu AI nie jest zależnością podstawowej aplikacji.

---

# 65. Testy obowiązkowe

## Call handling

- incoming answered,
- incoming rejected,
- outgoing answered,
- outgoing unanswered,
- rapid call end,
- unknown number,
- contact number.

## Overlay

- autosave,
- process killed,
- IME,
- rotation/window change,
- screen lock/unlock,
- multiple active notes.

## Client

- create from number,
- Contacts name,
- manual edit,
- one number only,
- address update.

## Jobs

- first job,
- multiple jobs,
- blank preliminary term,
- duplicate term warning,
- active → completed,
- active → closed,
- reopen,
- new based on previous.

## SMS AI

- global OFF,
- client OFF,
- no ACTIVE jobs,
- one ACTIVE,
- multiple ACTIVE,
- SMS before job,
- SMS between jobs,
- SMS after completion,
- resume with new analysis window,
- invalid JSON,
- no network.

## AI field protection

- empty address,
- filled address,
- same address,
- new address,
- empty term,
- same term,
- changed term.

## Calendar

- create,
- update,
- delete,
- permission denied.

## Navigation

- Maps installed,
- ETA parsed,
- parser fails,
- notification access denied,
- manual arrival HH:MM.

## Bulk operations

- complete multiple,
- archive multiple,
- delete multiple,
- restore from trash.

---

# 66. Definition of Done v1

Aplikacja v1 jest gotowa dopiero, gdy na fizycznym urządzeniu:

1. wykrywa przychodzące i wychodzące rozmowy,
2. pokazuje overlay po rozpoczęciu rozmowy,
3. zapisuje notatkę mimo nagłego zakończenia rozmowy,
4. pozwala stworzyć klienta,
5. pozwala stworzyć zlecenie,
6. filtruje CallLog zgodnie z wymaganiami,
7. poprawnie archiwizuje notatki,
8. obsługuje zadania,
9. obsługuje wiele zleceń klienta,
10. automatycznie kończy zlecenia +24 h,
11. wykrywa ponowny kontakt,
12. przestrzega okien analizy SMS,
13. AI nigdy nie nadpisuje istniejących zatwierdzonych danych,
14. Calendar działa w create/update/delete,
15. Nawiguj otwiera Google Maps,
16. ETA ma działający manualny fallback,
17. szablony SMS działają,
18. podstawowa aplikacja działa bez AI i bez internetu,
19. żaden brak opcjonalnego permission nie powoduje crasha,
20. build release APK przechodzi wszystkie testy krytyczne.

---

# 67. Funkcje świadomie poza v1

- WhatsApp
- dyktowanie głosowe
- synchronizacja cloud
- wielu użytkowników
- wiele telefonów klienta
- wiele aktualnych adresów klienta
- ręczne tagi
- podgląd SMS w aplikacji
- automatyczne wysyłanie SMS
- nagrywanie rozmów
- Maps SDK
- Google Routes API
- Navigation SDK
- własny CRM webowy
- fakturowanie
- zdjęcia/załączniki
- statystyki biznesowe

---

# 68. Zasada dalszego developmentu

Każdy kolejny moduł musi spełniać warunek:

**jego awaria nie może zaburzać podstawowego przepływu:**

```text
ROZMOWA
→ NOTATKA
→ KLIENT
→ ZLECENIE
```

AI, SMS, Calendar, ETA i nawigacja są adapterami wokół tego rdzenia, a nie jego fundamentem.