#!/usr/bin/env bash
set -euo pipefail

# Populate the local shared catalog through the same authenticated backoffice
# API used by the application. This keeps a fresh database usable by live
# browser tests without embedding a session token in source or command lines.

require_env() {
    local name="$1"
    if [ -z "${!name:-}" ]; then
        echo "ERROR: required environment variable $name is missing or blank." >&2
        exit 1
    fi
}

require_env BACKEND_E2E_URL
require_env BACKEND_E2E_EMAIL
require_env BACKEND_E2E_PASSWORD
require_env RULE_KNOWLEDGE_ASSET_FALLBACK_ROOT

case "$BACKEND_E2E_URL" in
    http://127.0.0.1:*|http://localhost:*|https://127.0.0.1:*|https://localhost:*) ;;
    *) echo "ERROR: BACKEND_E2E_URL must be a local Linux development URL." >&2; exit 1 ;;
esac

CATALOG_PDF="${BACKEND_E2E_CATALOG_PDF:-$RULE_KNOWLEDGE_ASSET_FALLBACK_ROOT/DnD_BasicRules_2018.pdf}"
if [ ! -f "$CATALOG_PDF" ]; then
    echo "ERROR: local shared catalog PDF was not found: $CATALOG_PDF" >&2
    exit 1
fi
CATALOG_PDF_SHA256="$(sha256sum "$CATALOG_PDF" | cut -d ' ' -f 1)"
if [ "$CATALOG_PDF_SHA256" != "7a0c5d8bf52d15092f156d78418aa3d43307e271f810d2f06bf2f0258e9288a3" ]; then
    echo "ERROR: local shared catalog PDF does not match the approved Basic Rules source." >&2
    exit 1
fi
USE_EXACT_SOURCE="${BACKEND_E2E_CATALOG_PDF:+true}"
EXPECTED_RULEBOOK_ID=""

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/dnd-master-catalog.XXXXXX")"
cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT
AUTH_CONFIG="$TMP_DIR/curl-auth.conf"
RESPONSE="$TMP_DIR/response.json"

json_value() {
    local expression="$1" file="$2"
    node -e 'const fs=require("node:fs"); const value=JSON.parse(fs.readFileSync(process.argv[1], "utf8")); const result=Function("value", "return " + process.argv[2])(value); if (result === undefined || result === null || result === "") process.exit(2); process.stdout.write(String(result));' "$file" "$expression"
}

catalog_is_ready() {
    node -e 'const fs=require("node:fs"); const catalog=JSON.parse(fs.readFileSync(process.argv[1], "utf8")); const expected=process.argv[2]; process.exit(Array.isArray(catalog) && catalog.some(item => item.edition === "DND_5E_2014" && item.status === "READY" && item.rulebookId && (!expected || item.rulebookId === expected)) ? 0 : 1);' "$RESPONSE" "$EXPECTED_RULEBOOK_ID"
}

request() {
    local method="$1" url="$2"
    shift 2
    curl --silent --show-error --output "$RESPONSE" --write-out '%{http_code}' \
        --config "$AUTH_CONFIG" --request "$method" "$@" "$url"
}

echo "==> Ensuring published D&D 5e (2014) shared catalog rulebook..."
status="$(curl --silent --show-error --output "$RESPONSE" --write-out '%{http_code}' "$BACKEND_E2E_URL/api/v1/rulebook-catalog")"
if [ "$status" = "200" ] && [ -z "$USE_EXACT_SOURCE" ] && catalog_is_ready; then
    echo "    Shared catalog rulebook is ready."
    exit 0
fi
if [ "$status" != "200" ]; then
    echo "ERROR: could not read the public shared catalog (HTTP $status)." >&2
    exit 1
fi

status="$(curl --silent --show-error --output "$RESPONSE" --write-out '%{http_code}' \
    --header 'Content-Type: application/json' --data-binary @- \
    "$BACKEND_E2E_URL/api/v1/auth/login" <<EOF
{"username":"$BACKEND_E2E_EMAIL","password":"$BACKEND_E2E_PASSWORD"}
EOF
)"
if [ "$status" != "200" ]; then
    echo "ERROR: local demo user authentication failed while seeding the shared catalog (HTTP $status)." >&2
    exit 1
fi
session_token="$(json_value 'value.token' "$RESPONSE")" || {
    echo "ERROR: local demo user authentication did not return a session token." >&2
    exit 1
}
printf 'header = "Authorization: Bearer %s"\n' "$session_token" > "$AUTH_CONFIG"
chmod 600 "$AUTH_CONFIG"
unset session_token

status="$(request GET "$BACKEND_E2E_URL/api/v1/backoffice/rulebook-catalog")"
if [ "$status" != "200" ]; then
    echo "ERROR: local demo user cannot access the shared catalog backoffice (HTTP $status)." >&2
    exit 1
fi
revision_id=""
if [ -z "$USE_EXACT_SOURCE" ]; then
    revision_id="$(json_value 'value.filter(item => item.edition === "DND_5E_2014" && !item.published).sort((a, b) => b.revisionNumber - a.revisionNumber)[0]?.catalogRevisionId' "$RESPONSE" || true)"
fi
if [ -z "$revision_id" ]; then
    status="$(request POST "$BACKEND_E2E_URL/api/v1/backoffice/rulebook-catalog" \
        --form 'edition=DND_5E_2014' \
        --form "file=@$CATALOG_PDF;type=application/pdf")"
    if [ "$status" != "200" ]; then
        echo "ERROR: local shared catalog upload failed (HTTP $status)." >&2
        exit 1
    fi
    revision_id="$(json_value 'value.catalogRevisionId ?? value.id' "$RESPONSE")" || {
        echo "ERROR: local shared catalog upload did not return a revision identifier." >&2
        exit 1
    }
    EXPECTED_RULEBOOK_ID="$(json_value 'value.rulebookId' "$RESPONSE")" || {
        echo "ERROR: local shared catalog upload did not return its document identifier." >&2
        exit 1
    }
else
    EXPECTED_RULEBOOK_ID="$(json_value "value.filter(item => item.catalogRevisionId === '$revision_id')[0]?.rulebookId" "$RESPONSE")" || {
        echo "ERROR: local shared catalog draft did not include its document identifier." >&2
        exit 1
    }
fi

attempt=0
while true; do
    attempt=$((attempt + 1))
    status="$(request POST "$BACKEND_E2E_URL/api/v1/backoffice/rulebook-catalog/$revision_id/publish")"
    if [ "$status" = "200" ]; then
        status="$(curl --silent --show-error --output "$RESPONSE" --write-out '%{http_code}' "$BACKEND_E2E_URL/api/v1/rulebook-catalog")"
        if [ "$status" = "200" ] && catalog_is_ready; then
            echo "    Shared catalog rulebook is ready."
            exit 0
        fi
    elif [ "$status" != "409" ]; then
        echo "ERROR: local shared catalog publish failed (HTTP $status)." >&2
        exit 1
    fi
    if (( attempt % 30 == 0 )); then
        echo "    Rulebook is still being processed; continuing to wait (${attempt} checks)."
    fi
    sleep 2
done
