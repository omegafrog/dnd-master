# Local Ollama operation

Copy `.env.local-ai.example` to `.env.local-ai`, then set `OLLAMA_MODEL_ROOT` to an existing canonical directory on `E:` or `F:`. The scripts reject C:, D:, named volumes, empty paths, junctions/reparse points, paths that resolve outside E:/F:, and roots with less than 20 GB free.

Run `powershell -ExecutionPolicy Bypass -File scripts/prepare-local-ai.ps1` once to start Ollama and explicitly pull the chat and embedding models inside the Ollama container. This is the only command that pulls models.

Run `powershell -ExecutionPolicy Bypass -File scripts/verify-local-ai.ps1` to inspect the compose topology, mount contract, running service, and already-installed models. It never pulls a model. Ollama is exposed only as `127.0.0.1:11434`; the Maven runner shares its network namespace and uses `http://localhost:11434`.

`powershell -ExecutionPolicy Bypass -File scripts/benchmark-local-ai.ps1` first verifies the topology, then runs two warmups and five measured executions through the namespace-sharing Maven/Temurin 21 runner and prints p95 milliseconds. It intentionally logs only aggregate timings; prompts, raw responses, and secrets are not emitted. The required `LocalAiBenchmarkRouteTest` is supplied by the B4 live-E2E gate, so this command is not part of B3 verification.
