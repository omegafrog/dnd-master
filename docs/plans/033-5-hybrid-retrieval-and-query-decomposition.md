# 033-5 — Hybrid Retrieval And Query Decomposition

- Status: `pending`
- Issue: [#130](https://github.com/omegafrog/dnd-master/issues/130)
- Parent: [#125](https://github.com/omegafrog/dnd-master/issues/125)
- Dependencies: [033-2](033-2-retrieval-evaluation-corpus.md), [033-4](033-4-safe-story-rag-visibility.md)
- Spec: GM quality/RAG/fine-tuning plan §Phase 4.1–4.3

## Outcome

Rule and story retrieval use explicit scope filters and decomposed intents with normalized hybrid candidates.

## Vertical Scope

- Combine dense and BM25/keyword search.
- Enforce owner, session, package, document type, extraction version, visibility, stage, and active locator.
- Decompose action into rules, scene, NPC, combat, resources, and continuity intents.
- Merge, normalize, and deduplicate candidates.
- Define safe empty-result behavior.

## Policy Unit Tests

- Every scope dimension is enforced before ranking.
- Query decomposition is deterministic for fixed input.
- Scope mismatch and secret candidates are rejected.

## Integration And Contract Tests

- Rule/story adapters expose common candidate metadata and scores.
- Retrieval failure produces explicit degraded result, never silent unrestricted evidence.

## UI ~ Entity E2E

Multi-intent player action → scoped evidence pack → GM response; verify rule, scene, and continuity evidence remain correctly separated.

## Implementation Scope

Search ports, SQL/vector adapters, contracts, metadata filters, intent decomposition, metrics, and tests.

## Out Of Scope

Reranker implementation and context expansion.

## Completion

Current RAG approaches Oracle quality while retaining zero scope/secret violations and latency budget.
