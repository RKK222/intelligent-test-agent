#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KIT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
VERSION_FILE="${KIT_ROOT}/version.env"
SOURCE_CHECKSUM_FILE="${KIT_ROOT}/SOURCE_SHA256SUMS"
SOURCE_ARCHIVE=""
OUTPUT_DIR="${PWD}/lobehub-linux-client-output"
FORCE=0
BUILD_DIR=""

usage() {
  cat <<'USAGE'
Usage: scripts/build-lobehub-linux-client.sh [options]

Build the locked LobeHub Desktop tar.gz on a native Linux x86_64 host. The command produces a
candidate client and Pending build evidence; it never creates final approval evidence.

Options:
  --source-archive <path>   Locked source archive from this build kit.
  --version-file <path>     Version lock (default: ../version.env).
  --source-checksums <path> Source checksum manifest (default: ../SOURCE_SHA256SUMS).
  --output-dir <path>       Candidate output directory.
  --force                   Replace only the two known candidate output files.
  -h, --help                Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source-archive) SOURCE_ARCHIVE="$2"; shift 2 ;;
    --version-file) VERSION_FILE="$2"; shift 2 ;;
    --source-checksums) SOURCE_CHECKSUM_FILE="$2"; shift 2 ;;
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    --force) FORCE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 1; }
}

require_regular_file() {
  [[ -f "$1" && ! -L "$1" && -s "$1" ]] || {
    echo "Required non-empty regular file not found: $1" >&2
    exit 1
  }
}

state_value() {
  local file="$1" key="$2"
  awk -F= -v wanted="${key}" '
    $1 == wanted { value=substr($0, index($0, "=") + 1); count++ }
    END { if (count != 1 || value == "") exit 1; print value }
  ' "${file}"
}

sha256_file() {
  sha256sum "$1" | awk '{print $1}'
}

cleanup() {
  [[ -n "${BUILD_DIR}" && -d "${BUILD_DIR}" ]] && rm -rf "${BUILD_DIR}"
}
trap cleanup EXIT

[[ "$(uname -s)" == Linux ]] || { echo "Linux client must be built on Linux" >&2; exit 1; }
case "$(uname -m)" in
  x86_64|amd64) ;;
  *) echo "Linux client must be built natively on x86_64, got $(uname -m)" >&2; exit 1 ;;
esac

for command_name in awk bun corepack find node openssl sha256sum tar; do
  require_command "${command_name}"
done
require_regular_file "${VERSION_FILE}"
require_regular_file "${SOURCE_CHECKSUM_FILE}"

