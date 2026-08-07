# 035-5 UI five-turn and combat acceptance journey

- Status: `planned`
- Tracker: local Markdown
- Dependencies: 035-1, 035-2, 035-3, 035-4
- Product rules: BR-001 through BR-010, AC-001 through AC-007

## Outcome

One repeatable real-browser scenario proves that a Solo Player can use only the frontend UI to index supplied Rulebook/Storybook assets, create a bundle, create a character/party, start an adventure, exchange five GM turns, enter and resolve combat, and observe only player-safe information.

## Implementation scope

- Build a deterministic UI fixture using assets already available to the application.
- Drive upload, indexing completion, bundle generation, provider selection, character/party creation, and adventure start through visible UI.
- Execute five semantically distinct player actions, including a perception/skill check, saving throw, multiple dice action, and combat actions.
- Capture browser-visible evidence: screenshots, UI state, network assertions, turn cursor, combat state, and failure/retry behavior.
- Fail the suite on hidden DC/internal ID leakage, uncommitted partial turn, duplicate roll, or rule/combat mismatch.
- Document environment prerequisites and artifact retention without mutating input files or DB directly.

## Likely files

- `src/web-ui/e2e/backend-ui-journey.spec.ts`
- `src/web-ui/e2e/backend-runtime-binding.spec.ts`
- `src/web-ui/e2e/backend-story-rag-visibility.spec.ts`
- `src/web-ui/e2e/fixtures/main.tsx`
- `src/web-ui/src/features/adventure/AdventureStream.tsx`
- `src/system-tests/src/test/java/com/dndmaster/system/SoloAdventureE2ETest.java`
- `docs/testing/*`

## Acceptance criteria

- The browser creates or selects indexed Rulebook/Storybook assets through UI controls only.
- Adventure starts with a locked bundle, character, party, provider, and runtime configuration.
- Five GM turns complete in order and each committed turn advances the cursor exactly once.
- At least one save, multiple dice roll, attack, damage, combat entry, and combat exit are observed.
- Story drives the player toward the next objective and does not expose hidden plan details.
- UI/network assertions prove no DC, hidden ending, internal ID, raw prompt, or protected fact leakage.
- The run produces a report with pass/fail evidence and preserves unrelated dirty worktree changes.

## Test contract

- Unit: scenario command builders and assertion helpers for turn cursor, visibility, and idempotency.
- Integration: real service path with configured local provider and persistence; no DB writes outside application behavior.
- `UI ~ entity` E2E: one Playwright test covers the complete indexed-assets → five-turn → combat journey using only frontend actions.

## Out of scope

- Fixing provider, projection, rules, or readiness behavior; tickets 035-1 through 035-4.
- Performance benchmark or model fine-tuning.
