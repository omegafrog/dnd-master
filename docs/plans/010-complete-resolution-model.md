# Plan 010: Complete Resolution Model

## Status

Completed. Structured resolution detail, richer kinds, validators, persistence/API contract, and package detail UI are in place. AI extraction contract now accepts the richer schema; dedicated evaluation corpus expansion remains follow-up work if needed.

## Issue

[GitHub #39](https://github.com/omegafrog/dnd-master/issues/39)

## Dependencies

- Plan 009

## Spec Trace

- `REQ-RES-006`, `REQ-RES-008` through `REQ-RES-010`, `REQ-RES-018`, `REQ-RES-019`
- `UC-003`

## Outcome

Scenario Package represents all approved 판정·굴림 forms, including ordered multi-step procedures and source-grounded outcomes.

## Implementation Scope

- Add attack, damage, healing, opposed, initiative, recharge, random-table, special-roll kinds.
- Add ordered steps, conditions, modifiers, advantage/disadvantage, reroll, actor/roller, result visibility.
- Extract rolls from stat blocks without structuring entire stat blocks.
- Add type-specific validators and runtime capability metadata.
- Expand package detail UI and evaluation corpus.

## Acceptance Criteria

- Ordered save-to-damage procedures preserve dependency and half/full outcomes.
- Random table ranges are complete or explicitly PARTIAL.
- Unstated outcomes and modifiers are never synthesized.
- Unknown actor/roller/visibility remains PARTIAL.
- Unsupported engine capability is reported without invalidating source evidence.

## Test Contract

- Policy unit per Resolution kind and compound procedure.
- Golden/evaluation: varied Korean/English prose, DC/dice/modifier/source exactness.
- Contract: Package detail schema for every kind.
- UI-to-entity E2E: compile advanced fixtures -> persisted steps/outcomes -> accurate detail/warnings.

## Excluded

- Runtime command execution.
- Custom user-defined Resolution types.
