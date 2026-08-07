# 035-1 Typed GM response and recovery

- Status: `completed`
- Tracker: local Markdown
- Dependencies: none
- Product rules: BR-003, BR-005, BR-009, FR-002, AC-003

## Outcome

Malformed, incomplete, unsafe, or provider-specific GM responses become typed failures or validated candidates. A failed response never commits a partial turn, and the UI receives a retryable, safe failure contract.

## Implementation scope

- Replace loosely typed provider response handling with a closed GM response contract.
- Define failure categories, retryability, safe player message, and correlation identity.
- Keep repair bounded to one attempt and exclude protected values from repair input/output.
- Preserve deadline/cancellation semantics across provider call, repair, and validation.
- Map backend failure categories to UI states with retry behavior and no raw provider payload.

## Likely files

- `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/api/GmAgentController.java`
- `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/infrastructure/ai/GmCompletionAdapter.java`
- `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/infrastructure/ai/OpenAiGmProvider.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/RuntimeTurnApplicationService.java`
- `src/web-ui/src/features/adventure/AdventureApi.ts`
- `src/web-ui/src/features/adventure/AdventureStream.tsx`

## Acceptance criteria

- Valid responses from configured providers complete a turn.
- Malformed initial and repair responses produce one typed failure and no committed turn/version change.
- Provider timeout, schema error, grounding error, and dependency error are distinguishable and safely rendered.
- Retry uses the original command identity without duplicating a successful turn.
- Protected context, raw prompt, and raw provider output never appear in player-visible failure responses.

## Test contract

- Unit: parser, required fields, unknown tool, bounded repair, deadline exhaustion, and failure taxonomy.
- Integration: Ollama/OpenAI malformed response and timeout paths prove no turn/event publication.
- `UI ~ entity` E2E: submit an action against a controlled malformed provider, verify retry UI and unchanged adventure cursor, then retry successfully.

## Out of scope

- Cross-feature hidden-information projection; ticket 035-2.
- Rule and combat command implementation; ticket 035-3.
- Full five-turn browser journey; ticket 035-5.
