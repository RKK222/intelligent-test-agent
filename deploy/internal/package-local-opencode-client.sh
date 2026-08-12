#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=archive-common.sh
source "${SCRIPT_DIR}/archive-common.sh"

OUTPUT_DIR="${SCRIPT_DIR}/dist/local-opencode-client"
VERSION="${TEST_AGENT_LOCAL_CLIENT_VERSION:-0.1.0}"
SIGNING_KEY="${TEST_AGENT_LOCAL_CLIENT_SIGNING_KEY:-}"
PUBLIC_KEY="${TEST_AGENT_LOCAL_CLIENT_SIGNING_PUBLIC_KEY:-}"
CLIENT_JAR="${TEST_AGENT_LOCAL_CLIENT_JAR:-}"
SKIP_BUILD=0

JRE_DARWIN_URL="${TEST_AGENT_LOCAL_CLIENT_JRE_DARWIN_ARM64_URL:-https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.9%2B10/OpenJDK21U-jre_aarch64_mac_hotspot_21.0.9_10.tar.gz}"
JRE_DARWIN_SHA="${TEST_AGENT_LOCAL_CLIENT_JRE_DARWIN_ARM64_SHA256:-1f7f6506b598e85d7d8ff8b36563d98657d2d81b16bfca3cd242d7906cfbd11b}"
JRE_DARWIN_ARCHIVE="${TEST_AGENT_LOCAL_CLIENT_JRE_DARWIN_ARM64_ARCHIVE:-}"
JRE_LINUX_URL="${TEST_AGENT_LOCAL_CLIENT_JRE_LINUX_ARM64_GLIBC_URL:-https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.9%2B10/OpenJDK21U-jre_aarch64_linux_hotspot_21.0.9_10.tar.gz}"
JRE_LINUX_SHA="${TEST_AGENT_LOCAL_CLIENT_JRE_LINUX_ARM64_GLIBC_SHA256:-1d041073c65e834bdb4da732485a54ff829859dcd1549e7992f15bd73341be29}"
JRE_LINUX_ARCHIVE="${TEST_AGENT_LOCAL_CLIENT_JRE_LINUX_ARM64_GLIBC_ARCHIVE:-}"
OPENCODE_DARWIN_URL="${TEST_AGENT_LOCAL_CLIENT_OPENCODE_DARWIN_ARM64_URL:-https://github.com/anomalyco/opencode/releases/download/v1.18.4/opencode-darwin-arm64.zip}"
OPENCODE_DARWIN_SHA="${TEST_AGENT_LOCAL_CLIENT_OPENCODE_DARWIN_ARM64_SHA256:-04fb881b632b323c712dfda6dcbbc6fce736394f07ba76176e52d6665925d4e6}"
OPENCODE_DARWIN_ARCHIVE="${TEST_AGENT_LOCAL_CLIENT_OPENCODE_DARWIN_ARM64_ARCHIVE:-}"
OPENCODE_LINUX_URL="${TEST_AGENT_LOCAL_CLIENT_OPENCODE_LINUX_ARM64_GLIBC_URL:-https://github.com/anomalyco/opencode/releases/download/v1.18.4/opencode-linux-arm64.tar.gz}"
OPENCODE_LINUX_SHA="${TEST_AGENT_LOCAL_CLIENT_OPENCODE_LINUX_ARM64_GLIBC_SHA256:-eba87efba3976d533a24cca0316f8ef375b5f8e797c0a95c25ee919700b7ba35}"
OPENCODE_LINUX_ARCHIVE="${TEST_AGENT_LOCAL_CLIENT_OPENCODE_LINUX_ARM64_GLIBC_ARCHIVE:-}"

