# 036-5 Real-provider UI five-turn and combat acceptance

- Status: `planned`
- Tracker: local Markdown
- Dependencies: 036-1, 036-2, 036-3, 036-4
- Product rules: BR-001 through BR-010, AC-001 through AC-007

## 구현 목적

fixture가 아닌 실제 Ollama/OpenAI provider와 실제 프런트 UI만 사용해 전체 모험을 검증한다. 다섯 턴의 스토리 진행, 여러 판정, 전투, 재시도, 원자성, 브라우저 비밀 누출 방지를 한 acceptance journey로 증명한다.

## Outcome

One repeatable Playwright journey proves through frontend UI only that a real Ollama or OpenAI-compatible provider follows the locked Storybook, applies Rulebook-grounded checks and combat through authoritative services, advances five turns, and leaks no private information to the browser.

## Implementation scope

- Use the visible UI for Rulebook/Storybook upload or selection, indexing, bundle creation, provider selection, character/party setup, adventure start, retry, and all player actions.
- Require a live provider; fixture GM responses are forbidden for this acceptance lane.
- Wait asynchronously for Story Plan generation with a dedicated maximum deadline of 30 minutes and visible progress/failure state.
- Execute five semantically distinct turns covering exploration, perception/skill check, save or opposed check, combat entry, attack/damage, and combat completion.
- Assert Storybook objective order without exposing future or hidden facts in test output.
- Assert Rulebook-linked dice formulas, modifiers, outcomes, initiative, HP changes, and legal turn order.
- Capture DOM, screenshots, API responses, SSE events, console errors, and safe diagnostics as artifacts.
- Exercise provider timeout/malformed-response retry and verify command identity, cursor, rolls, and mutations remain idempotent.
- Fail rather than skip when the configured provider is reachable but slow, malformed, weak, or grounding-invalid. Skip only when the explicit acceptance lane is not requested.

## Likely files

- `src/web-ui/e2e/backend-ui-journey.spec.ts`
- `src/web-ui/e2e/backend-runtime-binding.spec.ts`
- `src/web-ui/e2e/backend-story-rag-visibility.spec.ts`
- `src/web-ui/playwright.config.ts`
- `src/web-ui/src/features/adventure/AdventureStream.tsx`
- `src/system-tests/src/test/java/com/dndmaster/system/RetryIdempotencyIntegrationTest.java`
- `docs/testing/*`

## Acceptance criteria

- The browser completes setup and five GM turns without direct API, database, or backend test-fixture mutation.
- Story events remain consistent with the locked Storybook and do not reveal future objectives, hidden endings, NPC secrets, or secret DCs.
- At least one perception/skill check, one save or opposed check, initiative, attack, damage, HP mutation, and combat exit are authoritatively verified.
- Each committed action advances the cursor once and creates no duplicate roll, damage, HP, or turn record after retry.
- All captured browser traffic and DOM are free of private-state keys, protected canaries, raw prompts, provider bodies, and hidden evidence excerpts.
- Story Plan generation may take up to 30 minutes; exceeding it yields a visible safe failure and no partial adventure state.
- A reachable provider failure is reported as test failure with sanitized artifacts, never converted to automatic skip.
- The run emits a concise acceptance report containing provider/model, schema version, turn IDs, public citation handles, authoritative result IDs, timings, and artifact paths.

## Test contract

- Unit: Playwright assertion helpers for story order, citation handles, network canaries, cursor identity, dice formulas, HP, initiative, and retry invariants.
- Integration: preflight verifies provider reachability, model availability, schema capability, indexed assets, and service readiness without creating gameplay state.
- `UI ~ entity` E2E: one tagged Playwright scenario covers indexed assets → Story Plan → five turns → checks → combat → completion through frontend UI only.

## Out of scope

- Deterministic fixture responses as proof of real-provider acceptance.
- Direct database seeding or backend calls from the browser test to advance gameplay.
- Model quality benchmarking unrelated to this fixed acceptance story.
