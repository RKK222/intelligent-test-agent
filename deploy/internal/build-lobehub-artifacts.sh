#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
VERSION_FILE="${SCRIPT_DIR}/lobehub/version.env"
CLIENT_CONTRACT_FILE="${SCRIPT_DIR}/lobehub-client-artifact-contract.sh"
COMMUNITY_SNAPSHOT_VERIFIER="${SCRIPT_DIR}/verify-lobehub-community-snapshot.mjs"

FORK_DIR="${TEST_AGENT_LOBEHUB_FORK_DIR:-${ROOT_DIR}/../lobehub-platform}"
OUTPUT_DIR="${TEST_AGENT_LOBEHUB_ARTIFACT_DIR:-${ROOT_DIR}/lobehub-release-artifacts}"
WINDOWS_CLIENT=""
WINDOWS_SIGNATURE_EVIDENCE=""
LINUX_CLIENT=""
LINUX_APPROVAL_EVIDENCE=""
LINUX_ACCEPTANCE_RECORD=""
LINUX_BUILD_EVIDENCE=""
NODE_BASE_IMAGE=""
BUSYBOX_BASE_IMAGE=""
PARADEDB_SOURCE_IMAGE=""
RUSTFS_SOURCE_IMAGE=""
MC_SOURCE_IMAGE=""
APP_IMAGE=""
PARADEDB_IMAGE=""
RUSTFS_IMAGE=""
VALIDATE_ONLY=0
ALLOW_DIRTY=0
SERVER_ONLY=0
SKIP_APP_BUILD=0
FORCE=0
FORK_COMMIT=""
BUILD_CONTEXT_DIR=""
COMMUNITY_METADATA=""

usage() {
  cat <<'USAGE'
Usage: deploy/internal/build-lobehub-artifacts.sh [options]

Build the real linux/amd64 LobeHub server artifact set from the independent fork. A complete
package also imports externally built Windows/Linux clients and validates Authenticode plus
Linux reviewer evidence bound to an independent acceptance record.

Required image inputs are approved digest references (repository@sha256:...). The output images
use immutable internal tags plus Docker image IDs because docker save/load does not restore
Registry RepoDigest mappings.

Options:
  --fork-dir <path>                    Independent LobeHub fork checkout.
  --output-dir <path>                  Artifact output directory.
  --windows-client <path>              Enterprise-signed Windows x64 EXE.
  --windows-signature-evidence <path>  KEY=value Authenticode verification evidence.
  --linux-client <path>                Approved Linux x86_64 client tar.gz.
  --linux-approval-evidence <path>     KEY=value Linux validation and approval evidence.
  --linux-acceptance-record <path>     Approved Linux validation checklist record.
  --linux-build-evidence <path>        Native Linux candidate build evidence.
  --node-base-image <digest-ref>       Approved Node build base.
  --busybox-base-image <digest-ref>    Approved BusyBox runtime base.
  --paradedb-source-image <digest-ref> Approved ParadeDB/PostgreSQL 17 source image.
  --rustfs-source-image <digest-ref>   Approved RustFS source image.
  --mc-source-image <digest-ref>       Approved minio/mc source image.
  --app-image <tag>                    Output app tag (default from version lock).
  --paradedb-image <tag>               Output ParadeDB tag (default from version lock).
  --rustfs-image <tag>                 Output RustFS tag (default from version lock).
  --skip-app-build                     Use an already-built local app tag after verifying it.
  --server-only                        Build a real server staging set without clients. This set
                                       is intentionally rejected by package-release.sh.
  --validate-only                      Validate source, versions, image refs and client evidence.
  --allow-dirty                        Allow a dirty fork only with --validate-only.
  --force                              Replace the explicit output directory.
  -h, --help                           Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --fork-dir) FORK_DIR="$2"; shift 2 ;;
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    --windows-client) WINDOWS_CLIENT="$2"; shift 2 ;;
    --windows-signature-evidence) WINDOWS_SIGNATURE_EVIDENCE="$2"; shift 2 ;;
    --linux-client) LINUX_CLIENT="$2"; shift 2 ;;
    --linux-approval-evidence) LINUX_APPROVAL_EVIDENCE="$2"; shift 2 ;;
    --linux-acceptance-record) LINUX_ACCEPTANCE_RECORD="$2"; shift 2 ;;
    --linux-build-evidence) LINUX_BUILD_EVIDENCE="$2"; shift 2 ;;
    --node-base-image) NODE_BASE_IMAGE="$2"; shift 2 ;;
    --busybox-base-image) BUSYBOX_BASE_IMAGE="$2"; shift 2 ;;
    --paradedb-source-image) PARADEDB_SOURCE_IMAGE="$2"; shift 2 ;;
    --rustfs-source-image) RUSTFS_SOURCE_IMAGE="$2"; shift 2 ;;
    --mc-source-image) MC_SOURCE_IMAGE="$2"; shift 2 ;;
    --app-image) APP_IMAGE="$2"; shift 2 ;;
    --paradedb-image) PARADEDB_IMAGE="$2"; shift 2 ;;
    --rustfs-image) RUSTFS_IMAGE="$2"; shift 2 ;;
    --skip-app-build) SKIP_APP_BUILD=1; shift ;;
    --server-only) SERVER_ONLY=1; shift ;;
    --validate-only) VALIDATE_ONLY=1; shift ;;
    --allow-dirty) ALLOW_DIRTY=1; shift ;;
    --force) FORCE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

