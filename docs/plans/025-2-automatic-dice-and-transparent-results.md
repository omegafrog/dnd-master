# 025-2 Automatic Dice and Transparent Results

- Issue: #103
- Status: blocked
- Dependencies: #102 / plan 025-1

## Goal

Resolve a validated check with system-owned dice and an authoritative full character-sheet snapshot, then show the complete calculation to the player.

## Scope

- Read a versioned, rule-relevant active character snapshot: ability modifiers, proficiency, HP/AC, resources, inventory, and conditions as needed for the check.
- Add Adventure-to-Dice Roll port/adapter for idempotent trusted runtime calls.
- Map structured check specifications to immutable roll results; AI cannot choose random values or final totals.
- Return full public breakdown: expression, d20 values, modifier sources, proficiency, advantage/disadvantage, DC, total, outcome, citations.
- Render the resolved check in UI.

## Acceptance Criteria

- Same `commandId` produces the same visible roll result after retry.
- Roll command cannot be used for a non-active player or altered check specification.
- A check uses the latest versioned sheet snapshot, not model-supplied modifier text.
- `NO_CHECK` returns narration without synthetic dice data.

## Test Contract

- Unit: check-to-expression/modifier policy and roll-scope authorization.
- Integration: character snapshot and Dice Roll adapters, including command-id replay.
- UI ↔ entity E2E: player action → visible d20/modifiers/DC/outcome sourced from persisted DiceRoll.

## Implementation Areas

- `character-management-service` internal sheet snapshot contract.
- `dice-roll-service` trusted Adventure runtime authorization/result contract.
- `adventure-service` DiceRollPort, runtime saga phase, transparent turn mapper.
- `web-ui` roll breakdown card.

