# RAG Retrieval Evaluation Plan Index

| Plan | Status | Dependencies | Scope |
|---|---|---|---|
| [RAG-001 Gold Validation](rag-001-gold-validation.md) | `completed` | none | 50-case schema, chunk/evidence validation, gold report |
| [RAG-002 Retrieval Contract](rag-002-retrieval-contract.md) | `ready-for-agent` | RAG-001 | top-20 ranked contract, metrics, offline evaluator harness |
| [RAG-003 Dense Baseline](rag-003-dense-baseline.md) | `planned` | RAG-002 | injected Dense adapter and baseline artifacts |
| [RAG-004 BM25 Baseline](rag-004-bm25-baseline.md) | `planned` | RAG-002 | BM25 adapter and baseline artifacts |
| [RAG-005 Hybrid Comparison](rag-005-hybrid-comparison.md) | `planned` | RAG-003, RAG-004 | RRF, comparison, failure taxonomy |
| [RAG-006 Reranker and Context](rag-006-reranker-context.md) | `planned` | RAG-005 | reranker, parent context, generation handoff |
| [RAG-007 Generation Abstention](rag-007-generation-abstention.md) | `planned` | RAG-006 | generation, citation, unanswerable evaluation |