state_value() {
  local file="$1" key="$2"
  awk -F= -v wanted="${key}" \
    '$1 == wanted { print substr($0, index($0, "=") + 1); found=1 } END { if (!found) exit 1 }' \
    "${file}"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 1; }
}

require_file() {
  [[ -f "$1" && -s "$1" ]] || { echo "Required non-empty file not found: $1" >&2; exit 1; }
}

require_digest_ref() {
  local key="$1" value="$2"
  [[ "${value}" =~ ^[A-Za-z0-9._:/-]+@sha256:[0-9a-f]{64}$ ]] || {
    echo "${key} must be pinned by a lowercase sha256 digest" >&2
    exit 1
  }
}

require_immutable_tag() {
  local key="$1" value="$2" leaf tag
  leaf="${value##*/}"
  tag="${leaf#*:}"
  [[ "${value}" =~ ^[A-Za-z0-9._:/-]+$ && "${value}" != *@* \
    && "${leaf}" == *:* && -n "${tag}" && "${tag}" != latest ]] || {
    echo "${key} must use an immutable non-latest tag" >&2
    exit 1
  }
}

validate_clients() {
  [[ "${SERVER_ONLY}" -eq 1 ]] && return 0
  lobehub_verify_client_artifacts \
    "${WINDOWS_CLIENT}" "${WINDOWS_SIGNATURE_EVIDENCE}" \
    "${LINUX_CLIENT}" "${LINUX_APPROVAL_EVIDENCE}" \
    "${LINUX_ACCEPTANCE_RECORD}" "${LINUX_BUILD_EVIDENCE}" \
    "${INTERNAL_VERSION}" "${LOCKED_FORK_COMMIT}"
}

