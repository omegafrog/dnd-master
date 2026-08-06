#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export BACKEND_E2E_UI=1
export BACKEND_E2E_URL="${BACKEND_E2E_URL:-http://127.0.0.1:8080}"

required=(BACKEND_E2E_EMAIL BACKEND_E2E_PASSWORD BACKEND_E2E_SESSION_ID)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "Missing $name; use a seeded app-all session." >&2
    exit 2
  fi
done

if ! curl -fsS "$BACKEND_E2E_URL/actuator/health" >/dev/null 2>&1; then
  echo "Backend unavailable at $BACKEND_E2E_URL. Start app-all first: src/start-dev.sh" >&2
  exit 3
fi

cd "$ROOT/src/web-ui"
npm run test:e2e -- e2e/backend-ui-journey.spec.ts
