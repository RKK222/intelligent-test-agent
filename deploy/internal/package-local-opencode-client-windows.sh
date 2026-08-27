#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=archive-common.sh
source "${SCRIPT_DIR}/archive-common.sh"

OUTPUT_DIR="${SCRIPT_DIR}/dist/local-opencode-client"
VERSION="${TEST_AGENT_LOCAL_CLIENT_VERSION:-}"
MINIMUM_VERSION="${TEST_AGENT_LOCAL_CLIENT_MINIMUM_VERSION:-}"
SIGNING_KEY="${TEST_AGENT_LOCAL_CLIENT_SIGNING_KEY:-}"
PUBLIC_KEY="${TEST_AGENT_LOCAL_CLIENT_SIGNING_PUBLIC_KEY:-}"
CLIENT_JAR="${TEST_AGENT_LOCAL_CLIENT_JAR:-}"
DOWNLOAD_BASE_URL="${TEST_AGENT_LOCAL_CLIENT_DOWNLOAD_BASE_URL:-}"
SERVER_URL="${TEST_AGENT_LOCAL_CLIENT_SERVER_URL:-}"
WEB_URL="${TEST_AGENT_LOCAL_CLIENT_WEB_URL:-}"
ALLOW_INSECURE_CONTROL="${TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL:-false}"
PUBLIC_CONFIG_COMMIT="${TEST_AGENT_LOCAL_CLIENT_PUBLIC_CONFIG_COMMIT:-}"
PUBLIC_CAPABILITY_BUNDLE="${TEST_AGENT_LOCAL_CLIENT_PUBLIC_CAPABILITY_BUNDLE:-}"
SKIP_BUILD=0

JDK_WINDOWS_URL="${TEST_AGENT_LOCAL_CLIENT_JDK_WINDOWS_X64_URL:-https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip}"
JDK_WINDOWS_SHA="${TEST_AGENT_LOCAL_CLIENT_JDK_WINDOWS_X64_SHA256:-f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e}"
JDK_WINDOWS_ARCHIVE="${TEST_AGENT_LOCAL_CLIENT_JDK_WINDOWS_X64_ARCHIVE:-}"
OPENCODE_WINDOWS_URL="${TEST_AGENT_LOCAL_CLIENT_OPENCODE_WINDOWS_X64_URL:-https://github.com/anomalyco/opencode/releases/download/v1.18.4/opencode-windows-x64-baseline.zip}"
OPENCODE_WINDOWS_SHA="${TEST_AGENT_LOCAL_CLIENT_OPENCODE_WINDOWS_X64_SHA256:-3bfb70c41d0278221d1fbc58efe77f79615491252498ff3f5a82db64266234e0}"
OPENCODE_WINDOWS_ARCHIVE="${TEST_AGENT_LOCAL_CLIENT_OPENCODE_WINDOWS_X64_ARCHIVE:-}"

usage() {
  cat <<'USAGE'
Usage: deploy/internal/package-local-opencode-client-windows.sh [options]

Build one signed Win10 x64 release and a user-level candidate ZIP. The PE files
are cross-compiled on Mac and therefore remain non-production until Authenticode
signing and a real Windows 10 acceptance run are completed.

Options:
  --output-dir <path>        Shared local-opencode-client distribution root.
  --version <yyyyMMddHHmmss> Immutable Beijing-time release version.
  --minimum-version <value>  Last deployed version; candidate must be greater.
  --download-base-url <url>  Shared Nginx HTTP distribution root.
  --server-url <url>         Platform control root.
  --web-url <url>            Browser root; defaults to server-url.
  --allow-insecure-control <true|false>
  --signing-key <path>       Existing organization RSA private key.
  --public-key <path>        Matching public key; derived when omitted.
  --client-jar <path>        Prebuilt managed local-client JAR.
  --public-config-commit <id>
  --public-capability-bundle <path>
  --skip-build               Reuse --client-jar instead of Maven.
  -h, --help                 Show help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    --version) VERSION="$2"; shift 2 ;;
    --minimum-version) MINIMUM_VERSION="$2"; shift 2 ;;
    --download-base-url) DOWNLOAD_BASE_URL="$2"; shift 2 ;;
    --server-url) SERVER_URL="$2"; shift 2 ;;
    --web-url) WEB_URL="$2"; shift 2 ;;
    --allow-insecure-control) ALLOW_INSECURE_CONTROL="$2"; shift 2 ;;
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

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 1; }
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

