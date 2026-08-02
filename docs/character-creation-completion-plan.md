# Character Creation Completion Plan

## Goal
Complete the D&D 5e 2014 character creation flow so that users select only rule-defined choices and all derived sheet values are calculated consistently in the UI and persisted payload.

## Status
- Phase 1: implemented in rules, UI, validation, and persisted build data.
- Phase 2: implemented in rules, UI, and persisted spell model.
- Phase 3: implemented for current level-1 subclass catalog.
- Phase 4: combat/equipment rules implemented and tested; equipped-item UI and persistence integration remain.
- Phase 5: frontend validation contract implemented; backend endpoint integration remains.
- Phase 6: not complete.

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
- [ ] Add equipped-item controls to character creation and persist the resolved state.
- [ ] Validate armor proficiency and druid metal-armor restrictions.

## Phase 5 — Server-side validation
- [x] Define stable validation error codes for standard array, skill counts, subclass requirements, spells, equipment groups, choices, and expertise.
- [ ] Locate the character-sheet creation handler and invoke equivalent validation before persistence.
- [ ] Reject payloads that violate the selected edition catalog.

## Phase 6 — Architecture and regression
- [ ] Move hardcoded frontend catalogs behind an edition/rulebook catalog API.
- [ ] Split `CharacterCreationPage` into step components.
- [ ] Restore removed `ScenarioCompilationWorkerTest` cases.
- [ ] Add one successful creation test per class.
- [ ] Add persistence round-trip and party-add integration tests.

## Completion criteria
- No rule-defined choice is represented by free text or an unresolved placeholder.
- UI validation and backend validation agree.
- Preview and persisted derived statistics are identical.
- All targeted frontend tests and the production build pass.
