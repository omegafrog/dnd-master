#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEV_PID=""

cleanup() {
  if [[ -n "$DEV_PID" ]] && kill -0 "$DEV_PID" 2>/dev/null; then
    kill -INT "$DEV_PID" 2>/dev/null || true
    wait "$DEV_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

export BACKEND_E2E_URL="${BACKEND_E2E_URL:-http://127.0.0.1:8080}"
export BACKEND_E2E_EMAIL="${BACKEND_E2E_EMAIL:-demo-player@example.com}"
export BACKEND_E2E_PASSWORD="${BACKEND_E2E_PASSWORD:-secret-password}"
if [[ -z "${BACKEND_E2E_STORYBOOKS_JSON:-}" ]]; then
  export BACKEND_E2E_STORYBOOKS_JSON='[{"path":"/home/jiwoo/workspace/dnd-master/docs/assets/892902-A_Most_Potent_Brew.pdf","role":"MAIN_SCENARIO"},{"path":"/home/jiwoo/workspace/dnd-master/docs/assets/892902-A_Potent_Brew_Map.pdf","role":"MAP"},{"path":"/home/jiwoo/workspace/dnd-master/docs/assets/892902-A_Most_Potent_Brew_Player_Handout.pdf","role":"HANDOUT"}]'
fi

required_files=(
  "/home/jiwoo/workspace/dnd-master/docs/assets/892902-A_Most_Potent_Brew.pdf"
  "/home/jiwoo/workspace/dnd-master/docs/assets/892902-A_Potent_Brew_Map.pdf"
  "/home/jiwoo/workspace/dnd-master/docs/assets/892902-A_Most_Potent_Brew_Player_Handout.pdf"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "Missing input file: $file" >&2
    exit 1
  fi
done

cd "$ROOT"
if ! curl -fsS "$BACKEND_E2E_URL/actuator/health" >/dev/null 2>&1; then
  echo "Backend unavailable at $BACKEND_E2E_URL; starting src/start-dev.sh"
  ./src/start-dev.sh >"${TMPDIR:-/tmp}/dnd-character-e2e-start.log" 2>&1 &
  DEV_PID=$!
  for _ in {1..60}; do
    curl -fsS "$BACKEND_E2E_URL/actuator/health" >/dev/null 2>&1 && break
    sleep 1
  done
  if ! curl -fsS "$BACKEND_E2E_URL/actuator/health" >/dev/null 2>&1; then
    echo "Backend failed to start. Log: ${TMPDIR:-/tmp}/dnd-character-e2e-start.log" >&2
    exit 1
  fi
fi
npm --prefix src/web-ui run test:e2e:character:fresh
