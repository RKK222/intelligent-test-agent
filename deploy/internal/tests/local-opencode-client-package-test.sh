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

VERSION=20260820153045
PUBLIC_CONFIG_COMMIT=0123456789abcdef0123456789abcdef01234567
HTTP_PORT="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()')"
DOWNLOAD_ROOT="http://127.0.0.1:${HTTP_PORT}/"
mkdir -p "${TEST_ROOT}/inputs/jdk/fake-jdk/bin" \
  "${TEST_ROOT}/system-jdk/bin" \
  "${TEST_ROOT}/escaped-jdk/bin" \
  "${TEST_ROOT}/external-jdk/bin" \
  "${TEST_ROOT}/absolute-escaped-jdk/bin" \
  "${TEST_ROOT}/java-only/bin" \
  "${TEST_ROOT}/java17/bin" \
  "${TEST_ROOT}/java21-javac17/bin" \
  "${TEST_ROOT}/split-java/bin" \
  "${TEST_ROOT}/split-javac/bin" \
  "${TEST_ROOT}/inputs/opencode"
cat >"${TEST_ROOT}/inputs/jdk/fake-jdk/bin/java" <<'JAVA'
#!/usr/bin/env sh
if [ "${1:-}" = "-version" ]; then
  echo 'openjdk version "21.0.9"' >&2
fi
exit 0
JAVA
cat >"${TEST_ROOT}/inputs/jdk/fake-jdk/bin/javac" <<'JAVAC'
#!/usr/bin/env sh
echo 'javac 21.0.9'
exit 0
JAVAC
cat >"${TEST_ROOT}/system-jdk/bin/java" <<'JAVA'
#!/usr/bin/env sh
if [ "${1:-}" = "-version" ]; then
  echo 'openjdk version "21.0.9"' >&2
fi
exit 0
JAVA
cat >"${TEST_ROOT}/system-jdk/bin/javac" <<'JAVAC'
#!/usr/bin/env sh
echo 'javac 21.0.9'
exit 0
JAVAC
cp "${TEST_ROOT}/system-jdk/bin/java" "${TEST_ROOT}/java-only/bin/java"
cp "${TEST_ROOT}/system-jdk/bin/java" "${TEST_ROOT}/escaped-jdk/bin/java"
cp "${TEST_ROOT}/system-jdk/bin/javac" "${TEST_ROOT}/external-jdk/bin/javac"
ln -s "../../external-jdk/bin/javac" "${TEST_ROOT}/escaped-jdk/bin/javac"
cp "${TEST_ROOT}/system-jdk/bin/java" "${TEST_ROOT}/absolute-escaped-jdk/bin/java"
ln -s "${TEST_ROOT}/external-jdk/bin/javac" "${TEST_ROOT}/absolute-escaped-jdk/bin/javac"
cp "${TEST_ROOT}/system-jdk/bin/java" "${TEST_ROOT}/java21-javac17/bin/java"
cp "${TEST_ROOT}/system-jdk/bin/java" "${TEST_ROOT}/split-java/bin/java"
cp "${TEST_ROOT}/system-jdk/bin/javac" "${TEST_ROOT}/split-javac/bin/javac"
cat >"${TEST_ROOT}/java21-javac17/bin/javac" <<'JAVAC'
#!/usr/bin/env sh
echo 'javac 17.0.13'
exit 0
JAVAC
cat >"${TEST_ROOT}/java17/bin/java" <<'JAVA'
#!/usr/bin/env sh
if [ "${1:-}" = "-version" ]; then
  echo 'openjdk version "17.0.13"' >&2
