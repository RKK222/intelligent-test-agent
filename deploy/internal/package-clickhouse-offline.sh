#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=archive-common.sh
source "${SCRIPT_DIR}/archive-common.sh"
OUTPUT_DIR="${SCRIPT_DIR}/dist"
BUNDLE_NAME="test-agent-clickhouse-offline"
VERSION="26.3.17.56"
IMAGE="test-agent-clickhouse:${VERSION}"
SOURCE_IMAGE="clickhouse/clickhouse-server:${VERSION}@sha256:422be85ae7344058369cdd366ac0efea9daa8428b55c9cf50258e83a7d12fcb3"
IMAGE_TAR=""
PINNED_IMAGE_DOCKERFILE="${SCRIPT_DIR}/memory/PinnedImage.Dockerfile"

usage() {
  cat <<'USAGE'
Usage: package-clickhouse-offline.sh [options]

Build the standalone ClickHouse 26.3.17.56 linux/amd64 offline bundle.

Optional environment variables:
  TEST_AGENT_CLICKHOUSE_PACKAGE_USERNAME  Generated bundle username.
  TEST_AGENT_CLICKHOUSE_PACKAGE_PASSWORD  Generated bundle password. If omitted,
                                          a random 64-hex-character password is used.

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

for command_name in zip unzip tar awk openssl python3; do require_command "${command_name}"; done
for required in CLICKHOUSE-ANALYTICS.md deploy-clickhouse.sh clickhouse.env.example clickhouse-users.xml.example; do
  require_file "${SCRIPT_DIR}/${required}"
done
require_file "${PINNED_IMAGE_DOCKERFILE}"

mkdir -p "${OUTPUT_DIR}"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-clickhouse-offline.XXXXXX")"
cleanup() { rm -rf "${TMP_ROOT}"; }
trap cleanup EXIT

if [[ -z "${IMAGE_TAR}" ]]; then
  require_command docker
  docker buildx version >/dev/null
  IMAGE_TAR="${TMP_ROOT}/test-agent-clickhouse_${VERSION}-linux-amd64.tar"
  # 直接导出 amd64 docker archive，避免 Apple Silicon 本地 image store 中同 tag 的
  # arm64 镜像覆盖或阻止 amd64 变体加载。
  docker buildx build --platform linux/amd64 --provenance=false \
    --build-arg "SOURCE_IMAGE=${SOURCE_IMAGE}" -t "${IMAGE}" \
    --output "type=docker,dest=${IMAGE_TAR}" \
    -f "${PINNED_IMAGE_DOCKERFILE}" "${SCRIPT_DIR}/memory"
else
  require_file "${IMAGE_TAR}"
fi

python3 - "${IMAGE_TAR}" "${IMAGE}" <<'PY'
import json
import sys
import tarfile

archive_path, expected_tag = sys.argv[1:]
with tarfile.open(archive_path) as archive:
    manifest = json.load(archive.extractfile("manifest.json"))
    matches = [item for item in manifest if expected_tag in item.get("RepoTags", [])]
    if len(matches) != 1:
        raise SystemExit(f"ClickHouse image tar does not contain exactly one {expected_tag}")
    config = json.load(archive.extractfile(matches[0]["Config"]))
if config.get("os") != "linux" or config.get("architecture") != "amd64":
    raise SystemExit(
        "ClickHouse image tar must be linux/amd64, got "
        f"{config.get('os')}/{config.get('architecture')}"
    )
PY

BUNDLE_ROOT="${TMP_ROOT}/${BUNDLE_NAME}"
mkdir -p "${BUNDLE_ROOT}/config"
install -m 0644 "${SCRIPT_DIR}/CLICKHOUSE-ANALYTICS.md" "${BUNDLE_ROOT}/START-HERE.md"
install -m 0755 "${SCRIPT_DIR}/deploy-clickhouse.sh" "${BUNDLE_ROOT}/deploy-clickhouse.sh"

username="${TEST_AGENT_CLICKHOUSE_PACKAGE_USERNAME:-testagent_analytics}"
password="${TEST_AGENT_CLICKHOUSE_PACKAGE_PASSWORD:-}"
[[ "${username}" =~ ^[A-Za-z][A-Za-z0-9_]{1,31}$ ]] || {
  echo "ClickHouse package username must contain 2-32 letters, digits or underscores" >&2
  exit 1
}
if [[ -z "${password}" ]]; then
  password="$(openssl rand -hex 32)"
else
  [[ "${password}" =~ ^[A-Za-z0-9._~-]{8,64}$ ]] || {
    echo "Explicit ClickHouse package password must contain 8-64 URL-safe characters" >&2
    exit 1
  }
fi
password_sha256="$(printf '%s' "${password}" | openssl dgst -sha256 | awk '{print $NF}')"
awk -v username="${username}" -v password="${password}" '
  /^TEST_AGENT_CLICKHOUSE_USERNAME=/ { print "TEST_AGENT_CLICKHOUSE_USERNAME=" username; next }
  { gsub(/REPLACE_CLICKHOUSE_PASSWORD/, password); print }
' \
  "${SCRIPT_DIR}/clickhouse.env.example" >"${BUNDLE_ROOT}/config/clickhouse.env"
awk -v username="${username}" -v digest="${password_sha256}" '
  { gsub(/testagent_analytics/, username); gsub(/REPLACE_CLICKHOUSE_PASSWORD_SHA256/, digest); print }
' \
  "${SCRIPT_DIR}/clickhouse-users.xml.example" >"${BUNDLE_ROOT}/config/clickhouse-users.xml"
printf '%s\n' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_ENABLED=true' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_URL=jdbc:clickhouse://REPLACE_CLICKHOUSE_HOST:8123/testagent_analytics' \
  "TEST_AGENT_ANALYTICS_CLICKHOUSE_USERNAME=${username}" \
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
