# ADR-012: Character rule engine and aggregate-owned mutation guards

- Status: Accepted
- Date: 2026-08-03

## Context

Character creation and runtime play require two different kinds of rule decisions.

The first kind is transient action resolution: attack bonuses, damage, saving throws, skill checks, spell attacks, spell save DCs, movement limits, action economy, target validity, and other values that may change for every turn or action.

The second kind protects the character's own state: whether an armor item may be equipped, whether a spell may be prepared, whether a resource may be restored beyond its maximum, whether a level-up is valid, and whether a character field may be changed after the session has locked it.

The current implementation calculates and validates many D&D 5e values in the web client. This makes the client an accidental source of truth and permits rule behavior to diverge between character creation, character updates, and runtime play. It also makes it difficult for the GM agent to explain a rejected command with stable machine-readable reasons and rule provenance.

## Decision

### 1. Separate transient engine decisions from character invariants

Turn- and action-scoped calculations are owned by the runtime game engine. The engine evaluates an action against the current character snapshot, encounter state, active effects, and the resolved ruleset.

Character state invariants are owned by the `CharacterSheet` aggregate. Every command that changes character-owned state must be evaluated before mutation. The aggregate applies the change only after the applicable character rules accept it.

Examples of aggregate-owned guards include:

- owned equipment and equipped-item consistency;
- armor, weapon, shield, and hand conflicts;
- class and feature equipment restrictions;
- prepared-spell membership and limits;
- current hit points and resource maximums;
- valid level progression;
- session-locked identity and build fields.

A D&D 5e druid attempting to equip prohibited metal armor is rejected by the character mutation path. The aggregate remains unchanged and its version is not advanced.

### 2. Use one rules implementation for preview and mutation

The character rules engine exposes pure evaluation and derivation operations. Evaluation may be used before a command to provide previews and alternatives, but the aggregate invokes the same rule contract again as the final mutation guard.

Frontend checks are advisory only. They may improve responsiveness while migration is in progress, but neither character creation nor character mutation trusts client-provided derived values.

### 3. Return structured command outcomes

Character and runtime commands return one of three outcomes:

- `APPLIED`: the command was accepted and produced domain events or state changes;
- `REJECTED`: one or more rule violations prevented the mutation;
- `REQUIRES_CHOICE`: the command cannot proceed until the player selects among explicit alternatives.

A rule violation contains at least:

- a stable code;
- category and severity;
- subject and target identifiers when applicable;
- a message key or fallback message;
- structured parameters;
- ruleset and source references when available.

Rejected mutations do not modify state, emit mutation events, or increment the aggregate version.

### 4. Make GM communication an output concern

The GM agent does not invent the rule decision. It sends a structured command to the appropriate engine or aggregate and receives a structured outcome.

For a rejection, the GM uses the violation code, parameters, provenance, and suggested alternatives to narrate the reason and present valid next choices. This keeps narration flexible while preserving an authoritative, auditable rule result.

### 5. Preserve ruleset identity

Every evaluation and persisted derived snapshot identifies the effective ruleset revision, rulebook catalog revisions, and approved scenario overlay revision. Existing characters remain explainable even after a rulebook or scenario is recompiled.

## Processing boundaries

```text
Player action
  -> GM agent structures intent
  -> runtime game engine resolves turn/action rules
  -> character or encounter aggregates apply accepted commands
  -> structured outcome returns to GM

Character mutation
  -> application service loads character and effective ruleset
  -> rules engine evaluates proposed mutation
  -> CharacterSheet aggregate accepts or rejects
  -> accepted state is persisted; rejected state is unchanged
```

## Consequences

- The backend becomes authoritative for derived values and rule validation.
- `CharacterSheet` gains an explicit mutation-rule contract rather than accepting arbitrary structured-sheet replacement.
- Runtime calculations and character mutation rules remain separate but share the same resolved ruleset and rule definitions.
- The frontend will migrate from local D&D calculations to backend evaluation responses.
- GM-facing APIs must preserve structured violations instead of reducing them to generic exception strings.
- Existing update endpoints require incremental migration so that all mutation paths invoke aggregate guards.

## Follow-up work

1. Introduce common mutation decision and violation types in the character domain.
2. Route `CharacterSheet.applyUpdate` through an edition-specific mutation rules resolver.
3. Implement D&D 5e 2014 equipment guards, beginning with ownership, hand conflicts, armor proficiency, and druid metal armor restrictions.
4. Add a non-persisting character-build evaluation endpoint for previews and incomplete builds.
5. Stop accepting client-authored derived statistics during character creation and updates.
6. Expose structured command outcomes to the runtime GM tool boundary.
