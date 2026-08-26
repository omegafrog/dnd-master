# RAG-018 runtime checkpoint

- Plan: RAG-018 / parent #189
- Status: `in-progress`
- orchestration_state: `handoff-required`
- attempt: 5
- handoff_reason: `same-slot-recovery`
- Worktree: `/tmp/dnd-rag-product-plan`
- Dependency: RAG-016 is `completed`

## Current stage

The prior RAG-018 review findings and the order-independent citation-key fix are implemented and verified. This same-slot recovery found the inherited two-file patch already committed as `d6687f0d`; no code correction was needed. Keep the official plan status `in-progress` pending main re-review. Do not start RAG-019.

## Handoff evidence

- Required product and architecture specs read.
- RAG-016 plan and completed checkpoint read.
- Recovery Git state recorded: the inherited two-file patch was present in `d6687f0d`; only this checkpoint was modified and the pre-existing untracked `src/preprocessing_agent.egg-info/` remained out of scope and preserved.
- Graph query located the story-plan generation application service, generation port, stage source validator, gateway, projection, and related tests.
- Same-slot recovery read the implementation skill, AGENTS.md, plans index, RAG-018 and RAG-019 plans, product/architecture specs, and this checkpoint before verification.
- Failing-first evidence: `reserves_later_caller_key_before_assigning_generated_key_to_blank_citation` failed before the production change with `IllegalArgumentException` from `withCitationKeys()` when a blank citation consumed `citation-1` before the later caller key was seen.
- Green evidence: the same regression assigns `citation-2`, preserves the caller's `citation-1`, and assigns `citation-3`; the existing duplicate-key, gateway serialization, unknown-key, and canonical provenance tests remain green.

## Attempt 5 verified files

- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/storyplan/AdventureStoryPlanGenerationPort.java`
- `src/adventure-service/src/test/java/com/dndmaster/adventure/infrastructure/integration/CrossContextHttpAdventureStoryPlanGenerationGatewayTest.java`
- `docs/plans/.runtime/RAG-018/checkpoint.md`

## Prior RAG-018 files retained

- `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/api/AdventureStoryPlanController.java`
- `src/ai-game-master-service/src/test/java/com/dndmaster/aigamemaster/api/AdventureStoryPlanControllerMarkdownTest.java`

## Test and review evidence

- Failing-first test: `reserves_later_caller_key_before_assigning_generated_key_to_blank_citation` failed before the inherited production change with the expected generated-key collision.
- Focused recovery pass: from `/tmp/dnd-rag-product-plan/src`, `./gradlew --no-daemon --rerun-tasks :adventure-service:test --tests com.dndmaster.adventure.infrastructure.integration.CrossContextHttpAdventureStoryPlanGenerationGatewayTest` — `BUILD SUCCESSFUL` (19 tests), with non-empty `INTERNAL_SERVICE_TOKEN`.
- Full recovery pass: from `/tmp/dnd-rag-product-plan/src`, `./gradlew --no-daemon --rerun-tasks :adventure-service:test :ai-game-master-service:test` — `BUILD SUCCESSFUL` (10 tasks executed, 1m 59s), with non-empty `INTERNAL_SERVICE_TOKEN`, `RULE_KNOWLEDGE_BACKOFFICE_ADMIN_PLAYER_IDS`, and `CODEX_EXECUTABLE`.
- `git diff --check` — passed before checkpoint update.
- `graphify update .` — passed; no code-graph topology changes detected, with the expected missing SQL parser and empty-node warnings.
- Independent review remains pending for the main wrapper; no subagent was spawned.

## Commit evidence

- Inherited implementation commit: `d6687f0d` (`fix(rag): reserve caller citation keys before generation`) contains the two code files listed above and the inverse-order regression test.
- Recovery checkpoint commit: `0c1cfa0e1b42347b5fc5f452151af104afe52123` (`docs(rag): record RAG-018 attempt 5 recovery`), containing only `docs/plans/.runtime/RAG-018/checkpoint.md`.
- Previous implementation commits: `0c473901`, `b955c6ec`.
- Expected post-commit Git status: only pre-existing untracked `src/preprocessing_agent.egg-info/`; preserve and leave unstaged.

## Blocker

- No implementation or test blocker. Official status reconciliation remains intentionally pending: RAG-018 stays `in-progress` and RAG-019 stays `planned` until the main wrapper completes independent re-review.

## Next actions

- exact next_action: Main wrapper performs independent RAG-018 re-review and official status reconciliation; do not implement RAG-019 or mark RAG-018 completed, and keep `src/preprocessing_agent.egg-info/` unstaged.

## Scope guard

Do not change preprocessing, embeddings, PGVector, model selection, unrelated UI, or unrelated user changes. Preserve exact citation provenance and validation semantics; do not add fuzzy matching or locator repair.
