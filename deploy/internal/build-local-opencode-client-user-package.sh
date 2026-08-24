#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=archive-common.sh
source "${SCRIPT_DIR}/archive-common.sh"

LAUNCHER=""
VERSION=""
OUTPUT=""
ICON="${ROOT_DIR}/frontend/apps/agent-web/src/assets/pets/radar-bunny.png"

usage() {
  cat <<'USAGE'
Usage: deploy/internal/build-local-opencode-client-user-package.sh \
  --launcher <install.sh> --version <yyyyMMddHHmmss> --output <package.tar.gz>

Build a Kylin ARM64 user-level package. After extraction, an ordinary user can
double-click TestAgent-Local-Client; installation stays below the user's home
directory and never invokes dpkg or sudo.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --launcher) LAUNCHER="$2"; shift 2 ;;
    --version) VERSION="$2"; shift 2 ;;
    --output) OUTPUT="$2"; shift 2 ;;
    --icon) ICON="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -f "${LAUNCHER}" && ! -L "${LAUNCHER}" ]] || {
  echo "Local client launcher not found or is a symlink: ${LAUNCHER}" >&2
  exit 1
}
[[ "${VERSION}" =~ ^[0-9]{14}$ ]] || {
  echo "Local client user package version must be a 14-digit Beijing timestamp" >&2
  exit 2
}
[[ -f "${ICON}" && ! -L "${ICON}" ]] || {
  echo "Local client desktop icon not found or is a symlink: ${ICON}" >&2
  exit 1
}
[[ -n "${OUTPUT}" && "${OUTPUT}" != / && "${OUTPUT}" == *.tar.gz ]] || {
  echo "Local client user package output must be an explicit .tar.gz path" >&2
  exit 2
}
command -v go >/dev/null 2>&1 || { echo "Required command not found: go" >&2; exit 1; }
command -v tar >/dev/null 2>&1 || { echo "Required command not found: tar" >&2; exit 1; }

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-local-client-user-package.XXXXXX")"
cleanup() {
  case "${TEMP_DIR}" in
    */test-agent-local-client-user-package.*) rm -rf "${TEMP_DIR}" ;;
    *) echo "Refusing unsafe temporary directory cleanup: ${TEMP_DIR}" >&2 ;;
  esac
}
trap cleanup EXIT

PACKAGE_ROOT="${TEMP_DIR}/stage/TestAgent-Local-Client"
mkdir -p "${PACKAGE_ROOT}/resources" "$(dirname "${OUTPUT}")"
install -m 0755 "${LAUNCHER}" "${PACKAGE_ROOT}/resources/test-agent-local-client"
install -m 0644 "${ICON}" "${PACKAGE_ROOT}/resources/radar-bunny.png"
launcher_sha="$(sha256_file "${LAUNCHER}")"
icon_sha="$(sha256_file "${ICON}")"

# 静态 ARM64 启动器只负责打开终端和校验包内资源，运行时下载与签名验证继续复用既有脚本。
(
  cd "${ROOT_DIR}"
  GOOS=linux GOARCH=arm64 CGO_ENABLED=0 GO111MODULE=off \
    go build -trimpath \
      -ldflags "-s -w -X main.expectedLauncherSHA256=${launcher_sha} -X main.expectedIconSHA256=${icon_sha} -X main.releaseVersion=${VERSION}" \
      -o "${PACKAGE_ROOT}/TestAgent-Local-Client" \
      ./deploy/internal/local-opencode-client/bootstrap
)
chmod 0755 "${PACKAGE_ROOT}/TestAgent-Local-Client"
{
  printf '%s\n' \
    'TestAgent 本地客户端（麒麟 Linux ARM64 用户包）' \
    '' \
    '使用方法：' \
    '1. 完整解压本压缩包。' \
    '2. 双击 TestAgent-Local-Client。' \
    '3. 按提示输入统一认证号和 Client Key，等待安装完成。' \
    '' \
    '本客户端只写入当前用户的 ~/.local 与 ~/.config，不需要 sudo，也不会调用 dpkg。' \
    '如果文件管理器禁止双击可执行文件，请在本目录打开终端，运行：' \
    '  ./TestAgent-Local-Client' \
    '' \
    '运行依赖：系统需预装 curl、openssl、tar，并能访问平台内网下载地址。' \
    "发布版本：${VERSION}"
} >"${PACKAGE_ROOT}/README.txt"
chmod 0644 "${PACKAGE_ROOT}/README.txt"

rm -f "${OUTPUT}"
archive_create_tar_gz "${OUTPUT}" "${TEMP_DIR}/stage" TestAgent-Local-Client
printf 'Kylin ARM64 user package: %s\n' "${OUTPUT}"
