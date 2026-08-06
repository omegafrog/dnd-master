# 033-3 — RAG A/B Condition Harness

- Status: `pending`
- Issue: [#128](https://github.com/omegafrog/dnd-master/issues/128)
- Parent: [#125](https://github.com/omegafrog/dnd-master/issues/125)
- Dependencies: [033-1](033-1-gm-quality-baseline.md), [033-2](033-2-retrieval-evaluation-corpus.md)
- Spec: GM quality/RAG/fine-tuning plan §Phase 2

## Outcome

Identical GM cases run under No RAG, Current RAG, Oracle RAG, and Distractor RAG, enabling bottleneck classification.

## Vertical Scope

- Define condition-specific evidence providers.
- Keep corpus, model, generation settings, and run count identical.
- Compare rule accuracy, citations, hallucination, leaks, continuity, structure, human score, and end-to-end latency.
- Report retrieval, prompt/context, or generation bottleneck.

## Policy Unit Tests

- Conditions cannot change case or generation configuration.
- Oracle evidence is exact; distractor evidence is similar but incorrect.
- Statistical aggregation preserves per-condition variance.

## Integration And Contract Tests

- Runtime accepts injected evidence condition without changing canonical turn contract.
- Report contains all four conditions for every case.

## UI ~ Entity E2E

Same player action under each condition → GM response → quality report; verify condition isolation and persisted evidence provenance.

## Implementation Scope

Condition model, evidence injection seam, benchmark runner, report schema, comparison analysis, and regression tests.

## Out Of Scope

Changing retrieval ranking or production prompting.

## Completion

Four-condition report identifies whether observed gains come from retrieval, context/prompt, or generation.
