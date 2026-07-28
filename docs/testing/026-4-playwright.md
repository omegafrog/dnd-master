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

The test attaches complete bundle, Blueprint-before/after-publish, and character creation request/response JSON. The local fixture rejects character creation unless the shared Blueprint state is published and the expected revision/node value are present.
