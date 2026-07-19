#!/bin/sh
set -eu
. /workspace/scripts/local-ai-common.sh
require_loopback_topology
if ! mvn -q -pl ai-game-master-service -am -Dtest=LocalAiBenchmarkRouteTest -Dspring.ai.ollama.base-url=http://localhost:11434 -Dlocal-ai.ollama.base-url=http://localhost:11434 test >/tmp/local-ai-benchmark-maven.log 2>&1; then
  tail -100 /tmp/local-ai-benchmark-maven.log >&2
  exit 1
fi
result=$(grep '^LOCAL_AI_BENCHMARK ' /tmp/local-ai-benchmark-maven.log | tail -1 | sed 's/^LOCAL_AI_BENCHMARK //')
test -n "$result" || { echo 'Benchmark result marker missing.' >&2; exit 1; }
printf '%s\n' "$result"