fi
exit 0
JAVA
cat >"${TEST_ROOT}/java17/bin/javac" <<'JAVAC'
#!/usr/bin/env sh
echo 'javac 21.0.9'
exit 0
JAVAC
printf '#!/usr/bin/env sh\nexit 0\n' >"${TEST_ROOT}/inputs/opencode/opencode"
chmod 0755 "${TEST_ROOT}/inputs/jdk/fake-jdk/bin/java" \
  "${TEST_ROOT}/inputs/jdk/fake-jdk/bin/javac" \
  "${TEST_ROOT}/system-jdk/bin/java" \
  "${TEST_ROOT}/system-jdk/bin/javac" \
  "${TEST_ROOT}/escaped-jdk/bin/java" \
  "${TEST_ROOT}/external-jdk/bin/javac" \
  "${TEST_ROOT}/absolute-escaped-jdk/bin/java" \
  "${TEST_ROOT}/java21-javac17/bin/java" \
  "${TEST_ROOT}/java21-javac17/bin/javac" \
  "${TEST_ROOT}/split-java/bin/java" \
  "${TEST_ROOT}/split-javac/bin/javac" \
  "${TEST_ROOT}/java-only/bin/java" \
  "${TEST_ROOT}/java17/bin/java" \
  "${TEST_ROOT}/java17/bin/javac" \
  "${TEST_ROOT}/inputs/opencode/opencode"
tar -C "${TEST_ROOT}/inputs/jdk" -czf "${TEST_ROOT}/jdk-linux.tar.gz" fake-jdk
tar -C "${TEST_ROOT}/inputs/opencode" -czf "${TEST_ROOT}/opencode-linux.tar.gz" opencode
printf 'test client jar\n' >"${TEST_ROOT}/test-agent-local-client.jar"
mkdir -p "${TEST_ROOT}/public-capabilities/public-capabilities/agents"
printf '%s\n' 'public test agent' >"${TEST_ROOT}/public-capabilities/public-capabilities/agents/test.md"
printf '{"schemaVersion":1,"sourceCommit":"%s"}\n' "${PUBLIC_CONFIG_COMMIT}" \
  >"${TEST_ROOT}/public-capabilities/public-capabilities/manifest.json"
tar -C "${TEST_ROOT}/public-capabilities" -czf "${TEST_ROOT}/public-capabilities.tar.gz" public-capabilities
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "${TEST_ROOT}/signing-private.pem" >/dev/null 2>&1

if "${ROOT_DIR}/deploy/internal/package-local-opencode-client.sh" \
    --output-dir "${TEST_ROOT}/unsafe-dist" \
    --version "${VERSION}" \
    --download-base-url "${DOWNLOAD_ROOT}" \
    --server-url https://platform.example.internal \
    --signing-key "${TEST_ROOT}/signing-private.pem" \
    --client-jar "${TEST_ROOT}/test-agent-local-client.jar" \
    --skip-build >/dev/null 2>&1; then
  echo "Unsafe package output directory was unexpectedly accepted" >&2
  exit 1
fi

package_release() {
  TEST_AGENT_LOCAL_CLIENT_JDK_LINUX_ARM64_GLIBC_ARCHIVE="${TEST_ROOT}/jdk-linux.tar.gz" \
  TEST_AGENT_LOCAL_CLIENT_JDK_LINUX_ARM64_GLIBC_SHA256="$(sha256_file "${TEST_ROOT}/jdk-linux.tar.gz")" \
  TEST_AGENT_LOCAL_CLIENT_OPENCODE_LINUX_ARM64_GLIBC_ARCHIVE="${TEST_ROOT}/opencode-linux.tar.gz" \
  TEST_AGENT_LOCAL_CLIENT_OPENCODE_LINUX_ARM64_GLIBC_SHA256="$(sha256_file "${TEST_ROOT}/opencode-linux.tar.gz")" \
    "${ROOT_DIR}/deploy/internal/package-local-opencode-client.sh" \
      --output-dir "${TEST_ROOT}/dist/local-opencode-client" \
      --version "${VERSION}" \
      --download-base-url "${DOWNLOAD_ROOT}" \
      --server-url https://platform.example.internal \
      --signing-key "${TEST_ROOT}/signing-private.pem" \
      --client-jar "${TEST_ROOT}/test-agent-local-client.jar" \
      --public-config-commit "${PUBLIC_CONFIG_COMMIT}" \
      --public-capability-bundle "${TEST_ROOT}/public-capabilities.tar.gz" \
      --skip-build
}

