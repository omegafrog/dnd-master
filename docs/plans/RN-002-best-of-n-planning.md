# Plan RN-002: Best-of-N TurnPlan Planning

- Issue: #227
- Parent Issue: #225
- Status: `completed`
- Dependencies: RN-001
- Source: #208

## 구현 목적

최종 prose를 여러 번 생성하지 않고 compact TurnPlan 후보를 비교한다. 동일한 규칙·상태·정보 경계를 지키는 후보 중 Player Agency와 연속성을 보장하면서 현재 Story Stage에 유용한 계획을 선택한다.

## 구현 범위

- configurable candidate count N: 기본 3, 단순 턴 1.
- `PlanCandidatePort`, `CandidateHardFilter`, `PlanJudgePort`, `PlanSelectionPolicy`.
- invalid candidate 제거: secret leak, unsupported state/entity, agency/rule violation.
- score/evidence/selection audit와 deterministic tie-break.
- 기존 `RuntimePlanningPort` 및 AI GM v2 DTO와 명시적 versioned mapping.

## 제외 범위

- TurnPlan schema 자체 재설계.
- prose Best-of-N, Writer, Verifier.
- adaptive N 정책의 고급 학습.

## Acceptance Criteria

- N=1/N=3 동작.
- 후보는 동일 Player Intent, state, stage, information boundary를 공유.
- invalid 후보는 Judge 전에 제거.
- agency/continuity 우선 후보가 선택.
- 동률이면 더 단순한 계획 선택.
- Writer에는 선택된 후보 하나만 전달.
- prose 생성 없이 후보 비교.

## Test Contract

- Policy unit: candidate count, hard filter, scoring/tie-break, zero-valid fallback.
- Contract/integration: AI GM candidate DTO round-trip, provider failure, audit persistence.
- UI ~ entity E2E: 같은 Player action이 runtime turn 하나로 처리되고 선택된 plan만 writer context에 도달.

## 구현 순서

1. candidate/filter/judge domain ports.
2. AI GM adapter/DTO.
3. runtime orchestration/audit.
4. existing endpoint regression and E2E.
