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
DEFAULT_SERVER_URL="${TEST_AGENT_LOCAL_CLIENT_DEFAULT_SERVER_URL:-}"
DEFAULT_WEB_URL="${TEST_AGENT_LOCAL_CLIENT_DEFAULT_WEB_URL:-${DEFAULT_SERVER_URL}}"
ALLOW_INSECURE_SETUP="${TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_SETUP:-false}"
MACOS_APPLICATION_IDENTITY="${TEST_AGENT_LOCAL_CLIENT_MACOS_APPLICATION_IDENTITY:-}"
MACOS_INSTALLER_IDENTITY="${TEST_AGENT_LOCAL_CLIENT_MACOS_INSTALLER_IDENTITY:-}"
MACOS_NOTARY_PROFILE="${TEST_AGENT_LOCAL_CLIENT_MACOS_NOTARY_PROFILE:-}"
DEB_BUILDER_IMAGE="${TEST_AGENT_LOCAL_CLIENT_DEB_BUILDER_IMAGE:-debian:bookworm-slim}"

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
ARM64 glibc Linux. The output includes double-clickable macOS PKG and Kylin DEB
installers. A signing private key is mandatory and is never copied into the
resulting artifacts.

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
documented in deploy/internal/env.example. Kylin DEB packaging uses the Linux
dpkg-deb builder image selected by TEST_AGENT_LOCAL_CLIENT_DEB_BUILDER_IMAGE.
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

validate_default_url() {
  local name="$1" value="$2"
  [[ -z "${value}" ]] && return
  case "${value}" in
    http://*|https://*) ;;
    *) echo "${name} must use http:// or https://" >&2; exit 2 ;;
  esac
  case "${value}" in
    *[!A-Za-z0-9._:/%+-]*) echo "${name} contains unsupported characters" >&2; exit 2 ;;
  esac
}

numeric_package_version() {
  if [[ "${VERSION}" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+) ]]; then
    printf '%s.%s.%s' "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}" "${BASH_REMATCH[3]}"
  else
    printf '0.1.0'
  fi
}

write_native_launcher() {
  local destination="$1" resource_root_expression="$2"
  cat >"${destination}" <<EOF
#!/usr/bin/env sh
set -eu
RESOURCE_ROOT=${resource_root_expression}
exec "\${RESOURCE_ROOT}/jre/bin/java" \
  "-Dtestagent.localclient.packagedOpencodeExecutable=\${RESOURCE_ROOT}/opencode/bin/opencode" \
  "-Dtestagent.localclient.defaultServerUrl=${DEFAULT_SERVER_URL}" \
  "-Dtestagent.localclient.defaultWebUrl=${DEFAULT_WEB_URL}" \
  "-Dtestagent.localclient.allowInsecureSetup=${ALLOW_INSECURE_SETUP}" \
  -jar "\${RESOURCE_ROOT}/test-agent-local-client.jar"
EOF
  chmod 0755 "${destination}"
}