file_size() {
  wc -c <"$1" | tr -d '[:space:]'
}

fetch_source() {
  local supplied_path="$1" url="$2" expected_sha="$3" destination="$4" actual_sha
  if [[ -n "${supplied_path}" ]]; then
    [[ -f "${supplied_path}" && ! -L "${supplied_path}" ]] || {
      echo "Source archive not found or is a symlink: ${supplied_path}" >&2
      exit 1
    }
    cp "${supplied_path}" "${destination}"
  else
    curl -fL --retry 3 --proto '=https' "${url}" -o "${destination}"
  fi
  actual_sha="$(sha256_file "${destination}")"
  [[ "${actual_sha}" == "${expected_sha}" ]] || {
    echo "Source SHA-256 mismatch: expected=${expected_sha} actual=${actual_sha}" >&2
    exit 1
  }
}

normalize_runtime_metadata() {
  find "$1" -exec touch -h -t 198001010000.00 {} +
}

archive_create_runtime_tar_gz() {
  local output="$1" base_dir="$2" value output_parent
  shift 2
  local -a metadata_flags=() owner_flags=() exclude_flags=()
  output_parent="$(cd "$(dirname "${output}")" && pwd -P)"
  output="${output_parent}/$(basename "${output}")"
  while IFS= read -r value; do
    [[ -z "${value}" ]] || metadata_flags+=("${value}")
  done < <(archive_tar_metadata_flags)
  for value in "${TEST_AGENT_ARCHIVE_METADATA_EXCLUDES[@]}"; do
    exclude_flags+=("--exclude=${value}")
  done
  if tar --version 2>&1 | grep -qi 'bsdtar'; then
    owner_flags=(--uid 0 --gid 0 --uname root --gname root)
  else
    owner_flags=(--owner=0 --group=0 --numeric-owner)
  fi
  (
    cd "${base_dir}"
    find "$@" -print | LC_ALL=C sort | \
      COPYFILE_DISABLE=1 COPY_EXTENDED_ATTRIBUTES_DISABLE=1 \
      tar "${metadata_flags[@]}" "${exclude_flags[@]}" "${owner_flags[@]}" \
        --no-recursion --format ustar -cf - -T - | gzip -n >"${output}"
  )
  archive_strip_file_metadata "${output}"
}

normalize_windows_jdk() {
  local archive="$1" output="$2" work="$3" java_binary java_home stage
  mkdir -p "${work}/extract"
  unzip -q "${archive}" -d "${work}/extract"
  java_binary="$(find "${work}/extract" -type f -iname java.exe | sort | head -n 1)"
  [[ -n "${java_binary}" ]] || { echo "Windows JDK archive lacks java.exe" >&2; exit 1; }
  java_home="$(cd "$(dirname "${java_binary}")/.." && pwd -P)"
  [[ -f "${java_home}/bin/javac.exe" ]] || { echo "Windows JDK archive lacks javac.exe" >&2; exit 1; }
  [[ -f "${java_home}/bin/javaw.exe" ]] || { echo "Windows JDK archive lacks javaw.exe" >&2; exit 1; }
  stage="${work}/stage"
  mkdir -p "${stage}/jdk"
  cp -a "${java_home}/." "${stage}/jdk/"
  chmod 0755 "${stage}/jdk/bin/java.exe" "${stage}/jdk/bin/javaw.exe" "${stage}/jdk/bin/javac.exe"
  normalize_runtime_metadata "${stage}/jdk"
  archive_create_runtime_tar_gz "${output}" "${stage}" jdk
}

