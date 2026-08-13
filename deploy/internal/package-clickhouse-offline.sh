#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=archive-common.sh
source "${SCRIPT_DIR}/archive-common.sh"
OUTPUT_DIR="${SCRIPT_DIR}/dist"
BUNDLE_NAME="test-agent-clickhouse-offline"
VERSION="26.3.17.56"
IMAGE="test-agent-clickhouse:${VERSION}"
SOURCE_IMAGE="clickhouse/clickhouse-server:${VERSION}"
IMAGE_TAR=""

usage() {
  cat <<'USAGE'
Usage: package-clickhouse-offline.sh [options]

Build the standalone ClickHouse 26.3.17.56 linux/amd64 offline bundle.

Options:
  --image-tar <path>   Reuse a Docker-loadable test-agent-clickhouse image tar.
  --output-dir <path>  Output directory. Default: deploy/internal/dist.
  -h, --help           Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --image-tar) IMAGE_TAR="$2"; shift 2 ;;
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

require_command() { command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 1; }; }
require_file() { [[ -f "$1" ]] || { echo "Required file not found: $1" >&2; exit 1; }; }
sha256_digest() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}';
  else shasum -a 256 "$1" | awk '{print $1}'; fi
}

for command_name in zip unzip tar awk openssl; do require_command "${command_name}"; done
for required in CLICKHOUSE-ANALYTICS.md deploy-clickhouse.sh clickhouse.env.example clickhouse-users.xml.example; do
  require_file "${SCRIPT_DIR}/${required}"
done

mkdir -p "${OUTPUT_DIR}"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-clickhouse-offline.XXXXXX")"
cleanup() { rm -rf "${TMP_ROOT}"; }
trap cleanup EXIT

if [[ -z "${IMAGE_TAR}" ]]; then
  require_command docker
  docker pull --platform linux/amd64 "${SOURCE_IMAGE}" >/dev/null
  docker tag "${SOURCE_IMAGE}" "${IMAGE}"
  [[ "$(docker image inspect -f '{{.Os}}/{{.Architecture}}' "${IMAGE}")" == "linux/amd64" ]] || {
    echo "ClickHouse image must be linux/amd64" >&2; exit 1;
  }
  IMAGE_TAR="${TMP_ROOT}/test-agent-clickhouse_${VERSION}-linux-amd64.tar"
  docker save -o "${IMAGE_TAR}" "${IMAGE}"
else
  require_file "${IMAGE_TAR}"
fi

manifest_json="$(tar -xOf "${IMAGE_TAR}" manifest.json)"
grep -Fq "test-agent-clickhouse:${VERSION}" <<<"${manifest_json}" || {
  echo "ClickHouse image tar does not contain ${IMAGE}" >&2; exit 1;
}

BUNDLE_ROOT="${TMP_ROOT}/${BUNDLE_NAME}"
mkdir -p "${BUNDLE_ROOT}/config"
install -m 0644 "${SCRIPT_DIR}/CLICKHOUSE-ANALYTICS.md" "${BUNDLE_ROOT}/START-HERE.md"
install -m 0755 "${SCRIPT_DIR}/deploy-clickhouse.sh" "${BUNDLE_ROOT}/deploy-clickhouse.sh"

password="$(openssl rand -hex 32)"
password_sha256="$(printf '%s' "${password}" | openssl dgst -sha256 | awk '{print $NF}')"
awk -v password="${password}" '{gsub(/REPLACE_CLICKHOUSE_PASSWORD/, password); print}' \
  "${SCRIPT_DIR}/clickhouse.env.example" >"${BUNDLE_ROOT}/config/clickhouse.env"
awk -v digest="${password_sha256}" '{gsub(/REPLACE_CLICKHOUSE_PASSWORD_SHA256/, digest); print}' \
  "${SCRIPT_DIR}/clickhouse-users.xml.example" >"${BUNDLE_ROOT}/config/clickhouse-users.xml"
printf '%s\n' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_ENABLED=true' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_URL=jdbc:clickhouse://REPLACE_CLICKHOUSE_HOST:8123/testagent_analytics' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_USERNAME=testagent_analytics' \
  "TEST_AGENT_ANALYTICS_CLICKHOUSE_PASSWORD=${password}" \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=false' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_START=2025-01-01T00:00:00Z' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_END=' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_CLEANUP_LEGACY_ROLLUPS=false' \
  >"${BUNDLE_ROOT}/config/backend-clickhouse.env"
chmod 0600 "${BUNDLE_ROOT}/config/"*

target_tar="${BUNDLE_ROOT}/test-agent-clickhouse_${VERSION}-linux-amd64.tar"
install -m 0600 "${IMAGE_TAR}" "${target_tar}"
printf '%s  %s\n' "$(sha256_digest "${target_tar}")" "$(basename "${target_tar}")" >"${target_tar}.sha256"
chmod 0600 "${target_tar}.sha256"

tmp_archive="${TMP_ROOT}/${BUNDLE_NAME}.zip"
archive_create_zip "${tmp_archive}" "${TMP_ROOT}" "${BUNDLE_NAME}"
unzip -tq "${tmp_archive}" >/dev/null
output_archive="${OUTPUT_DIR}/${BUNDLE_NAME}.zip"
output_checksum="${output_archive}.sha256"
install -m 0600 "${tmp_archive}" "${output_archive}"
printf '%s  %s\n' "$(sha256_digest "${output_archive}")" "$(basename "${output_archive}")" >"${output_checksum}"
chmod 0600 "${output_checksum}"
archive_strip_file_metadata "${output_archive}" "${output_checksum}"
printf 'ClickHouse bundle: %s\n' "${output_archive}"
printf 'Bundle checksum: %s\n' "${output_checksum}"
