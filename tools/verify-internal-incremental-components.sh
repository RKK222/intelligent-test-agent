#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_SCRIPT="${ROOT_DIR}/deploy/internal/package-release.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-incremental-components.XXXXXX")"
OUTPUT_DIR="${TMP_ROOT}/dist"
STATE_FILE="${OUTPUT_DIR}/component-state.env"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

mkdir -p "${OUTPUT_DIR}/backend/lib"
printf 'backend\n' >"${OUTPUT_DIR}/backend/test-agent-app.jar"
# 增量封装也必须经过正式 Flyway 字节门禁；夹具复制当前完整 migration 资源，避免新增兼容历史后漏测。
PERSISTENCE_JAR_ROOT="${TMP_ROOT}/persistence-jar-root"
mkdir -p "${PERSISTENCE_JAR_ROOT}/db"
cp -a "${ROOT_DIR}/backend/test-agent-persistence/src/main/resources/db/migration" \
  "${PERSISTENCE_JAR_ROOT}/db/"
cp -a "${ROOT_DIR}/backend/test-agent-persistence/src/main/resources/db/migration-compat" \
  "${PERSISTENCE_JAR_ROOT}/db/"
cp -a "${ROOT_DIR}/backend/test-agent-persistence/src/main/resources/db/migration-postgresql" \
  "${PERSISTENCE_JAR_ROOT}/db/"
cp -a "${ROOT_DIR}/backend/test-agent-persistence/src/main/resources/db/clickhouse" \
  "${PERSISTENCE_JAR_ROOT}/db/"
(cd "${PERSISTENCE_JAR_ROOT}" && zip -qr \
  "${OUTPUT_DIR}/backend/lib/test-agent-persistence-0.1.0-SNAPSHOT.jar" .)
XXL_JAR_ROOT="${TMP_ROOT}/xxl-jar-root"
mkdir -p "${XXL_JAR_ROOT}/xxl-job/db"
cp -a "${ROOT_DIR}/backend/test-agent-xxl-job-integration/src/main/resources/xxl-job/db/." \
  "${XXL_JAR_ROOT}/xxl-job/db/"
(cd "${XXL_JAR_ROOT}" && zip -qr \
  "${OUTPUT_DIR}/backend/lib/test-agent-xxl-job-integration-0.1.0-SNAPSHOT.jar" .)
printf 'frontend\n' >"${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz"
# zip-only 测试复用已生成制品，显式补齐可逐文件验真的客户端目录，避免把构建阶段误当作封装阶段。
LOCAL_CLIENT_ROOT="${OUTPUT_DIR}/local-opencode-client"
LOCAL_CLIENT_VERSION="20260820153045"
LOCAL_CLIENT_RELEASE="${LOCAL_CLIENT_ROOT}/releases/${LOCAL_CLIENT_VERSION}"
mkdir -p "${LOCAL_CLIENT_ROOT}/stable" "${LOCAL_CLIENT_RELEASE}"
printf '#!/usr/bin/env bash\nexit 0\n' >"${OUTPUT_DIR}/local-opencode-client/install.sh"
printf 'fixture deb\n' >"${OUTPUT_DIR}/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.deb"
cp "${OUTPUT_DIR}/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.deb" \
  "${OUTPUT_DIR}/local-opencode-client/test-agent-local-client_${LOCAL_CLIENT_VERSION}_arm64.deb"
printf 'fixture client jar\n' >"${LOCAL_CLIENT_RELEASE}/test-agent-local-client.jar"
printf 'fixture Linux JDK\n' >"${LOCAL_CLIENT_RELEASE}/jdk.tar.gz"
printf 'fixture Linux OpenCode\n' >"${LOCAL_CLIENT_RELEASE}/opencode.tar.gz"
for artifact in test-agent-local-client.jar jdk.tar.gz opencode.tar.gz; do
  printf 'fixture signature for %s\n' "${artifact}" >"${LOCAL_CLIENT_RELEASE}/${artifact}.sig"
