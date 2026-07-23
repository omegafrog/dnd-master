# P1: Owner-Scoped File Hash Dedupe Core

## Status

Completed

## Goal

Make duplicate judgment use raw file SHA-256, scoped by `ownerPlayerId`, while keeping `operationKey` as request replay metadata.

## Scope

- Update upload registration logic in `rule-knowledge-service`.
- Compute hash from raw uploaded bytes only.
- Reuse existing document state for same owner + same hash.
- Keep different owners independent even when bytes match.
- Keep same-owner different-bytes uploads as new documents.

## Dependencies

- None.

## Acceptance Criteria

- Same owner + same bytes returns same document state.
- Different owner + same bytes creates separate documents.
- Same owner + changed bytes creates a new document.
- Same `operationKey` + different bytes still conflicts.
- Hash input excludes filename, format, and document type.

## Test Contract

- Unit tests for SHA-256 helper over raw bytes.
- Unit tests for owner-scoped duplicate detection.
- Unit tests for idempotency-key conflict still intact.

## Implementation Notes

- Prefer one shared hash helper in rule-knowledge upload flow.
- Do not change UI behavior in this slice.
- Do not touch schema yet.
