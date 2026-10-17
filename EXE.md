# Build and E2E execution

## PDF extraction provider comparison

The default `hybrid` provider uses positioned PyMuPDF text blocks and Docling table cells, merging them by page and table bounding box. When provider regions overlap ambiguously, a configured AI provider receives only that cropped region and both table candidates to select or correct the cells; the result replaces native cells only when it passes geometry checks and preserves at least as much source text. If this request fails or returns weaker text coverage, PyMuPDF cells remain. If Docling is unavailable or fails, the original PyMuPDF page content is retained and the page receives a warning. Install Docling with its model dependencies using `pip install -e '.[docling]'`. The first Docling conversion downloads model files. `PREPROCESSING_AI_*` settings are supplied to the extraction process by the rule-knowledge service; the selected model must accept image input for ambiguous-region resolution. `PREPROCESSING_PDF_PROVIDER=pymupdf4llm` selects PyMuPDF4LLM Markdown output; `pymupdf` selects the legacy PyMuPDF block provider; and `docling` selects Docling-only extraction. PyMuPDF4LLM is dual-licensed under AGPL-3.0 or Artifex's commercial license; confirm the applicable terms before deploying it as a network service. Compare providers on the same PDF and shared chunking pipeline with:

```bash
.venv/bin/python scripts/compare_pdf_providers.py --pdf /path/to/book.pdf --output /tmp/pdf-provider-comparison
```

The output directory must be new. `comparison.json` reports extraction and downstream processing time, chunk/validation counts, and word overlap. Word overlap is a provider-to-provider difference measure, not an accuracy score against human-verified answers. Docling uses model-based layout and table detection; OCR is disabled for text-based documents, while the existing page-level OCR fallback remains available in the application service. Automatic blank-form exclusion is not yet mapped to the shared page contract.

## PyMuPDF text with vision-extracted tables

일반 문장은 PyMuPDF로 추출한다. 이미지 기반 표 처리는 페이지 안의 각 레이아웃 영역에 점수를 매긴 뒤 선택한다. 점수는 열 정렬 반복 35점, 행 구조 반복 25점, 선·격자 20점, 공간상 행·열로 정렬된 배경색 반복 15점, 표에서 자주 보이는 값의 배치 5점으로 계산한다. 여러 행에서 양쪽 열에 각각 5개 이상의 단어가 반복되는 본문은 20점, 문장 줄바꿈 형태는 15점, 셀 구조가 없는 색상 상자와 장식 도형은 각각 10점을 감점한다. 서로 나란히 있고 같은 행 높이를 공유하는 후보 영역은 한 번의 이미지 추출로 묶으며, 겹친 영역에서 같은 셀 내용이 다시 반환되면 중복 결과로 제외한다.

55점 이상은 해당 영역을 바로 이미지 기반 표 추출로 보낸다. 20~54점은 120 DPI 화면 검사 후 표로 보이거나 판단이 불확실하면 추출한다. 20점 미만도 작은 2×2 표 의심, 반복 색칠 도형, 이미지와 선택 가능한 글자의 겹침, 앞 페이지 표의 연속 가능성, PyMuPDF 표 후보가 있으면 검사한다. 그 밖의 낮은 점수 영역은 10%의 결정 가능한 표본 검사 대상에 포함한다. 최종 추출은 선택된 영역만 108 DPI로 렌더링하고, 반환된 좌표를 원래 PDF 페이지 좌표로 되돌린다. `PREPROCESSING_LLM_TABLES_ENABLED=false`로 모델 호출을 끌 수 있다. AI 제공자 설정이 없으면 PyMuPDF 텍스트를 보존하며 페이지에 `TABLE_EXTRACTION_NOT_CONFIGURED` 경고를 남긴다. 검사나 추출이 실패해도 원문 텍스트는 보존한다.

55점, 20점, 120 DPI 및 10%는 초기 정책값이며, 표 탐지 재현율 98% 이상은 목표이지 측정 결과가 아니다. 사람이 원본과 대조한 여러 룰북 표본으로 점수와 처리량을 검증해야 한다.

## PDF table candidate review

To create a human-review bundle for a PDF without selecting pages by book name, title, or page number, run:

First create a fast PyMuPDF text extraction and candidate review bundle. This first pass does not run Docling:

```bash
.venv/bin/python scripts/review_pdf_table_candidates.py --pdf /path/to/book.pdf --output /tmp/book-table-review
```

Open `review.html`, compare each candidate image with its PyMuPDF text, choose a result for every candidate, then save the downloaded `table-review.json` into the output directory. The first pass writes PyMuPDF text for every page to `merged_extraction.json`. Candidate selection favors recall and can include non-table pages; it does not guarantee that every table is found.

Only after human review, install Docling and run the second pass. It accepts only pages marked `TABLE_PRESENT` and gives no other pages to Docling:

```bash
uv pip install --python .venv/bin/python -e '.[docling]'
.venv/bin/python scripts/review_pdf_table_candidates.py --pdf /path/to/book.pdf --output /tmp/book-table-review --docling-review /tmp/book-table-review/table-review.json
```

The second pass updates `review.html` and `merged_extraction.json` with the selected pages' Docling table cells and the combined text-plus-table output. Pages marked `NO_TABLE`, `UNSURE`, or left blank are not processed by Docling.

