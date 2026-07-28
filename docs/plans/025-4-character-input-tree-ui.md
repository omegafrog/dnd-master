# Plan 025-4: Character Input Tree UI

- Status: proposed
- Dependencies: 025-3
- Blocks: 025-5

## Goal

Render and edit the blueprint tree through one reusable component.

## Scope

- Add recursive `CharacterInputTree`.
- Render strictly by `inputMode`, never by options length.
- Keep free-text suggestions as optional suggestions.
- Add missing-child control and evidence/status display.
- Replace flat state/payload in character creation UI with tree state.
- Save and reload without changing mode/value/tree.

## Acceptance

- `race`/`class` select only when mode is selection.
- `name`, `background`, `starting_ability_scores` stay editable text when free text.
- `CON` can be added beneath `starting_ability_scores`.
- Save/reload retains controls, values, children, suggestions, and evidence.

## Test contract

- Policy unit tests: recursive reducer/state updates and control selection.
- UI~entity e2e: render API tree → edit/add child → save → reload and assert DOM control types and values.

## Main files

`CharacterInputTree.tsx`, `CharacterCreationPage.tsx`, `SetupApi.ts`, UI tests, shared DTO/state types.
