#!/usr/bin/env bash
set -euo pipefail

IMAGE="${1:-test-agent-opencode-worker:internal}"
CONTAINER="test-agent-opencode-official-smoke-$$"
EXPECTED_OPENCODE_VERSION="${EXPECTED_OPENCODE_VERSION:-2.0.18}"
EXPECTED_OPENCODE_ASSET_NAME="${EXPECTED_OPENCODE_ASSET_NAME:-cli-linux-x64-baseline-2.0.18.tgz}"
EXPECTED_OPENCODE_ASSET_SHA256="${EXPECTED_OPENCODE_ASSET_SHA256:-548b709efa8229f97c35f7cc6ba635425c407c5b3382a7435e92a80ce006cfcd}"
EXPECTED_OPENCODE_SUBAGENT_DEPTH="${EXPECTED_OPENCODE_SUBAGENT_DEPTH:-2}"
EXPECTED_OPENCODE_ABI="${EXPECTED_OPENCODE_ABI:-auto}"

if [[ "${EXPECTED_OPENCODE_ABI}" == "auto" ]]; then
  case "${EXPECTED_OPENCODE_VERSION%%.*}" in
    2|3|4|5|6|7|8|9) EXPECTED_OPENCODE_ABI=V2 ;;
    *) EXPECTED_OPENCODE_ABI=V1 ;;
  esac
fi
case "${EXPECTED_OPENCODE_ABI}" in
  V2)
    EXPECTED_HEALTH_PATH=/api/info
    EXPECTED_DEPENDENCIES=("@opencode/plugin" "@opencode/client" "effect" "zod")
    PUBLIC_TOOL_SOURCE='import { z } from "zod"; export default { description: "public offline probe", input: z.object({ value: z.string().optional() }), async execute(input) { return { output: input.value ?? "public-ok" } } }'
    WORKSPACE_TOOL_SOURCE='import * as sdk from "@opencode/client"; import * as Effect from "effect"; import { z } from "zod"; const loaded = Boolean(sdk && Effect && z); export default { description: "workspace offline probe", input: z.object({ value: z.string().optional() }), async execute(input) { return { output: loaded ? (input.value ?? "workspace-ok") : "missing" } } }'
    ;;
  V1)
    EXPECTED_HEALTH_PATH=/global/health
    EXPECTED_DEPENDENCIES=("@opencode-ai/plugin" "@opencode-ai/sdk" "effect" "zod")
    PUBLIC_TOOL_SOURCE='import { tool } from "@opencode-ai/plugin"; export default tool({ description: "public offline probe", args: { value: tool.schema.string().optional() }, async execute(args) { return args.value ?? "public-ok" } })'
    WORKSPACE_TOOL_SOURCE='import { tool } from "@opencode-ai/plugin"; import * as sdk from "@opencode-ai/sdk"; import * as Effect from "effect"; import { z } from "zod"; const loaded = Boolean(sdk && Effect && z); export default tool({ description: "workspace offline probe", args: { value: z.string().optional() }, async execute(args) { return loaded ? (args.value ?? "workspace-ok") : "missing" } })'
    ;;
  *)
    echo "Unsupported EXPECTED_OPENCODE_ABI: ${EXPECTED_OPENCODE_ABI}" >&2
    exit 2
    ;;
esac

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
if [[ "${EXPECTED_OPENCODE_ABI}" == V2 && "${version}" != "opencode v${EXPECTED_OPENCODE_VERSION}" ]] \
  || [[ "${EXPECTED_OPENCODE_ABI}" == V1 && "${version#v}" != "${EXPECTED_OPENCODE_VERSION}" \
    && "${version#opencode }" != "${EXPECTED_OPENCODE_VERSION}" \
    && "${version#opencode v}" != "${EXPECTED_OPENCODE_VERSION}" ]]; then
  echo "Unexpected opencode version: ${version}" >&2
  exit 1
fi