package_release
RELEASE_DIR="${TEST_ROOT}/dist/local-opencode-client/releases/${VERSION}"
PUBLIC_KEY="${TEST_ROOT}/signing-public.pem"
openssl pkey -in "${TEST_ROOT}/signing-private.pem" -pubout -out "${PUBLIC_KEY}" >/dev/null
openssl dgst -sha256 -verify "${PUBLIC_KEY}" \
  -signature "${RELEASE_DIR}/manifest.json.sig" \
  "${RELEASE_DIR}/manifest.json" >/dev/null
for artifact in test-agent-local-client.jar jdk.tar.gz opencode.tar.gz public-capabilities.tar.gz; do
  openssl dgst -sha256 -verify "${PUBLIC_KEY}" \
    -signature "${RELEASE_DIR}/${artifact}.sig" \
    "${RELEASE_DIR}/${artifact}" >/dev/null
done

grep -q '"schemaVersion": 2' "${RELEASE_DIR}/manifest.json"
grep -q '"version": "20260820153045"' "${TEST_ROOT}/dist/local-opencode-client/catalog.json"
openssl dgst -sha256 -verify "${PUBLIC_KEY}" \
  -signature "${TEST_ROOT}/dist/local-opencode-client/catalog.json.sig" \
  "${TEST_ROOT}/dist/local-opencode-client/catalog.json" >/dev/null

NGINX_TEMPLATE="${ROOT_DIR}/deploy/internal/nginx/gateway.conf.template"
test "$(grep -c 'location = /downloads/local-opencode-client/catalog.json {' "${NGINX_TEMPLATE}")" -eq 2
test "$(grep -c 'location = /downloads/local-opencode-client/catalog.json.sig {' "${NGINX_TEMPLATE}")" -eq 2
test "$(grep -c 'location = /downloads/local-opencode-client/installer {' "${NGINX_TEMPLATE}")" -eq 2
test "$(grep -c 'location = /downloads/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.deb {' "${NGINX_TEMPLATE}")" -eq 2
test "$(grep -F -c 'test-agent-local-client_[0-9]{14}_arm64\.deb' "${NGINX_TEMPLATE}")" -eq 2
grep -q 'Cache-Control "public, max-age=31536000, immutable"' "${NGINX_TEMPLATE}"

DEB_FILE="${TEST_ROOT}/dist/local-opencode-client/test-agent-local-client_${VERSION}_arm64.deb"
DEB_DOWNLOAD_ALIAS="${TEST_ROOT}/dist/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.deb"
test -f "${DEB_FILE}"
cmp "${DEB_FILE}" "${DEB_DOWNLOAD_ALIAS}"
ar -p "${DEB_FILE}" data.tar.gz >"${TEST_ROOT}/data.tar.gz"
tar -tzf "${TEST_ROOT}/data.tar.gz" >"${TEST_ROOT}/deb-data.list"
grep -q '^\./usr/bin/test-agent-local-client$' "${TEST_ROOT}/deb-data.list"
if grep -Eq '\.(jar|tar\.gz)$' "${TEST_ROOT}/deb-data.list"; then
  echo "DEB data payload unexpectedly contains a runtime artifact" >&2
  exit 1
fi

if package_release >/dev/null 2>&1; then
  echo "Duplicate immutable client version was unexpectedly overwritten" >&2
  exit 1
fi

python3 -m http.server "${HTTP_PORT}" --bind 127.0.0.1 \
  --directory "${TEST_ROOT}/dist/local-opencode-client" >"${TEST_ROOT}/http.log" 2>&1 &
HTTP_PID="$!"
for _ in $(seq 1 30); do
  curl -fsS "${DOWNLOAD_ROOT}catalog.json" >/dev/null 2>&1 && break
  sleep 0.1
