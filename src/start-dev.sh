#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
INFRA="$ROOT/infra"
UI="$ROOT/web-ui"
DEMO_USER_INIT_SQL="/docker-entrypoint-initdb.d/02-seed-demo-user.sql"
GRADLEW_TMP=""
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
require_env INTERNAL_SERVICE_TOKEN
require_env RULE_KNOWLEDGE_BACKOFFICE_ADMIN_PLAYER_IDS
require_env CODEX_EXECUTABLE

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

# Live Playwright separately requires BACKEND_E2E_URL, BACKEND_E2E_EMAIL,
# BACKEND_E2E_PASSWORD, BACKEND_E2E_RULEBOOK_FILE, and BACKEND_E2E_STORYBOOKS_JSON.

run_node() { "$NODE_BIN" "$@"; }
run_npm() {
    run_node "$NPM_CLI" "$@"
}

cleanup() {
    echo ""
    echo "Shutting down..."
    [ -n "${BACKEND_PID:-}" ] && kill "$BACKEND_PID" 2>/dev/null || true
    [ -n "${FRONTEND_PID:-}" ] && kill "$FRONTEND_PID" 2>/dev/null || true
    [ -n "$GRADLEW_TMP" ] && rm -f "$GRADLEW_TMP"
    wait 2>/dev/null || true
    echo "Done."
}
trap cleanup EXIT INT TERM

echo "==> Starting infra (PostgreSQL)..."
docker compose -f "$INFRA/compose.yaml" up -d --wait

echo "==> Applying demo user init SQL..."
docker compose -f "$INFRA/compose.yaml" exec -T postgres \
    psql --username postgres --dbname postgres --set ON_ERROR_STOP=1 \
    --file "$DEMO_USER_INIT_SQL"
echo "    Infra ready."

# WSL bash cannot execute the repository's CRLF gradle wrapper directly.
# Normalize only a temporary copy so the source wrapper remains untouched.
GRADLEW_TMP="$(mktemp "${TMPDIR:-/tmp}/dnd-master-gradlew.XXXXXX")"
tr -d '\r' < "$ROOT/gradlew" > "$GRADLEW_TMP"
chmod +x "$GRADLEW_TMP"

echo "==> Starting backend (app-all)..."
(cd "$ROOT" && exec bash "$GRADLEW_TMP" :app-all:bootRun) &
BACKEND_PID=$!
echo "    Backend PID: $BACKEND_PID"

echo "==> Starting frontend (web-ui)..."
if [ ! -d "$UI/node_modules" ] || ! (cd "$UI" && run_node -e "require.resolve('@rollup/rollup-linux-x64-gnu')" >/dev/null 2>&1); then
    echo "    Installing Linux frontend dependencies..."
    rm -rf "$UI/node_modules"
    (cd "$UI" && run_npm install --include=optional)
fi
(cd "$UI" && run_npm run dev) &
FRONTEND_PID=$!
echo "    Frontend PID: $FRONTEND_PID"

echo ""
echo "  Backend:  http://localhost:8080"
echo "  Frontend: http://localhost:5173"
echo "  Swagger:  http://localhost:8080/swagger-ui.html"
echo "  Demo login: demo-player@example.com / secret-password"
echo "  Press Ctrl+C to stop all."
echo ""

wait
