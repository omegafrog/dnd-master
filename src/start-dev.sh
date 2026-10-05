#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
INFRA="$ROOT/infra"
UI="$ROOT/web-ui"
DEMO_USER_INIT_SQL="/docker-entrypoint-initdb.d/02-seed-demo-user.sql"
GRADLEW_TMP_DIR=""
RELAY_PID=""
USER_PC_AGENT_PID=""
USER_PC_AGENT_LOG=""
USER_PC_AGENT_READY=false
if [ "$(uname -s)" != "Linux" ]; then
    echo "ERROR: start-dev.sh must be run inside WSL/Linux (uname -s=Linux required)." >&2
    exit 1
fi

require_env() {
    local name="$1"
    if [ -z "${!name:-}" ]; then
        echo "ERROR: required environment variable $name is missing or blank." >&2
        exit 1
    fi
}

require_available_port() {
    local port="$1"
    case "$port" in
        ''|*[!0-9]*)
            echo "ERROR: local agent connection relay port must be numeric: $port" >&2
            exit 1
            ;;
    esac
    if ss -ltn "sport = :$port" | awk 'NR > 1 { found = 1 } END { exit !found }'; then
        echo "ERROR: local agent connection relay port $port is already in use." >&2
        exit 1
    fi
}

# Local-only defaults keep this developer launcher runnable without exporting
# production credentials. Deployments must provide their own values.
export INTERNAL_SERVICE_TOKEN="${INTERNAL_SERVICE_TOKEN:-local-development-internal-token}"
export LOCAL_AGENT_CONNECTION_RELAY_PORT="${LOCAL_AGENT_CONNECTION_RELAY_PORT:-8081}"
export BACKEND_SERVER_PORT="${BACKEND_SERVER_PORT:-8080}"
export FRONTEND_DEV_PORT="${FRONTEND_DEV_PORT:-5173}"
export POSTGRES_PORT="${POSTGRES_PORT:-5432}"
export REDIS_PORT="${REDIS_PORT:-6379}"
export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-dnd-master-$(printf '%s' "$ROOT" | sha256sum | cut -c1-8)}"
export SERVER_PORT="$BACKEND_SERVER_PORT"
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:$POSTGRES_PORT/postgres}"
export SPRING_DATA_REDIS_PORT="${SPRING_DATA_REDIS_PORT:-$REDIS_PORT}"
export BACKEND_E2E_URL="${BACKEND_E2E_URL:-http://127.0.0.1:$BACKEND_SERVER_PORT}"
export IDENTITY_ACCESS_BASE_URL="${IDENTITY_ACCESS_BASE_URL:-$BACKEND_E2E_URL/}"
export RULE_KNOWLEDGE_BASE_URL="${RULE_KNOWLEDGE_BASE_URL:-$BACKEND_E2E_URL/}"
export AI_GAME_MASTER_BASE_URL="${AI_GAME_MASTER_BASE_URL:-$BACKEND_E2E_URL/}"
LOCAL_AGENT_CONNECTION_RELAY_URL="http://127.0.0.1:${LOCAL_AGENT_CONNECTION_RELAY_PORT}"
export AGENT_CONNECTION_RELAY_URL="${AGENT_CONNECTION_RELAY_URL:-$LOCAL_AGENT_CONNECTION_RELAY_URL}"
export RELAY_INTERNAL_ADDRESS="${RELAY_INTERNAL_ADDRESS:-$LOCAL_AGENT_CONNECTION_RELAY_URL}"
export RULE_KNOWLEDGE_ASSET_FALLBACK_ROOT="${RULE_KNOWLEDGE_ASSET_FALLBACK_ROOT:-$ROOT/../docs/assets}"
# Keep the repository's local catalog-admin marker and the seeded demo player's
# actual identity together. The browser Backoffice sends the authenticated
# player ID, so omitting the seeded ID makes local catalog publication fail
# with 403 before a Solo Player can select a published Rulebook.
export RULE_KNOWLEDGE_BACKOFFICE_ADMIN_PLAYER_IDS="${RULE_KNOWLEDGE_BACKOFFICE_ADMIN_PLAYER_IDS:-local-catalog-admin,00000000-0000-0000-0000-000000000001,aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa}"
export CODEX_EXECUTABLE="${CODEX_EXECUTABLE:-/home/jiwoo/.nvm/versions/node/v24.12.0/bin/codex}"
export CODEX_CHARACTER_TAG_MODEL="${CODEX_CHARACTER_TAG_MODEL:-gpt-5.6-luna}"
# The installed Codex model cache (/home/jiwoo/.codex/models_cache.json)
# reports context_window=272000 for gpt-5.6-luna. The public model page
# (https://developers.openai.com/api/docs/models/gpt-5.6-luna) advertises a
# larger total window, but this local app-server path uses the
# smaller effective limit until its own model metadata reports otherwise.
# Other selected provider/model pairs must be configured explicitly.
export GM_RUNTIME_CONTEXT_LIMITS="${GM_RUNTIME_CONTEXT_LIMITS:-codex-cli/gpt-5.6-luna=272000}"
export RULE_KNOWLEDGE_PREPROCESSING_PYTHON_EXECUTABLE="${RULE_KNOWLEDGE_PREPROCESSING_PYTHON_EXECUTABLE:-/home/jiwoo/workspace/dnd-master/.venv-docling/bin/python}"
export RULE_KNOWLEDGE_PREPROCESSING_WORKING_DIRECTORY="${RULE_KNOWLEDGE_PREPROCESSING_WORKING_DIRECTORY:-$ROOT/..}"
# The WSL-local Tesseract installation is linked against libraries kept next
# to the executable.  Export the path before starting Java/Python so both the
# source preview extractor and the preprocessing retry process can invoke it.
export LD_LIBRARY_PATH="/home/jiwoo/.local/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export TESSDATA_PREFIX="${TESSDATA_PREFIX:-/home/jiwoo/.local/usr/share/tesseract-ocr/5/tessdata}"
export RULE_KNOWLEDGE_OCR_LANGUAGES="${RULE_KNOWLEDGE_OCR_LANGUAGES:-eng}"
export BACKEND_E2E_EMAIL="${BACKEND_E2E_EMAIL:-demo-player@example.com}"
export BACKEND_E2E_PASSWORD="${BACKEND_E2E_PASSWORD:-secret-password}"
export USER_PC_AGENT_RELAY_WEBSOCKET_URL="${USER_PC_AGENT_RELAY_WEBSOCKET_URL:-ws://127.0.0.1:${LOCAL_AGENT_CONNECTION_RELAY_PORT}/ws/agent}"
if [ -z "${BACKEND_E2E_STORYBOOKS_JSON:-}" ]; then
    export BACKEND_E2E_STORYBOOKS_JSON="[{\"path\":\"$ROOT/../docs/assets/892902-A_Most_Potent_Brew.pdf\",\"role\":\"MAIN_SCENARIO\"},{\"path\":\"$ROOT/../docs/assets/892902-A_Potent_Brew_Map.pdf\",\"role\":\"MAP\"},{\"path\":\"$ROOT/../docs/assets/892902-A_Most_Potent_Brew_Player_Handout.pdf\",\"role\":\"HANDOUT\"}]"
