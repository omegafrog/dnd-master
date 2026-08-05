# 032-5a — Rulebook Template and Blueprint Layering

- Status: `completed`
- Parent: [032-5](032-5-story-continuity-facts-and-game-clock.md)
- Dependencies: [032-1](032-1-typed-gm-turn-lifecycle.md), [032-2](032-2-atomic-commit-and-sse-projection.md), [032-4](032-4-capability-scoped-tool-saga.md)
- Spec: Product BR-001/004/017/018/021; Architecture §§3.2, 3.5, 3.6; ADR-013

## Outcome

A rulebook revision produces a versioned published base game-system template. Storybook and additional extracted sources can add or constrain character-creation behavior through a versioned blueprint revision. Runtime binding locks the exact definition and blueprint versions used by a session.

## Vertical Scope

- Add versioned `GameSystemTemplateRevision` owned by the rule-knowledge boundary.
- Store normalized, validated definition JSON with `DRAFT` and `PUBLISHED` lifecycle.
- Add publication validation and immutable published revisions.
- Add `CharacterCreationBlueprint` provenance for base template, storybook, and additional source revisions.
- Compile base template plus source-grounded overlays into a versioned final blueprint.
- Reject overlay conflicts that change authoritative rulebook mechanics or introduce unsupported fields without evidence.
- Lock `gameSystemDefinitionVersion` and `characterBlueprintVersion` in `RuntimeBinding`.
- Expose locked versions to character creation and runtime context.

## Policy Unit Tests

- Different rulebook revisions produce independent base templates.
- Storybook overlay can add or constrain fields but cannot silently rewrite base mechanics.
- Unsupported or conflicting overlay fails publication.
- Published template and blueprint revisions are immutable.
- Runtime binding preserves exact definition and blueprint versions after later publication.

## Integration and Contract Tests

- Definition publication persists immutable version and normalized JSON.
- Blueprint compilation persists source provenance and version references.
- Runtime binding stores and returns both locked versions.
- Character creation reads the bound blueprint, never the latest published revision.
- Rulebook-definition lookup returns the bound published version for clock/rule evaluation.

## Out of Scope

- Adventure story-plan revisions, committed facts, or map visibility.
- Provider-specific extraction prompts.
- Editing published definitions in place.

## Completion

- Base template, overlay, blueprint, and binding version invariants pass.
- 032-5 can resolve clock rules from the locked published definition.
- Status becomes `completed`; 032-5 remains gated until this plan is complete.
