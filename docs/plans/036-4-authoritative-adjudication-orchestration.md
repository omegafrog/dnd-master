# 036-4 Authoritative adjudication and combat orchestration

- Status: `planned`
- Tracker: local Markdown
- Dependencies: 036-1, 036-2, 036-3
- Product rules: BR-002, BR-006, BR-007, BR-009, BR-010, AC-004, AC-005, AC-006

## 구현 목적

AI를 판정 결과의 권위자로 사용하지 않는다. AI는 의도와 도구 호출만 제안하고, 지각·내성·공격·피해·HP·initiative·턴 순서는 잠긴 룰과 서버 상태로 결정해 재시도에도 동일한 결과를 보장한다.

## Outcome

The model proposes narrative intent and typed tool calls. Runtime services exclusively determine perception, skills, saves, attacks, damage, HP, initiative, turn order, and combat transitions from locked Rulebook evidence and persisted game state.

## Implementation scope

- Define typed tool commands for checks, saves, attacks, damage, initiative, movement, combat entry, and combat exit.
- Validate tool arguments and rule citations before dice execution or state mutation.
- Route all outcomes through `DeterministicAdjudicationService`, dice ports, combat services, and `AuthoritativeStateMutationPort`.
- Prevent provider `judgment`, narration, or private state from directly changing HP, turn order, inventory, conditions, or map state.
- Generate final public judgment and narration facts from authoritative resolution results.
- Require explicit reveal policy for secret DCs and hidden modifiers.
- Commit GM lifecycle, turn cursor, dice identity, authoritative resolution, and state mutation atomically or compensate to the previous state.
- Reuse the same command, roll, and mutation identities for safe retry; reject fingerprint changes.

## Likely files

- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/RuntimeTurnApplicationService.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/DeterministicRuleResolver.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/DeterministicAdjudicationService.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/AuthoritativeResolution.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/AuthoritativeStateMutationPort.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/combat/AdventureCombatApplicationService.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/api/AdventureController.java`
- `src/adventure-service/src/main/resources/db/migration/*`

## Acceptance criteria

- Perception, skill, save, attack, damage, HP, initiative, and turn order are computed outside the model.
- Every authoritative outcome references the applicable locked Rulebook evidence and persisted inputs.
- Narration contradicting the computed outcome is rejected or regenerated before commit.
- One logical UI command produces at most one roll set, one HP mutation, and one cursor advance across retries.
- Provider timeout or malformed output leaves no partial HP, initiative, cursor, or combat-state mutation.
- Combat cannot exit until authoritative completion conditions are satisfied.
- Secret DCs and hidden modifiers remain private unless a rule-backed reveal transition permits disclosure.

## Test contract

- Unit: deterministic checks/saves/attacks/damage, initiative ordering, reveal policy, tool validation, and narration/outcome consistency.
- Integration: transactional success, injected failure at each boundary, retry with identical identity, changed-fingerprint conflict, and combat lifecycle persistence.
- `UI ~ entity` E2E: UI actions produce matching dice, resolution, HP, initiative, combat, and cursor entities exactly once.

## Out of scope

- Model-based arithmetic or provider-authored HP values.
- Full real-provider browser journey; covered by 036-5.