fi
require_env INTERNAL_SERVICE_TOKEN
require_env AGENT_CONNECTION_RELAY_URL
require_env RELAY_INTERNAL_ADDRESS
require_env RULE_KNOWLEDGE_BACKOFFICE_ADMIN_PLAYER_IDS
require_env CODEX_EXECUTABLE
require_env GM_RUNTIME_CONTEXT_LIMITS
require_env RULE_KNOWLEDGE_PREPROCESSING_PYTHON_EXECUTABLE
require_env BACKEND_E2E_URL
require_env BACKEND_E2E_EMAIL
require_env BACKEND_E2E_PASSWORD
require_env BACKEND_E2E_STORYBOOKS_JSON

if [ ! -d "$RULE_KNOWLEDGE_ASSET_FALLBACK_ROOT" ]; then
    echo "ERROR: RULE_KNOWLEDGE_ASSET_FALLBACK_ROOT directory was not found: $RULE_KNOWLEDGE_ASSET_FALLBACK_ROOT" >&2
    exit 1
fi

NVM_BIN="/home/jiwoo/.nvm/versions/node/v24.12.0/bin"
SDKMAN_JAVA_HOME="/home/jiwoo/.sdkman/candidates/java/current"
NODE_BIN="$NVM_BIN/node"
NPM_CLI="$NVM_BIN/npm"
JAVA_BIN="$SDKMAN_JAVA_HOME/bin/java"
GRAPHIFY_BIN="/home/jiwoo/.local/bin/graphify"
export JAVA_HOME="$SDKMAN_JAVA_HOME"
export PATH="$NVM_BIN:$SDKMAN_JAVA_HOME/bin:/home/jiwoo/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

