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
Usage: deploy/internal/build-local-opencode-client-deb.sh \
  --launcher <install.sh> --version <yyyyMMddHHmmss> --output <package.deb>

Build the Kylin ARM64 DEB wrapper around an already generated, signed-runtime
launcher. This does not create a Kylin/UKey system-package signature.
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

[[ -f "${LAUNCHER}" ]] || { echo "Local client launcher not found: ${LAUNCHER}" >&2; exit 1; }
[[ "${VERSION}" =~ ^[0-9]{14}$ ]] || {
  echo "Local client DEB version must be a 14-digit Beijing timestamp" >&2
  exit 2
}
[[ -f "${ICON}" ]] || { echo "Local client desktop icon not found: ${ICON}" >&2; exit 1; }
[[ -n "${OUTPUT}" && "${OUTPUT}" != / && "${OUTPUT}" == *.deb ]] || {
  echo "Local client DEB output must be an explicit .deb path" >&2
  exit 2
}
command -v ar >/dev/null 2>&1 || { echo "Required command not found: ar" >&2; exit 1; }
command -v tar >/dev/null 2>&1 || { echo "Required command not found: tar" >&2; exit 1; }

create_root_owned_tar_gz() {
  local output="$1" directory="$2" value
  local -a metadata_flags=()
  while IFS= read -r value; do
    [[ -z "${value}" ]] || metadata_flags+=("${value}")
  done < <(archive_tar_metadata_flags)
  if tar --version 2>/dev/null | grep -qi bsdtar; then
    COPYFILE_DISABLE=1 COPY_EXTENDED_ATTRIBUTES_DISABLE=1 \
      tar "${metadata_flags[@]}" --uid 0 --gid 0 --uname root --gname root \
        -C "${directory}" -czf "${output}" .
  else
    tar --owner=0 --group=0 --numeric-owner -C "${directory}" -czf "${output}" .
  fi
  archive_strip_file_metadata "${output}"
}

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-local-client-deb.XXXXXX")"
cleanup() {
  case "${TEMP_DIR}" in
    */test-agent-local-client-deb.*) rm -rf "${TEMP_DIR}" ;;
    *) echo "Refusing unsafe temporary directory cleanup: ${TEMP_DIR}" >&2 ;;
  esac
}
trap cleanup EXIT

mkdir -p "${TEMP_DIR}/control" "${TEMP_DIR}/data/usr/bin" \
  "${TEMP_DIR}/data/usr/share/applications" \
  "${TEMP_DIR}/data/usr/share/icons/hicolor/512x512/apps"
install -m 0755 "${LAUNCHER}" "${TEMP_DIR}/data/usr/bin/test-agent-local-client"
install -m 0644 "${ICON}" \
  "${TEMP_DIR}/data/usr/share/icons/hicolor/512x512/apps/test-agent-local-client.png"
{
  printf '%s\n' '[Desktop Entry]' 'Type=Application' 'Name=TestAgent 本地客户端' \
    'Comment=初始化并接入 TestAgent 本地工作区' \
    'Exec=/usr/bin/test-agent-local-client setup' \
    'Icon=test-agent-local-client' 'Terminal=true' 'StartupNotify=true' \
    'Categories=Development;Utility;'
} >"${TEMP_DIR}/data/usr/share/applications/test-agent-local-client.desktop"
chmod 0644 "${TEMP_DIR}/data/usr/share/applications/test-agent-local-client.desktop"
{
  printf 'Package: test-agent-local-client\n'
  printf 'Version: %s\n' "${VERSION}"
  printf 'Section: utils\nPriority: optional\nArchitecture: arm64\n'
  printf 'Maintainer: Test Agent Platform\n'
  printf 'Depends: curl, openssl, tar\n'
  printf 'Description: TestAgent Kylin ARM64 local OpenCode client installer\n'
} >"${TEMP_DIR}/control/control"

create_root_owned_tar_gz "${TEMP_DIR}/control.tar.gz" "${TEMP_DIR}/control"
create_root_owned_tar_gz "${TEMP_DIR}/data.tar.gz" "${TEMP_DIR}/data"
printf '2.0\n' >"${TEMP_DIR}/debian-binary"
mkdir -p "$(dirname "${OUTPUT}")"
rm -f "${OUTPUT}"
# DEB 是普通 ar 容器，不需要符号索引；Apple ar 若尝试 ranlib 会丢弃非 Mach-O 成员。
(cd "${TEMP_DIR}" && ar -rcS "${OUTPUT}" debian-binary control.tar.gz data.tar.gz)
archive_strip_file_metadata "${OUTPUT}"
printf 'Kylin ARM64 DEB: %s\n' "${OUTPUT}"
