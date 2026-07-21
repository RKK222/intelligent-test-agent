#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=deploy-node-common.sh
source "${SCRIPT_DIR}/deploy-node-common.sh"

if [[ $# -ne 1 || ! "$1" =~ ^122\.233\.30\.([1-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-4])$ ]]; then
  echo "Usage: register-backend-on-frontend.sh <122.233.30.x>" >&2
  exit 2
fi
detect_site_ip frontend >/dev/null
BACKEND_IP="$1"
[[ "${BACKEND_IP}" != "122.233.30.2" ]] || { echo "Frontend IP cannot be registered as a backend" >&2; exit 1; }

curl -fsS "http://${BACKEND_IP}:8080/actuator/health/readiness" >/dev/null

NODE_NAME="test-agent-two-backend-122.233.30.2"
NODE_ARCHIVE="${SCRIPT_DIR}/nodes/${NODE_NAME}.tar.gz"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-frontend-register.XXXXXX")"
cleanup() { rm -rf "${TMP_ROOT}"; }
trap cleanup EXIT

verify_checksum_pair "${NODE_ARCHIVE}"
tar -xzf "${NODE_ARCHIVE}" -C "${TMP_ROOT}"
NGINX_ENV="${TMP_ROOT}/${NODE_NAME}/config/nginx.env"
[[ -f "${NGINX_ENV}" ]] || { echo "Frontend package does not contain nginx.env" >&2; exit 1; }

append_env_csv_value "${NGINX_ENV}" TEST_AGENT_NGINX_BACKENDS "${BACKEND_IP}:8080"
append_env_csv_value "${NGINX_ENV}" TEST_AGENT_NGINX_SERVER_ROUTES \
  "$(server_id_from_host "${BACKEND_IP}")=${BACKEND_IP}:8080"

TARGET_TMP="$(mktemp "${SCRIPT_DIR}/nodes/.${NODE_NAME}.tar.gz.XXXXXX")"
tar -C "${TMP_ROOT}" -czf "${TARGET_TMP}" "${NODE_NAME}"
chmod 0600 "${TARGET_TMP}"
mv -f "${TARGET_TMP}" "${NODE_ARCHIVE}"
printf '%s  %s\n' "$(sha256_digest "${NODE_ARCHIVE}")" "$(basename "${NODE_ARCHIVE}")" \
  >"${NODE_ARCHIVE}.sha256"
chmod 0600 "${NODE_ARCHIVE}.sha256"

printf 'Registered backend %s in packaged nginx.env.\n' "${BACKEND_IP}"
printf 'Next: bash deploy-frontend-node.sh\n'
