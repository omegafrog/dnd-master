#!/bin/sh
set -eu
. /workspace/scripts/local-ai-common.sh
require_loopback_topology
num_predict=${NUM_PREDICT:?Set NUM_PREDICT to the candidate token cap}
case "$num_predict" in *[!0-9]*|'') echo 'NUM_PREDICT must be a positive integer.' >&2; exit 1;; esac
test "$num_predict" -gt 0 || { echo 'NUM_PREDICT must be positive.' >&2; exit 1; }
report=ai-game-master-service/build/test-results/test/TEST-com.dndmaster.aigamemaster.LocalAiBenchmarkRouteTest.xml
if ! ./gradlew -q -Dspring.ai.ollama.base-url=http://localhost:11434 \
  :ai-game-master-service:test --tests com.dndmaster.aigamemaster.LocalAiBenchmarkRouteTest \
  >/tmp/local-ai-benchmark-gradle.log 2>&1; then
  tail -100 /tmp/local-ai-benchmark-gradle.log >&2
  exit 1
fi
result=$(grep '^LOCAL_AI_BENCHMARK ' "$report" | tail -1 | sed 's/^LOCAL_AI_BENCHMARK //')
test -n "$result" || { echo 'Benchmark result marker missing.' >&2; exit 1; }
printf '%s\n' "$result"
