#!/usr/bin/env bash
set -euo pipefail

# Test-only app-server stand-in. It exposes an authenticated account without
# reading, creating, or changing any real Codex account data.
while IFS= read -r request; do
    [[ "$request" =~ \"id\"[[:space:]]*:[[:space:]]*([0-9]+) ]] || continue
    request_id="${BASH_REMATCH[1]}"

    if [[ "$request" == *'"method":"account/read"'* || "$request" == *'"method" : "account/read"'* ]]; then
        printf '{"id":%s,"result":{"account":{"type":"chatgpt"}}}\n' "$request_id"
    else
        printf '{"id":%s,"result":{}}\n' "$request_id"
    fi
done
