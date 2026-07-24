# Plan 015: Character/Map Command Saga

## Status

Completed.

## Issue

[GitHub #44](https://github.com/omegafrog/dnd-master/issues/44)

## Dependencies

- Plan 014

## Spec Trace

- `REQ-RUN-015`, `REQ-RUN-016`, `REQ-RUN-020` through `REQ-RUN-024`
- `UC-007`
- `BR-012` through `BR-014`, `BR-019`
- ADR-003

## Outcome

A Runtime Turn applies idempotent, versioned character and map effects, recovers from partial service failure, records origin, and finalizes only after required effects commit.

## Implementation Scope

- Add common runtime command envelope to Character and Combat Map services.
- Add commandId idempotency and expected-version validation.
- Add HP, inventory, effect, resource, and movement command adapters.
- Extend Saga with multiple ordered remote steps and resume.
- Record SOURCE_AUTHORED, RUNTIME_ADJUDICATED, ENGINE_RESULT origins.
- Support out-of-source adjudication without modifying Scenario Package.
- Refresh character/map UI from authoritative services.

## Acceptance Criteria

- Same commandId never applies a remote effect twice.
- Version conflict reloads state and forces replanning.
- Partial failure cannot publish successful final narration.
- Already recorded Dice Result remains auditable but unapplied when later step fails.
- Session state wins over source initial state after committed changes.
- Runtime-created facts never mutate Scenario Package.

## Test Contract

- Policy unit: multi-step Saga, version conflict, origin classification, resume/reject.
- Integration: Character/Map idempotency and optimistic locking.
- System: dice succeeds, character update fails transiently, resume completes once.
- UI-to-entity E2E: player action -> remote state entities change -> refreshed HP/map -> final response.

## Excluded

- New authoritative state types beyond approved character/map/session scope.
