# Runtime Narrative Generation Plan Index

Parent issue: [#225](https://github.com/omegafrog/dnd-master/issues/225)

| Plan | Source issue | Status | Depends on | Vertical outcome |
|---|---:|---|---|---|
| [RN-001](RN-001-runtime-narrative-state.md) | [#226](https://github.com/omegafrog/dnd-master/issues/226) / #206 | `ready-for-agent` | #218, #219 | Typed Runtime Narrative State, epistemic-safe projection, validated State Delta commit. |
| [RN-002](RN-002-best-of-n-planning.md) | [#227](https://github.com/omegafrog/dnd-master/issues/227) / #208 | `ready-for-agent` | RN-001 | Configurable compact plan candidates, hard filter, judge, selected-plan handoff. |
| [RN-003](RN-003-narrative-verifier.md) | [#228](https://github.com/omegafrog/dnd-master/issues/228) / #207 | `completed` | RN-001, RN-002 | Structured final verification and one bounded same-turn rewrite. |
| [RN-004](RN-004-style-exemplar-retrieval.md) | [#229](https://github.com/omegafrog/dnd-master/issues/229) / #209 | `ready-for-agent` | RN-003 | Separate style exemplar retrieval, provenance-safe writer context. |

## Dependency decision

RN-001 is executable because completed #218/#219 provide resolved-turn and writer seams. RN-002 depends on actor-safe runtime context. RN-003 depends on selected plans and writer delivery. RN-004 depends on verifier admission rules and the final writer handoff.
