# 026-4 Playwright regression

The local `player-journey.spec.ts` test is a deterministic UI contract fixture. The real extraction and persistence contract runs in `document-derived-backend.spec.ts` against a seeded `app-all` instance.

Required environment variables for the backend test:

- `BACKEND_E2E_URL`
- `BACKEND_E2E_TOKEN`
- `BACKEND_E2E_PLAYER_ID`
- `BACKEND_E2E_BUNDLE_ID`
- `BACKEND_E2E_SCENARIO_PACKAGE_ID`
- `BACKEND_E2E_SESSION_ID`
- optional `BACKEND_E2E_EXPECTED_BLUEPRINT_TERMS` (comma-separated 4e/5e and Storybook terms)

Safe Story RAG browser/PostgreSQL coverage runs with:

- `BACKEND_E2E_ADVENTURE_ID`
- `BACKEND_E2E_HIDDEN_STORY_EXCERPT`
- optional `BACKEND_E2E_REVEALED_STORY_REF` (defaults to `storybook:e2e:hidden-story`)

Run `npm run test:e2e -- e2e/backend-story-rag-visibility.spec.ts` from `src/web-ui`.
The seed must mark the story chunk `REVEALED_AFTER_EVENT`, with `disclosureEvent=GM_TURN_COMMITTED` and `disclosureTurn=2`, and include its locator in the second turn’s citations. The Playwright browser talks to the Vite fixture, which proxies adventure requests to the seeded authenticated `app-all` instance. Story evidence is retrieved from PostgreSQL; no evidence is injected by the browser. The test checks immediate HTTP projection, the pre-reveal UI, the post-reveal source reference, and the PostgreSQL-backed conversation after reload.

The test attaches complete bundle, Blueprint-before/after-publish, and character creation request/response JSON. The local fixture rejects character creation unless the shared Blueprint state is published and the expected revision/node value are present.