done
client_jar_sha="$(shasum -a 256 "${LOCAL_CLIENT_RELEASE}/test-agent-local-client.jar" | awk '{print $1}')"
jdk_sha="$(shasum -a 256 "${LOCAL_CLIENT_RELEASE}/jdk.tar.gz" | awk '{print $1}')"
opencode_sha="$(shasum -a 256 "${LOCAL_CLIENT_RELEASE}/opencode.tar.gz" | awk '{print $1}')"
client_jar_size="$(wc -c <"${LOCAL_CLIENT_RELEASE}/test-agent-local-client.jar" | tr -d ' ')"
jdk_size="$(wc -c <"${LOCAL_CLIENT_RELEASE}/jdk.tar.gz" | tr -d ' ')"
opencode_size="$(wc -c <"${LOCAL_CLIENT_RELEASE}/opencode.tar.gz" | tr -d ' ')"
printf '%s\n' \
  '{' \
  '  "schemaVersion": 2,' \
  "  \"version\": \"${LOCAL_CLIENT_VERSION}\"," \
  '  "publishedAt": "2026-08-20T07:30:45Z",' \
  '  "platform": "linux",' \
  '  "architecture": "arm64",' \
  '  "launcherVersionMin": 1,' \
  '  "launcherVersionMax": 1,' \
  '  "protocolVersion": "local-opencode-client.v1",' \
  '  "opencodeVersion": "1.18.4",' \
  '  "artifacts": [' \
  "    {\"kind\": \"CLIENT_JAR\", \"path\": \"releases/${LOCAL_CLIENT_VERSION}/test-agent-local-client.jar\", \"size\": ${client_jar_size}, \"sha256\": \"${client_jar_sha}\", \"signaturePath\": \"releases/${LOCAL_CLIENT_VERSION}/test-agent-local-client.jar.sig\"}," \
  "    {\"kind\": \"JDK\", \"path\": \"releases/${LOCAL_CLIENT_VERSION}/jdk.tar.gz\", \"size\": ${jdk_size}, \"sha256\": \"${jdk_sha}\", \"signaturePath\": \"releases/${LOCAL_CLIENT_VERSION}/jdk.tar.gz.sig\"}," \
  "    {\"kind\": \"OPENCODE\", \"path\": \"releases/${LOCAL_CLIENT_VERSION}/opencode.tar.gz\", \"size\": ${opencode_size}, \"sha256\": \"${opencode_sha}\", \"signaturePath\": \"releases/${LOCAL_CLIENT_VERSION}/opencode.tar.gz.sig\"}" \
  '  ]' \
  '}' >"${LOCAL_CLIENT_RELEASE}/manifest.json"
printf 'fixture manifest signature\n' >"${LOCAL_CLIENT_RELEASE}/manifest.json.sig"
cp "${LOCAL_CLIENT_RELEASE}/manifest.json" "${LOCAL_CLIENT_ROOT}/stable/manifest.json"
cp "${LOCAL_CLIENT_RELEASE}/manifest.json.sig" "${LOCAL_CLIENT_ROOT}/stable/manifest.json.sig"
manifest_sha="$(shasum -a 256 "${LOCAL_CLIENT_RELEASE}/manifest.json" | awk '{print $1}')"
printf '%s\n' \
  '{' \
  '  "schemaVersion": 1,' \
  '  "releases": [' \
  "    {\"version\": \"${LOCAL_CLIENT_VERSION}\", \"manifestPath\": \"releases/${LOCAL_CLIENT_VERSION}/manifest.json\", \"manifestSha256\": \"${manifest_sha}\", \"manifestSignaturePath\": \"releases/${LOCAL_CLIENT_VERSION}/manifest.json.sig\"}" \
  '  ]' \
  '}' >"${LOCAL_CLIENT_ROOT}/catalog.json"
printf 'fixture catalog signature\n' >"${LOCAL_CLIENT_ROOT}/catalog.json.sig"
printf 'programs\n' >"${OUTPUT_DIR}/test-agent-programs.tar.gz"
printf 'worker\n' >"${OUTPUT_DIR}/test-agent-opencode-worker_internal-linux-amd64.tar"
printf 'it-tools\n' >"${OUTPUT_DIR}/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar"
printf 'fixture checksum\n' >"${OUTPUT_DIR}/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar.sha256"
printf 'omni-tools\n' >"${OUTPUT_DIR}/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar"
printf 'fixture checksum\n' >"${OUTPUT_DIR}/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar.sha256"
printf 'source\n' >"${OUTPUT_DIR}/test-agent-toolbox-source.tar.gz"
printf 'fixture checksum\n' >"${OUTPUT_DIR}/test-agent-toolbox-source.tar.gz.sha256"
printf 'catalog\n' >"${OUTPUT_DIR}/toolbox-catalog-v1.json"
printf 'fixture checksum\n' >"${OUTPUT_DIR}/toolbox-catalog-v1.json.sha256"
printf '# toolbox fixture\n' >"${OUTPUT_DIR}/TOOLBOX.md"

