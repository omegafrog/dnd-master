# 032-2 — Atomic Commit and SSE Projection

- Status: `completed`
- Issue: [#116](https://github.com/omegafrog/dnd-master/issues/116)
- Parent: [#114](https://github.com/omegafrog/dnd-master/issues/114)
- Dependencies: [032-1](032-1-typed-gm-turn-lifecycle.md)
- Spec: Product UC-008, FR-001/002/009; Architecture §§5.3–5.5, 6

## Outcome

GM Turn local state becomes visible only through one committed session version. Chat and future map windows consume the same ordered event stream.

## Vertical Scope

- Enforce one active GM Turn per session with DB constraint and application lock.
- Make Adventure, turn, conversation/context, commit marker, and outbox write one local transaction.
- Add ordered session event outbox and `GET /events?afterVersion=` SSE endpoint.
- Emit terminal failure without publishing partial narration/state.
- Add replay cursor and committed projection refresh contract.
- Replace repository-local JDBC connections where they prevent shared transaction participation.
- Add browser SSE hook and turn processing/failed states.

## Policy Unit Tests

- `GmTurnCommitPolicy` rejects missing required local state.
- only committed version is publishable.
- session event versions are monotonic and duplicate-safe.
- one active turn policy rejects concurrent different commands but replays duplicates.

## Integration and Contract Tests

- PostgreSQL failure injection between every local write → full rollback.
- concurrent submit test → one active turn.
- outbox at-least-once delivery and SSE `Last-Event-ID` replay.
- contract drift test ensures OpenAPI and controller both use actual SSE semantics.

## UI ~ Entity E2E

Submit turn while chat and second window subscribe → processing shown → only final commit updates both to same session version; disconnect/reconnect replays missed result.

## Implementation Scope

- runtime application transaction boundary/repositories
- adventure runtime migrations
- session event outbox/publisher/controller
- web UI SSE hook and turn status components
- system retry/idempotency tests

## Out of Scope

External tool Saga, tactical-map contents, context compaction.

## Completion

- Failure-injection suite proves player-visible atomicity.
- Status becomes `completed`; 032-3 and 032-6 may become ready.