# 用最小公共配置和按统一认证号隔离的用户运行目录拉起官方 baseline server；
# 不发布宿主机端口，所有探测都在容器内完成。
docker run -d \
  --platform linux/amd64 \
  --name "${CONTAINER}" \
  --network none \
  --env TEST_AGENT_OPENCODE_SERVER_PASSWORD=smoke-password \
  --env "PUBLIC_TOOL_SOURCE=${PUBLIC_TOOL_SOURCE}" \
  --env "WORKSPACE_TOOL_SOURCE=${WORKSPACE_TOOL_SOURCE}" \
  --entrypoint bash \
  "${IMAGE}" \
  -lc 'user_root=/tmp/opencode-users/DEV_888888888 && config_link="$user_root/.testagent-runtime/current-public-config" && mkdir -p /tmp/opencode-config/tools "$user_root/.cache" "$user_root/.local/state" "$user_root/.tmp" "$user_root/.testagent-runtime" /tmp/workspace/.opencode/tools && chmod 0755 "$user_root" "$user_root/.cache" "$user_root/.local" "$user_root/.local/state" "$user_root/.tmp" && printf "%s\n" "{\"\$schema\":\"https://opencode.ai/config.json\"}" > /tmp/opencode-config/opencode.json && printf "%s\n" "$PUBLIC_TOOL_SOURCE" > /tmp/opencode-config/tools/public-probe.ts && printf "%s\n" "$WORKSPACE_TOOL_SOURCE" > /tmp/workspace/.opencode/tools/workspace-probe.ts && ln -s /tmp/opencode-config "$config_link" && cd /tmp/workspace && HOME="$user_root" XDG_DATA_HOME="$user_root" XDG_CACHE_HOME="$user_root/.cache" XDG_STATE_HOME="$user_root/.local/state" TMPDIR="$user_root/.tmp" OPENCODE_CONFIG_DIR="$config_link" exec /usr/local/bin/opencode serve --hostname 127.0.0.1 --port 4096 --print-logs' \
  >/dev/null

healthy=0
for _ in {1..60}; do
  if docker exec --env "EXPECTED_HEALTH_PATH=${EXPECTED_HEALTH_PATH}" "${CONTAINER}" node -e \
    'fetch("http://127.0.0.1:4096" + process.env.EXPECTED_HEALTH_PATH, { headers: { authorization: "Basic " + Buffer.from("opencode:smoke-password").toString("base64") }, signal: AbortSignal.timeout(1000) }).then((response) => { if (!response.ok) throw new Error(String(response.status)); process.exit(0) }).catch(() => process.exit(1))' \
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

# 直接读取 PID 1 环境，确认最终 OpenCode 进程继承了隔离参数，而不是只检查启动命令文本。
docker exec "${CONTAINER}" node -e '
  const fs = require("node:fs");
  const values = Object.fromEntries(fs.readFileSync("/proc/1/environ", "utf8").split("\0").filter(Boolean).map((entry) => {
    const index = entry.indexOf("=");
    return [entry.slice(0, index), entry.slice(index + 1)];
  }));
  const root = "/tmp/opencode-users/DEV_888888888";
  const expected = {
    HOME: root,
    XDG_DATA_HOME: root,
    XDG_CACHE_HOME: `${root}/.cache`,
    XDG_STATE_HOME: `${root}/.local/state`,
    TMPDIR: `${root}/.tmp`,
    OPENCODE_CONFIG_DIR: `${root}/.testagent-runtime/current-public-config`,
  };
  for (const [key, value] of Object.entries(expected)) {
    if (values[key] !== value) throw new Error(`unexpected ${key}: ${JSON.stringify(values[key])}`);
  }
'

# OpenCode 会在模块加载时读取 HOME/XDG/TMP 并创建各自的 opencode 子目录；新增隔离目录必须保持为物理目录。
docker exec "${CONTAINER}" sh -lc '
  test -L /tmp/opencode-users/DEV_888888888/.testagent-runtime/current-public-config
  test "$(readlink -f /tmp/opencode-users/DEV_888888888/.testagent-runtime/current-public-config)" = /tmp/opencode-config
  for path in \
    /tmp/opencode-users/DEV_888888888 \
    /tmp/opencode-users/DEV_888888888/.cache \
    /tmp/opencode-users/DEV_888888888/.local/state \
    /tmp/opencode-users/DEV_888888888/.tmp \
    /tmp/opencode-users/DEV_888888888/opencode \
    /tmp/opencode-users/DEV_888888888/.cache/opencode \
    /tmp/opencode-users/DEV_888888888/.local/state/opencode \
    /tmp/opencode-users/DEV_888888888/.tmp/opencode; do
    test -d "$path"
    test ! -L "$path"
  done
