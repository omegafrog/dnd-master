# 032-8 — Fog of War and Hidden Tokens

- Status: `in-progress`
- Issue: [#122](https://github.com/omegafrog/dnd-master/issues/122)
- Parent: [#114](https://github.com/omegafrog/dnd-master/issues/114)
- Dependencies: [032-5](032-5-story-continuity-facts-and-game-clock.md), [032-7](032-7-confirmed-grid-map-interaction.md)
- Spec: Product UC-006, BR-014~017, AC-007~009; Architecture §§3.4–3.5, 8

## Outcome

Combat Map owns server-calculated visibility. Player payload contains only current/explored cells and legitimately revealed tokens, traps, doors, objects, and one-turn last-seen markers.

## Vertical Scope

- Add wall/door/obstacle-aware line-of-sight and visibility snapshot.
- Persist current visible, explored, and hidden cell state.
- Add token discovery/visibility/provenance; hidden identity and coordinates never enter player DTO.
- Add default token categories and accessible legend styling.
- Keep discovered traps visible.
- On monster exit from sight, show same token reduced at last position until rulebook one turn; then remove.
- Recalculate after committed movement, door changes, reveals, and game-time events.

## Policy Unit Tests

- LOS blocked by wall/closed door; opening updates visibility.
- explored cells remain dim, never current-visible.
- hidden monster/trap serialization returns no identity/coordinate.
- Last Seen expiry uses AdventureClock/rule turn and is exactly one turn.

## Integration and Contract Tests

- visibility snapshot persistence/version/idempotency.
- `GameTimeAdvanced` drives expiry once.
- negative JSON/schema tests for every hidden field.
- SSE projection refresh stays on committed version.

## UI ~ Entity E2E

Explore map → open door → reveal monster → monster leaves sight → reduced last-seen token appears → one rule turn passes → marker disappears; hidden trap stays absent until detected.

## Implementation Scope

- combat-map visibility domain/app/infra/migrations
- Adventure clock/map command integration
- player tactical-map schema and UI fog/token renderer
- accessibility and browser tests

## Out of Scope

Hex grids, free drawing, user token creation.

## Completion

- Secret-projection hard gate passes with zero leaks.
- Status becomes `completed`; 032-9 may advance when 032-5 complete.
