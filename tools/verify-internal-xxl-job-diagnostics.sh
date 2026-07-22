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
chmod +x "${FAKE_BIN}/getent" "${FAKE_BIN}/curl"

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

printf 'XXL-JOB enterprise diagnostics verified\n'