usage() {
  cat <<'USAGE'
Usage: deploy/internal/package-local-opencode-client.sh [options]

Build the signed local OpenCode client HTTP distribution for Apple Silicon and
ARM64 glibc Linux. A signing private key is mandatory and is never copied into
the resulting artifacts.

Options:
  --output-dir <path>   Output directory. Default: deploy/internal/dist/local-opencode-client.
  --version <version>   Immutable client release version. Default: 0.1.0.
  --signing-key <path>  PEM private key used to sign stable/manifest.json.
  --public-key <path>   PEM public key; derived from the private key when omitted.
  --client-jar <path>   Prebuilt shaded client JAR.
  --skip-build          Do not invoke Maven; requires --client-jar.
  -h, --help            Show this help.

Source archive paths, URLs and pinned SHA-256 values can be overridden with the
TEST_AGENT_LOCAL_CLIENT_JRE_* and TEST_AGENT_LOCAL_CLIENT_OPENCODE_* variables
documented in deploy/internal/env.example.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    --version) VERSION="$2"; shift 2 ;;
    --signing-key) SIGNING_KEY="$2"; shift 2 ;;
    --public-key) PUBLIC_KEY="$2"; shift 2 ;;
    --client-jar) CLIENT_JAR="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

case "${VERSION}" in
  ''|*[!A-Za-z0-9._-]*) echo "Invalid local client version: ${VERSION}" >&2; exit 2 ;;
esac
case "${OUTPUT_DIR}" in
  ''|'/'|"${HOME}"|"${ROOT_DIR}"|"${SCRIPT_DIR}")
    echo "Refusing unsafe local client output directory: ${OUTPUT_DIR}" >&2
    exit 2
    ;;
esac
[[ "$(basename "${OUTPUT_DIR}")" == "local-opencode-client" ]] || {
  echo "Refusing output directory without a local-opencode-client leaf: ${OUTPUT_DIR}" >&2
  exit 2
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 1; }
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    echo "sha256sum or shasum is required" >&2
    exit 1
  fi
}

fetch_source() {
  local supplied_path="$1" url="$2" expected_sha="$3" destination="$4" actual_sha
  if [[ -n "${supplied_path}" ]]; then
    [[ -f "${supplied_path}" ]] || { echo "Source archive not found: ${supplied_path}" >&2; exit 1; }
    cp "${supplied_path}" "${destination}"
  else
    curl -fL --retry 3 --proto '=https' "${url}" -o "${destination}"
  fi
  actual_sha="$(sha256_file "${destination}")"
  [[ "${actual_sha}" == "${expected_sha}" ]] || {
    echo "Source SHA-256 mismatch for ${destination}: expected=${expected_sha} actual=${actual_sha}" >&2
    exit 1
  }
}

extract_archive() {
  local archive="$1" destination="$2"
  mkdir -p "${destination}"
  case "${archive}" in
    *.zip) unzip -q "${archive}" -d "${destination}" ;;
    *.tar.gz|*.tgz) tar -C "${destination}" -xzf "${archive}" ;;
    *) echo "Unsupported source archive: ${archive}" >&2; exit 1 ;;
  esac
}

normalize_jre() {
  local archive="$1" output="$2" work="$3" java_binary java_home stage
  extract_archive "${archive}" "${work}/extract"
  java_binary="$(find "${work}/extract" -type f -path '*/bin/java' | sort | head -n 1)"
  [[ -n "${java_binary}" ]] || { echo "JRE archive does not contain bin/java" >&2; exit 1; }
  java_home="$(cd "$(dirname "${java_binary}")/.." && pwd)"
  stage="${work}/stage"
  mkdir -p "${stage}/jre"
  cp -a "${java_home}/." "${stage}/jre/"
  chmod 0755 "${stage}/jre/bin/java"
  archive_create_tar_gz "${output}" "${stage}" jre
}

normalize_opencode() {
  local archive="$1" output="$2" work="$3" executable stage
  extract_archive "${archive}" "${work}/extract"
  executable="$(find "${work}/extract" -type f -name opencode | sort | head -n 1)"
  [[ -n "${executable}" ]] || { echo "OpenCode archive does not contain the opencode executable" >&2; exit 1; }
  stage="${work}/stage"
  mkdir -p "${stage}/opencode/bin"
  cp -a "${executable}" "${stage}/opencode/bin/opencode"
  chmod 0755 "${stage}/opencode/bin/opencode"
  if [[ -f "${ROOT_DIR}/opencode-source/opencode-1.18.4/LICENSE" ]]; then
    cp "${ROOT_DIR}/opencode-source/opencode-1.18.4/LICENSE" "${stage}/opencode/LICENSE"
  fi
  archive_create_tar_gz "${output}" "${stage}" opencode
}

