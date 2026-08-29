# Plan 218: Resolved Turn Lifecycle

- Issue: #218
- Status: `completed`
- Dependencies: 없음

## 구현 목적

Planner 결정과 rule/tool 해결 결과를 prose와 분리한 `ResolvedTurnPlan`으로 영속화한다. Writer 실패가 world state, conversation, tool saga를 변경하거나 중복 실행하지 않게 한다.

## 구현 범위

- `TurnPlan`, `ResolvedTurnPlan`, PlannerContext lifecycle 모델 추가.
- `RuntimeTurn`에 `RESOLVED_UNCOMMITTED` lifecycle과 immutable resolved artifact 저장.
- `RuntimeTurnApplicationService`에서 planning → existing authorized resolution → uncommitted persistence 분리.
- 기존 `RuntimePlan`은 public response/persistence compatibility projection으로 유지.
- additive JSON migration, legacy `runtime_turn_json` deserialization 유지.

## 제외 범위

- Writer 호출과 prose 재시도.
- 개발용 artifact 조회 API.

## Acceptance Criteria

- prose 없이 resolved turn을 command id와 함께 영속화할 수 있다.
- unresolved/resolved turn은 adventure context, conversation, world transition을 commit하지 않는다.
- duplicate command와 crash recovery가 같은 resolved artifact를 재사용한다.
- 기존 runtime turn JSON row를 읽어 presented compatibility projection으로 재생할 수 있다.

## Test Contract

- Policy unit: lifecycle transition, resolved artifact immutability, pre-presentation no-commit, duplicate command.
- Integration: repository migration/legacy JSON deserialization, existing tool saga idempotency.
- UI ~ entity E2E: 기존 `/messages` 요청이 plan/resolution 단계까지 하나의 runtime turn으로 저장되고, UI 응답 전에 state가 커밋되지 않음을 검증한다.

## 구현 순서

1. domain lifecycle과 compatibility projection.
2. persistence migration/repository.
3. application orchestration과 recovery.
4. unit/integration/E2E regression.