plan_output="$(bash "${PACKAGE_SCRIPT}" --component-plan-only \
  --output-dir "${OUTPUT_DIR}" --component-state-file "${STATE_FILE}")"
worker_fingerprint="$(sed -n 's/^worker runtime fingerprint: //p' <<<"${plan_output}")"
toolbox_fingerprint="$(sed -n 's/^toolbox fingerprint: //p' <<<"${plan_output}")"
local_client_fingerprint="$(sed -n 's/^local client fingerprint: //p' <<<"${plan_output}")"
[[ "${worker_fingerprint}" =~ ^[0-9a-f]{64}$ \
  && "${toolbox_fingerprint}" =~ ^[0-9a-f]{64}$ \
  && "${local_client_fingerprint}" =~ ^[0-9a-f]{64}$ ]]
printf 'TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=%s\n' "${worker_fingerprint}" \
  >"${OUTPUT_DIR}/.worker-runtime-artifact.env"
printf 'TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT=%s\n' "${toolbox_fingerprint}" \
  >"${OUTPUT_DIR}/.toolbox-artifact.env"

# 没有历史状态时必须生成全量包，并把本次指纹记录为下次增量基线。
bash "${PACKAGE_SCRIPT}" --zip-only --output-dir "${OUTPUT_DIR}" \
  --component-state-file "${STATE_FILE}" >/dev/null
full_listing="$(unzip -Z1 "${OUTPUT_DIR}/test-agent-internal-release.zip")"
grep -Fxq 'dist/test-agent-programs.tar.gz' <<<"${full_listing}"
grep -Fxq 'dist/test-agent-opencode-worker_internal-linux-amd64.tar' <<<"${full_listing}"
grep -Fxq 'deploy/internal/deploy-python-libs.sh' <<<"${full_listing}"
grep -Fxq 'deploy/internal/verify-python-libs.sh' <<<"${full_listing}"
grep -Fxq 'dist/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar' <<<"${full_listing}"
grep -Fxq 'dist/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar' <<<"${full_listing}"
grep -Fxq 'dist/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.deb' <<<"${full_listing}"
if grep -Eq '^dist/lobehub/' <<<"${full_listing}"; then
  echo 'Default release unexpectedly contains LobeHub artifacts' >&2
  exit 1
fi
full_manifest="$(unzip -p "${OUTPUT_DIR}/test-agent-internal-release.zip" \
  deploy/internal/release-components.env)"
grep -Fxq 'TEST_AGENT_RELEASE_LOBEHUB=disabled' <<<"${full_manifest}"
grep -Fxq 'TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT=included' <<<"${full_manifest}"
grep -Fxq "TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=${worker_fingerprint}" "${STATE_FILE}"
grep -Fxq "TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT=${toolbox_fingerprint}" "${STATE_FILE}"
grep -Fxq "TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_FINGERPRINT=${local_client_fingerprint}" \
  "${STATE_FILE}"
local_client_version="$(sed -n 's/^TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_VERSION=//p' "${STATE_FILE}")"
local_client_manifest_sha="$(sed -n 's/^TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_MANIFEST_SHA256=//p' "${STATE_FILE}")"
local_client_signature_sha="$(sed -n 's/^TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_SIGNATURE_SHA256=//p' "${STATE_FILE}")"
local_client_install_sha="$(sed -n 's/^TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_INSTALL_SHA256=//p' "${STATE_FILE}")"
bash "${ROOT_DIR}/deploy/internal/deploy-internal-frontend.sh" \
  --archive "${OUTPUT_DIR}/test-agent-internal-release.zip" --validate-only >/dev/null
bash "${ROOT_DIR}/deploy/internal/verify-local-opencode-client-distribution.sh" \
  --root "${LOCAL_CLIENT_ROOT}" \
  --expected-version "${local_client_version}" \
  --expected-manifest-sha256 "${local_client_manifest_sha}" \
  --expected-signature-sha256 "${local_client_signature_sha}" \
  --expected-install-sha256 "${local_client_install_sha}" >/dev/null
printf 'tampered deb alias\n' >"${LOCAL_CLIENT_ROOT}/TestAgent-Local-Client-Kylin-arm64.deb"
if bash "${ROOT_DIR}/deploy/internal/verify-local-opencode-client-distribution.sh" \
  --root "${LOCAL_CLIENT_ROOT}" \
  --expected-version "${local_client_version}" \
  --expected-manifest-sha256 "${local_client_manifest_sha}" \
  --expected-signature-sha256 "${local_client_signature_sha}" \
  --expected-install-sha256 "${local_client_install_sha}" >/dev/null 2>&1; then
  echo 'Tampered local client distribution unexpectedly passed verification' >&2
  exit 1
