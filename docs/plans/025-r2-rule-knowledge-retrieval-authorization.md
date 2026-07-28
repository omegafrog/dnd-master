# 025-R2 - Rule-knowledge retrieval authorization

- Status: approved
- Issue: [#77](https://github.com/omegafrog/dnd-master/issues/77)
- Parent: [025](025-session-knowledge-set-retrieval.md)
- Original issue: [#25](https://github.com/omegafrog/dnd-master/issues/25)
- Dependencies: 025-R1
- Blocks: 025-R3

## Goal

Enforce session-bounded document authorization across the adventure → rule-knowledge boundary.

## Acceptance

- Adventure sends an explicit scoped retrieval request.
- Foreign and unindexed documents are rejected.
- STORYBOOK and RULEBOOK retrieval use the same session scope.
- Caller-supplied document IDs cannot expand authorization.

## Test contract

- Policy and adapter/API integration tests: foreign, unindexed, and injected IDs.
- `ui ~ entity` regression: removing a selected document blocks later retrieval.

## Implementation scope

Cross-context retrieval port/adapter, rule-knowledge request authorization, query scope propagation, and integration tests.
