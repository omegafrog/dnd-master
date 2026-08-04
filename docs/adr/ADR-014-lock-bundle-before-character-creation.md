# ADR-014: Lock Bundle Before Character Creation

## Status

Accepted

## Context

Character fields and rules are derived from the selected bundle. Allowing that bundle content to change after character creation begins could add or remove fields and invalidate created character state.

## Decision

Before entering character creation, the preparation flow creates a **Bundle Lock** over the exact versions of:

- Scenario Bundle and its document membership and roles.
- Scenario Package.
- Knowledge Documents and extraction versions.
- Game System Definition.
- Character creation schema or blueprint.

Before this transition, the Solo Player may add or remove bundle documents and create a new draft revision. After character creation begins, none of the locked inputs may be added, removed, replaced, reclassified, or upgraded for that adventure preparation flow.

The lock applies to the selected bundle revision, not permanently to the bundle identity. Later edits create a new revision. Existing adventures continue using the locked revision; future adventures may select the newer revision.

Adventure start is a later boundary. It locks party membership and runtime configuration. Source navigation during play may advance within the locked package, but it cannot introduce content outside the Bundle Lock.

## Consequences

- Character structure remains consistent from creation through play.
- No character migration is needed during adventure preparation.
- Later source or rule changes create a different bundle revision for a separate preparation flow.
- APIs that mutate locked Knowledge Document membership must reject the operation.
- Package-switch APIs are valid only before Bundle Lock creation.
