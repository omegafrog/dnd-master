# 031-2 계획·시트 기반 첫 GM 프롤로그와 재접속 표시

- Issue: #108
- Status: pending
- Dependencies: #107

## Goal

승인된 첫 단계와 실제 캐릭터 시트에 근거해 모험의 첫 GM 서술을 만들고, 이를 영속 대화로 저장하여 새로고침 뒤에도 첫 화면에서 보여 준다.

## Scope

- 일반 플레이어 행동 턴과 분리된 start-time prologue orchestration seam을 추가한다.
- 첫 stage, 인용 가능한 Storybook evidence, 파티의 실제 시트 snapshot만으로 프롤로그와 초기 장면을 생성한다.
- 프롤로그를 `Adventure` conversation의 첫 GM entry로 원자적으로 저장하고, 시작 복구가 중복 메시지를 만들지 않게 한다.
- 기존 모험 대화/현재 stage를 조회하는 read API와 UI hydration을 제공한다.
- 준비가 완료된 신규 시작에서 generic clarification("추가 정보 필요")을 첫 GM 응답으로 반환하지 않는다.

## Acceptance Criteria

- 새 모험은 플레이어 입력 전, 현재 첫 단계와 관련 캐릭터 사실을 반영한 GM 프롤로그를 가진다.
- 프롤로그의 게임 사실은 031-1 plan citation 또는 character-sheet snapshot으로 추적된다.
- 페이지 새로고침·재접속 후에도 동일한 저장 프롤로그와 대화 순서를 표시한다.
- start 재시도/복구는 프롤로그를 정확히 한 번만 기록한다.

## Test Contract

- Policy unit: 프롤로그 입력이 stage plan·sheet·citation 밖의 게임 사실을 사용하지 못하게 한다.
- Integration: session-start outbox/recovery와 Adventure conversation의 exactly-once 프롤로그 저장을 검증한다.
- UI ↔ entity E2E: 새 세션을 시작하고 첫 GM 프롤로그를 본 뒤 새로고침해 같은 메시지가 유지되는지 검증한다.

## Implementation Areas

- `adventure-service`: start-time prologue application service, conversation read projection, idempotent persistence.
- `ai-game-master-service`: grounded prologue contract/adapter와 narration safety.
- `character-management-service`: prologue용 authoritative sheet snapshot 읽기.
- `web-ui`: `AdventureApi` conversation hydration과 `AdventureStream` 초기 렌더링.
