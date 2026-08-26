# RAG-018 runtime checkpoint

- Plan: RAG-018 / parent #189
- Status: `in-progress`
- orchestration_state: `handoff-required`
- attempt: 3
- handoff_reason: `same-plan-review-follow-up`
- Worktree: `/tmp/dnd-rag-product-plan`
- Dependency: RAG-016 is `completed`

## Current stage

The two recorded RAG-018 review findings are implemented and verified. Keep the official plan status `in-progress`; stop after the follow-up commit so the main wrapper can run independent review. Do not start RAG-019.

## Handoff evidence

- Required product and architecture specs read.
- RAG-016 plan and completed checkpoint read.
- Current Git state recorded: only pre-existing untracked `src/preprocessing_agent.egg-info/`; it is out of scope and must be preserved.
- Graph query located the story-plan generation application service, generation port, stage source validator, gateway, projection, and related tests.
- Fresh-context handoff read the implementation skill, AGENTS.md, plans index, RAG-018 plan, product/architecture specs, and this checkpoint before implementation.
- Failing-first evidence: the duplicate-key test failed before the production change because the gateway sent the request and surfaced a downstream `IllegalStateException`; it passed after deterministic duplicate rejection was added.

## Attempt 3 changed files

- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/storyplan/AdventureStoryPlanGenerationPort.java`
- `src/adventure-service/src/test/java/com/dndmaster/adventure/infrastructure/integration/CrossContextHttpAdventureStoryPlanGenerationGatewayTest.java`
- `docs/plans/.runtime/RAG-018/checkpoint.md`

## Prior RAG-018 files retained

- `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/api/AdventureStoryPlanController.java`
- `src/ai-game-master-service/src/test/java/com/dndmaster/aigamemaster/api/AdventureStoryPlanControllerMarkdownTest.java`

## Test and review evidence

- Failing-first test: `rejects_duplicate_caller_citation_keys_before_sending_or_resolving` failed before production changes; the stable-key gateway test also ran through the pre-existing resolver but did not cover caller-key serialization/provenance until this follow-up.
- Focused pass: `./gradlew :adventure-service:test --tests com.dndmaster.adventure.infrastructure.integration.CrossContextHttpAdventureStoryPlanGenerationGatewayTest` — `BUILD SUCCESSFUL` (18 tests).
- Focused pass: `./gradlew :ai-game-master-service:test --tests com.dndmaster.aigamemaster.api.AdventureStoryPlanControllerMarkdownTest` — `BUILD SUCCESSFUL`.
- Full pass: `./gradlew :adventure-service:test :ai-game-master-service:test` — `BUILD SUCCESSFUL` after the final production change, with non-empty `INTERNAL_SERVICE_TOKEN`, `RULE_KNOWLEDGE_BACKOFFICE_ADMIN_PLAYER_IDS`, and `CODEX_EXECUTABLE` set. The run emitted pre-existing H2 scheduler shutdown warnings but exited 0.
- `git diff --check` — passed.
- `graphify update .` — passed; graph rebuilt with expected missing SQL parser/empty-node warnings.
- Independent review is intentionally deferred to the main wrapper after this commit; no subagent was spawned.

## Commit evidence

- Follow-up commit: created after the implementation, test, diff-check, and graphify evidence recorded above; the final Git commit is reported in the handoff.
- Previous implementation `HEAD`: `0c473901`.
- Current Git status after the follow-up commit: only pre-existing untracked `src/preprocessing_agent.egg-info/`; preserved and out of scope.

## Blocker

- No code or test blocker. The official plan-status reconciliation remains intentionally pending: RAG-018 stays `in-progress` and RAG-019 stays `planned` until the main wrapper completes independent review.

## Next actions

- exact next_action: Main wrapper runs independent review after the follow-up commit; resolve only any newly confirmed in-scope finding, then perform official plan-status reconciliation separately. Keep `src/preprocessing_agent.egg-info/` unstaged.

## Scope guard

Do not change preprocessing, embeddings, PGVector, model selection, unrelated UI, or unrelated user changes. Preserve exact citation provenance and validation semantics; do not add fuzzy matching or locator repair.
