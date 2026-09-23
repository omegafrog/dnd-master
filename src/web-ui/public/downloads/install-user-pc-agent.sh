#!/usr/bin/env bash
set -euo pipefail

base_url="${1:?사용법: install-user-pc-agent.sh <서버 주소> <연결 토큰>}"
access_token="${2:?사용법: install-user-pc-agent.sh <서버 주소> <연결 토큰>}"
command -v java >/dev/null 2>&1 || { echo 'Java 21 이상이 필요합니다.' >&2; exit 1; }
if ! command -v codex >/dev/null 2>&1; then
  curl -fsSL https://chatgpt.com/codex/install.sh | sh
fi
target_dir="${USER_PC_AGENT_DIR:-$HOME/.dnd-master/user-pc-agent}"
mkdir -p "$target_dir"
archive="$target_dir/user-pc-agent.tar.gz"
curl -fsSL "$base_url/downloads/user-pc-agent-linux.tar.gz" -o "$archive"
tar -xzf "$archive" -C "$target_dir"
export RELAY_WEBSOCKET_URL="${RELAY_WEBSOCKET_URL:-${base_url/http/ws}/ws/agent}"
export AGENT_ACCESS_TOKEN="$access_token"
export CODEX_EXECUTABLE="${CODEX_EXECUTABLE:-codex}"
exec "$target_dir/user-pc-agent/bin/user-pc-agent"
