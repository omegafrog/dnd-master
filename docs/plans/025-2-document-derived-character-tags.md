# Plan 025-2: Document-Derived Character Tags

- Status: proposed
- Dependencies: 025-1
- Blocks: 025-3

## Goal

Extract dynamic character input candidates from rulebook/storybook evidence and compile one tree.

## Scope

- Add `CharacterInputTagExtractionPort` and structured candidate contract.
- Separate character-tag extraction from resolution extraction.
- Merge STORYBOOK first, RULEBOOK fallback.
- Preserve source type, evidence, confidence, uncertainty, and partial children.
- Unknown input mode/value → `FREE_TEXT` plus warning.

## Acceptance

- `race`/`class` become selection nodes when documents provide valid values.
- `background`/`starting_ability_scores` can remain free input with suggestions.
- Storybook override wins over conflicting rulebook data.
- New document fields and missing child tags enter the tree dynamically.

## Test contract

- Policy unit tests: precedence, dynamic nodes, uncertainty fallback, evidence, partial extraction.
- UI~entity e2e: fixture documents → extraction candidate → compiled blueprint tree with correct mode/source.

## Main files

`ScenarioPackageCompilationService.java`, `CharacterCreationBlueprintCompiler.java`, extraction port/adapter, compilation tests, rule-knowledge gateway seams.
