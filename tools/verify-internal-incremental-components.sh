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
# 增量封装也必须经过正式 Flyway 字节门禁，测试夹具复用主 migration 构造外置 persistence JAR。
PERSISTENCE_JAR_ROOT="${TMP_ROOT}/persistence-jar-root"
for migration_resource in \
  db/migration/V20260728160800__create_toolbox_click_tracking.sql \
  db/migration/V20260730090000__add_lobehub_model_gateway.sql \
  db/migration-compat/lobehub-missing/V20260802173416__backfill_lobehub_model_gateway.sql \
  db/migration/V20260803133000__support_public_agent_config_rollout_supersede.sql \
  db/migration-compat/lobehub-missing-after-rollout/V20260803141754__backfill_lobehub_model_gateway_after_rollout.sql \
  db/migration/V20260805132000__create_support_access_audit.sql; do
  mkdir -p "${PERSISTENCE_JAR_ROOT}/$(dirname "${migration_resource}")"
  cp "${ROOT_DIR}/backend/test-agent-persistence/src/main/resources/${migration_resource}" \
    "${PERSISTENCE_JAR_ROOT}/${migration_resource}"
done
(cd "${PERSISTENCE_JAR_ROOT}" && zip -qr \
  "${OUTPUT_DIR}/backend/lib/test-agent-persistence-0.1.0-SNAPSHOT.jar" .)
printf 'frontend\n' >"${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz"
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
if grep -Eq '^dist/(test-agent-workflow-offline|lobehub/)' <<<"${full_listing}"; then
  echo 'Default release unexpectedly contains Workflow or LobeHub artifacts' >&2
  exit 1
fi
full_manifest="$(unzip -p "${OUTPUT_DIR}/test-agent-internal-release.zip" \
  deploy/internal/release-components.env)"
grep -Fxq 'TEST_AGENT_RELEASE_WORKFLOW=disabled' <<<"${full_manifest}"
grep -Fxq 'TEST_AGENT_RELEASE_WORKFLOW_VERSION=none' <<<"${full_manifest}"
grep -Fxq 'TEST_AGENT_RELEASE_WORKFLOW_ARCHIVE_SHA256=none' <<<"${full_manifest}"
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

# Workflow 只允许显式 opt-in；参数顺序不应影响 ZIP 内容或组件清单。
printf 'workflow\n' >"${OUTPUT_DIR}/test-agent-workflow-offline.tar.gz"
printf 'fixture checksum\n' >"${OUTPUT_DIR}/test-agent-workflow-offline.tar.gz.sha256"
printf '%s\n' \
  'TEST_AGENT_WORKFLOW_RELEASE_VERSION=fixture-workflow' \
  'TEST_AGENT_WORKFLOW_ARCHIVE_SHA256=fixture-workflow-sha256' \
  >"${OUTPUT_DIR}/.workflow-artifact.env"
rm -f "${OUTPUT_DIR}/test-agent-internal-release.zip" \
  "${OUTPUT_DIR}/test-agent-internal-release.zip.sha256"
bash "${PACKAGE_SCRIPT}" --with-workflow --zip-only --output-dir "${OUTPUT_DIR}" \
  --component-state-file "${STATE_FILE}" >/dev/null
workflow_listing="$(unzip -Z1 "${OUTPUT_DIR}/test-agent-internal-release.zip")"
grep -Fxq 'dist/test-agent-workflow-offline.tar.gz' <<<"${workflow_listing}"
grep -Fxq 'dist/test-agent-workflow-offline.tar.gz.sha256' <<<"${workflow_listing}"
workflow_manifest="$(unzip -p "${OUTPUT_DIR}/test-agent-internal-release.zip" \
  deploy/internal/release-components.env)"
grep -Fxq 'TEST_AGENT_RELEASE_WORKFLOW=included' <<<"${workflow_manifest}"
grep -Fxq 'TEST_AGENT_RELEASE_WORKFLOW_VERSION=fixture-workflow' <<<"${workflow_manifest}"

# 恢复默认发布后即使输出目录残留历史 workflow 制品，也必须继续排除。
rm -f "${OUTPUT_DIR}/test-agent-internal-release.zip" \
  "${OUTPUT_DIR}/test-agent-internal-release.zip.sha256"
bash "${PACKAGE_SCRIPT}" --zip-only --output-dir "${OUTPUT_DIR}" \
  --component-state-file "${STATE_FILE}" >/dev/null
disabled_again_listing="$(unzip -Z1 "${OUTPUT_DIR}/test-agent-internal-release.zip")"
if grep -Eq '^dist/test-agent-workflow-offline' <<<"${disabled_again_listing}"; then
  echo 'Stale Workflow artifacts leaked into a disabled release' >&2
  exit 1
fi

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

echo 'Incremental components and explicit Workflow/LobeHub release gates verified'
