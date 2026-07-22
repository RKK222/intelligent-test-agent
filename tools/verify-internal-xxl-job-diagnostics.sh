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
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == "backend-admin-down" && "${url}" == 'http://127.0.0.1:18080/xxl-job-admin/actuator/health/readiness' ]]; then
  printf '{"status":"DOWN"}\n__TEST_AGENT_HTTP_STATUS__:503'
elif [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == "admin-down" && "${url}" == *'/actuator/health/readiness' ]]; then
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
address="${XXL_DIAG_BACKEND_IP:-${XXL_DIAG_FRONTEND_IP:-122.233.30.2}}"
printf '2: eth0    inet %s/24 brd 122.233.30.255 scope global eth0\n' "${address}"
EOF
cat >"${FAKE_BIN}/systemctl" <<'EOF'
#!/usr/bin/env bash
test "$1" = 'show'
test "$2" = 'test-agent-backend'
exec_start='/usr/bin/java -jar /data/testagent/dist/backend/test-agent-app.jar'
environment_files='/data/testagent/config/backend.env (ignore_errors=no)'
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-wrong-execstart' ]]; then
  exec_start='/usr/bin/java -jar /data/testagent/dist/backend/wrong-app.jar'
elif [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-execstart-jar-backup' ]]; then
  exec_start='/usr/bin/java -jar /data/testagent/dist/backend/test-agent-app.jar.bak'
elif [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-wrong-environment-files' ]]; then
  environment_files='/data/testagent/config/wrong-backend.env (ignore_errors=no)'
elif [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-environment-files-old' ]]; then
  environment_files='/data/testagent/config/backend.env.old (ignore_errors=no)'
fi
cat <<'OUTPUT'
LoadState=loaded
ActiveState=active
SubState=running
MainPID=4242
ActiveEnterTimestamp=Wed 2026-07-22 20:00:00 CST
OUTPUT
printf 'ExecStart={ path=/usr/bin/java ; argv[]=%s ; }\n' "${exec_start}"
printf 'EnvironmentFiles=%s\n' "${environment_files}"
EOF
cat >"${FAKE_BIN}/ss" <<'EOF'
#!/usr/bin/env bash
cat <<'OUTPUT'
State  Recv-Q Send-Q Local Address:Port Peer Address:Port Process
LISTEN 0      4096        0.0.0.0:8080      0.0.0.0:* users:(("java",pid=4242,fd=100))
LISTEN 0      4096        0.0.0.0:18080     0.0.0.0:* users:(("java",pid=4242,fd=101))
LISTEN 0      4096        0.0.0.0:9999      0.0.0.0:* users:(("java",pid=4242,fd=102))
LISTEN 0      4096        0.0.0.0:4096      0.0.0.0:* users:(("opencode",pid=5000,fd=10))
OUTPUT
EOF
cat >"${FAKE_BIN}/nc" <<'EOF'
#!/usr/bin/env bash
test "$1" = '-z'
test "$2" = '-w'
test "$3" = '3'
test -n "$4"
test -n "$5"
EOF
cat >"${FAKE_BIN}/ps" <<'EOF'
#!/usr/bin/env bash
test "$1" = '-eo'
test "$2" = 'pid=,args='
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-ps-error' ]]; then
  exit 42
fi
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-ps-jar-backup' ]]; then
  printf ' 4242 /usr/bin/java -jar /data/testagent/dist/backend/test-agent-app.jar.bak\n'
  exit 0
fi
printf ' 4242 /usr/bin/java -jar /data/testagent/dist/backend/test-agent-app.jar\n'
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-extra-java' ]]; then
  printf ' 5252 /usr/bin/java -jar /data/testagent/dist/backend/another-java-app.jar\n'
fi
EOF
cat >"${FAKE_BIN}/journalctl" <<'EOF'
#!/usr/bin/env bash
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-log-no-match' ]]; then
  printf '%s\n' '2026-07-22T21:00:00+0800 backend ordinary lifecycle line'
  exit 0
fi
printf '%s\n' \
  '2026-07-22T21:00:00+0800 backend XxlJob ExecutorRegistryThread started token=raw-log-token-value digest=raw-log-digest-value' \
  '2026-07-22T21:00:01+0800 backend Hikari MySQL jdbc:mysql://raw-jdbc-user:raw-jdbc-password@122.233.30.148:3306/xxl_job?password=raw-jdbc-query-value' \
  '2026-07-22T21:00:02+0800 backend XxlJob GET https://diag.example/xxl-job-admin/#raw-absolute-fragment-value' \
  '2026-07-22T21:00:03+0800 backend XxlJob GET https://diag.example/xxl-job-admin/?opaque=raw-absolute-query-value' \
  '2026-07-22T21:00:03+0800 backend XxlJob GET /xxl-job-admin/?opaque=raw-relative-query-value' \
  '2026-07-22T21:00:04+0800 backend XxlJob GET /xxl-job-admin/#raw-relative-fragment-value' \
  '2026-07-22T21:00:05+0800 backend XxlJob Authorization: Bearer raw-bearer-token-value' \
  '2026-07-22T21:00:05+0800 backend XxlJob Authorization: Bearer "raw-sensitive-bearer-double"' \
  "2026-07-22T21:00:05+0800 backend XxlJob Authorization: Bearer 'raw-sensitive-bearer-single'" \
  '2026-07-22T21:00:06+0800 backend XxlJob payload={"token":"raw-quoted-token-value"}' \
  "2026-07-22T21:00:07+0800 backend XxlJob ticket=raw-sensitive-ticket-bare cookie='raw-sensitive-cookie-single' token=\"raw-sensitive-token-double\" password=raw-sensitive-password-bare secret='raw-sensitive-secret-single' authorization=\"raw-sensitive-authorization-double\" digest='raw-sensitive-digest-single'" \
  "2026-07-22T21:00:08+0800 backend XxlJob accessToken=raw-sensitive-access-camel access_token='raw-sensitive-access-snake' apiToken=\"raw-sensitive-api-token-camel\" api_token=raw-sensitive-api-token-snake apiKey='raw-sensitive-api-key-camel' api_key=\"raw-sensitive-api-key-snake\" platform_session_digest='raw-sensitive-platform-session-digest'" \
  '2026-07-22T21:00:02+0800 backend unrelated raw-unrelated-value'
EOF
chmod +x "${FAKE_BIN}/getent" "${FAKE_BIN}/curl" "${FAKE_BIN}/ip" \
  "${FAKE_BIN}/systemctl" "${FAKE_BIN}/ss" "${FAKE_BIN}/nc" "${FAKE_BIN}/ps" "${FAKE_BIN}/journalctl"

BROKEN_SHA_BIN="${TMP_ROOT}/broken-sha-bin"
FILTER_FAILURE_BIN="${TMP_ROOT}/filter-failure-bin"
REDACTION_FAILURE_BIN="${TMP_ROOT}/redaction-failure-bin"
mkdir -p "${BROKEN_SHA_BIN}" "${FILTER_FAILURE_BIN}" "${REDACTION_FAILURE_BIN}"
cat >"${BROKEN_SHA_BIN}/sha256sum" <<'EOF'
#!/usr/bin/env bash
exit 127
EOF
cat >"${BROKEN_SHA_BIN}/shasum" <<'EOF'
#!/usr/bin/env bash
exit 127
EOF
cat >"${FILTER_FAILURE_BIN}/grep" <<'EOF'
#!/usr/bin/env bash
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-filter-failure' && "$*" == *'xxl-job|XxlJob|Flyway'* ]]; then
  exit 2
fi
exec /usr/bin/grep "$@"
EOF
cat >"${REDACTION_FAILURE_BIN}/sed" <<'EOF'
#!/usr/bin/env bash
input="$(cat)"
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-redaction-failure' && "${input}" == *'XxlJob'* ]]; then
  exit 2
fi
printf '%s' "${input}" | /usr/bin/sed "$@"
EOF
chmod +x "${BROKEN_SHA_BIN}/sha256sum" "${BROKEN_SHA_BIN}/shasum" \
  "${FILTER_FAILURE_BIN}/grep" "${REDACTION_FAILURE_BIN}/sed"

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

BACKEND_SCRIPT="${ROOT_DIR}/deploy/internal/diagnose-xxl-job-backend.sh"
if [[ ! -f "${BACKEND_SCRIPT}" ]]; then
  printf 'missing backend diagnostics script: %s\n' "${BACKEND_SCRIPT}" >&2
  exit 1
fi

BACKEND_FIXTURE="${TMP_ROOT}/backend-fixture"
BACKEND_DATA_ROOT="${BACKEND_FIXTURE}/data"
mkdir -p "${BACKEND_DATA_ROOT}"
printf '122.233.30.4\n' >"${BACKEND_DATA_ROOT}/.serverhost"
printf 'test-agent-backend-122-233-30-4\n' >"${BACKEND_DATA_ROOT}/.serverid"
MALICIOUS_MARKER="${BACKEND_FIXTURE}/dotenv-executed"
cat >"${BACKEND_FIXTURE}/backend.env" <<'EOF'
SPRING_PROFILES_ACTIVE=prod
TEST_AGENT_DEPLOYMENT_MODE=internal
TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.4
TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-4
TEST_AGENT_DB_URL=jdbc:postgresql://raw-db-url-user:raw-db-url-password@122.233.30.147:5432/postgres?password=raw-db-query-value
TEST_AGENT_DB_USERNAME=postgres
TEST_AGENT_DB_PASSWORD=raw-db-password-value
TEST_AGENT_REDIS_HOST=122.233.30.20
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=raw-redis-password-value
TEST_AGENT_XXL_JOB_ENABLED=true
TEST_AGENT_XXL_JOB_MYSQL_URL=jdbc:mysql://raw-mysql-url-user:raw-mysql-url-password@122.233.30.148:3306/xxl_job?password=raw-mysql-query-value
TEST_AGENT_XXL_JOB_MYSQL_USERNAME=xxl_job
TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=raw-xxl-password-value
TEST_AGENT_XXL_JOB_ACCESS_TOKEN=raw-xxl-access-token-value
TEST_AGENT_XXL_JOB_ADMIN_PORT=18080
TEST_AGENT_XXL_JOB_EXECUTOR_PORT=9999
TEST_AGENT_OPENCODE_MANAGER_TOKEN=raw-manager-token-value
TEST_AGENT_INTERNAL_PROXY_API_KEY=raw-proxy-api-key-value
SYS_DATA_ROOT_DIR=/must/not/be/used/by/fixture
EOF
printf 'TEST_AGENT_API_TOKEN=$(touch %s)\n' "${MALICIOUS_MARKER}" >>"${BACKEND_FIXTURE}/backend.env"
cp "${BACKEND_FIXTURE}/backend.env" "${BACKEND_FIXTURE}/backend-mismatch.env"
sed 's/TEST_AGENT_SERVER_ADVERTISED_HOST=122\.233\.30\.4/TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.114/' \
  "${BACKEND_FIXTURE}/backend-mismatch.env" >"${BACKEND_FIXTURE}/backend-mismatch.env.tmp"
mv "${BACKEND_FIXTURE}/backend-mismatch.env.tmp" "${BACKEND_FIXTURE}/backend-mismatch.env"

make_backend_env_variant() {
  local target="$1" expression="$2"
  sed "${expression}" "${BACKEND_FIXTURE}/backend.env" >"${target}"
}
make_backend_env_variant "${BACKEND_FIXTURE}/backend-wrong-redis-host.env" \
  's/TEST_AGENT_REDIS_HOST=122\.233\.30\.20/TEST_AGENT_REDIS_HOST=122.233.30.21/'
make_backend_env_variant "${BACKEND_FIXTURE}/backend-wrong-redis-port.env" \
  's/TEST_AGENT_REDIS_PORT=6379/TEST_AGENT_REDIS_PORT=6380/'
make_backend_env_variant "${BACKEND_FIXTURE}/backend-wrong-mysql-host.env" \
  's/122\.233\.30\.148:3306\/xxl_job/122.233.30.149:3306\/xxl_job/'
make_backend_env_variant "${BACKEND_FIXTURE}/backend-wrong-mysql-port.env" \
  's/122\.233\.30\.148:3306\/xxl_job/122.233.30.148:3307\/xxl_job/'

backend_run() {
  PATH="${TEST_AGENT_DIAG_EXTRA_PATH:-}${FAKE_BIN}:${PATH}" \
    XXL_DIAG_FIXTURE_MODE="${XXL_DIAG_FIXTURE_MODE:-healthy}" \
    XXL_DIAG_BACKEND_IP="${XXL_DIAG_BACKEND_IP:-122.233.30.4}" \
    TEST_AGENT_DIAG_BACKEND_ENV="${TEST_AGENT_DIAG_BACKEND_ENV:-${BACKEND_FIXTURE}/backend.env}" \
    TEST_AGENT_DIAG_DATA_ROOT="${BACKEND_DATA_ROOT}" \
    TEST_AGENT_DIAG_PS_BIN="${TEST_AGENT_DIAG_PS_BIN:-ps}" \
    bash "${BACKEND_SCRIPT}" "$@"
}

backend_run --expected-host 122.233.30.4 --minutes 15 >"${TMP_ROOT}/backend-ok.log" 2>&1
grep -Fq '[PASS] 当前机器包含 expected host 122.233.30.4' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[PASS] systemd test-agent-backend is active/running with MainPID=4242' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[PASS] 127.0.0.1:8080 readiness is UP' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[PASS] 127.0.0.1:18080 XXL Admin readiness is UP' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[INFO] REDIS_ENDPOINT=122.233.30.20:6379' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[INFO] XXL_MYSQL_ENDPOINT=jdbc:mysql://122.233.30.148:3306/xxl_job' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[INFO] ADMIN_PORT=18080' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[INFO] EXECUTOR_PORT=9999' "${TMP_ROOT}/backend-ok.log"
grep -Eq '\[INFO\] TEST_AGENT_XXL_JOB_ACCESS_TOKEN=SET length=[0-9]+ sha256=[0-9a-f]{16}$' "${TMP_ROOT}/backend-ok.log"
grep -Fq 'https://diag.example/xxl-job-admin/#[REDACTED_FRAGMENT]' "${TMP_ROOT}/backend-ok.log"
grep -Fq 'https://diag.example/xxl-job-admin/?[REDACTED_QUERY]' "${TMP_ROOT}/backend-ok.log"
grep -Fq '/xxl-job-admin/?[REDACTED_QUERY]' "${TMP_ROOT}/backend-ok.log"
grep -Fq '/xxl-job-admin/#[REDACTED_FRAGMENT]' "${TMP_ROOT}/backend-ok.log"
grep -Fq 'Authorization=[REDACTED]' "${TMP_ROOT}/backend-ok.log"
test "$(grep -Fc 'Authorization=[REDACTED]' "${TMP_ROOT}/backend-ok.log")" -eq 3
grep -Fq 'payload={"token":"[REDACTED]"}' "${TMP_ROOT}/backend-ok.log"
grep -Fq 'ticket=[REDACTED]' "${TMP_ROOT}/backend-ok.log"
grep -Fq "cookie='[REDACTED]'" "${TMP_ROOT}/backend-ok.log"
grep -Fq 'token="[REDACTED]"' "${TMP_ROOT}/backend-ok.log"
grep -Fq 'password=[REDACTED]' "${TMP_ROOT}/backend-ok.log"
grep -Fq "secret='[REDACTED]'" "${TMP_ROOT}/backend-ok.log"
grep -Fq 'authorization="[REDACTED]"' "${TMP_ROOT}/backend-ok.log"
grep -Fq "digest='[REDACTED]'" "${TMP_ROOT}/backend-ok.log"
grep -Fq 'accessToken=[REDACTED]' "${TMP_ROOT}/backend-ok.log"
grep -Fq "access_token='[REDACTED]'" "${TMP_ROOT}/backend-ok.log"
grep -Fq 'apiToken="[REDACTED]"' "${TMP_ROOT}/backend-ok.log"
grep -Fq 'api_token=[REDACTED]' "${TMP_ROOT}/backend-ok.log"
grep -Fq "apiKey='[REDACTED]'" "${TMP_ROOT}/backend-ok.log"
grep -Fq 'api_key="[REDACTED]"' "${TMP_ROOT}/backend-ok.log"
grep -Fq "platform_session_digest='[REDACTED]'" "${TMP_ROOT}/backend-ok.log"
grep -Fq '[PASS] systemd ExecStart 指向固定后台 JAR' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[PASS] systemd EnvironmentFiles 包含固定 backend.env' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[PASS] 专用 Linux 仅有一个后台 Java，PID 与 MainPID=4242 一致' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[INFO] 4096-4115 为 opencode 用户进程端口池，非管理页首要链路；本脚本不执行 Docker/worker/manager 操作' "${TMP_ROOT}/backend-ok.log"
if grep -Fq 'sed:' "${TMP_ROOT}/backend-ok.log"; then
  printf 'backend diagnostics redaction failed\n' >&2
  exit 1
fi
test ! -e "${MALICIOUS_MARKER}"
if grep -Eq 'raw-(db|redis|xxl|manager|proxy|api|log|jdbc|mysql|unrelated|absolute|relative|bearer|quoted|sensitive)' "${TMP_ROOT}/backend-ok.log"; then
  printf 'backend diagnostics leaked sensitive fixture values\n' >&2
  exit 1
fi

set +e
XXL_DIAG_FIXTURE_MODE=backend-admin-down backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-admin-down.log" 2>&1
status=$?
set -e
test "${status}" -eq 1
grep -Fq '[FAIL] 127.0.0.1:18080 XXL Admin readiness 返回 HTTP 503 或非 UP 响应' "${TMP_ROOT}/backend-admin-down.log"

set +e
TEST_AGENT_DIAG_BACKEND_ENV="${BACKEND_FIXTURE}/backend-mismatch.env" backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-config-mismatch.log" 2>&1
status=$?
set -e
test "${status}" -eq 1
grep -Fq '[FAIL] advertised host 与 expected host 不一致' "${TMP_ROOT}/backend-config-mismatch.log"

for topology_case in wrong-redis-host wrong-redis-port wrong-mysql-host wrong-mysql-port; do
  set +e
  TEST_AGENT_DIAG_BACKEND_ENV="${BACKEND_FIXTURE}/backend-${topology_case}.env" \
    backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-${topology_case}.log" 2>&1
  status=$?
  set -e
  test "${status}" -eq 1
  grep -Fq '[FAIL] 固定共享拓扑不一致' "${TMP_ROOT}/backend-${topology_case}.log"
done

for systemd_case in backend-extra-java backend-wrong-execstart backend-execstart-jar-backup backend-wrong-environment-files backend-environment-files-old backend-ps-jar-backup; do
  set +e
  XXL_DIAG_FIXTURE_MODE="${systemd_case}" backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/${systemd_case}.log" 2>&1
  status=$?
  set -e
  test "${status}" -eq 1
  case "${systemd_case}" in
    backend-extra-java) grep -Fq '[FAIL] 专用 Linux 上的 Java 进程数量不是 1' "${TMP_ROOT}/${systemd_case}.log" ;;
    backend-wrong-execstart) grep -Fq '[FAIL] systemd ExecStart 未指向 /data/testagent/dist/backend/test-agent-app.jar' "${TMP_ROOT}/${systemd_case}.log" ;;
    backend-execstart-jar-backup) grep -Fq '[FAIL] systemd ExecStart 未指向 /data/testagent/dist/backend/test-agent-app.jar' "${TMP_ROOT}/${systemd_case}.log" ;;
    backend-wrong-environment-files) grep -Fq '[FAIL] systemd EnvironmentFiles 未包含 /data/testagent/config/backend.env' "${TMP_ROOT}/${systemd_case}.log" ;;
    backend-environment-files-old) grep -Fq '[FAIL] systemd EnvironmentFiles 未包含 /data/testagent/config/backend.env' "${TMP_ROOT}/${systemd_case}.log" ;;
    backend-ps-jar-backup) grep -Fq '[FAIL] 专用 Linux 上的 Java 进程数量不是 1' "${TMP_ROOT}/${systemd_case}.log" ;;
  esac
