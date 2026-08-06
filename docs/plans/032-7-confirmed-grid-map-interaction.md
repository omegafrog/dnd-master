# 032-7 — Confirmed Grid-Map Interaction

- Status: `completed`
- Issue: [#121](https://github.com/omegafrog/dnd-master/issues/121)
- Parent: [#114](https://github.com/omegafrog/dnd-master/issues/114)
- Dependencies: [032-4](032-4-capability-scoped-tool-saga.md), [032-6](032-6-bundle-map-compilation-and-activation.md)
- Spec: Product UC-004/005, BR-009~013; Architecture §§4.3, 5.2, 6.1

## Outcome

Player directly manipulates a square-grid map. UI confirmation converts the candidate into a typed GM Turn; backend rules decide final movement/state.

## Vertical Scope

- Build separate tactical map window with square grid overlay, token snapping, legend shell.
- Keep drag/click/target/location candidate frontend-only.
- Show anchored confirmation popover with summary, confirm, cancel.
- Confirm submits `MapActionInput`; cancel restores committed projection without server mutation.
- Add map command tools for player movement, door/object interaction, target/location selection.
- Validate player token ownership, path, movement allowance, obstacle, turn order, map/session version.
- Publish final position/rejection with same GM Turn SSE version.

## Policy Unit Tests

- only player-controlled party tokens are draggable.
- candidate never mutates committed state.
- path must be adjacent, in bounds, unblocked, and within rule allowance.
- stale map version and duplicate command behavior are deterministic.

## Integration and Contract Tests

- Adventure Tool Gateway → Combat Map versioned command.
- move/interact command replay and ownership authorization.
- committed tactical projection version equals GM response session version.

## UI ~ Entity E2E

- drag → cancel: no API command, token returns.
- drag → confirm: one GM Turn, one map mutation, narration and token update together.
- invalid move: persisted map unchanged, reason shown.

## Implementation Scope

- combat-map movement/interaction domain and APIs
- Adventure map tool adapter
- web tactical-map feature, window state, candidate/popover
- browser/system tests

## Out of Scope

Fog/LOS, hidden monsters/traps, last-seen semantics.

## Completion

- Confirm/cancel and authoritative movement tests pass.
- Status becomes `completed`; 032-8 waits for 032-5 too.

## Execution

- Added typed map-action candidate state with square-grid rendering, player-token selection/drag/drop, obstacle cells, legend, and confirmation popover.
- Cancel restores the committed projection locally; confirmation submits exactly one idempotent `MAP_ACTION` GM Turn.
- Extended player-safe combat-map response with grid and obstacle metadata; preserved internal authoritative movement validation and versioning.
- Web UI: 26 test files / 76 tests passing; combat-map movement tests and typecheck passing.

## Review Follow-up

- Map command IDs now remain UUIDs; current session version is forwarded and the committed map projection reloads after confirmation.
- Confirmation is single-flight; Adventure validates the typed payload and routes movement through the versioned Combat Map gateway before committing the turn.
- Added adjacent path expansion plus target, location, and object-interaction candidate controls.
- Replaced Adventure map read stub with owner-scoped latest projection gateway and adventure-keyed Combat Map read endpoint.
