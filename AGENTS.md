## graphify

## Domain language and user-facing terminology

- Do not invent English labels, abbreviations, or unexplained domain terms in user-facing responses, plans, tests, API contracts, or new documentation.
- Use the existing vocabulary in `CONTEXT.md` first. When no agreed term exists, write the meaning out in plain Korean and keep that wording until the term is explicitly agreed.
- Never use `free-form attack`, `자유형 공격`, `materialized AC`, or `stat block` as unexplained terms. Use descriptive wording such as `플레이어가 자연어로 입력한 전투 행동`, `룰북에서 확인해 저장한 대상의 방어도`, and `룰북에서 확인한 몬스터 전투 수치(방어도·HP·공격 보정치 등)`.
- Code may retain an existing external identifier for compatibility, but explanations and newly introduced names must use the plain-language meaning. If an existing identifier must be mentioned, explain it immediately in Korean.
- Before adding a new domain term, check `CONTEXT.md`, describe the concept in plain Korean, and record the agreed term there only after it is settled.

Build and E2E checkout verification details are in [EXE.md](EXE.md).

Build and E2E checkout verification details are in [EXE.md](EXE.md).

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
