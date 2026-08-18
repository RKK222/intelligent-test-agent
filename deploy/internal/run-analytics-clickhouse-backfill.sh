#!/usr/bin/env bash
set -euo pipefail

BACKEND_ENV="/data/testagent/config/backend.env"
BACKEND_SERVICE="test-agent-backend"
BACKEND_HEALTH_URL="http://127.0.0.1:8080/actuator/health"
BACKEND_READINESS_URL="http://127.0.0.1:8080/actuator/health/readiness"
BACKEND_LOG_FILE=""
COMPLETION_STATE_FILE=""
TIMEOUT_SECONDS=7200
RESTORE_REQUIRED=0
BACKEND_LOG_START_BYTE=1

usage() {
  cat <<'USAGE'
Usage: run-analytics-clickhouse-backfill.sh [options]

Temporarily enables the existing Java ClickHouse backfill runner, verifies its
completion log, and restores the persistent backfill switch to false.

Options:
  --backend-env <path>       backend.env path.
  --backend-service <name>   systemd service name. Default: test-agent-backend.
  --backend-health-url <url> Backend health URL.
  --backend-readiness-url <url>
                             Backend readiness URL.
  --backend-log-file <path> Backend rolling log. Default: <install-root>/logs/backend.log.
  --completion-state-file <path>
                             Persistent verified marker. Default: <config-dir>/analytics-clickhouse-backfill.state.
  --timeout-seconds <n>      Backfill completion timeout. Default: 7200.
  -h, --help                 Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backend-env)
      BACKEND_ENV="$2"
      shift 2
      ;;
    --backend-service)
      BACKEND_SERVICE="$2"
      shift 2
      ;;
    --backend-health-url)
      BACKEND_HEALTH_URL="$2"
      shift 2
      ;;
    --backend-readiness-url)
      BACKEND_READINESS_URL="$2"
      shift 2
      ;;
    --backend-log-file)
      BACKEND_LOG_FILE="$2"
      shift 2
      ;;
    --completion-state-file)
      COMPLETION_STATE_FILE="$2"
      shift 2
      ;;
    --timeout-seconds)
      TIMEOUT_SECONDS="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

key_count() {
  local file="$1" key="$2"
  grep -c "^${key}=" "${file}" || true
}

# dotenv 只读取最后一次文本赋值，不 source 现场密钥文件。
env_value() {
  local file="$1" wanted_key="$2"
  local line key value result=""
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ "${line}" == *=* ]] || continue
    key="${line%%=*}"
    [[ "${key}" == "${wanted_key}" ]] || continue
    value="${line#*=}"
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    result="${value}"
  done <"${file}"
  printf '%s' "${result}"
}

require_exact_value() {
  local key="$1" expected="$2"
  if [[ "$(key_count "${BACKEND_ENV}" "${key}")" -ne 1 ]]; then
    echo "${BACKEND_ENV} must contain exactly one ${key}" >&2
    exit 1
  fi
  if [[ "$(env_value "${BACKEND_ENV}" "${key}")" != "${expected}" ]]; then
    echo "Unexpected ${key} in ${BACKEND_ENV}" >&2
    exit 1
  fi
}

set_env_value() {
  local key="$1" value="$2" tmp
  tmp="$(mktemp "${BACKEND_ENV}.new.XXXXXX")"
  awk -v wanted_key="${key}" -v wanted_value="${value}" '
    index($0, wanted_key "=") == 1 { print wanted_key "=" wanted_value; next }
    { print }
  ' "${BACKEND_ENV}" >"${tmp}"
  chmod --reference="${BACKEND_ENV}" "${tmp}" 2>/dev/null || chmod 0600 "${tmp}"
  chown --reference="${BACKEND_ENV}" "${tmp}" 2>/dev/null || true
  mv -f "${tmp}" "${BACKEND_ENV}"
}

