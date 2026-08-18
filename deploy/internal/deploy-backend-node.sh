#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=deploy-node-common.sh
source "${SCRIPT_DIR}/deploy-node-common.sh"

LOCAL_IP="$(detect_site_ip backend)"
NODE_NAME="test-agent-two-backend-${LOCAL_IP}"
NODE_ARCHIVE="${SCRIPT_DIR}/nodes/${NODE_NAME}-SENSITIVE.tar.gz"
RELEASE_ARCHIVE="${SCRIPT_DIR}/test-agent-internal-release.zip"
DEPLOY_LOG="$(cd "${SCRIPT_DIR}/.." && pwd)/deploy-${LOCAL_IP}.log"
INSTALL_ROOT="${TEST_AGENT_INSTALL_ROOT:-/data/testagent}"

[[ -f "${RELEASE_ARCHIVE}" ]] || { echo "Required file not found: ${RELEASE_ARCHIVE}" >&2; exit 1; }
[[ -f "${NODE_ARCHIVE}" ]] || {
  echo "No prepared config for ${LOCAL_IP}. Run: bash init-backend-node-config.sh" >&2
  exit 1
}

verify_checksum_pair "${NODE_ARCHIVE}"
tar -xzf "${NODE_ARCHIVE}" -C "${SCRIPT_DIR}"
DEPLOY_SCRIPT="${SCRIPT_DIR}/${NODE_NAME}/deploy-multi-backend-node.sh"
[[ -f "${DEPLOY_SCRIPT}" ]] || { echo "Deployment script not found after extraction: ${DEPLOY_SCRIPT}" >&2; exit 1; }
TOOLBOX_ENV_SOURCE="${SCRIPT_DIR}/${NODE_NAME}/config/toolbox.env"
[[ -f "${TOOLBOX_ENV_SOURCE}" ]] || { echo "Prepared toolbox config not found: ${TOOLBOX_ENV_SOURCE}" >&2; exit 1; }
BACKEND_ENV_SOURCE="${SCRIPT_DIR}/${NODE_NAME}/config/backend.env"
INSTALLED_BACKEND_ENV="${INSTALL_ROOT}/config/backend.env"
PRESERVE_INSTALLED_MARKER="__PRESERVE_FROM_INSTALLED_BACKEND_ENV__"

# ClickHouse/Mem0 已独立部署，增量平台包只携带地址和开关；密钥必须继承目标机现有配置。
hydrate_preserved_env_value "${BACKEND_ENV_SOURCE}" "${INSTALLED_BACKEND_ENV}" \
  TEST_AGENT_ANALYTICS_CLICKHOUSE_PASSWORD "${PRESERVE_INSTALLED_MARKER}" 8
hydrate_preserved_env_value "${BACKEND_ENV_SOURCE}" "${INSTALLED_BACKEND_ENV}" \
  TEST_AGENT_MEMORY_SERVICE_API_KEY "${PRESERVE_INSTALLED_MARKER}" 32
hydrate_preserved_env_value "${BACKEND_ENV_SOURCE}" "${INSTALLED_BACKEND_ENV}" \
  TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET "${PRESERVE_INSTALLED_MARKER}" 32
if grep -qF "${PRESERVE_INSTALLED_MARKER}" "${BACKEND_ENV_SOURCE}"; then
  echo "Prepared backend configuration still contains an unresolved installed-secret marker" >&2
  exit 1
fi
printf 'Existing ClickHouse and memory runtime secrets carried forward without printing values\n'

manifest_value() {
  local content="$1" key="$2"
  awk -F= -v wanted="${key}" '$1 == wanted { value=substr($0, index($0, "=") + 1) } END { print value }' \
    <<<"${content}"
}

state_file_value() {
  local file="$1" key="$2"
  [[ -f "${file}" ]] || return 0
  awk -F= -v wanted="${key}" '$1 == wanted { value=substr($0, index($0, "=") + 1) } END { print value }' \
    "${file}"
}

release_component_manifest="$(unzip -p "${RELEASE_ARCHIVE}" deploy/internal/release-components.env 2>/dev/null || true)"
toolbox_component_mode="$(manifest_value "${release_component_manifest}" TEST_AGENT_RELEASE_TOOLBOX)"
toolbox_component_fingerprint="$(manifest_value "${release_component_manifest}" TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT)"
toolbox_component_mode="${toolbox_component_mode:-included}"
[[ "${toolbox_component_mode}" == included || "${toolbox_component_mode}" == reuse ]] || {
  echo "Invalid toolbox component mode: ${toolbox_component_mode}" >&2
  exit 1
}

verify_reused_toolbox() {
  local diagnose_script="${INSTALL_ROOT}/deploy/internal/diagnose-toolbox.sh"
  local installed_state="${INSTALL_ROOT}/config/release-component-state.env"
  local installed_fingerprint
  installed_fingerprint="$(state_file_value "${installed_state}" TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT)"
  [[ -n "${toolbox_component_fingerprint}" \
    && "${installed_fingerprint}" == "${toolbox_component_fingerprint}" ]] || {
    echo "Incremental release toolbox fingerprint does not match the installed component; deploy a full component package" >&2
    return 1
  }
  [[ -x "${diagnose_script}" && -f "${INSTALL_ROOT}/config/toolbox.env" ]] || {
    echo "Incremental release reuses toolbox, but the existing toolbox deployment is incomplete" >&2
    return 1
  }
  TEST_AGENT_TOOLBOX_ENV_FILE="${INSTALL_ROOT}/config/toolbox.env" "${diagnose_script}"
}

