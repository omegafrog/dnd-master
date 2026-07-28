# 025 - Session knowledge set retrieval authorization

- Status: approved
- Issue: [#75](https://github.com/omegafrog/dnd-master/issues/75)
- Original issue: [#25](https://github.com/omegafrog/dnd-master/issues/25)
- Slices: [025-R1](025-r1-runtime-session-scope.md), [025-R2](025-r2-rule-knowledge-retrieval-authorization.md), [025-R3](025-r3-runtime-compatibility.md)

## Outcome

Persisted `SessionKnowledgeSet` becomes the sole authorization scope for runtime document retrieval.

## Acceptance

- Session document selection controls later runtime retrieval.
- Documents outside the persisted session selection cannot provide evidence.
- Owner and indexed-state checks remain enforced at the boundary.
- Legacy runtime reads remain compatible during migration.

## Dependency

R1 → R2 → R3.