validate_fork() {
  local package_version dirty
  [[ -d "${FORK_DIR}/.git" ]] || { echo "Independent fork not found: ${FORK_DIR}" >&2; exit 1; }
  require_file "${FORK_DIR}/package.json"
  require_file "${FORK_DIR}/pnpm-lock.yaml"
  require_file "${FORK_DIR}/Dockerfile"
  package_version="$(node -p "require(process.argv[1]).version" "${FORK_DIR}/package.json")"
  [[ "v${package_version}" == "${INTERNAL_VERSION}" ]] || {
    echo "Fork version must be ${INTERNAL_VERSION}, got v${package_version}" >&2
    exit 1
  }
  git -C "${FORK_DIR}" cat-file -e "${UPSTREAM_COMMIT}^{commit}"
  git -C "${FORK_DIR}" merge-base --is-ancestor "${UPSTREAM_COMMIT}" HEAD || {
    echo "Fork HEAD does not descend from ${UPSTREAM_COMMIT}" >&2
    exit 1
  }
  FORK_COMMIT="$(git -C "${FORK_DIR}" rev-parse HEAD)"
  [[ "${FORK_COMMIT}" == "${LOCKED_FORK_COMMIT}" ]] || {
    echo "Fork HEAD must be the locked release commit ${LOCKED_FORK_COMMIT}, got ${FORK_COMMIT}" >&2
    exit 1
  }
  if ! git -C "${FORK_DIR}" ls-files --error-unmatch pnpm-lock.yaml >/dev/null 2>&1; then
    if [[ "${ALLOW_DIRTY}" -ne 1 || "${VALIDATE_ONLY}" -ne 1 ]]; then
      echo "Fork lockfile must be tracked before release" >&2
      exit 1
    fi
  fi
  grep -F 'pnpm install --frozen-lockfile' "${FORK_DIR}/Dockerfile" >/dev/null || {
    echo "Fork Dockerfile must use frozen lockfile installation" >&2
    exit 1
  }
  dirty="$(git -C "${FORK_DIR}" status --porcelain)"
  if [[ -n "${dirty}" && "${ALLOW_DIRTY}" -ne 1 ]]; then
    echo "Fork must be clean before release build" >&2
    exit 1
  fi
  if [[ "${ALLOW_DIRTY}" -eq 1 && "${VALIDATE_ONLY}" -ne 1 ]]; then
    echo "--allow-dirty is only permitted with --validate-only" >&2
    exit 1
  fi
}

community_metadata_value() {
  local key="$1"
  printf '%s\n' "${COMMUNITY_METADATA}" | awk -F= -v wanted="${key}" \
    '$1 == wanted { print substr($0, index($0, "=") + 1); found=1 } END { if (!found) exit 1 }'
}

validate_community_snapshot() {
  require_file "${COMMUNITY_SNAPSHOT_VERIFIER}"
  git -C "${FORK_DIR}" ls-files --error-unmatch \
    scripts/community-agent-snapshot.selection.json \
    src/services/communityAgentSnapshot.snapshot.json >/dev/null 2>&1 || {
      echo "Community snapshot manifest and selection must be tracked" >&2
      exit 1
    }
  [[ -n "$(git -C "${FORK_DIR}" ls-files 'public/community-agent-snapshot/avatars/*')" ]] || {
    echo "Community snapshot avatars must be tracked" >&2
    exit 1
  }
  COMMUNITY_METADATA="$(node "${COMMUNITY_SNAPSHOT_VERIFIER}" "${FORK_DIR}")"
  COMMUNITY_AGENT_COUNT="$(community_metadata_value COMMUNITY_AGENT_COUNT)"
  COMMUNITY_SNAPSHOT_FETCHED_AT="$(community_metadata_value COMMUNITY_SNAPSHOT_FETCHED_AT)"
  COMMUNITY_SNAPSHOT_SHA256="$(community_metadata_value COMMUNITY_SNAPSHOT_SHA256)"
  COMMUNITY_SELECTION_SHA256="$(community_metadata_value COMMUNITY_SELECTION_SHA256)"
  COMMUNITY_ASSET_SET_SHA256="$(community_metadata_value COMMUNITY_ASSET_SET_SHA256)"
  COMMUNITY_SOURCE_REPOSITORY="$(community_metadata_value COMMUNITY_SOURCE_REPOSITORY)"
  COMMUNITY_SOURCE_LICENSE="$(community_metadata_value COMMUNITY_SOURCE_LICENSE)"
  COMMUNITY_SOURCE_LICENSE_URL="$(community_metadata_value COMMUNITY_SOURCE_LICENSE_URL)"
}

