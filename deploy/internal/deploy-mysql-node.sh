#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=deploy-node-common.sh
source "${SCRIPT_DIR}/deploy-node-common.sh"

LOCAL_IP="$(detect_site_ip mysql)"
NODE_NAME="test-agent-two-backend-${LOCAL_IP}-mysql"
NODE_ARCHIVE="${SCRIPT_DIR}/nodes/${NODE_NAME}-SENSITIVE.tar.gz"
RELEASE_ARCHIVE="${SCRIPT_DIR}/test-agent-internal-release.zip"
DEPLOY_LOG="$(cd "${SCRIPT_DIR}/.." && pwd)/deploy-${LOCAL_IP}-mysql.log"

[[ -f "${RELEASE_ARCHIVE}" ]] || { echo "Required file not found: ${RELEASE_ARCHIVE}" >&2; exit 1; }
verify_checksum_pair "${RELEASE_ARCHIVE}"
verify_checksum_pair "${NODE_ARCHIVE}"
tar -xzf "${NODE_ARCHIVE}" -C "${SCRIPT_DIR}"

DEPLOY_SCRIPT="${SCRIPT_DIR}/${NODE_NAME}/deploy-xxl-job-mysql.sh"
MYSQL_ENV="${SCRIPT_DIR}/${NODE_NAME}/config/mysql.env"
[[ -f "${DEPLOY_SCRIPT}" && -f "${MYSQL_ENV}" ]] || {
  echo "MySQL deployment script or config is missing after extraction" >&2
  exit 1
}

printf 'Detected PostgreSQL/MySQL host IP: %s\n' "${LOCAL_IP}"
printf 'Full deployment log: %s\n' "${DEPLOY_LOG}"
{
  bash "${DEPLOY_SCRIPT}" --env-file "${MYSQL_ENV}" --release-archive "${RELEASE_ARCHIVE}" validate
  bash "${DEPLOY_SCRIPT}" --env-file "${MYSQL_ENV}" --release-archive "${RELEASE_ARCHIVE}" deploy
  bash "${DEPLOY_SCRIPT}" --env-file "${MYSQL_ENV}" verify
} 2>&1 | tee "${DEPLOY_LOG}"

printf 'MySQL deploy and verification completed: %s\n' "${LOCAL_IP}"
