#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
INFRA="$ROOT/infra"
UI="$ROOT/web-ui"
DEMO_USER_INIT_SQL="/docker-entrypoint-initdb.d/02-seed-demo-user.sql"
GRADLEW_TMP=""

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
GRADLEW_TMP="$(mktemp)"
tr -d '\r' < "$ROOT/gradlew" > "$GRADLEW_TMP"
chmod +x "$GRADLEW_TMP"

echo "==> Starting backend (app-all)..."
(cd "$ROOT" && exec bash "$GRADLEW_TMP" :app-all:bootRun) &
BACKEND_PID=$!
echo "    Backend PID: $BACKEND_PID"

echo "==> Starting frontend (web-ui)..."
if [ ! -d "$UI/node_modules" ]; then
    echo "    Installing frontend dependencies..."
    (cd "$UI" && npm install)
fi
(cd "$UI" && exec npm run dev) &
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