verify_linux_amd64_image() {
  local image="$1" architecture os
  architecture="$(docker image inspect -f '{{.Architecture}}' "${image}")"
  os="$(docker image inspect -f '{{.Os}}' "${image}")"
  [[ "${architecture}" == amd64 && "${os}" == linux ]] || {
    echo "Image must be linux/amd64, got ${os}/${architecture}: ${image}" >&2
    exit 1
  }
}

verify_app_image_revision() {
  local image="$1" actual_revision
  actual_revision="$(docker image inspect \
    -f '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "${image}")"
  [[ "${actual_revision}" == "${FORK_COMMIT}" ]] || {
    echo "App image revision does not match fork commit ${FORK_COMMIT}: ${actual_revision}" >&2
    exit 1
  }
}

require_file "${VERSION_FILE}"
require_file "${CLIENT_CONTRACT_FILE}"
# 构建机与后续离线安装器使用完全相同的客户端真实性门禁。
source "${CLIENT_CONTRACT_FILE}"
UPSTREAM_VERSION="$(state_value "${VERSION_FILE}" LOBEHUB_UPSTREAM_VERSION)"
UPSTREAM_COMMIT="$(state_value "${VERSION_FILE}" LOBEHUB_UPSTREAM_COMMIT)"
LOCKED_FORK_COMMIT="$(state_value "${VERSION_FILE}" LOBEHUB_FORK_COMMIT)"
INTERNAL_VERSION="$(state_value "${VERSION_FILE}" LOBEHUB_INTERNAL_VERSION)"
CONTRACT_VERSION="$(state_value "${VERSION_FILE}" LOBEHUB_PLATFORM_CONTRACT_VERSION)"
POSTGRES_MAJOR="$(state_value "${VERSION_FILE}" LOBEHUB_PARADEDB_POSTGRES_MAJOR)"

APP_IMAGE="${APP_IMAGE:-test-agent/lobehub:${INTERNAL_VERSION}}"
PARADEDB_IMAGE="${PARADEDB_IMAGE:-test-agent/paradedb:pg17-${INTERNAL_VERSION}}"
RUSTFS_IMAGE="${RUSTFS_IMAGE:-test-agent/rustfs:${INTERNAL_VERSION}}"

require_command git
require_command node
require_command shasum
require_command tar
validate_fork
validate_community_snapshot
require_digest_ref NODE_BASE_IMAGE "${NODE_BASE_IMAGE}"
require_digest_ref BUSYBOX_BASE_IMAGE "${BUSYBOX_BASE_IMAGE}"
require_digest_ref PARADEDB_SOURCE_IMAGE "${PARADEDB_SOURCE_IMAGE}"
require_digest_ref RUSTFS_SOURCE_IMAGE "${RUSTFS_SOURCE_IMAGE}"
require_digest_ref MC_SOURCE_IMAGE "${MC_SOURCE_IMAGE}"
require_immutable_tag APP_IMAGE "${APP_IMAGE}"
require_immutable_tag PARADEDB_IMAGE "${PARADEDB_IMAGE}"
require_immutable_tag RUSTFS_IMAGE "${RUSTFS_IMAGE}"
validate_clients

if [[ "${VALIDATE_ONLY}" -eq 1 ]]; then
  echo "LobeHub artifact inputs passed"
  exit 0
fi

require_command corepack
require_command docker
docker buildx version >/dev/null
docker scout sbom --help >/dev/null

OUTPUT_PARENT="$(dirname "${OUTPUT_DIR}")"
mkdir -p "${OUTPUT_PARENT}"
OUTPUT_PARENT="$(cd "${OUTPUT_PARENT}" && pwd)"
OUTPUT_DIR="${OUTPUT_PARENT}/$(basename "${OUTPUT_DIR}")"
case "${OUTPUT_DIR}" in
  /|"${HOME}"|"${ROOT_DIR}"|"${FORK_DIR}")
    echo "Unsafe LobeHub output directory: ${OUTPUT_DIR}" >&2
    exit 1
    ;;
esac
if [[ -e "${OUTPUT_DIR}" && "${FORCE}" -ne 1 ]]; then
  echo "Output already exists; use --force for this exact directory: ${OUTPUT_DIR}" >&2
  exit 1
