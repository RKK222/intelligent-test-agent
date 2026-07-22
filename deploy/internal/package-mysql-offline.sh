#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_TAR="${SCRIPT_DIR}/dist/mysql_8.4-linux-amd64.tar"
NODES_DIR=""
OUTPUT_DIR="${SCRIPT_DIR}/dist"
BUNDLE_NAME="test-agent-mysql-offline"

usage() {
  cat <<'USAGE'
Usage: package-mysql-offline.sh --nodes-dir <path> [options]

Assemble the standalone MySQL image, prepared .147 configuration and deploy
scripts into one fixed-name USB delivery bundle.

Options:
  --image-tar <path>   Docker-loadable mysql:8.4 linux/amd64 tar.
  --nodes-dir <path>   Directory containing the prepared .147 MySQL node archive and SHA file.
  --output-dir <path>  Output directory. Default: deploy/internal/dist.
  -h, --help           Show this help.

Fixed output names:
  test-agent-mysql-offline.zip
  test-agent-mysql-offline.zip.sha256
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --image-tar)
      IMAGE_TAR="$2"
      shift 2
      ;;
    --nodes-dir)
      NODES_DIR="$2"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

require_file() {
  [[ -f "$1" ]] || {
    echo "Required file not found: $1" >&2
    exit 1
  }
}

sha256_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    echo "Neither sha256sum nor shasum is available" >&2
    exit 1
  fi
}

verify_checksum_pair() {
  local file="$1"
  local checksum="${file}.sha256"
  local expected actual checksum_name
  require_file "${file}"
  require_file "${checksum}"
  checksum_name="$(awk 'NF >= 2 {print $2; exit}' "${checksum}")"
  checksum_name="${checksum_name#\*}"
  [[ "${checksum_name}" == "$(basename "${file}")" ]] || {
    echo "Checksum file does not name $(basename "${file}"): ${checksum}" >&2
    exit 1
  }
  expected="$(awk 'NF >= 2 {print $1; exit}' "${checksum}")"
  actual="$(sha256_digest "${file}")"
  [[ -n "${expected}" && "${expected}" == "${actual}" ]] || {
    echo "SHA256 mismatch: ${file}" >&2
    exit 1
  }
}

require_archive_entry() {
  local listing="$1"
  local entry="$2"
  grep -Fx "${entry}" <<<"${listing}" >/dev/null || {
    echo "Required archive entry not found: ${entry}" >&2
    exit 1
  }
}

[[ -n "${NODES_DIR}" ]] || {
  echo "--nodes-dir is required" >&2
  usage >&2
  exit 2
}

for command_name in zip unzip tar awk; do
  command -v "${command_name}" >/dev/null 2>&1 || {
    echo "Required command not found: ${command_name}" >&2
    exit 1
  }
done

NODE_DIR="test-agent-two-backend-122.233.30.147-mysql"
NODE_ARCHIVE="${NODES_DIR}/${NODE_DIR}-SENSITIVE.tar.gz"
require_file "${IMAGE_TAR}"
require_file "${SCRIPT_DIR}/deploy-node-common.sh"
require_file "${SCRIPT_DIR}/deploy-mysql-node.sh"
require_file "${SCRIPT_DIR}/deploy-xxl-job-mysql.sh"
verify_checksum_pair "${NODE_ARCHIVE}"

image_listing="$(tar -tf "${IMAGE_TAR}")"
require_archive_entry "${image_listing}" manifest.json

node_size="$(wc -c <"${NODE_ARCHIVE}" | tr -d '[:space:]')"
[[ "${node_size}" -le 1048576 ]] || {
  echo "Prepared MySQL node archive exceeds 1 MiB: ${NODE_ARCHIVE}" >&2
  exit 1
}
node_listing="$(tar -tzf "${NODE_ARCHIVE}")"
require_archive_entry "${node_listing}" "${NODE_DIR}/deploy-xxl-job-mysql.sh"
require_archive_entry "${node_listing}" "${NODE_DIR}/MULTI-BACKEND.md"
require_archive_entry "${node_listing}" "${NODE_DIR}/config/mysql.env"
mysql_env="$(tar -xOzf "${NODE_ARCHIVE}" "${NODE_DIR}/config/mysql.env")"
grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_IMAGE=mysql:8.4' <<<"${mysql_env}"
grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_HOST_PORT=3306' <<<"${mysql_env}"
grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_DATABASE=xxl_job' <<<"${mysql_env}"
grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_USERNAME=xxl_job' <<<"${mysql_env}"
if grep -q 'REPLACE_' <<<"${mysql_env}"; then
  echo "Prepared mysql.env still contains a REPLACE_ placeholder" >&2
  exit 1
