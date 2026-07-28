# Plan 025-5: Scenario-to-Character Flow

- Status: proposed
- Dependencies: 025-3, 025-4

## Goal

Separate scenario compilation from character blueprint review and make the new page the sole review surface.

## Scope

- Remove character review/input component from `ScenarioSetup`.
- Keep setup responsible for document selection and compilation only.
- On compile success navigate to `/scenarios/:scenarioId/character-creation`.
- Add route/page loading and direct-access fallback to setup when blueprint is absent.
- Connect `CharacterInputTree` review/save/confirm flow.
- Resolve existing session-scoped route contract as part of the route change.

## Acceptance

- Setup never renders character input controls after compile.
- Compile success changes route to the dedicated page.
- Dedicated page loads the compiled tree and saves review revisions.
- Direct page access without blueprint returns to setup.
- Full route→compile→page→edit→save flow works.

## Test contract

- Policy unit tests: navigation/fallback state decisions.
- UI~entity e2e: compile in setup → route transition → tree edit → API/domain save.

## Main files

`ScenarioSetup.tsx`, `CharacterCreationPage.tsx`, `AppShell.tsx`, route and UI integration tests.
