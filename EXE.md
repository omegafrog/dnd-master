# Build and E2E execution

- Treat the active checkout as the single source for the build, development server, and Playwright test. Before building or launching anything, record `git rev-parse --show-toplevel` and `git rev-parse HEAD`.
- Run `src/start-dev.sh` from that same checkout so its backend, frontend, and compiled classes come from one worktree and revision. Do not use an already-running service merely because its health endpoint responds.
- Before E2E, identify the listener PID for port 8080 and verify `/proc/<pid>/cwd` resolves to the recorded checkout or one of its descendants. Also verify the running Java classpath/build output points into that checkout. For a local UI run, apply the same worktree check to the port 5173 frontend process.
- If a listener belongs to another checkout, stop or otherwise retire that stale local process, restart the approved launcher from the recorded checkout, wait for health, and repeat the ownership checks. Do not run E2E until the checks pass.
- Launch Playwright from the launcher environment after its backend health and worktree checks pass. Capture the test command, active checkout path, source revision, backend/frontend PIDs, and their resolved working directories with the result so the E2E evidence is tied to the code that was built and run.
- For non-local environments, explicit user-supplied URL overrides remain allowed, but do not present those runs as verification of the local checkout.

## E2E runs that exercise Codex

- When an E2E run is meant to verify the Codex path, start the `user-pc-agent` built from the same active checkout before starting the browser flow. The client must connect to that checkout's relay with the test user's access token and use the Codex CLI installation already logged in on the client machine. Follow [the user PC agent run guide](src/user-pc-agent/README.md); do not substitute a server-side Codex process or a different checkout's client.
- The frontend already lets you select the provider for a session: open **GM 연결 설정** in the adventure session and choose **Codex OAuth** (`codex-cli`), then apply the change. The profile-level **AI 엔드포인트 설정** is a separate setting and does not start or monitor the `user-pc-agent` process. Start that process separately and confirm its connection log before submitting an E2E action that invokes the GM.
- A normal E2E run using Ollama or an OpenAI-compatible endpoint does not exercise the user PC client. Report which endpoint was active and whether the same-checkout client was connected with the E2E result. If the scenario does not select Codex, do not describe that run as verification of the WebSocket-to-local-Codex path.
