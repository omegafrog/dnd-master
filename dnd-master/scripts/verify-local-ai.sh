#!/bin/sh
set -eu
. /workspace/scripts/local-ai-common.sh
require_model_mount
require_loopback_topology
models=$(ollama list)
printf '%s\n' "$models" | grep -Fq 'qwen3:8b'
printf '%s\n' "$models" | grep -Fq 'qwen3-embedding:0.6b'
printf '%s\n' "$models"
