# 모험 완료 E2E 결과 (2026-10-06)

## 실행

- 기존 저장 데이터를 재사용: bundle `982ec8e4-9a45-4e73-a156-4535b00cf8a8`, package `beb5727b-c332-4d82-87ff-dc727b7f71e0`, session `303dcf7f-591e-44cd-9024-39191afc025b`, adventure `91dc2cc7-feed-4273-aad8-50cdbeafa483`.
- `./src/start-dev.sh` 실행 시 `BACKEND_SERVER_PORT=44100`, `FRONTEND_DEV_PORT=44101`, `POSTGRES_PORT=44103`, `REDIS_PORT=44104`, `LOCAL_AGENT_CONNECTION_RELAY_PORT=44105`, `COMPOSE_PROJECT_NAME=dnd-e2e-adventure-313845`, `ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED=true`, `RULE_KNOWLEDGE_ASSET_FALLBACK_ROOT=/home/jiwoo/workspace/dnd-master/docs/assets`를 사용.
- 실제 Playwright 브라우저에서 기존 세션을 열고 입력했다. 사용자 PC 에이전트의 WebSocket 연결은 `/tmp/e2e-user-pc-agent-20261006.log`에서 확인.

## 결과

- 마지막 전투 행동은 실제 전투 UI의 공격 버튼으로 제출. Rat 7 공격 요청 응답은 `COMBAT_ENDED`, 주사위 12, 명중 17 대 방어도 12. 여덟 마리 쥐가 모두 쓰러졌고 전투 참가자 목록에서 제외됨을 확인.
- 이후 UI에서 굴 입구를 찾아 빈 맥주통과 돌로 막고 Glowkindle에게 보고하는 행동을 한 번 제출. turn `9d05269d-e7bf-4ae2-a29f-01b982b312c0`이 `COMMITTED`; 지하 유적과 연결된 굴을 발견하고 막았으며 의뢰와 유입 경로 조사가 해결됐다는 서술을 받음.
- 최종 DB 상태: adventure `COMPLETED`, version 9; session `COMPLETED`, version 49. turn completion proposal은 `complete=true`, resolved objectives `objective-clear-cellar`, `objective-find-origin`, criteria `resolution-clear-rats`, `resolution-trace-origin`.
- UI는 그 응답 직후 `모험 완료 / 모험의 막이 내렸습니다 / 마지막 장면` 기록 화면으로 즉시 전환. 굴을 막고 보고한 응답이 마지막 장면 자체였으며, 그 뒤 플레이어가 이어서 응답하는 별도의 엔딩 페이즈는 관찰되지 않음. 따라서 목표 해결, 상태 완료, 마무리 서술까지는 동작했지만 별도 엔딩 상호작용 단계 요구는 충족하지 못한 것으로 판정.
- 진단 로그에서 첫 장면의 `Beer Cellar` 전환은 모델이 `TRANSITION`을 제안했고 시나리오 근거 `location-beer-cellar`로 통과했다. 최종 장면도 `Wizard’s Tower Brewery`로 돌아가는 `TRANSITION`을 제안해 `resolution-trace-origin`으로 통과했다. 이번 실행에서는 전환 누락이나 전환 검증 탈락이 없었다.
- 근거 충분성 판정은 자연 1 빗나감 설명의 근거를 찾지 못해 같은 검색어 지문에서 세 차례 `false`를 반환했다. 마지막 굴 차단·보고 입력도 난이도와 차단 조건을 직접 뒷받침하는 근거가 없다는 `false` 판정이 세 번 나왔지만, GM 응답과 완료 처리는 끝까지 진행됐다. 상세 원인은 [조사 보고서](e2e-adventure-issue-findings-2026-10-05.md)의 항목 8에 기록했다.
- 완료 화면에 보상 항목은 표시되지 않음.
- 사용자 PC 에이전트의 WebSocket 등록과 연결 로그를 확인했고 이번 모험 중 연결은 유지됐다. 연결을 강제로 끊었다 복구하는 실시간 시험은 하지 않았다. 재연결 대기와 새 연결 식별자 발급은 단위 시험 및 릴레이 회귀 시험으로 검증했다.

## 관찰된 클라이언트 문제

- 세션 재접속 시 저장된 `PENDING_ROLL`이 UI에 복원되지 않아 판정 입력이 사라짐. 해당 turn은 재전송하지 않고 저장된 정확한 turn ID로 판정을 처리.
- 세션 본문에는 행동 입력창과 제출 버튼이 정상 렌더됐지만, Playwright의 역할 이름 선택자 `getByRole('button', { name: '행동 보내기', exact: true })`가 버튼을 찾지 못해 입력 자동화가 제출 전 대기 시간 초과. DOM CSS 선택자 `form button[type=submit]`으로 확인 후 한 번 제출해 서버 도달을 검증했다. 이는 수동 브라우저 사용자 입력에 대한 실패 증거가 아니라 자동화 접근성 선택자 문제.
- 세션의 현재 위치 UI는 `opening`을 표시했으나 서버가 기록한 장면 ID는 `beer-cellar`로 이동한 상태.
- 화면 복원 시 전투 조회 `GET /api/v1/adventures/{id}/combat`가 404 및 `ERR_ABORTED`를 기록함. 전투 종료 화면 자체는 정상 표시.
- 이 실행에서 기존 `EvidenceAcquisitionApplicationService` 로그가 개발 플래그와 무관하게 검색 문장과 룰북 발췌 일부를 출력했다. 실행 후 해당 추적 로그를 플래그 뒤로 옮기고 근거 ID·자료 위치만 남기도록 바꿨다. 플래그를 끈 경우 로그와 발췌 원문이 모두 억제되고, 켠 경우 단계 로그는 남지만 발췌 원문은 기록되지 않는 테스트를 추가해 통과했다.

