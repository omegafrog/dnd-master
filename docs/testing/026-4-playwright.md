# 026-4 Playwright regression

The local `player-journey.spec.ts` test is a deterministic UI contract fixture. The real extraction and persistence contract runs in `document-derived-backend.spec.ts` against a seeded `app-all` instance.

Required environment variables for the backend test:

- `BACKEND_E2E_URL`
- `BACKEND_E2E_TOKEN`
- `BACKEND_E2E_PLAYER_ID`
- `BACKEND_E2E_BUNDLE_ID`
- `BACKEND_E2E_SCENARIO_PACKAGE_ID`
- `BACKEND_E2E_SESSION_ID`
- `BACKEND_E2E_EMAIL` and `BACKEND_E2E_PASSWORD` for browser login
- optional `BACKEND_E2E_EXPECTED_BLUEPRINT_TERMS` (comma-separated 4e/5e and Storybook terms)

## Real character blueprint UI E2E

Run `npm run test:e2e:character:ui` from `src/web-ui` with `BACKEND_E2E_URL`, `BACKEND_E2E_EMAIL`, `BACKEND_E2E_PASSWORD`, and `BACKEND_E2E_SCENARIO_PACKAGE_ID` set. The package must already be `READY` with a published character blueprint, and the backend must be reachable by both Playwright's API request context and the Vite proxy. The test logs in through the browser, loads the real blueprint review page, creates a session through the real UI API, and verifies that the backend persisted the package and published revision.

If those variables are not set, Playwright reports the test as skipped with the exact missing variable names; it is not a fixture-only substitute.

The test attaches complete bundle, Blueprint-before/after-publish, and character creation request/response JSON. The local fixture rejects character creation unless the shared Blueprint state is published and the expected revision/node value are present.
