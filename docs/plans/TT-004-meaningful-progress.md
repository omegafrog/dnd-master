# TT-004: Meaningful Progress And Presentation-Only Retry

- Issue: [#241](https://github.com/omegafrog/dnd-master/issues/241)
- Status: `ready-for-agent`
- Dependencies: TT-003 `completed`
- Parent: [#237](https://github.com/omegafrog/dnd-master/issues/237)

## 구현 목적

Player intent 반영과 Meaningful Progress를 GM Turn commit 조건으로 만든다. 실패 retry는 저장된 ResolvedTurnPlan만 재사용하고 rule/tool resolution을 반복하지 않는다.

## Implementation Scope

- `MeaningfulProgress` value object와 verifier policy
- intent → plan → resolved → narration 일치 검증
- 직전 N turn 반복 검증 seam
- presentation-only retry API/application flow
- `NO_MEANINGFUL_PROGRESS` typed failure 연결

## Acceptance Criteria

- 성공 turn은 progress category 최소 1개
- 구체적 선택지/판단 정보 없는 `DECISION_REQUIRED` 거부
- intent 누락 또는 narration/result 불일치는 commit 안 됨
- retry는 persisted resolved artifact를 재사용하고 tool/state effect를 반복하지 않음

## Test Contract

- Policy unit: 다섯 progress category와 rejection counterexamples
- Application test: resolved replay, idempotency, commit boundary
- UI/API ~ entity E2E: 반복 중립 narration 거부 → presentation retry → 단일 commit

## Implementation Boundaries

- TT-003 failure artifact 사용
- Writer가 state delta/tool command를 생성하지 않음
