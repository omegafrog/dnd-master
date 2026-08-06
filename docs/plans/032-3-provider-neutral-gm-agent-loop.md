# 032-3 — Provider-Neutral GM Agent Loop

- Status: `completed`
- Issue: [#117](https://github.com/omegafrog/dnd-master/issues/117)
- Parent: [#114](https://github.com/omegafrog/dnd-master/issues/114)
- Dependencies: [032-2](032-2-atomic-commit-and-sse-projection.md)
- Spec: Product PR-001/003/004; Architecture §§4.2–4.6, 5.6

## Outcome

Local model performs grounded GM Plan → Finalize behind provider-neutral contracts. This slice remains read-only: no model tool may mutate state yet.

## Vertical Scope

- Add `GmContextEnvelope` using locked bundle/package, RAG, character snapshots, current context, and recent turns.
- Add provider-neutral `GmAgentPort`, structured plan/final result, and local Ollama adapter.
- Replace deterministic runtime planning placeholder with AI GM adapter.
- Validate structured schema, citations, evidence scope, narration safety, and state-delta emptiness.
- Record provider/model/reasoning and validation diagnostics on turn.
- Preserve strict player/GM secret separation in response DTOs.

## Policy Unit Tests

- context assembler enforces Session Knowledge Set and locked revisions.
- final validator rejects uncited rule claims, invented roll/state changes, and hidden-data leakage.
- malformed/timeout provider output fails closed.
- meta questions produce no game-state advancement.

## Integration and Contract Tests

- Ollama adapter structured-output fixtures and exception mapping.
- AI service/adventure service provider-neutral contract test.
- RAG rule/story intent scope regression.
- provider failure leaves prior committed projection unchanged.

## UI ~ Entity E2E

Player text → local GM reads persisted session context/RAG → grounded narration commits → UI renders citations and no hidden fields.

## Implementation Scope

- `ai-game-master-service` agent app/domain/infra packages
- Adventure `GmContextAssembler`, agent HTTP adapter, final validator
- model config/audit/metrics
- AI/adventure contract schemas and tests

## Out of Scope

Write tools, adaptive plan mutation, map manipulation, GPT switch gate.

## Completion

- Local provider produces contract-valid read-only turns.
- Status becomes `completed`; 032-4 may become ready when 032-2 also complete.
