# HANDOFF_PROTOCOL

ENV-2: poniższy ogólny szablon jest do użycia przez zamknięty projekt. Zewnętrzny opiekun wyłącznie utrzymuje środowisko; nie podejmuje decyzji aplikacyjnych ani nie uruchamia prac. Nadrzędne: control/ENVIRONMENT_BOUNDARY.md (po przeniesieniu do repo: docs/control/ENVIRONMENT_BOUNDARY.md). Identyfikatory wymagań i bramki wypełnia projekt, nie opiekun.

Każde przekazanie zawiera: handoff ID; rolę nadawcy i odbiorcy; phase/CP/prompt ID; wersję PT; commit bazowy i końcowy (gdy istnieją); status COMPLETE/BLOCKED/PARTIAL; wykonany zakres; zmienione pliki; kryteria odbioru i dowody; materialne odkrycia; ograniczenia; proponowane aktualizacje kanonu; nierozstrzygnięte kwestie; następny dozwolony krok.

Odkrycia techniczne trafiają do evidence/harness i researchu, nie bezpośrednio do MASTER_SPEC. Użytkownik rozstrzyga zmiany produktu. Nie przekazuj sekretów ani pełnych logów; wskaż niezbędny fragment i bezpieczny plik dowodowy.

Agent zatrzymuje się po wykonaniu fazy albo napotkaniu blokera: brak istotnego kontekstu, sprzeczność kanonu, konieczność wyjścia poza allowlistę plików/uprawnień, brak źródła API, zmieniony stan repo. Nie rozpoczyna następnej fazy sam.