done
TOOLS_WITHOUT_JAVA="${TEST_ROOT}/tools-without-java"
mkdir -p "${TOOLS_WITHOUT_JAVA}"
for tool in awk basename chmod cmp cp curl dirname env expr find getconf grep hostname ldd ln mkdir mktemp mv \
  openssl readlink rm rmdir sh sha256sum shasum sort stat tail tar tr wc; do
  tool_path="$(command -v "${tool}" || true)"
  [ -n "${tool_path}" ] || continue
  ln -s "${tool_path}" "${TOOLS_WITHOUT_JAVA}/${tool}"
done
mkdir -p "${TEST_ROOT}/install/config"
cat >"${TEST_ROOT}/install/config/credentials.properties" <<'CREDENTIALS'
unifiedAuthId=test-user
clientKey=tack_v1_test-only-value
CREDENTIALS
chmod 0700 "${TEST_ROOT}/install/config"
chmod 0600 "${TEST_ROOT}/install/config/credentials.properties"
 : >"${TEST_ROOT}/http.log"
PATH="${TEST_ROOT}/system-jdk/bin:${PATH}" \
TEST_AGENT_LOCAL_CLIENT_TEST_MODE=true \
TEST_AGENT_LOCAL_CLIENT_TEST_PLATFORM=linux-arm64-glibc \
TEST_AGENT_LOCAL_CLIENT_INSTALL_ROOT="${TEST_ROOT}/install/runtime" \
TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR="${TEST_ROOT}/install/config" \
TEST_AGENT_LOCAL_CLIENT_STATE_DIR="${TEST_ROOT}/install/state" \
TEST_AGENT_LOCAL_CLIENT_SKIP_SERVICE_START=true \
  sh "${TEST_ROOT}/dist/local-opencode-client/install.sh"
test -x "${TEST_ROOT}/install/runtime/current/jdk/bin/java"
test -x "${TEST_ROOT}/install/runtime/current/jdk/bin/javac"
test -x "${TEST_ROOT}/install/runtime/current/opencode/bin/opencode"
test ! -L "${TEST_ROOT}/install/runtime/current/jdk"
grep -qx 'source=system-jdk21' "${TEST_ROOT}/install/runtime/current/jdk.provenance"
if grep -q '/jdk.tar.gz' "${TEST_ROOT}/http.log"; then
  echo "完整系统 JDK 21 初装意外请求了 JDK 归档或签名" >&2
  exit 1
fi
for expected_request in \
  "GET /releases/${VERSION}/manifest.json " \
  "GET /releases/${VERSION}/manifest.json.sig " \
  "GET /releases/${VERSION}/test-agent-local-client.jar " \
  "GET /releases/${VERSION}/test-agent-local-client.jar.sig " \
  "GET /releases/${VERSION}/opencode.tar.gz " \
  "GET /releases/${VERSION}/opencode.tar.gz.sig " \
  "GET /releases/${VERSION}/public-capabilities.tar.gz " \
  "GET /releases/${VERSION}/public-capabilities.tar.gz.sig "; do
  grep -q "${expected_request}" "${TEST_ROOT}/http.log"
done

# PATH java/javac 虽同指一组命令，home 内的外部 javac 链接仍必须拒绝并回退受签名 JDK。
mkdir -p "${TEST_ROOT}/install-escaped/config"
cp "${TEST_ROOT}/install/config/credentials.properties" "${TEST_ROOT}/install-escaped/config/credentials.properties"
chmod 0700 "${TEST_ROOT}/install-escaped/config"
chmod 0600 "${TEST_ROOT}/install-escaped/config/credentials.properties"
: >"${TEST_ROOT}/http.log"
PATH="${TEST_ROOT}/escaped-jdk/bin:${PATH}" \
TEST_AGENT_LOCAL_CLIENT_TEST_MODE=true \
TEST_AGENT_LOCAL_CLIENT_TEST_PLATFORM=linux-arm64-glibc \
TEST_AGENT_LOCAL_CLIENT_INSTALL_ROOT="${TEST_ROOT}/install-escaped/runtime" \
TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR="${TEST_ROOT}/install-escaped/config" \
TEST_AGENT_LOCAL_CLIENT_STATE_DIR="${TEST_ROOT}/install-escaped/state" \
TEST_AGENT_LOCAL_CLIENT_SKIP_SERVICE_START=true \
  sh "${TEST_ROOT}/dist/local-opencode-client/install.sh"
