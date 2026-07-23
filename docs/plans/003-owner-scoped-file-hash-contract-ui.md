# P3: Owner-Scoped File Hash Contract And UI Regression Coverage

## Status

Completed

## Goal

Keep API and UI behavior aligned with owner-scoped dedupe without breaking existing upload flows.

## Scope

- Update rule-knowledge API contract and tests where duplicate semantics are visible.
- Add regression coverage for same-owner reupload behavior.
- Keep batch upload UI request replay behavior intact.
- Ensure e2e covers duplicate upload path from the user view.

## Dependencies

- P1 owner-scoped file hash dedupe core.
- P2 owner-scoped file hash persistence.

## Acceptance Criteria

- API reflects duplicate reuse behavior for same owner + same file.
- UI does not create a false second document for same-owner reupload.
- Different-owner same-file behavior remains independent.
- Existing batch upload and retry flows still work.

## Test Contract

- Contract test for duplicate reuse semantics.
- UI unit test for repeated upload with same content.
- `ui ~ entity` e2e test for reupload path.

## Implementation Notes

- No new UX required unless duplicate result needs clearer messaging.
- Keep idempotency key generation separate from file identity.
