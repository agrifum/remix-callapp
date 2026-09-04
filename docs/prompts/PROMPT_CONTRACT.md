# PROMPT_CONTRACT

Każdy finalny prompt implementacyjny zawiera wszystkie poniższe pola, wypełnione lub jawnie oznaczone „nie dotyczy” z uzasadnieniem. Brak istotnego pola, niezweryfikowane API, istotne U lub nieaktualna wersja kanonu wykluczają READY.

- PROMPT ID
- ROLE
- SOURCE FILES TO READ
- OBJECTIVE
- CURRENT STATE
- IN SCOPE
- EXPLICIT NON-GOALS
- RELEVANT PRODUCT REQUIREMENTS
- RELEVANT TECHNICAL EVIDENCE
- ASSUMPTIONS
- UNRESOLVED ISSUES
- FILES / COMPONENTS ALLOWED TO CHANGE
- DEPENDENCY POLICY
- IMPLEMENTATION CONSTRAINTS
- EXPECTED OUTPUT
- ACCEPTANCE CRITERIA
- STOP CONDITIONS
- HANDOFF REQUIREMENTS

PROMPT ID jest unikalny dla rewizji. Powiąż phase, CP, wersję PT, commit bazowy i wpis PROMPT_LEDGER. Prompt referuje do MASTER_SPEC, włącza tylko relewantne R, nie całą specyfikację. Nie kopiuj całej historii ani dokumentacji.

ASSUMPTIONS nie może służyć do samodzielnego zamknięcia kwestii produktu. ACCEPTANCE CRITERIA nie może dodawać nowych funkcji bez R-ID. Ograniczenia techniczne oznacz oddzielnie. Po zmianie kanonu/CP/commitów sprawdź wpływ przed wykonaniem.

Bieżący etap tworzy tylko szablony. Nie ma promptu gotowego do wykonania i nie ma akceptacji uruchamiania implementacji.
