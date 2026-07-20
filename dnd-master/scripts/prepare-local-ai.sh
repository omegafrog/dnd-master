#!/bin/sh
set -eu
. /workspace/scripts/local-ai-common.sh
require_model_mount
ollama pull qwen3:4b-instruct-2507-q4_K_M
ollama pull qwen3-embedding:0.6b
ollama list