test -f "${TEST_ROOT}/install-escaped/runtime/current/jdk.tar.gz"
grep -q "/jdk.tar.gz " "${TEST_ROOT}/http.log"

mv "${TEST_ROOT}/system-jdk" "${TEST_ROOT}/system-jdk-removed"
"${TEST_ROOT}/install/runtime/current/jdk/bin/java" -version 2>&1 | grep -q '21.0.9'
"${TEST_ROOT}/install/runtime/current/jdk/bin/javac" -version 2>&1 | grep -q '21.0.9'

# marker 不是单独的豁免凭据：格式或文件类型异常时，缺失签名 JDK 归档必须使已有 release 失效。
for marker_case in multi-line trailing-nul symbolic-link; do
  marker_root="${TEST_ROOT}/marker-${marker_case}/runtime"
  mkdir -p "$(dirname "${marker_root}")"
  cp -R "${TEST_ROOT}/install/runtime" "${marker_root}"
  marker_file="${marker_root}/current/jdk.provenance"
  case "${marker_case}" in
    multi-line)
      printf 'source=system-jdk21\nextra\n' >"${marker_file}"
      ;;
    trailing-nul)
      printf 'source=system-jdk21\n\0' >"${marker_file}"
      ;;
    symbolic-link)
      mv "${marker_file}" "${marker_file}.regular"
      ln -s "$(basename "${marker_file}").regular" "${marker_file}"
      ;;
  esac
  if PATH="${TEST_ROOT}/system-jdk/bin:${PATH}" \
    TEST_AGENT_LOCAL_CLIENT_TEST_MODE=true \
    TEST_AGENT_LOCAL_CLIENT_TEST_PLATFORM=linux-arm64-glibc \
    TEST_AGENT_LOCAL_CLIENT_INSTALL_ROOT="${marker_root}" \
    TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR="${TEST_ROOT}/install/config" \
    TEST_AGENT_LOCAL_CLIENT_STATE_DIR="${TEST_ROOT}/marker-${marker_case}/state" \
    TEST_AGENT_LOCAL_CLIENT_SKIP_SERVICE_START=true \
      sh "${TEST_ROOT}/dist/local-opencode-client/install.sh" run >/dev/null 2>&1; then
    echo "异常 marker ${marker_case} 意外允许无归档 release 运行" >&2
    exit 1
  fi
done
grep -q "downloadBaseUrl=${DOWNLOAD_ROOT}" "${TEST_ROOT}/install/config/client.properties"
grep -q '^signingPublicKeyBase64=' "${TEST_ROOT}/install/config/client.properties"
test "$(stat -f '%Lp' "${TEST_ROOT}/install/config/credentials.properties" 2>/dev/null \
  || stat -c '%a' "${TEST_ROOT}/install/config/credentials.properties")" = 600

for scenario in no-java java-only java17 java21-javac17 split-path-java-javac absolute-escaped-javac; do
  case "${scenario}" in
    no-java) scenario_path="${TOOLS_WITHOUT_JAVA}" ;;
    java-only) scenario_path="${TEST_ROOT}/java-only/bin:${TOOLS_WITHOUT_JAVA}" ;;
    java17) scenario_path="${TEST_ROOT}/java17/bin:${TOOLS_WITHOUT_JAVA}" ;;
    java21-javac17) scenario_path="${TEST_ROOT}/java21-javac17/bin:${TOOLS_WITHOUT_JAVA}" ;;
    split-path-java-javac) scenario_path="${TEST_ROOT}/split-java/bin:${TEST_ROOT}/split-javac/bin:${TOOLS_WITHOUT_JAVA}" ;;
    absolute-escaped-javac) scenario_path="${TEST_ROOT}/absolute-escaped-jdk/bin:${TOOLS_WITHOUT_JAVA}" ;;
  esac
  mkdir -p "${TEST_ROOT}/install-${scenario}/config"
  cat >"${TEST_ROOT}/install-${scenario}/config/credentials.properties" <<'CREDENTIALS'
