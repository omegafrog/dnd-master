# Plan 011: Resolution Override Recompile

## Status

Completed.

## Issue

[GitHub #40](https://github.com/omegafrog/dnd-master/issues/40)

## Dependencies

- Plan 009

## Spec Trace

- `REQ-PKG-008` through `REQ-PKG-013`
- `UC-004`, `UC-008`
- `BR-015`, `BR-016`

## Outcome

Owner corrects a Resolution Unit without changing SourceSpan truth, recompiles, and receives automatic reapplication or an explicit anchor conflict.

## Implementation Scope

- Add Resolution Override aggregate, revision, audit, and persistence.
- Add composite anchor using document/content/quote/context/locator/Unit fingerprints.
- Add exact reapply and conflict policies.
- Add review/edit/conflict-resolution UI.
- Recompile to a new Package Version while preserving prior versions.

## Acceptance Criteria

- User Override wins over automatic extraction for the target Package.
- Exact single anchor match reapplies automatically.
- Missing or multiple candidates produce a conflict and do not auto-apply.
- Changed source meaning cannot silently inherit an old correction.
- Override author, reason, timestamps, and prior revisions remain auditable.

## Test Contract

- Policy unit: anchor matching, conflict classification, override precedence, revision history.
- Integration: persistence and recompile across changed Extraction Versions.
- Contract: review/override/conflict APIs.
- UI-to-entity E2E: edit Resolution -> persisted Override -> new Package -> conflict/reapplied state.

## Excluded

- Direct SourceSpan editing.
- Runtime Package switch.
