#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/local-client-package-test.XXXXXX")"
HTTP_PID=""

cleanup() {
  if [[ -n "${HTTP_PID}" ]]; then
    kill "${HTTP_PID}" >/dev/null 2>&1 || true
    wait "${HTTP_PID}" >/dev/null 2>&1 || true
  fi
  rm -rf "${TEST_ROOT}"
}
trap cleanup EXIT

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

mkdir -p "${TEST_ROOT}/inputs/jre-darwin/fake-jre/bin" \
  "${TEST_ROOT}/inputs/jre-linux/fake-jre/bin" \
  "${TEST_ROOT}/inputs/opencode-darwin" \
  "${TEST_ROOT}/inputs/opencode-linux"
printf '#!/usr/bin/env sh\nexit 0\n' >"${TEST_ROOT}/inputs/jre-darwin/fake-jre/bin/java"
cp "${TEST_ROOT}/inputs/jre-darwin/fake-jre/bin/java" \
  "${TEST_ROOT}/inputs/jre-linux/fake-jre/bin/java"
printf '#!/usr/bin/env sh\nexit 0\n' >"${TEST_ROOT}/inputs/opencode-darwin/opencode"
cp "${TEST_ROOT}/inputs/opencode-darwin/opencode" \
  "${TEST_ROOT}/inputs/opencode-linux/opencode"
chmod 0755 "${TEST_ROOT}/inputs/jre-darwin/fake-jre/bin/java" \
  "${TEST_ROOT}/inputs/jre-linux/fake-jre/bin/java" \
  "${TEST_ROOT}/inputs/opencode-darwin/opencode" \
  "${TEST_ROOT}/inputs/opencode-linux/opencode"
tar -C "${TEST_ROOT}/inputs/jre-darwin" -czf "${TEST_ROOT}/jre-darwin.tar.gz" fake-jre
tar -C "${TEST_ROOT}/inputs/jre-linux" -czf "${TEST_ROOT}/jre-linux.tar.gz" fake-jre
(cd "${TEST_ROOT}/inputs/opencode-darwin" && zip -q "${TEST_ROOT}/opencode-darwin.zip" opencode)
tar -C "${TEST_ROOT}/inputs/opencode-linux" -czf "${TEST_ROOT}/opencode-linux.tar.gz" opencode
printf 'test client jar\n' >"${TEST_ROOT}/test-agent-local-client.jar"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "${TEST_ROOT}/signing-private.pem" >/dev/null 2>&1

if "${ROOT_DIR}/deploy/internal/package-local-opencode-client.sh" \
    --output-dir "${TEST_ROOT}/unsafe-dist" \
    --signing-key "${TEST_ROOT}/signing-private.pem" \
    --client-jar "${TEST_ROOT}/test-agent-local-client.jar" \
    --skip-build >/dev/null 2>&1; then
  echo "Unsafe package output directory was unexpectedly accepted" >&2
  exit 1
fi

(
  cd "${TEST_ROOT}"
  TEST_AGENT_LOCAL_CLIENT_JRE_DARWIN_ARM64_ARCHIVE="${TEST_ROOT}/jre-darwin.tar.gz" \
  TEST_AGENT_LOCAL_CLIENT_JRE_DARWIN_ARM64_SHA256="$(sha256_file "${TEST_ROOT}/jre-darwin.tar.gz")" \
  TEST_AGENT_LOCAL_CLIENT_JRE_LINUX_ARM64_GLIBC_ARCHIVE="${TEST_ROOT}/jre-linux.tar.gz" \
  TEST_AGENT_LOCAL_CLIENT_JRE_LINUX_ARM64_GLIBC_SHA256="$(sha256_file "${TEST_ROOT}/jre-linux.tar.gz")" \
  TEST_AGENT_LOCAL_CLIENT_OPENCODE_DARWIN_ARM64_ARCHIVE="${TEST_ROOT}/opencode-darwin.zip" \
  TEST_AGENT_LOCAL_CLIENT_OPENCODE_DARWIN_ARM64_SHA256="$(sha256_file "${TEST_ROOT}/opencode-darwin.zip")" \
  TEST_AGENT_LOCAL_CLIENT_OPENCODE_LINUX_ARM64_GLIBC_ARCHIVE="${TEST_ROOT}/opencode-linux.tar.gz" \
  TEST_AGENT_LOCAL_CLIENT_OPENCODE_LINUX_ARM64_GLIBC_SHA256="$(sha256_file "${TEST_ROOT}/opencode-linux.tar.gz")" \
    "${ROOT_DIR}/deploy/internal/package-local-opencode-client.sh" \
      --output-dir dist/local-opencode-client \
      --version 0.1.0-test \
      --signing-key "${TEST_ROOT}/signing-private.pem" \
      --client-jar "${TEST_ROOT}/test-agent-local-client.jar" \
      --skip-build
)