fi
cp "${LOCAL_CLIENT_ROOT}/test-agent-local-client_${LOCAL_CLIENT_VERSION}_arm64.deb" \
  "${LOCAL_CLIENT_ROOT}/TestAgent-Local-Client-Kylin-arm64.deb"

# 同一批次只补日志的 zip-only 必须保留当前全量选择，不能把尚未部署的组件误删掉。
bash "${PACKAGE_SCRIPT}" --zip-only --output-dir "${OUTPUT_DIR}" \
  --component-state-file "${STATE_FILE}" >/dev/null
repacked_listing="$(unzip -Z1 "${OUTPUT_DIR}/test-agent-internal-release.zip")"
grep -Fxq 'dist/test-agent-programs.tar.gz' <<<"${repacked_listing}"
grep -Fxq 'dist/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar' <<<"${repacked_listing}"

# 模拟下一次正常发布：源码指纹未变化时，大体积 runtime 与 toolbox 制品必须全部省略。
rm -f "${OUTPUT_DIR}/test-agent-internal-release.zip" \
  "${OUTPUT_DIR}/test-agent-internal-release.zip.sha256"
bash "${PACKAGE_SCRIPT}" --zip-only --output-dir "${OUTPUT_DIR}" \
  --component-state-file "${STATE_FILE}" >/dev/null
incremental_listing="$(unzip -Z1 "${OUTPUT_DIR}/test-agent-internal-release.zip")"
if grep -Eq '^dist/(test-agent-programs|test-agent-opencode-worker|test-agent_(it|omni)-tools|test-agent-toolbox-source|toolbox-catalog)' \
  <<<"${incremental_listing}"; then
  echo 'Unchanged worker runtime or toolbox artifacts leaked into incremental ZIP' >&2
  exit 1
fi
if grep -Eq '^dist/local-opencode-client/' <<<"${incremental_listing}"; then
  echo 'Unchanged local client artifacts leaked into incremental ZIP' >&2
  exit 1
fi
incremental_manifest="$(unzip -p "${OUTPUT_DIR}/test-agent-internal-release.zip" \
  deploy/internal/release-components.env)"
grep -Fxq 'TEST_AGENT_RELEASE_WORKER_RUNTIME=reuse' <<<"${incremental_manifest}"
grep -Fxq 'TEST_AGENT_RELEASE_TOOLBOX=reuse' <<<"${incremental_manifest}"
grep -Fxq 'TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT=reuse' <<<"${incremental_manifest}"
bash "${ROOT_DIR}/deploy/internal/deploy-internal-frontend.sh" \
  --archive "${OUTPUT_DIR}/test-agent-internal-release.zip" --validate-only >/dev/null

# 上一轮已部署成功但目标机尚无组件状态时，可把可信 release 指纹作为一次性恢复基线。
BASELINE_FILE="${TMP_ROOT}/worker-runtime-baseline.env"
printf '%s\n' \
  'TEST_AGENT_RELEASE_WORKER_RUNTIME_BASELINE_VERSION=1' \
  "TEST_AGENT_RELEASE_WORKER_RUNTIME_BASELINE_SOURCE_COMMIT=$(git -C "${ROOT_DIR}" rev-parse HEAD)" \
  'TEST_AGENT_RELEASE_WORKER_RUNTIME_BASELINE_RELEASE_SHA256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' \
  "TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=${worker_fingerprint}" \
  >"${BASELINE_FILE}"
bash "${PACKAGE_SCRIPT}" --zip-only --worker-runtime-baseline-file "${BASELINE_FILE}" \
  --output-dir "${OUTPUT_DIR}" --component-state-file "${STATE_FILE}" >/dev/null
baseline_listing="$(unzip -Z1 "${OUTPUT_DIR}/test-agent-internal-release.zip")"
if grep -Eq '^dist/(test-agent-programs|test-agent-opencode-worker)' <<<"${baseline_listing}"; then
  echo 'Worker artifacts leaked into deployed-baseline reuse ZIP' >&2
  exit 1
fi
baseline_manifest="$(unzip -p "${OUTPUT_DIR}/test-agent-internal-release.zip" \
  deploy/internal/release-components.env)"
grep -Fxq 'TEST_AGENT_RELEASE_WORKER_RUNTIME=reuse' <<<"${baseline_manifest}"
grep -Fxq "TEST_AGENT_RELEASE_WORKER_RUNTIME_BASELINE_SOURCE_COMMIT=$(git -C "${ROOT_DIR}" rev-parse HEAD)" \
  <<<"${baseline_manifest}"
