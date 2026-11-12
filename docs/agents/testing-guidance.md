# Testing and verification guidance

This provider-neutral document stores durable project-specific testing preferences. Exact launch commands and configuration belong in `docs/agents/EXEC.md` and `docs/agents/execution.json`.

## Active guidance

### unit-tests-only
- Status: active
- Applies to: 각 개별 플랜의 테스트 작성·실행과 전체 플랜 최종 검증
- Guidance: 각 개별 플랜에서는 단위 테스트만 작성·실행한다. 실제 DB·컨테이너·HTTP/소켓 서버·브라우저·별도 프로세스·실제 모델을 연결하는 통합 테스트는 개별 플랜에 추가하거나 실행하지 않는다. 모든 플랜의 구현과 단위 테스트가 끝난 뒤 #375가 전체 플랜을 묶은 최종 통합 테스트를 1회만 실행한다. 개별 단위 테스트는 가짜 의존성을 사용한다. 최종 통합 시나리오는 docs/plans/combat-movement-spell-range/final-integration-test.md를 따른다.
- Source: 2026-10-26 사용자 “단위 테스트만 플랜에서 작성하도록 하고 통합 테스트는 전체 플랜에서 1회 하도록 해”
- Updated: 2026-10-26

### Wait for adventure startup to finish
- Status: active
- Applies to: D&D Master adventure runtime E2E
- Guidance: When the UI or session status shows `STARTING`, `준비 중`, or says the same request is still running, treat the original request as pending. Keep observing the same session and read its state at 10-second intervals for up to 2 minutes. Do not submit the start request again, create another adventure, or switch to an unrelated existing adventure while it is pending. If it remains pending after 2 minutes, record the session ID, visible state, and diagnostics, then stop without retrying until the failure state is confirmed.
- Rationale: The user explicitly instructed that a session in `STARTING` must be allowed to continue before another action is taken.
- Source: 2026-10-21 user instruction: “starting에서 기다려야된다고” and “테스트 지침에 추가해”.
- Updated: 2026-10-21

### 룰북 처리 완료 전 서버를 종료하지 않기
- Status: active
- Applies to: UI에서 룰북 PDF를 올리는 통합 E2E
- Guidance: 업로드 뒤 해당 룰북이 완료 또는 실패 상태가 될 때까지 같은 실행의 서버와 데이터베이스를 유지한다. 준비 실패로 전처리 중 서버가 종료됐다면 그 데이터베이스와 파일 저장 경로를 다음 업로드 시도에 재사용하지 말고, 원인을 기록한 뒤 새 격리 실행과 새 저장 경로를 준비한다. 이미 실패한 같은 파일 등록을 다시 선택해 재시도하지 않는다. 브라우저 업로드 검증은 [EXE.md](../../EXE.md)의 `START_DEV_SKIP_LOCAL_RULEBOOK_CATALOG_SEED=true` 절차를 따른다.
- Rationale: 2026-10-28 #375 실행에서 전처리 중 서버를 먼저 종료해 등록이 `PREPROCESSING_PROCESS_FAILED`가 됐고, 다음 시도가 같은 데이터베이스의 같은 파일 등록을 재사용해 다시 실패했다. 증거: `E2E-PLAN375-SERVER-FRONTEND-READINESS-20261028-01` 및 `E2E-PLAN375-FAILED-RULEBOOK-REUSE-20261028-02`.
- Source: #375 실패 기록과 실행 로그, [실행 지침](../../EXE.md).
- Updated: 2026-10-28

