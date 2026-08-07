# 036-4 Authoritative adjudication and combat orchestration

- Status: `planned`
- Tracker: local Markdown
- Dependencies: 036-1, 036-2, 036-3
- Product rules: BR-002, BR-006, BR-007, BR-009, BR-010, AC-004, AC-005, AC-006

## 구현 목적

AI는 의도와 typed tool call만 제안한다. 판정·주사위·공격·피해·HP·initiative·combat state는 잠긴 룰과 서버 상태가 결정해 retry에도 중복·불일치가 없게 한다.

## Outcome

Runtime exclusively determines and mutates authoritative game outcomes.

## Scope

- Define and validate typed checks, saves, attacks, damage, initiative, movement, and combat commands.
- Route outcomes through adjudication, dice, character, combat, and mutation ports.
- Reject provider-authored state deltas and contradictory narration.
- Apply reveal policy to secret DCs/modifiers.
- Preserve command, roll, mutation, and cursor identity across retries.
- Commit lifecycle and state atomically or compensate safely.

## Acceptance

- All listed outcomes computed outside model and tied to locked Rulebook evidence.
- Retry creates at most one roll set, mutation, and cursor advance.
- Provider failure leaves no partial HP, initiative, cursor, or combat mutation.
- Combat exit requires authoritative completion.

## Test contract

- Unit: deterministic resolution, ordering, reveal, tool validation, narration consistency.
- Integration: success, boundary failures, retry identity, changed fingerprint, combat lifecycle.
- `UI ~ entity` E2E: UI actions create matching dice/resolution/HP/initiative/combat entities once.

## Likely files

- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/{RuntimeTurnApplicationService,DeterministicRuleResolver,DeterministicAdjudicationService,AuthoritativeResolution,AuthoritativeStateMutationPort}.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/combat/AdventureCombatApplicationService.java`
- `src/adventure-service/src/main/resources/db/migration/*`

## Out of scope

Full real-provider browser journey; ticket 036-5.
