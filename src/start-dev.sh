#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
INFRA="$ROOT/infra"
UI="$ROOT/web-ui"
DEMO_USER_INIT_SQL="/docker-entrypoint-initdb.d/02-seed-demo-user.sql"
GRADLEW_TMP_DIR=""
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

# Local-only defaults keep this developer launcher runnable without exporting
# production credentials. Deployments must provide their own values.
export INTERNAL_SERVICE_TOKEN="${INTERNAL_SERVICE_TOKEN:-local-development-internal-token}"
export RULE_KNOWLEDGE_BACKOFFICE_ADMIN_PLAYER_IDS="${RULE_KNOWLEDGE_BACKOFFICE_ADMIN_PLAYER_IDS:-local-catalog-admin}"
export CODEX_EXECUTABLE="${CODEX_EXECUTABLE:-/home/jiwoo/.nvm/versions/node/v24.12.0/bin/codex}"
export RULE_KNOWLEDGE_PREPROCESSING_PYTHON_EXECUTABLE="${RULE_KNOWLEDGE_PREPROCESSING_PYTHON_EXECUTABLE:-/home/jiwoo/workspace/dnd-master/.venv-docling/bin/python}"
export BACKEND_E2E_URL="${BACKEND_E2E_URL:-http://localhost:8080}"
export BACKEND_E2E_EMAIL="${BACKEND_E2E_EMAIL:-demo-player@example.com}"
export BACKEND_E2E_PASSWORD="${BACKEND_E2E_PASSWORD:-secret-password}"
if [ -z "${BACKEND_E2E_STORYBOOKS_JSON:-}" ]; then
    export BACKEND_E2E_STORYBOOKS_JSON='[{"path":"/home/jiwoo/workspace/dnd-master/docs/assets/892902-A_Most_Potent_Brew.pdf","role":"MAIN_SCENARIO"},{"path":"/home/jiwoo/workspace/dnd-master/docs/assets/892902-A_Potent_Brew_Map.pdf","role":"MAP"},{"path":"/home/jiwoo/workspace/dnd-master/docs/assets/892902-A_Most_Potent_Brew_Player_Handout.pdf","role":"HANDOUT"}]'
fi
require_env INTERNAL_SERVICE_TOKEN
require_env RULE_KNOWLEDGE_BACKOFFICE_ADMIN_PLAYER_IDS
require_env CODEX_EXECUTABLE
require_env RULE_KNOWLEDGE_PREPROCESSING_PYTHON_EXECUTABLE
require_env BACKEND_E2E_URL
require_env BACKEND_E2E_EMAIL
require_env BACKEND_E2E_PASSWORD
require_env BACKEND_E2E_STORYBOOKS_JSON

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
    [ -n "${BACKEND_PID:-}" ] && kill "$BACKEND_PID" 2>/dev/null || true
    [ -n "${FRONTEND_PID:-}" ] && kill "$FRONTEND_PID" 2>/dev/null || true
    [ -n "$GRADLEW_TMP_DIR" ] && rm -rf "$GRADLEW_TMP_DIR"
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
GRADLEW_TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/dnd-master-gradlew.XXXXXX")"
tr -d '\r' < "$ROOT/gradlew" > "$GRADLEW_TMP_DIR/gradlew"
cp -R "$ROOT/gradle" "$GRADLEW_TMP_DIR/gradle"
chmod +x "$GRADLEW_TMP_DIR/gradlew"

echo "==> Starting backend (app-all)..."
(cd "$ROOT" && exec bash "$GRADLEW_TMP_DIR/gradlew" :app-all:bootRun) &
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