write_installed_toolbox_fingerprint() {
  local state_file="${INSTALL_ROOT}/config/release-component-state.env"
  local worker_fingerprint worker_baseline_source_commit worker_baseline_release_sha256 tmp
  worker_fingerprint="$(state_file_value "${state_file}" TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT)"
  worker_baseline_source_commit="$(state_file_value "${state_file}" \
    TEST_AGENT_RELEASE_WORKER_RUNTIME_BASELINE_SOURCE_COMMIT)"
  worker_baseline_release_sha256="$(state_file_value "${state_file}" \
    TEST_AGENT_RELEASE_WORKER_RUNTIME_BASELINE_RELEASE_SHA256)"
  mkdir -p "$(dirname "${state_file}")"
  tmp="$(mktemp "${state_file}.new.XXXXXX")"
  {
    printf 'TEST_AGENT_RELEASE_COMPONENT_STATE_VERSION=1\n'
    [[ -z "${worker_fingerprint}" ]] || printf 'TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=%s\n' "${worker_fingerprint}"
    [[ -z "${worker_baseline_source_commit}" ]] || \
      printf 'TEST_AGENT_RELEASE_WORKER_RUNTIME_BASELINE_SOURCE_COMMIT=%s\n' \
        "${worker_baseline_source_commit}"
    [[ -z "${worker_baseline_release_sha256}" ]] || \
      printf 'TEST_AGENT_RELEASE_WORKER_RUNTIME_BASELINE_RELEASE_SHA256=%s\n' \
        "${worker_baseline_release_sha256}"
    printf 'TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT=%s\n' "${toolbox_component_fingerprint}"
  } >"${tmp}"
  chmod 0600 "${tmp}"
  mv -f "${tmp}" "${state_file}"
}

deploy_or_verify_toolbox() {
  local toolbox_script="${INSTALL_ROOT}/deploy/internal/toolbox-docker.sh"
  local diagnose_script="${INSTALL_ROOT}/deploy/internal/diagnose-toolbox.sh"
  install -d -m 0755 "${INSTALL_ROOT}/config" "${INSTALL_ROOT}/dist"
  install -m 0600 "${TOOLBOX_ENV_SOURCE}" "${INSTALL_ROOT}/config/toolbox.env"
  if [[ "${toolbox_component_mode}" == included ]]; then
    unzip -jo "${RELEASE_ARCHIVE}" \
      dist/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar \
      dist/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar.sha256 \
      dist/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar \
      dist/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar.sha256 \
      -d "${INSTALL_ROOT}/dist"
    TEST_AGENT_TOOLBOX_ENV_FILE="${INSTALL_ROOT}/config/toolbox.env" "${toolbox_script}" deploy
  else
    printf 'Toolbox component unchanged; reuse existing images and containers\n'
  fi
  TEST_AGENT_TOOLBOX_ENV_FILE="${INSTALL_ROOT}/config/toolbox.env" "${diagnose_script}"
  if [[ "${toolbox_component_mode}" == included && -n "${toolbox_component_fingerprint}" ]]; then
    write_installed_toolbox_fingerprint
  fi
}

PEER_IP="122.233.30.4"
[[ "${LOCAL_IP}" == "122.233.30.4" ]] && PEER_IP="122.233.30.114"
PEER_ARGS=(--peer-host "${PEER_IP}")
PEER_LABEL="${PEER_IP}"
# 发布要求先停全部旧 Java；固定先部署 .4 时，.114 尚未启动，首节点只做本机全量校验。
# 第二台 .114 会反查 .4，最后前端入口还会同时检查两个 Java 和两个 XXL Admin。
if [[ "${LOCAL_IP}" == "122.233.30.4" ]]; then
  PEER_ARGS=(--skip-peer-check)
  PEER_LABEL="deferred-to-122.233.30.114"
fi

printf 'Detected backend IP: %s; verification peer: %s\n' "${LOCAL_IP}" "${PEER_LABEL}"
printf 'Full deployment log: %s\n' "${DEPLOY_LOG}"
{
  bash "${DEPLOY_SCRIPT}" backend \
    --backend-host "${LOCAL_IP}" \
    "${PEER_ARGS[@]}" \
    --release-archive "${RELEASE_ARCHIVE}" \
    --install-root "${INSTALL_ROOT}" \
    --validate-only
  if [[ "${toolbox_component_mode}" == reuse ]]; then
    verify_reused_toolbox
  fi
  bash "${DEPLOY_SCRIPT}" backend \
    --backend-host "${LOCAL_IP}" \
    "${PEER_ARGS[@]}" \
    --release-archive "${RELEASE_ARCHIVE}" \
    --install-root "${INSTALL_ROOT}"
  deploy_or_verify_toolbox
  bash "${DEPLOY_SCRIPT}" backend \
    --backend-host "${LOCAL_IP}" \
    "${PEER_ARGS[@]}" \
    --release-archive "${RELEASE_ARCHIVE}" \
    --install-root "${INSTALL_ROOT}" \
    --verify-only
} 2>&1 | tee "${DEPLOY_LOG}"

printf 'Backend deploy and verification completed: %s\n' "${LOCAL_IP}"
