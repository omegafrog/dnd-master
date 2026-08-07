# 035-4 Runtime readiness and preflight

- Status: `completed`
- Tracker: local Markdown
- Dependencies: 035-1
- Product rules: BR-007, AC-006

## Outcome

Session setup and runtime share one explicit readiness result. Missing indexing, incompatible versions, missing provider capability, and unsupported definitions are visible as blockers or clearly labeled degraded capabilities; they never fail silently during start or first turn.

## Implementation scope

- Consolidate provider, package, rulebook, Game System Definition, character blueprint, tool, and map preflight checks.
- Define `ready`, `blocked`, and supported `degraded` states with safe reasons and retryability.
- Persist/read the readiness result against the locked Runtime Binding version.
- Expose readiness to setup UI and enforce the same result at adventure start.
- Add recovery behavior for transient provider/indexing/dependency failures.

## Likely files

- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/RuntimeBindingApplicationService.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/api/RuntimeBindingController.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/session/AdventureSessionApplicationService.java`
- `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/infrastructure/ai/OllamaStartupPreflight.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/GmProviderQualityGateStartupValidator.java`
- `src/web-ui/src/features/adventure-session/*`

## Acceptance criteria

- UI clearly distinguishes indexed-ready, indexing-pending, blocked, and supported-degraded states.
- Start is rejected with actionable blockers when required assets/versions/capabilities are missing.
- A degraded state cannot silently bypass a required rule or disclose incomplete runtime behavior.
- Readiness and actual start use the same locked binding/version and provider selection.
- Transient failures can be retried without creating duplicate bindings or sessions.

## Test contract

- Unit: readiness matrix for each blocker, warning, degraded capability, and version mismatch.
- Integration: binding/start checks agree across provider, rulebook, definition, character, dice, and map adapters.
- `UI ~ entity` E2E: create a bundle through UI with pending/failed prerequisites, verify the blocked state, recover through UI, then start successfully.

## Out of scope

- General RAG ranking quality.
- Full five-turn/combat acceptance scenario; ticket 035-5.