fi

STAGING_DIR="$(mktemp -d "${OUTPUT_PARENT}/.lobehub-artifacts.XXXXXX")"
MC_CONTAINER=""
cleanup() {
  if [[ -n "${MC_CONTAINER}" ]]; then
    docker rm -f "${MC_CONTAINER}" >/dev/null 2>&1 || true
  fi
  [[ -n "${BUILD_CONTEXT_DIR}" && -d "${BUILD_CONTEXT_DIR}" ]] && rm -rf "${BUILD_CONTEXT_DIR}"
  [[ -d "${STAGING_DIR}" ]] && rm -rf "${STAGING_DIR}"
}
trap cleanup EXIT
mkdir -p "${STAGING_DIR}/images" "${STAGING_DIR}/clients" "${STAGING_DIR}/bin" \
  "${STAGING_DIR}/sbom" "${STAGING_DIR}/source"

if [[ "${SKIP_APP_BUILD}" -ne 1 ]]; then
  # Docker Desktop BuildKit may reject uncached Docker Hub manifests with an incorrect compressed
  # metadata size. Pulling the exact approved manifests first both verifies them and makes the build
  # consume the daemon's content-addressed cache without weakening digest pinning.
  docker pull --platform linux/amd64 "${NODE_BASE_IMAGE}"
  docker pull --platform linux/amd64 "${BUSYBOX_BASE_IMAGE}"
  # 从锁定提交导出独立上下文，保证长时间构建期间工作树变化不会混入镜像。
  BUILD_CONTEXT_DIR="$(mktemp -d "${OUTPUT_PARENT}/.lobehub-build-context.XXXXXX")"
  git -C "${FORK_DIR}" archive --format=tar "${FORK_COMMIT}" \
    | tar -xf - -C "${BUILD_CONTEXT_DIR}"
  docker buildx build --platform linux/amd64 --load \
    --build-arg "NODE_BASE_IMAGE=${NODE_BASE_IMAGE}" \
    --build-arg "BUSYBOX_BASE_IMAGE=${BUSYBOX_BASE_IMAGE}" \
    --build-arg "LOBEHUB_FORK_COMMIT=${FORK_COMMIT}" \
    --tag "${APP_IMAGE}" "${BUILD_CONTEXT_DIR}"
  rm -rf "${BUILD_CONTEXT_DIR}"
  BUILD_CONTEXT_DIR=""
fi
verify_linux_amd64_image "${APP_IMAGE}"
verify_app_image_revision "${APP_IMAGE}"

docker pull --platform linux/amd64 "${PARADEDB_SOURCE_IMAGE}"
docker pull --platform linux/amd64 "${RUSTFS_SOURCE_IMAGE}"
docker pull --platform linux/amd64 "${MC_SOURCE_IMAGE}"
docker tag "${PARADEDB_SOURCE_IMAGE}" "${PARADEDB_IMAGE}"
docker tag "${RUSTFS_SOURCE_IMAGE}" "${RUSTFS_IMAGE}"
verify_linux_amd64_image "${PARADEDB_IMAGE}"
verify_linux_amd64_image "${RUSTFS_IMAGE}"

docker save -o "${STAGING_DIR}/images/lobehub-image.tar" "${APP_IMAGE}"
docker save -o "${STAGING_DIR}/images/paradedb-image.tar" "${PARADEDB_IMAGE}"
docker save -o "${STAGING_DIR}/images/rustfs-image.tar" "${RUSTFS_IMAGE}"

MC_CONTAINER="$(docker create --platform linux/amd64 "${MC_SOURCE_IMAGE}")"
if ! docker cp "${MC_CONTAINER}:/usr/bin/mc" "${STAGING_DIR}/bin/mc-linux-amd64" 2>/dev/null; then
  docker cp "${MC_CONTAINER}:/usr/local/bin/mc" "${STAGING_DIR}/bin/mc-linux-amd64"
