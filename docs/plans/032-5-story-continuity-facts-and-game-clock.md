# 032-5 — Story Continuity, Facts, and Game Clock

- Status: `completed`
- Issue: [#119](https://github.com/omegafrog/dnd-master/issues/119)
- Parent: [#114](https://github.com/omegafrog/dnd-master/issues/114)
- Dependencies: [032-4](032-4-capability-scoped-tool-saga.md), [032-5a](032-5a-rulebook-template-and-blueprint-layering.md)
- Spec: Product UC-002/007, BR-001/004/017/018/021; Architecture §§3.3–3.6

## Outcome

GM can adapt unrevealed adventure structure while committed facts remain immutable. Canonical game time drives duration effects and later visibility behavior.

## Vertical Scope

- Add append-only `CommittedWorldFact` ledger with visibility/provenance/cause turn.
- Add versioned `AdventureClock` and `GameDuration`.
- Read rulebook time definition; fallback to 1 turn=12 seconds.
- Add immutable `AdventureStoryPlanRevision` predecessor/cause chain and current pointer.
- Inject current plan revision/stage/facts/clock into GM context.
- Add `revise_story_plan` and `advance_game_time` domain tools.
- Reject changes contradicting public facts, committed events, source locks, or rules.

## Policy Unit Tests

- revealed/committed fact contradiction fails; unrevealed branch/ending revision succeeds.
- plan history is immutable and causal.
- clock is monotonic; GM response count does not advance it.
- rule time wins; missing rule uses five turns/minute.

## Integration and Contract Tests

- append-only plan/fact/clock migrations and current-pointer transaction.
- tool outcomes and GM Turn commit share version references.
- retry never duplicates facts or advances clock twice.

## UI ~ Entity E2E

Unexpected player action → GM revises hidden plan, records visible result/facts, advances correct time → player sees coherent next scene/effect duration without plan spoilers.

## Implementation Scope

- Adventure runtime plan/fact/time domain/app/infra
- existing story-plan repository/service migration
- Game System Definition time evaluation adapter
- GM context/tool schemas and projection fields

## Out of Scope

Map compilation/visibility, compaction.

## Completion

- Continuity and time invariants pass.
- Status becomes `completed`; 032-8/032-9 remain gated by map dependencies.
