#!/bin/sh
set -eu
. /workspace/scripts/local-ai-common.sh
require_loopback_topology
num_predict=${NUM_PREDICT:?Set NUM_PREDICT to the candidate token cap}
case "$num_predict" in *[!0-9]*|'') echo 'NUM_PREDICT must be a positive integer.' >&2; exit 1;; esac
test "$num_predict" -gt 0 || { echo 'NUM_PREDICT must be positive.' >&2; exit 1; }
if ! mvn -q -pl ai-game-master-service -am -Dtest=LocalAiBenchmarkRouteTest \
  -Dspring.ai.ollama.base-url=http://localhost:11434 \
  -Dlocal-ai.ollama.base-url=http://localhost:11434 \
  -Dlocal-ai.ollama.request-timeout=120s \
  -Dspring.ai.ollama.chat.options.num-predict="$num_predict" test >/tmp/local-ai-benchmark-maven.log 2>&1; then
  tail -100 /tmp/local-ai-benchmark-maven.log >&2
  exit 1
fi
result=$(grep '^LOCAL_AI_BENCHMARK ' /tmp/local-ai-benchmark-maven.log | tail -1 | sed 's/^LOCAL_AI_BENCHMARK //')
test -n "$result" || { echo 'Benchmark result marker missing.' >&2; exit 1; }
printf '%s\n' "$result"
