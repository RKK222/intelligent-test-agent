#!/usr/bin/env bash
set -euo pipefail

IMAGE="${1:-test-agent-opencode-worker:internal}"
CONTAINER="test-agent-opencode-node-smoke-$$"

cleanup() {
  docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

glibc_version="$(docker run --rm --platform linux/amd64 --entrypoint getconf "${IMAGE}" GNU_LIBC_VERSION)"
if [[ "${glibc_version}" != "glibc 2.31" ]]; then
  echo "Unexpected glibc version: ${glibc_version}; Docker 18.09 compatibility requires bullseye/glibc 2.31" >&2
  exit 1
fi

docker run --rm --platform linux/amd64 --entrypoint node "${IMAGE}" \
  -e 'require("node:worker_threads"); console.log("node worker runtime ok")' >/dev/null

version="$(docker run --rm --platform linux/amd64 --entrypoint /usr/local/bin/opencode "${IMAGE}" --version)"
if [[ "${version}" != "1.17.8" ]]; then
  echo "Unexpected opencode version: ${version}" >&2
  exit 1
fi

# 用最小公共配置拉起真实 Node server；不发布宿主机端口，所有探测都在容器内完成。
docker run -d \
  --platform linux/amd64 \
  --name "${CONTAINER}" \
  --network none \
  --entrypoint bash \
  "${IMAGE}" \
  -lc 'mkdir -p /tmp/opencode-config/tools /tmp/opencode-data /tmp/workspace/.opencode/tools && printf "%s\n" "{\"\$schema\":\"https://opencode.ai/config.json\"}" > /tmp/opencode-config/opencode.json && printf "%s\n" '\''import { tool } from "@opencode-ai/plugin"; export default tool({ description: "public offline probe", args: { value: tool.schema.string().optional() }, async execute(args) { return args.value ?? "public-ok" } })'\'' > /tmp/opencode-config/tools/public-probe.ts && printf "%s\n" '\''import { tool } from "@opencode-ai/plugin"; import * as sdk from "@opencode-ai/sdk"; import * as Effect from "effect"; import { z } from "zod"; const loaded = Boolean(sdk && Effect && z); export default tool({ description: "workspace offline probe", args: { value: z.string().optional() }, async execute(args) { return loaded ? (args.value ?? "workspace-ok") : "missing" } })'\'' > /tmp/workspace/.opencode/tools/workspace-probe.ts && cd /tmp/workspace && XDG_DATA_HOME=/tmp/opencode-data OPENCODE_CONFIG_DIR=/tmp/opencode-config exec /usr/local/bin/opencode serve --hostname 127.0.0.1 --port 4096 --print-logs' \
  >/dev/null

healthy=0
for _ in {1..60}; do
  if docker exec "${CONTAINER}" node -e \
    'fetch("http://127.0.0.1:4096/global/health").then((response) => { if (!response.ok) process.exit(1) }).catch(() => process.exit(1))' \
    >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 1
done
if [[ "${healthy}" -ne 1 ]]; then
  docker logs "${CONTAINER}" >&2 || true
  echo "OpenCode Node server health check timed out" >&2
  exit 1
fi

docker exec "${CONTAINER}" node -e \
  'fetch("http://127.0.0.1:4096/global/config").then(async (response) => { if (!response.ok) throw new Error(`${response.status} ${await response.text()}`) }).catch((error) => { console.error(error); process.exit(1) })'
# 断网条件下同时加载公共区和项目区 Tool，并确认四个自定义 Tool 基线包都来自随 programs 交付的链接。
docker exec "${CONTAINER}" node -e \
  'fetch("http://127.0.0.1:4096/experimental/tool/ids?directory=%2Ftmp%2Fworkspace").then(async (response) => { if (!response.ok) throw new Error(`${response.status} ${await response.text()}`); const ids = await response.json(); for (const expected of ["public-probe", "workspace-probe"]) { if (!ids.includes(expected)) throw new Error(`missing custom Tool: ${expected}; ids=${JSON.stringify(ids)}`) } }).catch((error) => { console.error(error); process.exit(1) })'
docker exec "${CONTAINER}" sh -lc \
  'for dir in /tmp/opencode-config /tmp/workspace/.opencode; do test -L "$dir/node_modules/@opencode-ai/plugin"; test -L "$dir/node_modules/@opencode-ai/sdk"; test -L "$dir/node_modules/effect"; test -L "$dir/node_modules/zod"; test ! -e "$dir/package.json"; done'
docker exec "${CONTAINER}" sh -lc \
  'test "$(readlink -f /usr/local/bin/opencode)" = /usr/local/lib/opencode-node/bin/opencode && ! command -v bun >/dev/null'

# launcher 必须在 Docker 的停止宽限期内自行退出，不能依赖 SIGKILL 清理。
docker stop --time 5 "${CONTAINER}" >/dev/null
exit_code="$(docker inspect --format '{{.State.ExitCode}}' "${CONTAINER}")"
if [[ "${exit_code}" != "0" ]]; then
  docker logs "${CONTAINER}" >&2 || true
  echo "OpenCode Node server did not stop gracefully: exit=${exit_code}" >&2
  exit 1
fi

echo "OpenCode Node worker image verified: image=${IMAGE} version=${version} ${glibc_version}"
