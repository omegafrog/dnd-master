#!/usr/bin/env bash
set -euo pipefail

# Test-only app-server stand-in. It exposes an authenticated account without
# reading, creating, or changing any real Codex account data.
login_attempt=0
while IFS= read -r request; do
    [[ "$request" =~ \"id\"[[:space:]]*:[[:space:]]*([0-9]+) ]] || continue
    request_id="${BASH_REMATCH[1]}"

    if [[ "$request" == *'"method":"account/read"'* || "$request" == *'"method" : "account/read"'* ]]; then
        if (( login_attempt == 0 || login_attempt >= 3 )); then
            printf '{"id":%s,"result":{"account":{"type":"chatgpt"}}}\n' "$request_id"
        else
            printf '{"id":%s,"result":{"account":null}}\n' "$request_id"
        fi
    elif [[ "$request" == *'"method":"account/login/start"'* || "$request" == *'"method" : "account/login/start"'* ]]; then
        login_attempt=$((login_attempt + 1))
        printf '{"id":%s,"result":{"authUrl":"https://auth.example.test/login"}}\n' "$request_id"
        case "$login_attempt" in
            1) printf '{"method":"account/login/cancelled"}\n' ;;
            2) printf '{"method":"account/login/failed"}\n' ;;
            *) printf '{"method":"account/login/completed"}\n' ;;
        esac
    else
        printf '{"id":%s,"result":{}}\n' "$request_id"
    fi
done
