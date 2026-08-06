#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
COLLECTOR="${ROOT_DIR}/deploy/internal/collect-recent-process-logs.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/verify-process-log-collector.XXXXXX")"
INSTALL_ROOT="${TMP_ROOT}/data/testagent"
NGINX_HOME="${TMP_ROOT}/data/apps/nginx"
OUTPUT_DIR="${TMP_ROOT}/output"
FAKE_BIN="${TMP_ROOT}/bin"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

mkdir -p \
  "${INSTALL_ROOT}/config" \
  "${INSTALL_ROOT}/data/agent-opencode/manager/worker/logs" \
  "${INSTALL_ROOT}/data/agent-opencode/manager/worker/processes" \
  "${NGINX_HOME}/sbin" \
  "${NGINX_HOME}/conf" \
  "${NGINX_HOME}/logs" \
  "${OUTPUT_DIR}" \
  "${FAKE_BIN}"

RECENT_ISO_UTC="$(date -u '+%Y-%m-%dT%H:%M:%S')"
RECENT_MANAGER_LOCAL="$(date '+%Y/%m/%d %H:%M:%S')"
RECENT_NGINX_ACCESS="$(date '+%d/%b/%Y:%H:%M:%S %z')"

printf '%s\n' \
  'TEST_AGENT_DB_PASSWORD=config-secret-must-not-be-collected' \
  'TEST_AGENT_OPENCODE_MANAGER_TOKEN=config-manager-secret' \
  >"${INSTALL_ROOT}/config/backend.env"
printf '122.233.30.4\n' >"${INSTALL_ROOT}/data/.serverhost"
printf '%s\n' \
  "timestamp=${RECENT_ISO_UTC}Z listening on 14097" \
  "timestamp=${RECENT_ISO_UTC}Z ERROR connection refused while checking provider" \
  "timestamp=${RECENT_ISO_UTC}Z {\"prompt\":\"user says ERROR and password=prompt-secret\"}" \
  "${RECENT_MANAGER_LOCAL} event=manager_command_entry command=health timeoutMs=10000" \
  'timestamp=2020-01-01T00:00:00Z ERROR old-inline-managed-error' \
  >"${INSTALL_ROOT}/data/agent-opencode/manager/worker/logs/000857009-20260803T054400.000000000Z-14097.log"
printf '{"unifiedAuthId":"000857009","token":"state-secret"}\n' \
  >"${INSTALL_ROOT}/data/agent-opencode/manager/worker/processes/14097.json"
printf 'old managed ERROR must not be collected\n' \
  >"${INSTALL_ROOT}/data/agent-opencode/manager/worker/logs/old-user-20200101T000000.000000000Z-14098.log"
touch -t 202001010000 \
  "${INSTALL_ROOT}/data/agent-opencode/manager/worker/logs/old-user-20200101T000000.000000000Z-14098.log"

printf '%s\n' \
  "127.0.0.1 - - [${RECENT_NGINX_ACCESS}] \"GET /api/run?ticket=nginx-ticket-secret HTTP/1.1\" 502 0" \
  '127.0.0.1 - - [01/Jan/2020:00:00:00 +0800] "GET /old-inline-nginx-error HTTP/1.1" 502 0' \
  >"${NGINX_HOME}/logs/access.log"
printf '%s\n' \
  "${RECENT_MANAGER_LOCAL} [error] upstream timed out while reading response header from upstream" \
  'Authorization: Bearer nginx-bearer-secret' \
  >"${NGINX_HOME}/logs/error.log"
printf 'old nginx ERROR must not be collected\n' >"${NGINX_HOME}/logs/error.log.9"
touch -t 202001010000 "${NGINX_HOME}/logs/error.log.9"
printf 'events {}\nhttp {}\n' >"${NGINX_HOME}/conf/nginx.conf"

cat >"${NGINX_HOME}/sbin/nginx" <<'EOF'
#!/usr/bin/env bash
printf 'nginx: configuration file syntax is ok\n'
printf 'nginx: configuration test is successful\n'
EOF

