# 033-7 — Grounding Hard Gate And Degraded Mode

- Status: `pending`
- Issue: [#132](https://github.com/omegafrog/dnd-master/issues/132)
- Parent: [#125](https://github.com/omegafrog/dnd-master/issues/125)
- Dependencies: [033-3](033-3-rag-ab-evaluation.md), [033-4](033-4-safe-story-rag-visibility.md)
- Spec: GM quality/RAG/fine-tuning plan §Phase 5

## Outcome

Unsupported claims, false citations, secret disclosure, and premature outcomes are blocked server-side.

## Vertical Scope

- Validate citation evidence ID, document, locator, and extraction version.
- Validate rule claims, supplied resolution agreement, disclosure, unresolved rolls, and conflicting evidence.
- Implement validation → one bounded repair → revalidation → safe refusal/pending judgment.
- Define rule/story/all-evidence degraded modes.

## Policy Unit Tests

- Every listed violation fails closed.
- Repair is attempted at most once and cannot mutate authoritative state.
- Missing evidence forbids corresponding claims and finalization.

## Integration And Contract Tests

- Integrate `GmFinalValidator`, grounded rule answers, scene citations, and runtime persistence.
- Persist validation outcome, repair attempt, and refusal reason.

## UI ~ Entity E2E

Invalid citation/secret/uncertain roll → bounded repair or refusal → player receives no unsupported result.

## Implementation Scope

Claim/evidence validation, repair contract, degraded result model, persistence, metrics, and tests.

## Out Of Scope

Replacing deterministic adjudication.

## Completion

False citation, unsupported rule claim, premature outcome, and secret leak rates all reach 0%; repair-inclusive p95 stays within budget.
