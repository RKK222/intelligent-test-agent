#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
VERSION_FILE="${SCRIPT_DIR}/lobehub/version.env"
FORK_DIR="${TEST_AGENT_LOBEHUB_FORK_DIR:-${ROOT_DIR}/../lobehub-platform}"
OUTPUT_DIR="${SCRIPT_DIR}/dist-client-kit"
FORCE=0
STAGING_PARENT=""

usage() {
  cat <<'USAGE'
Usage: deploy/internal/build-lobehub-client-kit.sh [options]

Export the locked, clean LobeHub fork plus native Windows/Linux client build and approval tools.
The kit does not contain node_modules, signing keys, signed clients or pre-approved evidence.

Options:
  --fork-dir <path>   Independent LobeHub fork checkout.
  --output-dir <path> Destination for the build-kit ZIP and SHA-256 file.
  --force             Replace only the exact versioned ZIP and checksum.
  -h, --help          Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --fork-dir) FORK_DIR="$2"; shift 2 ;;
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    --force) FORCE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 1; }
}

require_file() {
  [[ -f "$1" && ! -L "$1" && -s "$1" ]] || {
    echo "Required non-empty regular file not found: $1" >&2
    exit 1
  }
}

state_value() {
  local file="$1" key="$2"
  awk -F= -v wanted="${key}" \
    '$1 == wanted { value=substr($0,index($0,"=")+1); count++ } END { if (count != 1 || value == "") exit 1; print value }' \
    "${file}"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

write_manifest() {
  local root="$1" output="$2" excluded_name="$3"
  (
    cd "${root}"
    find . -type f ! -name "${excluded_name}" -print | sed 's#^\./##' | LC_ALL=C sort |
      while IFS= read -r file; do
        printf '%s  %s\n' "$(sha256_file "${file}")" "${file}"
      done >"${output}"
  )
}

cleanup() {
  [[ -n "${STAGING_PARENT}" && -d "${STAGING_PARENT}" ]] && rm -rf "${STAGING_PARENT}"
}
trap cleanup EXIT

for command_name in awk find git node sed tar zip; do
  require_command "${command_name}"
done
require_command shasum
for required in \
  "${VERSION_FILE}" \
  "${SCRIPT_DIR}/clients/Build-LobeHubWindowsClient.ps1" \
  "${SCRIPT_DIR}/clients/build-lobehub-linux-client.sh" \
  "${SCRIPT_DIR}/clients/approve-lobehub-linux-client.sh" \
  "${SCRIPT_DIR}/clients/linux-client-acceptance-record.example" \
  "${SCRIPT_DIR}/lobehub-client-artifact-contract.sh" \
  "${ROOT_DIR}/docs/deployment/lobehub-client-build.md"; do
  require_file "${required}"
done

INTERNAL_VERSION="$(state_value "${VERSION_FILE}" LOBEHUB_INTERNAL_VERSION)"
UPSTREAM_COMMIT="$(state_value "${VERSION_FILE}" LOBEHUB_UPSTREAM_COMMIT)"
FORK_COMMIT="$(state_value "${VERSION_FILE}" LOBEHUB_FORK_COMMIT)"
[[ "${INTERNAL_VERSION}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+-platform\.[0-9]+$ \
  && "${FORK_COMMIT}" =~ ^[0-9a-f]{40}$ ]] || {
  echo "Malformed LobeHub version lock" >&2
  exit 1
}
[[ -d "${FORK_DIR}/.git" ]] || { echo "Independent fork not found: ${FORK_DIR}" >&2; exit 1; }
[[ "$(git -C "${FORK_DIR}" rev-parse HEAD)" == "${FORK_COMMIT}" ]] || {
  echo "Fork HEAD does not match locked commit ${FORK_COMMIT}" >&2
  exit 1
}
git -C "${FORK_DIR}" cat-file -e "${UPSTREAM_COMMIT}^{commit}"
git -C "${FORK_DIR}" merge-base --is-ancestor "${UPSTREAM_COMMIT}" "${FORK_COMMIT}" || {
  echo "Locked fork commit does not descend from ${UPSTREAM_COMMIT}" >&2
  exit 1
}
[[ -z "$(git -C "${FORK_DIR}" status --porcelain)" ]] || {
  echo "Fork must be clean before creating a native client build kit" >&2
  exit 1
}
[[ "$(node -p 'require(process.argv[1]).version' "${FORK_DIR}/package.json")" == "${INTERNAL_VERSION#v}" ]] || {
  echo "Fork package version does not match ${INTERNAL_VERSION}" >&2
  exit 1
}

mkdir -p "${OUTPUT_DIR}"
[[ -d "${OUTPUT_DIR}" && ! -L "${OUTPUT_DIR}" ]] || {
  echo "Output must be a real directory: ${OUTPUT_DIR}" >&2
  exit 1
}
OUTPUT_DIR="$(cd "${OUTPUT_DIR}" && pwd)"
case "${OUTPUT_DIR}" in
  /|"${HOME}"|"${ROOT_DIR}"|"${FORK_DIR}")
    echo "Unsafe client build-kit output directory: ${OUTPUT_DIR}" >&2
    exit 1
    ;;
esac

KIT_NAME="lobehub-client-build-kit-${INTERNAL_VERSION}"
ZIP_OUTPUT="${OUTPUT_DIR}/${KIT_NAME}.zip"
CHECKSUM_OUTPUT="${ZIP_OUTPUT}.sha256"
if [[ "${FORCE}" -ne 1 && ( -e "${ZIP_OUTPUT}" || -e "${CHECKSUM_OUTPUT}" ) ]]; then
  echo "Client build kit already exists; use --force for this exact versioned ZIP" >&2
  exit 1
fi

STAGING_PARENT="$(mktemp -d "${OUTPUT_DIR}/.lobehub-client-kit.XXXXXX")"
KIT_ROOT="${STAGING_PARENT}/${KIT_NAME}"
mkdir -p "${KIT_ROOT}/source" "${KIT_ROOT}/scripts"
SOURCE_ARCHIVE="${KIT_ROOT}/source/lobehub-${INTERNAL_VERSION}.tar.gz"

# 仅从锁定 commit 导出源码；工作树、.git、node_modules 和构建缓存不会进入工具包。
git -C "${FORK_DIR}" archive --format=tar.gz \
  --prefix="lobehub-${INTERNAL_VERSION}/" \
  --output="${SOURCE_ARCHIVE}" "${FORK_COMMIT}"
install -m 0644 "${VERSION_FILE}" "${KIT_ROOT}/version.env"
install -m 0644 "${ROOT_DIR}/docs/deployment/lobehub-client-build.md" "${KIT_ROOT}/BUILDING.md"
install -m 0644 "${SCRIPT_DIR}/clients/Build-LobeHubWindowsClient.ps1" \
  "${KIT_ROOT}/scripts/Build-LobeHubWindowsClient.ps1"
install -m 0755 "${SCRIPT_DIR}/clients/build-lobehub-linux-client.sh" \
  "${KIT_ROOT}/scripts/build-lobehub-linux-client.sh"
install -m 0755 "${SCRIPT_DIR}/clients/approve-lobehub-linux-client.sh" \
  "${KIT_ROOT}/scripts/approve-lobehub-linux-client.sh"
install -m 0644 "${SCRIPT_DIR}/clients/linux-client-acceptance-record.example" \
  "${KIT_ROOT}/linux-client-acceptance-record.example"
install -m 0644 "${SCRIPT_DIR}/lobehub-client-artifact-contract.sh" \
  "${KIT_ROOT}/scripts/lobehub-client-artifact-contract.sh"

printf '%s  %s\n' "$(sha256_file "${SOURCE_ARCHIVE}")" \
  "source/lobehub-${INTERNAL_VERSION}.tar.gz" >"${KIT_ROOT}/SOURCE_SHA256SUMS"
write_manifest "${KIT_ROOT}" "${KIT_ROOT}/BUILD_KIT_SHA256SUMS" BUILD_KIT_SHA256SUMS

# 归一化 ZIP 元数据并固定文件顺序，使同一 commit 与脚本内容能产出稳定的工具包字节。
find "${KIT_ROOT}" -type f -exec touch -t 198001010000 {} +
TEMP_ZIP="${STAGING_PARENT}/${KIT_NAME}.zip"
(
  cd "${STAGING_PARENT}"
  find "${KIT_NAME}" -type f -print | LC_ALL=C sort | zip -X -q "${TEMP_ZIP}" -@
)
TEMP_CHECKSUM="${STAGING_PARENT}/${KIT_NAME}.zip.sha256"
printf '%s  %s\n' "$(sha256_file "${TEMP_ZIP}")" "${KIT_NAME}.zip" >"${TEMP_CHECKSUM}"

mv -f "${TEMP_ZIP}" "${ZIP_OUTPUT}"
mv -f "${TEMP_CHECKSUM}" "${CHECKSUM_OUTPUT}"
echo "LobeHub native client build kit created: ${ZIP_OUTPUT}"
echo "Checksum: ${CHECKSUM_OUTPUT}"
