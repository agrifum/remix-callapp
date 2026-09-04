# CONTEXT_POLICY

ENV-2: poniższy ogólny szablon jest do użycia przez zamknięty projekt. Zewnętrzny opiekun wyłącznie utrzymuje środowisko; nie podejmuje decyzji aplikacyjnych ani nie uruchamia prac. Nadrzędne: control/ENVIRONMENT_BOUNDARY.md (po przeniesieniu do repo: docs/control/ENVIRONMENT_BOUNDARY.md). Identyfikatory wymagań i bramki wypełnia projekt, nie opiekun.

Context pack jest krótkim, samowystarczalnym wyciągiem dla jednej fazy, nie alternatywną specyfikacją.

Obowiązkowe pola: objective; relevant requirements (R-ID + dokładne potrzebne zachowanie); relevant decisions (aktywne D-ID); known constraints; technical references (T-ID, URL, data weryfikacji); files/components involved; non-goals; unresolved issues relevant to phase. Dodaj CP-ID, phase ID, wersję kanonu, stan repo/commit, stop condition i listę plików do przeczytania.

Orchestrator sprawdza zależności także poza wybranym ekranem: zapis danych, uprawnienia, wyłączenie AI i skutki zmiany decyzji. Agent dostaje niezbędne fragmenty wraz z locatorami; samo polecenie „przeczytaj wszystko” jest niedopuszczalne.

Zalecany budżet redakcyjny: CP około 1–2 stron, reguły trwałe krótko, źródła jako istotne cytaty/parafrazy i linki. To heurystyka, nie obietnica limitu modelu. Jeśli brakuje koniecznego kontekstu, zawęź fazę lub dodaj uzasadniony fragment zamiast ścinać istotne wymaganie.

Nie kopiuj całej historii, pełnych logów, raportów researchowych i nieaktywnych decyzji. Stary czat jest dostępny wyłącznie dla provenance lub badania błędnej ekstrakcji. Duże logi pozostają w harness; handoff podaje selektywny dowód i lokalizator.

Przed wykonaniem: CP musi odpowiadać bieżącej wersji MASTER_SPEC i commitowi repo. Zmiana jednego z nich uruchamia ocenę wpływu; zależny CP/prompt traci READY. Załączone projekty/pamięć nie zapewniają automatycznie pełnego dostępu do wszystkich plików.

