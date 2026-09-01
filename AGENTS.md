## graphify

## Execution environment

- Run every shell command, test, build, script, Git check, and application/service process inside WSL Ubuntu-24.04.
- From Windows, invoke repository work through `wsl.exe -d Ubuntu-24.04 -- bash -lc "..."`.
- Do not run npm, Gradle, Node.js, Python, Vite, Playwright, or development servers directly from Windows PowerShell or cmd.
- Subagents must follow the same WSL-only execution rule.

All repository commands, builds, tests, E2E runs, Gradle tasks, Node tools, Playwright, and graphify must run inside WSL Ubuntu-24.04. From a Windows-hosted shell, invoke them only through:

```powershell
wsl.exe -d Ubuntu-24.04 -- bash -lc 'cd /home/jiwoo/workspace/dnd-master && <command>'
```

Never run `npm`, `npx`, `node`, `gradle`, `./gradlew`, `java`, `playwright`, or `graphify` from a Windows path, UNC path, PowerShell-resolved `node_modules/.bin`, or Windows Java/Node installation. Before every run, change directory to the Linux checkout and verify the resolved tools:

```bash
pwd
test "$PWD" = /home/jiwoo/workspace/dnd-master
command -v node npm npx java graphify
```

For this checkout, prepend the WSL toolchain paths when needed:

```bash
export PATH=/home/jiwoo/.nvm/versions/node/v24.12.0/bin:/home/jiwoo/.sdkman/candidates/java/current/bin:/home/jiwoo/.local/bin:$PATH
export JAVA_HOME=/home/jiwoo/.sdkman/candidates/java/current
```

For local development and live browser quality checks, run `src/start-dev.sh`. Do not stop merely because the caller's shell is missing runtime values. The development launcher must define the repository-approved local defaults itself, start the infrastructure/backend/frontend, and export the live Playwright values consumed by a test launched from that environment. Keep credentials and asset paths in the development launcher synchronized with the seeded demo user and repository assets.

Required runtime values must be supplied by `src/start-dev.sh` before starting services or live E2E. Fail fast with a clear message only when the launcher cannot provide or validate them:

- Backend: `RULE_KNOWLEDGE_BACKOFFICE_ADMIN_PLAYER_IDS=local-catalog-admin`, `CODEX_EXECUTABLE=/home/jiwoo/.nvm/versions/node/v24.12.0/bin/codex`, and non-empty `INTERNAL_SERVICE_TOKEN` shared by internal gateways/controllers.
- Live Playwright: `BACKEND_E2E_URL`, `BACKEND_E2E_EMAIL`, `BACKEND_E2E_PASSWORD`, and non-empty `BACKEND_E2E_STORYBOOKS_JSON`; rulebooks come from the published shared catalog.
- Fresh Potent Brew runs must use Linux asset paths under `/home/jiwoo/workspace/dnd-master/docs/assets/` and roles `MAIN_SCENARIO`, `MAP`, and `HANDOUT`.

Do not ask the user to manually provide these local-development values before trying the launcher. Invoke the launcher inside WSL, wait for backend health, then run Playwright with the same development values. User-supplied overrides remain allowed for non-local environments.

If `docker` is missing, the Docker daemon is stopped, or the WSL Docker socket is unavailable, do not stop at detection and ask the user to start it. Repair the local development prerequisite: install or expose the Linux Docker CLI inside Ubuntu-24.04 as needed, start the Docker engine or restore the WSL Docker integration/socket, verify `docker info` and `docker compose version`, then rerun `src/start-dev.sh`. Run Docker commands only from the Linux checkout inside WSL; never invoke a Windows-path `docker.exe`. If privilege elevation, an interactive password, or a host setting prevents recovery, exhaust non-interactive WSL-safe options first, then report the exact blocked operation.

Before launching E2E, verify `curl -fsS "$BACKEND_E2E_URL/actuator/health"` and ensure port 8080 is owned by the current WSL checkout. If any required value is missing, the URL resolves to a Windows path, or `command -v` resolves outside WSL, stop before running the test. Do not silently fall back to a stale backend, Windows `npm`, Windows Java, or a partial environment.

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