unifiedAuthId=test-user
clientKey=tack_v1_test-only-value
CREDENTIALS
  chmod 0700 "${TEST_ROOT}/install-${scenario}/config"
  chmod 0600 "${TEST_ROOT}/install-${scenario}/config/credentials.properties"
  : >"${TEST_ROOT}/http.log"
  PATH="${scenario_path}" \
  TEST_AGENT_LOCAL_CLIENT_TEST_MODE=true \
  TEST_AGENT_LOCAL_CLIENT_TEST_PLATFORM=linux-arm64-glibc \
  TEST_AGENT_LOCAL_CLIENT_INSTALL_ROOT="${TEST_ROOT}/install-${scenario}/runtime" \
  TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR="${TEST_ROOT}/install-${scenario}/config" \
  TEST_AGENT_LOCAL_CLIENT_STATE_DIR="${TEST_ROOT}/install-${scenario}/state" \
  TEST_AGENT_LOCAL_CLIENT_SKIP_SERVICE_START=true \
    sh "${TEST_ROOT}/dist/local-opencode-client/install.sh"
  test -f "${TEST_ROOT}/install-${scenario}/runtime/current/jdk.tar.gz"
  test -f "${TEST_ROOT}/install-${scenario}/runtime/current/jdk.tar.gz.sig"
  test ! -e "${TEST_ROOT}/install-${scenario}/runtime/current/jdk.provenance"
  grep -q "/jdk.tar.gz " "${TEST_ROOT}/http.log"
  grep -q "/jdk.tar.gz.sig " "${TEST_ROOT}/http.log"
done

# 普通 release 即使被写入 marker，也必须继续校验已签名 JDK 归档。
ordinary_root="${TEST_ROOT}/install-no-java/runtime"
printf 'source=system-jdk21\n' >"${ordinary_root}/current/jdk.provenance"
printf 'tampered-jdk' >"${ordinary_root}/current/jdk.tar.gz"
if PATH="${TOOLS_WITHOUT_JAVA}" \
  TEST_AGENT_LOCAL_CLIENT_TEST_MODE=true \
  TEST_AGENT_LOCAL_CLIENT_TEST_PLATFORM=linux-arm64-glibc \
  TEST_AGENT_LOCAL_CLIENT_INSTALL_ROOT="${ordinary_root}" \
  TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR="${TEST_ROOT}/install-no-java/config" \
  TEST_AGENT_LOCAL_CLIENT_STATE_DIR="${TEST_ROOT}/install-no-java/state" \
  TEST_AGENT_LOCAL_CLIENT_SKIP_SERVICE_START=true \
    sh "${TEST_ROOT}/dist/local-opencode-client/install.sh" run >/dev/null 2>&1; then
  echo "普通 release 的 marker 意外跳过了受签名 JDK 归档校验" >&2
  exit 1
fi

