# 034-5 RAG A/B and human evaluation

- Status: `completed`
- Tracker: local Markdown
- Dependencies: 034-2, 034-3, 034-4
- Product rules: PR-003, PR-008, BR-002, BR-006, BR-026

## Outcome

Same frozen cases and model configuration run under No RAG, Current RAG, Oracle, and Distractor conditions. Automated and blind-human results determine whether Current RAG significantly improves service quality.

## Implementation scope

- Add production execution entrypoint for `RagAbRunner` using real Current RAG and provider adapters.
- Freeze case order, generation settings, model digest, repetitions, and random seeds where supported.
- Replace request-supplied `humanScore` with post-response blind reviewer records.
- Score structure, rule accuracy, citation accuracy, state consistency, continuity, hallucination, secret leak, latency, and cost.
- Use paired bootstrap or permutation testing with confidence interval and minimum effect threshold.
- Persist raw outputs and reviewer provenance separately from aggregate artifact.

## Likely files

- `src/ai-game-master-service/.../benchmark/rag/*`
- `src/ai-game-master-service/.../benchmark/GmAdapterBenchmarkExecutor.java`
- `src/ai-game-master-service/.../api/GmQualityEvaluationService.java`
- `src/ai-game-master-service/.../retrieval/*`

## Acceptance criteria

- Four conditions execute complete identical case/repetition matrices.
- Current RAG significantly outperforms No RAG on primary quality score.
- Citation, continuity, structure, human score, safety, latency, and cost meet release thresholds with no safety regression.
- Human narration score mean >= 4.0/5.0 from blind post-response ratings.
- Artifact reports effect size, confidence interval, p-value/test method, exclusions, and bottleneck classification.

## Test contract

- Unit: condition isolation, paired statistics, incomplete matrices, reviewer validation.
- Integration: real Current RAG evidence and provider response for all four conditions.
- Regression: protected facts and forbidden evidence remain absent in every condition except evaluator-owned oracle metadata.
- `ui ~ entity` e2e: selected winning configuration serves a grounded turn with source links while persisted entity state matches deterministic adjudication.

## Out of scope

- Fine-tuned model decision; ticket 034-6.

## Execution notes

- Added blind reviewer records with score/provenance validation and reviewer-matrix completeness checks.
- Added deterministic paired permutation significance testing plus bootstrap confidence intervals for Current RAG vs No RAG.
- Added primary release gates for citation, structure, human score, safety, and latency; report persists effect and statistical fields.
- Added separate raw-response and reviewer-provenance artifact store.
