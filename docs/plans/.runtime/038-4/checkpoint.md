# 038-4 checkpoint

- attempt: 13
- status: in-progress
- scope: player trigger qualification, durable planned trigger effects, active-map/session ownership, immutable revealed stages and append-only future revision
- resumed after 038-3 completion at `5f55cc4d`
- blockers: fresh independent review remains unavailable due provider usage limit
- completed: durable player-origin RuntimeTurn evidence, canonical action matching, GM-only qualifyingAction projection, missing-action validator retry violation, and explicit generator-only revision API
- blockers: fresh independent review remains unavailable due provider usage limit
- completed: coordinate-only fog reveal handling and state-advancing player evidence discriminator
- completed: GM typed-turn origin enforcement and repository-backed append-only story-plan history path retained across future revisions
- completed: explicit agent-origin runtime command path keeps AI turns out of player trigger evidence, with regression coverage
- completed: durable RuntimeTurnOrigin provenance distinguishes PLAYER, GM, and AGENT across JSON/Postgres reload; read-only turns remain persisted with advancesState=false and are ineligible as trigger evidence
- completed: meta turns are durably saved before return, legacy RuntimeTurn JSON defaults explicitly to non-player GM provenance, and post-start plan generation is rejected in favor of future-stage revision
- completed: story-plan history remains denied to player projection while internal-token GM history returns owner/session-scoped append-only GmPlanView revisions
- completed: adventure OpenAPI documents the internal-token GM history route, owner/session scope, revision response, and 401/403 behavior with contract regression
- completed: history HTTP dispatch returns 401 for missing/invalid internal token and 403 for foreign owner; qualifyingAction now survives the Adventure-to-Combat Map typed trigger effect and durable operation fingerprint
