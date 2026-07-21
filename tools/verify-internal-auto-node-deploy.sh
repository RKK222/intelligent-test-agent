#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-auto-node-verify.XXXXXX")"
cleanup() { rm -rf "${TMP_ROOT}"; }
trap cleanup EXIT

BUNDLE="${TMP_ROOT}/test-agent-two-backend-complete"
FAKE_BIN="${TMP_ROOT}/bin"
CALL_LOG="${TMP_ROOT}/calls.log"
mkdir -p "${BUNDLE}/nodes" "${FAKE_BIN}"
export CALL_LOG

for script in deploy-node-common.sh deploy-backend-node.sh deploy-frontend-node.sh \
  init-backend-node-config.sh register-backend-on-frontend.sh; do
  cp "${ROOT_DIR}/deploy/internal/${script}" "${BUNDLE}/${script}"
done
printf 'release fixture\n' >"${BUNDLE}/test-agent-internal-release.zip"

printf '%s\n' '#!/usr/bin/env bash' '[[ "${1:-}" == "-I" ]] && printf "%s\n" "${TEST_AGENT_FIXTURE_IP}"' \
  >"${FAKE_BIN}/hostname"
printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${FAKE_BIN}/curl"
chmod +x "${FAKE_BIN}/hostname" "${FAKE_BIN}/curl"

sha256_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

write_checksum() {
  local file="$1"
  printf '%s  %s\n' "$(sha256_digest "${file}")" "$(basename "${file}")" >"${file}.sha256"
}

create_backend_node() {
  local ip="$1"
  local node="test-agent-two-backend-${ip}"
  local root="${TMP_ROOT}/${node}"
  mkdir -p "${root}/${node}/config"
  printf '%s\n' \
    "TEST_AGENT_SERVER_ADVERTISED_HOST=${ip}" \
    "TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-${ip//./-}" \
    'TEST_AGENT_DB_PASSWORD=secret-must-not-print' \
    'TEST_AGENT_OPENCODE_MANAGER_TOKEN=manager-must-not-print' \
    'TEST_AGENT_INTERNAL_PROXY_API_KEY=proxy-must-not-print' \
    >"${root}/${node}/config/backend.env"
  printf '%s\n' 'TEST_AGENT_OPENCODE_MANAGER_TOKEN=manager-must-not-print' \
    >"${root}/${node}/config/docker.env"
  printf '%s\n' \
    '#!/usr/bin/env bash' \
    'printf "%s\n" "$*" >>"${CALL_LOG}"' \
    'if [[ "$*" == *"--validate-only"* ]]; then echo "node validation passed"; fi' \
    'if [[ "$*" == *"--verify-only"* ]]; then echo "node verification passed"; fi' \
    'if [[ "$*" != *"--validate-only"* && "$*" != *"--verify-only"* ]]; then echo "Java and Docker started"; fi' \
    >"${root}/${node}/deploy-multi-backend-node.sh"
  printf '# fixture\n' >"${root}/${node}/MULTI-BACKEND.md"
  tar -C "${root}" -czf "${BUNDLE}/nodes/${node}-SENSITIVE.tar.gz" "${node}"
  write_checksum "${BUNDLE}/nodes/${node}-SENSITIVE.tar.gz"
}

create_frontend_node() {
  local node="test-agent-two-backend-122.233.30.2"
  local root="${TMP_ROOT}/${node}"
  mkdir -p "${root}/${node}/config"
  printf '%s\n' \
    'TEST_AGENT_NGINX_BACKENDS=122.233.30.4:8080,122.233.30.114:8080' \
    'TEST_AGENT_NGINX_TERMINAL_ROUTES=test-agent-backend-122-233-30-4=122.233.30.4:8080,test-agent-backend-122-233-30-114=122.233.30.114:8080' \
    >"${root}/${node}/config/nginx.env"
  printf '%s\n' \
    '#!/usr/bin/env bash' \
    'printf "%s\n" "$*" >>"${CALL_LOG}"' \
    'echo "frontend phase completed"' \
    >"${root}/${node}/deploy-multi-backend-node.sh"
  printf '# fixture\n' >"${root}/${node}/MULTI-BACKEND.md"
  tar -C "${root}" -czf "${BUNDLE}/nodes/${node}.tar.gz" "${node}"
  write_checksum "${BUNDLE}/nodes/${node}.tar.gz"
}

create_backend_node 122.233.30.4
create_backend_node 122.233.30.114
create_frontend_node

backend_output="$(cd "${BUNDLE}" && PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_FIXTURE_IP=122.233.30.4 \
  bash deploy-backend-node.sh 2>&1)"
grep -Fq 'Detected backend IP: 122.233.30.4' <<<"${backend_output}"
grep -Fq 'Java and Docker started' <<<"${backend_output}"
test "$(grep -c '^backend ' "${CALL_LOG}")" -eq 3
grep -Fq -- '--peer-host 122.233.30.114' "${CALL_LOG}"
test -s "${TMP_ROOT}/deploy-122.233.30.4.log"

frontend_output="$(cd "${BUNDLE}" && PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_FIXTURE_IP=122.233.30.2 \
  bash deploy-frontend-node.sh 2>&1)"
grep -Fq 'Detected frontend IP: 122.233.30.2' <<<"${frontend_output}"
test "$(grep -c '^frontend ' "${CALL_LOG}")" -eq 3
test -s "${TMP_ROOT}/deploy-122.233.30.2.log"

init_output="$(cd "${BUNDLE}" && PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_FIXTURE_IP=122.233.30.115 \
  bash init-backend-node-config.sh 2>&1)"
if grep -Eq 'secret-must-not-print|manager-must-not-print|proxy-must-not-print' <<<"${init_output}"; then
  echo "New-node initializer leaked a secret" >&2
  exit 1
fi
NEW_ARCHIVE="${BUNDLE}/nodes/test-agent-two-backend-122.233.30.115-SENSITIVE.tar.gz"
test -s "${NEW_ARCHIVE}"
test "$(wc -c <"${NEW_ARCHIVE}" | tr -d '[:space:]')" -le 1048576
new_backend_env="$(tar -xOzf "${NEW_ARCHIVE}" test-agent-two-backend-122.233.30.115/config/backend.env)"
grep -Fxq 'TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.115' <<<"${new_backend_env}"
grep -Fxq 'TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-115' <<<"${new_backend_env}"

register_output="$(cd "${BUNDLE}" && PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_FIXTURE_IP=122.233.30.2 \
  bash register-backend-on-frontend.sh 122.233.30.115 2>&1)"
grep -Fq 'Registered backend 122.233.30.115' <<<"${register_output}"
updated_nginx_env="$(tar -xOzf "${BUNDLE}/nodes/test-agent-two-backend-122.233.30.2.tar.gz" \
  test-agent-two-backend-122.233.30.2/config/nginx.env)"
grep -Fq '122.233.30.115:8080' <<<"${updated_nginx_env}"
grep -Fq 'test-agent-backend-122-233-30-115=122.233.30.115:8080' <<<"${updated_nginx_env}"

echo 'Automatic node IP detection, full phase execution, new backend env initialization and frontend registration verified'
