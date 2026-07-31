# 031-3 현재 단계 인지 GM 턴과 근거 있는 전환

- Issue: #109
- Status: pending
- Dependencies: #107, #108, #102, #103, #104

## Goal

후속 GM 턴이 현재 단계의 상세 계획·관련 스토리북 근거·활성 파티 시트를 인지하여 자연스럽게 진행하고, 근거 있는 전환 조건을 만족할 때 다음 단계로 이동하게 한다.

## Scope

- runtime planning request에 active stage, plan citation, stage-scoped evidence, authoritative sheet snapshot을 제공한다.
- 일반 행동은 현재 단계의 묘사와 다음 선택지로 보수적으로 진행하고, 진행할 수 없는 구체적 정보 부족에만 clarification을 반환한다.
- 단계 완료/전환 제안을 plan의 transition condition과 evidence에 대해 검증하고 Adventure cursor·conversation과 함께 저장한다.
- 025의 구조화 판정, 자동 굴림, 검증된 상태 효과 정책을 보존하며 전환은 그 결과를 우회하지 않는다.
- 재개·중복 action에서 stage transition이 중복 적용되지 않게 한다.

## Acceptance Criteria

- GM 입력은 현재 stage와 관련된 Storybook evidence만을 게임 사실의 근거로 사용한다.
- 정상적인 첫 행동은 모호한 clarification 대신 현재 장면에 맞는 서술 또는 필요한 기존 판정 흐름을 반환한다.
- 충족된 transition condition은 다음 stage cursor와 출처를 한 번만 저장하고 재개 후 유지한다.
- 근거 없는 stage jump, NPC/단서/보상 생성, 025 밖의 상태 변경은 거절된다.

## Test Contract

- Policy unit: stage-scoped evidence, clarification threshold, transition condition, duplicate transition을 검증한다.
- Integration: runtime turn, resolved check, persisted cursor, resume/idempotency 경로를 검증한다.
- UI ↔ entity E2E: 여러 플레이어 행동으로 첫 단계를 진행·전환하고 새로고침 뒤 다음 단계와 대화가 유지되는지 검증한다.

## Implementation Areas

- `adventure-service`: runtime planning contract, stage transition policy, Adventure persistence/read model.
- `ai-game-master-service`: stage-aware grounded planning/narration adapter.
- `rule-knowledge-service`: stage-scoped evidence retrieval.
- `web-ui`: active-stage 표시와 turn/transition hydration.
