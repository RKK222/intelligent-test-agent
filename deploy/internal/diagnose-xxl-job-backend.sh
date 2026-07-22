#!/usr/bin/env bash
set -uo pipefail

EXPECTED_HOST=''
MINUTES=15
BACKEND_ENV="${TEST_AGENT_DIAG_BACKEND_ENV:-/data/testagent/config/backend.env}"
PS_BIN="${TEST_AGENT_DIAG_PS_BIN:-ps}"
STATUS_MARKER='__TEST_AGENT_HTTP_STATUS__:'
has_failure=0

usage() {
  printf '%s\n' 'Usage: diagnose-xxl-job-backend.sh --expected-host <122.233.30.4|122.233.30.114> [--minutes <5-120>]'
}

usage_error() {
  printf '[FAIL] %s\n' "$1" >&2
  usage >&2
  exit 2
}

while (( $# > 0 )); do
  case "$1" in
    --expected-host) [[ $# -ge 2 ]] || usage_error '--expected-host 缺少值'; EXPECTED_HOST="$2"; shift 2 ;;
    --minutes) [[ $# -ge 2 ]] || usage_error '--minutes 缺少值'; MINUTES="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage_error "未知参数：$1" ;;
  esac
done
[[ "${EXPECTED_HOST}" == '122.233.30.4' || "${EXPECTED_HOST}" == '122.233.30.114' ]] || usage_error '非法 expected host'
[[ "${MINUTES}" =~ ^[0-9]+$ ]] && (( MINUTES >= 5 && MINUTES <= 120 )) || usage_error '--minutes 必须为 5～120'

pass() {
  printf '[PASS] %s\n' "$1"
}

warn() {
  printf '[WARN] %s\n' "$1"
}

info() {
  printf '[INFO] %s\n' "$1"
}

# 诊断应尽量收集完整现场，因此普通健康或配置异常只累积失败状态。
fail() {
  printf '[FAIL] %s\n' "$1"
  has_failure=1
}

critical() {
  printf '[FAIL] %s\n' "$1" >&2
  exit 2
}

# dotenv 仅作为文本读取，不执行变量展开、命令替换或其它 shell 语法。
env_value() {
  local file="$1" wanted_key="$2" line key value result=''
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

sha256_text() {
  if command -v sha256sum >/dev/null 2>&1; then
    printf '%s' "$1" | sha256sum | awk '{print substr($1, 1, 16)}'
  elif command -v shasum >/dev/null 2>&1; then
    printf '%s' "$1" | shasum -a 256 | awk '{print substr($1, 1, 16)}'
  else
    return 1
  fi
}

secret_summary() {
  local key="$1" value digest
  value="$(env_value "${BACKEND_ENV}" "${key}")"
  if [[ -z "${value}" ]]; then
    info "${key}=UNSET"
    return
  fi
  digest="$(sha256_text "${value}")" || critical '缺少可用的 SHA-256 实现，无法安全生成配置摘要'
  info "${key}=SET length=${#value} sha256=${digest}"
}

# JDBC 摘要只保留协议、主机、端口、库名，丢弃 userinfo、query 和 fragment。
sanitize_jdbc_url() {
  local value="$1" base prefix remainder
  base="${value%%\?*}"
  base="${base%%#*}"
  if [[ "${base}" == *'://'* ]]; then
    prefix="${base%%//*}//"
    remainder="${base#*//}"
    [[ "${remainder}" != *@* ]] || remainder="${remainder#*@}"
    printf '%s%s' "${prefix}" "${remainder}"
  else
    printf '%s' "${base}"
  fi
}

jdbc_host_port() {
  local value="$1" default_port="$2" remainder authority host port
  remainder="${value#*://}"
  authority="${remainder%%/*}"
  host="${authority%%:*}"
  port="${authority##*:}"
  [[ "${port}" != "${authority}" ]] || port="${default_port}"
  printf '%s\n%s' "${host}" "${port}"
}

property_value() {
  local text="$1" wanted="$2" line result=''
  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ "${line}" == "${wanted}="* ]] || continue
    result="${line#*=}"
  done <<<"${text}"
  printf '%s' "${result}"
}

redact_stream() {
  sed -E \
    -e 's/Authorization[[:space:]]*:[[:space:]]*Bearer[[:space:]]+[^,;[:space:]"}]+/Authorization=[REDACTED]/Ig' \
    -e 's@(https?://[^[:space:]?"#]+|/[^[:space:]?"#]+)\?[^[:space:]"]*@\1?[REDACTED_QUERY]@g' \
    -e 's@(https?://[^[:space:]?"#]+|/[^[:space:]?"#]+)#[^[:space:]"]*@\1#[REDACTED_FRAGMENT]@g' \
    -e 's@(((jdbc:)?[[:alpha:]][[:alnum:]+.-]*://)[^?[:space:]]+)\?[^[:space:]]+@\1?[REDACTED_QUERY]@g' \
    -e 's|((jdbc:)?[[:alpha:]][[:alnum:]+.-]*://)[^/@[:space:]]+@|\1[REDACTED_USERINFO]@|g' \
    -e 's/((ticket|cookie|token|password|secret|authorization|digest|api[_-]?key)[[:space:]]*[=:][[:space:]]*)[^,;[:space:]"}]+/\1[REDACTED]/Ig' \
    -e 's/("(ticket|cookie|token|password|secret|authorization|digest|api[_-]?key)"[[:space:]]*:[[:space:]]*")[^"]*/\1[REDACTED]/Ig'
}

http_get() {
  curl --silent --show-error --connect-timeout 3 --max-time 8 \
    --write-out "\n${STATUS_MARKER}%{http_code}" "$1" 2>&1
}

probe_readiness() {
  local label="$1" url="$2" response status body
  if ! response="$(http_get "${url}")"; then
    fail "${label} 连接失败"
    return
  fi
  status="${response##*${STATUS_MARKER}}"
  body="${response%$'\n'${STATUS_MARKER}*}"
  if [[ "${status}" == '200' ]] && grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"${body}"; then
    pass "${label} is UP"
  else
    fail "${label} 返回 HTTP ${status:-000} 或非 UP 响应"
  fi
}

# nc 不可用时仍给 /dev/tcp 子进程设置独立看门狗，避免网络探测无限阻塞。
tcp_fallback() {
  local host="$1" port="$2" probe_pid watchdog_pid status
  bash -c 'exec 3<>"/dev/tcp/${1}/${2}"' _ "${host}" "${port}" >/dev/null 2>&1 &
  probe_pid=$!
  ( sleep 3; kill "${probe_pid}" >/dev/null 2>&1 ) &
  watchdog_pid=$!
  wait "${probe_pid}"
  status=$?
  kill "${watchdog_pid}" >/dev/null 2>&1 || true
  wait "${watchdog_pid}" >/dev/null 2>&1 || true
  return "${status}"
}

tcp_probe() {
  local label="$1" host="$2" port="$3"
  if [[ -z "${host}" || ! "${port}" =~ ^[0-9]+$ ]]; then
    fail "${label} endpoint 无法解析"
    return
  fi
  if command -v nc >/dev/null 2>&1; then
    if nc -z -w 3 "${host}" "${port}" >/dev/null 2>&1; then
      pass "${label} ${host}:${port} TCP 可达"
    else
      fail "${label} ${host}:${port} TCP 不可达"
    fi
  elif tcp_fallback "${host}" "${port}"; then
    pass "${label} ${host}:${port} TCP 可达（Bash /dev/tcp fallback）"
  else
    fail "${label} ${host}:${port} TCP 不可达（Bash /dev/tcp fallback）"
  fi
}

check_listener() {
  local port="$1" label="$2" lines pids pid mismatched=0
  if ! lines="$(grep -E ":${port}[[:space:]]" <<<"${SS_OUTPUT}")"; then
    fail "${label} ${port} 未监听"
    return
  fi
  pids="$(grep -Eo 'pid=[0-9]+' <<<"${lines}" | cut -d= -f2 | sort -u)"
  if [[ -z "${pids}" ]]; then
    warn "${label} ${port} 正在监听，但当前权限看不到 PID"
    return
  fi
  while IFS= read -r pid; do
    [[ "${pid}" == "${MAIN_PID}" ]] || mismatched=1
  done <<<"${pids}"
  if [[ "${mismatched}" -eq 0 ]]; then
    pass "${label} ${port} 由 systemd MainPID=${MAIN_PID} 监听"
  else
    fail "${label} ${port} 的监听 PID 与 systemd MainPID=${MAIN_PID} 不一致"
  fi
}

for required_command in ip systemctl ss curl journalctl grep sed awk cut sort tr; do
  command -v "${required_command}" >/dev/null 2>&1 || critical "缺少 ${required_command}，无法完成后台诊断"
done
command -v "${PS_BIN}" >/dev/null 2>&1 || critical '缺少 ps，无法完成后台诊断'
if ! printf 'diagnostic-filter-probe\n' | grep -Eq '^diagnostic-filter-probe$'; then
  critical 'grep 不可用，无法执行日志关键词过滤'
fi
if ! REDACTION_PROBE="$(printf 'Authorization: Bearer diagnostic-secret\n' | redact_stream)" || \
  [[ "${REDACTION_PROBE}" != 'Authorization=[REDACTED]' ]]; then
  critical 'sed 不可用，无法执行日志脱敏'
fi
SHA256_PROBE="$(sha256_text 'diagnostic-sha256-probe' 2>/dev/null)" || critical '缺少可用的 SHA-256 实现'
[[ "${SHA256_PROBE}" =~ ^[0-9a-f]{16}$ ]] || critical '缺少可用的 SHA-256 实现'
[[ -r "${BACKEND_ENV}" ]] || critical "backend.env 不存在或不可读: ${BACKEND_ENV}"

if ip -4 -o addr show scope global | grep -Fq "${EXPECTED_HOST}/"; then
  pass "当前机器包含 expected host ${EXPECTED_HOST}"
else
  critical "当前机器不包含 expected host ${EXPECTED_HOST}"
fi

PROFILE="$(env_value "${BACKEND_ENV}" SPRING_PROFILES_ACTIVE)"
DEPLOYMENT_MODE="$(env_value "${BACKEND_ENV}" TEST_AGENT_DEPLOYMENT_MODE)"
ADVERTISED_HOST="$(env_value "${BACKEND_ENV}" TEST_AGENT_SERVER_ADVERTISED_HOST)"
LINUX_SERVER_ID="$(env_value "${BACKEND_ENV}" TEST_AGENT_LINUX_SERVER_ID)"
DB_URL="$(sanitize_jdbc_url "$(env_value "${BACKEND_ENV}" TEST_AGENT_DB_URL)")"
DB_USERNAME="$(env_value "${BACKEND_ENV}" TEST_AGENT_DB_USERNAME)"
REDIS_HOST="$(env_value "${BACKEND_ENV}" TEST_AGENT_REDIS_HOST)"
REDIS_PORT="$(env_value "${BACKEND_ENV}" TEST_AGENT_REDIS_PORT)"
XXL_ENABLED="$(env_value "${BACKEND_ENV}" TEST_AGENT_XXL_JOB_ENABLED)"
XXL_MYSQL_URL="$(sanitize_jdbc_url "$(env_value "${BACKEND_ENV}" TEST_AGENT_XXL_JOB_MYSQL_URL)")"
XXL_MYSQL_USERNAME="$(env_value "${BACKEND_ENV}" TEST_AGENT_XXL_JOB_MYSQL_USERNAME)"
ADMIN_PORT="$(env_value "${BACKEND_ENV}" TEST_AGENT_XXL_JOB_ADMIN_PORT)"
EXECUTOR_PORT="$(env_value "${BACKEND_ENV}" TEST_AGENT_XXL_JOB_EXECUTOR_PORT)"
XXL_MYSQL_HOST_PORT="$(jdbc_host_port "${XXL_MYSQL_URL}" 3306)"
XXL_MYSQL_HOST="${XXL_MYSQL_HOST_PORT%%$'\n'*}"
XXL_MYSQL_PORT="${XXL_MYSQL_HOST_PORT##*$'\n'}"

info "PROFILE=${PROFILE:-UNSET}"
info "DEPLOYMENT_MODE=${DEPLOYMENT_MODE:-UNSET}"
info "ADVERTISED_HOST=${ADVERTISED_HOST:-UNSET}"
info "LINUX_SERVER_ID=${LINUX_SERVER_ID:-UNSET}"
info "DB_ENDPOINT=${DB_URL:-UNSET}"
info "DB_USERNAME=${DB_USERNAME:-UNSET}"
info "REDIS_ENDPOINT=${REDIS_HOST:-UNSET}:${REDIS_PORT:-UNSET}"
info "XXL_ENABLED=${XXL_ENABLED:-UNSET}"
info "XXL_MYSQL_ENDPOINT=${XXL_MYSQL_URL:-UNSET}"
info "XXL_MYSQL_USERNAME=${XXL_MYSQL_USERNAME:-UNSET}"
info "ADMIN_PORT=${ADMIN_PORT:-UNSET}"
info "EXECUTOR_PORT=${EXECUTOR_PORT:-UNSET}"

[[ "${ADVERTISED_HOST}" == "${EXPECTED_HOST}" ]] || fail 'advertised host 与 expected host 不一致'
[[ -n "${LINUX_SERVER_ID}" ]] || fail 'linux server ID 未配置'
[[ "${XXL_ENABLED}" == 'true' ]] || fail 'XXL-JOB 未启用'
[[ "${ADMIN_PORT}" == '18080' ]] || fail 'Admin 端口不是固定值 18080'
[[ "${EXECUTOR_PORT}" == '9999' ]] || fail 'executor 端口不是固定值 9999'
[[ "${REDIS_HOST}" == '122.233.30.20' && "${REDIS_PORT}" == '6379' ]] || \
  fail '固定共享拓扑不一致：Redis 必须为 122.233.30.20:6379'
[[ "${XXL_MYSQL_HOST}" == '122.233.30.148' && "${XXL_MYSQL_PORT}" == '3306' ]] || \
  fail '固定共享拓扑不一致：XXL MySQL 必须为 122.233.30.148:3306'

for secret_key in \
  TEST_AGENT_DB_PASSWORD \
  TEST_AGENT_REDIS_PASSWORD \
  TEST_AGENT_XXL_JOB_MYSQL_PASSWORD \
  TEST_AGENT_XXL_JOB_ACCESS_TOKEN \
  TEST_AGENT_API_TOKEN \
  TEST_AGENT_OPENCODE_MANAGER_TOKEN \
  TEST_AGENT_INTERNAL_PROXY_API_KEY; do
  secret_summary "${secret_key}"
done

DATA_ROOT="${TEST_AGENT_DIAG_DATA_ROOT:-$(env_value "${BACKEND_ENV}" SYS_DATA_ROOT_DIR)}"
[[ -n "${DATA_ROOT}" ]] || critical 'SYS_DATA_ROOT_DIR 未配置'
[[ -r "${DATA_ROOT}/.serverhost" ]] || critical "身份文件不存在或不可读: ${DATA_ROOT}/.serverhost"
[[ -r "${DATA_ROOT}/.serverid" ]] || critical "身份文件不存在或不可读: ${DATA_ROOT}/.serverid"
SERVER_HOST="$(tr -d '\r\n' <"${DATA_ROOT}/.serverhost")"
SERVER_ID="$(tr -d '\r\n' <"${DATA_ROOT}/.serverid")"
[[ "${SERVER_HOST}" == "${EXPECTED_HOST}" ]] || fail '.serverhost 与 expected host 不一致'
[[ "${SERVER_ID}" == "${LINUX_SERVER_ID}" ]] || fail '.serverid 与 backend.env 的 linux server ID 不一致'
if [[ "${SERVER_HOST}" == "${EXPECTED_HOST}" && "${SERVER_ID}" == "${LINUX_SERVER_ID}" ]]; then
  pass '.serverhost/.serverid 与本机 backend.env 一致'
fi

if ! SYSTEMD_OUTPUT="$(systemctl show test-agent-backend \
  --property=LoadState,ActiveState,SubState,MainPID,ExecStart,EnvironmentFiles,ActiveEnterTimestamp \
  --no-pager 2>&1)"; then
  fail 'systemctl 无法读取 test-agent-backend 状态'
  SYSTEMD_OUTPUT=''
fi
LOAD_STATE="$(property_value "${SYSTEMD_OUTPUT}" LoadState)"
ACTIVE_STATE="$(property_value "${SYSTEMD_OUTPUT}" ActiveState)"
SUB_STATE="$(property_value "${SYSTEMD_OUTPUT}" SubState)"
MAIN_PID="$(property_value "${SYSTEMD_OUTPUT}" MainPID)"
ACTIVE_SINCE="$(property_value "${SYSTEMD_OUTPUT}" ActiveEnterTimestamp)"
EXEC_START="$(property_value "${SYSTEMD_OUTPUT}" ExecStart)"
ENVIRONMENT_FILES="$(property_value "${SYSTEMD_OUTPUT}" EnvironmentFiles)"
info "SYSTEMD_LOAD_STATE=${LOAD_STATE:-UNSET}"
info "SYSTEMD_ACTIVE_STATE=${ACTIVE_STATE:-UNSET}"
info "SYSTEMD_SUB_STATE=${SUB_STATE:-UNSET}"
info "SYSTEMD_MAIN_PID=${MAIN_PID:-UNSET}"
info "SYSTEMD_ACTIVE_SINCE=${ACTIVE_SINCE:-UNSET}"
if [[ "${LOAD_STATE}" == 'loaded' && "${ACTIVE_STATE}" == 'active' && "${SUB_STATE}" == 'running' && "${MAIN_PID}" =~ ^[1-9][0-9]*$ ]]; then
  pass "systemd test-agent-backend is active/running with MainPID=${MAIN_PID}"
else
  fail 'systemd test-agent-backend 未处于 loaded/active/running 或 MainPID 无效'
fi
if [[ "${EXEC_START}" == *'/data/testagent/dist/backend/test-agent-app.jar'* ]]; then
  pass 'systemd ExecStart 指向固定后台 JAR'
else
  fail 'systemd ExecStart 未指向 /data/testagent/dist/backend/test-agent-app.jar'
fi
if [[ "${ENVIRONMENT_FILES}" == *'/data/testagent/config/backend.env'* ]]; then
  pass 'systemd EnvironmentFiles 包含固定 backend.env'
else
  fail 'systemd EnvironmentFiles 未包含 /data/testagent/config/backend.env'
fi

if ! PS_OUTPUT="$("${PS_BIN}" -eo pid=,args= 2>&1)"; then
  fail 'ps 无法读取 Java 进程状态'
  PS_OUTPUT=''
fi
JAVA_PROCESS_COUNT="$(awk '$2 ~ /(^|\/)java$/ { count++ } END { print count + 0 }' <<<"${PS_OUTPUT}")"
APP_PROCESS_COUNT="$(awk -v jar='/data/testagent/dist/backend/test-agent-app.jar' \
  '$2 ~ /(^|\/)java$/ && index($0, jar) > 0 { count++ } END { print count + 0 }' <<<"${PS_OUTPUT}")"
APP_PROCESS_PID="$(awk -v jar='/data/testagent/dist/backend/test-agent-app.jar' \
  '$2 ~ /(^|\/)java$/ && index($0, jar) > 0 { print $1; exit }' <<<"${PS_OUTPUT}")"
if [[ "${JAVA_PROCESS_COUNT}" == '1' && "${APP_PROCESS_COUNT}" == '1' && "${APP_PROCESS_PID}" == "${MAIN_PID}" ]]; then
  pass "专用 Linux 仅有一个后台 Java，PID 与 MainPID=${MAIN_PID} 一致"
else
  fail "专用 Linux 上的 Java 进程数量不是 1，或后台 JAR PID 与 MainPID=${MAIN_PID:-UNSET} 不一致"
fi

if ! SS_OUTPUT="$(ss -ltnp 2>&1)"; then
  fail 'ss 无法读取 TCP 监听状态'
  SS_OUTPUT=''
fi
check_listener 8080 '平台后端端口'
check_listener 18080 'XXL Admin 端口'
check_listener 9999 'XXL executor 端口'

probe_readiness '127.0.0.1:8080 readiness' 'http://127.0.0.1:8080/actuator/health/readiness'
probe_readiness '127.0.0.1:18080 XXL Admin readiness' 'http://127.0.0.1:18080/xxl-job-admin/actuator/health/readiness'

tcp_probe 'Redis' "${REDIS_HOST}" "${REDIS_PORT}"
tcp_probe 'XXL MySQL' "${XXL_MYSQL_HOST}" "${XXL_MYSQL_PORT}"
if [[ "${EXPECTED_HOST}" == '122.233.30.4' ]]; then
  PEER_HOST='122.233.30.114'
else
  PEER_HOST='122.233.30.4'
fi
tcp_probe '对端平台后端' "${PEER_HOST}" 8080
tcp_probe '对端 XXL Admin' "${PEER_HOST}" 18080
tcp_probe '对端 XXL executor' "${PEER_HOST}" 9999

info "最近 ${MINUTES} 分钟 XXL-JOB/数据库/调度相关日志（已脱敏）"
if RECENT_LOGS="$(journalctl -u test-agent-backend --since "${MINUTES} minutes ago" --no-pager -o short-iso 2>&1)"; then
  FILTERED_LOGS="$(grep -Ei 'xxl-job|XxlJob|Flyway|Hikari|MySQL|Admin.*readiness|ExecutorRegistryThread|registry error|Connection refused|SKIPPED_LOCK_HELD|schedule|trigger|handle' \
    <<<"${RECENT_LOGS}")"
  filter_status=$?
  case "${filter_status}" in
    0)
      REDACTED_LOGS="$(printf '%s\n' "${FILTERED_LOGS}" | redact_stream)" || critical '日志脱敏执行失败'
      [[ -z "${REDACTED_LOGS}" ]] || printf '%s\n' "${REDACTED_LOGS}"
      ;;
    1) info '最近日志中没有命中诊断关键词' ;;
    *) critical '日志关键词过滤执行失败' ;;
  esac
else
  fail 'journalctl 无法读取 test-agent-backend 日志'
fi

info '4096-4115 为 opencode 用户进程端口池，非管理页首要链路；本脚本不执行 Docker/worker/manager 操作'
exit "${has_failure}"
