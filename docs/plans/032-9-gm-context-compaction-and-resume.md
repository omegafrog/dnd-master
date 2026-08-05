# 032-9 — GM Context Compaction and Resume

- Status: `completed`
- Issue: [#123](https://github.com/omegafrog/dnd-master/issues/123)
- Parent: [#114](https://github.com/omegafrog/dnd-master/issues/114)
- Dependencies: [032-5](032-5-story-continuity-facts-and-game-clock.md), [032-8](032-8-fog-of-war-and-hidden-tokens.md)
- Spec: Product UC-009, BR-023~025, AC-012/013; Architecture §§3.3, 6.3, 7

## Outcome

Long sessions compact at provider-specific 70% context use without losing canonical state or exact latest situation. Resume rebuilds prompt from checkpoint plus fresh entity versions.

## Vertical Scope

- Add provider token estimator and compaction scheduling after committed turn.
- Add compaction barrier: no active/pending turn, tool, map candidate, or stale character/map snapshot.
- Persist immutable `GmContextCheckpoint` with story/result summary, unresolved threats, plan/fact/clock refs.
- Preserve exact latest player text/map action, preceding scene, last GM response, current turn/round/location/map/fog/unresolved choice.
- Update character sheet and runtime refs before summary generation.
- Add compaction model contract; backend validates required fields and exact tail.
- Rebuild next context from checkpoint + latest authoritative snapshots.
- Add forced-compaction test/admin seam without player-visible summary.

## Policy Unit Tests

- estimated use below 70% does not compact; crossing threshold schedules once.
- active/pending or save failure blocks compaction.
- exact-tail fields are byte-for-byte preserved.
- summaries cannot replace authoritative character/map/fact/clock state.

## Integration and Contract Tests

- checkpoint append/idempotency/current pointer migration tests.
- provider switch recalculates context limit.
- failed/malformed compaction retains previous checkpoint/context.
- prompt assembler resolves every referenced entity version.

## UI ~ Entity E2E

Run exploration/combat turns → force compaction → disconnect/reconnect → next text/map action receives coherent GM response from same exact latest situation and canonical states; no compaction payload exposed.

## Implementation Scope

- Adventure checkpoint domain/app/infra/migrations
- AI compaction port/provider adapter
- prompt/token estimation and observability
- system/browser resume tests

## Out of Scope

Player-visible summaries, manual editing, provider quality gate.

## Completion

- Exact-tail and resume regression corpus passes.
- Status becomes `completed`; 032-10 waits for all slices.
