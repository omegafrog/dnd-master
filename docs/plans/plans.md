# GM Quality Hardening Tracker

Tracker mode: local Markdown. 외부 Issue는 생성하지 않는다.

| ID | Ticket | Status | Dependencies |
|---|---|---|---|
| 034-1 | [Secret-safe model context](034-1-secret-safe-model-context.md) | completed | - |
| 034-2 | [Deterministic GM response gate](034-2-deterministic-gm-response-gate.md) | completed | 034-1 |
| 034-3 | [Bounded provider latency](034-3-bounded-provider-latency.md) | completed | - |
| 034-4 | [Production retrieval quality run](034-4-production-retrieval-quality-run.md) | completed | 034-1 |
| 034-5 | [RAG A/B and human evaluation](034-5-rag-ab-human-evaluation.md) | blocked | 034-2, 034-3, 034-4 |
| 034-6 | [Fine-tuned holdout decision](034-6-finetuned-holdout-decision.md) | blocked | 034-5 |

## Release gates

- Rule Recall@5 >= 95%, Story Recall@5 >= 90%, retrieval p95 <= 500 ms.
- Structure success >= 99%, citation accuracy >= 95%, human score >= 4.0/5.0.
- Secret leak, invented state, scope violation rates must each equal 0%.
- Current RAG must significantly outperform No RAG without safety or latency regression.
- Fine-tuned model must significantly outperform baseline on frozen holdout without safety, latency, or cost regression.
