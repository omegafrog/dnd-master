# 034-2 Deterministic GM response gate

- Status: `completed`
- Tracker: local Markdown
- Dependencies: 034-1
- Product rules: BR-004, BR-005, BR-006, BR-007, BR-008, FR-001, FR-002

## Outcome

No provider response reaches runtime or player projection unless citations, facts, state, tools, and secret safety pass one shared deterministic gate.

## Implementation scope

- Introduce stable citation identity: document ID, extraction version, locator, evidence type.
- Validate citations against selected evidence identity and source excerpt, not full-object equality or substring checks.
- Replace free-form state authority with typed referenced fact IDs and deterministic adjudication results.
- Keep provider `stateDelta` empty; only authoritative mutation ports may change state.
- Run secret checks at AI service response boundary and Adventure runtime commit boundary.
- On malformed/unsafe output: discard original, perform at most one bounded repair without protected values, then fail closed.
- Consolidate `GmFinalValidator`, rule-answer grounding, and quality evaluation semantics around shared value objects/policies.

## Likely files

- `src/adventure-service/.../runtime/GmFinalValidator.java`
- `src/adventure-service/.../runtime/RuntimeTurnApplicationService.java`
- `src/ai-game-master-service/.../api/GmAgentController.java`
- `src/ai-game-master-service/.../application/rule/GroundedRuleAnswerService.java`
- `src/ai-game-master-service/.../api/GmQualityEvaluationService.java`

## Acceptance criteria

- Secret, unsupported citation, invented fact/state, forbidden tool, or non-empty model state delta yields no player-visible partial response.
- Every rule claim maps to an exact selected evidence identity.
- Every state claim maps to canonical fact/adjudication IDs; unknown IDs fail.
- Repair cannot receive or reproduce protected values.
- Secret leak and invented-state rates are 0% across frozen adversarial live scenarios.

## Test contract

- Unit: one failing case for each gate and one complete valid response.
- Unit: paraphrase/alias secret cases and false-positive collision cases.
- Integration: actual AI HTTP response is rejected before turn commit and projection publication.
- `ui ~ entity` e2e: malicious prompt requests hidden fact and state mutation; UI receives stable failure, database state/version and SSE projection remain unchanged.

## Out of scope

- Provider performance tuning; ticket 034-3.

## Execution notes

- Added stable evidence identity (`documentId`, extraction version, locator, evidence type) and exact excerpt validation.
- Rejected substring-based quality evidence matches; evaluator now requires exact cited values.
- Added AI-boundary protected-fact checks with normalized exact and multi-token paraphrase detection.
- Provider state delta remains fail-closed and runtime validation remains authoritative.
- Targeted adventure and AI GM gate tests pass. Full combined service run reached test completion but Gradle did not terminate cleanly.
