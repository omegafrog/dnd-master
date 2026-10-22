# Testing and verification guidance

This provider-neutral document stores durable project-specific testing preferences. Exact launch commands and configuration belong in `docs/agents/EXEC.md` and `docs/agents/execution.json`.

## Active guidance

### Wait for adventure startup to finish
- Status: active
- Applies to: D&D Master adventure runtime E2E
- Guidance: When the UI or session status shows `STARTING`, `준비 중`, or says the same request is still running, treat the original request as pending. Keep observing the same session and read its state at 10-second intervals for up to 2 minutes. Do not submit the start request again, create another adventure, or switch to an unrelated existing adventure while it is pending. If it remains pending after 2 minutes, record the session ID, visible state, and diagnostics, then stop without retrying until the failure state is confirmed.
- Rationale: The user explicitly instructed that a session in `STARTING` must be allowed to continue before another action is taken.
- Source: 2026-10-21 user instruction: “starting에서 기다려야된다고” and “테스트 지침에 추가해”.
- Updated: 2026-10-21

### Use headed Chromium and verify the visible login page
- Status: active
- Applies to: browser-based adventure E2E
- Guidance: Use headed Chromium and inspect screenshots when an accessibility snapshot omits route content or login controls. Do not infer that the app is blank from a partial snapshot. Do not use CUA Driver for this workflow. Use Playwright CLI one action at a time for UI setup; inspect agent-backed interactions visually before and after each request, without fixed responses or scripted sequences.
- Rationale: On 2026-10-21, the first incomplete semantic snapshot was mistaken for a blank app even though Chromium showed the login page.
- Source: 2026-10-21 user correction: use Chromium, not CUA; use screenshots/UI for agent-response paths.
- Updated: 2026-10-21

### Stop on a real agent 5xx response
- Status: active
- Applies to: browser-based adventure E2E
- Guidance: Submit one real agent-backed action once. If the UI or network shows 5xx, connection failure, or an explicit failure message, stop the scenario, record the visible state and evidence, and ask the user before another attempt. Keep the browser and server running when requested.
- Rationale: A failed request may have changed persistent session state, so resubmission can duplicate or obscure the failure.
- Source: 2026-10-21 user instruction: stop and ask when a problem occurs; do not close browser or server.
- Updated: 2026-10-21

### Resume a starting session with no map draft
- Status: active
- Applies to: browser-based adventure map preparation
- Guidance: If a session is `STARTING` but the preparation map cannot be loaded, inspect the backend outcome before taking action. When the UI confirms that the map draft is missing, use the visible map-recovery action once; it resumes the same session and adventure ID. If that request fails, stop and ask before another attempt. Never create a second adventure to recover the session.
- Rationale: A failed stage-artifact preparation can persist the session and adventure before the map draft exists. The existing session must resume that same start transaction to finish map preparation.
- Source: 2026-10-21 E2E finding: `preparationMap` threw `NoSuchElementException` when the STARTING session had no prepared map.
- Updated: 2026-10-21

### Check map-needed situation output before retrying a missing map
- Status: active
- Applies to: browser-based adventure E2E where the current story enters a mapped location
- Guidance: After entering the mapped location, verify the current map and grid in the headed browser. If the map is absent, do not submit the player action again. Inspect generated situation diagnostics for `mapNeeded`, `mapDefinitionId`, and the available map count. If a map was available but the required location was generated with `mapNeeded=false`, treat it as a situation-generation failure; correct the generation prompt and regression test, then use a fresh adventure so the situation is generated again. Do not force map activation through an API or database change.
- Rationale: A map-preparation draft can exist while the current situation is mapless; runtime then hides the map and cannot recover the missing situation-to-map binding.
- Source: 2026-10-21 headed E2E: the cellar encounter had one available map but its generated situation had `mapNeeded=false`, so the map-entry gate deactivated it.
- Updated: 2026-10-21