ORIGINAL_CLIENT_JAR="${TEST_ROOT}/original-client.jar"
ORIGINAL_CLIENT_SIGNATURE="${TEST_ROOT}/original-client.jar.sig"
ORIGINAL_OPENCODE_ARCHIVE="${TEST_ROOT}/original-opencode.tar.gz"
ORIGINAL_OPENCODE_SIGNATURE="${TEST_ROOT}/original-opencode.tar.gz.sig"
ORIGINAL_MANIFEST_SIGNATURE="${TEST_ROOT}/original-manifest.json.sig"
cp "${RELEASE_DIR}/test-agent-local-client.jar" "${ORIGINAL_CLIENT_JAR}"
cp "${RELEASE_DIR}/test-agent-local-client.jar.sig" "${ORIGINAL_CLIENT_SIGNATURE}"
cp "${RELEASE_DIR}/opencode.tar.gz" "${ORIGINAL_OPENCODE_ARCHIVE}"
cp "${RELEASE_DIR}/opencode.tar.gz.sig" "${ORIGINAL_OPENCODE_SIGNATURE}"
cp "${RELEASE_DIR}/manifest.json.sig" "${ORIGINAL_MANIFEST_SIGNATURE}"
for signed_failure in client-jar client-jar-same-size client-jar-signature opencode opencode-same-size opencode-signature manifest-signature; do
  cp "${ORIGINAL_CLIENT_JAR}" "${RELEASE_DIR}/test-agent-local-client.jar"
  cp "${ORIGINAL_CLIENT_SIGNATURE}" "${RELEASE_DIR}/test-agent-local-client.jar.sig"
  cp "${ORIGINAL_OPENCODE_ARCHIVE}" "${RELEASE_DIR}/opencode.tar.gz"
  cp "${ORIGINAL_OPENCODE_SIGNATURE}" "${RELEASE_DIR}/opencode.tar.gz.sig"
  cp "${ORIGINAL_MANIFEST_SIGNATURE}" "${RELEASE_DIR}/manifest.json.sig"
  case "${signed_failure}" in
    client-jar) printf 'tampered client jar' >"${RELEASE_DIR}/test-agent-local-client.jar" ;;
    client-jar-same-size)
      python3 - "${RELEASE_DIR}/test-agent-local-client.jar" <<'PY'
from pathlib import Path
artifact = Path(__import__("sys").argv[1])
content = bytearray(artifact.read_bytes())
content[-1] ^= 1
artifact.write_bytes(content)
PY
      ;;
    client-jar-signature) printf 'invalid client jar signature' >"${RELEASE_DIR}/test-agent-local-client.jar.sig" ;;
    opencode) printf 'tampered opencode' >"${RELEASE_DIR}/opencode.tar.gz" ;;
    opencode-same-size)
      python3 - "${RELEASE_DIR}/opencode.tar.gz" <<'PY'
from pathlib import Path
artifact = Path(__import__("sys").argv[1])
content = bytearray(artifact.read_bytes())
content[-1] ^= 1
artifact.write_bytes(content)
PY
      ;;
    opencode-signature) printf 'invalid opencode signature' >"${RELEASE_DIR}/opencode.tar.gz.sig" ;;
    manifest-signature) printf 'invalid manifest signature' >"${RELEASE_DIR}/manifest.json.sig" ;;
  esac
  mkdir -p "${TEST_ROOT}/install-${signed_failure}/config"
  cp "${TEST_ROOT}/install/config/credentials.properties" \
    "${TEST_ROOT}/install-${signed_failure}/config/credentials.properties"
  chmod 0700 "${TEST_ROOT}/install-${signed_failure}/config"
  chmod 0600 "${TEST_ROOT}/install-${signed_failure}/config/credentials.properties"
  if PATH="${TEST_ROOT}/inputs/jdk/fake-jdk/bin:${PATH}" \
    TEST_AGENT_LOCAL_CLIENT_TEST_MODE=true \
    TEST_AGENT_LOCAL_CLIENT_TEST_PLATFORM=linux-arm64-glibc \
    TEST_AGENT_LOCAL_CLIENT_INSTALL_ROOT="${TEST_ROOT}/install-${signed_failure}/runtime" \
    TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR="${TEST_ROOT}/install-${signed_failure}/config" \
    TEST_AGENT_LOCAL_CLIENT_STATE_DIR="${TEST_ROOT}/install-${signed_failure}/state" \
    TEST_AGENT_LOCAL_CLIENT_SKIP_SERVICE_START=true \
      sh "${TEST_ROOT}/dist/local-opencode-client/install.sh" >/dev/null 2>&1; then
    echo "系统 JDK 分支的 ${signed_failure} 篡改意外完成安装" >&2
    exit 1
  fi
  test ! -L "${TEST_ROOT}/install-${signed_failure}/runtime/current"