build_macos_installer() {
  local release_dir="$1" destination="$2" package_version app_root payload_root scripts_root iconset component_plist unsigned_pkg
  [[ "$(uname -s)" == Darwin ]] || {
    echo "macOS PKG creation requires the documented external Mac build host" >&2
    exit 1
  }
  require_command pkgbuild
  require_command plutil
  require_command sips
  require_command iconutil
  package_version="$(numeric_package_version)"
  payload_root="${TEMP_DIR}/native-macos/payload"
  scripts_root="${TEMP_DIR}/native-macos/scripts"
  app_root="${payload_root}/Applications/TestAgent Local Client.app"
  mkdir -p "${app_root}/Contents/MacOS" "${app_root}/Contents/Resources" \
    "${payload_root}/Library/LaunchAgents" "${scripts_root}"
  tar -C "${app_root}/Contents/Resources" -xzf "${release_dir}/temurin-jre21-darwin-arm64.tar.gz"
  tar -C "${app_root}/Contents/Resources" -xzf "${release_dir}/opencode-1.18.4-darwin-arm64.tar.gz"
  cp "${release_dir}/test-agent-local-client.jar" "${app_root}/Contents/Resources/test-agent-local-client.jar"
  write_native_launcher \
    "${app_root}/Contents/MacOS/TestAgentLocalClient" \
    '"$(CDPATH= cd -- "$(dirname -- "$0")/../Resources" && pwd)"'
  cat >"${app_root}/Contents/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleDisplayName</key><string>TestAgent 本地客户端</string>
  <key>CFBundleExecutable</key><string>TestAgentLocalClient</string>
  <key>CFBundleIconFile</key><string>TestAgentLocalClient</string>
  <key>CFBundleIdentifier</key><string>com.enterprise.testagent.local-opencode-client</string>
  <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
  <key>CFBundleName</key><string>TestAgent Local Client</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>${package_version}</string>
  <key>CFBundleVersion</key><string>${package_version}</string>
  <key>LSMinimumSystemVersion</key><string>12.0</string>
</dict></plist>
EOF
  iconset="${TEMP_DIR}/native-macos/TestAgentLocalClient.iconset"
  mkdir -p "${iconset}"
  for specification in '16 icon_16x16.png' '32 icon_16x16@2x.png' \
    '32 icon_32x32.png' '64 icon_32x32@2x.png' '128 icon_128x128.png' \
    '256 icon_128x128@2x.png' '256 icon_256x256.png' '512 icon_256x256@2x.png' \
    '512 icon_512x512.png' '1024 icon_512x512@2x.png'; do
    set -- ${specification}
    sips -z "$1" "$1" "${ROOT_DIR}/frontend/apps/agent-web/src/assets/pets/radar-bunny.png" \
      --out "${iconset}/$2" >/dev/null
  done
  iconutil -c icns "${iconset}" -o "${app_root}/Contents/Resources/TestAgentLocalClient.icns"
  cat >"${payload_root}/Library/LaunchAgents/com.enterprise.testagent.local-opencode-client.plist" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>com.enterprise.testagent.local-opencode-client</string>
  <key>ProgramArguments</key><array><string>/Applications/TestAgent Local Client.app/Contents/MacOS/TestAgentLocalClient</string></array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><dict><key>SuccessfulExit</key><false/></dict>
  <key>LimitLoadToSessionType</key><string>Aqua</string>
</dict></plist>
EOF
  chmod 0644 "${payload_root}/Library/LaunchAgents/com.enterprise.testagent.local-opencode-client.plist"
  cat >"${scripts_root}/preinstall" <<'EOF'
#!/usr/bin/env sh
set -eu
target_volume="${3:-/}"
[ "${target_volume}" = / ] || exit 0
console_uid="$(/usr/bin/stat -f '%u' /dev/console 2>/dev/null || true)"
case "${console_uid}" in ''|*[!0-9]*) exit 0 ;; esac
[ "${console_uid}" -ge 500 ] || exit 0
/bin/launchctl bootout "gui/${console_uid}/com.enterprise.testagent.local-opencode-client" >/dev/null 2>&1 || true
EOF
  cat >"${scripts_root}/postinstall" <<'EOF'
