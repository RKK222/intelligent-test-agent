#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=deploy-node-common.sh
source "${SCRIPT_DIR}/deploy-node-common.sh"

LOCAL_IP="$(detect_site_ip backend)"
SEED_NAME="test-agent-two-backend-122.233.30.4"
SEED_ARCHIVE="${SCRIPT_DIR}/nodes/${SEED_NAME}-SENSITIVE.tar.gz"
NODE_NAME="test-agent-two-backend-${LOCAL_IP}"
TARGET_ARCHIVE="${SCRIPT_DIR}/nodes/${NODE_NAME}-SENSITIVE.tar.gz"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-new-backend.XXXXXX")"
cleanup() { rm -rf "${TMP_ROOT}"; }
trap cleanup EXIT

if [[ -f "${TARGET_ARCHIVE}" ]]; then
  verify_checksum_pair "${TARGET_ARCHIVE}"
  printf 'Prepared backend configuration already exists for %s; no file was overwritten.\n' "${LOCAL_IP}"
  printf 'Next: bash deploy-backend-node.sh\n'
  exit 0
fi

verify_checksum_pair "${SEED_ARCHIVE}"
tar -xzf "${SEED_ARCHIVE}" -C "${TMP_ROOT}"
mv "${TMP_ROOT}/${SEED_NAME}" "${TMP_ROOT}/${NODE_NAME}"

BACKEND_ENV="${TMP_ROOT}/${NODE_NAME}/config/backend.env"
DOCKER_ENV="${TMP_ROOT}/${NODE_NAME}/config/docker.env"
[[ -f "${BACKEND_ENV}" && -f "${DOCKER_ENV}" ]] || {
  echo "Seed package does not contain backend.env and docker.env" >&2
  exit 1
}
replace_env_value "${BACKEND_ENV}" TEST_AGENT_SERVER_ADVERTISED_HOST "${LOCAL_IP}"
replace_env_value "${BACKEND_ENV}" TEST_AGENT_LINUX_SERVER_ID "$(server_id_from_host "${LOCAL_IP}")"

TARGET_TMP="$(mktemp "${SCRIPT_DIR}/nodes/.${NODE_NAME}.tar.gz.XXXXXX")"
tar -C "${TMP_ROOT}" -czf "${TARGET_TMP}" "${NODE_NAME}"
if [[ "$(wc -c <"${TARGET_TMP}" | tr -d '[:space:]')" -gt 1048576 ]]; then
  echo "Generated node configuration exceeds 1 MiB" >&2
  exit 1
fi
chmod 0600 "${TARGET_TMP}"
mv -f "${TARGET_TMP}" "${TARGET_ARCHIVE}"
printf '%s  %s\n' "$(sha256_digest "${TARGET_ARCHIVE}")" "$(basename "${TARGET_ARCHIVE}")" \
  >"${TARGET_ARCHIVE}.sha256"
chmod 0600 "${TARGET_ARCHIVE}.sha256"

printf 'Initialized backend.env and docker.env for %s without printing secrets.\n' "${LOCAL_IP}"
printf 'Node package: %s\n' "${TARGET_ARCHIVE}"
printf 'Next: bash deploy-backend-node.sh\n'
printf 'After this backend is ready, register %s on the frontend with register-backend-on-frontend.sh.\n' "${LOCAL_IP}"
