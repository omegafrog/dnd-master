# TT-003: Typed GM Failure Artifacts On Canonical RuntimeTurn

- Issue: [#240](https://github.com/omegafrog/dnd-master/issues/240)
- Status: `completed`
- Dependencies: 없음
- Parent: [#237](https://github.com/omegafrog/dnd-master/issues/237)

## 구현 목적

GM 실패를 stage, code, retryability, root-cause class, correlation ID로 보존한다. `RuntimeTurn`을 canonical lifecycle로 사용하고 player-safe 오류와 내부 diagnostics를 분리한다.

## Implementation Scope

- `RuntimeTurnFailureArtifact`, failure stage/code, classifier, append repository
- `RuntimeTurnFailurePersistence` 확장과 legacy `GmTurnFailureRecorder` compatibility mapping
- canonical RuntimeTurn routing과 legacy row/JSON reader 유지
- diagnostics redaction 및 typed projection
- transient provider failure 최대 1회 정책

## Acceptance Criteria

- 모든 GM 실패에 typed stage/code 존재
- timeout/unavailable만 자동 1회 retryable
- validation/citation/safety/conflict는 자동 retry 없음
- public API는 stable safe code만 노출
- legacy runtime rows와 public turn response 호환

## Test Contract

- Policy unit: source failure → stage/code/retry matrix
- Persistence integration: append/read, correlation, legacy projection
- UI/API ~ entity E2E: provider failure → public error + authenticated internal diagnostics

## Implementation Boundaries

- Meaningful Progress와 presentation retry endpoint는 TT-004
- raw provider body, hidden facts, exception message 비노출

## Completion Evidence

- Added typed failure artifact, stage/code enums, retry classifier, append/read repository, and V32 migration.
- Runtime presentation/safety failures now append redacted artifacts in an independent transaction.
- Diagnostics projection exposes typed artifacts while preserving legacy turn projection.
- Verification: targeted classifier, persistence, diagnostics, and runtime lifecycle tests passed.
