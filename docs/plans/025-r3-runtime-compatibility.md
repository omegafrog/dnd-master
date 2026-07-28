# 025-R3 - Runtime compatibility and migration hardening

- Status: approved
- Issue: [#78](https://github.com/omegafrog/dnd-master/issues/78)
- Parent: [025](025-session-knowledge-set-retrieval.md)
- Original issue: [#25](https://github.com/omegafrog/dnd-master/issues/25)
- Dependencies: 025-R1, 025-R2

## Goal

Harden compatibility while making `SessionKnowledgeSet` the single retrieval scope.

## Acceptance

- Legacy runtime binding reads remain supported.
- Duplicate scope ownership has an explicit deprecation boundary.
- Empty-set read semantics are explicit and consistent.
- Existing sessions survive migration and continue to retrieve only authorized documents.

## Test contract

- Old-session restart and legacy binding read tests.
- Migration compatibility test.
- `ui ~ entity` end-to-end: document selection → session persistence → runtime retrieval.

## Implementation scope

Compatibility adapter, migration/schema hardening, empty-set behavior, and full regression coverage.
