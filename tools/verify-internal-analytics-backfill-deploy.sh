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
JOURNALCTL_LOG="${TMP_ROOT}/journalctl.log"
BACKEND_LOG_FILE="${TMP_ROOT}/logs/backend.log"
COMPLETION_STATE_FILE="${TMP_ROOT}/analytics-clickhouse-backfill.state"
mkdir -p "${FAKE_BIN}" "$(dirname "${BACKEND_LOG_FILE}")"
export SYSTEMCTL_LOG JOURNALCTL_LOG BACKEND_LOG_FILE

write_backend_env() {
  rm -f "${COMPLETION_STATE_FILE}" "${BACKEND_LOG_FILE}"
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
  'if [[ "${TEST_AGENT_FIXTURE_BACKFILL_RESULT:-success}" == "file-success" && "${1:-}" == "restart" ]]; then' \
  '  mkdir -p "$(dirname "${BACKEND_LOG_FILE}")"' \
  '  printf "%s\n" "ClickHouse 运营回填完成, skipped=true, verified=true, sourceEvents=0, targetFacts=0, cleaned=false" >>"${BACKEND_LOG_FILE}"' \
  'fi' \
  'exit 0' \
  >"${FAKE_BIN}/systemctl"
printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${FAKE_BIN}/curl"
printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${FAKE_BIN}/sleep"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'printf "%s\n" "$*" >>"${JOURNALCTL_LOG}"' \
  'if [[ "$*" == *"-n 0"* && "$*" == *"--show-cursor"* ]]; then' \
  '  printf "%s\n" "-- cursor: fixture-cursor"' \
  'elif [[ "${TEST_AGENT_FIXTURE_BACKFILL_RESULT:-success}" == "failure" ]]; then' \
  '  printf "%s\n" "Application run failed" "-- cursor: fixture-failure"' \
  'elif [[ "${TEST_AGENT_FIXTURE_BACKFILL_RESULT:-success}" == "unverified" ]]; then' \
  '  printf "%s\n" "ClickHouse 运营回填完成, skipped=false, verified=false, sourceEvents=42, targetFacts=41, cleaned=false" "-- cursor: fixture-unverified"' \
  'elif [[ "${TEST_AGENT_FIXTURE_BACKFILL_RESULT:-success}" == "file-success" ]]; then' \
  '  printf "%s\n" "-- cursor: fixture-file-fallback"' \
  'elif [[ "$*" == *"--after-cursor fixture-cursor-1"* ]]; then' \
  '  printf "%s\n" "ClickHouse 运营回填完成, skipped=false, verified=true, sourceEvents=42, targetFacts=42, cleaned=false"' \
  '  for ((index = 0; index < 450; index++)); do printf "startup-noise-%s\n" "${index}"; done' \
  '  printf "%s\n" "-- cursor: fixture-complete"' \
  'else' \
  '  printf "%s\n" "Analytics hourly rollup rebuilt, start=2026-08-18T08:00:00Z, end=2026-08-18T09:00:00Z, rows=42, traceId=analytics-clickhouse-backfill"' \
  '  printf "%s\n" "-- cursor: fixture-cursor-1"' \
  'fi' \
  >"${FAKE_BIN}/journalctl"
chmod +x "${FAKE_BIN}/id" "${FAKE_BIN}/systemctl" "${FAKE_BIN}/curl" \
  "${FAKE_BIN}/sleep" "${FAKE_BIN}/journalctl"

write_backend_env
success_output="$(PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_FIXTURE_BACKFILL_RESULT=success \
  bash "${SCRIPT}" \
    --backend-env "${BACKEND_ENV}" \
    --backend-health-url http://127.0.0.1:18080/actuator/health \
    --backend-readiness-url http://127.0.0.1:18080/actuator/health/readiness \
    --backend-log-file "${BACKEND_LOG_FILE}" \
    --completion-state-file "${COMPLETION_STATE_FILE}" \
    --timeout-seconds 3 2>&1)"
grep -Fq 'ClickHouse analytics backfill verified' <<<"${success_output}"
grep -Fq 'ClickHouse analytics backfill progress: Analytics hourly rollup rebuilt' \
  <<<"${success_output}"
grep -Fq 'ClickHouse analytics backfill completion observed: ClickHouse 运营回填完成' \
  <<<"${success_output}"
grep -Fxq 'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=false' "${BACKEND_ENV}"
grep -Fxq 'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_STATUS=VERIFIED' "${COMPLETION_STATE_FILE}"
test "$(grep -c '^restart test-agent-backend$' "${SYSTEMCTL_LOG}")" -eq 1
grep -Fq -- '--after-cursor fixture-cursor --show-cursor --no-pager' "${JOURNALCTL_LOG}"
grep -Fq -- '--after-cursor fixture-cursor-1 --show-cursor --no-pager' "${JOURNALCTL_LOG}"
if grep -Fq -- '-n 400' "${JOURNALCTL_LOG}"; then
  echo 'Backfill orchestration still uses a lossy fixed-size journal window' >&2
  exit 1
fi
if grep -Fq 'secret-must-not-print' <<<"${success_output}"; then
  echo 'Successful backfill orchestration leaked a secret' >&2
  exit 1
fi

# 已持久化完成标记的后续平台包不能再次开启 Runner 或重启 Java。
state_skip_output="$(PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_FIXTURE_BACKFILL_RESULT=failure \
  bash "${SCRIPT}" \
    --backend-env "${BACKEND_ENV}" \
    --backend-health-url http://127.0.0.1:18080/actuator/health \
    --backend-readiness-url http://127.0.0.1:18080/actuator/health/readiness \
    --backend-log-file "${BACKEND_LOG_FILE}" \
    --completion-state-file "${COMPLETION_STATE_FILE}" \
    --timeout-seconds 3 2>&1)"
grep -Fq 'already verified; skip runner and backend restart' <<<"${state_skip_output}"
test "$(grep -c '^restart test-agent-backend$' "${SYSTEMCTL_LOG}")" -eq 1

# 兼容旧包已经输出成功日志、但尚未写完成标记的现场，直接登记状态且不重启。
rm -f "${COMPLETION_STATE_FILE}"
printf '%s\n' \
  'timestamp="2026-08-18T14:00:00+08:00" message="ClickHouse 运营回填完成, skipped=false, verified=true, sourceEvents=42, targetFacts=42, cleaned=false"' \
  >"${BACKEND_LOG_FILE}"
: >"${SYSTEMCTL_LOG}"
adopt_output="$(PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_FIXTURE_BACKFILL_RESULT=failure \
  bash "${SCRIPT}" \
    --backend-env "${BACKEND_ENV}" \
    --backend-log-file "${BACKEND_LOG_FILE}" \
    --completion-state-file "${COMPLETION_STATE_FILE}" \
    --timeout-seconds 3 2>&1)"
grep -Fq 'Adopted existing verified ClickHouse analytics backfill completion from backend log' \
  <<<"${adopt_output}"
grep -Fq 'already verified; skip runner and backend restart' <<<"${adopt_output}"
test ! -s "${SYSTEMCTL_LOG}"

# journald 无法返回本次日志时，必须从同一 Java 的滚动文件识别完成信号。
write_backend_env
: >"${SYSTEMCTL_LOG}"
: >"${JOURNALCTL_LOG}"
file_output="$(PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_FIXTURE_BACKFILL_RESULT=file-success \
  bash "${SCRIPT}" \
    --backend-env "${BACKEND_ENV}" \
    --backend-log-file "${BACKEND_LOG_FILE}" \
    --completion-state-file "${COMPLETION_STATE_FILE}" \
    --timeout-seconds 3 2>&1)"
grep -Fq 'ClickHouse analytics backfill completion observed: ClickHouse 运营回填完成' \
  <<<"${file_output}"
grep -Fxq 'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_STATUS=VERIFIED' "${COMPLETION_STATE_FILE}"
test "$(grep -c '^restart test-agent-backend$' "${SYSTEMCTL_LOG}")" -eq 1

write_backend_env
: >"${SYSTEMCTL_LOG}"
: >"${JOURNALCTL_LOG}"
if failure_output="$(PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_FIXTURE_BACKFILL_RESULT=failure \
  bash "${SCRIPT}" \
    --backend-env "${BACKEND_ENV}" \
    --backend-log-file "${BACKEND_LOG_FILE}" \
    --completion-state-file "${COMPLETION_STATE_FILE}" \
    --timeout-seconds 3 2>&1)"; then
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

# 完成日志必须明确 verified=true，不能把校验失败的结果持久化为成功状态。
write_backend_env
: >"${SYSTEMCTL_LOG}"
if unverified_output="$(PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_FIXTURE_BACKFILL_RESULT=unverified \
  bash "${SCRIPT}" \
    --backend-env "${BACKEND_ENV}" \
    --backend-log-file "${BACKEND_LOG_FILE}" \
    --completion-state-file "${COMPLETION_STATE_FILE}" \
    --timeout-seconds 3 2>&1)"; then
  echo 'Backfill orchestration unexpectedly accepted verified=false' >&2
  exit 1
fi
grep -Fq 'failed during backend startup' <<<"${unverified_output}"
grep -Fxq 'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=false' "${BACKEND_ENV}"
test ! -e "${COMPLETION_STATE_FILE}"
test "$(grep -c '^restart test-agent-backend$' "${SYSTEMCTL_LOG}")" -eq 2

write_backend_env
printf 'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=false\n' >>"${BACKEND_ENV}"
if PATH="${FAKE_BIN}:${PATH}" bash "${SCRIPT}" \
  --backend-env "${BACKEND_ENV}" \
  --backend-log-file "${BACKEND_LOG_FILE}" \
  --completion-state-file "${COMPLETION_STATE_FILE}" \
  --timeout-seconds 3 >/dev/null 2>&1; then
  echo 'Backfill orchestration unexpectedly accepted a duplicate switch' >&2
  exit 1
fi

write_backend_env
printf '%s\n' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_STATE_VERSION=1' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_CUTOVER_ID=analytics-v1' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_STATUS=UNKNOWN' \
  >"${COMPLETION_STATE_FILE}"
if PATH="${FAKE_BIN}:${PATH}" bash "${SCRIPT}" \
  --backend-env "${BACKEND_ENV}" \
  --backend-log-file "${BACKEND_LOG_FILE}" \
  --completion-state-file "${COMPLETION_STATE_FILE}" \
  --timeout-seconds 3 >/dev/null 2>&1; then
  echo 'Backfill orchestration unexpectedly accepted an unknown completion marker' >&2
  exit 1
fi

echo 'ClickHouse deployment backfill persistent skip, legacy-log adoption, file fallback, cursor progress, rollback, secret redaction and duplicate-key gates verified'