### opentelemetry-local-observability-v2
- Status: active
- Applies to: Spring Boot 로컬 관측성 설치·개발 실행·수집 검증
- Guidance: OpenTelemetry Collector → VictoriaLogs 로그 경로와 Actuator → VictoriaMetrics 메트릭 경로, 공식 MCP 두 개를 사용한다. Vector/Fluent Bit을 설치하지 않는다. setup CLI가 포트·보존 기간·프로젝트/서비스 식별자·실제 Actuator URL을 관리한다. SDK/appender 또는 개발 전용 외부 Java agent로 실제 OTel LogRecord를 export하며 진단 JSON을 Body 문자열에 중첩하지 않는다. Resource와 Attributes를 OTel 대응 필드로 매핑하고 실제 Trace/Span을 유지한다. Collector endpoint와 앱 profile의 설정을 함께 갱신한다. 앱별 실제 수집은 최근 로그 검색과 project/service별 최신 up=1로 확인하며 backend API readiness와 구분한다. production artifact에 개발 agent·registry를 포함하지 않는다. 데이터·queue·검증 출력은 session 내부에 두고 bridge의 host 공개 localhost·내부 service DNS·컨테이너 기준 scrape_url을 구분해 EXEC에 기록한다. Docker Desktop/WSL 앱 접근은 host.docker.internal을 사용하고 포트/path를 유지한다. 원본 metrics_url만 바꾸고 scrape를 방치하지 않는다. 연결 probe는 listener의 실제 기동과 내부 known-response를 먼저 확인한 뒤 방향·bind·접속 주소별로 판정한다. 필수 경로 실패는 nonzero로 종료하고 프로세스 exit=0만으로 연결 성공을 주장하지 않는다. schema 검사만을 위해 E2E를 실행하지 않는다.
- Rationale: 합의한 OTel 수집과 메트릭 경로를 설치·실행·진단에서 동일하게 사용한다.
- Source: 2026-10-22 사용자 인프라 구성도 및 Vector 대신 OpenTelemetry 사용 정정
- Updated: 2026-10-22

### spring-boot-diagnostic-log-v1
- Status: active
- Applies to: Spring Boot 진단 emitter·관련 작업 흐름·로그 수집/조회 변경의 집중 검증
- Guidance: 프로젝트 진단 v1 계약과 diagnostic-log.schema.json을 읽고 emitter가 실제 출력한 원본 JSONL의 필수 필드·타입·catalog별 outcome/duration/error/retry/state를 검사한다. 테스트 fixture만으로 실제 앱 준수를 주장하지 않는다. 활성 Trace/Span 조건, 동일 operation의 재시도/parent/correlation 연결, async·WebSocket context 전달 및 MDC 정리, monotonic duration, 비밀값 redaction과 완결된 작업의 terminal 이벤트를 관련 seam에서 검증한다. 가변 메시지 exact match나 이벤트 severity 고정 assert를 만들지 않는다. schema 검사는 Docker 없이 victorialogs-diagnostic-logs profile로 실행하며 HARNESS_DIAGNOSTIC_LOG_FILE을 원본 파일로 지정한다. VictoriaLogs OTLP query는 접두사 없는 event_name·trace_id·service.name·diag.* 필드를 사용하고 원본 숫자 타입과 저장소 문자열 표현을 구분한다. 지연 비교는 같은 workload/기간/revision·표본 outcome·오류율을 포함한다. 로그 검증만을 위해 E2E를 추가 실행하지 않으며 실패 학습 pending이 실제 테스트 결과를 덮지 않는다.
- Rationale: 형식 오류와 실제 context/동작 오류를 구분하고 근거 없는 오류율·성능 판정을 막는다.
- Source: 2026-10-22 사용자가 제공한 Spring Boot Diagnostic Log Schema v1 및 구현·테스트 지침 반영 요청
- Updated: 2026-10-22

## Superseded guidance

Move replaced entries here with the replacement identifier and date.
