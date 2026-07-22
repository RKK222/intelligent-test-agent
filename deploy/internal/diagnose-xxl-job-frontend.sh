#!/usr/bin/env bash
set -uo pipefail

if [[ "$#" -ne 0 ]]; then
  printf '[FAIL] 不接受命令行参数，直接运行脚本即可\n' >&2
  exit 2
fi

NGINX_BIN="${TEST_AGENT_DIAG_NGINX_BIN:-/data/apps/nginx/sbin/nginx}"
NGINX_ENV="${TEST_AGENT_DIAG_NGINX_ENV:-/data/testagent/config/nginx.env}"
ACCESS_LOG="${TEST_AGENT_DIAG_NGINX_ACCESS_LOG:-/data/apps/nginx/logs/access.log}"
ERROR_LOG="${TEST_AGENT_DIAG_NGINX_ERROR_LOG:-/data/apps/nginx/logs/error.log}"
NGINX_PREFIX='/data/apps/nginx/'
NGINX_MAIN_CONF='/data/apps/nginx/conf/nginx.conf'
STATUS_MARKER='__TEST_AGENT_HTTP_STATUS__:'
has_failure=0

pass() {
  printf '[PASS] %s\n' "$1"
}

# 汇总配置和探测异常，确保一次运行可以输出完整的现场证据。
fail() {
  printf '[FAIL] %s\n' "$1"
  has_failure=1
}

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

redact_stream() {
  sed -E \
    -e 's@(https?://[^[:space:]?"#]+|/[^[:space:]?"#]+)\?[^[:space:]"]*@\1?[REDACTED_QUERY]@g' \
    -e 's@(https?://[^[:space:]?"#]+|/[^[:space:]?"#]+)#[^[:space:]"]*@\1#[REDACTED_FRAGMENT]@g' \
    -e 's/((ticket|cookie|token|password|secret|authorization|digest)[[:space:]]*[=:][[:space:]]*)[^,;[:space:]"}]+/\1[REDACTED]/Ig' \
    -e 's/("(ticket|cookie|token|password|secret|authorization|digest)"[[:space:]]*:[[:space:]]*")[^"]*/\1[REDACTED]/Ig'
}

http_get() {
  curl --silent --show-error --connect-timeout 3 --max-time 8 \
    --write-out "\n${STATUS_MARKER}%{http_code}" "$1" 2>&1
}

probe_readiness() {
  local endpoint="$1" response status body
  if ! response="$(http_get "http://${endpoint}/xxl-job-admin/actuator/health/readiness")"; then
    fail "${endpoint} readiness 连接失败"
    return
  fi
  status="${response##*${STATUS_MARKER}}"
  body="${response%$'\n'${STATUS_MARKER}*}"
  if [[ "${status}" == '200' ]] && grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"${body}"; then
    pass "${endpoint} readiness is UP"
  else
    fail "${endpoint} readiness 返回 HTTP ${status:-000} 或非 UP 响应"
  fi
}

command -v ip >/dev/null 2>&1 || { printf '[FAIL] 缺少 ip，无法确认前端机器地址\n' >&2; exit 2; }
command -v curl >/dev/null 2>&1 || { printf '[FAIL] 缺少 curl，无法探测 Admin readiness\n' >&2; exit 2; }
[[ -x "${NGINX_BIN}" ]] || { printf '[FAIL] Nginx binary 不存在或不可执行: %s\n' "${NGINX_BIN}" >&2; exit 2; }
[[ -r "${NGINX_ENV}" ]] || { printf '[FAIL] Nginx env 不存在或不可读: %s\n' "${NGINX_ENV}" >&2; exit 2; }
[[ -r "${ACCESS_LOG}" ]] || { printf '[FAIL] Nginx access log 不存在或不可读: %s\n' "${ACCESS_LOG}" >&2; exit 2; }
[[ -r "${ERROR_LOG}" ]] || { printf '[FAIL] Nginx error log 不存在或不可读: %s\n' "${ERROR_LOG}" >&2; exit 2; }

if ip -4 -o addr show scope global | grep -Fq '122.233.30.2/'; then
  pass '当前机器是 122.233.30.2'
else
  printf '[FAIL] 当前机器不是 122.233.30.2\n' >&2
  exit 2
fi

nginx_mode="$(env_value "${NGINX_ENV}" TEST_AGENT_NGINX_MODE)"
nginx_admins="$(env_value "${NGINX_ENV}" TEST_AGENT_NGINX_XXL_JOB_ADMINS)"
nginx_listen_port="$(env_value "${NGINX_ENV}" TEST_AGENT_NGINX_LISTEN_PORT)"
nginx_additional_listen_ports="$(env_value "${NGINX_ENV}" TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS)"

[[ "${nginx_mode}" == 'multi' ]] || fail 'nginx.env 的 TEST_AGENT_NGINX_MODE 不是 multi'
[[ "${nginx_admins}" == '122.233.30.4:18080,122.233.30.114:18080' ]] || fail 'nginx.env 的 XXL Admin 节点与双后台目标不一致'
[[ "${nginx_listen_port}" == '80' ]] || fail 'nginx.env 的主监听端口不是 80'
[[ "${nginx_additional_listen_ports}" == '9996' ]] || fail 'nginx.env 的附加监听端口不是 9996'
if [[ "${has_failure}" -eq 0 ]]; then
  pass 'nginx.env 的多节点 Admin 与监听端口配置正确'
fi

if ! effective_config="$("${NGINX_BIN}" -p "${NGINX_PREFIX}" -c "${NGINX_MAIN_CONF}" -T 2>&1)"; then
  fail 'Nginx -T 无法读取有效配置'
else
  grep -Fq 'upstream test_agent_xxl_job_admin {' <<<"${effective_config}" || fail 'Nginx effective configuration missing XXL Admin upstream'
  grep -Fq 'server 122.233.30.4:18080 max_fails=3 fail_timeout=10s;' <<<"${effective_config}" || fail 'Nginx effective configuration missing XXL Admin server 122.233.30.4:18080'
  grep -Fq 'server 122.233.30.114:18080 max_fails=3 fail_timeout=10s;' <<<"${effective_config}" || fail 'Nginx effective configuration missing XXL Admin server 122.233.30.114:18080'
  grep -Fq 'location /xxl-job-admin/ {' <<<"${effective_config}" || fail 'Nginx effective configuration missing /xxl-job-admin/ location'
  grep -Fq 'proxy_pass http://test_agent_xxl_job_admin;' <<<"${effective_config}" || fail 'Nginx effective configuration missing XXL Admin proxy_pass'
  [[ "${has_failure}" -eq 0 ]] && pass 'Nginx effective configuration contains XXL Admin upstream'
fi

probe_readiness '122.233.30.4:18080'
probe_readiness '122.233.30.114:18080'

printf '[INFO] 最近 200 行 Nginx XXL Admin 相关日志（已脱敏）\n'
{ tail -n 200 "${ACCESS_LOG}"; tail -n 200 "${ERROR_LOG}"; } \
  | grep -Ei 'xxl-job-admin|upstream|connect\(\) failed|timed out|no live upstreams|502|504' \
  | redact_stream || true

exit "${has_failure}"
