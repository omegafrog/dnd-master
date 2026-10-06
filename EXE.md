# Build and E2E execution

- Treat the checkout used for the requested work as the single source for the build, development server, and Playwright test. Before building or launching anything, verify `pwd` and `git rev-parse --show-toplevel` identify the active checkout; record the checkout path and `git rev-parse HEAD` for the run.
- Run `src/start-dev.sh` from that checkout so its backend, frontend, and compiled classes come from the same worktree and revision. Do not use an already-running service merely because its health endpoint responds.
- Before E2E, identify the listener PID for port 8080 and verify `/proc/<pid>/cwd` resolves to the recorded active checkout or one of its descendants. Also verify the running Java classpath/build output points into that same checkout. For a local UI run, apply the same worktree check to the port 5173 frontend process.
- If a listener belongs to another checkout, stop or otherwise retire that stale local process, restart the approved launcher from the recorded active checkout, wait for health, and repeat the ownership checks. Do not run E2E until the checks pass.
- Launch Playwright from the launcher environment after its backend health and worktree checks pass. Capture the test command, active checkout path, source revision, backend/frontend PIDs, and their resolved working directories with the result so the E2E evidence is tied to the code that was built and run.
- For non-local environments, explicit user-supplied URL overrides remain allowed, but do not present those runs as verification of the local checkout.

## Development diagnostics

The optional diagnostic logs use `ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED`. The default is `false`, so diagnostic records are suppressed unless explicitly enabled. Start the local stack with logs enabled using:

```bash
ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED=true src/start-dev.sh
```

Disable them explicitly with `ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED=false src/start-dev.sh`, or leave the variable unset. The flag is read by both the adventure service and rule-knowledge service. Check the launcher output for records beginning `dev_`.

When enabled, the logs include turn IDs and proposed/accepted situation transitions; agent operation, status, failure class and bounded error response; scenario compilation/search counts for source excerpts, dense retrieval, BM25 retrieval, rank fusion, selected excerpts, extracted candidates, candidate validation/repair, override application, and final package units; and committed player action/GM narration under `dev_gm_turn_content`. Candidate and evidence identifiers are included to correlate stages. Evidence excerpt text and full prompts are not logged. Error response snippets can contain provider diagnostics, and GM narration may contain player content, so enable this only for local development and turn it off after collection.

For parallel worktrees or E2E runs, first choose five currently unused host ports, then export them before starting the launcher: `BACKEND_SERVER_PORT`, `FRONTEND_DEV_PORT`, `POSTGRES_PORT`, `REDIS_PORT`, and `LOCAL_AGENT_CONNECTION_RELAY_PORT`. Record the selected ports with the run. Give each independent run a unique `COMPOSE_PROJECT_NAME`; reuse a project name only when intentionally resuming its saved test database. Do not rely on launcher defaults when another worktree may be running.

## E2E runs that exercise Codex

- When an E2E run is meant to verify the Codex path, start the `user-pc-agent` built from the same active checkout before starting the browser flow. The client must connect to that checkout's relay with the test user's access token and use the Codex CLI installation already logged in on the client machine. Follow [the user PC agent run guide](src/user-pc-agent/README.md); do not substitute a server-side Codex process or a different checkout's client.
- The frontend already lets you select the provider for a session: open **GM 연결 설정** in the adventure session and choose **Codex OAuth** (`codex-cli`), then apply the change. The profile-level **AI 엔드포인트 설정** is a separate setting and does not start or monitor the `user-pc-agent` process. Start that process separately and confirm its connection log before submitting an E2E action that invokes the GM.
- A normal E2E run using Ollama or an OpenAI-compatible endpoint does not exercise the user PC client. Report which endpoint was active and whether the same-checkout client was connected with the E2E result. If the scenario does not select Codex, do not describe that run as verification of the WebSocket-to-local-Codex path.
- Treat the user PC agent WebSocket as interruptible. The client process retries the connection with increasing delays (up to 30 seconds) and logs each failed attempt. After a disconnect, verify the reconnection log before submitting the next GM-backed action. An in-flight request is not replayed automatically because the server may already have applied it; inspect the action result and resubmit only when the session confirms it was not applied.
