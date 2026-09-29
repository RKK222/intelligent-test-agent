#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/local-client-windows-package-test.XXXXXX")"

cleanup() {
  case "${TEST_ROOT}" in
    */local-client-windows-package-test.*) rm -rf "${TEST_ROOT}" ;;
    *) echo "Refusing unsafe temporary cleanup: ${TEST_ROOT}" >&2 ;;
  esac
}
trap cleanup EXIT

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

VERSION=20260827210000
PUBLIC_CONFIG_COMMIT=0123456789abcdef0123456789abcdef01234567
OUTPUT_DIR="${TEST_ROOT}/dist/local-opencode-client"
mkdir -p "${TEST_ROOT}/jdk/jdk-21/bin" "${TEST_ROOT}/opencode" "${TEST_ROOT}/rtk" \
  "${TEST_ROOT}/public-capabilities/public-capabilities/agents" "${OUTPUT_DIR}"
printf 'fake java PE\n' >"${TEST_ROOT}/jdk/jdk-21/bin/java.exe"
printf 'fake javaw PE\n' >"${TEST_ROOT}/jdk/jdk-21/bin/javaw.exe"
printf 'fake javac PE\n' >"${TEST_ROOT}/jdk/jdk-21/bin/javac.exe"
printf 'fake opencode PE\n' >"${TEST_ROOT}/opencode/opencode.exe"
printf 'fake rtk PE\n' >"${TEST_ROOT}/rtk/rtk.exe"
(
  cd "${TEST_ROOT}/jdk"
  zip -qr "${TEST_ROOT}/jdk-windows.zip" jdk-21
)
(
  cd "${TEST_ROOT}/opencode"
  zip -q "${TEST_ROOT}/opencode-windows.zip" opencode.exe
)
(
  cd "${TEST_ROOT}/rtk"
  zip -q "${TEST_ROOT}/rtk-windows.zip" rtk.exe
)
printf 'test local client jar\n' >"${TEST_ROOT}/test-agent-local-client.jar"
printf 'public test agent\n' \
  >"${TEST_ROOT}/public-capabilities/public-capabilities/agents/test.md"
printf '{"schemaVersion":1,"sourceCommit":"%s"}\n' "${PUBLIC_CONFIG_COMMIT}" \
  >"${TEST_ROOT}/public-capabilities/public-capabilities/manifest.json"
tar -C "${TEST_ROOT}/public-capabilities" -czf "${TEST_ROOT}/public-capabilities.tar.gz" public-capabilities
printf 'Apache License 2.0 test license\n' >"${TEST_ROOT}/rtk-license"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "${TEST_ROOT}/signing-private.pem" >/dev/null 2>&1
openssl pkey -in "${TEST_ROOT}/signing-private.pem" -pubout \
  -out "${TEST_ROOT}/signing-public.pem" >/dev/null

