# TT-007: Approved Prompt And Model Runtime Lineage

- Issue: [#244](https://github.com/omegafrog/dnd-master/issues/244)
- Status: `completed`
- Dependencies: TT-003 `completed`
- Parent: [#237](https://github.com/omegafrog/dnd-master/issues/237)

## 구현 목적

승인된 gm-eval registry artifact를 planner, resolver, writer, verifier runtime role에 연결한다. 실제 사용한 prompt/model/run lineage를 RuntimeTurn artifact와 diagnostics에 보존한다.

## Implementation Scope

- approved role configuration read port
- AI provider router와 runtime role별 config 연결
- `EffectivePromptLineage` 확장 및 failure/presentation artifact 연결
- diagnostics projection과 legacy unknown compatibility
- cache invalidation/version monotonicity

## Acceptance Criteria

- unapproved prompt/model candidate runtime 사용 불가
- 각 role의 effective prompt/model/eval/run lineage 조회 가능
- legacy row는 추측 대신 explicit unknown
- Eval/tuning execution은 synchronous player path 밖에 유지

## Test Contract

- Policy unit: approved-only selection, stale activation, role isolation
- Adapter integration: registry → provider config → persisted lineage
- UI/API ~ entity E2E: approved config로 turn 실행 → authenticated diagnostics lineage 확인

## Implementation Boundaries

- TT-003 canonical RuntimeTurn/failure artifact 사용
- 기존 `gm-eval-service` gate/approval 구현 재사용

## Implementation Evidence

- Added approved runtime configuration read port, exact-role selection policy, local monotonic activation adapter, and dataset/eval lineage fields.
- Resolved artifacts now retain per-role lineages for planner, judge, writer, and verifier when the approved configuration adapter is configured; presentation retry carries the same immutable map.
- Diagnostics exposes both the legacy primary lineage and the role-keyed lineage projection; legacy rows remain explicit unknown (`null`) rather than inferred.
- Verification: `ApprovedPromptSelectionPolicyTest`, `ResolvedTurnLifecycleTest`, runtime diagnostics tests, and adventure-service compilation.