### 지도 준비를 끝낸 뒤 세션 시작을 확정하기
- Status: active
- Applies to: 지도 초안 검수 뒤 모험 입력을 시작하는 브라우저 E2E
- Guidance: 지도 자르기·격자·벽과 문 초안을 저장한 것만으로 세션 시작이 끝난 것은 아니다. 세션이 `STARTED`로 바뀌고 화면이 런타임으로 이동할 때까지 `맵 준비 완료, 모험 시작` 버튼을 통해 마지막 시작 요청을 완료한다. 세션이 `STARTING`이면 턴 입력을 보내지 말고 세션 준비 화면으로 돌아가 시작 절차를 마친다. 시작 완료가 보이지 않으면 추가 입력을 보내지 말고 상태와 로그를 기록한다.
- Rationale: 2026-10-28 첫 #375 실행에서는 지도를 저장한 뒤 시작 요청을 누락해 `/turns`가 409를 반환했다. 재실행에서는 이 안내와 화면 보호 수정에 따라 시작 버튼을 눌러 `STARTED`와 전투 진입까지 확인했다. 이후 적 차례 AI 전달 실패로 전체 이동 흐름은 미검증이며 재실행 한도도 소진됐다.
- Source: 실패 `PLAN375-03-TURN-409`, `PLAN375-RERUN04-ENEMY-AI-REMOTE-FAILURE`와 실행 로그, [회귀 검사](../../src/web-ui/src/features/adventure-session/AdventureSessionPanel.test.tsx).
- Updated: 2026-10-28

### 장면 서술만으로 지도 필요 여부를 판단하지 않기
- Status: active
- Applies to: 준비된 시나리오에서 지도가 필요한 상황에 진입하는 브라우저 E2E
- Guidance: 룰북·시나리오·지도 파일이 연결됐거나 화면 서술에 지하실·전투 장소가 나와도 활성 지도가 준비됐다고 간주하지 않는다. 런타임의 현재 준비 상황에 저장된 `mapNeeded`(그 상황에 지도가 필요한지 나타내는 값)가 참이고, `dev_flow`의 `situation_map_entry` 기록이 지도를 실제 활성화했는지 확인한다. `/combat-map`의 HTTP 200은 지도 화면이 있다는 보장이 아니며 빈 지도 응답일 수 있다. 지도 필요 값이 거짓이거나 `exploration-groups`가 409를 반환하면 추가 플레이어 입력을 보내지 말고 해당 시나리오 상황 설정과 지도 연결을 조사한다.
- Rationale: #375의 세 번째 E2E에서 준비 지도는 저장됐지만 활성 상황 `beer-cellar`의 `mapNeeded=false`였고 GM도 `mapEntryRequested=false`를 반환했다. 서버는 지도를 비활성화한 뒤 지도 조회에 빈 응답을 반환했고, 탐험 그룹 조회는 활성 지도 부재로 실패했다. 서술상 지하 저장고에 있다는 점만으로 지도 필요 상황이라고 판단한 것이 원인이었다.
- Source: `E2E-PLAN375-20261028-05-MAP-001`, [실패 기록](/home/jiwoo/workspace/dnd-master/.git/harness-workflows/sessions/f64c97035f1cd6d99daf52e066e2c920a21e00a5020848b42017061cb7b224c5/runtime/e2e/plan375-postfix-20261028-05/failure.json), 백엔드 실행 로그 `adventure-backend-only-8499e328-1e77-409b-99f2-a952a21cc45f/2.log`의 848, 868–872, 950, 968–972행.
- Updated: 2026-10-28

### 적 자동 차례의 에이전트 실패 뒤 상태를 확인하기
- Status: active
- Applies to: 사용자 PC 에이전트가 적의 자동 차례를 처리하는 브라우저 E2E
- Guidance: 적 자동 차례 AI 요청이 중계기 `REMOTE_FAILURE` 또는 서버 `DELIVERY_FAILED`로 실패하면 브라우저 입력을 멈추고 요청·작업·전투 식별자와 화면을 기록한다. 브라우저에서 재전송하지 않는다. `CombatAutoProgressionWorker`는 같은 작업을 별도 사용자 입력 없이 재예약하므로 작업 상태와 로그를 확인한다. 현재 소스는 총 5회 실패 뒤 작업을 `FAILED`로 끝내고 요청 잠금을 해제한다. 수정 전 빌드로 실행 중인 서버에는 이 한도가 적용되지 않으므로 서비스 빌드 revision을 확인한다. 중계기 응답만으로 사용자 PC 에이전트 내부 원인을 추정하지 않는다.
- Rationale: 2026-10-28 #375 재실행은 시작 버튼과 전투 진입까지 성공했으나 적 자동 차례의 사용자 PC 에이전트 요청이 `REMOTE_FAILURE`/`DELIVERY_FAILED`를 반환했다. 기존 서버는 작업을 최대 60초 간격으로 계속 재예약했고, 서버·중계기 기록에는 내부 실패 원인이 없었다. 후속 수정은 5회 제한과 요청 해제를 추가했다.
- Source: 실패 `PLAN375-RERUN04-ENEMY-AI-REMOTE-FAILURE`, [실행 기록](/home/jiwoo/workspace/dnd-master/.git/harness-workflows/sessions/f64c97035f1cd6d99daf52e066e2c920a21e00a5020848b42017061cb7b224c5/runtime/e2e/plan375-authorized-rerun-20261028-04/failure.json), [재시도 처리](../../src/adventure-service/src/main/java/com/dndmaster/adventure/application/combat/CombatAutoProgressionWorker.java).
- Updated: 2026-10-28

