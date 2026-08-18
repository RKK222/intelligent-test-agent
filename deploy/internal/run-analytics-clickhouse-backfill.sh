#!/usr/bin/env bash
set -euo pipefail

BACKEND_ENV="/data/testagent/config/backend.env"
BACKEND_SERVICE="test-agent-backend"
BACKEND_HEALTH_URL="http://127.0.0.1:8080/actuator/health"
BACKEND_READINESS_URL="http://127.0.0.1:8080/actuator/health/readiness"
TIMEOUT_SECONDS=7200
RESTORE_REQUIRED=0

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
      elif [[ "${line}" == *'ClickHouse 运营回填完成,'* ]]; then
        completion_line="${line}"
      elif [[ "${line}" == *'Application run failed'* \
        || "${line}" == *'ClickHouse 回填校验失败'* \
        || "${line}" == *'另一节点正在执行 ClickHouse 回填'* \
        || "${line}" == *'回填时间窗口非法'* ]]; then
        failure_line="${line}"
      elif [[ "${line}" == *'Analytics hourly rollup rebuilt'* \
        || "${line}" == *'Analytics daily rollup rebuilt'* ]]; then
        progress_line="${line}"
      fi
    done < <(journal_after_start)
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

for command in awk chown chmod curl date grep journalctl mktemp mv sed sleep systemctl tail; do
  require_command "${command}"
done

require_exact_value TEST_AGENT_ANALYTICS_CLICKHOUSE_ENABLED true
require_exact_value TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED false
require_exact_value TEST_AGENT_ANALYTICS_CLICKHOUSE_CLEANUP_LEGACY_ROLLUPS false
if [[ "$(key_count "${BACKEND_ENV}" TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_START)" -ne 1 \
  || -z "$(env_value "${BACKEND_ENV}" TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_START)" ]]; then
  echo "${BACKEND_ENV} must contain one non-empty TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_START" >&2
  exit 1
fi

JOURNAL_CURSOR="$(journalctl -u "${BACKEND_SERVICE}" -n 0 --show-cursor --no-pager 2>/dev/null \
  | sed -n 's/^-- cursor: //p' | tail -n 1)"
JOURNAL_START_EPOCH="$(date +%s)"

echo "Starting idempotent ClickHouse analytics backfill through ${BACKEND_SERVICE}"
RESTORE_REQUIRED=1
set_env_value TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED true
systemctl restart "${BACKEND_SERVICE}"
wait_for_backfill

# 当前 Java 进程只在启动时读取一次开关；落盘恢复 false 后无需为关闭开关再次中断服务。
set_env_value TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED false
wait_http "${BACKEND_HEALTH_URL}" "backend health after analytics backfill" 120
wait_http "${BACKEND_READINESS_URL}" "backend readiness after analytics backfill" 120
RESTORE_REQUIRED=0
trap - EXIT INT TERM

echo "ClickHouse analytics backfill verified; persistent backfill switch restored to false"