require_linux_path() {
    local name="$1" path="$2"
    case "$path" in
        /home/jiwoo/*) ;;
        *) echo "ERROR: $name must resolve to a Linux/WSL path: $path" >&2; exit 1 ;;
    esac
    if [ ! -x "$path" ]; then
        echo "ERROR: required Linux/WSL executable $name was not found or is not executable at $path." >&2
        exit 1
    fi
}
require_linux_path CODEX_EXECUTABLE "$CODEX_EXECUTABLE"
require_linux_path RULE_KNOWLEDGE_PREPROCESSING_PYTHON_EXECUTABLE "$RULE_KNOWLEDGE_PREPROCESSING_PYTHON_EXECUTABLE"
require_linux_path node "$NODE_BIN"
require_linux_path npm "$NPM_CLI"
require_linux_path java "$JAVA_BIN"
require_linux_path graphify "$GRAPHIFY_BIN"
for tool in node npm java graphify; do
    expected=""
    case "$tool" in node) expected="$NODE_BIN";; npm) expected="$NPM_CLI";; java) expected="$JAVA_BIN";; graphify) expected="$GRAPHIFY_BIN";; esac
    resolved="$(command -v "$tool")"
    if [ "$resolved" != "$expected" ]; then
        echo "ERROR: $tool must resolve to the WSL toolchain path $expected (resolved $resolved)." >&2
        exit 1
    fi
done

# Live Playwright inherits the local values above when launched from this
# development environment. Rulebooks come from the published shared catalog.

run_node() { "$NODE_BIN" "$@"; }
run_npm() {
    run_node "$NPM_CLI" "$@"
}

cleanup() {
    echo ""
    echo "Shutting down..."
    [ -n "${USER_PC_AGENT_PID:-}" ] && kill "$USER_PC_AGENT_PID" 2>/dev/null || true
    [ -n "${BACKEND_PID:-}" ] && kill "$BACKEND_PID" 2>/dev/null || true
    [ -n "${FRONTEND_PID:-}" ] && kill "$FRONTEND_PID" 2>/dev/null || true
    [ -n "${RELAY_PID:-}" ] && kill "$RELAY_PID" 2>/dev/null || true
    [ "${USER_PC_AGENT_READY:-false}" = true ] && [ -n "${USER_PC_AGENT_LOG:-}" ] && rm -f "$USER_PC_AGENT_LOG"
    [ -n "$GRADLEW_TMP_DIR" ] && rm -rf "$GRADLEW_TMP_DIR"
    wait 2>/dev/null || true
    echo "Done."
}
trap cleanup EXIT INT TERM

echo "==> Starting infra (PostgreSQL and Redis)..."
docker compose -f "$INFRA/compose.yaml" up -d --wait

echo "==> Applying demo user init SQL..."
docker compose -f "$INFRA/compose.yaml" exec -T postgres \
    psql --username postgres --dbname postgres --set ON_ERROR_STOP=1 \
    --file "$DEMO_USER_INIT_SQL"
echo "    Infra ready."

# WSL bash cannot execute the repository's CRLF gradle wrapper directly.
# Normalize only a temporary copy so the source wrapper remains untouched.
GRADLEW_TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/dnd-master-gradlew.XXXXXX")"
tr -d '\r' < "$ROOT/gradlew" > "$GRADLEW_TMP_DIR/gradlew"
cp -R "$ROOT/gradle" "$GRADLEW_TMP_DIR/gradle"
chmod +x "$GRADLEW_TMP_DIR/gradlew"

echo "==> Starting local agent connection relay..."
for port in "$BACKEND_SERVER_PORT" "$FRONTEND_DEV_PORT" "$POSTGRES_PORT" "$REDIS_PORT"; do
    require_available_port "$port"
done
require_available_port "$LOCAL_AGENT_CONNECTION_RELAY_PORT"
(cd "$ROOT" && exec bash "$GRADLEW_TMP_DIR/gradlew" :agent-connection-relay-service:bootRun --args="--server.port=$LOCAL_AGENT_CONNECTION_RELAY_PORT") &
RELAY_PID=$!
echo "    Relay PID: $RELAY_PID"

echo "==> Waiting for local agent connection relay health..."
for attempt in $(seq 1 90); do
    if curl -fsS "$LOCAL_AGENT_CONNECTION_RELAY_URL/actuator/health" >/dev/null; then
        echo "    Local agent connection relay health is ready."
        break
    fi
    if ! kill -0 "$RELAY_PID" 2>/dev/null; then
        echo "ERROR: local agent connection relay stopped before becoming healthy." >&2
        exit 1
    fi
    if [ "$attempt" = "90" ]; then
        echo "ERROR: local agent connection relay did not become healthy within 180 seconds." >&2
        exit 1
    fi
    sleep 2
done

echo "==> Starting backend (app-all)..."
(cd "$ROOT" && exec bash "$GRADLEW_TMP_DIR/gradlew" :app-all:bootRun) &
BACKEND_PID=$!
echo "    Backend PID: $BACKEND_PID"

echo "==> Waiting for backend health..."
for attempt in $(seq 1 90); do
    if curl -fsS "$BACKEND_E2E_URL/actuator/health" >/dev/null; then
        echo "    Backend health is ready."
        break
    fi
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
        echo "ERROR: backend stopped before becoming healthy." >&2
        exit 1
    fi
    if [ "$attempt" = "90" ]; then
        echo "ERROR: backend did not become healthy within 180 seconds." >&2
        exit 1
    fi
    sleep 2
done

echo "==> Creating a demo-player login token for the user PC agent..."
AGENT_LOGIN_PAYLOAD="$("$NODE_BIN" -e 'console.log(JSON.stringify({username: process.argv[1], password: process.argv[2]}))' "$BACKEND_E2E_EMAIL" "$BACKEND_E2E_PASSWORD")"
AGENT_LOGIN_RESPONSE="$(curl --fail --silent --show-error \
    --header 'Content-Type: application/json' \
    --data "$AGENT_LOGIN_PAYLOAD" \
    "$BACKEND_E2E_URL/api/v1/auth/login")"
AGENT_ACCESS_TOKEN="$(printf '%s' "$AGENT_LOGIN_RESPONSE" | "$NODE_BIN" -e 'let body = ""; process.stdin.on("data", chunk => body += chunk).on("end", () => { const session = JSON.parse(body); if (!session.token || !session.playerId) process.exit(1); process.stdout.write(session.token); })')" || {
    echo "ERROR: demo-player login response did not contain a token and player ID for the user PC agent." >&2
    exit 1
}
AGENT_PLAYER_ID="$(printf '%s' "$AGENT_LOGIN_RESPONSE" | "$NODE_BIN" -e 'let body = ""; process.stdin.on("data", chunk => body += chunk).on("end", () => { const session = JSON.parse(body); if (!session.token || !session.playerId) process.exit(1); process.stdout.write(session.playerId); })')" || {
    echo "ERROR: demo-player login response did not contain a token and player ID for the user PC agent." >&2
    exit 1
}
AGENT_CONNECTION_ID="$("$NODE_BIN" -e 'process.stdout.write(require("node:crypto").randomUUID())')"

echo "==> Starting user PC agent..."
echo "==> Waiting for any previous demo-player agent connection to close..."
for attempt in $(seq 1 45); do
    if docker compose -f "$INFRA/compose.yaml" exec -T redis redis-cli \
        EXISTS "agent-connection-location:$AGENT_PLAYER_ID" 2>/dev/null | tr -d '\r' | grep -qx '0'; then
        break
    fi
    if [ "$attempt" = "45" ]; then
        echo "ERROR: an existing user PC agent connection for the demo player is still active; stop it before starting this local development environment." >&2
        exit 1
    fi
    sleep 2
done
USER_PC_AGENT_LOG="$(mktemp "${TMPDIR:-/tmp}/dnd-master-user-pc-agent.XXXXXX.log")"
(cd "$ROOT" && \
    RELAY_WEBSOCKET_URL="$USER_PC_AGENT_RELAY_WEBSOCKET_URL" \
    AGENT_ACCESS_TOKEN="$AGENT_ACCESS_TOKEN" \
    AGENT_CONNECTION_ID="$AGENT_CONNECTION_ID" \
    CODEX_EXECUTABLE="$CODEX_EXECUTABLE" \
    CODEX_WORK_DIRECTORY="$ROOT" \
    exec bash "$GRADLEW_TMP_DIR/gradlew" :user-pc-agent:run) >"$USER_PC_AGENT_LOG" 2>&1 &
USER_PC_AGENT_PID=$!
echo "    User PC agent PID: $USER_PC_AGENT_PID"

echo "==> Waiting for the user PC agent WebSocket connection..."
for attempt in $(seq 1 90); do
    if docker compose -f "$INFRA/compose.yaml" exec -T redis redis-cli \
        HGET "agent-connection-location:$AGENT_PLAYER_ID" connectionId 2>/dev/null | tr -d '\r' | grep -Fqx "$AGENT_CONNECTION_ID"; then
        echo "    User PC agent WebSocket connection is ready."
        USER_PC_AGENT_READY=true
        break
    fi
    if ! kill -0 "$USER_PC_AGENT_PID" 2>/dev/null; then
        echo "ERROR: user PC agent stopped before its WebSocket connection was registered. Log: $USER_PC_AGENT_LOG" >&2
        exit 1
    fi
    if [ "$attempt" = "90" ]; then
        echo "ERROR: user PC agent did not register a WebSocket connection within 180 seconds. Log: $USER_PC_AGENT_LOG" >&2
        exit 1
    fi
    sleep 2
done

"$ROOT/seed-local-rulebook-catalog.sh"

echo "==> Starting frontend (web-ui)..."
if [ ! -d "$UI/node_modules" ] || ! (cd "$UI" && run_node -e "require.resolve('@rollup/rollup-linux-x64-gnu')" >/dev/null 2>&1); then
    echo "    Installing Linux frontend dependencies..."
    rm -rf "$UI/node_modules"
    (cd "$UI" && run_npm install --include=optional)
fi
(cd "$UI" && run_npm run dev -- --host 127.0.0.1 --port "$FRONTEND_DEV_PORT" --strictPort) &
FRONTEND_PID=$!
echo "    Frontend PID: $FRONTEND_PID"

echo ""
echo "  Backend:  $BACKEND_E2E_URL"
echo "  Frontend: http://127.0.0.1:$FRONTEND_DEV_PORT"
echo "  Swagger:  $BACKEND_E2E_URL/swagger-ui.html"
echo "  Demo login: demo-player@example.com / secret-password"
echo "  Press Ctrl+C to stop all."
echo ""

wait
