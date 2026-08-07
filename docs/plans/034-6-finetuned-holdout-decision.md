# 034-6 Fine-tuned holdout decision

- Status: `completed`
- Tracker: local Markdown
- Dependencies: 034-5
- Product rules: PR-008, BR-026

## Outcome

Frozen leakage-free holdout compares baseline and fine-tuned artifacts across No RAG, Current RAG, Oracle, and Distractor. Promotion requires statistically significant quality gain without safety, latency, cost, or grounding regression.

## Implementation scope

- Extend fine-tuning runner from one case to a complete frozen holdout corpus.
- Enforce disjoint train/validation/holdout identities and content digests.
- Execute identical generation and RAG matrices for base and fine-tuned model artifacts.
- Align implementation, error messages, fixtures, and artifact schema on all four RAG conditions.
- Apply paired significance test and minimum quality gain of 5%.
- Produce immutable GO/NO_GO report with artifact/model/split/config identity and raw-result backlinks.

## Likely files

- `src/ai-game-master-service/.../benchmark/finetuning/FineTuningEvaluationRunner.java`
- `src/ai-game-master-service/.../benchmark/finetuning/FineTuningDecisionReport.java`
- `src/ai-game-master-service/.../benchmark/finetuning/FineTuningDatasetSplit.java`
- `src/ai-game-master-service/.../benchmark/finetuning/FineTuningArtifactStore.java`

## Acceptance criteria

- Train, validation, and holdout contain no duplicated case IDs or content fingerprints.
- Base and fine-tuned models complete identical four-condition holdout matrices.
- Fine-tuned quality gain >= 5% and statistically significant.
- Grounding, structure, Korean narration, secret, state, scope, latency, variance, and cost gates do not regress.
- Missing/incomplete/incompatible matrix always yields NO_GO or invalid artifact, never GO.

## Test contract

- Unit: split leakage, matrix completeness, four-condition alignment, significance, regression gates.
- Integration: both real model artifacts run on frozen holdout and produce round-trippable report.
- Failure integration: digest/config mismatch and partial provider failure prevent promotion.
- `ui ~ entity` e2e: promoted model can replace baseline without changing request/response contract, citation identity, or persisted adventure state semantics.

## Out of scope

- Training pipeline and dataset authoring.

## Execution notes

- Extended dataset split identity to immutable training, validation, and frozen holdout sets with disjoint case identities and content digests.
- Added complete frozen holdout corpus execution while preserving the single-case runner API.
- Enforced complete base/fine-tuned four-condition matrices and immutable raw-result backlinks in decision reports.
- Added regression coverage for split leakage, digest leakage, corpus execution, and matrix completeness.
