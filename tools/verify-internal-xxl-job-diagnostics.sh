#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "${TMP_ROOT}"' EXIT
FAKE_BIN="${TMP_ROOT}/bin"
mkdir -p "${FAKE_BIN}"

cat >"${FAKE_BIN}/getent" <<'EOF'
#!/usr/bin/env bash
[[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" != "dns-fail" ]] || exit 2
printf '122.233.30.2 STREAM mimo.sdc.cs.icbc\n'
EOF

cat >"${FAKE_BIN}/curl" <<'EOF'
#!/usr/bin/env bash
url="${*: -1}"
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == "admin-down" && "${url}" == *'/actuator/health/readiness' ]]; then
  printf '<html>Bad Gateway</html>\n__TEST_AGENT_HTTP_STATUS__:502'
else
  case "${url}" in
    */actuator/health/readiness) printf '{"status":"UP"}\n__TEST_AGENT_HTTP_STATUS__:200' ;;
    *) printf '<!doctype html><title>MIMO</title>\n__TEST_AGENT_HTTP_STATUS__:200' ;;
  esac
fi
EOF
cat >"${FAKE_BIN}/ip" <<'EOF'
#!/usr/bin/env bash
printf '2: eth0    inet %s/24 brd 122.233.30.255 scope global eth0\n' "${XXL_DIAG_FRONTEND_IP:-122.233.30.2}"
EOF
chmod +x "${FAKE_BIN}/getent" "${FAKE_BIN}/curl" "${FAKE_BIN}/ip"

ENTRY_SCRIPT="${ROOT_DIR}/deploy/internal/diagnose-xxl-job-entry.sh"
set +e
PATH="${FAKE_BIN}:${PATH}" XXL_DIAG_FIXTURE_MODE=healthy bash "${ENTRY_SCRIPT}" --unexpected-argument >"${TMP_ROOT}/entry-argument.log" 2>&1
status=$?
set -e
test "${status}" -eq 2
grep -Fq '[FAIL] 不接受命令行参数' "${TMP_ROOT}/entry-argument.log"

PATH="${FAKE_BIN}:${PATH}" XXL_DIAG_FIXTURE_MODE=healthy bash "${ENTRY_SCRIPT}" >"${TMP_ROOT}/entry-ok.log"
grep -Fq '[PASS] 域名解析' "${TMP_ROOT}/entry-ok.log"
grep -Fq '[WARN] 当前入口使用 HTTP' "${TMP_ROOT}/entry-ok.log"

set +e
PATH="${FAKE_BIN}:${PATH}" XXL_DIAG_FIXTURE_MODE=admin-down bash "${ENTRY_SCRIPT}" >"${TMP_ROOT}/entry-down.log" 2>&1
status=$?
set -e
test "${status}" -eq 1
grep -Fq '[FAIL]' "${TMP_ROOT}/entry-down.log"

set +e
PATH="${FAKE_BIN}:${PATH}" XXL_DIAG_FIXTURE_MODE=dns-fail bash "${ENTRY_SCRIPT}" >"${TMP_ROOT}/entry-dns.log" 2>&1
status=$?
set -e
test "${status}" -eq 1

FRONTEND_FIXTURE="${TMP_ROOT}/frontend-fixture"
mkdir -p "${FRONTEND_FIXTURE}"
cat >"${FRONTEND_FIXTURE}/nginx.env" <<'EOF'
TEST_AGENT_NGINX_MODE=multi
TEST_AGENT_NGINX_XXL_JOB_ADMINS=122.233.30.4:18080,122.233.30.114:18080
TEST_AGENT_NGINX_LISTEN_PORT=80
TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS=9996
EOF
cat >"${FRONTEND_FIXTURE}/nginx.conf" <<'EOF'
upstream test_agent_xxl_job_admin {
    server 122.233.30.4:18080 max_fails=3 fail_timeout=10s;
    server 122.233.30.114:18080 max_fails=3 fail_timeout=10s;
}
location /xxl-job-admin/ {
    proxy_pass http://test_agent_xxl_job_admin;
}
EOF
cat >"${FRONTEND_FIXTURE}/nginx" <<'EOF'
#!/usr/bin/env bash
test "$1" = '-p'
test "$3" = '-c'
test "$5" = '-T'
cat "${XXL_DIAG_FRONTEND_NGINX_CONFIG}"
EOF
cat >"${FRONTEND_FIXTURE}/access.log" <<'EOF'
122.233.30.9 - - [22/Jul/2026:21:00:00 +0800] "GET /xxl-job-admin/?ticket=raw-ticket-value&token=raw-token-value HTTP/1.1" 502 0 "-" "curl"
122.233.30.9 - - [22/Jul/2026:21:00:01 +0800] "GET https://diag.example/xxl-job-admin/#raw-fragment-value HTTP/1.1" 502 0 "-" "curl"
122.233.30.9 - - [22/Jul/2026:21:00:02 +0800] "GET /xxl-job-admin/?opaque=raw-relative-query-value HTTP/1.1" 502 0 "-" "curl"
122.233.30.9 - - [22/Jul/2026:21:00:03 +0800] "GET /xxl-job-admin/#raw-relative-fragment-value HTTP/1.1" 502 0 "-" "curl"
EOF
cat >"${FRONTEND_FIXTURE}/error.log" <<'EOF'
2026/07/22 21:00:01 [error] 1#1: *1 connect() failed (111: Connection refused) while connecting to upstream, request: "GET /xxl-job-admin/ HTTP/1.1", cookie=raw-cookie-value authorization=raw-authorization-value password=raw-password-value
EOF
chmod +x "${FRONTEND_FIXTURE}/nginx"

