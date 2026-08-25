#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/local-client-update-test.XXXXXX")"
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

OLD_VERSION=20260820120000
NEW_VERSION=20260820153045
PUBLIC_CONFIG_COMMIT=0123456789abcdef0123456789abcdef01234567
HTTP_PORT="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()')"
DOWNLOAD_ROOT="http://127.0.0.1:${HTTP_PORT}/"
DIST_ROOT="${TEST_ROOT}/dist/local-opencode-client"
INSTALL_ROOT="${TEST_ROOT}/install/runtime"
CONFIG_DIR="${TEST_ROOT}/install/config"
STATE_DIR="${TEST_ROOT}/install/state"
CACHE_INSTALL_ROOT="${TEST_ROOT}/cache-install/runtime"
CACHE_CONFIG_DIR="${TEST_ROOT}/cache-install/config"
CACHE_STATE_DIR="${TEST_ROOT}/cache-install/state"

mkdir -p "${TEST_ROOT}/inputs/jdk/fake-jdk/bin" "${TEST_ROOT}/inputs/opencode"
cat >"${TEST_ROOT}/inputs/jdk/fake-jdk/bin/java" <<'JAVA'
#!/usr/bin/env sh
set -eu

if [ "${1:-}" = "-version" ]; then
  echo 'openjdk version "21.0.9"' >&2
  exit 0
fi
[ "${1:-}" = "-jar" ] || exit 0
[ "${3:-}" != "self-check" ] || exit 0

jar_path="$2"
running_version="$(basename "$(dirname "${jar_path}")")"
state_dir="${TEST_AGENT_LOCAL_CLIENT_STATE_DIR:?}"
target_version="${TEST_FAKE_TARGET_VERSION:-}"
[ -n "${target_version}" ] || exit 0

if [ "${running_version}" = "${target_version}" ]; then
  pending_file="${state_dir}/pending-update.properties"
  activation_temp="${state_dir}/.update-activation.fake"
  [ -f "${pending_file}" ] || exit 43
  cp "${pending_file}" "${activation_temp}"
  case "${TEST_FAKE_ACTIVATION_MODE:-READY}" in
    READY)
      printf 'status=READY\nerrorCode=\nactualVersion=%s\nobservedAt=2026-08-20T08:00:00Z\n' \
        "${running_version}" >>"${activation_temp}"
      ;;
    FAILED)
      printf 'status=FAILED\nerrorCode=OPENCODE_START_FAILED\nactualVersion=%s\nobservedAt=2026-08-20T08:00:00Z\n' \
        "${running_version}" >>"${activation_temp}"
      ;;
    *) exit 43 ;;
  esac
  chmod 0600 "${activation_temp}"
  mv -f "${activation_temp}" "${state_dir}/update-activation.properties"
  sleep 2
  exit 0
fi

# 自动回滚后 update-result 已存在，旧版本只需正常启动，不得重复触发同一命令。
[ ! -f "${state_dir}/update-result.properties" ] || exit 0
pending_temp="${state_dir}/.pending-update.fake"
{
  printf 'schemaVersion=1\n'
  printf 'commandId=lcuc_shell_state_machine\n'
  printf 'clientInstanceId=lci_shell_state_machine\n'
  printf 'connectionGeneration=7\npolicyRevision=11\n'
  printf 'currentVersion=%s\n' "${running_version}"
  printf 'targetVersion=%s\n' "${target_version}"
  printf 'direction=%s\n' "${TEST_FAKE_DIRECTION:?}"
  printf 'releaseDigest=%s\n' "${TEST_FAKE_RELEASE_DIGEST:?}"
  printf 'preparedAt=2026-08-20T07:59:00Z\n'
} >"${pending_temp}"
chmod 0600 "${pending_temp}"
mv -f "${pending_temp}" "${state_dir}/pending-update.properties"
exit 42
JAVA
cat >"${TEST_ROOT}/inputs/jdk/fake-jdk/bin/javac" <<'JAVAC'
#!/usr/bin/env sh
echo 'javac 21.0.9'
exit 0
JAVAC
printf '#!/usr/bin/env sh\nexit 0\n' >"${TEST_ROOT}/inputs/opencode/opencode"
chmod 0755 "${TEST_ROOT}/inputs/jdk/fake-jdk/bin/java" \
  "${TEST_ROOT}/inputs/jdk/fake-jdk/bin/javac" \
  "${TEST_ROOT}/inputs/opencode/opencode"
