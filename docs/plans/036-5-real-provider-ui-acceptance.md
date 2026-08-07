# 036-5 Real-provider UI five-turn and combat acceptance

- Status: `planned`
- Tracker: local Markdown
- Dependencies: 036-1, 036-2, 036-3, 036-4
- Product rules: BR-001 through BR-010, AC-001 through AC-007

## 구현 목적

실제 Ollama/OpenAI-compatible provider와 사용자-visible UI만 사용해 자료 준비부터 5턴·전투·재시도까지 검증한다. browser secrecy와 entity idempotency를 acceptance 증거로 남긴다.

## Outcome

One repeatable Playwright journey proves indexed assets → five turns → checks → combat → completion through UI only.

## Scope

- Use UI for assets, indexing, bundle, provider, character/party, start, retry, and actions.
- Forbid fixture GM responses and direct API/database gameplay mutation.
- Cover exploration, check, save/opposed check, combat entry, attack/damage, completion.
- Capture DOM, screenshots, network, SSE, console, and sanitized diagnostics.
- Verify no private keys, canaries, prompts, provider bodies, or hidden excerpts.
- Verify retry identity, cursor, rolls, HP, initiative, and mutation idempotency.

## Acceptance

- Browser completes setup and five turns with live provider.
- Rulebook-grounded checks and combat complete.
- No duplicate entities after retry.
- Slow/malformed reachable provider fails test with safe artifacts, never auto-skips.
- Report includes provider/model, schema, turn IDs, citation handles, result IDs, timings, artifacts.

## Test contract

- Unit: Playwright helpers for order, handles, canaries, cursor, dice, HP, initiative, retry.
- Integration: provider/assets/service preflight without gameplay state.
- `UI ~ entity` E2E: one tagged scenario covers full journey.

## Likely files

- `src/web-ui/e2e/{backend-ui-journey,backend-runtime-binding,backend-story-rag-visibility}.spec.ts`
- `src/web-ui/playwright.config.ts`
- `src/web-ui/src/features/adventure/AdventureStream.tsx`
- `src/system-tests/src/test/java/com/dndmaster/system/RetryIdempotencyIntegrationTest.java`
- `docs/testing/*`

## Out of scope

Fixture proof, direct gameplay seeding, unrelated model benchmarking.