### Use headed Chromium and verify the visible login page
- Status: active
- Applies to: browser-based adventure E2E
- Guidance: Use headed Chromium and inspect screenshots when an accessibility snapshot omits route content or login controls. Do not infer that the app is blank from a partial snapshot. Do not use CUA Driver for this workflow. Use Playwright CLI one action at a time for UI setup; inspect agent-backed interactions visually before and after each request, without fixed responses or scripted sequences. Treat 404s for optional assets such as `/favicon.ico` as incidental when the requested page and required API calls work; stop on failure of the requested page/API, 5xx, connection failure, or explicit UI error.
- Rationale: On 2026-10-21, the first incomplete semantic snapshot was mistaken for a blank app even though Chromium showed the login page. On 2026-10-25, an optional favicon 404 caused a read-only sheet check to stop although the login UI had loaded.
- Source: 2026-10-21 user correction to use Chromium, not CUA; 2026-10-25 read-only sheet verification and project lesson `E2E-PLAN374-READONLY-11-FAVICON-404`.
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

### playwright-short-socket-directory
- Status: active
- Applies to: Headed Chromium runs launched through Playwright CLI from nested session paths
- Guidance: Before the first browser launch, create a temporary `.e2e-sockets` symlink in the active worktree root pointing to `$HARNESS_SESSION_DIR/tmp`, record it in the artifact guard, and set `PWTEST_SOCKETS_DIR=.e2e-sockets`. An absolute nested session path remains too long after Playwright appends `cli-server`. Keep socket files, browser output, screenshots, logs, and runtime evidence under the session directory; remove the symlink during cleanup and verify no guarded artifacts remain. If the browser cannot open, record the failure and stop without submitting the scenario; a later run requires an explicitly authorized attempt.
- Rationale: The default Playwright daemon socket path exceeded the system path limit in two 2026-10-25 runtime attempts; a short session-local `PWTEST_SOCKETS_DIR` let the daemon start in one of them.
- Source: Project E2E lesson `E2E-PLAYWRIGHT-SHORT-SOCKET-PATH-01`; failed runs `20261025T090949-plan374-final` and `20261025T1022-plan374-single`; successful short-symlink browser launch `20261025T093504-plan374-corrected`.
- Updated: 2026-10-25

### Verify the relay listener before trusting its health response
- Status: active
- Applies to: Local full-stack browser runs that start the user PC agent connection relay
- Guidance: Before launch, read the selected execution profile and confirm every port is either free for this run or serving the expected reused service. After the relay starts, verify that its single listener PID belongs to the active checkout's relay module. Do not treat a successful health URL by itself as proof that this run owns the relay. Confirm the current user PC agent connection is registered before opening the browser. If ownership or registration fails, stop before browser or scenario actions and preserve any unexpected listener.
- Rationale: On 2026-10-25 the relay launched by `start-dev.sh` failed to bind because port 8081 was occupied; an unrelated listener answered the health check, while the agent repeatedly received `ConnectException` and never registered.
- Source: Project E2E lesson `E2E-RELAY-LISTENER-OWNERSHIP-01`; [EXE.md](../../EXE.md), [execution guidance](../../docs/agents/EXEC.md), and [relay startup check](../../src/start-dev.sh).
- Updated: 2026-10-25

## Superseded guidance

Move replaced entries here with the replacement identifier and date.