require_command openssl
require_command curl
require_command tar
require_command unzip
require_command find
[[ -n "${SIGNING_KEY}" && -f "${SIGNING_KEY}" ]] || {
  echo "TEST_AGENT_LOCAL_CLIENT_SIGNING_KEY or --signing-key is required" >&2
  exit 1
}

if [[ "${SKIP_BUILD}" -eq 0 && -z "${CLIENT_JAR}" ]]; then
  require_command mvn
  (cd "${ROOT_DIR}/backend" && mvn -q -pl test-agent-local-client -am -DskipTests package)
  CLIENT_JAR="${ROOT_DIR}/backend/test-agent-local-client/target/test-agent-local-client.jar"
fi
[[ -n "${CLIENT_JAR}" && -f "${CLIENT_JAR}" ]] || {
  echo "Local client shaded JAR not found; provide --client-jar or allow Maven build" >&2
  exit 1
}

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-local-client-package.XXXXXX")"
trap 'rm -rf "${TEMP_DIR}"' EXIT
SOURCE_DIR="${TEMP_DIR}/sources"
mkdir -p "${SOURCE_DIR}"
fetch_source "${JRE_DARWIN_ARCHIVE}" "${JRE_DARWIN_URL}" "${JRE_DARWIN_SHA}" "${SOURCE_DIR}/jre-darwin.tar.gz"
fetch_source "${JRE_LINUX_ARCHIVE}" "${JRE_LINUX_URL}" "${JRE_LINUX_SHA}" "${SOURCE_DIR}/jre-linux.tar.gz"
fetch_source "${OPENCODE_DARWIN_ARCHIVE}" "${OPENCODE_DARWIN_URL}" "${OPENCODE_DARWIN_SHA}" "${SOURCE_DIR}/opencode-darwin.zip"
fetch_source "${OPENCODE_LINUX_ARCHIVE}" "${OPENCODE_LINUX_URL}" "${OPENCODE_LINUX_SHA}" "${SOURCE_DIR}/opencode-linux.tar.gz"

RELEASE_DIR="${OUTPUT_DIR}/releases/${VERSION}"
STABLE_DIR="${OUTPUT_DIR}/stable"
rm -rf "${OUTPUT_DIR}"
mkdir -p "${RELEASE_DIR}" "${STABLE_DIR}"
cp "${CLIENT_JAR}" "${RELEASE_DIR}/test-agent-local-client.jar"
normalize_jre "${SOURCE_DIR}/jre-darwin.tar.gz" "${RELEASE_DIR}/temurin-jre21-darwin-arm64.tar.gz" "${TEMP_DIR}/jre-darwin"
normalize_jre "${SOURCE_DIR}/jre-linux.tar.gz" "${RELEASE_DIR}/temurin-jre21-linux-arm64-glibc.tar.gz" "${TEMP_DIR}/jre-linux"
normalize_opencode "${SOURCE_DIR}/opencode-darwin.zip" "${RELEASE_DIR}/opencode-1.18.4-darwin-arm64.tar.gz" "${TEMP_DIR}/opencode-darwin"
normalize_opencode "${SOURCE_DIR}/opencode-linux.tar.gz" "${RELEASE_DIR}/opencode-1.18.4-linux-arm64-glibc.tar.gz" "${TEMP_DIR}/opencode-linux"

