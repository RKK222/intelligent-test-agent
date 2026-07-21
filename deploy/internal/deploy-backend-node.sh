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

printf 'Detected backend IP: %s; verification peer: %s\n' "${LOCAL_IP}" "${PEER_IP}"
printf 'Full deployment log: %s\n' "${DEPLOY_LOG}"
{
  bash "${DEPLOY_SCRIPT}" backend \
    --backend-host "${LOCAL_IP}" \
    --peer-host "${PEER_IP}" \
    --release-archive "${RELEASE_ARCHIVE}" \
    --validate-only
  bash "${DEPLOY_SCRIPT}" backend \
    --backend-host "${LOCAL_IP}" \
    --peer-host "${PEER_IP}" \
    --release-archive "${RELEASE_ARCHIVE}"
  bash "${DEPLOY_SCRIPT}" backend \
    --backend-host "${LOCAL_IP}" \
    --peer-host "${PEER_IP}" \
    --release-archive "${RELEASE_ARCHIVE}" \
    --verify-only
} 2>&1 | tee "${DEPLOY_LOG}"

printf 'Backend deploy and verification completed: %s\n' "${LOCAL_IP}"
