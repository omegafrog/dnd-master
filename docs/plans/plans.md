# GM Quality Hardening Tracker

Tracker mode: local Markdown. 외부 Issue는 생성하지 않는다.

| ID | Ticket | Status | Dependencies |
|---|---|---|---|
| 034-1 | [Secret-safe model context](034-1-secret-safe-model-context.md) | completed | - |
| 034-2 | [Deterministic GM response gate](034-2-deterministic-gm-response-gate.md) | completed | 034-1 |
| 034-3 | [Bounded provider latency](034-3-bounded-provider-latency.md) | completed | - |
| 034-4 | [Production retrieval quality run](034-4-production-retrieval-quality-run.md) | completed | 034-1 |
| 034-5 | [RAG A/B and human evaluation](034-5-rag-ab-human-evaluation.md) | completed | 034-2, 034-3, 034-4 |
| 034-6 | [Fine-tuned holdout decision](034-6-finetuned-holdout-decision.md) | completed | 034-5 |

## Release gates

- Rule Recall@5 >= 95%, Story Recall@5 >= 90%, retrieval p95 <= 500 ms.
- Structure success >= 99%, citation accuracy >= 95%, human score >= 4.0/5.0.
- Secret leak, invented state, scope violation rates must each equal 0%.
- Current RAG must significantly outperform No RAG without safety or latency regression.
- Fine-tuned model must significantly outperform baseline on frozen holdout without safety, latency, or cost regression.

## 035 GM adventure quality acceptance

| ID | Ticket | Status | Dependencies |
|---|---|---|---|
| 035-1 | [Typed GM response and recovery](035-1-typed-gm-response-recovery.md) | planned | - |
| 035-2 | [Player projection and secret safety](035-2-player-projection-secret-safety.md) | planned | 035-1 |
| 035-3 | [Authoritative rules, dice, and combat](035-3-authoritative-rules-dice-combat.md) | planned | 035-1, 035-2 |
| 035-4 | [Runtime readiness and preflight](035-4-runtime-readiness-preflight.md) | planned | 035-1 |
| 035-5 | [UI five-turn and combat acceptance](035-5-ui-five-turn-combat-acceptance.md) | planned | 035-1, 035-2, 035-3, 035-4 |