PUBLIC_KEY="${TEST_ROOT}/signing-public.pem"
openssl pkey -in "${TEST_ROOT}/signing-private.pem" -pubout -out "${PUBLIC_KEY}" >/dev/null
openssl dgst -sha256 -verify "${PUBLIC_KEY}" \
  -signature "${TEST_ROOT}/dist/local-opencode-client/stable/manifest.json.sig" \
  "${TEST_ROOT}/dist/local-opencode-client/stable/manifest.json" >/dev/null
(cd "${TEST_ROOT}/dist/local-opencode-client" && \
  test "$(sha256_file TestAgent-Local-Client-macOS-arm64.pkg)" = \
    "$(awk -F'"' '$2 == "darwinArm64InstallerSha256" { print $4; exit }' stable/manifest.json)" && \
  test "$(sha256_file TestAgent-Local-Client-Kylin-arm64.deb)" = \
    "$(awk -F'"' '$2 == "linuxArm64GlibcInstallerSha256" { print $4; exit }' stable/manifest.json)")
PKG_EXPANDED="${TEST_ROOT}/pkg-expanded"
pkgutil --expand-full \
  "${TEST_ROOT}/dist/local-opencode-client/TestAgent-Local-Client-macOS-arm64.pkg" \
  "${PKG_EXPANDED}"
test -x "$(find "${PKG_EXPANDED}" -type f -path \
  '*/Payload/Applications/TestAgent Local Client.app/Contents/MacOS/TestAgentLocalClient' | head -n 1)"
APP_INFO_PLIST="$(find "${PKG_EXPANDED}" -type f -path \
  '*/Payload/Applications/TestAgent Local Client.app/Contents/Info.plist' | head -n 1)"
grep -q '<string>TestAgent 本地客户端</string>' "${APP_INFO_PLIST}"
if grep -q '<key>LSUIElement</key>' "${APP_INFO_PLIST}"; then
  echo "macOS app unexpectedly hides its first-run configuration entry" >&2
  exit 1
fi
if sed -n '/<relocate>/,/<\/relocate>/p' "${PKG_EXPANDED}/PackageInfo" | grep -q '<bundle '; then
  echo "macOS PKG unexpectedly allows the app bundle to be relocated" >&2
  exit 1
fi
if grep -Eq 'launchctl kickstart|/usr/bin/open' "${PKG_EXPANDED}/Scripts/postinstall"; then
  echo "macOS PKG postinstall unexpectedly starts a blocking fallback command" >&2
  exit 1
fi
grep -q 'launchctl bootstrap' "${PKG_EXPANDED}/Scripts/postinstall"
grep -q '</dev/null' "${PKG_EXPANDED}/Scripts/postinstall"
grep -q 'Library/LaunchAgents/com.enterprise.testagent.local-opencode-client.plist' \
  "${PKG_EXPANDED}/Scripts/postinstall"
