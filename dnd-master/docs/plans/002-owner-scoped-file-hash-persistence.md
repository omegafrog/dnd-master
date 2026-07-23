# P2: Owner-Scoped File Hash Persistence

## Status

Completed

## Goal

Enforce owner-scoped file-hash uniqueness in persistence so duplicate uploads cannot race into two stored rows.

## Scope

- Update `PostgresRulebookRegistrationRepository`.
- Add lookup/query support for owner + content hash.
- Change conflict target away from `operation_key` as identity.
- Update migration and constraint/index strategy for `(owner_player_id, content_hash)`.

## Dependencies

- P1 owner-scoped file hash dedupe core.

## Acceptance Criteria

- Repository can find existing registration by owner + hash.
- DB prevents two rows with same owner + hash.
- Concurrent same-owner same-file uploads do not create duplicates.
- Request replay by `operationKey` still behaves separately from file identity.

## Test Contract

- Integration test for owner + hash unique enforcement.
- Integration test for same-owner duplicate replay returning existing registration.
- Integration test for concurrent insert race.

## Implementation Notes

- Treat current `content_hash` as raw-byte SHA-256.
- Add backfill/migration guard if existing hash semantics differ.
- Preserve existing ownership queries.