fi
docker rm "${MC_CONTAINER}" >/dev/null
MC_CONTAINER=""
chmod 0755 "${STAGING_DIR}/bin/mc-linux-amd64"

if [[ "${SERVER_ONLY}" -ne 1 ]]; then
  cp "${WINDOWS_CLIENT}" "${STAGING_DIR}/clients/lobehub-windows-x64.exe"
  cp "${LINUX_CLIENT}" "${STAGING_DIR}/clients/lobehub-linux-x86_64.tar.gz"
  cp "${WINDOWS_SIGNATURE_EVIDENCE}" \
    "${STAGING_DIR}/windows-authenticode-verification.txt"
  cp "${LINUX_APPROVAL_EVIDENCE}" \
    "${STAGING_DIR}/linux-client-verification.txt"
  cp "${LINUX_ACCEPTANCE_RECORD}" \
    "${STAGING_DIR}/linux-client-acceptance-record.txt"
  cp "${LINUX_BUILD_EVIDENCE}" \
    "${STAGING_DIR}/linux-client-build-evidence.txt"
fi

git -C "${FORK_DIR}" archive --format=tar.gz \
  --prefix="lobehub-${INTERNAL_VERSION}/" \
  --output="${STAGING_DIR}/source/lobehub-${INTERNAL_VERSION}.tar.gz" "${FORK_COMMIT}"

docker scout sbom --format spdx --output "${STAGING_DIR}/sbom/lobehub.spdx.json" \
  "local://${APP_IMAGE}"
{
  printf 'LobeHub %s production dependency license inventory\n' "${INTERNAL_VERSION}"
  printf 'Generated from the committed pnpm lockfile; JSON follows.\n\n'
  # Corepack 自身的默认 pnpm 版本不一定服从目标仓库的 packageManager；这里显式锁定
  # 与 fork 一致的版本，避免许可证清单阶段在完整镜像构建完成后才因版本漂移失败。
  corepack pnpm@10.33.0 --dir "${FORK_DIR}" licenses list --prod --json
  printf '\nCommunity Agent snapshot source: %s\n' "${COMMUNITY_SOURCE_REPOSITORY}"
  printf 'Community Agent snapshot license: %s (%s)\n' \
    "${COMMUNITY_SOURCE_LICENSE}" "${COMMUNITY_SOURCE_LICENSE_URL}"
  printf 'Community Agent snapshot SHA-256: %s\n' "${COMMUNITY_SNAPSHOT_SHA256}"
} >"${STAGING_DIR}/LICENSES.txt"

cat >"${STAGING_DIR}/approved-resources.json" <<EOF
{
  "schemaVersion": 1,
  "internalVersion": "${INTERNAL_VERSION}",
  "resources": [
    {"type":"source","name":"lobehub/lobehub","version":"${UPSTREAM_VERSION}","commit":"${UPSTREAM_COMMIT}","approved":true},
    {"type":"fork-source","name":"lobehub-platform","version":"${INTERNAL_VERSION}","commit":"${FORK_COMMIT}","approved":true},
    {"type":"community-agent-snapshot","source":"${COMMUNITY_SOURCE_REPOSITORY}","license":"${COMMUNITY_SOURCE_LICENSE}","licenseUrl":"${COMMUNITY_SOURCE_LICENSE_URL}","fetchedAt":"${COMMUNITY_SNAPSHOT_FETCHED_AT}","agentCount":${COMMUNITY_AGENT_COUNT},"snapshotSha256":"${COMMUNITY_SNAPSHOT_SHA256}","selectionSha256":"${COMMUNITY_SELECTION_SHA256}","assetSetSha256":"${COMMUNITY_ASSET_SET_SHA256}","approved":true},
    {"type":"container-base","reference":"${NODE_BASE_IMAGE}","approved":true},
    {"type":"container-base","reference":"${BUSYBOX_BASE_IMAGE}","approved":true},
    {"type":"container","reference":"${PARADEDB_SOURCE_IMAGE}","approved":true},
    {"type":"container","reference":"${RUSTFS_SOURCE_IMAGE}","approved":true},
    {"type":"binary-source","reference":"${MC_SOURCE_IMAGE}","approved":true}
  ],
  "runtimeDownloads": []
}
EOF

