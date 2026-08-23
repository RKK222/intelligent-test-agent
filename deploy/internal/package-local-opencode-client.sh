#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=archive-common.sh
source "${SCRIPT_DIR}/archive-common.sh"

OUTPUT_DIR="${SCRIPT_DIR}/dist/local-opencode-client"
VERSION="${TEST_AGENT_LOCAL_CLIENT_VERSION:-}"
SIGNING_KEY="${TEST_AGENT_LOCAL_CLIENT_SIGNING_KEY:-}"
PUBLIC_KEY="${TEST_AGENT_LOCAL_CLIENT_SIGNING_PUBLIC_KEY:-}"
CLIENT_JAR="${TEST_AGENT_LOCAL_CLIENT_JAR:-}"
DOWNLOAD_BASE_URL="${TEST_AGENT_LOCAL_CLIENT_DOWNLOAD_BASE_URL:-}"
SERVER_URL="${TEST_AGENT_LOCAL_CLIENT_SERVER_URL:-}"
PUBLIC_CONFIG_COMMIT="${TEST_AGENT_LOCAL_CLIENT_PUBLIC_CONFIG_COMMIT:-}"
PUBLIC_CAPABILITY_BUNDLE="${TEST_AGENT_LOCAL_CLIENT_PUBLIC_CAPABILITY_BUNDLE:-}"
SKIP_BUILD=0

JDK_LINUX_URL="${TEST_AGENT_LOCAL_CLIENT_JDK_LINUX_ARM64_GLIBC_URL:-https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.9%2B10/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.9_10.tar.gz}"
JDK_LINUX_SHA="${TEST_AGENT_LOCAL_CLIENT_JDK_LINUX_ARM64_GLIBC_SHA256:-edf0da4debe7cf475dbe320d174d6eed81479eb363f41e38a2efb740428c603a}"
JDK_LINUX_ARCHIVE="${TEST_AGENT_LOCAL_CLIENT_JDK_LINUX_ARM64_GLIBC_ARCHIVE:-}"
OPENCODE_LINUX_URL="${TEST_AGENT_LOCAL_CLIENT_OPENCODE_LINUX_ARM64_GLIBC_URL:-https://github.com/anomalyco/opencode/releases/download/v1.18.4/opencode-linux-arm64.tar.gz}"
OPENCODE_LINUX_SHA="${TEST_AGENT_LOCAL_CLIENT_OPENCODE_LINUX_ARM64_GLIBC_SHA256:-eba87efba3976d533a24cca0316f8ef375b5f8e797c0a95c25ee919700b7ba35}"
OPENCODE_LINUX_ARCHIVE="${TEST_AGENT_LOCAL_CLIENT_OPENCODE_LINUX_ARM64_GLIBC_ARCHIVE:-}"

usage() {
  cat <<'USAGE'
Usage: deploy/internal/package-local-opencode-client.sh [options]

Build one immutable Kylin ARM64/glibc local-client release, its signed catalog,
and a DEB whose data payload contains only the stable launcher shell.

Options:
  --output-dir <path>        Distribution root ending in local-opencode-client.
  --version <yyyyMMddHHmmss> Immutable Beijing-time release version; generated when omitted.
  --download-base-url <url>  Nginx HTTP root embedded in the launcher.
  --server-url <url>         Platform HTTPS root embedded in client.properties.
  --signing-key <path>       PEM private key; never copied to the distribution.
  --public-key <path>        Matching PEM public key; derived when omitted.
  --client-jar <path>        Prebuilt shaded client JAR.
  --public-config-commit <id> Fixed public Git commit embedded as the first-run capability baseline.
  --public-capability-bundle <path> Complete public-capabilities.tar.gz generated for that commit.
  --skip-build               Do not invoke Maven; requires --client-jar.
  -h, --help                 Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    --version) VERSION="$2"; shift 2 ;;
    --download-base-url) DOWNLOAD_BASE_URL="$2"; shift 2 ;;
    --server-url) SERVER_URL="$2"; shift 2 ;;
    --signing-key) SIGNING_KEY="$2"; shift 2 ;;
    --public-key) PUBLIC_KEY="$2"; shift 2 ;;
    --client-jar) CLIENT_JAR="$2"; shift 2 ;;
    --public-config-commit) PUBLIC_CONFIG_COMMIT="$2"; shift 2 ;;
    --public-capability-bundle) PUBLIC_CAPABILITY_BUNDLE="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "${VERSION}" ]]; then
  VERSION="$(TZ=Asia/Shanghai date +%Y%m%d%H%M%S)"