grep -q 'target_volume=' "${PKG_EXPANDED}/Scripts/preinstall"
grep -q 'target_volume=' "${PKG_EXPANDED}/Scripts/postinstall"
DEB_EXPANDED="${TEST_ROOT}/deb-expanded"
mkdir -p "${DEB_EXPANDED}"
(
  cd "${DEB_EXPANDED}"
  ar -x "${TEST_ROOT}/dist/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.deb"
  test "$(printf '%s\n' debian-binary control.tar.gz data.tar.gz)" = "$(ar -t "${TEST_ROOT}/dist/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.deb")"
  tar -tzf data.tar.gz | grep -q './opt/testagent/local-opencode-client/bin/test-agent-local-client'
  tar -tzf data.tar.gz | grep -q './usr/lib/systemd/user/test-agent-local-opencode-client.service'
  tar -tzf data.tar.gz | grep -q './usr/share/applications/test-agent-local-opencode-client.desktop'
)
(cd "${TEST_ROOT}/dist/local-opencode-client/releases/0.1.0-test" && \
  if command -v sha256sum >/dev/null 2>&1; then sha256sum -c SHA256SUMS; else shasum -a 256 -c SHA256SUMS; fi)

cp "${TEST_ROOT}/dist/local-opencode-client/stable/manifest.json" "${TEST_ROOT}/tampered-manifest.json"
printf ' ' >>"${TEST_ROOT}/tampered-manifest.json"
if openssl dgst -sha256 -verify "${PUBLIC_KEY}" \
  -signature "${TEST_ROOT}/dist/local-opencode-client/stable/manifest.json.sig" \
  "${TEST_ROOT}/tampered-manifest.json" >/dev/null 2>&1; then
  echo "Tampered manifest unexpectedly passed signature verification" >&2
  exit 1
fi

case "$(uname -s):$(uname -m)" in
  Darwin:arm64|Linux:aarch64|Linux:arm64)
    HTTP_PORT="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()')"
    python3 -m http.server "${HTTP_PORT}" --bind 127.0.0.1 \
      --directory "${TEST_ROOT}/dist/local-opencode-client" >"${TEST_ROOT}/http.log" 2>&1 &
    HTTP_PID="$!"
    for _ in $(seq 1 30); do
      curl -fsS "http://127.0.0.1:${HTTP_PORT}/stable/manifest.json" >/dev/null 2>&1 && break
      sleep 0.1
    done
    mkdir -p "${TEST_ROOT}/install/config"
    printf 'tack_v1_test-only-value\n' >"${TEST_ROOT}/install/config/client.key"
    chmod 0600 "${TEST_ROOT}/install/config/client.key"
    TEST_AGENT_LOCAL_CLIENT_DOWNLOAD_BASE_URL="http://127.0.0.1:${HTTP_PORT}" \
    TEST_AGENT_LOCAL_CLIENT_SERVER_URL=https://platform.example.internal \
    TEST_AGENT_LOCAL_CLIENT_WEB_URL=https://web.example.internal \
    TEST_AGENT_LOCAL_CLIENT_INSTALL_ROOT="${TEST_ROOT}/install/runtime" \
    TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR="${TEST_ROOT}/install/config" \
    TEST_AGENT_LOCAL_CLIENT_STATE_DIR="${TEST_ROOT}/install/state" \
    TEST_AGENT_LOCAL_CLIENT_SKIP_SERVICE_START=true \
      sh "${TEST_ROOT}/dist/local-opencode-client/install.sh"
    test -x "${TEST_ROOT}/install/runtime/current/jre/bin/java"
    test -x "${TEST_ROOT}/install/runtime/current/opencode/bin/opencode"
    test "$(stat -f '%Lp' "${TEST_ROOT}/install/config/client.key" 2>/dev/null \
      || stat -c '%a' "${TEST_ROOT}/install/config/client.key")" = 600
    grep -qx 'webUrl=https://web.example.internal' "${TEST_ROOT}/install/config/client.properties"
    grep -q 'SuccessfulExit' "${TEST_ROOT}/dist/local-opencode-client/install.sh"
    grep -q 'Restart=on-failure' "${TEST_ROOT}/dist/local-opencode-client/install.sh"
    ;;
  *)
    printf 'Installer execution skipped on unsupported test host %s/%s\n' "$(uname -s)" "$(uname -m)"
    ;;
esac

echo "Local OpenCode client package, signature, checksums and ARM64 installer verified"
