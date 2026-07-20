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
  'for arg in "$@"; do' \
  '  if [[ "${arg}" == "-T" ]]; then' \
  '    printf "# configuration file %s:\n" "${TEST_AGENT_NGINX_CONF_PATH}"' \
  '  fi' \
  'done' \
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
  local additional_listen_ports="${3:-}"
  {
    printf 'TEST_AGENT_NGINX_MODE=%s\n' "${mode}"
    printf 'TEST_AGENT_NGINX_BACKENDS=%s\n' "${backends}"
    printf 'TEST_AGENT_NGINX_LISTEN_PORT=80\n'
    printf 'TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS=%s\n' "${additional_listen_ports}"
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

write_env multi '122.233.30.4:8080,122.233.30.114:8080' '9996'
printf 'TEST_AGENT_NGINX_TERMINAL_ROUTES=server-a=122.233.30.4:8080,server-b=122.233.30.114:8080\n' >>"${ENV_FILE}"
run_configure
grep -Fq 'server 122.233.30.4:8080 max_fails=3 fail_timeout=10s;' "${CONF_PATH}"
grep -Fq 'server 122.233.30.114:8080 max_fails=3 fail_timeout=10s;' "${CONF_PATH}"
test "$(grep -Fc 'max_fails=3' "${CONF_PATH}")" = 2
grep -Fq 'listen 80;' "${CONF_PATH}"
grep -Fq 'listen 9996;' "${CONF_PATH}"
grep -Fq 'location = /api/internal/platform/opencode-runtime/management/linux-servers/server-a/terminal/ws {' "${CONF_PATH}"
grep -Fq 'proxy_pass http://122.233.30.4:8080;' "${CONF_PATH}"
test "$(grep -Fxc 'reload nginx' "${CALL_LOG}")" = 2

TLS_CERT="${TMP_ROOT}/test-agent.crt"
TLS_KEY="${TMP_ROOT}/test-agent.key"
: >"${TLS_CERT}"
: >"${TLS_KEY}"
write_env single '122.233.30.114:8080' '443'
{
  printf 'TEST_AGENT_NGINX_TLS_ENABLED=true\n'
  printf 'TEST_AGENT_NGINX_TLS_CERTIFICATE=%s\n' "${TLS_CERT}"
  printf 'TEST_AGENT_NGINX_TLS_CERTIFICATE_KEY=%s\n' "${TLS_KEY}"
} >>"${ENV_FILE}"
run_configure
grep -Fq 'listen 80 ssl;' "${CONF_PATH}"
grep -Fq 'listen 443 ssl;' "${CONF_PATH}"
grep -Fq "ssl_certificate ${TLS_CERT};" "${CONF_PATH}"

CUSTOM_ROOT="${TMP_ROOT}/custom-nginx"
CUSTOM_CONF_PATH="${CUSTOM_ROOT}/conf/conf.d/test-agent-gateway.conf"
CUSTOM_MAIN_CONF="${CUSTOM_ROOT}/conf/nginx.conf"
CUSTOM_CALL_LOG="${TMP_ROOT}/custom-nginx-calls.log"
mkdir -p "${CUSTOM_ROOT}/sbin" "$(dirname "${CUSTOM_CONF_PATH}")"
printf 'events {}\nhttp { include conf/conf.d/*.conf; }\n' >"${CUSTOM_MAIN_CONF}"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'printf "%s\n" "$*" >>"${TEST_AGENT_NGINX_TEST_CALL_LOG}"' \
  'for arg in "$@"; do' \
  '  if [[ "${arg}" == "-T" ]]; then' \
  '    printf "# configuration file %s:\n" "${TEST_AGENT_NGINX_CONF_PATH}"' \
  '  fi' \
  'done' \
  'exit 0' >"${CUSTOM_ROOT}/sbin/nginx"
chmod +x "${CUSTOM_ROOT}/sbin/nginx"
{
  printf 'TEST_AGENT_NGINX_MODE=single\n'
  printf 'TEST_AGENT_NGINX_BACKENDS=122.233.30.114:8080\n'
  printf 'TEST_AGENT_NGINX_LISTEN_PORT=80\n'
  printf 'TEST_AGENT_FRONTEND_ROOT=/data/testagent/frontend\n'
  printf 'TEST_AGENT_NGINX_CONF_PATH=%s\n' "${CUSTOM_CONF_PATH}"
  printf 'TEST_AGENT_NGINX_BIN=%s\n' "${CUSTOM_ROOT}/sbin/nginx"
  printf 'TEST_AGENT_NGINX_PREFIX=%s\n' "${CUSTOM_ROOT}"
  printf 'TEST_AGENT_NGINX_MAIN_CONF=%s\n' "${CUSTOM_MAIN_CONF}"
  printf 'TEST_AGENT_NGINX_RELOAD_MODE=binary\n'
} >"${ENV_FILE}"
TEST_AGENT_NGINX_TEST_CALL_LOG="${CUSTOM_CALL_LOG}" \
  bash "${ROOT_DIR}/deploy/internal/configure-nginx.sh" --env-file "${ENV_FILE}"
grep -Fq -- "-p ${CUSTOM_ROOT}/ -c ${CUSTOM_MAIN_CONF} -t" "${CUSTOM_CALL_LOG}"
grep -Fq -- "-p ${CUSTOM_ROOT}/ -c ${CUSTOM_MAIN_CONF} -T" "${CUSTOM_CALL_LOG}"
grep -Fq -- "-p ${CUSTOM_ROOT}/ -c ${CUSTOM_MAIN_CONF} -s reload" "${CUSTOM_CALL_LOG}"

write_env single '122.233.30.4:8080,122.233.30.114:8080'
if PATH="${FAKE_BIN}:${PATH}" bash "${ROOT_DIR}/deploy/internal/configure-nginx.sh" --env-file "${ENV_FILE}" --validate-only; then
  echo "single mode unexpectedly accepted multiple backends" >&2
  exit 1
fi

write_env multi '122.233.30.4:8080,122.233.30.114:8080' '9996,80'
if PATH="${FAKE_BIN}:${PATH}" bash "${ROOT_DIR}/deploy/internal/configure-nginx.sh" --env-file "${ENV_FILE}" --validate-only; then
  echo "duplicate additional listen port was unexpectedly accepted" >&2
  exit 1
fi

echo "Internal single/multi-backend Nginx rendering and reload flows verified"