normalize_windows_opencode() {
  local archive="$1" output="$2" work="$3" executable stage
  mkdir -p "${work}/extract"
  unzip -q "${archive}" -d "${work}/extract"
  executable="$(find "${work}/extract" -type f -iname opencode.exe | sort | head -n 1)"
  [[ -n "${executable}" ]] || { echo "OpenCode archive lacks opencode.exe" >&2; exit 1; }
  stage="${work}/stage"
  mkdir -p "${stage}/opencode/bin" "${stage}/opencode/plugins"
  cp "${executable}" "${stage}/opencode/bin/opencode.exe"
  chmod 0755 "${stage}/opencode/bin/opencode.exe"
  cp "${ROOT_DIR}/deploy/internal/opencode-observability-plugin.mjs" \
    "${stage}/opencode/plugins/test-agent-observability.mjs"
  if [[ -f "${ROOT_DIR}/opencode-source/opencode-1.18.4/LICENSE" ]]; then
    cp "${ROOT_DIR}/opencode-source/opencode-1.18.4/LICENSE" "${stage}/opencode/LICENSE"
  fi
  chmod 0644 "${stage}/opencode/plugins/test-agent-observability.mjs"
  normalize_runtime_metadata "${stage}/opencode"
  archive_create_runtime_tar_gz "${output}" "${stage}" opencode
}

if [[ -z "${VERSION}" ]]; then
  VERSION="$(TZ=Asia/Shanghai date +%Y%m%d%H%M%S)"
fi
[[ "${VERSION}" =~ ^[0-9]{14}$ ]] || { echo "Version must be a 14-digit Beijing timestamp" >&2; exit 2; }
case "${OUTPUT_DIR}" in
  ''|'/'|"${HOME}"|"${ROOT_DIR}"|"${SCRIPT_DIR}") echo "Unsafe output directory: ${OUTPUT_DIR}" >&2; exit 2 ;;
esac
[[ "$(basename "${OUTPUT_DIR}")" == local-opencode-client ]] || {
  echo "Output directory must end in local-opencode-client" >&2
  exit 2
}
mkdir -p "${OUTPUT_DIR}"
# archive_create_zip 会切换到 staging 目录执行，输出必须先固定为绝对路径。
OUTPUT_DIR="$(cd "${OUTPUT_DIR}" && pwd -P)"
PREVIOUS_VERSION="${MINIMUM_VERSION}"
if [[ -n "${PREVIOUS_VERSION}" && ! "${PREVIOUS_VERSION}" =~ ^[0-9]{14}$ ]]; then
  echo "Minimum version must be a 14-digit timestamp" >&2
  exit 2
