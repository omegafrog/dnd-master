# 035-2 Player projection and secret safety

- Status: `completed`
- Tracker: local Markdown
- Dependencies: 035-1
- Product rules: BR-001, BR-002, BR-007, AC-002

## Outcome

All player-visible story, runtime, tool, citation, conversation, and combat-map outputs pass through one disclosure policy. Hidden facts, DCs, unrevealed branches, GM-only state, and internal identifiers are rejected or omitted before publication.

## Implementation scope

- Define a single typed `PlayerProjection` contract and disclosure policy.
- Apply it to prologue, GM narration/judgment, citations, tool results, runtime snapshots, and combat-map views.
- Validate evidence identity, session/package/version scope, and disclosure state before publication.
- Add negative tests for values hidden in alternate fields, warnings, tool arguments, map metadata, and error text.
- Keep authoritative private state available to deterministic runtime policies without exposing it to UI/model projections.

## Likely files

- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/ModelInputProjection.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/GmFinalValidator.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/RuntimeTurnApplicationService.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/prologue/AdventurePrologueApplicationService.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/combat/*`
- `src/combat-map-service/src/main/java/com/dndmaster/combatmap/*`
- `src/web-ui/src/features/adventure/AdventureStream.tsx`

## Acceptance criteria

- Prologue and five-turn responses do not reveal DC 13, hidden endings, internal IDs, or unrevealed facts.
- Hidden information is not present in API JSON, SSE payloads, browser-visible network responses, citations, warnings, or error messages.
- Public evidence remains available with stable provenance after its disclosure condition is met.
- Scope/version/disclosure mismatches fail closed without leaking the rejected value.
- Combat-map visibility and story visibility use compatible public projection rules.

## Test contract

- Unit: every disclosure state, scope mismatch, alternate-field leak, and internal-ID redaction case.
- Integration: prologue/runtime/combat projections use the same policy and preserve public evidence.
- `UI ~ entity` E2E: request hidden trap/DC/ending through the browser, inspect rendered UI and network payloads, then trigger an allowed reveal and verify only the permitted fact appears.

## Out of scope

- Provider parsing/retry behavior; ticket 035-1.
- Rule adjudication correctness; ticket 035-3.
