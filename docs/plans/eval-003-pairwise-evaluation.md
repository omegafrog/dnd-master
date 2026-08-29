# EVAL-003: Pairwise GM response evaluation

Status: `planned`

Parent: #204

Issue: #216

Dependencies: EVAL-001, EVAL-002

## 구현 목적

동일 EvalCase에서 나온 baseline과 candidate GM 응답을 비교해 A/B/TIE와 dimension별 preference·근거를 반환한다. 모델·프롬프트·TurnPlan 등 이후 변경의 상대 품질을 비교 가능하게 만든다.

## Scope

- Define pairwise request/result, `PairwiseJudgePort`, winner enum, per-dimension preference/evidence.
- Reuse validated EvalCase and absolute results; reject non-identical case identity/version comparisons.
- Provide structured pairwise judge adapter and fail-closed validation.
- Preserve hard constraint outcomes separately from overall preference.

## Acceptance Criteria

- [ ] Same case A/B response produces `A`, `B`, or `TIE`, applicable dimension preferences, reason/evidence.
- [ ] Different case ID/version A/B comparison is rejected.
- [ ] Malformed judge reply is explicit pairwise judge failure, not a winner.
- [ ] A prose preference does not erase either response's hard constraint failures.

## Test Contract

- Policy unit tests: same-case guard; each winner; dimension preference validation; malformed result; hard-result independence.
- UI ~ entity e2e: pairwise input JSON -> application service -> fake judge -> serialized pairwise result fixture.
- Adapter contract tests: pairwise structured output parser accepts canonical response and rejects extra/missing dimensions.

## Out of Scope

- Dataset-wide aggregate rates and persisted run report.
