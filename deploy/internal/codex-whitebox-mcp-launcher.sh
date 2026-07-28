#!/bin/bash
set -euo pipefail

# 镜像内通过 /usr/local/bin 软链接启动，必须先解析真实路径，避免把 Codex 根目录误判为 /usr/local。
SCRIPT_PATH="$(readlink -f "${BASH_SOURCE[0]}")"
SCRIPT_DIR="$(cd "$(dirname "${SCRIPT_PATH}")" && pwd)"
CODEX_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
EXTERNAL_RUNTIME_ROOT="/data/testagent/programs/opencode"
BUILTIN_RUNTIME_ROOT="/usr/local/lib/opencode"

runtime_root="${BUILTIN_RUNTIME_ROOT}"
if [[ -f "${EXTERNAL_RUNTIME_ROOT}/bin/codex-whitebox-mcp.mjs" ]]; then
  runtime_root="${EXTERNAL_RUNTIME_ROOT}"
fi

export TEST_AGENT_CODEX_BIN="${CODEX_ROOT}/bin/codex-official"
export TEST_AGENT_OPENCODE_RUNTIME_ROOT="${runtime_root}"
export PATH="/usr/local/bin:/usr/bin:/bin"
exec /usr/local/bin/node "${runtime_root}/bin/codex-whitebox-mcp.mjs"