cat >"${FAKE_BIN}/systemctl" <<'EOF'
#!/usr/bin/env bash
case "${1:-}" in
  show)
    printf '%s\n' \
      'ActiveState=active' \
      'SubState=running' \
      'MainPID=4242' \
      'ExecMainStatus=0' \
      'NRestarts=1'
    ;;
  status)
    printf '%s\n' \
      'test-agent-backend.service - Test Agent Backend' \
      'Active: active (running)'
    ;;
esac
EOF

cat >"${FAKE_BIN}/journalctl" <<'EOF'
#!/usr/bin/env bash
if [[ "$*" == *'-u docker'* ]]; then
  printf '%s\n' \
    '2026-08-03T13:43:58+0800 docker resource temporarily unavailable' \
    '2026-08-03T13:43:59+0800 clientSecret=docker-journal-secret'
else
  printf '%s\n' \
    '2026-08-03T13:44:00+0800 backend FlywayValidateException Migration checksum mismatch' \
    '2026-08-03T13:44:01+0800 backend StackOverflowError in request logger' \
    '2026-08-03T13:44:02+0800 Authorization: Bearer backend-bearer-secret' \
    '2026-08-03T13:44:03+0800 contextToken=backend-context-secret'
fi
EOF

cat >"${FAKE_BIN}/docker" <<'EOF'
#!/usr/bin/env bash
case "${1:-}" in
  ps)
    if [[ "$*" == *'{{.Names}}|{{.Image}}'* ]]; then
      printf 'test-agent-opencode-worker|worker:test|Up 2 days|14096-15095/tcp\n'
    else
      printf 'test-agent-opencode-worker\n'
    fi
    ;;
  inspect)
    printf 'name=/test-agent-opencode-worker image=worker:test status=running running=true started=2026-08-02T00:00:00Z finished= exit=0 oom=false restarts=2 health=healthy\n'
    ;;
  logs)
    if [[ "$*" != *'--since 72h'* ]]; then
      printf 'unsupported docker --since value: %s\n' "$*" >&2
      exit 41
    fi
    printf '%s\n' \
      '2026-08-03T13:44:04+0800 manager websocket disconnected' \
      '2026-08-03T13:44:05+0800 Address already in use port=14097' \
      '2026-08-03T13:44:06+0800 token=container-log-secret'
    ;;
  *)
    exit 1
    ;;
esac
EOF

cat >"${FAKE_BIN}/curl" <<'EOF'
#!/usr/bin/env bash
if [[ "$*" == *'-w '* ]]; then
  printf 'http_code=200 total_seconds=0.012\n'
else
  printf '{"status":"UP"}\n'
fi
EOF

cat >"${FAKE_BIN}/ss" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' \
  'State Recv-Q Send-Q Local Address:Port Peer Address:Port Process' \
  'LISTEN 0 128 0.0.0.0:8080 0.0.0.0:* users:(("java",pid=4242,fd=7))'
EOF

cat >"${FAKE_BIN}/free" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' 'total used free' 'Mem: 8192 4096 4096'
EOF

cat >"${FAKE_BIN}/ps" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' \
  '  PID  PPID USER     STARTED ELAPSED %CPU %MEM COMMAND' \
  ' 4242     1 root     Aug  3  01:00   1.0  2.0 java' \
  ' 5252     1 root     Aug  3  01:00   0.1  0.2 nginx'
EOF

