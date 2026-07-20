#!/bin/sh
set -eu
root=/workspace
compose=$(cat "$root/compose.local-ai.yml")
for contract in 'network_mode: service:ollama' '127.0.0.1:${LOCAL_AI_HOST_PORT:-11434}:11434' 'http://localhost:11434' ':/root/.ollama'; do
  printf '%s' "$compose" | grep -Fq "$contract"
done
! printf '%s' "$compose" | grep -Fq 'ollama pull'
grep -Fq 'ollama pull qwen3:4b-instruct-2507-q4_K_M' "$root/scripts/prepare-local-ai.sh"
grep -Fq 'ollama pull qwen3-embedding:0.6b' "$root/scripts/prepare-local-ai.sh"
! grep -Fq 'ollama pull' "$root/scripts/verify-local-ai.sh"
! grep -Fq 'ollama pull' "$root/scripts/benchmark-local-ai.sh"
grep -Fq 'LocalAiBenchmarkRouteTest' "$root/scripts/benchmark-local-ai.sh"
grep -Fq 'http://localhost:11434' "$root/scripts/benchmark-local-ai.sh"
grep -Fq 'WARMUP_RUNS = 2' "$root/ai-game-master-service/src/test/java/com/dndmaster/aigamemaster/LocalAiBenchmarkRouteTest.java"
grep -Fq 'MEASURED_RUNS = 5' "$root/ai-game-master-service/src/test/java/com/dndmaster/aigamemaster/LocalAiBenchmarkRouteTest.java"
grep -Fq 'TTFT_TARGET_MS = 10_000' "$root/ai-game-master-service/src/test/java/com/dndmaster/aigamemaster/LocalAiBenchmarkRouteTest.java"

. "$root/scripts/local-ai-common.sh"
validate_mount_metadata bind 'F:\DNDMaster\ollama' /root/.ollama false 21474836480
for fixture in \
  "volume|F:\DNDMaster\ollama|/root/.ollama|false|21474836480" \
  "bind|C:\models|/root/.ollama|false|21474836480" \
  "bind|models|/root/.ollama|false|21474836480" \
  "bind|F:\DNDMaster\ollama|/models|false|21474836480" \
  "bind|F:\DNDMaster\ollama|/root/.ollama|true|21474836480" \
  "bind|F:\DNDMaster\ollama|/root/.ollama|false|21474836479"; do
  old_ifs=$IFS
  IFS='|'
  set -- $fixture
  IFS=$old_ifs
  if validate_mount_metadata "$1" "$2" "$3" "$4" "$5" >/dev/null 2>&1; then
    echo "Rejected mount metadata fixture was accepted: $fixture" >&2
    exit 1
  fi
done

fixture_root=/tmp/local-ai-model-mount
mkdir -p "$fixture_root"
LOCAL_AI_MODEL_MOUNT=$fixture_root LOCAL_AI_REQUIRE_MOUNT=0 LOCAL_AI_MIN_FREE_KB=1 require_model_mount
if LOCAL_AI_MODEL_MOUNT=$fixture_root LOCAL_AI_REQUIRE_MOUNT=0 LOCAL_AI_MIN_FREE_KB=999999999999 require_model_mount >/dev/null 2>&1; then
  echo 'Insufficient-space fixture was accepted.' >&2
  exit 1
fi
rm -f /tmp/local-ai-model-link
ln -s "$fixture_root" /tmp/local-ai-model-link
if LOCAL_AI_MODEL_MOUNT=/tmp/local-ai-model-link LOCAL_AI_REQUIRE_MOUNT=0 LOCAL_AI_MIN_FREE_KB=1 require_model_mount >/dev/null 2>&1; then
  echo 'Symbolic-link fixture was accepted.' >&2
  exit 1
fi

fake_bin=/tmp/local-ai-fake-bin
mkdir -p "$fake_bin"
printf '%s\n' '#!/bin/sh' 'count=$(cat /tmp/local-ai-mvn-count 2>/dev/null || echo 0)' 'echo $((count + 1)) > /tmp/local-ai-mvn-count' "echo 'LOCAL_AI_BENCHMARK {\"warmup_runs\":2,\"measured_runs\":5,\"ttft_samples_ms\":[100, 100, 100, 100, 100],\"ttft_p95_ms\":100}'" > "$fake_bin/mvn"
chmod +x "$fake_bin/mvn"
rm -f /tmp/local-ai-mvn-count
benchmark_output=$(LOCAL_AI_MODEL_MOUNT=$fixture_root LOCAL_AI_REQUIRE_MOUNT=0 LOCAL_AI_MIN_FREE_KB=1 \
  SPRING_AI_OLLAMA_BASE_URL=http://localhost:11434 LOCAL_AI_OLLAMA_BASE_URL=http://localhost:11434 \
  PATH="$fake_bin:$PATH" sh "$root/scripts/benchmark-local-ai.sh")
test "$(cat /tmp/local-ai-mvn-count)" = 1
test "$benchmark_output" = '{"warmup_runs":2,"measured_runs":5,"ttft_samples_ms":[100, 100, 100, 100, 100],"ttft_p95_ms":100}'
echo 'PASS local AI Linux orchestration contracts'
