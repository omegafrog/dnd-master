# 033-8 — Deterministic Adjudication And Narrative Separation

- Status: `pending`
- Issue: [#133](https://github.com/omegafrog/dnd-master/issues/133)
- Parent: [#125](https://github.com/omegafrog/dnd-master/issues/125)
- Dependencies: [033-7](033-7-grounding-hard-gate.md)
- Spec: GM quality/RAG/fine-tuning plan §Phase 6

## Outcome

Authoritative rules/state are resolved by deterministic services; LLM generates prose only from resolved outcomes.

## Vertical Scope

- Move hit, damage, HP/resources, effects, movement, dice, turn order, permissions, and state changes behind authoritative resolution.
- Keep LLM responsibilities to scene description, dialogue, tone, and next-choice prompting.
- Pass resolved outcome and provenance into narration.

## Policy Unit Tests

- Same input/state/seed produces same resolution.
- LLM cannot directly commit state, rolls, or unresolved decisions.
- Idempotent retries do not duplicate mutations.

## Integration And Contract Tests

- Integrate scenario resolution, dice-roll service, runtime tools, saga, and turn persistence.
- Validate narration contract against authoritative resolution.

## UI ~ Entity E2E

Player action → deterministic roll/state resolution → narrative generation → map/character/session projection; verify no state hallucination.

## Implementation Scope

Adjudication boundary, resolution commands/results, tool gateway, dice/state adapters, runtime contracts, and tests.

## Out Of Scope

Fine-tuning and retrieval ranking.

## Completion

State hallucination and premature unresolved judgments reach 0%; authoritative mutations remain idempotent.
