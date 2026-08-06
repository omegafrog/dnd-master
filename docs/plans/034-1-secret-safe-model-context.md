# 034-1 Secret-safe model context

- Status: `completed`
- Tracker: local Markdown
- Dependencies: none
- Product rules: BR-001, BR-006, BR-008, FR-005, FR-007

## Outcome

GM-only, unrevealed, unauthorized, or out-of-scope facts never enter model input. Model receives a typed, visibility-filtered context projection; authoritative private state remains available only to deterministic policies.

## Implementation scope

- Replace untyped `storyPlanContext`/`StoryContinuityContext.promptText()` flow with a typed model-input projection.
- Define explicit visibility states and a fail-closed projection policy for continuity facts, story evidence, plan data, map data, and character context.
- Enforce owner, session, package, document, extraction-version, and disclosure-state scope before `RuntimePlanningRequest` creation.
- Keep private facts in authoritative repositories; never encode secret values or reversible aliases in model input.
- Make cross-context retrieval adapters return visibility/provenance needed for local revalidation.
- Record projection audit metadata without recording secret values.

## Likely files

- `src/adventure-service/.../runtime/StoryContinuityContext.java`
- `src/adventure-service/.../runtime/RuntimeTurnApplicationService.java`
- `src/adventure-service/.../runtime/RuntimePlanningRequest.java`
- `src/adventure-service/.../integration/CrossContextHttpRuntimeEvidenceSearchGateway.java`
- `src/ai-game-master-service/.../api/GmAgentController.java`

## Acceptance criteria

- Hidden/unrevealed facts cannot be found in serialized provider requests.
- Evidence with mismatched owner, session, package, document, version, or disclosure state is rejected before provider invocation.
- Missing visibility metadata fails closed.
- Existing player-visible rule/story evidence still reaches model with stable provenance.
- Audit logs contain IDs and decisions, not prompt content or protected values.

## Test contract

- Unit: projection policy covers every visibility state and every scope mismatch.
- Unit: serialization scan proves protected literals and aliases absent from provider request.
- Integration: PostgreSQL-backed continuity and story evidence produce only player-visible model context.
- `ui ~ entity` e2e: player asks about an unrevealed trap; response and browser/network payload expose no trap name, location, image, or locator, then reveal event makes only allowed evidence available.

## Out of scope

- Model-output hard gating; ticket 034-2.
- Retrieval ranking quality; ticket 034-4.

## Execution notes

- Added `ModelInputProjection` as a fail-closed provider boundary.
- GM-only and unrevealed evidence are excluded; scope mismatch rejects before planning.
- Continuity projection includes public facts and clock/revision metadata only; checkpoint and plan-detail text stay authoritative.
- Runtime planning requests carry the typed projection and projected evidence pack; authoritative evidence remains in the committed turn.
- Added projection tests for hidden-literal redaction, scope rejection, and missing visibility behavior.
