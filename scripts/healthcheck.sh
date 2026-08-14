#!/usr/bin/env bash
set -Eeuo pipefail

# Local development health monitor. Run from WSL/Linux only:
#   ./scripts/healthcheck.sh
#   ./scripts/healthcheck.sh --watch --interval 30

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT/src/infra/compose.yaml}"
BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:5173}"
CATALOG_URL="${CATALOG_URL:-$BACKEND_URL/api/v1/rulebook-catalog}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-30}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-5}"
WATCH=false

usage() {
    cat <<'EOF'
Usage: scripts/healthcheck.sh [options]

Checks PostgreSQL, the backend health endpoint, the frontend, and the public
rulebook catalog endpoint. A single check is run by default.

Options:
  --watch              Repeat checks until interrupted
  --interval SECONDS   Delay between checks (default: 30)
  --timeout SECONDS    Timeout for HTTP checks (default: 5)
  -h, --help           Show this help

Environment overrides:
  COMPOSE_FILE, BACKEND_URL, FRONTEND_URL, CATALOG_URL,
  INTERVAL_SECONDS, TIMEOUT_SECONDS
EOF
}

die() {
    echo "ERROR: $*" >&2
    exit 2
}

require_positive_integer() {
    [[ "$2" =~ ^[1-9][0-9]*$ ]] || die "$1 must be a positive integer: $2"
}

while (($# > 0)); do
    case "$1" in
        --watch)
            WATCH=true
            shift
            ;;
        --interval)
            (($# >= 2)) || die "--interval requires a value"
            INTERVAL_SECONDS="$2"
            shift 2
            ;;
        --timeout)
            (($# >= 2)) || die "--timeout requires a value"
            TIMEOUT_SECONDS="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            die "unknown option: $1"
            ;;
    esac
done

[[ "$(uname -s)" == "Linux" ]] || die "Run this script inside WSL/Linux."
require_positive_integer "interval" "$INTERVAL_SECONDS"
require_positive_integer "timeout" "$TIMEOUT_SECONDS"

command -v curl >/dev/null 2>&1 || die "curl is required"
command -v docker >/dev/null 2>&1 || die "docker is required"
[[ -f "$COMPOSE_FILE" ]] || die "compose file not found: $COMPOSE_FILE"

CHECK_NAMES=()
CHECK_RESULTS=()

record_check() {
    CHECK_NAMES+=("$1")
    CHECK_RESULTS+=("$2")
}

check_postgres() {
    local output
    if ! output="$(docker compose -f "$COMPOSE_FILE" ps --status running --services 2>&1)"; then
        record_check "PostgreSQL container" "FAIL ($output)"
        return
    fi
    if ! grep -qx 'postgres' <<<"$output"; then
        record_check "PostgreSQL container" "FAIL (not running)"
        return
    fi
    if docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U postgres -d postgres >/dev/null 2>&1; then
        record_check "PostgreSQL" "PASS"
    else
        record_check "PostgreSQL" "FAIL (not ready)"
    fi
}

check_http() {
    local name="$1"
    local url="$2"
    local expected_status="${3:-}"
    local status

    if ! status="$(curl --silent --show-error --output /dev/null --max-time "$TIMEOUT_SECONDS" --write-out '%{http_code}' "$url" 2>/dev/null)"; then
        record_check "$name" "FAIL (unreachable)"
        return
    fi
    if [[ -n "$expected_status" && "$status" != "$expected_status" ]]; then
        record_check "$name" "FAIL (HTTP $status)"
    elif [[ "$status" =~ ^2[0-9][0-9]$ ]]; then
        record_check "$name" "PASS (HTTP $status)"
    else
        record_check "$name" "FAIL (HTTP $status)"
    fi
}

run_check() {
    CHECK_NAMES=()
    CHECK_RESULTS=()
    echo "[$(date '+%Y-%m-%d %H:%M:%S %Z')] health check"

    check_postgres
    check_http "Backend health" "$BACKEND_URL/actuator/health"
    check_http "Frontend" "$FRONTEND_URL/"
    check_http "Rulebook catalog API" "$CATALOG_URL"

    local failed=0
    local i
    for i in "${!CHECK_NAMES[@]}"; do
        printf '  %-22s %s\n' "${CHECK_NAMES[$i]}" "${CHECK_RESULTS[$i]}"
        [[ "${CHECK_RESULTS[$i]}" == PASS* ]] || failed=1
    done

    if ((failed == 0)); then
        echo "Result: PASS"
    else
        echo "Result: FAIL"
    fi
    return "$failed"
}

if [[ "$WATCH" == true ]]; then
    while true; do
        run_check || true
        echo "Next check in ${INTERVAL_SECONDS}s (Ctrl-C to stop)."
        sleep "$INTERVAL_SECONDS"
    done
else
    run_check
fi