fi
for existing_release in "${OUTPUT_DIR}"/releases/*; do
  [[ -d "${existing_release}" ]] || continue
  existing_version="$(basename "${existing_release}")"
  [[ "${existing_version}" =~ ^[0-9]{14}$ ]] || continue
  if [[ -z "${PREVIOUS_VERSION}" || "${existing_version}" > "${PREVIOUS_VERSION}" ]]; then
    PREVIOUS_VERSION="${existing_version}"
  fi
done
if [[ -n "${PREVIOUS_VERSION}" && ! "${VERSION}" > "${PREVIOUS_VERSION}" ]]; then
  echo "Release version must exceed existing/deployed version ${PREVIOUS_VERSION}" >&2
  exit 2
fi

DOWNLOAD_BASE_URL="${DOWNLOAD_BASE_URL%/}/"
SERVER_URL="${SERVER_URL%/}"
WEB_URL="${WEB_URL:-${SERVER_URL}}"
WEB_URL="${WEB_URL%/}"
[[ "${DOWNLOAD_BASE_URL}" =~ ^http://[A-Za-z0-9._:-]+(/[A-Za-z0-9._/-]*)?/$ ]] || {
  echo "Download base URL must be a canonical internal HTTP root" >&2
  exit 2
}
[[ "${ALLOW_INSECURE_CONTROL}" == true || "${ALLOW_INSECURE_CONTROL}" == false ]] || {
  echo "allow-insecure-control must be true or false" >&2
  exit 2
}
if [[ "${ALLOW_INSECURE_CONTROL}" == true ]]; then
  control_pattern='^https?://[A-Za-z0-9._:-]+(/[A-Za-z0-9._/-]*)?$'
else
  control_pattern='^https://[A-Za-z0-9._:-]+(/[A-Za-z0-9._/-]*)?$'
fi
[[ "${SERVER_URL}" =~ ${control_pattern} && "${WEB_URL}" =~ ${control_pattern} ]] || {
  echo "Server/web URL scheme is incompatible with allow-insecure-control" >&2
  exit 2
}

for command_name in openssl curl unzip tar find touch gzip sort jq go zip; do
  require_command "${command_name}"
done
[[ -n "${SIGNING_KEY}" && -f "${SIGNING_KEY}" && ! -L "${SIGNING_KEY}" ]] || {
  echo "Organization signing private key is required" >&2
  exit 1
}
[[ "${PUBLIC_CONFIG_COMMIT}" =~ ^[0-9a-fA-F]{40,64}$ ]] || {
  echo "public-config-commit must be a fixed Git commit" >&2
  exit 1
}
PUBLIC_CONFIG_COMMIT="$(printf '%s' "${PUBLIC_CONFIG_COMMIT}" | tr '[:upper:]' '[:lower:]')"
[[ -n "${PUBLIC_CAPABILITY_BUNDLE}" && -f "${PUBLIC_CAPABILITY_BUNDLE}" && ! -L "${PUBLIC_CAPABILITY_BUNDLE}" ]] || {
  echo "public-capability-bundle is required" >&2
  exit 1
}
BUNDLE_COMMIT="$(tar -xOzf "${PUBLIC_CAPABILITY_BUNDLE}" public-capabilities/manifest.json | jq -er '.sourceCommit')"
[[ "${BUNDLE_COMMIT}" == "${PUBLIC_CONFIG_COMMIT}" ]] || {
  echo "Public capability bundle commit mismatch" >&2
  exit 1
}

RELEASE_DIR="${OUTPUT_DIR}/releases/${VERSION}"
[[ ! -e "${RELEASE_DIR}" ]] || { echo "Immutable release already exists: ${RELEASE_DIR}" >&2; exit 1; }
if [[ "${SKIP_BUILD}" -eq 0 && -z "${CLIENT_JAR}" ]]; then
  require_command mvn
  (cd "${ROOT_DIR}/backend" && mvn -q -pl test-agent-local-client -am \
    -DskipTests -Dtest.agent.local.client.version="${VERSION}" package)
  CLIENT_JAR="${ROOT_DIR}/backend/test-agent-local-client/target/test-agent-local-client.jar"
fi
[[ -n "${CLIENT_JAR}" && -f "${CLIENT_JAR}" ]] || {
  echo "Managed local-client JAR not found" >&2
  exit 1
}

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-local-client-windows.XXXXXX")"
cleanup() {
  case "${TEMP_DIR}" in
    */test-agent-local-client-windows.*) rm -rf "${TEMP_DIR}" ;;
    *) echo "Refusing unsafe temporary cleanup: ${TEMP_DIR}" >&2 ;;
  esac
}
trap cleanup EXIT
mkdir -p "${TEMP_DIR}/sources" "${OUTPUT_DIR}/releases"
fetch_source "${JDK_WINDOWS_ARCHIVE}" "${JDK_WINDOWS_URL}" "${JDK_WINDOWS_SHA}" "${TEMP_DIR}/sources/jdk-windows.zip"
fetch_source "${OPENCODE_WINDOWS_ARCHIVE}" "${OPENCODE_WINDOWS_URL}" "${OPENCODE_WINDOWS_SHA}" "${TEMP_DIR}/sources/opencode-windows.zip"

