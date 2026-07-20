#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
INFRA="$ROOT/infra"
UI="$ROOT/web-ui"

cleanup() {
    echo ""
    echo "Shutting down..."
    [ -n "${BACKEND_PID:-}" ] && kill "$BACKEND_PID" 2>/dev/null
    [ -n "${FRONTEND_PID:-}" ] && kill "$FRONTEND_PID" 2>/dev/null
    wait 2>/dev/null
    echo "Done."
}
trap cleanup EXIT INT TERM

# 1. Infra (PostgreSQL)
echo "==> Starting infra (PostgreSQL)…"
docker compose -f "$INFRA/compose.yaml" up -d --wait
echo "    Infra ready."

# 2. Backend (app-all)
echo "==> Starting backend (app-all)…"
(cd "$ROOT" && ./gradlew :app-all:bootRun &)
BACKEND_PID=$!

# 3. Frontend (web-ui)
echo "==> Starting frontend (web-ui)…"
(cd "$UI" && npm run dev &)
FRONTEND_PID=$!

echo ""
echo "  Backend:  http://localhost:8080"
echo "  Frontend: http://localhost:5173"
echo "  Swagger:  http://localhost:8080/swagger-ui.html"
echo "  Press Ctrl+C to stop all."
echo ""

wait