wait_http() {
  local url="$1" label="$2" timeout_seconds="${3:-120}"
  local deadline=$((SECONDS + timeout_seconds))
  until curl -fsS "${url}" >/dev/null; do
    if (( SECONDS >= deadline )); then
      echo "Timed out waiting for ${label}: ${url}" >&2
      return 1
    fi
    sleep 3
  done
}

completion_state_verified() {
  [[ -e "${COMPLETION_STATE_FILE}" ]] || return 1
  if [[ ! -f "${COMPLETION_STATE_FILE}" || -L "${COMPLETION_STATE_FILE}" ]]; then
    echo "Invalid ClickHouse backfill completion state file: ${COMPLETION_STATE_FILE}" >&2
    return 2
  fi
  if [[ "$(key_count "${COMPLETION_STATE_FILE}" TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_STATE_VERSION)" -ne 1 \
    || "$(key_count "${COMPLETION_STATE_FILE}" TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_CUTOVER_ID)" -ne 1 \
    || "$(key_count "${COMPLETION_STATE_FILE}" TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_STATUS)" -ne 1 \
    || "$(env_value "${COMPLETION_STATE_FILE}" TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_STATE_VERSION)" != "1" \
    || "$(env_value "${COMPLETION_STATE_FILE}" TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_CUTOVER_ID)" != "analytics-v1" \
    || "$(env_value "${COMPLETION_STATE_FILE}" TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_STATUS)" != "VERIFIED" ]]; then
    echo "Unrecognized ClickHouse backfill completion state: ${COMPLETION_STATE_FILE}" >&2
    return 2
  fi
}

write_completion_state() {
  local tmp
  mkdir -p "$(dirname "${COMPLETION_STATE_FILE}")"
  tmp="$(mktemp "${COMPLETION_STATE_FILE}.new.XXXXXX")"
  {
    printf 'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_STATE_VERSION=1\n'
    printf 'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_CUTOVER_ID=analytics-v1\n'
    printf 'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_STATUS=VERIFIED\n'
  } >"${tmp}"
  chmod 0600 "${tmp}"
  chown --reference="${BACKEND_ENV}" "${tmp}" 2>/dev/null || true
  mv -f "${tmp}" "${COMPLETION_STATE_FILE}"
}

existing_backend_log_has_verified_completion() {
  [[ -f "${BACKEND_LOG_FILE}" ]] || return 1
  grep -Eq 'ClickHouse 运营回填完成, skipped=(true|false), verified=true,' "${BACKEND_LOG_FILE}"
}

backend_file_terminal_lines_after_start() {
  local current_bytes
  [[ -f "${BACKEND_LOG_FILE}" ]] || return 0
  current_bytes="$(wc -c <"${BACKEND_LOG_FILE}" | tr -d '[:space:]')"
  if [[ "${current_bytes}" =~ ^[0-9]+$ ]] && (( current_bytes + 1 < BACKEND_LOG_START_BYTE )); then
    # 周期滚动或人工轮转后从新文件开头读取，不能继续沿用旧 inode 的字节偏移。
    BACKEND_LOG_START_BYTE=1
  fi
  tail -c "+${BACKEND_LOG_START_BYTE}" "${BACKEND_LOG_FILE}" 2>/dev/null \
    | grep -E 'ClickHouse 运营回填完成,|Application run failed|ClickHouse 回填校验失败|另一节点正在执行 ClickHouse 回填|回填时间窗口非法' \
    || true
}

journal_after_start() {
  if [[ -n "${JOURNAL_CURSOR}" ]]; then
    journalctl -u "${BACKEND_SERVICE}" --after-cursor "${JOURNAL_CURSOR}" \
      --show-cursor --no-pager 2>/dev/null || true
  else
    journalctl -u "${BACKEND_SERVICE}" --since "@${JOURNAL_START_EPOCH}" \
      --show-cursor --no-pager 2>/dev/null || true
  fi
}

