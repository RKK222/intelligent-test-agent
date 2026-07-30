#!/bin/bash
set -euo pipefail

# 仅把企业内部模型代理转换为官方 Codex 配置，随后直接替换为官方 MCP 进程。
# 本脚本不代理 MCP 协议，不过滤工具、参数、返回值、stderr 或 threadId。
SCRIPT_PATH="$(readlink -f "${BASH_SOURCE[0]}")"
SCRIPT_DIR="$(cd "$(dirname "${SCRIPT_PATH}")" && pwd)"
CODEX_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

required_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || {
    echo "Codex MCP configuration missing: ${name}" >&2
    exit 1
  }
}

toml_string() {
  /usr/local/bin/node -e 'process.stdout.write(JSON.stringify(process.argv[1]))' "$1"
}

for name in \
  TEST_AGENT_INTERNAL_PROXY_BASE_URL \
  TEST_AGENT_INTERNAL_PROXY_API_KEY \
  TEST_AGENT_CODEX_PROVIDER_ID \
  TEST_AGENT_CODEX_MODEL \
  TEST_AGENT_CODEX_CONTEXT_WINDOW \
  ENTERPRISE_UCID; do
  required_env "${name}"
done

[[ "${TEST_AGENT_CODEX_CONTEXT_WINDOW}" =~ ^[0-9]+$ ]] \
  && (( TEST_AGENT_CODEX_CONTEXT_WINDOW >= 4096 && TEST_AGENT_CODEX_CONTEXT_WINDOW <= 2000000 )) || {
    echo "Codex MCP configuration invalid: TEST_AGENT_CODEX_CONTEXT_WINDOW" >&2
    exit 1
  }

proxy_base_url="$({
  /usr/local/bin/node -e '
    const value = process.argv[1];
    const parsed = new URL(value);
    if (!["http:", "https:"].includes(parsed.protocol) || parsed.username || parsed.password) process.exit(2);
    process.stdout.write(parsed.toString().replace(/\/$/, ""));
  ' "${TEST_AGENT_INTERNAL_PROXY_BASE_URL}"
} 2>/dev/null)" || {
  echo "Codex MCP configuration invalid: TEST_AGENT_INTERNAL_PROXY_BASE_URL" >&2
  exit 1
}

export PATH="/usr/local/bin:/usr/bin:/bin"
provider_key="test-agent-internal"

[[ "${HOME:-}" == /* ]] || {
  echo "Codex MCP configuration invalid: HOME" >&2
  exit 1
}

# 官方 MCP 的每次 codex 工具调用都会重新读取 CODEX_HOME/config.toml，不会继承
# mcp-server 命令行上的供应商覆盖；因此使用官方配置文件注入企业模型，不介入 MCP 协议。
export CODEX_HOME="${HOME}/.testagent-codex-mcp"
install -d -m 0700 "${CODEX_HOME}"
config_tmp="$(mktemp "${CODEX_HOME}/config.toml.tmp.XXXXXX")"
trap 'rm -f "${config_tmp}"' EXIT
{
  printf 'model = %s\n' "$(toml_string "${TEST_AGENT_CODEX_MODEL}")"
  printf 'model_provider = %s\n' "$(toml_string "${provider_key}")"
  printf 'model_context_window = %s\n' "${TEST_AGENT_CODEX_CONTEXT_WINDOW}"
  printf '%s\n' \
    'approval_policy = "never"' \
    '' \
    "[model_providers.${provider_key}]" \
    'name = "TestAgent internal model proxy"'
  printf 'base_url = %s\n' "$(toml_string "${proxy_base_url}")"
  printf '%s\n' \
    'env_key = "TEST_AGENT_INTERNAL_PROXY_API_KEY"' \
    'wire_api = "responses"' \
    'supports_websockets = false' \
    'env_http_headers = { "X-Enterprise-Model-Provider" = "TEST_AGENT_CODEX_PROVIDER_ID", "ucid" = "ENTERPRISE_UCID" }'
} >"${config_tmp}"
chmod 0600 "${config_tmp}"
mv -f "${config_tmp}" "${CODEX_HOME}/config.toml"
trap - EXIT

exec "${CODEX_ROOT}/bin/codex-official" mcp-server --strict-config
