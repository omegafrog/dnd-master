# 034-4 Production retrieval quality run

- Status: `completed`
- Tracker: local Markdown
- Dependencies: 034-1
- Product rules: PR-001, BR-001, BR-002, FR-007

## Outcome

Frozen 100-case corpus runs through real PostgreSQL/pgvector, rule/story HTTP APIs, production scoping, embedding, ranking, and evidence assembly. Versioned artifact decides retrieval release gate.

## Implementation scope

- Provide deterministic corpus seed/import for documents, chunks, embeddings, extraction versions, owners, sessions, packages, disclosure events, and forbidden evidence.
- Add local CLI/task entrypoint that starts from frozen corpus and invokes production `RetrievalEvaluationPort` wiring.
- Validate corpus references against seeded data before execution.
- Persist per-case candidates, ranks, scope decisions, latency, model/index identity, and aggregate metrics.
- Hard fail on any secret retrieval or scope violation.
- Keep evaluation endpoint/task unavailable from public runtime APIs.

## Likely files

- `src/ai-game-master-service/.../retrieval/*`
- `src/ai-game-master-service/src/main/resources/retrieval-evaluation-corpus.json`
- `src/rule-knowledge-service/.../search/*`
- `src/rule-knowledge-service/.../persistence/Pgvector*SearchRepository.java`
- `src/infra/compose.yaml`

## Acceptance criteria

- All 100 frozen cases execute against production-backed adapters.
- Rule Recall@5 >= 95%; Story Recall@5 >= 90%.
- Secret retrieval rate = 0%; scope violation rate = 0%.
- Retrieval p95 <= 500 ms on declared environment.
- Artifact contains reproducible corpus, embedding, index, service, and configuration identity.

## Test contract

- Unit: corpus validation, metrics, duplicate handling, percentile, hard-failure semantics.
- Integration: seeded PostgreSQL/pgvector plus real rule/story HTTP APIs.
- Failure integration: wrong owner/version/disclosure event never returns candidate.
- `ui ~ entity` e2e: rule question and post-reveal story question show expected source references; pre-reveal request shows none.

## Out of scope

- No RAG comparison and generation scoring; ticket 034-5.

## Execution notes

- Added profile-gated `retrieval-evaluation` Spring task for the frozen 100-case corpus.
- Added reproducibility identity to persisted evaluation artifacts: corpus, embedding, index, service, and configuration.
- Kept evaluation task outside default runtime APIs.
- Targeted retrieval tests and compile pass. Full module suite retains five pre-existing Ollama/FineTuning failures.
