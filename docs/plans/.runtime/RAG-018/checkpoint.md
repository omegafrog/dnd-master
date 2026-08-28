# RAG-018 runtime checkpoint

- Plan: RAG-018 / parent #189
- Status: `completed`
- orchestration_state: `completed`
- attempt: 5
- handoff_reason: `same-slot-recovery`
- Worktree: `/tmp/dnd-rag-product-plan`
- Dependency: RAG-016 is `completed`

## Current stage

The prior RAG-018 review findings and the order-independent citation-key fix are implemented and verified. This same-slot recovery found the inherited two-file patch already committed as `d6687f0d`; no code correction was needed. Main's independent review recorded Standards `PASS` and Spec `PASS` with no findings. Official RAG-018 status is now `completed`, and dependent RAG-019 is promoted to `ready-for-agent`. Do not implement RAG-019 in this task.

## Handoff evidence

- Required product and architecture specs read.
- RAG-016 plan and completed checkpoint read.
- Recovery Git state recorded: the inherited two-file patch was present in `d6687f0d`; only this checkpoint was modified and the pre-existing untracked `src/preprocessing_agent.egg-info/` remained out of scope and preserved.
- Graph query located the story-plan generation application service, generation port, stage source validator, gateway, projection, and related tests.
- Same-slot recovery read the implementation skill, AGENTS.md, plans index, RAG-018 and RAG-019 plans, product/architecture specs, and this checkpoint before verification.
- Failing-first evidence: `reserves_later_caller_key_before_assigning_generated_key_to_blank_citation` failed before the production change with `IllegalArgumentException` from `withCitationKeys()` when a blank citation consumed `citation-1` before the later caller key was seen.
- Green evidence: the same regression assigns `citation-2`, preserves the caller's `citation-1`, and assigns `citation-3`; the existing duplicate-key, gateway serialization, unknown-key, and canonical provenance tests remain green.
- Main independent review: Standards `PASS`; Spec `PASS`; findings: none.

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
- Focused controller pass: `AdventureStoryPlanControllerMarkdownTest` — `BUILD SUCCESSFUL` (14 tests).
- `git diff --check` — passed before checkpoint update.
- `graphify update .` — passed; no code-graph topology changes detected, with the expected missing SQL parser and empty-node warnings.
- A separate read-only reviewer broad run reported six unrelated `NoClassDefFoundError` failures; it reported no focused RAG-018 failure. Those broad-run failures remain a limitation of that review run and are not treated as in-scope RAG-018 findings.

## Commit evidence

- Inherited implementation commit: `d6687f0d` (`fix(rag): reserve caller citation keys before generation`) contains the two code files listed above and the inverse-order regression test.
- Recovery checkpoint commit before self-reference correction: `49f43836917eef65f39ce933a36b2bc7217eb415` (`docs(rag): record RAG-018 attempt 5 recovery`), containing only `docs/plans/.runtime/RAG-018/checkpoint.md`.
- Previous implementation commits: `0c473901` (`fix(rag): close citation key review follow-ups`), `b955c6ec` (`fix(rag): reject duplicate citation keys`), and `d6687f0d` (`fix(rag): reserve caller citation keys before generation`).
- Evidence correction commit: `472151da` (`docs(rag): correct RAG-018 recovery evidence`).
- Expected post-commit Git status: only pre-existing untracked `src/preprocessing_agent.egg-info/`; preserve and leave unstaged.

## Blocker

- None in scope. The separate broad read-only run's six unrelated `NoClassDefFoundError` failures are recorded as a limitation; focused RAG-018 verification and independent review have no unresolved in-scope finding.

## Next actions

- exact next_action: RAG-018 reconciliation is complete. A future implementation slot may execute RAG-019 because it is `ready-for-agent`; keep `src/preprocessing_agent.egg-info/` unstaged.

## Scope guard

Do not change preprocessing, embeddings, PGVector, model selection, unrelated UI, or unrelated user changes. Preserve exact citation provenance and validation semantics; do not add fuzzy matching or locator repair.