grep -Fxq 'TEST_AGENT_RELEASE_WORKER_RUNTIME_BASELINE_RELEASE_SHA256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' \
  <<<"${baseline_manifest}"

# 已部署客户端早于本地组件状态时，只允许通过固定源码提交、指纹和制品哈希的 baseline 恢复 reuse。
LOCAL_CLIENT_BASELINE_FILE="${TMP_ROOT}/local-client-baseline.env"
printf '%s\n' \
  'TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_BASELINE_VERSION=1' \
  "TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_BASELINE_SOURCE_COMMIT=$(git -C "${ROOT_DIR}" rev-parse HEAD)" \
  "TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_FINGERPRINT=${local_client_fingerprint}" \
  "TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_VERSION=${local_client_version}" \
  "TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_MANIFEST_SHA256=${local_client_manifest_sha}" \
  "TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_SIGNATURE_SHA256=${local_client_signature_sha}" \
  "TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_INSTALL_SHA256=${local_client_install_sha}" \
  >"${LOCAL_CLIENT_BASELINE_FILE}"
printf '%s\n' \
  'TEST_AGENT_RELEASE_COMPONENT_STATE_VERSION=1' \
  "TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=${worker_fingerprint}" \
  "TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT=${toolbox_fingerprint}" \
  >"${STATE_FILE}"
rm -f "${OUTPUT_DIR}/test-agent-internal-release.zip" \
  "${OUTPUT_DIR}/test-agent-internal-release.zip.sha256"
bash "${PACKAGE_SCRIPT}" --zip-only \
  --local-client-baseline-file "${LOCAL_CLIENT_BASELINE_FILE}" \
  --output-dir "${OUTPUT_DIR}" --component-state-file "${STATE_FILE}" >/dev/null
client_baseline_listing="$(unzip -Z1 "${OUTPUT_DIR}/test-agent-internal-release.zip")"
if grep -Eq '^dist/local-opencode-client/' <<<"${client_baseline_listing}"; then
  echo 'Local client artifacts leaked into deployed-baseline reuse ZIP' >&2
  exit 1
fi
client_baseline_manifest="$(unzip -p "${OUTPUT_DIR}/test-agent-internal-release.zip" \
  deploy/internal/release-components.env)"
grep -Fxq 'TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT=reuse' <<<"${client_baseline_manifest}"
grep -Fxq "TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_MANIFEST_SHA256=${local_client_manifest_sha}" \
  <<<"${client_baseline_manifest}"

# 只有 worker runtime 基线变化时，只重新携带 Manager/Codex/programs 与 worker 镜像。
printf '%s\n' \
  'TEST_AGENT_RELEASE_COMPONENT_STATE_VERSION=1' \
  'TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=outdated' \
  "TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT=${toolbox_fingerprint}" \
  "TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_FINGERPRINT=${local_client_fingerprint}" \
  "TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_VERSION=${local_client_version}" \
  "TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_MANIFEST_SHA256=${local_client_manifest_sha}" \
  "TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_SIGNATURE_SHA256=${local_client_signature_sha}" \
  "TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_INSTALL_SHA256=${local_client_install_sha}" \
  >"${STATE_FILE}"
rm -f "${OUTPUT_DIR}/test-agent-internal-release.zip" \
  "${OUTPUT_DIR}/test-agent-internal-release.zip.sha256"
bash "${PACKAGE_SCRIPT}" --zip-only --output-dir "${OUTPUT_DIR}" \
  --component-state-file "${STATE_FILE}" >/dev/null
worker_only_listing="$(unzip -Z1 "${OUTPUT_DIR}/test-agent-internal-release.zip")"
grep -Fxq 'dist/test-agent-programs.tar.gz' <<<"${worker_only_listing}"
grep -Fxq 'dist/test-agent-opencode-worker_internal-linux-amd64.tar' <<<"${worker_only_listing}"
if grep -Eq '^dist/test-agent_(it|omni)-tools_' <<<"${worker_only_listing}"; then
  echo 'Unchanged toolbox artifacts were unexpectedly included' >&2
  exit 1
fi
if grep -Eq '^dist/local-opencode-client/' <<<"${worker_only_listing}"; then
  echo 'Unchanged local client artifacts were unexpectedly included' >&2
  exit 1
fi

echo 'Incremental components and explicit LobeHub release gate verified'