CLIENT_SHA="$(sha256_file "${RELEASE_DIR}/test-agent-local-client.jar")"
JRE_DARWIN_OUTPUT_SHA="$(sha256_file "${RELEASE_DIR}/temurin-jre21-darwin-arm64.tar.gz")"
JRE_LINUX_OUTPUT_SHA="$(sha256_file "${RELEASE_DIR}/temurin-jre21-linux-arm64-glibc.tar.gz")"
OPENCODE_DARWIN_OUTPUT_SHA="$(sha256_file "${RELEASE_DIR}/opencode-1.18.4-darwin-arm64.tar.gz")"
OPENCODE_LINUX_OUTPUT_SHA="$(sha256_file "${RELEASE_DIR}/opencode-1.18.4-linux-arm64-glibc.tar.gz")"
MANIFEST="${STABLE_DIR}/manifest.json"
{
  printf '{\n'
  printf '  "schemaVersion": 1,\n'
  printf '  "protocolVersion": "local-opencode-client.v1",\n'
  printf '  "version": "%s",\n' "${VERSION}"
  printf '  "clientJarPath": "releases/%s/test-agent-local-client.jar",\n' "${VERSION}"
  printf '  "clientJarSha256": "%s",\n' "${CLIENT_SHA}"
  printf '  "darwinArm64JrePath": "releases/%s/temurin-jre21-darwin-arm64.tar.gz",\n' "${VERSION}"
  printf '  "darwinArm64JreSha256": "%s",\n' "${JRE_DARWIN_OUTPUT_SHA}"
  printf '  "darwinArm64OpencodePath": "releases/%s/opencode-1.18.4-darwin-arm64.tar.gz",\n' "${VERSION}"
  printf '  "darwinArm64OpencodeSha256": "%s",\n' "${OPENCODE_DARWIN_OUTPUT_SHA}"
  printf '  "linuxArm64GlibcJrePath": "releases/%s/temurin-jre21-linux-arm64-glibc.tar.gz",\n' "${VERSION}"
  printf '  "linuxArm64GlibcJreSha256": "%s",\n' "${JRE_LINUX_OUTPUT_SHA}"
  printf '  "linuxArm64GlibcOpencodePath": "releases/%s/opencode-1.18.4-linux-arm64-glibc.tar.gz",\n' "${VERSION}"
  printf '  "linuxArm64GlibcOpencodeSha256": "%s"\n' "${OPENCODE_LINUX_OUTPUT_SHA}"
  printf '}\n'
} >"${MANIFEST}"
openssl dgst -sha256 -sign "${SIGNING_KEY}" -out "${STABLE_DIR}/manifest.json.sig" "${MANIFEST}"

if [[ -n "${PUBLIC_KEY}" ]]; then
  [[ -f "${PUBLIC_KEY}" ]] || { echo "Signing public key not found: ${PUBLIC_KEY}" >&2; exit 1; }
  cp "${PUBLIC_KEY}" "${TEMP_DIR}/public.pem"
else
  openssl pkey -in "${SIGNING_KEY}" -pubout -out "${TEMP_DIR}/public.pem"
fi
openssl dgst -sha256 -verify "${TEMP_DIR}/public.pem" -signature "${STABLE_DIR}/manifest.json.sig" "${MANIFEST}" >/dev/null
PUBLIC_KEY_BASE64="$(openssl base64 -A -in "${TEMP_DIR}/public.pem")"
sed "s|__PUBLIC_KEY_BASE64__|${PUBLIC_KEY_BASE64}|" \
  "${SCRIPT_DIR}/local-opencode-client/install.sh.template" >"${OUTPUT_DIR}/install.sh"
chmod 0755 "${OUTPUT_DIR}/install.sh"

(
  cd "${RELEASE_DIR}"
  for artifact in test-agent-local-client.jar \
    temurin-jre21-darwin-arm64.tar.gz temurin-jre21-linux-arm64-glibc.tar.gz \
    opencode-1.18.4-darwin-arm64.tar.gz opencode-1.18.4-linux-arm64-glibc.tar.gz; do
    printf '%s  %s\n' "$(sha256_file "${artifact}")" "${artifact}"
  done >SHA256SUMS
)
archive_strip_file_metadata "${OUTPUT_DIR}/install.sh" "${STABLE_DIR}/manifest.json" \
  "${STABLE_DIR}/manifest.json.sig" "${RELEASE_DIR}"/*
printf 'Local client HTTP distribution: %s\n' "${OUTPUT_DIR}"
printf 'Release version: %s\n' "${VERSION}"