fi

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-mysql-offline.XXXXXX")"
cleanup() { rm -rf "${TMP_ROOT}"; }
trap cleanup EXIT

BUNDLE_ROOT="${TMP_ROOT}/${BUNDLE_NAME}"
NODE_ROOT="${TMP_ROOT}/node"
mkdir -p "${BUNDLE_ROOT}/nodes" "${NODE_ROOT}" "${OUTPUT_DIR}"
install -m 0644 "${SCRIPT_DIR}/MULTI-BACKEND.md" "${BUNDLE_ROOT}/START-HERE.md"
install -m 0644 "${SCRIPT_DIR}/deploy-node-common.sh" "${BUNDLE_ROOT}/deploy-node-common.sh"
install -m 0755 "${SCRIPT_DIR}/deploy-mysql-node.sh" "${BUNDLE_ROOT}/deploy-mysql-node.sh"
install -m 0600 "${IMAGE_TAR}" "${BUNDLE_ROOT}/mysql_8.4-linux-amd64.tar"
printf '%s  %s\n' "$(sha256_digest "${BUNDLE_ROOT}/mysql_8.4-linux-amd64.tar")" \
  mysql_8.4-linux-amd64.tar >"${BUNDLE_ROOT}/mysql_8.4-linux-amd64.tar.sha256"

# 节点敏感配置保持不变，只换入本次已校验的部署脚本和手册。
tar -C "${NODE_ROOT}" -xzf "${NODE_ARCHIVE}"
install -m 0755 "${SCRIPT_DIR}/deploy-xxl-job-mysql.sh" \
  "${NODE_ROOT}/${NODE_DIR}/deploy-xxl-job-mysql.sh"
install -m 0644 "${SCRIPT_DIR}/MULTI-BACKEND.md" "${NODE_ROOT}/${NODE_DIR}/MULTI-BACKEND.md"
TARGET_NODE="${BUNDLE_ROOT}/nodes/$(basename "${NODE_ARCHIVE}")"
tar -C "${NODE_ROOT}" -czf "${TARGET_NODE}" "${NODE_DIR}"
chmod 0600 "${TARGET_NODE}"
printf '%s  %s\n' "$(sha256_digest "${TARGET_NODE}")" "$(basename "${TARGET_NODE}")" \
  >"${TARGET_NODE}.sha256"

TMP_ARCHIVE="${TMP_ROOT}/${BUNDLE_NAME}.zip"
(cd "${TMP_ROOT}" && zip -qr "${TMP_ARCHIVE}" "${BUNDLE_NAME}")
unzip -tq "${TMP_ARCHIVE}" >/dev/null

OUTPUT_ARCHIVE="${OUTPUT_DIR}/${BUNDLE_NAME}.zip"
OUTPUT_CHECKSUM="${OUTPUT_ARCHIVE}.sha256"
OUTPUT_ARCHIVE_TMP="$(mktemp "${OUTPUT_DIR}/.${BUNDLE_NAME}.zip.XXXXXX")"
OUTPUT_CHECKSUM_TMP="$(mktemp "${OUTPUT_DIR}/.${BUNDLE_NAME}.zip.sha256.XXXXXX")"
install -m 0600 "${TMP_ARCHIVE}" "${OUTPUT_ARCHIVE_TMP}"
printf '%s  %s\n' "$(sha256_digest "${OUTPUT_ARCHIVE_TMP}")" "$(basename "${OUTPUT_ARCHIVE}")" \
  >"${OUTPUT_CHECKSUM_TMP}"
chmod 0600 "${OUTPUT_CHECKSUM_TMP}"
mv -f "${OUTPUT_ARCHIVE_TMP}" "${OUTPUT_ARCHIVE}"
mv -f "${OUTPUT_CHECKSUM_TMP}" "${OUTPUT_CHECKSUM}"

printf 'Standalone MySQL bundle: %s\n' "${OUTPUT_ARCHIVE}"
printf 'Bundle checksum: %s\n' "${OUTPUT_CHECKSUM}"
printf 'Bundle SHA256: %s\n' "$(sha256_digest "${OUTPUT_ARCHIVE}")"
