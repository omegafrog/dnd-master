# 033-1 — Reproducible GM Quality Baseline

- Status: `pending`
- Issue: [#126](https://github.com/omegafrog/dnd-master/issues/126)
- Parent: [#125](https://github.com/omegafrog/dnd-master/issues/125)
- Dependencies: none
- Spec: GM quality/RAG/fine-tuning plan §Phase 0

## Outcome

Versioned 30-case Korean GM benchmark produces repeatable model, quality, latency, and raw-response reports.

## Vertical Scope

- Freeze corpus and schema version.
- Record model name/digest, temperature, token cap, context size, and cold/warm state.
- Execute each model at least three times.
- Persist raw responses and calculate mean, variance, p50, and p95.

## Policy Unit Tests

- Identical configuration yields stable case identity and metric schema.
- Missing model digest/config fails the run.
- Aggregation correctly handles repeated runs and non-finite values.

## Integration And Contract Tests

- Benchmark runner loads the frozen corpus and emits a versioned report.
- Raw response artifacts map one-to-one to case/model/run records.

## UI ~ Entity E2E

Player action → GM turn → stored raw response and baseline metric record; verify structure, leak, citation, and latency fields.

## Implementation Scope

Benchmark assets, runner, report schema, artifact storage, local-model route integration, and regression tests.

## Out Of Scope

Retrieval changes, fine-tuning, new quality gates.

## Completion

Reproducible 30-case reports with structure success, leak rate, citation rate, p50, p95, mean, and variance.
