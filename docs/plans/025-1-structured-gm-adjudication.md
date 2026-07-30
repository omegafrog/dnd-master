# 025-1 Structured GM Adjudication Proposal

- Issue: #102
- Status: ready
- Dependencies: none

## Goal

Turn an authenticated direct-player action into a grounded, structured `NO_CHECK`, `CHECK`, or `CLARIFY` proposal. This slice exposes pre-roll information but performs no dice roll or state effect.

## Scope

- Replace text-only runtime adjudication boundary with an evidence-cited proposal contract.
- Resolve player identity from authentication and validate active character, owner, turn cursor, and expected adventure version.
- Validate proposal schema, cited evidence, selected rule set, and limited-creation policy.
- Return a transparent pre-roll view or a clarification/no-check response.
- Add UI action submission and proposal/clarification display.

## Acceptance Criteria

- A cited `CHECK` shows type, ability/skill/tool, DC, advantage state, and source citations before rolling.
- An uncited effect, invented game fact, malformed proposal, wrong owner, or stale version does not mutate adventure state.
- Insufficient input yields conservative no-effect narration or `CLARIFICATION_REQUIRED`.
- Existing saved adventures still load.

## Test Contract

- Unit: proposal/evidence policy rejects uncited and out-of-scope proposals; direct-turn authorization and version checks.
- Integration: AI GM proposal schema and Adventure adapter mapping.
- UI ↔ entity E2E: authenticated player sends action, sees a cited pre-roll check and a clarification path; no character/adventure effect is persisted.

## Implementation Areas

- `adventure-service` RuntimeTurn/Adventure API and runtime application/domain policies.
- `ai-game-master-service` structured adjudication endpoint/adapter.
- `web-ui` action and pre-roll result components.