tar -C "${TEST_ROOT}/inputs/jdk" -czf "${TEST_ROOT}/jdk-linux.tar.gz" fake-jdk
tar -C "${TEST_ROOT}/inputs/opencode" -czf "${TEST_ROOT}/opencode-linux.tar.gz" opencode
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "${TEST_ROOT}/signing-private.pem" >/dev/null 2>&1
mkdir -p "${TEST_ROOT}/public-capabilities/public-capabilities/agents"
printf '%s\n' 'public test agent' >"${TEST_ROOT}/public-capabilities/public-capabilities/agents/test.md"
printf '{"schemaVersion":1,"sourceCommit":"%s"}\n' "${PUBLIC_CONFIG_COMMIT}" \
  >"${TEST_ROOT}/public-capabilities/public-capabilities/manifest.json"
tar -C "${TEST_ROOT}/public-capabilities" -czf "${TEST_ROOT}/public-capabilities.tar.gz" public-capabilities

package_release() {
  local version="$1"
  printf 'fake client %s\n' "${version}" >"${TEST_ROOT}/test-agent-local-client-${version}.jar"
  TEST_AGENT_LOCAL_CLIENT_JDK_LINUX_ARM64_GLIBC_ARCHIVE="${TEST_ROOT}/jdk-linux.tar.gz" \
  TEST_AGENT_LOCAL_CLIENT_JDK_LINUX_ARM64_GLIBC_SHA256="$(sha256_file "${TEST_ROOT}/jdk-linux.tar.gz")" \
  TEST_AGENT_LOCAL_CLIENT_OPENCODE_LINUX_ARM64_GLIBC_ARCHIVE="${TEST_ROOT}/opencode-linux.tar.gz" \
  TEST_AGENT_LOCAL_CLIENT_OPENCODE_LINUX_ARM64_GLIBC_SHA256="$(sha256_file "${TEST_ROOT}/opencode-linux.tar.gz")" \
    "${ROOT_DIR}/deploy/internal/package-local-opencode-client.sh" \
      --output-dir "${DIST_ROOT}" \
      --version "${version}" \
      --download-base-url "${DOWNLOAD_ROOT}" \
      --server-url https://platform.example.internal \
      --signing-key "${TEST_ROOT}/signing-private.pem" \
      --client-jar "${TEST_ROOT}/test-agent-local-client-${version}.jar" \
      --public-config-commit "${PUBLIC_CONFIG_COMMIT}" \
      --public-capability-bundle "${TEST_ROOT}/public-capabilities.tar.gz" \
      --skip-build >/dev/null
}

run_launcher() {
  local target_version="$1" direction="$2" activation_mode="$3" release_digest
  release_digest="$(sha256_file "${DIST_ROOT}/releases/${target_version}/manifest.json")"
  if [[ ! -d "${INSTALL_ROOT}/releases/${target_version}" ]]; then
    cp -R "${DIST_ROOT}/releases/${target_version}" "${INSTALL_ROOT}/releases/${target_version}"
    tar -C "${INSTALL_ROOT}/releases/${target_version}" -xzf \
      "${INSTALL_ROOT}/releases/${target_version}/jdk.tar.gz"
    tar -C "${INSTALL_ROOT}/releases/${target_version}" -xzf \
      "${INSTALL_ROOT}/releases/${target_version}/opencode.tar.gz"
  fi
  TEST_AGENT_LOCAL_CLIENT_TEST_MODE=true \
  TEST_AGENT_LOCAL_CLIENT_TEST_PLATFORM=linux-arm64-glibc \
  TEST_AGENT_LOCAL_CLIENT_INSTALL_ROOT="${INSTALL_ROOT}" \
  TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR="${CONFIG_DIR}" \
  TEST_AGENT_LOCAL_CLIENT_STATE_DIR="${STATE_DIR}" \
  TEST_AGENT_LOCAL_CLIENT_SKIP_SERVICE_START=true \
  TEST_AGENT_LOCAL_CLIENT_ACTIVATION_TIMEOUT_SECONDS=5 \
  TEST_FAKE_TARGET_VERSION="${target_version}" \
  TEST_FAKE_DIRECTION="${direction}" \
  TEST_FAKE_RELEASE_DIGEST="${release_digest}" \
  TEST_FAKE_ACTIVATION_MODE="${activation_mode}" \
    sh "${DIST_ROOT}/install.sh" run
}

clear_update_markers() {
  rm -f "${STATE_DIR}/pending-update.properties" \
    "${STATE_DIR}/update-activation.properties" \
    "${STATE_DIR}/update-result.properties"
}

package_release "${OLD_VERSION}"
python3 -m http.server "${HTTP_PORT}" --bind 127.0.0.1 \
  --directory "${DIST_ROOT}" >"${TEST_ROOT}/http.log" 2>&1 &
HTTP_PID="$!"
for _ in $(seq 1 30); do
  curl -fsS "${DOWNLOAD_ROOT}catalog.json" >/dev/null 2>&1 && break
  sleep 0.1
