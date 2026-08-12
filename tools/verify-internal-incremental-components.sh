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
(cd "${PERSISTENCE_JAR_ROOT}" && zip -qr \
  "${OUTPUT_DIR}/backend/lib/test-agent-persistence-0.1.0-SNAPSHOT.jar" .)
XXL_JAR_ROOT="${TMP_ROOT}/xxl-jar-root"
mkdir -p "${XXL_JAR_ROOT}/xxl-job/db"
cp -a "${ROOT_DIR}/backend/test-agent-xxl-job-integration/src/main/resources/xxl-job/db/migration" \
  "${XXL_JAR_ROOT}/xxl-job/db/"
(cd "${XXL_JAR_ROOT}" && zip -qr \
  "${OUTPUT_DIR}/backend/lib/test-agent-xxl-job-integration-0.1.0-SNAPSHOT.jar" .)
printf 'frontend\n' >"${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz"
# zip-only 测试复用已生成制品，显式补齐本地客户端分发目录，避免把构建阶段误当作封装阶段。
mkdir -p "${OUTPUT_DIR}/local-opencode-client/stable"
printf '#!/usr/bin/env bash\nexit 0\n' >"${OUTPUT_DIR}/local-opencode-client/install.sh"
printf '{\n  "version": "fixture-local-client"\n}\n' \
  >"${OUTPUT_DIR}/local-opencode-client/stable/manifest.json"
printf 'fixture signature\n' >"${OUTPUT_DIR}/local-opencode-client/stable/manifest.json.sig"
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
[[ "${worker_fingerprint}" =~ ^[0-9a-f]{64}$ && "${toolbox_fingerprint}" =~ ^[0-9a-f]{64}$ ]]
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
if grep -Eq '^dist/lobehub/' <<<"${full_listing}"; then
  echo 'Default release unexpectedly contains LobeHub artifacts' >&2
  exit 1
fi
full_manifest="$(unzip -p "${OUTPUT_DIR}/test-agent-internal-release.zip" \
  deploy/internal/release-components.env)"
grep -Fxq 'TEST_AGENT_RELEASE_LOBEHUB=disabled' <<<"${full_manifest}"
grep -Fxq "TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=${worker_fingerprint}" "${STATE_FILE}"
grep -Fxq "TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT=${toolbox_fingerprint}" "${STATE_FILE}"

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
incremental_manifest="$(unzip -p "${OUTPUT_DIR}/test-agent-internal-release.zip" \
  deploy/internal/release-components.env)"
grep -Fxq 'TEST_AGENT_RELEASE_WORKER_RUNTIME=reuse' <<<"${incremental_manifest}"
grep -Fxq 'TEST_AGENT_RELEASE_TOOLBOX=reuse' <<<"${incremental_manifest}"

# 只有 worker runtime 基线变化时，只重新携带 Manager/Codex/programs 与 worker 镜像。
printf '%s\n' \
  'TEST_AGENT_RELEASE_COMPONENT_STATE_VERSION=1' \
  'TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=outdated' \
  "TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT=${toolbox_fingerprint}" \
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

echo 'Incremental components and explicit LobeHub release gate verified'
