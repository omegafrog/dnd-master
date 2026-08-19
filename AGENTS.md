## graphify

## WSL execution

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

Required runtime values must be supplied before starting services or live E2E. Fail fast with a clear message instead of starting and rediscovering missing configuration:

- Backend: `RULE_KNOWLEDGE_BACKOFFICE_ADMIN_PLAYER_IDS=local-catalog-admin` and `CODEX_EXECUTABLE=/home/jiwoo/.nvm/versions/node/v24.12.0/bin/codex`.
- Live Playwright: `BACKEND_E2E_URL`, `BACKEND_E2E_EMAIL`, `BACKEND_E2E_PASSWORD`, `BACKEND_E2E_RULEBOOK_FILE`, and non-empty `BACKEND_E2E_STORYBOOKS_JSON`.
- Fresh Potent Brew runs must use Linux asset paths under `/home/jiwoo/workspace/dnd-master/docs/assets/` and roles `MAIN_SCENARIO`, `MAP`, and `HANDOUT`.

Before launching E2E, verify `curl -fsS "$BACKEND_E2E_URL/actuator/health"` and ensure port 8080 is owned by the current WSL checkout. If any required value is missing, the URL resolves to a Windows path, or `command -v` resolves outside WSL, stop before running the test. Do not silently fall back to a stale backend, Windows `npm`, Windows Java, or a partial environment.

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
