# 032-4 — Capability-Scoped Tool Saga

- Status: `completed`
- Issue: [#118](https://github.com/omegafrog/dnd-master/issues/118)
- Parent: [#114](https://github.com/omegafrog/dnd-master/issues/114)
- Dependencies: [032-2](032-2-atomic-commit-and-sse-projection.md), [032-3](032-3-provider-neutral-gm-agent-loop.md)
- Spec: Product BR-005/007/012/026; Architecture §§2.2, 4.4–4.7, 6.2–6.3, 8

## Outcome

AI GM uses only turn-scoped domain tools. Dice and character mutations execute through recoverable, idempotent Runtime Command Saga steps before final narration commits.

## Vertical Scope

- Add signed/opaque `TurnCapability` bound to session, turn, owner, allowlist, expiry, nonce.
- Add explicit tool registry and schema-first `GmToolGatewayService`.
- Add persisted Runtime Command journal with command fingerprint/status/outcome/version.
- Implement Plan → Act → Finalize loop with bounded tool count and repair attempt.
- Adapt official dice and character command APIs first.
- Query unknown tool outcomes by command ID; resume without reroll/reapply.
- Forbid generic HTTP, DB, filesystem, shell, code execution, dynamic tool registration.

## Policy Unit Tests

- expired, cross-session, cross-turn, unauthorized tool calls fail before dispatch.
- same command/fingerprint replays; mismatch fails.
- required tool failure prevents GM Turn commit.
- structured `REJECTED`/`REQUIRES_CHOICE` becomes safe narration without mutation.

## Integration and Contract Tests

- Dice immutable replay and character expected-version contracts.
- timeout-after-apply recovery by command ID.
- capability token omitted from logs/audit payload.
- Saga resume across process restart.

## UI ~ Entity E2E

Player action requires roll/resource change → agent calls official tools → persisted dice/character entities update once → one matching GM response/state projection appears.

## Implementation Scope

- Adventure tool gateway, Saga app/domain/infra, migrations
- AI agent tool registry/client
- dice/character adapters and contract tests
- security/audit/metrics

## Out of Scope

Plan revisions, map tools, compaction, provider quality switch.

## Completion

- Security and Saga recovery suites pass.
- Status becomes `completed`; 032-5 and dependent map interaction can advance.
