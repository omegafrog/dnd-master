# 022 - 에이전트 캐릭터 자동 턴

- Status: completed
- Dependencies: 021
- Issue: [#54](https://github.com/omegafrog/dnd-master/issues/54)

## Outcome

공통 턴 커서가 직접 플레이 입력 대기와 에이전트 캐릭터 자동 실행을 구분한다.

## Scope

- 제어 방식 기반 턴 커서와 직접 플레이 입력 대기
- 에이전트 캐릭터 목록 순회와 차례 시 단일 시트 지연 조회
- AI 행동 후보를 Runtime Command Saga로 검증·실행
- `sessionId + turnId + characterSheetId` 기반 중복 억제

## Acceptance Criteria

- 전체 파티 시트를 한 번에 조회하지 않는다.
- 직접 플레이 차례에서는 사용자 입력 전 다음 차례로 이동하지 않는다.
- 에이전트 행동은 시트와 현재 턴 문맥을 근거로 자동 실행한다.
- 재시도에도 자동 행동이 중복 적용되지 않는다.

## Test Contract

- Policy unit: 턴 커서, 지연 조회, 멱등 키.
- Integration: AI 후보·시트 조회·Saga 실패 재시도.
- UI~entity E2E: 직접·에이전트 혼합 턴 흐름.

## Implementation Scope

- `adventure-service` runtime turn, planning contract, Character Management read port, tests.
- `ai-game-master-service` 행동 후보 계약 확장과 tests.
- `web-ui` 직접 입력 대기·에이전트 자동 진행 표시와 tests.
