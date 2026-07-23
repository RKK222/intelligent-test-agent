#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/dist"
BUNDLE_NAME="test-agent-redis-offline"
IMAGE_TAR=""
ZIP_ONLY=0
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
  --zip-only           Repack the existing fixed ZIP without rotating its password.
  --output-dir <path>  Output directory. Default: deploy/internal/dist.
  -h, --help           Show this help.

Without --image-tar the script pulls the pinned official linux/amd64 manifest,
tags it as test-agent-redis:7.4.9-alpine and exports it. The generated bundle
contains a random Redis password and must be handled as a 0600 sensitive file.
--zip-only preserves the current bundle's image and secret config, refreshes
the deployment manual/scripts, and replaces the fixed ZIP plus checksum.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --image-tar)
      IMAGE_TAR="$2"
      shift 2
      ;;
    --zip-only)
      ZIP_ONLY=1
      shift
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

write_bundle_archive() {
  local bundle_root="$1"
  local bundle_parent tmp_archive output_archive output_checksum
  local output_archive_tmp output_checksum_tmp
  bundle_parent="$(dirname "${bundle_root}")"
  tmp_archive="${TMP_ROOT}/${BUNDLE_NAME}.zip"
  (cd "${bundle_parent}" && zip -qr "${tmp_archive}" "${BUNDLE_NAME}")
  unzip -tq "${tmp_archive}" >/dev/null

  output_archive="${OUTPUT_DIR}/${BUNDLE_NAME}.zip"
  output_checksum="${output_archive}.sha256"
  output_archive_tmp="$(mktemp "${OUTPUT_DIR}/.${BUNDLE_NAME}.zip.XXXXXX")"
  output_checksum_tmp="$(mktemp "${OUTPUT_DIR}/.${BUNDLE_NAME}.zip.sha256.XXXXXX")"
  install -m 0600 "${tmp_archive}" "${output_archive_tmp}"
  printf '%s  %s\n' "$(sha256_digest "${output_archive_tmp}")" "$(basename "${output_archive}")" \
    >"${output_checksum_tmp}"
  chmod 0600 "${output_checksum_tmp}"
  mv -f "${output_archive_tmp}" "${output_archive}"
  mv -f "${output_checksum_tmp}" "${output_checksum}"

  printf 'Standalone Redis bundle: %s\n' "${output_archive}"
  printf 'Bundle checksum: %s\n' "${output_checksum}"
  printf 'Bundle SHA256: %s\n' "$(sha256_digest "${output_archive}")"
  printf 'Sensitive bundle permissions: %s\n' \
    "$(stat -f '%Lp' "${output_archive}" 2>/dev/null || stat -c '%a' "${output_archive}")"
}

if [[ "${ZIP_ONLY}" -eq 1 ]]; then
  [[ -z "${IMAGE_TAR}" ]] || {
    echo "--zip-only cannot be combined with --image-tar" >&2
    exit 2
  }
  existing_archive="${OUTPUT_DIR}/${BUNDLE_NAME}.zip"
  require_file "${existing_archive}"
  reuse_parent="${TMP_ROOT}/reuse"
  mkdir -p "${reuse_parent}"
  unzip -q "${existing_archive}" -d "${reuse_parent}"
  reuse_root="${reuse_parent}/${BUNDLE_NAME}"
  require_file "${reuse_root}/config/redis.env"
  require_file "${reuse_root}/config/redis.conf"
  require_file "${reuse_root}/config/backend-redis.env"
  require_file "${reuse_root}/test-agent-redis_${REDIS_VERSION}-alpine-linux-amd64.tar"
  require_file "${reuse_root}/test-agent-redis_${REDIS_VERSION}-alpine-linux-amd64.tar.sha256"
  if grep -R 'REPLACE_' "${reuse_root}/config" >/dev/null; then
    echo "Existing Redis bundle contains a placeholder" >&2
    exit 1
  fi
  existing_redis_password="$(awk '$1 == "requirepass" {print $2; exit}' \
    "${reuse_root}/config/redis.conf")"
  existing_backend_password="$(awk -F= '$1 == "TEST_AGENT_REDIS_PASSWORD" {print $2; exit}' \
    "${reuse_root}/config/backend-redis.env")"
  if [[ ${#existing_redis_password} -ne 64 \
    || "${existing_redis_password}" != "${existing_backend_password}" ]]; then
    echo "Existing Redis bundle contains inconsistent credentials" >&2
    exit 1
  fi
  existing_image_tar="${reuse_root}/test-agent-redis_${REDIS_VERSION}-alpine-linux-amd64.tar"
  existing_image_checksum="$(awk 'NF >= 2 {print $1; exit}' "${existing_image_tar}.sha256")"
  if [[ "${existing_image_checksum}" != "$(sha256_digest "${existing_image_tar}")" ]]; then
    echo "Existing Redis bundle image checksum mismatch" >&2
    exit 1
  fi
  install -m 0644 "${SCRIPT_DIR}/REDIS-OFFLINE.md" "${reuse_root}/START-HERE.md"
  install -m 0755 "${SCRIPT_DIR}/deploy-redis.sh" "${reuse_root}/deploy-redis.sh"
  install -m 0755 "${SCRIPT_DIR}/redis-healthcheck.sh" "${reuse_root}/redis-healthcheck.sh"
  chmod 0600 "${reuse_root}/config/redis.env" \
    "${reuse_root}/config/redis.conf" \
    "${reuse_root}/config/backend-redis.env" \
    "${reuse_root}/test-agent-redis_${REDIS_VERSION}-alpine-linux-amd64.tar" \
    "${reuse_root}/test-agent-redis_${REDIS_VERSION}-alpine-linux-amd64.tar.sha256"
  write_bundle_archive "${reuse_root}"
  exit 0
fi

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

write_bundle_archive "${BUNDLE_ROOT}"