done

cp "${ORIGINAL_CLIENT_JAR}" "${RELEASE_DIR}/test-agent-local-client.jar"
cp "${ORIGINAL_CLIENT_SIGNATURE}" "${RELEASE_DIR}/test-agent-local-client.jar.sig"
cp "${ORIGINAL_OPENCODE_ARCHIVE}" "${RELEASE_DIR}/opencode.tar.gz"
cp "${ORIGINAL_OPENCODE_SIGNATURE}" "${RELEASE_DIR}/opencode.tar.gz.sig"
cp "${ORIGINAL_MANIFEST_SIGNATURE}" "${RELEASE_DIR}/manifest.json.sig"

ORIGINAL_JDK_ARCHIVE="${TEST_ROOT}/original-jdk.tar.gz"
ORIGINAL_JDK_SIGNATURE="${TEST_ROOT}/original-jdk.tar.gz.sig"
cp "${RELEASE_DIR}/jdk.tar.gz" "${ORIGINAL_JDK_ARCHIVE}"
cp "${RELEASE_DIR}/jdk.tar.gz.sig" "${ORIGINAL_JDK_SIGNATURE}"
for failure_case in truncated-archive invalid-signature invalid-sha256; do
  cp "${ORIGINAL_JDK_ARCHIVE}" "${RELEASE_DIR}/jdk.tar.gz"
  cp "${ORIGINAL_JDK_SIGNATURE}" "${RELEASE_DIR}/jdk.tar.gz.sig"
  case "${failure_case}" in
    truncated-archive)
      printf 'damaged' >"${RELEASE_DIR}/jdk.tar.gz"
      ;;
    invalid-signature)
      printf 'invalid signature' >"${RELEASE_DIR}/jdk.tar.gz.sig"
      ;;
    invalid-sha256)
      python3 - "${RELEASE_DIR}/jdk.tar.gz" <<'PY'
from pathlib import Path
archive = Path(__import__("sys").argv[1])
content = bytearray(archive.read_bytes())
content[-1] ^= 1
archive.write_bytes(content)
PY
      ;;
  esac
  mkdir -p "${TEST_ROOT}/install-${failure_case}/config"
  cat >"${TEST_ROOT}/install-${failure_case}/config/credentials.properties" <<'CREDENTIALS'
unifiedAuthId=test-user
clientKey=tack_v1_test-only-value
CREDENTIALS
  chmod 0700 "${TEST_ROOT}/install-${failure_case}/config"
  chmod 0600 "${TEST_ROOT}/install-${failure_case}/config/credentials.properties"
  : >"${TEST_ROOT}/http.log"
  if PATH="${TOOLS_WITHOUT_JAVA}" \
    TEST_AGENT_LOCAL_CLIENT_TEST_MODE=true \
    TEST_AGENT_LOCAL_CLIENT_TEST_PLATFORM=linux-arm64-glibc \
    TEST_AGENT_LOCAL_CLIENT_INSTALL_ROOT="${TEST_ROOT}/install-${failure_case}/runtime" \
    TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR="${TEST_ROOT}/install-${failure_case}/config" \
    TEST_AGENT_LOCAL_CLIENT_STATE_DIR="${TEST_ROOT}/install-${failure_case}/state" \
    TEST_AGENT_LOCAL_CLIENT_SKIP_SERVICE_START=true \
      sh "${TEST_ROOT}/dist/local-opencode-client/install.sh" >/dev/null 2>&1; then
    echo "损坏 JDK ${failure_case} 意外完成安装" >&2
    exit 1
  fi
  test ! -L "${TEST_ROOT}/install-${failure_case}/runtime/current"
done

echo "Kylin ARM64 single-shell DEB, immutable signed release, catalog and bootstrap verified"
