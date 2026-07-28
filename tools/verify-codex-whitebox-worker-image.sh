#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE="${1:-test-agent-opencode-worker:internal}"
EXPECTED_VERSION="${EXPECTED_CODEX_VERSION:-0.145.0}"
EXPECTED_ASSET_SHA256="${EXPECTED_CODEX_ASSET_SHA256:-bfaf13c9ba34f2ad764e4a916c49cf7177aeba329cf0f719e2227566fc8d662a}"
EXPECTED_BWRAP_SHA256="${EXPECTED_CODEX_BWRAP_BINARY_SHA256:-77360cb751ccedc5971391444ac86a8a33c15b04d6b4a6fe45f5d25496e62c4c}"
PROBE="${SCRIPT_DIR}/probe-codex-mcp-tools.mjs"
CONTRACT_TEST="${SCRIPT_DIR}/test-codex-whitebox-mcp.mjs"

version="$(docker run --rm --platform linux/amd64 \
  --entrypoint /usr/local/lib/codex/bin/codex-official "${IMAGE}" --version)"
[[ "${version}" == "codex-cli ${EXPECTED_VERSION}" ]] || {
  echo "Unexpected Codex version: ${version}" >&2
  exit 1
}

docker run --rm --platform linux/amd64 --network none --entrypoint sh "${IMAGE}" -lc "
  test -s /usr/local/lib/codex/LICENSE
  test -s /usr/local/lib/codex/NOTICE
  test -s /usr/local/lib/codex/THIRD_PARTY_LICENSES/bubblewrap-COPYING
  test -s /usr/local/lib/codex/tests/probe-codex-whitebox-e2e.mjs
  grep -Fx 'version=${EXPECTED_VERSION}' /usr/local/lib/codex/RELEASE >/dev/null
  grep -Fx 'archive_sha256=${EXPECTED_ASSET_SHA256}' /usr/local/lib/codex/RELEASE >/dev/null
  printf '%s  %s\n' '${EXPECTED_BWRAP_SHA256}' /usr/local/lib/codex/bin/codex-resources/bwrap | sha256sum -c -
  grep -Fx 'allowed_approval_policies = [\"never\"]' /etc/codex/requirements.toml >/dev/null
  grep -Fx 'allowed_web_search_modes = [\"disabled\"]' /etc/codex/requirements.toml >/dev/null
  grep -F '\"@modelcontextprotocol/sdk\": \"1.29.0\"' /usr/local/lib/opencode/package-lock.json >/dev/null
"

docker run --rm --platform linux/amd64 --network none \
  --entrypoint node \
  --volume "${PROBE}:/tmp/probe-codex-mcp-tools.mjs:ro" \
  "${IMAGE}" /tmp/probe-codex-mcp-tools.mjs raw

docker run --rm --platform linux/amd64 --network none \
  --entrypoint node \
  --volume "${PROBE}:/tmp/probe-codex-mcp-tools.mjs:ro" \
  --env TEST_AGENT_INTERNAL_PROXY_BASE_URL=http://127.0.0.1:18080/v1 \
  --env TEST_AGENT_INTERNAL_PROXY_API_KEY=smoke-proxy-key \
  --env TEST_AGENT_CODEX_PROVIDER_ID=smoke-provider \
  --env TEST_AGENT_CODEX_MODEL=smoke-model \
  --env TEST_AGENT_CODEX_CONTEXT_WINDOW=131072 \
  --env ENTERPRISE_UCID=smoke-ucid \
  "${IMAGE}" /tmp/probe-codex-mcp-tools.mjs facade

docker run --rm --platform linux/amd64 --network none \
  --entrypoint node \
  --volume "${CONTRACT_TEST}:/tmp/test-codex-whitebox-mcp.mjs:ro" \
  --env TEST_AGENT_OPENCODE_RUNTIME_ROOT=/usr/local/lib/opencode \
  --env TEST_AGENT_CODEX_FACADE_PATH=/usr/local/lib/opencode/bin/codex-whitebox-mcp.mjs \
  --env TEST_AGENT_CODEX_POLICY_PATH=/etc/codex/requirements.toml \
  "${IMAGE}" /tmp/test-codex-whitebox-mcp.mjs

# 配置缺失时门面必须在暴露工具前退出，不能退回官方原始 MCP。
if docker run --rm --platform linux/amd64 --network none \
  --entrypoint /usr/local/bin/test-agent-codex-mcp "${IMAGE}" </dev/null >/dev/null 2>&1; then
  echo "Codex whitebox facade unexpectedly started without required configuration" >&2
  exit 1
fi

# 与企业启动脚本一致使用 privileged；假模型仅监听容器 loopback，整容器保持 --network none。
docker_server_arch="$(docker info --format '{{.Architecture}}' 2>/dev/null || true)"
case "${docker_server_arch}" in
  amd64|x86_64)
    docker run --rm --privileged --platform linux/amd64 --network none \
      --entrypoint node "${IMAGE}" /usr/local/lib/codex/tests/probe-codex-whitebox-e2e.mjs
    ;;
  *)
    # Apple Silicon 上的 amd64 仿真不能创建嵌套 namespace；目标 Linux 节点仍由 host check 强制跑真实 E2E。
    echo "Codex native sandbox E2E skipped: Docker server architecture is ${docker_server_arch:-unknown}; run deploy/internal/check-codex-whitebox-host.sh on every native linux/amd64 worker node" >&2
    ;;
esac

echo "Codex whitebox worker verified: image=${IMAGE} version=${version}"
