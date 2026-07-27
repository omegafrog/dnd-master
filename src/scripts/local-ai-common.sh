#!/bin/sh
set -eu

fail_validation() {
  echo "$1" >&2
  return 1
}

validate_mount_metadata() {
  mount_type=$1
  host_source=$2
  destination=$3
  source_is_reparse=$4
  free_bytes=$5

  test "$mount_type" = bind || fail_validation 'Ollama storage must be a bind mount, not a named volume.' || return 1
  case "$host_source" in
    [EeFf]:\\*|[EeFf]:/*) ;;
    *) fail_validation 'Ollama bind source metadata must identify a canonical E: or F: host directory.'; return 1 ;;
  esac
  case "$host_source" in
    *'..'*|*'//'*) fail_validation 'Ollama bind source metadata contains an unresolved path segment.'; return 1 ;;
  esac
  test "$destination" = /root/.ollama || fail_validation 'Ollama bind destination must be /root/.ollama.' || return 1
  test "$source_is_reparse" = false || fail_validation 'Ollama bind source must not be a junction or reparse point.' || return 1
  test "$free_bytes" -ge 21474836480 || fail_validation 'Ollama bind source requires at least 20 GB free.' || return 1
}

require_model_mount() {
  model_mount=${LOCAL_AI_MODEL_MOUNT:-/root/.ollama}
  minimum_kb=${LOCAL_AI_MIN_FREE_KB:-20971520}
  test -d "$model_mount" || fail_validation 'Ollama model mount directory is missing.' || return 1
  test ! -L "$model_mount" || fail_validation 'Ollama model mount must not be a symbolic link.' || return 1
  if test "${LOCAL_AI_REQUIRE_MOUNT:-1}" = 1; then
    awk -v target="$model_mount" '$5 == target { found=1 } END { exit found ? 0 : 1 }' /proc/self/mountinfo ||
      fail_validation 'Ollama model path is not a container mount point.' || return 1
  fi
  available_kb=$(df -Pk "$model_mount" | awk 'NR == 2 { print $4 }')
  test -n "$available_kb" && test "$available_kb" -ge "$minimum_kb" ||
    fail_validation 'Ollama model mount requires at least 20 GB free.' || return 1
}

require_loopback_topology() {
  test "${SPRING_AI_OLLAMA_BASE_URL:-}" = 'http://localhost:11434' ||
    fail_validation 'Spring AI Ollama base URL must use container loopback.' || return 1
  test "${LOCAL_AI_OLLAMA_BASE_URL:-}" = 'http://localhost:11434' ||
    fail_validation 'Local AI Ollama base URL must use container loopback.' || return 1
}
