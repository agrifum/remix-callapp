# RESEARCH_PROMPT_TEMPLATE

STATUS: TEMPLATE. ROLE: Technical Researcher.

PROMPT ID / PHASE / CP / PT: <identifiers>
SOURCE FILES TO READ: <relevant canonical requirements, U, source policy, source registry>
TECHNICAL QUESTION: <one precise uncertainty and decision it supports>
PRODUCT BOUNDARY: <R-ID; immutable WHAT; explicit non-goals>
CURRENT STATE: <verified repository/runtime details, or unknown>

Research current official documentation at execution time. Prefer primary documentation, then official source/repository, Context7 for library documentation, and justified secondary sources only for remaining gaps. Verify product/API version and applicable release channel; record checked-at dates. Do not equate newest with compatible. Do not copy versions from model memory.

Return a compact table: claim ID | documented fact / inference / unknown | concise finding | URL/title/publisher | applicable version/channel | checked at | confidence + reason | limitations/contradictions | consequence for next prompt.

Identify restrictions, failure conditions and open questions. Explicitly state what remains unverified. Technical reality may block or require a product decision, but cannot change product requirements. Do not implement code, install tooling, configure IDE, run experiments or tests in this research task.

Stop once the stated question has authoritative evidence or a clearly bounded unknown. Do not duplicate evidence with additional tools without a concrete gap. Handoff: findings, sources, scope impact and relevant U IDs.

