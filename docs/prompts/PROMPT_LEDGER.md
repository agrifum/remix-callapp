# PROMPT_LEDGER

Brak finalnych promptów implementacyjnych. Szablony nie są rekordami READY.

| PROMPT ID | PHASE | STATUS | CREATED | SOURCE REQUIREMENTS | TECHNICAL SOURCES | SUPERSEDES | EXECUTION TARGET |
|---|---|---|---|---|---|---|---|

Statusy: DRAFT, READY, EXECUTED, SUPERSEDED, BLOCKED.

Dodatkowe obowiązkowe dane przy pierwszym wpisie: CP-ID/revision, PT-version, base commit, review ID, execution authorization, handoff/evidence link. EXECUTED oznacza podjęte wykonanie, nie automatycznie sukces; wynik określa handoff.

Przejścia: DRAFT→READY po researchu, spójnym CP i jednym PASS; DRAFT/READY→BLOCKED przy luce; każdy nieaktualny prompt→SUPERSEDED z linkiem do następcy. READY→EXECUTED tylko po sprawdzeniu aktualnego ledgeru, plików i uprawnień. Nigdy nie uruchamiaj SUPERSEDED/BLOCKED ani szablonu.