done

# 模拟没有系统 JDK 的普通用户机器，验证后续版本只下载发生变化的签名制品。
TOOLS_WITHOUT_JAVA="${TEST_ROOT}/tools-without-java"
mkdir -p "${TOOLS_WITHOUT_JAVA}"
for tool in awk basename chmod cmp cp curl dirname env expr find getconf grep hostname ldd ln mkdir mktemp mv \
  openssl readlink rm rmdir sh sha256sum shasum sort stat tail tar tr wc; do
  tool_path="$(command -v "${tool}" || true)"
  [[ -n "${tool_path}" ]] || continue
  ln -s "${tool_path}" "${TOOLS_WITHOUT_JAVA}/${tool}"
done
mkdir -p "${CACHE_CONFIG_DIR}"
cat >"${CACHE_CONFIG_DIR}/credentials.properties" <<'CREDENTIALS'
unifiedAuthId=test-user
clientKey=tack_v1_test-only-value
CREDENTIALS
chmod 0700 "${CACHE_CONFIG_DIR}"
chmod 0600 "${CACHE_CONFIG_DIR}/credentials.properties"
PATH="${TOOLS_WITHOUT_JAVA}" \
TEST_AGENT_LOCAL_CLIENT_TEST_MODE=true \
TEST_AGENT_LOCAL_CLIENT_TEST_PLATFORM=linux-arm64-glibc \
TEST_AGENT_LOCAL_CLIENT_INSTALL_ROOT="${CACHE_INSTALL_ROOT}" \
TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR="${CACHE_CONFIG_DIR}" \
TEST_AGENT_LOCAL_CLIENT_STATE_DIR="${CACHE_STATE_DIR}" \
TEST_AGENT_LOCAL_CLIENT_SKIP_SERVICE_START=true \
  sh "${DIST_ROOT}/install.sh" setup >/dev/null
[ "$(readlink "${CACHE_INSTALL_ROOT}/current")" = "releases/${OLD_VERSION}" ]
[ -f "${CACHE_INSTALL_ROOT}/current/jdk.tar.gz" ]

mkdir -p "${CONFIG_DIR}"
cat >"${CONFIG_DIR}/credentials.properties" <<'CREDENTIALS'
unifiedAuthId=test-user
clientKey=tack_v1_test-only-value
clientInstanceId=lci_shell_state_machine
CREDENTIALS
chmod 0700 "${CONFIG_DIR}"
chmod 0600 "${CONFIG_DIR}/credentials.properties"
PATH="${TEST_ROOT}/inputs/jdk/fake-jdk/bin:${PATH}" \
TEST_AGENT_LOCAL_CLIENT_TEST_MODE=true \
TEST_AGENT_LOCAL_CLIENT_TEST_PLATFORM=linux-arm64-glibc \
TEST_AGENT_LOCAL_CLIENT_INSTALL_ROOT="${INSTALL_ROOT}" \
TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR="${CONFIG_DIR}" \
TEST_AGENT_LOCAL_CLIENT_STATE_DIR="${STATE_DIR}" \
TEST_AGENT_LOCAL_CLIENT_SKIP_SERVICE_START=true \
  sh "${DIST_ROOT}/install.sh"
[ "$(readlink "${INSTALL_ROOT}/current")" = "releases/${OLD_VERSION}" ]
grep -qx 'source=system-jdk21' "${INSTALL_ROOT}/current/jdk.provenance"
credential_digest="$(sha256_file "${CONFIG_DIR}/credentials.properties")"
printf '{"clientInstanceId":"lci_shell_state_machine","processIdentity":null,"workspaceRoots":{}}\n' \
  >"${STATE_DIR}/state.json"
chmod 0600 "${STATE_DIR}/state.json"
state_digest="$(sha256_file "${STATE_DIR}/state.json")"

package_release "${NEW_VERSION}"
old_jdk_digest="$(sha256_file "${DIST_ROOT}/releases/${OLD_VERSION}/jdk.tar.gz")"
new_jdk_digest="$(sha256_file "${DIST_ROOT}/releases/${NEW_VERSION}/jdk.tar.gz")"
old_opencode_digest="$(sha256_file "${DIST_ROOT}/releases/${OLD_VERSION}/opencode.tar.gz")"
new_opencode_digest="$(sha256_file "${DIST_ROOT}/releases/${NEW_VERSION}/opencode.tar.gz")"
[[ "${old_jdk_digest}" == "${new_jdk_digest}" ]] || {
  echo "相同 JDK 输入生成了不同发布摘要" >&2
  exit 1
}
[[ "${old_opencode_digest}" == "${new_opencode_digest}" ]] || {
  echo "相同 OpenCode 输入生成了不同发布摘要" >&2
  exit 1
}

