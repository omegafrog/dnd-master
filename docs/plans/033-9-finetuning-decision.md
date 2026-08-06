# 033-9 — Fine-tuning Experiment And Decision Report

- Status: `completed`
- Issue: [#134](https://github.com/omegafrog/dnd-master/issues/134)
- Parent: [#125](https://github.com/omegafrog/dnd-master/issues/125)
- Dependencies: [033-3](033-3-rag-ab-evaluation.md), [033-7](033-7-grounding-hard-gate.md), [033-8](033-8-deterministic-adjudication.md)
- Spec: GM quality/RAG/fine-tuning plan purpose and §Phase 2

## Outcome

Fine-tuning is accepted or rejected using controlled evidence, not intuition.

## Vertical Scope

- Define training/evaluation split from approved corpus and prevent test leakage.
- Compare base and fine-tuned models under identical No/Current/Oracle RAG conditions.
- Record quality, grounding, Korean narration, structure, latency, variance, and operational cost.
- Produce go/no-go decision and follow-up recommendations.

## Policy Unit Tests

- Train/test cases and model artifacts are versioned and disjoint.
- Evaluation settings remain identical across model variants.
- Decision report fails if required metrics/artifact digests are missing.

## Integration And Contract Tests

- Model artifact loads through existing provider-neutral configuration.
- Quality gates consume the same report schema for base and fine-tuned models.

## UI ~ Entity E2E

Same player journey on base and fine-tuned providers → grounded deterministic outcome → Korean narration; verify no regression in visibility, structure, or latency.

## Implementation Scope

Dataset split, training/evaluation runner, artifact metadata, provider wiring, comparative reports, and regression tests.

## Out Of Scope

Automatic production rollout or per-turn provider mixing.

## Completion

Decision report shows statistically meaningful quality gain, or records evidence-based rejection with identified bottleneck.

## Execution

- Added immutable, digest-addressed training/test split with overlap rejection.
- Added provider-neutral base/fine-tuned artifact metadata and JSON persistence.
- Added identical No RAG/Current RAG/Oracle evaluation matrix with quality, grounding, Korean narration, structure, latency, variance, and cost metrics.
- Added deterministic GO/NO_GO decision gates and required-metadata validation.
- Fixed shared benchmark sample variance and nearest-rank percentile calculations to match report contracts.

Verification: `:ai-game-master-service:test` passes.