chmod +x "${NGINX_HOME}/sbin/nginx" "${FAKE_BIN}"/*

bash -n "${COLLECTOR}"
if grep -nE '(^|[;&|[:space:]])(rg|jq|jd)([;&|[:space:]]|$)' "${COLLECTOR}"; then
  printf 'Collector invokes a forbidden site tool\n' >&2
  exit 1
fi

if PATH="${FAKE_BIN}:${PATH}" bash "${COLLECTOR}" \
  --days 3 \
  --role all \
  --output-dir "${OUTPUT_DIR}" \
  --install-root "${INSTALL_ROOT}" \
  --nginx-home "${NGINX_HOME}" \
  --node-label test-node; then
  printf 'Collector unexpectedly accepted missing sensitive-data acknowledgement\n' >&2
  exit 1
fi

if PATH="${FAKE_BIN}:${PATH}" bash "${COLLECTOR}" \
  --include-sensitive --days 15 --role backend \
  --output-dir "${OUTPUT_DIR}" \
  --install-root "${INSTALL_ROOT}" \
  --nginx-home "${NGINX_HOME}" \
  --node-label test-node; then
  printf 'Collector unexpectedly accepted more than 14 days\n' >&2
  exit 1
fi

PATH="${FAKE_BIN}:${PATH}" bash "${COLLECTOR}" \
  --include-sensitive \
  --days 3 \
  --role all \
  --output-dir "${OUTPUT_DIR}" \
  --install-root "${INSTALL_ROOT}" \
  --nginx-home "${NGINX_HOME}" \
  --node-label test-node

ARCHIVE="$(find "${OUTPUT_DIR}" -maxdepth 1 -type f \
  -name 'test-agent-process-logs-SENSITIVE-test-node-3d-*.tar.gz' -print -quit)"
test -n "${ARCHIVE}"
test -f "${ARCHIVE}.sha256"
test "$(stat -c '%a' "${ARCHIVE}" 2>/dev/null || stat -f '%Lp' "${ARCHIVE}")" = '600'
test "$(stat -c '%a' "${ARCHIVE}.sha256" 2>/dev/null || stat -f '%Lp' "${ARCHIVE}.sha256")" = '600'

EXTRACT_ROOT="${TMP_ROOT}/extract"
mkdir -p "${EXTRACT_ROOT}"
tar -C "${EXTRACT_ROOT}" -xzf "${ARCHIVE}"

grep -Fq 'RUNTIME_FATAL|' "${EXTRACT_ROOT}/DIAGNOSTIC-SUMMARY.txt"
grep -Fq 'MIGRATION|' "${EXTRACT_ROOT}/DIAGNOSTIC-SUMMARY.txt"
grep -Fq 'PORT_BIND|' "${EXTRACT_ROOT}/DIAGNOSTIC-SUMMARY.txt"
grep -Fq 'PROXY|' "${EXTRACT_ROOT}/DIAGNOSTIC-SUMMARY.txt"
grep -Fq 'MANAGER_LINK|' "${EXTRACT_ROOT}/DIAGNOSTIC-SUMMARY.txt"
grep -Fq 'MANAGER_LINK|1|' "${EXTRACT_ROOT}/DIAGNOSTIC-SUMMARY.txt"
grep -R -Fq 'manager websocket disconnected' "${EXTRACT_ROOT}/logs"
grep -R -Fq '[REDACTED_SENSITIVE_LOG_LINE]' "${EXTRACT_ROOT}/logs"
grep -R -Fq '?[REDACTED_QUERY]' "${EXTRACT_ROOT}/logs"
grep -R -Fq 'source_name_sha256=' "${EXTRACT_ROOT}/logs"

for forbidden in \
  config-secret-must-not-be-collected \
  config-manager-secret \
  prompt-secret \
  state-secret \
  nginx-ticket-secret \
  nginx-bearer-secret \
  docker-journal-secret \
  backend-bearer-secret \
  backend-context-secret \
  container-log-secret \
  old-user \
  'old managed ERROR' \
  'old nginx ERROR' \
  old-inline-managed-error \
  old-inline-nginx-error \
  000857009; do
  if grep -R -Fq "${forbidden}" "${EXTRACT_ROOT}"; then
    printf 'Diagnostic bundle leaked or included forbidden fixture: %s\n' "${forbidden}" >&2
    exit 1
  fi
done

test ! -e "${EXTRACT_ROOT}/config"
test ! -e "${EXTRACT_ROOT}/processes"
if find "${EXTRACT_ROOT}" -type f \
  \( -name '*.env' -o -name '*.json' -o -name '*.jar' -o -name '*private*' \) \
  | grep -q .; then
  printf 'Diagnostic bundle contains a forbidden config/state/binary file\n' >&2
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  (cd "${OUTPUT_DIR}" && sha256sum -c "$(basename "${ARCHIVE}").sha256")
else
  (cd "${OUTPUT_DIR}" && shasum -a 256 -c "$(basename "${ARCHIVE}").sha256")
fi

printf 'Recent process log collector verified without JSON/search-specific site tools\n'
