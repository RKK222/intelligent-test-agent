#!/usr/bin/env bash
set -euo pipefail

# 企业目标机启用官方 Codex MCP 前的失败关闭检查；兼容现场 Docker 18.09 CLI，不使用 --platform。
IMAGE="${1:-test-agent-opencode-worker:internal}"
EXPECTED_BWRAP_SHA256="${EXPECTED_CODEX_BWRAP_BINARY_SHA256:-77360cb751ccedc5971391444ac86a8a33c15b04d6b4a6fe45f5d25496e62c4c}"

fail() {
  echo "Codex MCP host check failed: $*" >&2
  exit 1
}

[[ "$(uname -s)" == "Linux" ]] || fail "host OS must be Linux"
case "$(uname -m)" in
  x86_64|amd64) ;;
  *) fail "host architecture must be x86_64/amd64" ;;
esac

docker_server_version="$(docker version --format '{{.Server.Version}}' 2>/dev/null || true)"
[[ -n "${docker_server_version}" ]] || fail "Docker server is unavailable"
version_core="${docker_server_version%%-*}"
version_major="${version_core%%.*}"
version_rest="${version_core#*.}"
version_minor="${version_rest%%.*}"
[[ "${version_major}" =~ ^[0-9]+$ && "${version_minor}" =~ ^[0-9]+$ ]] \
  || fail "cannot parse Docker server version ${docker_server_version}"
# Docker 18.09 等版本含前导零；显式按十进制转换，避免 Bash 将 09 当作非法八进制。
version_major=$((10#${version_major}))
version_minor=$((10#${version_minor}))
if (( version_major < 18 || (version_major == 18 && version_minor < 9) )); then
  fail "Docker ${docker_server_version} is older than the supported 18.09 baseline"
fi

kernel_release="$(uname -r)"
kernel_core="${kernel_release%%-*}"
kernel_major="${kernel_core%%.*}"
kernel_rest="${kernel_core#*.}"
kernel_minor="${kernel_rest%%.*}"
[[ "${kernel_major}" =~ ^[0-9]+$ && "${kernel_minor}" =~ ^[0-9]+$ ]] \
  || fail "cannot parse Linux kernel version ${kernel_release}"
kernel_major=$((10#${kernel_major}))
kernel_minor=$((10#${kernel_minor}))
if (( kernel_major < 4 || (kernel_major == 4 && kernel_minor < 19) )); then
  fail "Linux kernel ${kernel_release} is older than the supported 4.19 baseline"
fi

image_platform="$(docker image inspect -f '{{.Os}}/{{.Architecture}}' "${IMAGE}" 2>/dev/null || true)"
[[ "${image_platform}" == "linux/amd64" ]] || fail "image must be linux/amd64, got ${image_platform:-missing}"

docker run --rm --privileged --network none --entrypoint sh "${IMAGE}" -lc "
  test \"\$(getconf GNU_LIBC_VERSION)\" = 'glibc 2.31'
  test \"\$(/usr/local/lib/codex/bin/codex-official --version)\" = 'codex-cli 0.145.0'
  printf '%s  %s\\n' '${EXPECTED_BWRAP_SHA256}' /usr/local/lib/codex/bin/codex-resources/bwrap | sha256sum -c -
  /usr/local/lib/codex/bin/codex-resources/bwrap --ro-bind / / --proc /proc --dev /dev /bin/true
"

# 版本号不能代替能力验证：这里真实执行官方工具发现、指定 cwd、读取、原生只读拒写、Git 不变和续写测试。
docker run --rm --privileged --network none --entrypoint node "${IMAGE}" \
  /usr/local/lib/codex/tests/probe-codex-whitebox-e2e.mjs

echo "Codex MCP host compatible: kernel=${kernel_release} docker=${docker_server_version} image=${IMAGE}"
