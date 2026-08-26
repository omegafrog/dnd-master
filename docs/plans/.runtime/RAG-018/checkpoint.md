# RAG-018 runtime checkpoint

- Plan: RAG-018 / parent #189
- Status: `in-progress`
- orchestration_state: `handoff-required`
- attempt: 4
- handoff_reason: `same-plan-review-follow-up`
- Worktree: `/tmp/dnd-rag-product-plan`
- Dependency: RAG-016 is `completed`

## Current stage

The prior RAG-018 review findings and the attempt-4 ordering P1 are implemented and verified. Keep the official plan status `in-progress`; stop after this follow-up commit so the main wrapper can run independent review. Do not start RAG-019.

## Handoff evidence

- Required product and architecture specs read.
- RAG-016 plan and completed checkpoint read.
- Current Git state recorded: only pre-existing untracked `src/preprocessing_agent.egg-info/`; it is out of scope and must be preserved.
- Graph query located the story-plan generation application service, generation port, stage source validator, gateway, projection, and related tests.
- Fresh-context handoff read the implementation skill, AGENTS.md, plans index, RAG-018 plan, product/architecture specs, and this checkpoint before implementation.
- Failing-first evidence: `reserves_later_caller_key_before_assigning_generated_key_to_blank_citation` failed before the production change with `IllegalArgumentException` from `withCitationKeys()` when a blank citation consumed `citation-1` before the later caller key was seen.
- Green evidence: the same regression now assigns `citation-2`, preserves the caller's `citation-1`, and assigns `citation-3`; the existing duplicate-key, gateway serialization, and canonical provenance tests remain green.

## Attempt 4 changed files

- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/storyplan/AdventureStoryPlanGenerationPort.java`
- `src/adventure-service/src/test/java/com/dndmaster/adventure/infrastructure/integration/CrossContextHttpAdventureStoryPlanGenerationGatewayTest.java`
- `docs/plans/.runtime/RAG-018/checkpoint.md`

## Prior RAG-018 files retained

- `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/api/AdventureStoryPlanController.java`
- `src/ai-game-master-service/src/test/java/com/dndmaster/aigamemaster/api/AdventureStoryPlanControllerMarkdownTest.java`

## Test and review evidence

- Failing-first test: `reserves_later_caller_key_before_assigning_generated_key_to_blank_citation` failed before production changes with the expected generated-key collision.
- Focused pass: `./gradlew :adventure-service:test --tests com.dndmaster.adventure.infrastructure.integration.CrossContextHttpAdventureStoryPlanGenerationGatewayTest` — `BUILD SUCCESSFUL` (19 tests).
- Focused pass: `./gradlew :ai-game-master-service:test --tests com.dndmaster.aigamemaster.api.AdventureStoryPlanControllerMarkdownTest` — `BUILD SUCCESSFUL`.
- Full pass: `./gradlew :adventure-service:test :ai-game-master-service:test` — `BUILD SUCCESSFUL` after the final production change, with non-empty `INTERNAL_SERVICE_TOKEN`, `RULE_KNOWLEDGE_BACKOFFICE_ADMIN_PLAYER_IDS`, and `CODEX_EXECUTABLE` set. The run exited 0.
- `git diff --check` — passed.
- `graphify update .` — passed; graph rebuilt with expected missing SQL parser/empty-node warnings and skipped oversized HTML visualization.
- Independent review is intentionally deferred to the main wrapper after this commit; no subagent was spawned.

## Commit evidence

- Follow-up commit: created after the attempt-4 implementation, test, diff-check, graphify, and checkpoint evidence; the final Git commit is reported in the handoff.
- Previous implementation commits: `0c473901`, `b955c6ec`.
- Current Git status after the follow-up commit: only pre-existing untracked `src/preprocessing_agent.egg-info/`; preserved and out of scope.

## Blocker

- No code or test blocker. The official plan-status reconciliation remains intentionally pending: RAG-018 stays `in-progress` and RAG-019 stays `planned` until the main wrapper completes independent review.

## Next actions

- exact next_action: Main wrapper runs independent review after the attempt-4 follow-up commit; resolve only any newly confirmed in-scope finding, then perform official plan-status reconciliation separately. Keep `src/preprocessing_agent.egg-info/` unstaged.

## Scope guard

Do not change preprocessing, embeddings, PGVector, model selection, unrelated UI, or unrelated user changes. Preserve exact citation provenance and validation semantics; do not add fuzzy matching or locator repair.
