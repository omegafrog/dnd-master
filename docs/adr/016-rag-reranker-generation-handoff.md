# ADR-016: Reranker and generation handoff seam

## Decision

RAG-006 uses an injected offline `RerankerPort`. It receives the first 30 Hybrid candidates and returns at most five candidates. Reranking metrics are calculated separately from retrieval metrics so parent context cannot change retrieval gold IDs.

Generation receives a `GenerationHandoff` containing bounded context items, source citation, section path, source locator, and explicit `retrieved`/`parent` provenance. The evaluator chunk ID is the stable local identity. An optional ACL mapping may add a Java UUID or locator; no live Java adapter is required for offline evaluation.

## Consequences

The handoff is deterministic and bounded, and citation metadata remains available without coupling the evaluator to Java persistence or an LLM generation policy. Generation and abstention decisions remain outside this slice.
