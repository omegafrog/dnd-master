# Plan 025-3: Character Blueprint Tree API

- Status: proposed
- Dependencies: 025-1, 025-2
- Blocks: 025-4, 025-5

## Goal

Expose and persist the new tree contract as the only character blueprint contract.

## Scope

- Replace flat field/value review API with full tree snapshot GET/PUT.
- Persist tree JSON and immutable revisions.
- Add expected-revision optimistic conflict handling.
- Remove old flat compatibility/migration path.
- Keep compile response sufficient for route navigation.

## Acceptance

- GET returns node identity, hierarchy, mode, value, options, suggestions, status, evidence.
- PUT preserves tree shape and input modes while saving values.
- Stale revision returns conflict; no silent overwrite.
- Old flat payload is rejected or no longer exposed.

## Test contract

- Policy unit tests: aggregate revision and snapshot validation.
- UI~entity e2e: page-shaped tree PUT → repository JSON → GET returns identical tree metadata/value.

## Main files

`CharacterCreationBlueprintView.java`, `ScenarioPreparationController.java`, application service, `PostgresScenarioPackageRepository.java`, blueprint migrations/schema, API tests.