done

set +e
XXL_DIAG_FIXTURE_MODE=backend-ps-error backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-ps-error.log" 2>&1
status=$?
set -e
test "${status}" -eq 2
grep -Fq '[FAIL] ps 不可用，无法读取 Java 进程状态' "${TMP_ROOT}/backend-ps-error.log"

XXL_DIAG_FIXTURE_MODE=backend-log-no-match backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-log-no-match.log" 2>&1
grep -Fq '[INFO] 最近日志中没有命中诊断关键词' "${TMP_ROOT}/backend-log-no-match.log"

set +e
TEST_AGENT_DIAG_EXTRA_PATH="${BROKEN_SHA_BIN}:" backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-missing-sha.log" 2>&1
status=$?
set -e
test "${status}" -eq 2
grep -Fq '[FAIL] 缺少可用的 SHA-256 实现' "${TMP_ROOT}/backend-missing-sha.log"

set +e
TEST_AGENT_DIAG_EXTRA_PATH="${FILTER_FAILURE_BIN}:" XXL_DIAG_FIXTURE_MODE=backend-filter-failure \
  backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-filter-failure.log" 2>&1
status=$?
set -e
test "${status}" -eq 2
grep -Fq '[FAIL] 日志关键词过滤执行失败' "${TMP_ROOT}/backend-filter-failure.log"