APP_IMAGE_ID="$(docker image inspect -f '{{.Id}}' "${APP_IMAGE}")"
PARADEDB_IMAGE_ID="$(docker image inspect -f '{{.Id}}' "${PARADEDB_IMAGE}")"
RUSTFS_IMAGE_ID="$(docker image inspect -f '{{.Id}}' "${RUSTFS_IMAGE}")"
WINDOWS_VERIFIED=false
LINUX_APPROVED=false
[[ "${SERVER_ONLY}" -eq 1 ]] || WINDOWS_VERIFIED=true
[[ "${SERVER_ONLY}" -eq 1 ]] || LINUX_APPROVED=true
cat >"${STAGING_DIR}/release.env" <<EOF
LOBEHUB_INTERNAL_VERSION=${INTERNAL_VERSION}
LOBEHUB_UPSTREAM_VERSION=${UPSTREAM_VERSION}
LOBEHUB_UPSTREAM_COMMIT=${UPSTREAM_COMMIT}
LOBEHUB_FORK_COMMIT=${FORK_COMMIT}
LOBEHUB_PLATFORM_CONTRACT_VERSION=${CONTRACT_VERSION}
LOBEHUB_PARADEDB_POSTGRES_MAJOR=${POSTGRES_MAJOR}
LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=${WINDOWS_VERIFIED}
LOBEHUB_LINUX_CLIENT_APPROVED=${LINUX_APPROVED}
LOBEHUB_LINUX_EXECUTION_DEFAULT=false
LOBEHUB_APP_IMAGE=${APP_IMAGE}
LOBEHUB_APP_IMAGE_ID=${APP_IMAGE_ID}
LOBEHUB_PARADEDB_IMAGE=${PARADEDB_IMAGE}
LOBEHUB_PARADEDB_IMAGE_ID=${PARADEDB_IMAGE_ID}
LOBEHUB_RUSTFS_IMAGE=${RUSTFS_IMAGE}
LOBEHUB_RUSTFS_IMAGE_ID=${RUSTFS_IMAGE_ID}
LOBEHUB_NODE_BASE_IMAGE=${NODE_BASE_IMAGE}
LOBEHUB_BUSYBOX_BASE_IMAGE=${BUSYBOX_BASE_IMAGE}
LOBEHUB_PARADEDB_SOURCE_IMAGE=${PARADEDB_SOURCE_IMAGE}
LOBEHUB_RUSTFS_SOURCE_IMAGE=${RUSTFS_SOURCE_IMAGE}
LOBEHUB_MC_SOURCE_IMAGE=${MC_SOURCE_IMAGE}
EOF

(
  cd "${STAGING_DIR}"
  if command -v sha256sum >/dev/null 2>&1; then
    find . -type f ! -name SHA256SUMS | LC_ALL=C sort | while IFS= read -r file; do
      sha256sum "${file#./}"
    done >SHA256SUMS
  else
    find . -type f ! -name SHA256SUMS | LC_ALL=C sort | while IFS= read -r file; do
      shasum -a 256 "${file#./}"
    done >SHA256SUMS
  fi
)

if [[ -e "${OUTPUT_DIR}" ]]; then
  rm -rf "${OUTPUT_DIR}"
fi
mv "${STAGING_DIR}" "${OUTPUT_DIR}"
STAGING_DIR=""
trap - EXIT

if [[ "${SERVER_ONLY}" -eq 1 ]]; then
  echo "Real LobeHub server staging artifacts built at ${OUTPUT_DIR}."
  echo "This set has no signed clients and is intentionally not package-release eligible."
else
  echo "Complete LobeHub artifact set built at ${OUTPUT_DIR}."
  echo "Next: TEST_AGENT_LOBEHUB_ARTIFACT_DIR=${OUTPUT_DIR} deploy/internal/package-release.sh --lobehub-only"
fi
