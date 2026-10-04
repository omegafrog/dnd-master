#!/usr/bin/env bash
set -euo pipefail

# Deterministic test-only app-server. The first execution succeeds, the second
# is rejected for expired authentication, and later executions succeed after
# the UI explicitly reauthenticates. It never reads local Codex credentials.
turn_count=0
thread_count=0

record_turn() {
    local count_file="${CODEX_E2E_TURN_COUNT_FILE:-}"
    if [[ -n "$count_file" ]]; then
        printf '%s\n' "$turn_count" > "$count_file"
    fi
}

while IFS= read -r request; do
    [[ "$request" =~ \"id\"[[:space:]]*:[[:space:]]*([0-9]+) ]] || continue
    request_id="${BASH_REMATCH[1]}"

    case "$request" in
        *'"method":"account/read"'*|*'"method" : "account/read"'*)
            printf '{"id":%s,"result":{"account":{"type":"chatgpt"}}}\n' "$request_id"
            ;;
        *'"method":"account/login/start"'*|*'"method" : "account/login/start"'*)
            printf '{"id":%s,"result":{"authUrl":"https://auth.example.test/reauth"}}\n' "$request_id"
            printf '{"method":"account/login/completed"}\n'
            ;;
        *'"method":"thread/start"'*|*'"method" : "thread/start"'*)
            thread_count=$((thread_count + 1))
            printf '{"id":%s,"result":{"thread":{"id":"fixture-thread-%s"}}}\n' "$request_id" "$thread_count"
            ;;
        *'"method":"turn/start"'*|*'"method" : "turn/start"'*)
            if [[ "$request" == *'ROLE=SCENARIO_LOOKUP'* ]]; then
                printf '{"id":%s,"result":{"turn":{"id":"fixture-turn-scenario-lookup"}}}\n' "$request_id"
                printf '%s\n' '{"method":"item/agentMessage/delta","params":{"delta":"{\"status\":\"NOT_FOUND\",\"answer\":\"\",\"supportingElementIds\":[]}"}}'
                printf '%s\n' '{"method":"turn/completed","params":{"turn":{"id":"fixture-turn-scenario-lookup"}}}'
            elif [[ "$request" == *'TASK=EVIDENCE_RERANK'* ]]; then
                printf '{"id":%s,"result":{"turn":{"id":"fixture-turn-rerank"}}}\n' "$request_id"
                printf '%s\n' '{"method":"item/agentMessage/delta","params":{"delta":"{\"orderedCandidateIds\":[]}"}}'
                printf '%s\n' '{"method":"turn/completed","params":{"turn":{"id":"fixture-turn-rerank"}}}'
            elif [[ "$request" == *'OUTPUT={sufficient:boolean'* ]]; then
                printf '{"id":%s,"result":{"turn":{"id":"fixture-turn-sufficiency"}}}\n' "$request_id"
                printf '%s\n' '{"method":"item/agentMessage/delta","params":{"delta":"{\"sufficient\":false,\"selectedEvidenceIds\":[],\"selectionReasons\":{},\"missing\":\"관련 자료에서 확인되지 않았습니다.\"}"}}'
                printf '%s\n' '{"method":"turn/completed","params":{"turn":{"id":"fixture-turn-sufficiency"}}}'
            else
                turn_count=$((turn_count + 1))
                record_turn
                printf '{"id":%s,"result":{"turn":{"id":"fixture-turn-%s"}}}\n' "$request_id" "$turn_count"
                if (( turn_count == 2 )); then
                    printf '%s\n' '{"method":"turn/failed","params":{"error":{"code":"UNAUTHORIZED","message":"credentials expired"}}}'
                else
                    printf '%s\n' '{"method":"item/agentMessage/delta","params":{"delta":"{\"scene\":\"A quiet path through the woods.\",\"judgment\":\"행동이 통했습니다.\",\"narration\":\"숲길에서 발자국을 발견했습니다.\",\"combatStart\":false,\"combatEnemies\":[],\"situation\":{\"kind\":\"CONTINUE\",\"location\":\"숲길\",\"problem\":\"길을 살펴봅니다.\",\"threat\":\"아직 확인되지 않았습니다.\",\"goal\":\"흔적을 찾습니다.\",\"basis\":\"FALLBACK\",\"reference\":\"\",\"required\":true},\"mapEntryRequested\":false,\"runtimeFacts\":[]}"}}'
                    printf '%s\n' '{"method":"turn/completed","params":{"turn":{"id":"fixture-turn-complete"}}}'
                fi
            fi
            ;;
        *)
            printf '{"id":%s,"result":{}}\n' "$request_id"
            ;;
    esac
done