- Treat the checkout used for the requested work as the single source for the build, development server, and Playwright test. Before building or launching anything, verify `pwd` and `git rev-parse --show-toplevel` identify the active checkout; record the checkout path and `git rev-parse HEAD` for the run.
- Run `src/start-dev.sh` from that checkout so its backend, frontend, and compiled classes come from the same worktree and revision. Do not use an already-running service merely because its health endpoint responds.
- For a live browser upload test, set `START_DEV_SKIP_LOCAL_RULEBOOK_CATALOG_SEED=true` before starting the launcher. This skips the launcher's API-based shared-catalog bootstrap so only the UI upload triggers the extraction under test. Leave it unset for the normal developer environment.
- Before E2E, identify the listener PID for port 8080 and verify `/proc/<pid>/cwd` resolves to the recorded active checkout or one of its descendants. Also verify the running Java classpath/build output points into that same checkout. For a local UI run, apply the same worktree check to the port 5173 frontend process.
- If a listener belongs to another checkout, stop or otherwise retire that stale local process, restart the approved launcher from the recorded active checkout, wait for health, and repeat the ownership checks. Do not run E2E until the checks pass.
- Launch Playwright from the launcher environment after its backend health and worktree checks pass. Capture the test command, active checkout path, source revision, backend/frontend PIDs, and their resolved working directories with the result so the E2E evidence is tied to the code that was built and run.
- For non-local environments, explicit user-supplied URL overrides remain allowed, but do not present those runs as verification of the local checkout.
- When an E2E run needs rulebook, map, or other source assets, search `~/workspace/dnd-master/docs/assets` first (expand `~` to the current user's home directory) and verify the selected file matches the requested source. Do not assume assets are under the active checkout's `docs/assets` directory.

## Development diagnostics

The optional diagnostic logs use `ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED`. The default is `false`, so diagnostic records are suppressed unless explicitly enabled. Start the local stack with logs enabled using:

```bash
ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED=true src/start-dev.sh
```

Disable them explicitly with `ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED=false src/start-dev.sh`, or leave the variable unset. The flag is read by both the adventure service and rule-knowledge service. Check the launcher output for records beginning `dev_`.

When enabled, the logs include turn IDs and proposed/accepted situation transitions; agent operation, status, failure class and bounded error response; scenario compilation/search counts for source excerpts, dense vector retrieval, selected excerpts, extracted candidates, candidate validation/repair, override application, and final package units; and committed player action/GM narration under `dev_gm_turn_content`. Candidate and evidence identifiers are included to correlate stages. Evidence excerpt text and full prompts are not logged. Error response snippets can contain provider diagnostics, and GM narration may contain player content, so enable this only for local development and turn it off after collection.

Initial situation preparation logs use the `dev_initial_situation_` prefix. They record each stage's required situation IDs, evidence and map counts, prompt length/fingerprint, model request duration, and response shape. Batch failures include expected and returned stage counts/IDs, whether each detail object exists, and the failing materialization stage. They do not record prompt or response bodies.

The same flag is forwarded to the Vite development server. Its browser console records HTTP method, path, outcome, status and elapsed time, with a pending marker every 15 seconds; query strings, headers and bodies are excluded. Backend `dev_flow` records cover map preparation reads and writes, alignment checks, session start stages, runtime binding, the opening GM turn, and session completion. These records log stage names, identifiers, status/failure class and elapsed time, never prompts or response bodies. Enable with `ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED=true src/start-dev.sh` and inspect both the launcher log and browser console.

For parallel worktrees or E2E runs, first choose five currently unused host ports, then export them before starting the launcher: `BACKEND_SERVER_PORT`, `FRONTEND_DEV_PORT`, `POSTGRES_PORT`, `REDIS_PORT`, and `LOCAL_AGENT_CONNECTION_RELAY_PORT`. Record the selected ports with the run. Give each independent run a unique `COMPOSE_PROJECT_NAME`; reuse a project name only when intentionally resuming its saved test database. Do not rely on launcher defaults when another worktree may be running.

## E2E runs that exercise Codex

- When an E2E run is meant to verify the Codex path, start the `user-pc-agent` built from the same active checkout before starting the browser flow. The client must connect to that checkout's relay with the test user's access token and use the Codex CLI installation already logged in on the client machine. Follow [the user PC agent run guide](src/user-pc-agent/README.md); do not substitute a server-side Codex process or a different checkout's client.
- The frontend already lets you select the provider for a session: open **GM 연결 설정** in the adventure session and choose **Codex OAuth** (`codex-cli`), then apply the change. The profile-level **AI 엔드포인트 설정** is a separate setting and does not start or monitor the `user-pc-agent` process. Start that process separately and confirm its connection log before submitting an E2E action that invokes the GM.
- A normal E2E run using Ollama or an OpenAI-compatible endpoint does not exercise the user PC client. Report which endpoint was active and whether the same-checkout client was connected with the E2E result. If the scenario does not select Codex, do not describe that run as verification of the WebSocket-to-local-Codex path.
- Treat the user PC agent WebSocket as interruptible. The client process retries the connection with increasing delays (up to 30 seconds) and logs each failed attempt. After a disconnect, verify the reconnection log before submitting the next GM-backed action. An in-flight request is not replayed automatically because the server may already have applied it; inspect the action result and resubmit only when the session confirms it was not applied.
