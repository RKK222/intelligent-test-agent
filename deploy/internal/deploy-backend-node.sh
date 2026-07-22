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

[[ -f "${RELEASE_ARCHIVE}" ]] || { echo "Required file not found: ${RELEASE_ARCHIVE}" >&2; exit 1; }
[[ -f "${NODE_ARCHIVE}" ]] || {
  echo "No prepared config for ${LOCAL_IP}. Run: bash init-backend-node-config.sh" >&2
  exit 1
}

verify_checksum_pair "${NODE_ARCHIVE}"
tar -xzf "${NODE_ARCHIVE}" -C "${SCRIPT_DIR}"
DEPLOY_SCRIPT="${SCRIPT_DIR}/${NODE_NAME}/deploy-multi-backend-node.sh"
[[ -f "${DEPLOY_SCRIPT}" ]] || { echo "Deployment script not found after extraction: ${DEPLOY_SCRIPT}" >&2; exit 1; }

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
    --validate-only
  bash "${DEPLOY_SCRIPT}" backend \
    --backend-host "${LOCAL_IP}" \
    "${PEER_ARGS[@]}" \
    --release-archive "${RELEASE_ARCHIVE}"
  bash "${DEPLOY_SCRIPT}" backend \
    --backend-host "${LOCAL_IP}" \
    "${PEER_ARGS[@]}" \
    --release-archive "${RELEASE_ARCHIVE}" \
    --verify-only
} 2>&1 | tee "${DEPLOY_LOG}"

printf 'Backend deploy and verification completed: %s\n' "${LOCAL_IP}"
