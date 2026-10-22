# 로컬 개발 서버와 E2E 실행

## Adventure Runtime 집중 단위 테스트

Run the #367 movement-coordinator, map-image source, exploration-cycle rules and start preconditions, environment-planning delegation, situation-evidence validation, player-roll transition commit gate, Codex MCP situation-preflight, and AI relay reconnect-retry tests from the active checkout with the Gradle wrapper. These tests use in-memory dependencies and do not require PostgreSQL, Redis, or application services:

```bash
./src/gradlew -p src :adventure-service:test --tests com.dndmaster.adventure.MapMovementCoordinatorTest --tests com.dndmaster.adventure.api.PreparationMapImageSourceTest --tests com.dndmaster.adventure.ExplorationCycleTest --tests com.dndmaster.adventure.api.ExplorationEnvironmentControllerTest --tests com.dndmaster.adventure.api.AdventureMovementPreviewBoundaryTest --tests com.dndmaster.adventure.BestOfNTurnPlanningTest --tests com.dndmaster.adventure.SituationProposalGroundingPolicyTest --tests com.dndmaster.adventure.application.scenario.preparation.ScenarioPackageStageArtifactAdapterTest --tests com.dndmaster.adventure.application.scenario.preparation.SeededMapEntryFlowGmPortTest --tests com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationServiceTest --tests com.dndmaster.adventure.application.runtime.RuntimeGmPromptComposerTest --tests com.dndmaster.adventure.application.runtime.RuntimeTurnPlayerRollCommitGateTest --tests com.dndmaster.adventure.CrossContextHttpIntegrationTest :ai-game-master-local-codex:test --tests com.dndmaster.aigamemaster.localcodex.LocalCodexAiExecutionPortTest --tests com.dndmaster.aigamemaster.localcodex.McpStdioServerTest :combat-map-service:test --tests com.dndmaster.combatmap.CombatMapViewServiceTest --tests com.dndmaster.combatmap.GmViewAuthorizationTest --tests com.dndmaster.combatmap.MapPreparationPolicyTest
```

The matching Harness profile is `adventure-focused-unit` in `docs/agents/execution.json`.

The plan-scoped Harness profile `adventure-exploration-cycle-unit` runs only the #367 movement coordinator, exploration cycle, and exploration environment controller tests. Use this profile for plan-367 verification; its behavior is covered by focused unit and policy tests, while the integrated agent-driven journey is reserved for #375.

## 에이전트 응답 기반 모험 E2E

실제 브라우저 실행 전 [브라우저 실행·연결 절차](BROWSER.md)를 읽는다. 수동 모험 E2E는 headed Chromium에서 실제 화면을 캡처하고 한 단계씩 조작한다. 이 절차에서는 CUA Driver를 사용하지 않는다.

프런트엔드·중계기·사용자 PC 에이전트가 이미 실행 중이고 백엔드만 복구해야 할 때는 `adventure-backend-only` 프로필로 백엔드만 실행한다. 기존 백엔드를 유지하며 별도 포트로 띄우려면 `BACKEND_SERVER_PORT`를 지정한다. 새 프런트엔드도 필요하면 `adventure-frontend-only`에 `FRONTEND_DEV_PORT`와 그 백엔드 주소인 `BACKEND_E2E_URL`을 지정한다. 예: `BACKEND_SERVER_PORT=8085 node .codex/scripts/harness-exec.mjs --root "$PWD" --profile adventure-backend-only`, `FRONTEND_DEV_PORT=5177 BACKEND_E2E_URL=http://127.0.0.1:8085 node .codex/scripts/harness-exec.mjs --root "$PWD" --profile adventure-frontend-only`. 전체 `adventure-runtime-server` 프로필은 다른 서비스까지 시작하므로 이미 필요한 서비스가 실행 중인 경우 사용하지 않는다. 사용자가 서버를 유지하라고 요청한 E2E에서는 검증 후에도 백엔드·프런트엔드 프로세스를 종료하지 않는다. `adventure-frontend-only`는 시간 제한 없이 실행하며, 사용자가 중지할 때까지 프런트엔드를 유지한다.