wait_for_backfill() {
  local deadline=$((SECONDS + TIMEOUT_SECONDS)) line next_cursor
  local completion_line failure_line progress_line
  while (( SECONDS < deadline )); do
    next_cursor=""
    completion_line=""
    failure_line=""
    progress_line=""
    # 按 journal cursor 增量消费日志，避免逐日汇总产生大量日志后把完成信号挤出固定行数窗口。
    while IFS= read -r line; do
      if [[ "${line}" == '-- cursor: '* ]]; then
        next_cursor="${line#-- cursor: }"
      elif [[ "${line}" == *'ClickHouse 运营回填完成,'* \
        && "${line}" == *'verified=true,'* ]]; then
        completion_line="${line}"
      elif [[ "${line}" == *'ClickHouse 运营回填完成,'* ]]; then
        failure_line="${line}"
      elif [[ "${line}" == *'Application run failed'* \
        || "${line}" == *'ClickHouse 回填校验失败'* \
        || "${line}" == *'另一节点正在执行 ClickHouse 回填'* \
        || "${line}" == *'回填时间窗口非法'* ]]; then
        failure_line="${line}"
      elif [[ "${line}" == *'Analytics hourly rollup rebuilt'* \
        || "${line}" == *'Analytics daily rollup rebuilt'* ]]; then
        progress_line="${line}"
      fi
    done < <(
      journal_after_start
      # 企业旧 systemd 的 journal cursor 可能无法稳定返回新增行；滚动文件是同一 Java 日志的第二可信出口。
      backend_file_terminal_lines_after_start
    )
    if [[ -n "${next_cursor}" ]]; then
      JOURNAL_CURSOR="${next_cursor}"
    fi
    if [[ -n "${progress_line}" ]]; then
      printf 'ClickHouse analytics backfill progress: %s\n' "${progress_line}"
    fi
    if [[ -n "${completion_line}" ]]; then
      printf 'ClickHouse analytics backfill completion observed: %s\n' "${completion_line}"
      return 0
    fi
    if [[ -n "${failure_line}" ]]; then
      printf 'ClickHouse analytics backfill failed during backend startup: %s\n' \
        "${failure_line}" >&2
      return 1
    fi
    sleep 3
  done
  echo "Timed out after ${TIMEOUT_SECONDS}s waiting for ClickHouse analytics backfill" >&2
  return 1
}

restore_after_failure() {
  local original_status=$?
  trap - EXIT INT TERM
  if [[ "${RESTORE_REQUIRED}" -eq 1 ]]; then
    set +e
    echo "Restoring persistent ClickHouse backfill switch after failure" >&2
    if set_env_value TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED false; then
      RESTORE_REQUIRED=0
      systemctl restart "${BACKEND_SERVICE}"
      wait_http "${BACKEND_HEALTH_URL}" "backend health after backfill rollback" 120
      wait_http "${BACKEND_READINESS_URL}" "backend readiness after backfill rollback" 120
    else
      echo "Failed to restore backfill switch; stopping ${BACKEND_SERVICE}" >&2
      systemctl stop "${BACKEND_SERVICE}" || true
    fi
  fi
  exit "${original_status}"
}

trap restore_after_failure EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

[[ -f "${BACKEND_ENV}" ]] || {
  echo "Required file not found: ${BACKEND_ENV}" >&2
  exit 1
}
[[ "${BACKEND_SERVICE}" =~ ^[A-Za-z0-9_.@-]+(\.service)?$ ]] || {
  echo "Invalid backend systemd service name: ${BACKEND_SERVICE}" >&2
  exit 1
}
[[ "${TIMEOUT_SECONDS}" =~ ^[1-9][0-9]*$ ]] || {
  echo "timeout-seconds must be a positive integer" >&2
  exit 1
}
if [[ "$(id -u)" -ne 0 ]]; then
  echo "ClickHouse backfill deployment must run as root" >&2
  exit 1
fi