FRONTEND_SCRIPT="${ROOT_DIR}/deploy/internal/diagnose-xxl-job-frontend.sh"
frontend_run() {
  PATH="${FAKE_BIN}:${PATH}" \
    XXL_DIAG_FRONTEND_IP="${XXL_DIAG_FRONTEND_IP:-122.233.30.2}" \
    XXL_DIAG_FRONTEND_NGINX_CONFIG="${FRONTEND_FIXTURE}/nginx.conf" \
    TEST_AGENT_DIAG_NGINX_BIN="${FRONTEND_FIXTURE}/nginx" \
    TEST_AGENT_DIAG_NGINX_ENV="${FRONTEND_FIXTURE}/nginx.env" \
    TEST_AGENT_DIAG_NGINX_ACCESS_LOG="${FRONTEND_FIXTURE}/access.log" \
    TEST_AGENT_DIAG_NGINX_ERROR_LOG="${FRONTEND_FIXTURE}/error.log" \
    bash "${FRONTEND_SCRIPT}"
}

frontend_run >"${TMP_ROOT}/frontend-ok.log"
grep -Fq '[PASS] Nginx effective configuration contains XXL Admin upstream' "${TMP_ROOT}/frontend-ok.log"
grep -Fq '[PASS] 122.233.30.4:18080 readiness is UP' "${TMP_ROOT}/frontend-ok.log"
grep -Fq '[PASS] 122.233.30.114:18080 readiness is UP' "${TMP_ROOT}/frontend-ok.log"
grep -Fq 'https://diag.example/xxl-job-admin/#[REDACTED_FRAGMENT]' "${TMP_ROOT}/frontend-ok.log"
grep -Fq '/xxl-job-admin/?[REDACTED_QUERY]' "${TMP_ROOT}/frontend-ok.log"
grep -Fq '/xxl-job-admin/#[REDACTED_FRAGMENT]' "${TMP_ROOT}/frontend-ok.log"
if grep -Eq 'raw-(ticket|token|cookie|authorization|password|fragment|relative-query|relative-fragment)-value' "${TMP_ROOT}/frontend-ok.log"; then
  printf 'frontend diagnostics leaked sensitive fixture values\n' >&2
  exit 1
fi

sed -i '' 's/122\.233\.30\.114:18080 max_fails/122.233.30.115:18080 max_fails/' "${FRONTEND_FIXTURE}/nginx.conf"
set +e
frontend_run >"${TMP_ROOT}/frontend-missing-upstream.log" 2>&1
status=$?
set -e
test "${status}" -eq 1
grep -Fq '[FAIL] Nginx effective configuration missing XXL Admin server 122.233.30.114:18080' "${TMP_ROOT}/frontend-missing-upstream.log"

sed -i '' 's/122\.233\.30\.115:18080 max_fails/122.233.30.114:18080 max_fails/' "${FRONTEND_FIXTURE}/nginx.conf"
set +e
XXL_DIAG_FRONTEND_IP=122.233.30.3 frontend_run >"${TMP_ROOT}/frontend-wrong-host.log" 2>&1
status=$?
set -e
test "${status}" -eq 2
grep -Fq '[FAIL] 当前机器不是 122.233.30.2' "${TMP_ROOT}/frontend-wrong-host.log"

printf 'XXL-JOB enterprise diagnostics verified\n'
