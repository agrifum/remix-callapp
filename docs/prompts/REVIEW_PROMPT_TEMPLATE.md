# REVIEW_PROMPT_TEMPLATE

ROLE: Prompt Reviewer. INPUT: <one draft prompt ID and revision, CP, required canonical excerpts, ledger, technical evidence>.

Wykonaj dokładnie jeden krótki audyt przed wysłaniem agentowi. Nie implementuj, nie testuj aplikacji, nie twórz pętli kolejnych recenzentów.

Sprawdź: jednoznaczny cel; zgodność z MASTER_SPEC; aktywne decyzje; brak funkcji spoza zakresu; wystarczającą i aktualną dokumentację; jawne non-goals; mały zakres; jasny stop; brak zbędnego kontekstu; brak niesprawdzonych API/wersji. Sprawdź też wymagane pola kontraktu, stan READY zależności, blokujące U oraz zgodność CP/PT/commit.

OUTPUT: review ID; prompt ID/revision; PASS albo BLOCKED; maksymalnie krótka lista konkretnych błędów z locatorem i poprawką; data; wersje źródeł. Nie dodawaj wymagań produktu. PASS pozwala Orchestratorowi ustawić READY dopiero po spełnieniu wszystkich bramek, sam nie uruchamia agenta.

Popraw wyłącznie wykryte problemy i potwierdź ich usunięcie; nie uruchamiaj ponownie całego audytu. Materialna zmiana zakresu oznacza nowy prompt, nie kolejną pętlę tego samego review.