STAGING_DIR="${OUTPUT_DIR}/releases/.${VERSION}.windows-build.$$"
mkdir -p "${STAGING_DIR}"
cp "${CLIENT_JAR}" "${STAGING_DIR}/test-agent-local-client.jar"
cp "${PUBLIC_CAPABILITY_BUNDLE}" "${STAGING_DIR}/public-capabilities.tar.gz"
normalize_windows_jdk "${TEMP_DIR}/sources/jdk-windows.zip" "${STAGING_DIR}/jdk.tar.gz" "${TEMP_DIR}/jdk-windows"
normalize_windows_opencode "${TEMP_DIR}/sources/opencode-windows.zip" "${STAGING_DIR}/opencode.tar.gz" "${TEMP_DIR}/opencode-windows"

for artifact in test-agent-local-client.jar jdk.tar.gz opencode.tar.gz public-capabilities.tar.gz; do
  openssl dgst -sha256 -sign "${SIGNING_KEY}" -out "${STAGING_DIR}/${artifact}.sig" "${STAGING_DIR}/${artifact}"
done
PUBLISHED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
MANIFEST="${STAGING_DIR}/manifest.json"
jq -n \
  --arg version "${VERSION}" --arg publishedAt "${PUBLISHED_AT}" \
  --arg clientSha "$(sha256_file "${STAGING_DIR}/test-agent-local-client.jar")" \
  --arg jdkSha "$(sha256_file "${STAGING_DIR}/jdk.tar.gz")" \
  --arg opencodeSha "$(sha256_file "${STAGING_DIR}/opencode.tar.gz")" \
  --arg capabilitySha "$(sha256_file "${STAGING_DIR}/public-capabilities.tar.gz")" \
  --argjson clientSize "$(file_size "${STAGING_DIR}/test-agent-local-client.jar")" \
  --argjson jdkSize "$(file_size "${STAGING_DIR}/jdk.tar.gz")" \
  --argjson opencodeSize "$(file_size "${STAGING_DIR}/opencode.tar.gz")" \
  --argjson capabilitySize "$(file_size "${STAGING_DIR}/public-capabilities.tar.gz")" \
  '{schemaVersion:2,version:$version,publishedAt:$publishedAt,platform:"windows",architecture:"x64",launcherVersionMin:1,launcherVersionMax:1,protocolVersion:"local-opencode-client.v1",opencodeVersion:"1.18.4",artifacts:[
    {kind:"CLIENT_JAR",path:("releases/"+$version+"/test-agent-local-client.jar"),size:$clientSize,sha256:$clientSha,signaturePath:("releases/"+$version+"/test-agent-local-client.jar.sig")},
    {kind:"JDK",path:("releases/"+$version+"/jdk.tar.gz"),size:$jdkSize,sha256:$jdkSha,signaturePath:("releases/"+$version+"/jdk.tar.gz.sig")},
    {kind:"OPENCODE",path:("releases/"+$version+"/opencode.tar.gz"),size:$opencodeSize,sha256:$opencodeSha,signaturePath:("releases/"+$version+"/opencode.tar.gz.sig")},
    {kind:"PUBLIC_CAPABILITIES",path:("releases/"+$version+"/public-capabilities.tar.gz"),size:$capabilitySize,sha256:$capabilitySha,signaturePath:("releases/"+$version+"/public-capabilities.tar.gz.sig")}
  ]}' >"${MANIFEST}"
openssl dgst -sha256 -sign "${SIGNING_KEY}" -out "${STAGING_DIR}/manifest.json.sig" "${MANIFEST}"

