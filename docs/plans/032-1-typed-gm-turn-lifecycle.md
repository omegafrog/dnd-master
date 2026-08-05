# 032-1 — Typed GM Turn Lifecycle

- Status: `completed`
- Issue: [#115](https://github.com/omegafrog/dnd-master/issues/115)
- Parent: [#114](https://github.com/omegafrog/dnd-master/issues/114)
- Dependencies: none
- Spec: Product UC-001, UC-004, UC-010; Architecture §§4.3, 5.2, 6.1

## Outcome

Every player text, confirmed map action, or meta question enters one authenticated, versioned, idempotent GM Turn lifecycle. Existing text clients remain compatible through an adapter.

## Vertical Scope

- Add sealed `GmInput`: `TextInput`, `MapActionInput`, `MetaQuestionInput`.
- Add `GmTurn` status transitions: `STARTED`, `PROCESSING`, `COMMITTED`, `FAILED`.
- Add `POST /api/v1/adventures/{adventureId}/turns` with bearer-owner resolution, `Idempotency-Key`, `If-Match-Version`.
- Convert legacy `/messages` requests into `TextInput`; remove body `playerId` as authority.
- Persist typed input, status, fingerprint, stable failure, provider metadata placeholder.
- Reject one command ID reused with a different payload.
- Keep current evidence/planning behavior behind lifecycle until later slices replace it.
- Update adventure OpenAPI and TypeScript API types.

## Policy Unit Tests

- Only allowed state transitions succeed; terminal turns cannot reopen.
- Same idempotency key + same fingerprint returns existing result.
- Same key + different fingerprint fails.
- meta question input is state-free; map input requires map/version/action fields.
- stale expected session version and cross-owner access fail before planning.

## Integration and Contract Tests

- Flyway migration and Postgres round-trip for every input/status.
- `/turns` auth, 202/409/403, idempotent replay contract.
- `/messages` compatibility produces identical `TextInput` turn.
- Existing scoped-RAG and narration-safety regression tests remain green.

## UI ~ Entity E2E

Adventure UI submits text → API creates typed turn → persisted `GmTurn` commits → conversation projection shows one player input and one GM result in chronological order.

## Implementation Scope

- `src/adventure-service/.../application/runtime/`
- `src/adventure-service/.../domain/runtime/` or target `runtime/domain/turn/`
- `AdventureController`, API configuration/error mapping
- adventure migrations and contract schemas/OpenAPI
- `src/web-ui/src/features/adventure/AdventureApi.ts` and tests

## Out of Scope

SSE/outbox, agent tools, provider loop, map rendering, plan revision, clock, compaction.

## Completion

- Acceptance and all test contracts pass.
- Architecture dependency rules pass.
- Plan status becomes `completed`; remove `ready-for-agent` label from #115.
- Mark 032-2 ready only after this slice is merged/accepted.