#!/usr/bin/env sh
set -eu
target_volume="${3:-/}"
[ "${target_volume}" = / ] || exit 0
plist_path="/Library/LaunchAgents/com.enterprise.testagent.local-opencode-client.plist"
console_uid="$(/usr/bin/stat -f '%u' /dev/console 2>/dev/null || true)"
case "${console_uid}" in ''|*[!0-9]*) exit 0 ;; esac
[ "${console_uid}" -ge 500 ] || exit 0
console_user="$(/usr/bin/stat -f '%Su' /dev/console 2>/dev/null || true)"
case "${console_user}" in ''|root|loginwindow|*[!A-Za-z0-9._-]*) console_user='' ;; esac
if [ -n "${console_user}" ]; then
  console_home="$(/usr/bin/dscl . -read "/Users/${console_user}" NFSHomeDirectory 2>/dev/null \
    | /usr/bin/awk '{$1=""; sub(/^ /, ""); print}' || true)"
  case "${console_home}" in
    /*) /bin/rm -f "${console_home}/Library/LaunchAgents/com.enterprise.testagent.local-opencode-client.plist" ;;
  esac
fi
/bin/launchctl bootstrap "gui/${console_uid}" "${plist_path}" >/dev/null 2>&1 \
  </dev/null || true
EOF
  chmod 0755 "${scripts_root}/preinstall" "${scripts_root}/postinstall"
  if [[ -n "${MACOS_APPLICATION_IDENTITY}" ]]; then
    require_command codesign
    codesign --force --deep --options runtime --timestamp \
      --sign "${MACOS_APPLICATION_IDENTITY}" "${app_root}"
  fi
  # 禁止 Installer 根据历史 LaunchServices 记录把 App 重定位到用户目录，否则系统 LaunchAgent
  # 仍会访问 /Applications，最终出现安装成功但客户端无法启动的分裂状态。
  component_plist="${TEMP_DIR}/native-macos/components.plist"
  pkgbuild --analyze --root "${payload_root}" "${component_plist}" >/dev/null
  plutil -replace '0.BundleIsRelocatable' -bool NO "${component_plist}"
  plutil -replace '0.BundleHasStrictIdentifier' -bool YES "${component_plist}"
  unsigned_pkg="${TEMP_DIR}/native-macos/TestAgent-Local-Client-unsigned.pkg"
  pkgbuild --root "${payload_root}" --scripts "${scripts_root}" \
    --component-plist "${component_plist}" \
    --identifier com.enterprise.testagent.local-opencode-client \
    --version "${package_version}" --install-location / "${unsigned_pkg}" >/dev/null
  if [[ -n "${MACOS_INSTALLER_IDENTITY}" ]]; then
    require_command productsign
    productsign --sign "${MACOS_INSTALLER_IDENTITY}" --timestamp \
      "${unsigned_pkg}" "${destination}" >/dev/null
  else
    cp "${unsigned_pkg}" "${destination}"
    echo "WARNING: macOS PKG is unsigned; configure TEST_AGENT_LOCAL_CLIENT_MACOS_INSTALLER_IDENTITY for delivery" >&2
  fi
  if [[ -n "${MACOS_NOTARY_PROFILE}" ]]; then
    [[ -n "${MACOS_INSTALLER_IDENTITY}" ]] || {
      echo "macOS notarization requires TEST_AGENT_LOCAL_CLIENT_MACOS_INSTALLER_IDENTITY" >&2
      exit 1
    }
    xcrun notarytool submit "${destination}" --keychain-profile "${MACOS_NOTARY_PROFILE}" --wait
    xcrun stapler staple "${destination}"
  fi
}

build_kylin_installer() {
  local release_dir="$1" destination="$2" package_version package_root control_root work_root installed_size
  require_command docker
  destination="$(cd "$(dirname "${destination}")" && pwd)/$(basename "${destination}")"
  package_version="$(numeric_package_version)"
  work_root="${TEMP_DIR}/native-kylin"
  package_root="${work_root}/package"
  control_root="${package_root}/DEBIAN"
  mkdir -p "${package_root}/opt/testagent/local-opencode-client" \
    "${package_root}/usr/lib/systemd/user" \
    "${package_root}/usr/share/applications" \
    "${package_root}/usr/share/icons/hicolor/512x512/apps" \
    "${package_root}/etc/systemd/user/default.target.wants" \
    "${control_root}"
  tar -C "${package_root}/opt/testagent/local-opencode-client" \
    -xzf "${release_dir}/temurin-jre21-linux-arm64-glibc.tar.gz"
  tar -C "${package_root}/opt/testagent/local-opencode-client" \
    -xzf "${release_dir}/opencode-1.18.4-linux-arm64-glibc.tar.gz"
  cp "${release_dir}/test-agent-local-client.jar" \
    "${package_root}/opt/testagent/local-opencode-client/test-agent-local-client.jar"
  mkdir -p "${package_root}/opt/testagent/local-opencode-client/bin"
  write_native_launcher \
    "${package_root}/opt/testagent/local-opencode-client/bin/test-agent-local-client" \
    '"/opt/testagent/local-opencode-client"'
  cp "${ROOT_DIR}/frontend/apps/agent-web/src/assets/pets/radar-bunny.png" \
    "${package_root}/usr/share/icons/hicolor/512x512/apps/test-agent-local-client.png"
  cat >"${package_root}/usr/lib/systemd/user/test-agent-local-opencode-client.service" <<'EOF'
[Unit]
Description=TestAgent Local OpenCode Client
After=network-online.target
Wants=network-online.target

[Service]
ExecStart=/opt/testagent/local-opencode-client/bin/test-agent-local-client
Restart=on-failure
RestartSec=3

[Install]
WantedBy=default.target
EOF
  ln -s /usr/lib/systemd/user/test-agent-local-opencode-client.service \
    "${package_root}/etc/systemd/user/default.target.wants/test-agent-local-opencode-client.service"
  cat >"${package_root}/usr/share/applications/test-agent-local-opencode-client.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=TestAgent 本地客户端
Comment=连接 TestAgent 平台与本地工作区
Exec=/opt/testagent/local-opencode-client/bin/test-agent-local-client
Icon=test-agent-local-client
Terminal=false
Categories=Development;Utility;
StartupNotify=false
EOF
  # 麒麟图形安装器的维护脚本错误只显示为笼统“软件包操作异常”。包内直接预置
  # systemd 全局启用软链接，避免 postinst 跨用户调用 systemctl/runuser，并保留 journalctl 诊断。
  installed_size="$(du -sk "${package_root}" | awk '{print $1}')"
  cat >"${control_root}/control" <<EOF
Package: test-agent-local-client
Version: ${package_version}
Section: utils
Priority: optional
Architecture: arm64
Maintainer: TestAgent Platform Team
Depends: libc6 (>= 2.28)
Installed-Size: ${installed_size}
Description: TestAgent local OpenCode client
 Connects the TestAgent platform to an authorized local workspace.
EOF
  chmod 0755 "${control_root}"
  chmod 0644 "${control_root}/control"
  # Mac 不手工拼接 ar/tar；按麒麟官方 DEB 指南复用 Linux dpkg-deb 生成标准 root-owned 包。
  docker run --rm --platform linux/arm64 \
    -v "${work_root}:/work" \
    "${DEB_BUILDER_IMAGE}" \
    dpkg-deb --build --root-owner-group /work/package /work/TestAgent-Local-Client-Kylin-arm64.deb \
    >/dev/null
  cp "${work_root}/TestAgent-Local-Client-Kylin-arm64.deb" "${destination}"
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
validate_default_url TEST_AGENT_LOCAL_CLIENT_DEFAULT_SERVER_URL "${DEFAULT_SERVER_URL}"
validate_default_url TEST_AGENT_LOCAL_CLIENT_DEFAULT_WEB_URL "${DEFAULT_WEB_URL}"
case "${ALLOW_INSECURE_SETUP}" in true|false) ;; *)
  echo "TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_SETUP must be true or false" >&2
  exit 2
esac
if [[ "${DEFAULT_SERVER_URL}" == http://* || "${DEFAULT_WEB_URL}" == http://* ]]; then
  [[ "${ALLOW_INSECURE_SETUP}" == true ]] || {
    echo "HTTP native-installer defaults require TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_SETUP=true" >&2
    exit 2
  }
fi
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
MACOS_INSTALLER="${OUTPUT_DIR}/TestAgent-Local-Client-macOS-arm64.pkg"
KYLIN_INSTALLER="${OUTPUT_DIR}/TestAgent-Local-Client-Kylin-arm64.deb"
build_macos_installer "${RELEASE_DIR}" "${MACOS_INSTALLER}"
build_kylin_installer "${RELEASE_DIR}" "${KYLIN_INSTALLER}"
MACOS_INSTALLER_SHA="$(sha256_file "${MACOS_INSTALLER}")"
KYLIN_INSTALLER_SHA="$(sha256_file "${KYLIN_INSTALLER}")"
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
  printf '  "linuxArm64GlibcOpencodeSha256": "%s",\n' "${OPENCODE_LINUX_OUTPUT_SHA}"
  printf '  "darwinArm64InstallerPath": "TestAgent-Local-Client-macOS-arm64.pkg",\n'
  printf '  "darwinArm64InstallerSha256": "%s",\n' "${MACOS_INSTALLER_SHA}"
  printf '  "linuxArm64GlibcInstallerPath": "TestAgent-Local-Client-Kylin-arm64.deb",\n'
  printf '  "linuxArm64GlibcInstallerSha256": "%s"\n' "${KYLIN_INSTALLER_SHA}"
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
  "${STABLE_DIR}/manifest.json.sig" "${MACOS_INSTALLER}" "${KYLIN_INSTALLER}" "${RELEASE_DIR}"/*
printf 'Local client HTTP distribution: %s\n' "${OUTPUT_DIR}"
printf 'Release version: %s\n' "${VERSION}"
printf 'Double-click installers: %s, %s\n' "${MACOS_INSTALLER}" "${KYLIN_INSTALLER}"
