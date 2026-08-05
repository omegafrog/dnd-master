# Implementation Plans

Tracker: GitHub Issues — `omegafrog/dnd-master`

Parent: [#114 — AI GM Runtime](https://github.com/omegafrog/dnd-master/issues/114)

Status vocabulary: `pending` → `ready-for-agent` → `in-progress` → `completed`.

## 032 — AI GM Runtime

1. [032-1 Typed GM Turn lifecycle](032-1-typed-gm-turn-lifecycle.md) — [#115](https://github.com/omegafrog/dnd-master/issues/115) — `completed`
2. [032-2 Atomic commit and SSE projection](032-2-atomic-commit-and-sse-projection.md) — [#116](https://github.com/omegafrog/dnd-master/issues/116) — `completed`
3. [032-3 Provider-neutral GM agent loop](032-3-provider-neutral-gm-agent-loop.md) — [#117](https://github.com/omegafrog/dnd-master/issues/117) — `in-progress`
4. [032-4 Capability-scoped tool Saga](032-4-capability-scoped-tool-saga.md) — [#118](https://github.com/omegafrog/dnd-master/issues/118) — `pending`
5. [032-5 Story continuity, facts, and game clock](032-5-story-continuity-facts-and-game-clock.md) — [#119](https://github.com/omegafrog/dnd-master/issues/119) — `pending`
6. [032-6 Bundle map compilation and activation](032-6-bundle-map-compilation-and-activation.md) — [#120](https://github.com/omegafrog/dnd-master/issues/120) — `pending`
7. [032-7 Confirmed grid-map interaction](032-7-confirmed-grid-map-interaction.md) — [#121](https://github.com/omegafrog/dnd-master/issues/121) — `pending`
8. [032-8 Fog of war and hidden tokens](032-8-fog-of-war-and-hidden-tokens.md) — [#122](https://github.com/omegafrog/dnd-master/issues/122) — `pending`
9. [032-9 GM context compaction and resume](032-9-gm-context-compaction-and-resume.md) — [#123](https://github.com/omegafrog/dnd-master/issues/123) — `pending`
10. [032-10 GM provider quality gate and full journey](032-10-gm-provider-quality-gate-and-full-journey.md) — [#124](https://github.com/omegafrog/dnd-master/issues/124) — `pending`

Dependency chain: `032-1 → 032-2 → 032-3 → 032-4 → 032-5`; `032-2 → 032-6`; `032-4 + 032-6 → 032-7`; `032-5 + 032-7 → 032-8`; `032-5 + 032-8 → 032-9`; all → `032-10`.
