# Character Creation Completion Plan

## Goal
Complete the D&D 5e 2014 character creation flow so that users select only rule-defined choices and all derived sheet values are calculated consistently in the UI and persisted payload.

## Phase 1 — Choice completion
- Replace placeholder values such as `선택 언어 1개`, `악기 3종`, and `장인 도구` with typed choice requirements.
- Add language, tool, instrument, and gaming-set catalogs.
- Add background feature data and descriptions.
- Validate required choice counts before character creation.
- Persist selected languages, tools, and background feature.

## Phase 2 — Spell model
- Separate known, prepared, spellbook, domain, and pact spell behavior.
- Calculate prepared spell limits from class and ability modifier.
- Add domain spells and subclass bonus cantrips.
- Persist spell slots and recovery model.

## Phase 3 — Subclass effects
- Apply level-1 subclass effects to armor proficiency, AC, bonus spells, cantrips, and features.
- Ensure the displayed values and persisted values use the same resolved build.

## Phase 4 — Equipment and combat completion
- Separate owned equipment from equipped items.
- Validate shield/two-handed conflicts and armor proficiency.
- Add versatile, thrown, ammunition, unarmed, and monk martial-arts attacks.

## Phase 5 — Server-side validation
- Add backend validation for standard array, class skill counts, subclass requirements, spell counts, equipment groups, language/tool choices, and expertise.
- Reject payloads that violate the selected edition catalog.

## Phase 6 — Architecture and regression
- Move hardcoded frontend catalogs behind an edition/rulebook catalog API.
- Split `CharacterCreationPage` into step components.
- Restore removed worker tests.
- Add one successful creation test per class plus persistence and party-add integration tests.

## Completion criteria
- No rule-defined choice is represented by free text or an unresolved placeholder.
- UI validation and backend validation agree.
- Preview and persisted derived statistics are identical.
- All targeted frontend tests and the production build pass.
