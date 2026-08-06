# Implementation Plans

Tracker: GitHub Issues — `omegafrog/dnd-master`

Parent: [#114 — AI GM Runtime](https://github.com/omegafrog/dnd-master/issues/114)

Status vocabulary: `pending` → `ready-for-agent` → `in-progress` → `completed`.

## 032 — AI GM Runtime

1. [032-1 Typed GM Turn lifecycle](032-1-typed-gm-turn-lifecycle.md) — [#115](https://github.com/omegafrog/dnd-master/issues/115) — `completed`
2. [032-2 Atomic commit and SSE projection](032-2-atomic-commit-and-sse-projection.md) — [#116](https://github.com/omegafrog/dnd-master/issues/116) — `completed`
3. [032-3 Provider-neutral GM agent loop](032-3-provider-neutral-gm-agent-loop.md) — [#117](https://github.com/omegafrog/dnd-master/issues/117) — `completed`
4. [032-4 Capability-scoped tool Saga](032-4-capability-scoped-tool-saga.md) — [#118](https://github.com/omegafrog/dnd-master/issues/118) — `completed`
5. [032-5 Story continuity, facts, and game clock](032-5-story-continuity-facts-and-game-clock.md) — [#119](https://github.com/omegafrog/dnd-master/issues/119) — `completed`
   - [032-5a Rulebook template and blueprint layering](032-5a-rulebook-template-and-blueprint-layering.md) — `completed`
6. [032-6 Bundle map compilation and activation](032-6-bundle-map-compilation-and-activation.md) — [#120](https://github.com/omegafrog/dnd-master/issues/120) — `completed`
7. [032-7 Confirmed grid-map interaction](032-7-confirmed-grid-map-interaction.md) — [#121](https://github.com/omegafrog/dnd-master/issues/121) — `completed`
8. [032-8 Fog of war and hidden tokens](032-8-fog-of-war-and-hidden-tokens.md) — [#122](https://github.com/omegafrog/dnd-master/issues/122) — `completed`
9. [032-9 GM context compaction and resume](032-9-gm-context-compaction-and-resume.md) — [#123](https://github.com/omegafrog/dnd-master/issues/123) — `completed`
10. [032-10 GM provider quality gate and full journey](032-10-gm-provider-quality-gate-and-full-journey.md) — [#124](https://github.com/omegafrog/dnd-master/issues/124) — `completed`

Dependency chain: `032-1 → 032-2 → 032-3 → 032-4 → 032-5a → 032-5`; `032-2 → 032-6`; `032-4 + 032-6 → 032-7`; `032-5 + 032-7 → 032-8`; `032-5 + 032-8 → 032-9`; all → `032-10`.

## 033 — GM Quality, RAG, and Fine-tuning Evaluation

Parent: [#125](https://github.com/omegafrog/dnd-master/issues/125)

1. [033-1 Reproducible GM quality baseline](033-1-gm-quality-baseline.md) — [#126](https://github.com/omegafrog/dnd-master/issues/126) — `pending`
2. [033-2 Retrieval evaluation corpus and metrics](033-2-retrieval-evaluation-corpus.md) — [#127](https://github.com/omegafrog/dnd-master/issues/127) — `pending`
3. [033-3 RAG A/B condition harness](033-3-rag-ab-evaluation.md) — [#128](https://github.com/omegafrog/dnd-master/issues/128) — `pending`
4. [033-4 Safe Story RAG visibility projection](033-4-safe-story-rag-visibility.md) — [#129](https://github.com/omegafrog/dnd-master/issues/129) — `pending`
5. [033-5 Hybrid retrieval and query decomposition](033-5-hybrid-retrieval-and-query-decomposition.md) — [#130](https://github.com/omegafrog/dnd-master/issues/130) — `pending`
6. [033-6 Reranking and context expansion](033-6-reranking-and-context-expansion.md) — [#131](https://github.com/omegafrog/dnd-master/issues/131) — `pending`
7. [033-7 Grounding hard gate and degraded mode](033-7-grounding-hard-gate.md) — [#132](https://github.com/omegafrog/dnd-master/issues/132) — `pending`
8. [033-8 Deterministic adjudication and narrative separation](033-8-deterministic-adjudication.md) — [#133](https://github.com/omegafrog/dnd-master/issues/133) — `pending`
9. [033-9 Fine-tuning experiment and decision report](033-9-finetuning-decision.md) — [#134](https://github.com/omegafrog/dnd-master/issues/134) — `pending`

Dependency chain: `033-1 → 033-2 → 033-3`; `033-2 → 033-4`; `033-2 + 033-4 → 033-5 → 033-6`; `033-3 + 033-4 → 033-7 → 033-8`; `033-3 + 033-7 + 033-8 → 033-9`.