전투 이동·주문 사거리 계획(#362–#375)은 [테스트 안내](../testing/combat-movement-spell-range/README.md)에 따라 실제 앱과 사용자 PC 에이전트를 사용한다. 에이전트 응답이 없는 화면 준비·입력은 headed Chromium의 Playwright CLI로 한 단계씩 조작할 수 있다. 에이전트 응답이 필요한 단계는 요청 전후 화면을 직접 캡처해 확인하고, 실제 응답에 따라 다음 행동을 정한다. Playwright 테스트 코드, 고정 응답, 직접 API 호출, 미리 정한 화면 순서는 사용하지 않는다. 5xx나 명시적 실패가 나오면 재전송하지 말고 중단한다. 상세 원칙은 [ADR-024](../adr/ADR-024-agent-driven-runtime-e2e.md)를 따른다.

로그인 세션이 바뀌어 사용자 PC 에이전트만 다시 연결해야 할 때는 `user-pc-agent-only` 프로필을 사용한다. `RELAY_WEBSOCKET_URL`, `RELAY_HEALTH_URL`, `AGENT_ACCESS_TOKEN`, 새 `AGENT_CONNECTION_ID`, `CODEX_EXECUTABLE`, `CODEX_CLIENT_DIRECTORY`를 전달한다. 연결 식별자는 `node -p 'require("node:crypto").randomUUID()'`로 만든다. 이 프로필은 앱·중계기·브라우저를 재시작하지 않고 에이전트만 실행한다. 인증 토큰은 명령 인자나 로그에 넣지 않는다.

#367은 `adventure-runtime-server`로 전체 서버의 준비 상태를 확인한다. `adventure-exploration-groups-cua` 프로필은 이전 브라우저 준비 기록이며 현재 실행 지침으로 사용하지 않는다. 실행 에이전트는 #367 로컬 티켓과 Product Spec의 UC-005를 기준으로 headed Chromium의 실제 화면을 한 단계씩 확인하고, 실제 그룹 차례·지도 이동·사건 해결·환경 진행 및 저장 상태를 확인한다. 브라우저 준비만 된 것은 E2E 통과가 아니다. 기존 모험을 재사용하거나 데이터베이스를 초기화하지 않는다.

모험 시작 전 지도 준비 복원과 격자 정렬 복구처럼 에이전트 진행을 포함하지 않는 검사는 `adventure-map-preparation-check` 프로필을 사용할 수 있다.

## 시나리오 편집 및 모험 준비 E2E

`adventure-preparation-e2e` 프로필은 `src/web-ui/e2e/backend-scenario-preparation.spec.ts`만 실행한다. 원본 룰북·시나리오 자료로 추출, 시나리오 편집, 플레이 준비, 캐릭터·파티 및 모험 준비 레코드 생성까지 확인한 뒤 멈춘다. 이 검증은 모험을 시작하거나 에이전트의 런타임 응답에 의존하지 않으므로 Playwright를 사용할 수 있다.

실행 순서는 `adventure-runtime-server` 시작·준비 확인 후 `adventure-preparation-e2e` 실행이다. 기존 DB를 초기화하거나 사용자의 자료를 지우지 않는다. 계정, 원본 자료, 포트, artifact cleanup 검사는 `docs/agents/execution.json`과 아래 실행 절차를 따른다.

모험 시작 뒤 실제 에이전트 응답을 따라가는 검증은 이 프로필에 넣지 않는다. 해당 흐름은 [에이전트 실행 검증 안내](../testing/combat-movement-spell-range/README.md)와 개별 테스트 Markdown을 따른다.

상황 후보 생성·전환 검증은 [모험 상황 생성 수동 E2E](../testing/scenario-situation-candidates/README.md)와 연결된 개별 절차를 따른다. 실제 게임 마스터 응답에 따라 진행하고 기존 추출 자료·저장된 같은 지도 격자를 재사용한다.

## 사전 조건

- WSL/Linux 셸에서 저장소 루트 기준으로 실행한다.
- Docker Compose, Java, Node.js 24, npm, Graphify가 설치되어 있어야 한다.
- 추출 기능을 검증할 때는 `~/workspace/dnd-master/docs/assets`의 실제 입력 PDF를 사용하고, 테스트할 PDF 경로와 파일을 먼저 확인한다.
- 같은 포트나 데이터베이스를 쓰는 다른 실행이 있는지 확인한다. 독립 실행이면 `BACKEND_SERVER_PORT`, `FRONTEND_DEV_PORT`, `POSTGRES_PORT`, `REDIS_PORT`, `LOCAL_AGENT_CONNECTION_RELAY_PORT`에 사용하지 않는 포트를 지정하고 `COMPOSE_PROJECT_NAME`을 고유하게 지정한다.

## 전체 개발 서버 실행

저장소 루트에서 실행한다. 브라우저로 룰북을 업로드해 처리 경로를 검증할 때는 로컬 카탈로그 자동 등록을 끈다. 그래야 업로드 UI만 추출 작업을 시작한다.

```bash
START_DEV_SKIP_LOCAL_RULEBOOK_CATALOG_SEED=true src/start-dev.sh
```

진단 로그가 필요한 경우에만 다음처럼 켠다. 이 설정은 로컬 개발용이며, 로그에 플레이어가 입력한 내용이 포함될 수 있으므로 실행 로그를 외부에 공유하기 전에 확인한다.

```bash
START_DEV_SKIP_LOCAL_RULEBOOK_CATALOG_SEED=true \
ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED=true \
src/start-dev.sh
```

실행 스크립트가 PostgreSQL·Redis 컨테이너를 준비한 뒤 로컬 연결 중계기, 백엔드, 사용자 PC 에이전트, 프런트엔드를 시작한다. 실행 중 표시되는 주소를 사용한다. 기본값은 다음과 같다.

- 프런트엔드: `http://127.0.0.1:5173`
- 백엔드와 상태 확인: `http://127.0.0.1:8080/actuator/health`
- 로컬 연결 중계기: `http://127.0.0.1:8081/actuator/health`
- API 문서: `http://127.0.0.1:8080/swagger-ui.html`

프런트엔드와 백엔드는 같은 `src/start-dev.sh` 실행에서 띄운 프로세스를 사용한다. 기존 포트 리스너가 있으면 다른 체크아웃에서 실행 중인지 확인하고, 이 체크아웃의 E2E 결과로 보고하기 전에 현재 실행을 이 체크아웃에서 다시 시작한다. 포트가 충돌하면 위 환경 변수로 별도 포트를 지정한다.

종료할 때는 실행한 터미널에서 `Ctrl+C`를 누른다. 실행 스크립트가 자신이 띄운 백엔드·프런트엔드·중계기·사용자 PC 에이전트를 종료한다. PostgreSQL·Redis 컨테이너는 유지되므로 완전히 내릴 때는 실행 때 사용한 것과 같은 Compose 프로젝트 이름으로 다음 명령을 실행한다.

```bash
docker compose -p "$COMPOSE_PROJECT_NAME" -f src/infra/compose.yaml down
```

Compose 프로젝트 이름을 직접 지정하지 않았다면 실행 스크립트가 `src` 경로에서 기본 이름을 계산한다. 병렬 실행이나 나중에 컨테이너를 내릴 필요가 있는 테스트에서는 시작할 때 `COMPOSE_PROJECT_NAME`을 직접 지정하고 그 값을 보관한다.

기본 프로젝트 이름으로 실행했다면 저장소 루트에서 다음 명령으로 인프라를 내릴 수 있다.

```bash
compose_root="$(git rev-parse --show-toplevel)/src"
compose_project="dnd-master-$(printf '%s' "$compose_root" | sha256sum | cut -c1-8)"
docker compose -p "$compose_project" -f "$compose_root/infra/compose.yaml" down
```

## 브라우저 기반 룰북 추출 E2E

1. 위 명령으로 현재 체크아웃의 전체 서버를 시작하고 브라우저에서 `http://127.0.0.1:5173`을 연다.
2. 사용자 화면에서 룰북 업로드를 시작한다. 테스트 전용 PDF를 사용하고 기존 공유 카탈로그의 자료를 덮어쓰지 않는다.
3. 화면의 처리 상태가 완료 또는 경고 포함 완료가 될 때까지 기다린다. 실패하면 브라우저 오류와 서버 로그에서 단계 및 실패 원인을 확인한다.
4. 결과에 PyMuPDF에서 온 본문과 Docling에서 온 표 셀이 함께 포함되는지, 페이지 출처가 보존되는지 확인한다. Docling 표 후보는 문맥과 표 구조를 원본 PDF에 대조한다. 두 제공자의 결과를 단순히 합쳤다는 사실만으로 추출 정확도가 확인된 것은 아니다.
5. 실행 결과와 임시 산출물은 저장소 밖에 보관한다. E2E 실행 전후의 미추적·무시 파일을 비교하고, 실행 중 생성된 산출물만 정리한다. 기존 미추적 파일이나 무시 파일은 삭제하지 않는다.

기본 로그인 정보와 테스트 입력 자료 경로는 `src/start-dev.sh`에서 확인한다. 이 개발용 계정은 로컬 테스트에만 사용한다.

## 테스트 산출물과 실행 증거

E2E 결과에는 체크아웃 경로, `git rev-parse HEAD`, 실행 명령, 백엔드·프런트엔드 PID와 작업 디렉터리, URL, 사용한 입력 파일, 화면상 처리 결과, 관련 로그, 종료 및 정리 결과를 남긴다. 새 산출물은 저장소 밖에 기록하고, 저장소 안에 생성된 항목은 실행 전 상태와 대조해 새 항목만 제거한다.

Codex 연결 경로를 검증하는 경우에만 같은 체크아웃에서 사용자 PC 에이전트를 연결하고 연결 로그를 확인한다. 다른 AI 제공자를 사용한 테스트는 Codex 연결 경로를 검증한 것으로 보고하지 않는다.

### 지도 보정 UI 대기 경계

이 E2E는 개별 UI 조작과 화면 이동을 30초로 제한한다. 지도 진입·준비 완료 후 재개는 모델 요청을 포함하므로 300초까지 기다린다. 격자 측정은 기존 원점 핸들과 겹치지 않는 내부 교차점 두 곳에서 대각선 3×3칸을 선택하고 매 클릭 뒤 화면 상태를 확인한 다음 원점을 지도의 격자 왼쪽 위 모서리로 옮긴다. 격자 수는 지도에 실제로 인쇄된 칸 수와 맞춘다. 적용 버튼이 비활성인 상태를 전체 75분 테스트 제한까지 기다리지 않는다.

시작 위치 추론 재검증: 전용 입력은 `ENTRY_NARRATION`에 상황 진입 서술을 한 번만 전달한다. 그룹 이동 E2E는 격자 보정 후 `AI 벽·문 감지` 응답을 최대 300초 기다리고, 경계가 하나 이상인지 확인한 다음 초안을 저장한다. `adventure-focused-unit`은 `:ai-game-master-service:test --tests com.dndmaster.aigamemaster.api.MapModelContractTest`도 실행한다.

그룹 지도 이동 확인 후 `/turns` 응답은 GM 처리 요청이므로 최대 300초 기다린다. 일반 UI 조작의 30초 제한과 구분한다.

시작 위치 후보가 0개이면 최초 요청 뒤 최대 5회 재시도한다(총 6회). 각 시도와 후보 수·소요 시간·소진 여부를 진단 로그에 기록한다. 후보가 생기면 중단하고, 소진되면 기존 수동 선택으로 복구한다. 지도 활성화의 내부 HTTP 대기는 기존 준비 제한의 6배이며, E2E 지도 준비 완료 후 재개 대기는 최대 1,800초다. 일반 이동 제출은 300초를 유지한다.

지도 ID 재사용 검증은 `adventure-focused-unit`의 `PreparedMapEntryCommitGateTest`와 `CombatMapViewServiceTest`에 포함된다. 같은 지도 유지·이전 지도 복귀에서는 AI 생성과 시작 위치 추론을 호출하지 않고 저장 상태를 유지한다.

`HttpCombatMapPreparationGatewayTest`는 저장된 지도 재사용 시 추가 공간 자료 준비를 생략하고 `MAP_SPAWN_REVIEW_REQUIRED`(시작 위치 검수 필요) 응답을 유지하는 경로도 검사한다.

환경 단계 검증: GM 지도 상태에서 `MAP_IMAGE`를 제거한 텍스트와 `imageDataUri`를 분리한다. 격자·벽·문·토큰·발견 상태는 텍스트에 유지하고 실제 Codex 요청은 text/image 입력 각각을 가진다. `dev_environment_image_input`과 `dev_gm_environment_image`의 텍스트 길이·이미지 첨부 여부를 확인한다. 환경 단계 진행 응답은 모델 요청을 포함하므로 E2E에서 최대 300초 기다린다. `adventure-focused-unit`은 `HttpTypedRuntimeGmAgentPortTest`와 `TypedAgentContractControllerTest`도 실행한다.

## 적 전투 수치 출력 계약 검증

`enemy-sheet-contract-unit` 프로필은 에이전트의 `전투수치` JSON 응답 해석, 출처 범위 검증, 시트 재사용, 일반 턴과 적 시트 준비 요청의 구분을 검증한다. 실제 서버나 브라우저를 재시작하지 않는다.

```bash
node .codex/scripts/harness-exec.mjs --root "$PWD" --profile enemy-sheet-contract-unit
```

## #365 지도 활성화 전 초기 배치 검증

`BR-40`·`AC-33`의 지도 준비 경계는 `situation-map-preparation-unit` 프로필로 검증한다. 대상은 상황 진입 준비, 준비 요청·배치 저장, 필수 배치 누락 시 활성화 차단, 재사용과 공개 정보 제한이다. 실제 브라우저와 실행 중인 서버는 종료하거나 재시작하지 않는다.

```bash
node .codex/scripts/harness-exec.mjs --root "$PWD" --profile situation-map-preparation-unit
```

`#365` 문 잠금 상태의 PostgreSQL 저장·복원과 명령 이력 검증은 `situation-map-persistence-unit` 프로필로 실행한다. Docker 준비 상태를 확인한 뒤 테스트가 격리된 PostgreSQL 컨테이너를 생성·종료하며, 실행 중인 개발 DB나 서버에는 쓰지 않는다.

```bash
node .codex/scripts/harness-exec.mjs --root "$PWD" --profile situation-map-persistence-unit
```

## VictoriaLogs local

설정과 MCP 연결: [로컬 VictoriaLogs](../observability/victorialogs/README.md). `victorialogs-infrastructure`를 foreground로 실행하고 별도 세션에서 `victorialogs-check`와 실제 앱 profile을 사용한다. 종료는 `victorialogs-stop`이며 저장 데이터는 보존한다. 모든 실행은 `node .codex/scripts/harness-exec.mjs --root <workspace_root> --profile <id>`를 사용한다. Spring Boot 개발 profile 연결은 설치된 `victorialogs-local-setup` 스킬이 기존 source/config에 맞춰 완료한다.

새 개발 런타임은 먼저 `adventure-runtime-container-redis` 프로필로 Redis가 준비됐는지 확인하고, 없으면 기존 개발 Compose 프로젝트에 Redis만 시작한다. 이어 `adventure-runtime-container` 프로필로 공식 Ollama 이미지, `app-all`, 새 연결 중계기를 실행하고, 이미 실행 중인 공식 `vmagent` 컨테이너는 그대로 이용한다. 이 프로필은 `vmagent`를 시작하거나 재생성하지 않는다. 기존 연결 중계기도 건드리지 않고, 새 중계기는 기본 포트 18082를 사용한다. Ollama는 `/usr/share/ollama/.ollama/models`의 기존 모델 파일을 읽기 전용으로 사용하므로 대용량 모델을 복사하거나 다시 받지 않는다. 앱은 Compose 서비스 이름 `ollama`로 요청한다. Ollama에는 Docker GPU 연결을 설정한다. 앱과 새 중계기는 PostgreSQL·Redis 네트워크에 연결되고, 앱·중계기·`vmagent`는 VictoriaMetrics·Collector 네트워크에도 연결된다. `vmagent`가 `http://app-all:8080/actuator/prometheus`를 scrape해 VictoriaMetrics로 전송한다. 로그는 Collector 서비스 이름으로 전송한다. Docker named volume은 Docker Desktop에 그대로 둔다. 실행 준비는 앱·중계기 이미지 빌드에 한해 수행하며, Ollama 이미지는 컨테이너 시작 시 로컬에 없을 때만 Compose가 받는다.

`adventure-runtime-container`는 컨테이너 health와 `up=1`을 확인한 뒤 성공 종료하며, Compose 서비스는 background에서 계속 실행된다. 성공 receipt를 받은 다음 별도 터미널에서 headed Chromium 시나리오를 실행한다. 같은 프로필을 중복 실행하지 않는다.

실행 중인 스택에서 `app-all` 백엔드만 새 이미지로 갱신해야 할 때는 `adventure-runtime-container-app-only` 프로필을 사용한다. 이 프로필은 `app-all`만 빌드·재생성하고 해당 백엔드의 health만 확인한다. `--no-deps`로 실행하므로 브라우저, 연결 중계기, Ollama, vmagent 및 데이터베이스를 재시작하지 않는다. 로컬 룰북 관리 화면 권한은 `RULE_KNOWLEDGE_BACKOFFICE_ADMIN_PLAYER_IDS`로 한정하며 기본값은 로컬 데모 계정 하나다.

실행 중인 앱을 바꾸지 않고 `app-all` 이미지만 빌드할 때는 `adventure-runtime-container-app-build-only` 프로필을 사용한다. 컨테이너 적용은 별도 작업이다.

모험 동작 진단 로그가 필요한 로컬 E2E에서는 프로필 실행 시 `ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED=true`를 전달한다. 기본값은 false다. 이미 같은 Compose 프로젝트의 앱·릴레이 포트를 사용 중이면 사전 검사는 해당 서비스 소유가 확인될 때만 허용하고, Compose는 설정이 바뀐 `app-all`만 갱신한다. 수집이 끝난 뒤 다음 실행에서는 플래그를 끈다.

기존 WSL 백엔드가 8080을 사용 중이면 컨테이너 프로필은 중단되며 기존 프로세스를 종료하지 않는다. 전환할 때는 기존 백엔드를 명시적으로 종료한 뒤 프로필을 실행한다. 프로필이 성공 종료해도 Compose 서비스는 계속 실행된다. `bash scripts/container-runtime.sh stop`은 앱과 새 테스트 연결 중계기만 멈추며 Ollama, 기존 연결 중계기, vmagent는 계속 실행한다. 기존 WSL 백엔드를 유지하는 동안은 `victorialogs-wsl-metrics` 프로필이 메트릭을 수집한다.

## Diagnostic log v1

[진단 로그 v1 계약](../observability/victorialogs/diagnostic-log-schema.md)을 따른다. 원본 JSONL의 경로를 HARNESS_DIAGNOSTIC_LOG_FILE로 지정하고 harness-exec의 victorialogs-diagnostic-logs profile로 검사한다. Docker 없이 실행되며 실제 앱 emitter/context/redaction 및 수집 확인은 별도다.

## 개발용 로그·성능 수집을 연결한 백엔드

`adventure-backend-observability`는 기존 `adventure-backend-only`의 환경·명령을 유지하고 개발 실행에만 OpenTelemetry Java agent 2.21.0과 외부 Spring 설정을 추가한다. 수집 서버 준비 상태를 먼저 확인한다. 서비스 이름은 `app-all`, 프로젝트 ID는 `dnd-master-spec-combat-movement-spell-range`다. 로그·성능 자료는 각각 3일 보존한다. 기존 서버와 같은 8080을 사용하므로 동시에 실행하지 않는다.

Java agent는 `.codex/tools/opentelemetry/`의 개발 도구이며 배포 JAR에 포함하지 않는다. Prometheus registry는 `developmentOnly`에 두어 `bootRun`에서만 로드한다. 기존 `SPRING_CONFIG_ADDITIONAL_LOCATION`은 보존한다. 일반 logger 수집을 도메인 진단 계약 구현 완료로 간주하지 않는다. MCP 연결 정의는 `docs/observability/victorialogs/mcp.json`에 있으며 클라이언트 연결 여부는 별도로 확인해야 한다.

설정 적용 시 실행 중인 백엔드·브라우저를 재시작하지 않았다. 새 실행 프로필은 다음 백엔드 실행부터 적용한다.