set +e
TEST_AGENT_DIAG_EXTRA_PATH="${REDACTION_FAILURE_BIN}:" XXL_DIAG_FIXTURE_MODE=backend-redaction-failure \
  backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-redaction-failure.log" 2>&1
status=$?
set -e
test "${status}" -eq 2
grep -Fq '[FAIL] 日志脱敏执行失败' "${TMP_ROOT}/backend-redaction-failure.log"

set +e
TEST_AGENT_DIAG_PS_BIN=missing-ps-command backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-missing-ps.log" 2>&1
status=$?
set -e
test "${status}" -eq 2
grep -Fq '[FAIL] 缺少 ps，无法完成后台诊断' "${TMP_ROOT}/backend-missing-ps.log"

for misuse in missing-host invalid-host minutes-low minutes-high wrong-machine; do
  set +e
  case "${misuse}" in
    missing-host) backend_run >"${TMP_ROOT}/backend-${misuse}.log" 2>&1 ;;
    invalid-host) backend_run --expected-host 122.233.30.5 >"${TMP_ROOT}/backend-${misuse}.log" 2>&1 ;;
    minutes-low) backend_run --expected-host 122.233.30.4 --minutes 4 >"${TMP_ROOT}/backend-${misuse}.log" 2>&1 ;;
    minutes-high) backend_run --expected-host 122.233.30.4 --minutes 121 >"${TMP_ROOT}/backend-${misuse}.log" 2>&1 ;;
    wrong-machine) XXL_DIAG_BACKEND_IP=122.233.30.114 backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-${misuse}.log" 2>&1 ;;
  esac
  status=$?
  set -e
  test "${status}" -eq 2