TEST_AGENT_LOCAL_CLIENT_JDK_WINDOWS_X64_ARCHIVE="${TEST_ROOT}/jdk-windows.zip" \
TEST_AGENT_LOCAL_CLIENT_JDK_WINDOWS_X64_SHA256="$(sha256_file "${TEST_ROOT}/jdk-windows.zip")" \
TEST_AGENT_LOCAL_CLIENT_OPENCODE_WINDOWS_X64_ARCHIVE="${TEST_ROOT}/opencode-windows.zip" \
TEST_AGENT_LOCAL_CLIENT_OPENCODE_WINDOWS_X64_SHA256="$(sha256_file "${TEST_ROOT}/opencode-windows.zip")" \
TEST_AGENT_LOCAL_CLIENT_RTK_WINDOWS_X64_ARCHIVE="${TEST_ROOT}/rtk-windows.zip" \
TEST_AGENT_LOCAL_CLIENT_RTK_WINDOWS_X64_SHA256="$(sha256_file "${TEST_ROOT}/rtk-windows.zip")" \
TEST_AGENT_LOCAL_CLIENT_RTK_WINDOWS_X64_BINARY_SHA256="$(sha256_file "${TEST_ROOT}/rtk/rtk.exe")" \
TEST_AGENT_LOCAL_CLIENT_RTK_LICENSE_FILE="${TEST_ROOT}/rtk-license" \
TEST_AGENT_LOCAL_CLIENT_RTK_LICENSE_SHA256="$(sha256_file "${TEST_ROOT}/rtk-license")" \
  "${ROOT_DIR}/deploy/internal/package-local-opencode-client-windows.sh" \
    --output-dir "${OUTPUT_DIR}" \
    --version "${VERSION}" \
    --download-base-url http://downloads.example.internal/local-opencode-client/ \
    --server-url http://platform.example.internal:9996 \
    --allow-insecure-control true \
    --signing-key "${TEST_ROOT}/signing-private.pem" \
    --public-key "${TEST_ROOT}/signing-public.pem" \
    --client-jar "${TEST_ROOT}/test-agent-local-client.jar" \
    --public-config-commit "${PUBLIC_CONFIG_COMMIT}" \
    --public-capability-bundle "${TEST_ROOT}/public-capabilities.tar.gz" \
    --skip-build >/dev/null

RELEASE_DIR="${OUTPUT_DIR}/releases/${VERSION}"
PACKAGE="${OUTPUT_DIR}/TestAgent-Local-Client-Win10-x64-${VERSION}-unsigned.zip"
EVIDENCE="${OUTPUT_DIR}/windows-x64-package-evidence.json"
test -f "${PACKAGE}"
unzip -tq "${PACKAGE}" >/dev/null
jq -e --arg version "${VERSION}" \
  '.version == $version and .platform == "windows" and .architecture == "x64" and
   .minimumWindowsBuild == 17763 and .authenticodeSigned == false and .status == "CANDIDATE_ONLY"' \
  "${EVIDENCE}" >/dev/null
jq -e --arg version "${VERSION}" \
  '.version == $version and .platform == "windows" and .architecture == "x64" and
   .protocolVersion == "local-opencode-client.v1" and .opencodeVersion == "2.0.18"' \
  "${RELEASE_DIR}/manifest.json" >/dev/null
openssl dgst -sha256 -verify "${TEST_ROOT}/signing-public.pem" \
  -signature "${RELEASE_DIR}/manifest.json.sig" "${RELEASE_DIR}/manifest.json" >/dev/null
for artifact in test-agent-local-client.jar jdk.tar.gz opencode.tar.gz public-capabilities.tar.gz; do
  openssl dgst -sha256 -verify "${TEST_ROOT}/signing-public.pem" \
    -signature "${RELEASE_DIR}/${artifact}.sig" "${RELEASE_DIR}/${artifact}" >/dev/null
done

mkdir -p "${TEST_ROOT}/extracted"
unzip -q "${PACKAGE}" -d "${TEST_ROOT}/extracted"
PACKAGE_ROOT="${TEST_ROOT}/extracted/TestAgent-Local-Client-Win10-x64"
file "${PACKAGE_ROOT}/TestAgent-Local-Client-Setup.exe" | grep -Eq 'PE32\+ executable.*x86-64'
file "${PACKAGE_ROOT}/resources/TestAgent-Local-Client.exe" | grep -Eq 'PE32\+ executable.*x86-64'
test "$(jq -r '.releases | length' "${OUTPUT_DIR}/catalog.json")" -eq 1
openssl dgst -sha256 -verify "${TEST_ROOT}/signing-public.pem" \
  -signature "${OUTPUT_DIR}/catalog.json.sig" "${OUTPUT_DIR}/catalog.json" >/dev/null
test "$(jq -r '.packageSha256' "${EVIDENCE}")" = "$(sha256_file "${PACKAGE}")"

echo "local-opencode-client Windows package tests passed"
