# 033-8 — Deterministic Adjudication And Narrative Separation

- Status: `completed`
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

## Execution (partial)

- Added deterministic adjudication request fingerprinting and command-id idempotency.
- Added authoritative resolution model with explicit resolved/rejected/pending states, state changes, and provenance.
- Added narration contract that accepts only resolved authoritative outcomes.
- Added policy tests for deterministic retries, conflicting command reuse, provenance propagation, and unresolved-result refusal.
- Wired authoritative resolution into production runtime turns before provider planning; persisted outcomes use the runtime PostgreSQL command journal.
- Added runtime state mutation port and adapter; resolved state changes now persist in the authoritative adventure context before turn commit.
- Removed combat placeholders: seeded combat dice, server-side hit/failure adjudication, and real combat-map AI-state dispatch.

Completion verified: deterministic resolution, durable idempotency, runtime state projection, combat-map mutation flow, and player journey coverage are in place.
