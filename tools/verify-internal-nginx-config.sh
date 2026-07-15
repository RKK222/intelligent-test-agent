#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="$(mktemp -d)"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

FAKE_BIN="${TMP_ROOT}/bin"
CALL_LOG="${TMP_ROOT}/calls.log"
CONF_PATH="${TMP_ROOT}/nginx/test-agent-gateway.conf"
ENV_FILE="${TMP_ROOT}/nginx.env"
mkdir -p "${FAKE_BIN}"

printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'if [[ "$1" == "-T" ]]; then' \
  '  printf "# configuration file %s:\n" "${TEST_AGENT_NGINX_CONF_PATH}"' \
  'fi' \
  'exit 0' >"${FAKE_BIN}/nginx"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'printf "%s\n" "$*" >>"${TEST_AGENT_NGINX_TEST_CALL_LOG}"' \
  'exit 0' >"${FAKE_BIN}/systemctl"
chmod +x "${FAKE_BIN}/nginx" "${FAKE_BIN}/systemctl"

write_env() {
  local mode="$1"
  local backends="$2"
  {
    printf 'TEST_AGENT_NGINX_MODE=%s\n' "${mode}"
    printf 'TEST_AGENT_NGINX_BACKENDS=%s\n' "${backends}"
    printf 'TEST_AGENT_NGINX_LISTEN_PORT=80\n'
    printf 'TEST_AGENT_FRONTEND_ROOT=/data/testagent/frontend\n'
    printf 'TEST_AGENT_NGINX_CONF_PATH=%s\n' "${CONF_PATH}"
  } >"${ENV_FILE}"
}

run_configure() {
  PATH="${FAKE_BIN}:${PATH}" \
  TEST_AGENT_NGINX_TEST_CALL_LOG="${CALL_LOG}" \
  bash "${ROOT_DIR}/deploy/internal/configure-nginx.sh" --env-file "${ENV_FILE}"
}

write_env single '122.233.30.114:8080'
run_configure
grep -Fq 'server 122.233.30.114:8080 max_fails=3 fail_timeout=10s;' "${CONF_PATH}"
test "$(grep -Fc 'max_fails=3' "${CONF_PATH}")" = 1

write_env multi '122.233.30.4:8080,122.233.30.114:8080'
run_configure
grep -Fq 'server 122.233.30.4:8080 max_fails=3 fail_timeout=10s;' "${CONF_PATH}"
grep -Fq 'server 122.233.30.114:8080 max_fails=3 fail_timeout=10s;' "${CONF_PATH}"
test "$(grep -Fc 'max_fails=3' "${CONF_PATH}")" = 2
test "$(grep -Fxc 'reload nginx' "${CALL_LOG}")" = 2

write_env single '122.233.30.4:8080,122.233.30.114:8080'
if PATH="${FAKE_BIN}:${PATH}" bash "${ROOT_DIR}/deploy/internal/configure-nginx.sh" --env-file "${ENV_FILE}" --validate-only; then
  echo "single mode unexpectedly accepted multiple backends" >&2
  exit 1
fi

echo "Internal single/multi-backend Nginx rendering and reload flows verified"