INTERNAL_VERSION="$(state_value "${VERSION_FILE}" LOBEHUB_INTERNAL_VERSION)"
FORK_COMMIT="$(state_value "${VERSION_FILE}" LOBEHUB_FORK_COMMIT)"
[[ "${INTERNAL_VERSION}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+-platform\.[0-9]+$ ]] || {
  echo "Invalid internal version in ${VERSION_FILE}" >&2
  exit 1
}
[[ "${FORK_COMMIT}" =~ ^[0-9a-f]{40}$ ]] || {
  echo "Invalid fork commit in ${VERSION_FILE}" >&2
  exit 1
}

SOURCE_RELATIVE_PATH="source/lobehub-${INTERNAL_VERSION}.tar.gz"
SOURCE_ARCHIVE="${SOURCE_ARCHIVE:-${KIT_ROOT}/${SOURCE_RELATIVE_PATH}}"
require_regular_file "${SOURCE_ARCHIVE}"
EXPECTED_SOURCE_SHA="$(awk -v wanted="${SOURCE_RELATIVE_PATH}" \
  '$2 == wanted || $2 == "*" wanted { print $1; count++ } END { if (count != 1) exit 1 }' \
  "${SOURCE_CHECKSUM_FILE}")" || {
  echo "Source checksum manifest does not contain exactly ${SOURCE_RELATIVE_PATH}" >&2
  exit 1
}
[[ "${EXPECTED_SOURCE_SHA}" =~ ^[0-9a-f]{64}$ \
  && "$(sha256_file "${SOURCE_ARCHIVE}")" == "${EXPECTED_SOURCE_SHA}" ]] || {
  echo "Locked LobeHub source archive checksum mismatch" >&2
  exit 1
}

[[ "$(node --version)" == v24.11.1 ]] || {
  echo "Node.js v24.11.1 is required, got $(node --version)" >&2
  exit 1
}
[[ "$(bun --version)" == 1.3.2 ]] || {
  echo "Bun 1.3.2 is required, got $(bun --version)" >&2
  exit 1
}

mkdir -p "${OUTPUT_DIR}"
[[ -d "${OUTPUT_DIR}" && ! -L "${OUTPUT_DIR}" ]] || {
  echo "Output must be a real directory: ${OUTPUT_DIR}" >&2
  exit 1
}
OUTPUT_DIR="$(cd "${OUTPUT_DIR}" && pwd)"
CLIENT_OUTPUT="${OUTPUT_DIR}/lobehub-linux-x86_64.tar.gz"
BUILD_EVIDENCE_OUTPUT="${OUTPUT_DIR}/linux-client-build-evidence.txt"
if [[ "${FORCE}" -ne 1 && ( -e "${CLIENT_OUTPUT}" || -e "${BUILD_EVIDENCE_OUTPUT}" ) ]]; then
  echo "Candidate output already exists; use --force for this exact output directory" >&2
  exit 1
fi

# Git archive 路径先做越界检查，随后只解压到 mktemp 生成的目录。
if ! tar -tzf "${SOURCE_ARCHIVE}" | awk '
  /^\// { exit 1 }
  /(^|\/)\.\.($|\/)/ { exit 1 }
  END { if (NR == 0) exit 1 }
'; then
  echo "Locked source archive contains an unsafe path" >&2
  exit 1
fi
BUILD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/lobehub-linux-build.XXXXXX")"
tar -xzf "${SOURCE_ARCHIVE}" -C "${BUILD_DIR}"
SOURCE_ROOT="${BUILD_DIR}/lobehub-${INTERNAL_VERSION}"
require_regular_file "${SOURCE_ROOT}/package.json"
require_regular_file "${SOURCE_ROOT}/pnpm-lock.yaml"
[[ "$(node -p 'require(process.argv[1]).version' "${SOURCE_ROOT}/package.json")" == "${INTERNAL_VERSION#v}" ]] || {
  echo "Source package version does not match ${INTERNAL_VERSION}" >&2
  exit 1
}

(
  cd "${SOURCE_ROOT}"
  corepack pnpm@10.33.0 install --frozen-lockfile --node-linker=hoisted

  # 先验证 fork 的不可绕过策略，再在临时源码树内写入 Desktop 发布版本。
  corepack pnpm@10.33.0 --dir apps/desktop exec vitest run \
    src/main/const/enterpriseClientPolicy.test.ts \
    src/main/controllers/__tests__/RemoteServerConfigCtr.test.ts \
    src/main/controllers/__tests__/AuthCtr.test.ts
  corepack pnpm@10.33.0 --dir apps/cli exec vitest run \
    src/enterpriseClientPolicy.test.ts src/program.test.ts \
    src/commands/login.test.ts src/commands/logout.test.ts src/settings/index.test.ts
  corepack pnpm@10.33.0 run workflow:set-desktop-version "${INTERNAL_VERSION#v}" stable

  export APP_URL='http://chat.internal'
  export DATABASE_URL='postgresql://lobehub-build@127.0.0.1:5432/lobehub'
  export KEY_VAULTS_SECRET
  KEY_VAULTS_SECRET="$(openssl rand -base64 32 | tr -d '\n')"
  export LOBEHUB_ENTERPRISE_OFFLINE=1
  export NEXT_TELEMETRY_DISABLED=1
  export TELEMETRY_DISABLED=1
  export UPDATE_CHANNEL=stable
  export UPDATE_SERVER_URL='http://chat.internal/disabled-updates'

  corepack pnpm@10.33.0 --dir apps/desktop run build:main
  corepack pnpm@10.33.0 --dir apps/desktop exec electron-builder \
    --linux tar.gz --x64 --config electron-builder.mjs --publish never
)

shopt -s nullglob
RELEASE_FILES=("${SOURCE_ROOT}"/apps/desktop/release/*.tar.gz)
shopt -u nullglob
[[ "${#RELEASE_FILES[@]}" -eq 1 ]] || {
  echo "Expected exactly one Linux tar.gz, found ${#RELEASE_FILES[@]}" >&2
  exit 1
}
rm -f "${CLIENT_OUTPUT}" "${BUILD_EVIDENCE_OUTPUT}"
install -m 0644 "${RELEASE_FILES[0]}" "${CLIENT_OUTPUT}"

OS_NAME="$(. /etc/os-release && printf '%s' "${PRETTY_NAME:-${ID:-Linux}}")"
OS_NAME="$(printf '%s' "${OS_NAME}" | tr '\r\n' '  ')"
cat >"${BUILD_EVIDENCE_OUTPUT}" <<EOF
LINUX_BUILD_STATUS=Candidate
LINUX_CLIENT_FILE_SHA256=$(sha256_file "${CLIENT_OUTPUT}")
LOBEHUB_INTERNAL_VERSION=${INTERNAL_VERSION}
LOBEHUB_FORK_COMMIT=${FORK_COMMIT}
CLIENT_ARCHITECTURE=x86_64
CLIENT_EXECUTION_MODE=disabled
BUILD_OS=${OS_NAME}
BUILD_KERNEL=$(uname -r)
BUILD_NODE_VERSION=v24.11.1
BUILD_BUN_VERSION=1.3.2
SOURCE_ARCHIVE_SHA256=${EXPECTED_SOURCE_SHA}
EOF
chmod 0644 "${BUILD_EVIDENCE_OUTPUT}"

echo "Linux client candidate built at ${CLIENT_OUTPUT}"
echo "Pending evidence: ${BUILD_EVIDENCE_OUTPUT}"
echo "A separate reviewer must run approve-lobehub-linux-client.sh; this build is not release eligible yet."
