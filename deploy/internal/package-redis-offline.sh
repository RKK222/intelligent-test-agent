#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/dist"
BUNDLE_NAME="test-agent-redis-offline"
IMAGE_TAR=""
REDIS_VERSION="7.4.9"
REDIS_IMAGE="test-agent-redis:7.4.9-alpine"
# 官方 redis:7.4.9-alpine 的 linux/amd64 manifest，锁定后避免浮动 tag 改变交付内容。
SOURCE_IMAGE="redis@sha256:b1addbe72465a718643cff9e60a58e6df1841e29d6d7d60c9a85d8d72f08d1a7"

usage() {
  cat <<'USAGE'
Usage: package-redis-offline.sh [options]

Build a fixed-name, sensitive standalone Redis 7.4.9 linux/amd64 offline bundle.

Options:
  --image-tar <path>   Reuse a Docker-loadable test-agent-redis:7.4.9-alpine tar.
  --output-dir <path>  Output directory. Default: deploy/internal/dist.
  -h, --help           Show this help.

Without --image-tar the script pulls the pinned official linux/amd64 manifest,
tags it as test-agent-redis:7.4.9-alpine and exports it. The generated bundle
contains a random Redis password and must be handled as a 0600 sensitive file.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --image-tar)
      IMAGE_TAR="$2"
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

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

require_file() {
  [[ -f "$1" ]] || {
    echo "Required file not found: $1" >&2
    exit 1
  }
}

sha256_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

for command_name in zip unzip tar awk openssl; do
  require_command "${command_name}"
done
require_file "${SCRIPT_DIR}/REDIS-OFFLINE.md"
require_file "${SCRIPT_DIR}/deploy-redis.sh"
require_file "${SCRIPT_DIR}/redis-healthcheck.sh"
require_file "${SCRIPT_DIR}/redis.env.example"
require_file "${SCRIPT_DIR}/redis.conf.example"

mkdir -p "${OUTPUT_DIR}"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-redis-offline.XXXXXX")"
cleanup() { rm -rf "${TMP_ROOT}"; }
trap cleanup EXIT

if [[ -z "${IMAGE_TAR}" ]]; then
  require_command docker
  printf 'Pulling pinned Redis %s linux/amd64 image...\n' "${REDIS_VERSION}"
  docker pull --platform linux/amd64 "${SOURCE_IMAGE}" >/dev/null
  docker tag "${SOURCE_IMAGE}" "${REDIS_IMAGE}"
  image_platform="$(docker image inspect -f '{{.Os}}/{{.Architecture}}' "${REDIS_IMAGE}")"
  [[ "${image_platform}" == "linux/amd64" ]] || {
    echo "Redis image must be linux/amd64, got ${image_platform}" >&2
    exit 1
  }
  image_version="$(docker run --rm --platform linux/amd64 "${REDIS_IMAGE}" redis-server --version \
    | awk '{for (i=1; i<=NF; i++) if ($i ~ /^v=/) {sub(/^v=/, "", $i); print $i; exit}}')"
  [[ "${image_version}" == "${REDIS_VERSION}" ]] || {
    echo "Redis image version must be ${REDIS_VERSION}, got ${image_version:-unknown}" >&2
    exit 1
  }
  IMAGE_TAR="${TMP_ROOT}/test-agent-redis_${REDIS_VERSION}-alpine-linux-amd64.tar"
  docker save -o "${IMAGE_TAR}" "${REDIS_IMAGE}"
else
  require_file "${IMAGE_TAR}"
fi

image_listing="$(tar -tf "${IMAGE_TAR}")"
grep -Fx 'manifest.json' <<<"${image_listing}" >/dev/null || {
  echo "Redis image tar is missing manifest.json" >&2
  exit 1
}
manifest_json="$(tar -xOf "${IMAGE_TAR}" manifest.json)"
grep -Fq 'test-agent-redis:7.4.9-alpine' <<<"${manifest_json}" || {
  echo "Redis image tar does not contain ${REDIS_IMAGE}" >&2
  exit 1
}

BUNDLE_ROOT="${TMP_ROOT}/${BUNDLE_NAME}"
mkdir -p "${BUNDLE_ROOT}/config"
install -m 0644 "${SCRIPT_DIR}/REDIS-OFFLINE.md" "${BUNDLE_ROOT}/START-HERE.md"
install -m 0755 "${SCRIPT_DIR}/deploy-redis.sh" "${BUNDLE_ROOT}/deploy-redis.sh"
install -m 0755 "${SCRIPT_DIR}/redis-healthcheck.sh" "${BUNDLE_ROOT}/redis-healthcheck.sh"
install -m 0644 "${SCRIPT_DIR}/redis.env.example" "${BUNDLE_ROOT}/config/redis.env"

# 密码只写进 0600 离线包，不打印到终端，也不进入仓库文件。
redis_password="$(openssl rand -hex 32)"
awk -v password="${redis_password}" \
  '{gsub(/REPLACE_REDIS_PASSWORD/, password); print}' \
  "${SCRIPT_DIR}/redis.conf.example" >"${BUNDLE_ROOT}/config/redis.conf"
printf '%s\n' \
  'TEST_AGENT_REDIS_HOST=122.233.30.20' \
  'TEST_AGENT_REDIS_PORT=6379' \
  "TEST_AGENT_REDIS_PASSWORD=${redis_password}" \
  >"${BUNDLE_ROOT}/config/backend-redis.env"
chmod 0600 "${BUNDLE_ROOT}/config/redis.env" \
  "${BUNDLE_ROOT}/config/redis.conf" \
  "${BUNDLE_ROOT}/config/backend-redis.env"

TARGET_IMAGE_TAR="${BUNDLE_ROOT}/test-agent-redis_${REDIS_VERSION}-alpine-linux-amd64.tar"
install -m 0600 "${IMAGE_TAR}" "${TARGET_IMAGE_TAR}"
printf '%s  %s\n' "$(sha256_digest "${TARGET_IMAGE_TAR}")" "$(basename "${TARGET_IMAGE_TAR}")" \
  >"${TARGET_IMAGE_TAR}.sha256"
chmod 0600 "${TARGET_IMAGE_TAR}.sha256"

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

printf 'Standalone Redis bundle: %s\n' "${OUTPUT_ARCHIVE}"
printf 'Bundle checksum: %s\n' "${OUTPUT_CHECKSUM}"
printf 'Bundle SHA256: %s\n' "$(sha256_digest "${OUTPUT_ARCHIVE}")"
printf 'Sensitive bundle permissions: %s\n' "$(stat -f '%Lp' "${OUTPUT_ARCHIVE}" 2>/dev/null || stat -c '%a' "${OUTPUT_ARCHIVE}")"