'
if [[ "${EXPECTED_OPENCODE_ABI}" == V2 ]]; then
  docker exec "${CONTAINER}" node -e '
    const root = "/tmp/opencode-users/DEV_888888888";
    const headers = { authorization: "Basic " + Buffer.from("opencode:smoke-password").toString("base64") };
    Promise.all([fetch("http://127.0.0.1:4096/api/location?location%5Bdirectory%5D=%2Ftmp%2Fworkspace", { headers }), fetch("http://127.0.0.1:4096/api/info", { headers })]).then(async ([response, infoResponse]) => {
      if (!response.ok) throw new Error(`${response.status} ${await response.text()}`);
      if (!infoResponse.ok) throw new Error(`${infoResponse.status} ${await infoResponse.text()}`);
      const location = await response.json();
      const info = await infoResponse.json();
      if (location.directory !== "/tmp/workspace") throw new Error(`unexpected location: ${JSON.stringify(location)}`);
      if (info.paths?.tmp !== `${root}/.tmp/opencode`) throw new Error(`unexpected temp path: ${JSON.stringify(info.paths)}`);
    }).then(() => process.exit(0)).catch((error) => { console.error(error); process.exit(1) });
  '
else
  docker exec "${CONTAINER}" node -e '
    const root = "/tmp/opencode-users/DEV_888888888";
    const headers = { authorization: "Basic " + Buffer.from("opencode:smoke-password").toString("base64") };
    fetch("http://127.0.0.1:4096/path?directory=%2Ftmp%2Fworkspace", { headers }).then(async (response) => {
      if (!response.ok) throw new Error(`${response.status} ${await response.text()}`);
      const paths = await response.json();
      if (paths.home !== root) throw new Error(`unexpected home: ${JSON.stringify(paths)}`);
      if (paths.state !== `${root}/.local/state/opencode`) throw new Error(`unexpected state: ${JSON.stringify(paths)}`);
      if (paths.config !== `${root}/.config/opencode`) throw new Error(`unexpected config: ${JSON.stringify(paths)}`);
    }).then(() => process.exit(0)).catch((error) => { console.error(error); process.exit(1) });
  '
fi

if [[ "${EXPECTED_OPENCODE_ABI}" == V2 ]]; then
  docker exec --env "EXPECTED_DEPTH=${EXPECTED_OPENCODE_SUBAGENT_DEPTH}" "${CONTAINER}" node -e '
    fetch("http://127.0.0.1:4096/api/config?location%5Bdirectory%5D=%2Ftmp%2Fworkspace", { headers: { authorization: "Basic " + Buffer.from("opencode:smoke-password").toString("base64") } }).then(async (response) => {
      if (!response.ok) throw new Error(`${response.status} ${await response.text()}`);
      const entries = await response.json();
      if (!Array.isArray(entries)) throw new Error(`unexpected config envelope: ${JSON.stringify(entries)}`);
      const depths = entries.map((entry) => entry.info?.experimental?.subagent_depth).filter((value) => value !== undefined);
      const expected = process.env.EXPECTED_DEPTH;
      if (expected === "unsupported" ? depths.length !== 0 : !depths.includes(Number(expected))) {
        throw new Error(`unexpected subagent_depth: ${JSON.stringify(depths)}`);
      }
    }).then(() => process.exit(0)).catch((error) => { console.error(error); process.exit(1) });
  '
else
  docker exec --env "EXPECTED_DEPTH=${EXPECTED_OPENCODE_SUBAGENT_DEPTH}" "${CONTAINER}" node -e '
    fetch("http://127.0.0.1:4096/config?directory=%2Ftmp%2Fworkspace", { headers: { authorization: "Basic " + Buffer.from("opencode:smoke-password").toString("base64") } }).then(async (response) => {
      if (!response.ok) throw new Error(`${response.status} ${await response.text()}`);
      const config = await response.json();
      const expected = process.env.EXPECTED_DEPTH;
      if (expected === "unsupported" ? Object.hasOwn(config, "subagent_depth") : config.subagent_depth !== Number(expected)) {
        throw new Error(`unexpected subagent_depth: ${JSON.stringify(config.subagent_depth)}`);
      }
    }).then(() => process.exit(0)).catch((error) => { console.error(error); process.exit(1) });
  '
