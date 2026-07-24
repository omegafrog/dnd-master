# Plan 014: Dice Runtime Command Saga

## Status

Completed. Runtime command replay, idempotent dice execution, and persisted turn/roll command ids are in place.

## Issue

[GitHub #43](https://github.com/omegafrog/dnd-master/issues/43)

## Dependencies

- Plan 010
- Plan 013

## Spec Trace

- `REQ-RUN-011` through `REQ-RUN-017`
- `UC-006`
- `BR-012`, `BR-013`
- ADR-003

## Outcome

Runtime GM proposes a source-grounded roll, Adventure validates it, Dice service executes idempotently, and GM Finalization produces narration only after the result is authoritative.

## Implementation Scope

- Add RuntimePlan proposed-command schema and Resolution SourceRef.
- Add RuntimeCommand and Saga state machine/persistence.
- Extend Dice Roll command with session/turn/command IDs and idempotency.
- Add player/GM/engine roller handling.
- Add Runtime Finalization contract and authoritative result validation.
- Update AdventureStream with pending roll and final result flow.

## Acceptance Criteria

- Runtime GM cannot create a roll result.
- Same commandId returns the same Dice Result.
- Resolution trigger/DC/formula/source is validated before execution.
- Success/failure narration is impossible before Dice Result.
- Failed Finalization cannot alter authoritative state.
- Retry resumes the same Saga without another roll.

## Test Contract

- Policy unit: RuntimeCommand transitions, roller authorization, result gating, idempotency.
- Integration: Adventure-to-Dice HTTP contract, timeout/retry, persisted result reference.
- System: one failure after roll then Saga resume.
- UI-to-entity E2E: action -> pending roll -> Dice entity -> completed turn -> final narration.

## Excluded

- Character and map mutation.
