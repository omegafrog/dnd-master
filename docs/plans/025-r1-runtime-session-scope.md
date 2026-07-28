# 025-R1 - Runtime session scope policy

- Status: completed
- Issue: [#76](https://github.com/omegafrog/dnd-master/issues/76)
- Parent: [025](025-session-knowledge-set-retrieval.md)
- Original issue: [#25](https://github.com/omegafrog/dnd-master/issues/25)
- Dependencies: none
- Blocks: 025-R2

## Goal

Make persisted `SessionKnowledgeSet` the runtime evidence scope.

## Acceptance

- Runtime evidence requests carry and validate `sessionId`.
- Retrieval loads selected documents through `SessionKnowledgeSetRepository`.
- Persisted session scope wins over `RuntimeBinding.rulebookIds`.
- Missing or invalid session scope fails closed.
- Selection changes affect subsequent retrieval.

## Test contract

- Policy unit tests: scope changes, excluded documents, missing session.
- `ui ~ entity` test: select documents → save session → submit turn → assert retrieval scope.

## Implementation scope

Runtime evidence request/application flow, session knowledge repository seam, runtime binding scope resolution, and focused regression tests.
