# Build and E2E execution

- Treat the checkout used for the requested work as the single source for the build, development server, and Playwright test. Before building or launching anything, verify both `pwd` and `git rev-parse --show-toplevel` resolve to `/home/jiwoo/workspace/dnd-master`; record `git rev-parse HEAD` for the run.
- Run `src/start-dev.sh` from that checkout so its backend, frontend, and compiled classes come from the same worktree and revision. Do not use an already-running service merely because its health endpoint responds.
- Before E2E, identify the listener PID for port 8080 and verify `/proc/<pid>/cwd` resolves to `/home/jiwoo/workspace/dnd-master` or one of its descendants. Also verify the running Java classpath/build output points into that same checkout. For a local UI run, apply the same worktree check to the port 5173 frontend process.
- If a listener belongs to another checkout, stop or otherwise retire that stale local process, restart the approved launcher from `/home/jiwoo/workspace/dnd-master`, wait for health, and repeat the ownership checks. Do not run E2E until the checks pass.
- Launch Playwright from the launcher environment after its backend health and worktree checks pass. Capture the test command, active checkout path, source revision, backend/frontend PIDs, and their resolved working directories with the result so the E2E evidence is tied to the code that was built and run.
- For non-local environments, explicit user-supplied URL overrides remain allowed, but do not present those runs as verification of the local checkout.
