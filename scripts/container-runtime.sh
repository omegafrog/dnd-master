#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
INFRA_ROOT="$ROOT/src"
export WORKSPACE_ROOT="$ROOT"
export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-dnd-master-$(printf '%s' "$INFRA_ROOT" | sha256sum | cut -c1-8)}"
COMMON_DIR="$(git rev-parse --path-format=absolute --git-common-dir)"
export HARNESS_VL_PROJECT="${HARNESS_VL_PROJECT:-harness-vl-$(printf '%s' "$COMMON_DIR" | sha256sum | cut -c1-12)}"
export HARNESS_SESSION_DIR="${HARNESS_SESSION_DIR:-$ROOT/.codex/sessions/container-runtime}"
export RULE_KNOWLEDGE_ASSET_HOST_DIR="${RULE_KNOWLEDGE_ASSET_HOST_DIR:-$ROOT/../dnd-master/docs/assets}"
export OLLAMA_MODEL_HOST_DIR="${OLLAMA_MODEL_HOST_DIR:-/usr/share/ollama/.ollama/models}"
export OTEL_JAVAAGENT_JAR="${OTEL_JAVAAGENT_JAR:-$ROOT/.codex/tools/opentelemetry/opentelemetry-javaagent-2.21.0.jar}"
COMPOSE=(docker compose -p "$COMPOSE_PROJECT_NAME" -f src/infra/compose.app.yaml)
INFRA_COMPOSE=(docker compose -p "$COMPOSE_PROJECT_NAME" -f src/infra/compose.yaml)

case "${1:-}" in
  redis-check)
    docker info >/dev/null
    if "${INFRA_COMPOSE[@]}" ps --status running --services | grep -qx redis; then
      "${INFRA_COMPOSE[@]}" exec -T redis redis-cli ping | grep -qx PONG
    else
      python3 - <<'PY'
import socket
with socket.socket() as sock:
    sock.bind(("127.0.0.1", 6379))
PY
    fi
    ;;
  redis-up)
    "${INFRA_COMPOSE[@]}" up -d redis
    "${INFRA_COMPOSE[@]}" exec -T redis redis-cli ping | grep -qx PONG
    ;;
  build)
    "${COMPOSE[@]}" config --quiet
    "${COMPOSE[@]}" build app-all agent-connection-relay-e2e
    ;;
  check)
    docker info >/dev/null
    test -r "$OTEL_JAVAAGENT_JAR"
    test -d "$RULE_KNOWLEDGE_ASSET_HOST_DIR"
    test -d "$OLLAMA_MODEL_HOST_DIR/manifests"
    docker network inspect "${COMPOSE_PROJECT_NAME}_default" "$HARNESS_VL_PROJECT"_default >/dev/null
    "${COMPOSE[@]}" ps --status running --services | grep -qx metrics-scraper
    curl -fsS http://127.0.0.1:8428/api/v1/query?query=1 >/dev/null
    "${INFRA_COMPOSE[@]}" exec -T redis redis-cli ping | grep -qx PONG
    python3 - "$COMPOSE_PROJECT_NAME" "${BACKEND_SERVER_PORT:-8080}" "${E2E_AGENT_CONNECTION_RELAY_PORT:-18082}" <<'PY'
import socket, subprocess, sys
project, *ports = sys.argv[1:]
services = ("app-all", "agent-connection-relay-e2e")
for service, port in zip(services, map(int, ports)):
    with socket.socket() as sock:
        try:
            sock.bind(("127.0.0.1", port))
            continue
        except OSError as error:
            running = subprocess.run(
                ["docker", "compose", "-p", project, "-f", "src/infra/compose.app.yaml",
                 "ps", "--status", "running", "--services"],
                check=True, capture_output=True, text=True).stdout.splitlines()
            published = subprocess.run(
                ["docker", "compose", "-p", project, "-f", "src/infra/compose.app.yaml",
                 "port", service, "8080"],
                check=True, capture_output=True, text=True).stdout.strip()
            if service not in running or published != f"127.0.0.1:{port}":
                raise SystemExit(f"Required container port {port} is occupied: {error}")
            print(f"Container port {port} is already owned by this Compose service ({service})")
PY
    ;;
  up)
    mkdir -p "$HARNESS_SESSION_DIR/runtime/e2e/rulebooks"
    "${COMPOSE[@]}" up -d ollama app-all agent-connection-relay-e2e
    for _ in $(seq 1 180); do
      if curl -fsS "http://127.0.0.1:${BACKEND_SERVER_PORT:-8080}/actuator/health" >/dev/null; then
        break
      fi
      sleep 1
    done
    curl -fsS "http://127.0.0.1:${BACKEND_SERVER_PORT:-8080}/actuator/health"
    for _ in $(seq 1 90); do
      if python3 - <<'PY'
import json, urllib.parse, urllib.request
query = 'up{job="dnd-master-spec-combat-movement-spell-range/app-all",instance="app-all:8080"}'
url = 'http://127.0.0.1:8428/api/v1/query?' + urllib.parse.urlencode({'query': query})
try:
    with urllib.request.urlopen(url, timeout=3) as response:
        rows = json.load(response).get('data', {}).get('result', [])
    if any(row.get('value', [None, None])[1] == '1' for row in rows):
        print('Container metrics scrape: up=1')
        raise SystemExit(0)
except Exception:
    pass
raise SystemExit(1)
PY
      then
        break
      fi
      sleep 2
    done
    python3 - <<'PY'
import json, urllib.parse, urllib.request
query = 'up{job="dnd-master-spec-combat-movement-spell-range/app-all",instance="app-all:8080"}'
url = 'http://127.0.0.1:8428/api/v1/query?' + urllib.parse.urlencode({'query': query})
with urllib.request.urlopen(url, timeout=3) as response:
    rows = json.load(response).get('data', {}).get('result', [])
values = [row.get('value', [None, None])[1] for row in rows]
print(f'Container metrics scrape: expected up=1, actual={values or "missing"}')
if '1' not in values:
    raise SystemExit(1)
PY
    ;;
  stop)
    "${COMPOSE[@]}" stop app-all agent-connection-relay-e2e
    ;;
  app-up)
    docker info >/dev/null
    "${COMPOSE[@]}" config --quiet
    "${COMPOSE[@]}" build app-all
    "${COMPOSE[@]}" up -d --no-deps app-all
    for _ in $(seq 1 180); do
      if curl -fsS "http://127.0.0.1:${BACKEND_SERVER_PORT:-8080}/actuator/health" >/dev/null; then
        break
      fi
      sleep 1
    done
    curl -fsS "http://127.0.0.1:${BACKEND_SERVER_PORT:-8080}/actuator/health"
    ;;
  app-build)
    docker info >/dev/null
    "${COMPOSE[@]}" config --quiet
    "${COMPOSE[@]}" build app-all
    docker run --rm --entrypoint python3 dnd-master-app-all:dev -c \
      'import preprocessing_agent.adapters.process_cli; print("preprocessing agent import: ready")'
    ;;
  *)
    echo "Usage: scripts/container-runtime.sh redis-check|redis-up|build|check|up|app-build|app-up|stop" >&2
    exit 2
    ;;
esac
