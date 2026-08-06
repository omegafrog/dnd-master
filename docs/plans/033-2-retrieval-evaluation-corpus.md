# 033-2 — Retrieval Evaluation Corpus And Metrics

- Status: `pending`
- Issue: [#127](https://github.com/omegafrog/dnd-master/issues/127)
- Parent: [#125](https://github.com/omegafrog/dnd-master/issues/125)
- Dependencies: [033-1](033-1-gm-quality-baseline.md)
- Spec: GM quality/RAG/fine-tuning plan §Phase 1

## Outcome

Retrieval quality is measured independently from generation quality across a 100-case relevance corpus.

## Vertical Scope

- Add rule, scene, NPC continuity, combat, long-memory, and secrecy cases.
- Record owner/session/package, expected document/locator, acceptable alternatives, evidence type, and forbidden chunks.
- Measure Recall@1/@5, Precision@5, MRR, nDCG@5, secret retrieval, scope violations, and retrieval p50/p95.

## Policy Unit Tests

- Relevance labels accept only declared alternatives.
- Secret and scope violations are always hard failures.
- Metric calculations match hand-worked fixtures.

## Integration And Contract Tests

- Rule/story retrieval contracts return document, locator, version, score, and scope metadata.
- Corpus runner records retrieval candidates and latency without invoking generation.

## UI ~ Entity E2E

Scoped player action → retrieval request → evidence candidates; verify expected chunk appears and forbidden chunk never crosses owner/session/package scope.

## Implementation Scope

Corpus schema/assets, retrieval evaluation runner, metric library, rule/story adapters, reports, and test fixtures.

## Out Of Scope

Reranking and retrieval algorithm changes.

## Completion

100 cases run reproducibly; target Rule Recall@5 ≥95%, Story Recall@5 ≥90%, zero secret/scope violations, retrieval p95 ≤500ms.