for command in awk chown chmod curl date dirname grep journalctl mkdir mktemp mv sed sleep systemctl tail tr wc; do
  require_command "${command}"
done

BACKEND_CONFIG_DIR="$(dirname "${BACKEND_ENV}")"
if [[ -z "${BACKEND_LOG_FILE}" ]]; then
  BACKEND_LOG_FILE="$(dirname "${BACKEND_CONFIG_DIR}")/logs/backend.log"
fi
if [[ -z "${COMPLETION_STATE_FILE}" ]]; then
  COMPLETION_STATE_FILE="${BACKEND_CONFIG_DIR}/analytics-clickhouse-backfill.state"
fi
[[ "${BACKEND_LOG_FILE}" == /* && "${COMPLETION_STATE_FILE}" == /* ]] || {
  echo "Backend log and ClickHouse completion state paths must be absolute" >&2
  exit 1
}

require_exact_value TEST_AGENT_ANALYTICS_CLICKHOUSE_ENABLED true
require_exact_value TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED false
require_exact_value TEST_AGENT_ANALYTICS_CLICKHOUSE_CLEANUP_LEGACY_ROLLUPS false
if [[ "$(key_count "${BACKEND_ENV}" TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_START)" -ne 1 \
  || -z "$(env_value "${BACKEND_ENV}" TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_START)" ]]; then
  echo "${BACKEND_ENV} must contain one non-empty TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_START" >&2
  exit 1
fi

completion_state_status=0
if completion_state_verified; then
  completion_state_status=0
else
  completion_state_status=$?
fi
if [[ "${completion_state_status}" -eq 2 ]]; then
  exit 1
fi
if [[ "${completion_state_status}" -eq 1 ]] && existing_backend_log_has_verified_completion; then
  # 兼容已经完成 analytics-v1、但旧部署脚本尚未落本机状态文件的企业节点。
  write_completion_state
  completion_state_status=0
  echo "Adopted existing verified ClickHouse analytics backfill completion from backend log"
fi
if [[ "${completion_state_status}" -eq 0 ]]; then
  wait_http "${BACKEND_HEALTH_URL}" "backend health with verified analytics backfill" 120
  wait_http "${BACKEND_READINESS_URL}" "backend readiness with verified analytics backfill" 120
  trap - EXIT INT TERM
  echo "ClickHouse analytics backfill already verified; skip runner and backend restart"
  exit 0
fi

JOURNAL_CURSOR="$(journalctl -u "${BACKEND_SERVICE}" -n 0 --show-cursor --no-pager 2>/dev/null \
  | sed -n 's/^-- cursor: //p' | tail -n 1)"
JOURNAL_START_EPOCH="$(date +%s)"
if [[ -f "${BACKEND_LOG_FILE}" ]]; then
  backend_log_bytes="$(wc -c <"${BACKEND_LOG_FILE}" | tr -d '[:space:]')"
  [[ "${backend_log_bytes}" =~ ^[0-9]+$ ]] || {
    echo "Unable to determine backend log size: ${BACKEND_LOG_FILE}" >&2
    exit 1
  }
  BACKEND_LOG_START_BYTE=$((backend_log_bytes + 1))
fi

echo "Starting idempotent ClickHouse analytics backfill through ${BACKEND_SERVICE}"
RESTORE_REQUIRED=1
set_env_value TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED true
systemctl restart "${BACKEND_SERVICE}"
wait_for_backfill

# 当前 Java 进程只在启动时读取一次开关；落盘恢复 false 后无需为关闭开关再次中断服务。
set_env_value TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED false
write_completion_state
wait_http "${BACKEND_HEALTH_URL}" "backend health after analytics backfill" 120
wait_http "${BACKEND_READINESS_URL}" "backend readiness after analytics backfill" 120
RESTORE_REQUIRED=0
trap - EXIT INT TERM

echo "ClickHouse analytics backfill verified; persistent backfill switch restored to false"
