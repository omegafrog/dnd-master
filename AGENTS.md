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

## 개발용 동작 로그

- 새 기능을 만들거나 기존 동작을 바꿀 때는 시작, 완료, 대체 경로·재시도, 실패 경로에 개발용 로그를 남긴다. 로그만으로 어느 단계까지 실행됐는지와 실패 직전의 입력 크기·개수, 기대값과 실제값, 선택된 분기, 소요 시간, 실패 종류, 다음 복구 동작을 알 수 있게 한다.
- 저장소의 `DevelopmentDiagnostics`와 기존 `dev_` 로그 형식을 사용하고 `ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED`가 켜졌을 때만 상세 로그를 출력한다. 진단을 위해 예외를 삼키거나 실제 동작을 바꾸지 않는다.
- 프롬프트·응답 원문, 인증 정보, 비밀 값, 불필요한 플레이어 입력은 기록하지 않는다. AI 요청·응답은 길이, 지문, 상위 필드, 목록 개수와 식별자처럼 실패를 구분할 수 있는 구조만 기록한다. 스키마·목록 검증 실패에는 기대한 구조와 실제 구조를 함께 남긴다.
- 새 분기나 복구 동작을 추가하면 해당 분기에서 기록하는 진단 정보도 검증한다. 테스트에서는 로그의 주요 식별자·기대/실제 값·결과가 확인되는지 검사한다.