if [[ -n "${PUBLIC_KEY}" ]]; then
  [[ -f "${PUBLIC_KEY}" && ! -L "${PUBLIC_KEY}" ]] || { echo "Public key not found" >&2; exit 1; }
  cp "${PUBLIC_KEY}" "${TEMP_DIR}/public.pem"
else
  openssl pkey -in "${SIGNING_KEY}" -pubout -out "${TEMP_DIR}/public.pem"
fi
openssl dgst -sha256 -verify "${TEMP_DIR}/public.pem" -signature "${STAGING_DIR}/manifest.json.sig" "${MANIFEST}" >/dev/null
mv "${STAGING_DIR}" "${RELEASE_DIR}"

# 共享 catalog 继续使用全局唯一时间戳版本；每个平台启动器自行筛选 platform/architecture。
VERSION_LIST="${TEMP_DIR}/versions.txt"
for existing_release in "${OUTPUT_DIR}"/releases/*; do
  [[ -d "${existing_release}" ]] || continue
  existing_version="$(basename "${existing_release}")"
  [[ "${existing_version}" =~ ^[0-9]{14}$ ]] || continue
  openssl dgst -sha256 -verify "${TEMP_DIR}/public.pem" \
    -signature "${existing_release}/manifest.json.sig" "${existing_release}/manifest.json" >/dev/null || {
      echo "Existing release uses another key or is damaged: ${existing_release}" >&2
      exit 1
    }
  printf '%s\n' "${existing_version}" >>"${VERSION_LIST}"
done
sort -u "${VERSION_LIST}" -o "${VERSION_LIST}"
CATALOG_TEMP="${TEMP_DIR}/catalog.json"
jq -n --argjson releases "$(while IFS= read -r catalog_version; do
  manifest_path="${OUTPUT_DIR}/releases/${catalog_version}/manifest.json"
  jq -n --arg version "${catalog_version}" --arg sha "$(sha256_file "${manifest_path}")" \
    '{version:$version,manifestPath:("releases/"+$version+"/manifest.json"),manifestSha256:$sha,manifestSignaturePath:("releases/"+$version+"/manifest.json.sig")}'
done <"${VERSION_LIST}" | jq -s '.')" '{schemaVersion:1,releases:$releases}' >"${CATALOG_TEMP}"
openssl dgst -sha256 -sign "${SIGNING_KEY}" -out "${TEMP_DIR}/catalog.json.sig" "${CATALOG_TEMP}"
mv "${TEMP_DIR}/catalog.json.sig" "${OUTPUT_DIR}/catalog.json.sig"
mv "${CATALOG_TEMP}" "${OUTPUT_DIR}/catalog.json"

PUBLIC_KEY_DER_BASE64="$(openssl pkey -pubin -in "${TEMP_DIR}/public.pem" -outform DER | openssl base64 -A)"
LAUNCHER_SOURCE="./deploy/internal/local-opencode-client/windows-launcher"
STABLE_EXE="${TEMP_DIR}/TestAgent-Local-Client.exe"
COMMON_LDFLAGS="-s -w -X main.releaseVersion=${VERSION} -X main.serverURL=${SERVER_URL} -X main.webURL=${WEB_URL} -X main.downloadBaseURL=${DOWNLOAD_BASE_URL} -X main.allowInsecureControl=${ALLOW_INSECURE_CONTROL} -X main.publicKeyDERBase64=${PUBLIC_KEY_DER_BASE64}"
(
  cd "${ROOT_DIR}"
  GOOS=windows GOARCH=amd64 CGO_ENABLED=0 GO111MODULE=off \
    go build -trimpath -ldflags "${COMMON_LDFLAGS} -H windowsgui" -o "${STABLE_EXE}" "${LAUNCHER_SOURCE}"
)
STABLE_SHA="$(sha256_file "${STABLE_EXE}")"
SETUP_EXE="${TEMP_DIR}/TestAgent-Local-Client-Setup.exe"
(
  cd "${ROOT_DIR}"
  GOOS=windows GOARCH=amd64 CGO_ENABLED=0 GO111MODULE=off \
    go build -trimpath -ldflags "${COMMON_LDFLAGS} -X main.expectedStableSHA256=${STABLE_SHA}" \
      -o "${SETUP_EXE}" "${LAUNCHER_SOURCE}"
)

PACKAGE_NAME="TestAgent-Local-Client-Win10-x64"
PACKAGE_ROOT="${TEMP_DIR}/package/${PACKAGE_NAME}"
mkdir -p "${PACKAGE_ROOT}/resources"
cp "${SETUP_EXE}" "${PACKAGE_ROOT}/TestAgent-Local-Client-Setup.exe"
cp "${STABLE_EXE}" "${PACKAGE_ROOT}/resources/TestAgent-Local-Client.exe"
cp "${RELEASE_DIR}"/* "${PACKAGE_ROOT}/resources/"
{
  printf '%s\n' \
    'TestAgent 本地客户端（Windows 10 x64 候选安装包）' \
    '' \
    '1. 完整解压 ZIP。' \
    '2. 双击 TestAgent-Local-Client-Setup.exe。' \
    '3. 按提示输入统一认证号和 Client Key。' \
    '' \
    '最低系统：Windows 10 1809（build 17763），x64。' \
    '安装范围：当前用户 AppData；不需要管理员权限，不依赖系统 Java。' \
    '卸载前请先在任务计划程序停止 “TestAgent Local Client”。' \
    '' \
    '重要：本包由 macOS 交叉编译，尚未完成 Authenticode 签名和 Win10 真机验收，不能作为企业正式发布包。' \
    "发布版本：${VERSION}"
} >"${PACKAGE_ROOT}/README.txt"
PACKAGE_PATH="${OUTPUT_DIR}/TestAgent-Local-Client-Win10-x64-${VERSION}-unsigned.zip"
ALIAS_PATH="${OUTPUT_DIR}/TestAgent-Local-Client-Win10-x64-unsigned.zip"
rm -f "${PACKAGE_PATH}" "${ALIAS_PATH}"
archive_create_zip "${PACKAGE_PATH}" "${TEMP_DIR}/package" "${PACKAGE_NAME}"
cp "${PACKAGE_PATH}" "${ALIAS_PATH}"
unzip -tq "${PACKAGE_PATH}" >/dev/null

jq -n --arg version "${VERSION}" --arg package "$(basename "${PACKAGE_PATH}")" \
  --arg packageSha256 "$(sha256_file "${PACKAGE_PATH}")" \
  --arg setupSha256 "$(sha256_file "${SETUP_EXE}")" --arg launcherSha256 "${STABLE_SHA}" \
  '{schemaVersion:1,version:$version,platform:"windows",architecture:"x64",minimumWindowsBuild:17763,authenticodeSigned:false,package:$package,packageSha256:$packageSha256,setupSha256:$setupSha256,launcherSha256:$launcherSha256,status:"CANDIDATE_ONLY"}' \
  >"${OUTPUT_DIR}/windows-x64-package-evidence.json"
archive_strip_file_metadata "${OUTPUT_DIR}/catalog.json" "${OUTPUT_DIR}/catalog.json.sig" \
  "${RELEASE_DIR}"/* "${PACKAGE_PATH}" "${ALIAS_PATH}" "${OUTPUT_DIR}/windows-x64-package-evidence.json"

printf 'Win10 x64 signed release: %s\n' "${RELEASE_DIR}"
printf 'Win10 x64 unsigned candidate package: %s\n' "${PACKAGE_PATH}"
printf 'Candidate SHA-256: %s\n' "$(sha256_file "${PACKAGE_PATH}")"
printf 'Status: CANDIDATE_ONLY (requires Authenticode and Windows 10 acceptance)\n'