cache_http_start_line=$(( $(wc -l <"${TEST_ROOT}/http.log") + 1 ))
cache_setup_output="$(
  PATH="${TOOLS_WITHOUT_JAVA}" \
  TEST_AGENT_LOCAL_CLIENT_TEST_MODE=true \
  TEST_AGENT_LOCAL_CLIENT_TEST_PLATFORM=linux-arm64-glibc \
  TEST_AGENT_LOCAL_CLIENT_INSTALL_ROOT="${CACHE_INSTALL_ROOT}" \
  TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR="${CACHE_CONFIG_DIR}" \
  TEST_AGENT_LOCAL_CLIENT_STATE_DIR="${CACHE_STATE_DIR}" \
  TEST_AGENT_LOCAL_CLIENT_SKIP_SERVICE_START=true \
    sh "${DIST_ROOT}/install.sh" setup
)"
tail -n "+${cache_http_start_line}" "${TEST_ROOT}/http.log" >"${TEST_ROOT}/cache-http.log"
[ "$(readlink "${CACHE_INSTALL_ROOT}/current")" = "releases/${NEW_VERSION}" ]
grep -q '复用已校验的 JDK 本机缓存' <<<"${cache_setup_output}"
grep -q '复用已校验的 OPENCODE 本机缓存' <<<"${cache_setup_output}"
grep -q '复用已校验的 PUBLIC_CAPABILITIES 本机缓存' <<<"${cache_setup_output}"
grep -q "/releases/${NEW_VERSION}/test-agent-local-client.jar " "${TEST_ROOT}/cache-http.log"
if grep -Eq "/releases/${NEW_VERSION}/(jdk|opencode|public-capabilities)\.tar\.gz(\.sig)? " \
    "${TEST_ROOT}/cache-http.log"; then
  echo "未变化的客户端依赖被重复下载" >&2
  exit 1
fi

run_launcher "${NEW_VERSION}" UPDATE READY
[ "$(readlink "${INSTALL_ROOT}/current")" = "releases/${NEW_VERSION}" ]
grep -q '^status=SUCCEEDED$' "${STATE_DIR}/update-result.properties"
grep -q '^direction=UPDATE$' "${STATE_DIR}/update-result.properties"
[ "$(sha256_file "${CONFIG_DIR}/credentials.properties")" = "${credential_digest}" ]

clear_update_markers
run_launcher "${OLD_VERSION}" ROLLBACK READY
[ "$(readlink "${INSTALL_ROOT}/current")" = "releases/${OLD_VERSION}" ]
grep -q '^status=SUCCEEDED$' "${STATE_DIR}/update-result.properties"
grep -q '^direction=ROLLBACK$' "${STATE_DIR}/update-result.properties"
[ "$(sha256_file "${CONFIG_DIR}/credentials.properties")" = "${credential_digest}" ]

clear_update_markers
run_launcher "${NEW_VERSION}" UPDATE FAILED
[ "$(readlink "${INSTALL_ROOT}/current")" = "releases/${OLD_VERSION}" ]
grep -q '^status=AUTO_ROLLED_BACK$' "${STATE_DIR}/update-result.properties"
grep -q '^errorCode=OPENCODE_START_FAILED$' "${STATE_DIR}/update-result.properties"
grep -q '^clientInstanceId=lci_shell_state_machine$' "${CONFIG_DIR}/credentials.properties"
[ "$(sha256_file "${CONFIG_DIR}/credentials.properties")" = "${credential_digest}" ]

# 用户重新双击安装包时必须升级到签名 catalog 最新版本，同时保留稳定实例状态和凭据。
TEST_AGENT_LOCAL_CLIENT_TEST_MODE=true \
TEST_AGENT_LOCAL_CLIENT_TEST_PLATFORM=linux-arm64-glibc \
TEST_AGENT_LOCAL_CLIENT_INSTALL_ROOT="${INSTALL_ROOT}" \
TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR="${CONFIG_DIR}" \
TEST_AGENT_LOCAL_CLIENT_STATE_DIR="${STATE_DIR}" \
TEST_AGENT_LOCAL_CLIENT_SKIP_SERVICE_START=true \
  sh "${DIST_ROOT}/install.sh" setup
[ "$(readlink "${INSTALL_ROOT}/current")" = "releases/${NEW_VERSION}" ]
[ "$(sha256_file "${CONFIG_DIR}/credentials.properties")" = "${credential_digest}" ]
[ "$(sha256_file "${STATE_DIR}/state.json")" = "${state_digest}" ]

echo "Stable launcher update, signed dependency cache reuse, automatic rollback and stable identity reuse verified"
