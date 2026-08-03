# Character Creation Completion Plan

## Goal
Complete the D&D 5e 2014 character creation flow so that users select only rule-defined choices and all derived sheet values are calculated consistently in the backend and persisted payload.

## Status
- Phase 1: implemented in rules, UI, validation, and persisted build data.
- Phase 2: implemented in rules, UI, and persisted spell model.
- Phase 3: implemented for current level-1 subclass catalog.
- Phase 4: implemented in rules, UI, validation, and persisted equipped-item state.
- Phase 5: implemented for the active D&D 5e 2014 session character-sheet creation route.
- Phase 6: in progress; page decomposition, all-class server creation coverage, worker regression restoration, aggregate equipment guards, backend evaluation, and authoritative core derivation are complete. Attack derivation, frontend evaluation wiring, catalog migration, and integration regression remain.

## Phase 1 — Choice completion
- [x] Replace placeholder language, instrument, artisan-tool, gaming-set, and background-tool values with typed choices.
- [x] Add catalogs and required-count validation.
- [x] Add background feature data and descriptions.
- [x] Persist selected languages, tools, and background feature.

## Phase 2 — Spell model
- [x] Separate known, prepared, spellbook, domain, and pact spell behavior.
- [x] Calculate prepared spell limits from class and ability modifier.
- [x] Add domain spells and subclass bonus cantrips.
- [x] Persist spell slots and recovery model.

## Phase 3 — Subclass effects
- [x] Apply current level-1 subclass effects to armor proficiency, AC, bonus spells, cantrips, and features.
- [x] Use the same resolved effect data for preview and persistence.

## Phase 4 — Equipment and combat completion
- [x] Define owned equipment separately from equipped-item state.
- [x] Validate shield/two-handed and unowned-weapon conflicts.
- [x] Add versatile, thrown, ammunition, unarmed, and monk martial-arts attack derivation.
- [x] Add equipped-item controls to character creation and persist the resolved state.
- [x] Validate armor proficiency and druid metal-armor restrictions.

## Phase 5 — Server-side validation
- [x] Define stable validation error codes for standard array, skill counts, subclass requirements, spells, equipment groups, choices, and expertise.
- [x] Identify the active route: `/internal/v1/adventure-sessions/{sessionId}/character-sheets`.
- [x] Invoke D&D 5e 2014 validation in `CharacterSheetController` before persistence.
- [x] Reject malformed standard arrays, incomplete structured builds, invalid expertise, missing level-1 subclasses, and insufficient spell or equipment selections.
- [x] Preserve the existing D&D 5e 2024 creation and character-update contracts.

## Phase 6 — Architecture and regression
- [ ] Move hardcoded frontend catalogs behind an edition/rulebook catalog API.
- [~] Move character derivation and validation behind an authoritative backend rule engine.
  - [x] Record the engine/aggregate/GM responsibility split in ADR-012.
  - [x] Add structured rule violations and mutation decisions.
  - [x] Guard `CharacterSheet` updates before replacing aggregate state.
  - [x] Route application-service updates through an edition-specific mutation-rules resolver.
  - [x] Implement D&D 5e 2014 equipment mutation rules, including ownership, hand conflicts, armor proficiency, and druid metal armor rejection.
  - [x] Add a non-persisting character-build evaluation endpoint.
  - [x] Derive authoritative ability scores and modifiers, proficiency, HP, AC, speed, saves, skills, passive perception, and spell statistics in the backend.
  - [x] Ignore client-authored D&D 5e 2014 derived statistics on create and update.
  - [ ] Derive equipped attacks and damage in the backend.
  - [ ] Connect the frontend preview and completion state to the evaluation endpoint.
  - [x] Return structured mutation rejections to the GM tool boundary.
- [x] Split `CharacterCreationPage` into step components.
  - [x] Extract and connect `CharacterIdentitySelection`.
  - [x] Extract and connect `CharacterClassSelection`.
  - [x] Extract and connect `CharacterEquipmentLoadout`.
  - [x] Extract and connect `CharacterSpellSelection`.
  - [x] Extract and connect `CharacterSkillSelection`.
  - [x] Extract and connect `CharacterAbilityScores`.
  - [x] Extract and connect `CharacterRoleplayDetails`.
  - [x] Extract and connect `CharacterDerivedPreview`.
  - [x] Extract and connect `CharacterPartyStep`.
- [x] Restore removed `ScenarioCompilationWorkerTest` cases.
- [x] Add one successful creation test per class.
- [ ] Add persistence round-trip and party-add integration tests.

## Completion criteria
- No rule-defined choice is represented by free text or an unresolved placeholder.
- The backend rule engine is authoritative for validation and derived statistics.
- Character aggregate mutations reject invariant violations without changing state or version.
- Preview and persisted derived statistics are identical.
- All targeted frontend and backend tests and production builds pass.
