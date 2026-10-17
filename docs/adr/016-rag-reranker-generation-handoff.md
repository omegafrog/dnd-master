# ADR-016: Reranker and generation handoff seam

> **Historical decision:** The hybrid retrieval flow described below was superseded. Runtime rule-knowledge retrieval now uses dense vector similarity only; BM25 and rank fusion are not part of runtime retrieval.

## Decision

RAG-006 uses an injected offline `RerankerPort`. For the hybrid evidence-selection flow, reranking receives the deduplicated candidate pool and returns at most 30 candidates to the evidence-sufficiency judge. It does not apply a score threshold or choose the final generation evidence. Reranking metrics are calculated separately from retrieval metrics so parent context cannot change retrieval gold IDs. For ordinary player-action turns, [ADR-023](ADR-023-runtime-gm-input-context.md) narrows the handoff to at most 20 reranked candidates after up to 40 candidates per independent retrieval request; other task policies retain this document's 30-candidate limit.

Generation receives only the arbitrary subset selected by the evidence-sufficiency judge, rather than a fixed top-five prefix. The handoff keeps bounded context items, source citation, section path, source locator, and explicit `retrieved`/`parent` provenance. The evaluator chunk ID is the stable local identity. An optional ACL mapping may add a Java UUID or locator; the offline evaluator remains independent from the production Java adapter.

## Consequences

The reranker output is bounded at 30 while the final handoff size varies with the smallest sufficient evidence subset. Citation metadata remains available without coupling the evaluator to Java persistence. Retrieval, reranking, evidence sufficiency, and evidence redundancy remain separately measurable.
