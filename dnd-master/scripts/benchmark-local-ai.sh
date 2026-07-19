#!/bin/sh
set -eu
. /workspace/scripts/local-ai-common.sh
require_loopback_topology
iteration=1
samples=''
while [ "$iteration" -le 7 ]; do
  start=$(date +%s%N)
  if ! mvn -q -pl ai-game-master-service -am -Dtest=LocalAiBenchmarkRouteTest -Dspring.ai.ollama.base-url=http://localhost:11434 -Dlocal-ai.ollama.base-url=http://localhost:11434 test >/tmp/local-ai-benchmark-maven.log 2>&1; then
    tail -100 /tmp/local-ai-benchmark-maven.log >&2
    exit 1
  fi
  elapsed=$((($(date +%s%N) - start) / 1000000))
  if [ "$iteration" -gt 2 ]; then samples="$samples $elapsed"; fi
  iteration=$((iteration + 1))
done
set -- $(printf '%s\n' "$samples" | tr ' ' '\n' | sort -n)
printf '{"warmup_runs":2,"measured_runs":5,"samples_ms":[%s,%s,%s,%s,%s],"p95_ms":%s}\n' "$1" "$2" "$3" "$4" "$5" "$5"