## 근거 파일

- 정제한 백엔드/중계 서버 개발 진단 로그: `/tmp/e2e-diagnostics-20261006.log` (개발 진단·확정 행만 남기고 기존 원문 발췌 로그는 제거)
- 동일 경로 `/tmp/e2e-full-resume-20261006.log`도 정제된 사본으로 교체됨.
- 사용자 PC 에이전트 출력: `/tmp/e2e-user-pc-agent-20261006.log`
- 최종 쥐 처치 응답 및 화면: `/tmp/e2e-combat-last-rat.log`, `/tmp/e2e-combat-last-rat.png`
- 종료 목표 입력/응답: `/tmp/e2e-block-passage-retry.log`, `/tmp/e2e-block-passage.png`
- 입력 화면: `/tmp/e2e-before-block-retry.png`

## 수정 후 새 모험 E2E (2026-10-06)

수정한 대화 복구·장면 표시·동명이인 대응을 확인하기 위해 새 모험을 브라우저 UI로 만들고 완료까지 진행했다. 활성 워크트리는 `/home/jiwoo/workspace/dnd-master-spec-gm-context`; 포트 `44110–44114`, Compose 프로젝트 `dnd-e2e-fix-20261006-44110`, 개발 진단 플래그 활성화. 사용자 PC 에이전트는 같은 워크트리에서 실행했고 릴레이 WebSocket 연결을 확인했다.

- 시나리오 bundle `f2f43647-4041-4ea2-a82f-7292179f4b98`, package `ec0fb03e-7fe1-483c-a1c6-6822865560a8`, session `baa17cb8-2b50-4c82-9877-44a3c5ef203e`, adventure `e868f753-97e6-498d-b44b-fdd1ea97168a`.
- 판정 대기 turn `44fd18c3-4808-49fb-8f03-14f610998894`는 `PENDING_ROLL` 상태에서 브라우저 새로고침 후에도 입력 UI가 복원됐다. 조회 응답은 같은 turn ID, `expectedVersion=2`, `currentScene=opening-situation`을 반환했다. 복구된 판정 제출은 한 번만 보내 200으로 처리했다.
- turn `60fdcc7a-3acf-4df6-88b4-43ed3618386c`가 저장고 진입 전환을 확정했다. 브라우저 새로고침 후 현재 위치는 `beer-cellar`로 남았고, 이전 판정 결과도 대화 기록에 보존됐다.
- 파티에 이름이 모두 `Mira`인 캐릭터 시트 둘을 넣었다. 시트/전투 참가자 ID `44194223-8c88-4fd9-9416-fb5fef2cfabb`와 `412b38de-a270-4303-9157-e85a168d8193`가 각 행에 정확히 연결됐다. 우선권은 각각 19와 15였고 현재 차례 강조는 현재 참가자 ID와 일치하는 한 행에만 표시됐다.
- 전투에서 공격과 마법 화살을 실제 UI로 제출했다. 성공한 공격·주문으로 쓰러진 적은 참가자 목록에서 제거됐다. 초기 턴 종료 요청은 `AI_REQUEST_ALREADY_IN_PROGRESS`로 거부됐고, UI가 처리 완료 응답을 받은 뒤 재요청한 턴 종료는 성공했다. 즉, 진행 중인 AI 응답을 기다리도록 한 뒤 안전하게 차례가 넘어갔다.
- 완료 후 전투가 끝나 저장고 탐색으로 돌아왔고, 굴을 막고 의뢰인에게 보고하는 행동을 제출했다. 종료 화면은 `모험 완료 / 모험의 막이 내렸습니다` 크레딧 장면을 표시했다. 인증된 조회에서 session과 adventure 모두 `COMPLETED`, conversation version 30, 현재 장면은 쥐 소탕·통로 차단·양조장 문제 해결을 요약한 한국어 서술, 대화 50개, 대기 판정 없음으로 확인됐다.
- 사용자 행동 근거 수집은 각 단계에서 실제 진단 로그로 추적했다. 첫 행동은 판정 대기라 추가 검색이 필요하지 않았고, 저장고 진입은 검색·충분성 판정 각 한 번으로 통과했다. 전투 공격도 검색 한 번과 충분성 판정 성공 뒤 진행됐다. 최대 추가 검색 1회 제한 자체는 백엔드 회귀 테스트로도 검증했다.
- 종료 행동은 근거 검색·재정렬, 충분성 판정, 시나리오 조회, 게임 마스터 응답 경로를 거쳐 약 100초 후 확정됐다. 추가 검색 횟수 상한은 적용됐지만 이 전체 응답 시간은 여전히 길다. 실행 종료 정리 과정에서 원시 진단 로그를 남기지 않아 어느 원격 호출이 대부분의 시간을 차지했는지는 이 실행만으로 확정할 수 없다.
- 전투 중 보인 `1레벨 주문 슬롯이 부족합니다` 문구는 해당 주문 응답이 `COMMITTED`로 확정되고 주문 슬롯이 0으로 갱신된 뒤에도 표시됐다. 슬롯이 실제로 0인 최신 상태에 맞는 안내이며 서버의 자원 부족 거부가 아니므로 추가 오류로 분류하지 않았다.

E2E 종료 후 브라우저, 개발 서버, 릴레이, 전용 Compose 컨테이너·볼륨·네트워크를 정리했다. 포트 `44110–44114`에 남은 프로세스가 없고 전용 Compose 컨테이너가 없는 것을 확인했다. 실행 전후 `git status --short --ignored=matching` 차이는 없었고, 기존 워크트리 파일은 보존했다.
