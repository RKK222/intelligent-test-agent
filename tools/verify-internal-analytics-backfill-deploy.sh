#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${ROOT_DIR}/deploy/internal/run-analytics-clickhouse-backfill.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-analytics-backfill-verify.XXXXXX")"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

FAKE_BIN="${TMP_ROOT}/bin"
BACKEND_ENV="${TMP_ROOT}/backend.env"
SYSTEMCTL_LOG="${TMP_ROOT}/systemctl.log"
mkdir -p "${FAKE_BIN}"
export SYSTEMCTL_LOG

write_backend_env() {
  printf '%s\n' \
    'TEST_AGENT_ANALYTICS_CLICKHOUSE_ENABLED=true' \
    'TEST_AGENT_ANALYTICS_CLICKHOUSE_PASSWORD=secret-must-not-print' \
    'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=false' \
    'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_START=2025-01-01T00:00:00Z' \
    'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_END=' \
    'TEST_AGENT_ANALYTICS_CLICKHOUSE_CLEANUP_LEGACY_ROLLUPS=false' \
    >"${BACKEND_ENV}"
  chmod 0600 "${BACKEND_ENV}"
}

printf '%s\n' \
  '#!/usr/bin/env bash' \
  'if [[ "${1:-}" == "-u" ]]; then printf "0\n"; else command /usr/bin/id "$@"; fi' \
  >"${FAKE_BIN}/id"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'printf "%s\n" "$*" >>"${SYSTEMCTL_LOG}"' \
  'exit 0' \
  >"${FAKE_BIN}/systemctl"
printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${FAKE_BIN}/curl"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'if [[ "$*" == *"--show-cursor"* ]]; then' \
  '  printf "%s\n" "-- cursor: fixture-cursor"' \
  'elif [[ "${TEST_AGENT_FIXTURE_BACKFILL_RESULT:-success}" == "success" ]]; then' \
  '  printf "%s\n" "ClickHouse 运营回填完成, skipped=false, verified=true, sourceEvents=42, targetFacts=42, cleaned=false"' \
  'else' \
  '  printf "%s\n" "Application run failed"' \
  'fi' \
  >"${FAKE_BIN}/journalctl"
chmod +x "${FAKE_BIN}/id" "${FAKE_BIN}/systemctl" "${FAKE_BIN}/curl" \
  "${FAKE_BIN}/journalctl"

write_backend_env
success_output="$(PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_FIXTURE_BACKFILL_RESULT=success \
  bash "${SCRIPT}" \
    --backend-env "${BACKEND_ENV}" \
    --backend-health-url http://127.0.0.1:18080/actuator/health \
    --backend-readiness-url http://127.0.0.1:18080/actuator/health/readiness \
    --timeout-seconds 3 2>&1)"
grep -Fq 'ClickHouse analytics backfill verified' <<<"${success_output}"
grep -Fxq 'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=false' "${BACKEND_ENV}"
test "$(grep -c '^restart test-agent-backend$' "${SYSTEMCTL_LOG}")" -eq 1
if grep -Fq 'secret-must-not-print' <<<"${success_output}"; then
  echo 'Successful backfill orchestration leaked a secret' >&2
  exit 1
fi

write_backend_env
: >"${SYSTEMCTL_LOG}"
if failure_output="$(PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_FIXTURE_BACKFILL_RESULT=failure \
  bash "${SCRIPT}" --backend-env "${BACKEND_ENV}" --timeout-seconds 3 2>&1)"; then
  echo 'Backfill orchestration unexpectedly accepted a Java startup failure' >&2
  exit 1
fi
grep -Fq 'Restoring persistent ClickHouse backfill switch after failure' <<<"${failure_output}"
grep -Fxq 'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=false' "${BACKEND_ENV}"
test "$(grep -c '^restart test-agent-backend$' "${SYSTEMCTL_LOG}")" -eq 2
if grep -Fq 'secret-must-not-print' <<<"${failure_output}"; then
  echo 'Failed backfill orchestration leaked a secret' >&2
  exit 1
fi

write_backend_env
printf 'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=false\n' >>"${BACKEND_ENV}"
if PATH="${FAKE_BIN}:${PATH}" bash "${SCRIPT}" \
  --backend-env "${BACKEND_ENV}" --timeout-seconds 3 >/dev/null 2>&1; then
  echo 'Backfill orchestration unexpectedly accepted a duplicate switch' >&2
  exit 1
fi

echo 'ClickHouse deployment backfill success, rollback, secret redaction and duplicate-key gates verified'
