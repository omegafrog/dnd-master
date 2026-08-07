# 035-3 Authoritative rules, dice, and combat turns

- Status: `in-progress`
- Tracker: local Markdown
- Dependencies: 035-1, 035-2
- Product rules: BR-004, BR-005, BR-006, BR-008, AC-004, AC-005

## Outcome

The AI proposes actions, but rule evaluation, dice results, character mutations, and combat state are determined by authoritative services. The same GM turn commits all effects atomically and safely replays idempotent commands.

## Implementation scope

- Trace and consolidate the runtime path from GM tool call to deterministic adjudication.
- Wire supported ability checks, saving throws, multiple dice, attack rolls, and damage through authoritative roll commands.
- Integrate combat entry, initiative/turn ownership, legal actions, hit/damage, resources/effects, and combat exit.
- Require expected versions and command IDs for dice, character, and combat operations.
- Prevent provider prose/state delta from acting as authoritative state.

## Likely files

- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/RuntimeTurnApplicationService.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/DeterministicAdjudicationService.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/GmToolGatewayService.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/combat/AdventureCombatApplicationService.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/infrastructure/integration/HttpDiceToolPort.java`
- `src/dice-roll-service/src/main/java/com/dndmaster/diceroll/*`
- `src/combat-map-service/src/main/java/com/dndmaster/combatmap/*`

## Acceptance criteria

- A saving throw and a multi-dice action produce rule-grounded, authoritative results.
- Attack and damage update character/combat state only through authorized commands.
- Entering and leaving combat preserves story continuity and public visibility.
- Failed dependency, stale version, or unsupported rule causes no partial turn commit.
- Replaying the same command ID returns the prior result without duplicate rolls, damage, or events.

## Test contract

- Unit: rule operation policies, save/attack/damage resolution, legal combat action, and atomic failure cases.
- Integration: Adventure Runtime with dice, character, and combat adapters verifies persisted versions and journal replay.
- `UI ~ entity` E2E: browser action triggers a save, multiple dice, combat entry, attack/damage, and combat exit; assert visible outcome and no duplicate state after retry.

## Out of scope

- Adding unsupported game-system rules beyond the existing DSL.
- Provider model quality tuning; ticket 035-1.