done

READONLY_SQL_FILE="${ROOT_DIR}/deploy/internal/xxl-job-readonly-check.sql"
if [[ ! -f "${READONLY_SQL_FILE}" ]]; then
  printf 'missing XXL-JOB read-only SQL: %s\n' "${READONLY_SQL_FILE}" >&2
  exit 1
fi

# 只检查去除 -- 注释后的可执行 SQL，避免运维说明中的敏感词触发误报。
readonly_sql="$(sed -E 's/--.*$//' "${READONLY_SQL_FILE}")"
while IFS= read -r -d ';' statement; do
  trimmed_statement="$(printf '%s' "${statement}" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
  statement_first_word="$(printf '%s\n' "${trimmed_statement}" | awk 'NF { print $1; exit }' | tr '[:lower:]' '[:upper:]')"
  [[ -z "${statement_first_word}" ]] && continue
  case "${statement_first_word}" in
    SELECT|SHOW|WITH) ;;
    *)
      printf 'XXL-JOB read-only SQL contains a non-read-only statement: %s\n' "${statement_first_word:-<empty>}" >&2
      exit 1
      ;;
  esac

  # DML REPLACE 已由语句首词白名单拒绝；这里允许只读字符串函数 REPLACE(...)。
  if printf '%s\n' "${trimmed_statement}" | grep -Eqi '(^|[^[:alnum:]_])(INSERT|UPDATE|DELETE|MERGE|TRUNCATE|ALTER|CREATE|DROP|CALL|GRANT|REVOKE|LOCK|UNLOCK|SET|START|TRANSACTION|COMMIT|ROLLBACK)([^[:alnum:]_]|$)'; then
    printf 'XXL-JOB read-only SQL contains a forbidden keyword\n' >&2
    exit 1
  fi

  safe_digest_statement="$(printf '%s' "${trimmed_statement}" | sed -E 's/CHAR_LENGTH[[:space:]]*\([[:space:]]*platform_session_digest[[:space:]]*\)//Ig')"
  if printf '%s\n' "${safe_digest_statement}" | grep -Eqi '(^|[^[:alnum:]_])(password|token|platform_session_digest|executor_param|trigger_msg|handle_msg)([^[:alnum:]_]|$)'; then
    printf 'XXL-JOB read-only SQL projects a sensitive column or value\n' >&2
    exit 1
  fi
done < <(printf '%s' "${readonly_sql}")

printf 'XXL-JOB read-only SQL static boundary verified\n'

printf 'XXL-JOB enterprise diagnostics verified\n'
