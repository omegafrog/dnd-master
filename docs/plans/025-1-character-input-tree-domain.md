# Plan 025-1: Character Input Tree Domain

- Status: proposed
- Dependencies: none
- Blocks: 025-2, 025-3

## Goal

Replace flat character blueprint fields with recursive nodes and explicit input semantics.

## Scope

- Add `CharacterInputNode`, `InputMode`, extraction status, evidence, options, suggestions.
- Update `CharacterCreationBlueprint` aggregate for child insertion and mode/value preservation.
- Update compiler to accept dynamic roots/children and partial extraction.
- Remove fixed-key rejection and flat resolve semantics.

## Acceptance

- Nested `starting_ability_scores.CON` is representable.
- `FREE_TEXT` with suggestions remains free input.
- Missing child can be added without replacing existing children.
- STORYBOOK/RULEBOOK policy input is explicit, even if extraction integration lands in 025-2.

## Test contract

- Policy unit tests: node invariants, mode/options separation, partial extraction, user child, mode preservation.
- UI~entity e2e: serialize a representative tree through the blueprint boundary and assert nested node identity/value survives.

## Main files

`src/adventure-service/.../CharacterCreationBlueprint.java`, `CharacterInputNode.java`, `CharacterCreationBlueprintCompiler.java`, related domain tests.
