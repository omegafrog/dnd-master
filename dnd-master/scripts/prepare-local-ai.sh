#!/bin/sh
set -eu
. /workspace/scripts/local-ai-common.sh
require_model_mount
ollama pull qwen3:8b
ollama pull qwen3-embedding:0.6b
ollama list
