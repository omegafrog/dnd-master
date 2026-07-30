# 025-3 Validated State Effects Runtime Saga

- Issue: #104
- Status: blocked
- Dependencies: #103 / plan 025-2

## Goal

Apply only rule-derived effects of a resolved roll through their owning contexts, safely resume partial work, and finalize narration after state acknowledgement.

## Scope

- Derive `ValidatedEffect` only from a cited Resolution Unit and `ResolvedCheck`.
- Add idempotent, version-conditional commands for character HP/resources/conditions/inventory and applicable combat-map state.
- Persist RuntimeTurn phase, effect commands, acknowledgements, and roll references for resume/audit.
- Finalize GM narration from immutable resolved facts and acknowledged effects only.

## Acceptance Criteria

- Arbitrary GM prose cannot change HP, inventory, resources, conditions, NPC combat state, or map state.
- A mid-saga provider failure returns resumable pending state; retry does not duplicate dice or effects.
- Final narration is not committed before all required owner acknowledgements.
- UI shows applied effects and failure/retry state.

## Test Contract

- Unit: effect derivation/authorization rejects uncited or foreign-owner effects.
- Integration: expected-version conflict, idempotent owner command, RuntimeTurn resume.
- UI ↔ entity E2E: resolved action changes an allowed owner state once, survives retry, then exposes final narration/effect list.

## Implementation Areas

- `adventure-service` RuntimeTurn saga/domain policy/persistence.
- `character-management-service` effect commands and acknowledgements.
- `combat-map-service` only for supported map effects.
- `ai-game-master-service` final narration contract.
- `web-ui` effect and pending/recovery state.