fi
# 断网条件下同时加载公共区和项目区 Tool，并确认对应 ABI 的基线包都来自随 programs 交付的链接。
docker exec "${CONTAINER}" sh -lc \
  "for dir in /tmp/opencode-config /tmp/workspace/.opencode; do for dependency in ${EXPECTED_DEPENDENCIES[*]}; do test -L \"\$dir/node_modules/\$dependency\"; done; test -L \"\$dir/package.json\"; test -L \"\$dir/package-lock.json\"; for rule in node_modules package.json package-lock.json bun.lock .gitignore; do grep -Fx \"\$rule\" \"\$dir/.gitignore\" >/dev/null; done; done"
docker exec "${CONTAINER}" sh -lc \
  "for dependency in ${EXPECTED_DEPENDENCIES[*]}; do test -L \"/tmp/workspace/node_modules/\$dependency\"; done"
if [[ "${EXPECTED_OPENCODE_ABI}" == V2 ]]; then
  docker exec "${CONTAINER}" node -e '
    const headers = { authorization: "Basic " + Buffer.from("opencode:smoke-password").toString("base64"), "content-type": "application/json" };
    fetch("http://127.0.0.1:4096/api/rpc/testagent.runtime/tools?location%5Bdirectory%5D=%2Ftmp%2Fworkspace", { method: "POST", headers, body: JSON.stringify({ input: {} }) }).then(async (response) => {
      if (!response.ok) throw new Error(`${response.status} ${await response.text()}`);
      const body = await response.json();
      if (!Array.isArray(body.output) || body.output.length === 0) throw new Error(`missing V2 registered tools: ${JSON.stringify(body)}`);
    }).then(() => process.exit(0)).catch((error) => { console.error(error); process.exit(1) });
  '
else
  docker exec "${CONTAINER}" node -e '
    const headers = { authorization: "Basic " + Buffer.from("opencode:smoke-password").toString("base64") };
    fetch("http://127.0.0.1:4096/experimental/tool/ids?directory=%2Ftmp%2Fworkspace", { headers }).then(async (response) => {
      if (!response.ok) throw new Error(`${response.status} ${await response.text()}`);
      const ids = await response.json();
      for (const expected of ["public-probe", "workspace-probe"]) if (!ids.includes(expected)) throw new Error(`missing V1 custom Tool: ${expected}; ids=${JSON.stringify(ids)}`);
    }).then(() => process.exit(0)).catch((error) => { console.error(error); process.exit(1) });
  '
fi
docker exec "${CONTAINER}" sh -lc \
  "test \"\$(readlink -f /usr/local/bin/opencode)\" = /usr/local/lib/opencode/bin/opencode && test -x /usr/local/lib/opencode/bin/opencode-official && grep -Fx 'asset=${EXPECTED_OPENCODE_ASSET_NAME}' /usr/local/lib/opencode/RELEASE && grep -Fx 'archive_sha256=${EXPECTED_OPENCODE_ASSET_SHA256}' /usr/local/lib/opencode/RELEASE && ! command -v bun >/dev/null"

# launcher 必须在 Docker 的停止宽限期内自行退出，不能依赖 SIGKILL 清理。
docker stop --time 5 "${CONTAINER}" >/dev/null
exit_code="$(docker inspect --format '{{.State.ExitCode}}' "${CONTAINER}")"
if [[ "${exit_code}" != "0" ]]; then
  docker logs "${CONTAINER}" >&2 || true
  echo "OpenCode Node server did not stop gracefully: exit=${exit_code}" >&2
  exit 1
fi

echo "OpenCode ${EXPECTED_OPENCODE_ABI} worker image verified: image=${IMAGE} version=${version} ${glibc_version}"