fi
[[ "${VERSION}" =~ ^[0-9]{14}$ ]] || {
  echo "Local client version must be a 14-digit Beijing timestamp: ${VERSION}" >&2
  exit 2
}
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
DOWNLOAD_BASE_URL="${DOWNLOAD_BASE_URL%/}/"
[[ "${DOWNLOAD_BASE_URL}" =~ ^http://[A-Za-z0-9._:-]+(/[A-Za-z0-9._/-]*)?/$ ]] || {
  echo "Local client download base URL must be a canonical internal HTTP root" >&2
  exit 2
}
[[ "${SERVER_URL}" =~ ^https://[A-Za-z0-9._:-]+(/[A-Za-z0-9._/-]*)?$ ]] || {
  echo "Local client platform server URL must use canonical HTTPS" >&2
  exit 2
}
SERVER_URL="${SERVER_URL%/}"

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

file_size() {
  wc -c <"$1" | tr -d '[:space:]'
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

normalize_jdk() {
  local archive="$1" output="$2" work="$3" java_binary java_home stage
  mkdir -p "${work}/extract"
  tar -C "${work}/extract" -xzf "${archive}"
  java_binary="$(find "${work}/extract" -type f -path '*/bin/java' | sort | head -n 1)"
  [[ -n "${java_binary}" ]] || { echo "JDK archive does not contain bin/java" >&2; exit 1; }
  java_home="$(cd "$(dirname "${java_binary}")/.." && pwd)"
  [[ -x "${java_home}/bin/javac" ]] || { echo "JDK archive does not contain executable bin/javac" >&2; exit 1; }
  stage="${work}/stage"
  mkdir -p "${stage}/jdk"
  cp -a "${java_home}/." "${stage}/jdk/"
  chmod 0755 "${stage}/jdk/bin/java" "${stage}/jdk/bin/javac"
  archive_create_tar_gz "${output}" "${stage}" jdk
}

normalize_opencode() {
  local archive="$1" output="$2" work="$3" executable stage
  mkdir -p "${work}/extract"
  tar -C "${work}/extract" -xzf "${archive}"
  executable="$(find "${work}/extract" -type f -name opencode | sort | head -n 1)"
  [[ -n "${executable}" ]] || { echo "OpenCode archive does not contain the opencode executable" >&2; exit 1; }
  stage="${work}/stage"
  mkdir -p "${stage}/opencode/bin" "${stage}/opencode/plugins"
  cp -a "${executable}" "${stage}/opencode/bin/opencode"
  chmod 0755 "${stage}/opencode/bin/opencode"
  cp "${ROOT_DIR}/deploy/internal/opencode-observability-plugin.mjs" \
    "${stage}/opencode/plugins/test-agent-observability.mjs"
  chmod 0644 "${stage}/opencode/plugins/test-agent-observability.mjs"
  if [[ -f "${ROOT_DIR}/opencode-source/opencode-1.18.4/LICENSE" ]]; then
    cp "${ROOT_DIR}/opencode-source/opencode-1.18.4/LICENSE" "${stage}/opencode/LICENSE"
  fi
  archive_create_tar_gz "${output}" "${stage}" opencode
}

create_root_owned_tar_gz() {
  local output="$1" directory="$2"
  if tar --version 2>/dev/null | grep -qi bsdtar; then
    tar --uid 0 --gid 0 --uname root --gname root -C "${directory}" -czf "${output}" .
  else
    tar --owner=0 --group=0 --numeric-owner -C "${directory}" -czf "${output}" .
  fi
}

build_deb() {
  local launcher="$1" output="$2" work="$3"
  mkdir -p "${work}/control" "${work}/data/usr/bin"
  install -m 0755 "${launcher}" "${work}/data/usr/bin/test-agent-local-client"
  {
    printf 'Package: test-agent-local-client\n'
    printf 'Version: %s\n' "${VERSION}"
    printf 'Section: utils\nPriority: optional\nArchitecture: arm64\n'
    printf 'Maintainer: Test Agent Platform\n'
    printf 'Depends: curl, openssl, tar\n'
    printf 'Description: Test Agent Kylin ARM64 local OpenCode client launcher\n'
  } >"${work}/control/control"
  create_root_owned_tar_gz "${work}/control.tar.gz" "${work}/control"
  create_root_owned_tar_gz "${work}/data.tar.gz" "${work}/data"
  printf '2.0\n' >"${work}/debian-binary"
  rm -f "${output}"
  # DEB 是普通 ar 容器，不需要符号索引；Apple ar 若尝试 ranlib 会丢弃非 Mach-O 成员。
  (cd "${work}" && ar -rcS "${output}" debian-binary control.tar.gz data.tar.gz)
}

require_command openssl
require_command curl
require_command tar
require_command find
require_command ar
[[ -n "${SIGNING_KEY}" && -f "${SIGNING_KEY}" ]] || {
  echo "TEST_AGENT_LOCAL_CLIENT_SIGNING_KEY or --signing-key is required" >&2
  exit 1
}
[[ "${PUBLIC_CONFIG_COMMIT}" =~ ^[0-9a-fA-F]{40,64}$ ]] || {
  echo "--public-config-commit must be a fixed 40-64 character hexadecimal commit" >&2
  exit 1
}
PUBLIC_CONFIG_COMMIT="$(printf '%s' "${PUBLIC_CONFIG_COMMIT}" | tr '[:upper:]' '[:lower:]')"
[[ -n "${PUBLIC_CAPABILITY_BUNDLE}" && -f "${PUBLIC_CAPABILITY_BUNDLE}" ]] || {
  echo "--public-capability-bundle is required" >&2
  exit 1
}
require_command jq
BUNDLE_COMMIT="$(tar -xOzf "${PUBLIC_CAPABILITY_BUNDLE}" public-capabilities/manifest.json | jq -er '.sourceCommit')"
[[ "${BUNDLE_COMMIT}" == "${PUBLIC_CONFIG_COMMIT}" ]] || {
  echo "Public capability bundle commit mismatch: expected=${PUBLIC_CONFIG_COMMIT} actual=${BUNDLE_COMMIT}" >&2
  exit 1
}

RELEASE_DIR="${OUTPUT_DIR}/releases/${VERSION}"
[[ ! -e "${RELEASE_DIR}" ]] || {
  echo "Immutable local client release already exists: ${RELEASE_DIR}" >&2
  exit 1
}

if [[ "${SKIP_BUILD}" -eq 0 && -z "${CLIENT_JAR}" ]]; then
  require_command mvn
  (cd "${ROOT_DIR}/backend" && mvn -q -pl test-agent-local-client -am \
    -DskipTests -Dtest.agent.local.client.version="${VERSION}" package)
  CLIENT_JAR="${ROOT_DIR}/backend/test-agent-local-client/target/test-agent-local-client.jar"
fi
[[ -n "${CLIENT_JAR}" && -f "${CLIENT_JAR}" ]] || {
  echo "Local client shaded JAR not found; provide --client-jar or allow Maven build" >&2
  exit 1
}

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-local-client-package.XXXXXX")"
cleanup() {
  case "${TEMP_DIR}" in
    */test-agent-local-client-package.*) rm -rf "${TEMP_DIR}" ;;
    *) echo "Refusing unsafe temporary directory cleanup: ${TEMP_DIR}" >&2 ;;
  esac
}
trap cleanup EXIT
mkdir -p "${TEMP_DIR}/sources" "${OUTPUT_DIR}/releases"
fetch_source "${JDK_LINUX_ARCHIVE}" "${JDK_LINUX_URL}" "${JDK_LINUX_SHA}" "${TEMP_DIR}/sources/jdk-linux.tar.gz"
fetch_source "${OPENCODE_LINUX_ARCHIVE}" "${OPENCODE_LINUX_URL}" "${OPENCODE_LINUX_SHA}" "${TEMP_DIR}/sources/opencode-linux.tar.gz"

STAGING_DIR="${OUTPUT_DIR}/releases/.${VERSION}.build.$$"
mkdir -p "${STAGING_DIR}"
cp "${CLIENT_JAR}" "${STAGING_DIR}/test-agent-local-client.jar"
cp "${PUBLIC_CAPABILITY_BUNDLE}" "${STAGING_DIR}/public-capabilities.tar.gz"
normalize_jdk "${TEMP_DIR}/sources/jdk-linux.tar.gz" "${STAGING_DIR}/jdk.tar.gz" "${TEMP_DIR}/jdk-linux"
normalize_opencode "${TEMP_DIR}/sources/opencode-linux.tar.gz" "${STAGING_DIR}/opencode.tar.gz" "${TEMP_DIR}/opencode-linux"

for artifact in test-agent-local-client.jar jdk.tar.gz opencode.tar.gz public-capabilities.tar.gz; do
  openssl dgst -sha256 -sign "${SIGNING_KEY}" -out "${STAGING_DIR}/${artifact}.sig" "${STAGING_DIR}/${artifact}"
done
CLIENT_SHA="$(sha256_file "${STAGING_DIR}/test-agent-local-client.jar")"
JDK_SHA="$(sha256_file "${STAGING_DIR}/jdk.tar.gz")"
OPENCODE_SHA="$(sha256_file "${STAGING_DIR}/opencode.tar.gz")"
PUBLIC_CAPABILITY_SHA="$(sha256_file "${STAGING_DIR}/public-capabilities.tar.gz")"
PUBLISHED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
MANIFEST="${STAGING_DIR}/manifest.json"
{
  printf '{\n'
  printf '  "schemaVersion": 2,\n'
  printf '  "version": "%s",\n' "${VERSION}"
  printf '  "publishedAt": "%s",\n' "${PUBLISHED_AT}"
  printf '  "platform": "linux",\n'
  printf '  "architecture": "arm64",\n'
  printf '  "launcherVersionMin": 1,\n'
  printf '  "launcherVersionMax": 1,\n'
  printf '  "protocolVersion": "local-opencode-client.v1",\n'
  printf '  "opencodeVersion": "1.18.4",\n'
  printf '  "artifacts": [\n'
  printf '    {"kind": "CLIENT_JAR", "path": "releases/%s/test-agent-local-client.jar", "size": %s, "sha256": "%s", "signaturePath": "releases/%s/test-agent-local-client.jar.sig"},\n' "${VERSION}" "$(file_size "${STAGING_DIR}/test-agent-local-client.jar")" "${CLIENT_SHA}" "${VERSION}"
  printf '    {"kind": "JDK", "path": "releases/%s/jdk.tar.gz", "size": %s, "sha256": "%s", "signaturePath": "releases/%s/jdk.tar.gz.sig"},\n' "${VERSION}" "$(file_size "${STAGING_DIR}/jdk.tar.gz")" "${JDK_SHA}" "${VERSION}"
  printf '    {"kind": "OPENCODE", "path": "releases/%s/opencode.tar.gz", "size": %s, "sha256": "%s", "signaturePath": "releases/%s/opencode.tar.gz.sig"},\n' "${VERSION}" "$(file_size "${STAGING_DIR}/opencode.tar.gz")" "${OPENCODE_SHA}" "${VERSION}"
  printf '    {"kind": "PUBLIC_CAPABILITIES", "path": "releases/%s/public-capabilities.tar.gz", "size": %s, "sha256": "%s", "signaturePath": "releases/%s/public-capabilities.tar.gz.sig"}\n' "${VERSION}" "$(file_size "${STAGING_DIR}/public-capabilities.tar.gz")" "${PUBLIC_CAPABILITY_SHA}" "${VERSION}"
  printf '  ]\n}\n'
} >"${MANIFEST}"
openssl dgst -sha256 -sign "${SIGNING_KEY}" -out "${STAGING_DIR}/manifest.json.sig" "${MANIFEST}"

if [[ -n "${PUBLIC_KEY}" ]]; then
  [[ -f "${PUBLIC_KEY}" ]] || { echo "Signing public key not found: ${PUBLIC_KEY}" >&2; exit 1; }
  cp "${PUBLIC_KEY}" "${TEMP_DIR}/public.pem"
else
  openssl pkey -in "${SIGNING_KEY}" -pubout -out "${TEMP_DIR}/public.pem"
fi
openssl dgst -sha256 -verify "${TEMP_DIR}/public.pem" \
  -signature "${STAGING_DIR}/manifest.json.sig" "${MANIFEST}" >/dev/null
mv "${STAGING_DIR}" "${RELEASE_DIR}"

PUBLIC_KEY_PEM_BASE64="$(openssl base64 -A -in "${TEMP_DIR}/public.pem")"
PUBLIC_KEY_DER_BASE64="$(openssl pkey -pubin -in "${TEMP_DIR}/public.pem" -outform DER | openssl base64 -A)"
sed \
  -e "s|__PUBLIC_KEY_PEM_BASE64__|${PUBLIC_KEY_PEM_BASE64}|" \
  -e "s|__PUBLIC_KEY_DER_BASE64__|${PUBLIC_KEY_DER_BASE64}|" \
  -e "s|__DOWNLOAD_BASE_URL__|${DOWNLOAD_BASE_URL}|" \
  -e "s|__SERVER_URL__|${SERVER_URL}|" \
  "${SCRIPT_DIR}/local-opencode-client/install.sh.template" >"${OUTPUT_DIR}/install.sh"
chmod 0755 "${OUTPUT_DIR}/install.sh"

mkdir -p "${OUTPUT_DIR}/stable"
cp "${RELEASE_DIR}/manifest.json" "${OUTPUT_DIR}/stable/manifest.json"
cp "${RELEASE_DIR}/manifest.json.sig" "${OUTPUT_DIR}/stable/manifest.json.sig"

VERSION_LIST="${TEMP_DIR}/versions.txt"
for existing_release in "${OUTPUT_DIR}"/releases/*; do
  [[ -d "${existing_release}" ]] || continue
  existing_version="$(basename "${existing_release}")"
  [[ "${existing_version}" =~ ^[0-9]{14}$ ]] || continue
  [[ -f "${existing_release}/manifest.json" && -f "${existing_release}/manifest.json.sig" ]] || {
    echo "Existing release is incomplete: ${existing_release}" >&2
    exit 1
  }
  openssl dgst -sha256 -verify "${TEMP_DIR}/public.pem" \
    -signature "${existing_release}/manifest.json.sig" "${existing_release}/manifest.json" >/dev/null || {
      echo "Existing release manifest signature is invalid: ${existing_release}" >&2
      exit 1
    }
  printf '%s\n' "${existing_version}" >>"${VERSION_LIST}"
done
sort -u "${VERSION_LIST}" -o "${VERSION_LIST}"
CATALOG_TEMP="${TEMP_DIR}/catalog.json"
{
  printf '{\n  "schemaVersion": 1,\n  "releases": [\n'
  first=1
  while IFS= read -r catalog_version; do
    manifest_path="${OUTPUT_DIR}/releases/${catalog_version}/manifest.json"
    manifest_sha="$(sha256_file "${manifest_path}")"
    [[ "${first}" -eq 1 ]] || printf ',\n'
    printf '    {"version": "%s", "manifestPath": "releases/%s/manifest.json", "manifestSha256": "%s", "manifestSignaturePath": "releases/%s/manifest.json.sig"}' \
      "${catalog_version}" "${catalog_version}" "${manifest_sha}" "${catalog_version}"
    first=0
  done <"${VERSION_LIST}"
  printf '\n  ]\n}\n'
} >"${CATALOG_TEMP}"
openssl dgst -sha256 -sign "${SIGNING_KEY}" -out "${TEMP_DIR}/catalog.json.sig" "${CATALOG_TEMP}"
mv "${TEMP_DIR}/catalog.json.sig" "${OUTPUT_DIR}/catalog.json.sig"
mv "${CATALOG_TEMP}" "${OUTPUT_DIR}/catalog.json"

DEB_PATH="$(cd "${OUTPUT_DIR}" && pwd)/test-agent-local-client_${VERSION}_arm64.deb"
DEB_DOWNLOAD_ALIAS="${OUTPUT_DIR}/TestAgent-Local-Client-Kylin-arm64.deb"
build_deb "${OUTPUT_DIR}/install.sh" "${DEB_PATH}" "${TEMP_DIR}/deb"
cp "${DEB_PATH}" "${DEB_DOWNLOAD_ALIAS}"
archive_strip_file_metadata "${OUTPUT_DIR}/install.sh" "${OUTPUT_DIR}/catalog.json" \
  "${OUTPUT_DIR}/catalog.json.sig" "${OUTPUT_DIR}/stable/manifest.json" \
  "${OUTPUT_DIR}/stable/manifest.json.sig" "${RELEASE_DIR}"/* "${DEB_PATH}" \
  "${DEB_DOWNLOAD_ALIAS}"

printf 'Local client HTTP distribution: %s\n' "${OUTPUT_DIR}"
printf 'Release version: %s\n' "${VERSION}"
printf 'Kylin ARM64 DEB: %s\n' "${DEB_PATH}"
printf 'Kylin ARM64 download alias: %s\n' "${DEB_DOWNLOAD_ALIAS}"
