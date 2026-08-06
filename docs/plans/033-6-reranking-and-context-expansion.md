# 033-6 — Reranking And Context Expansion

- Status: `pending`
- Issue: [#131](https://github.com/omegafrog/dnd-master/issues/131)
- Parent: [#125](https://github.com/omegafrog/dnd-master/issues/125)
- Dependencies: [033-5](033-5-hybrid-retrieval-and-query-decomposition.md)
- Spec: GM quality/RAG/fine-tuning plan §Phase 4.4–4.5

## Outcome

Hybrid top-20 candidates become a small, diverse, context-complete evidence pack resilient to distractors.

## Vertical Scope

- Add cross-encoder, local, or pointwise reranking adapter seam.
- Select diverse top 3–5 candidates.
- Expand adjacent paragraphs and restore conditional/exception text.
- Limit same-document duplicates and preserve evidence-type diversity.

## Policy Unit Tests

- Reranking is deterministic for fixed candidates.
- Context expansion cannot cross document/version/scope boundaries.
- Diversity and maximum chunk constraints are enforced.

## Integration And Contract Tests

- Reranker timeout/failure falls back to safe pre-rank results.
- Evidence pack records candidate, rerank, and expansion provenance.

## UI ~ Entity E2E

Ambiguous player action with distractors → reranked evidence pack → grounded GM response; verify correct rule/story context.

## Implementation Scope

Reranker port/adapters, context expansion, evidence-pack assembly, observability, latency tests.

## Out Of Scope

Grounding hard gate and model fine-tuning.

## Completion

Distractor robustness improves, Current RAG nears Oracle, and retrieval p95 remains within budget.
