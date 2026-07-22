#!/usr/bin/env bash
set -uo pipefail

DOMAIN_HOST='mimo.sdc.cs.icbc'
DOMAIN_BASE='http://mimo.sdc.cs.icbc:9996'
IP_BASE='http://122.233.30.2:9996'
STATUS_MARKER='__TEST_AGENT_HTTP_STATUS__:'
has_failure=0

pass() {
  printf '[PASS] %s\n' "$1"
}

warn() {
  printf '[WARN] %s\n' "$1"
}

# 汇总所有只读探测结果，避免单项失败遮蔽后续入口证据。
fail() {
  printf '[FAIL] %s\n' "$1"
  has_failure=1
}

finish() {
  exit "${has_failure}"
}

http_get() {
  curl --silent --show-error --connect-timeout 3 --max-time 8 \
    --location --max-redirs 0 --write-out "\n${STATUS_MARKER}%{http_code}" "$1" 2>&1
}

probe_page() {
  local label="$1" url="$2" response status body
  if ! response="$(http_get "${url}")"; then
    fail "${label} 连接失败"
    return
  fi
  status="${response##*${STATUS_MARKER}}"
  body="${response%$'\n'${STATUS_MARKER}*}"
  if [[ "${status}" == '200' && -n "${body//[[:space:]]/}" ]]; then
    pass "${label} 返回 HTTP 200"
  elif [[ "${status}" == '502' || "${status}" == '504' ]]; then
    fail "${label} 返回 ${status}，检查企业入口或 Nginx upstream"
  else
    fail "${label} 返回 HTTP ${status:-000} 或空响应"
  fi
}

probe_readiness() {
  local label="$1" url="$2" response status body
  if ! response="$(http_get "${url}")"; then
    fail "${label} 连接失败"
    return
  fi
  status="${response##*${STATUS_MARKER}}"
  body="${response%$'\n'${STATUS_MARKER}*}"
  case "${status}" in
    200)
      if grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"${body}"; then
        pass "${label} 返回 HTTP 200 / UP"
      elif grep -Eqi '<!doctype|<html' <<<"${body}"; then
        fail "${label} 被错误路由到 HTML 页面"
      else
        fail "${label} 返回 HTTP 200，但不是 UP health JSON"
      fi
      ;;
    404) fail "${label} 返回 404，检查 /xxl-job-admin/ location" ;;
    502|504) fail "${label} 返回 ${status}，检查 Nginx Admin upstream" ;;
    *) fail "${label} 返回 HTTP ${status}" ;;
  esac
}

command -v getent >/dev/null 2>&1 || { printf '[FAIL] 缺少 getent，无法执行固定入口诊断\n' >&2; exit 2; }
command -v curl >/dev/null 2>&1 || { printf '[FAIL] 缺少 curl，无法执行固定入口诊断\n' >&2; exit 2; }

if dns_result="$(getent ahostsv4 "${DOMAIN_HOST}" 2>&1)" && grep -Fq '122.233.30.2' <<<"${dns_result}"; then
  pass '域名解析到 122.233.30.2'
else
  fail '域名未解析到 122.233.30.2'
fi
probe_page '域名入口' "${DOMAIN_BASE}/"
probe_page 'IP 入口' "${IP_BASE}/"
probe_readiness '域名同源 Admin readiness' "${DOMAIN_BASE}/xxl-job-admin/actuator/health/readiness"
probe_readiness 'IP 同源 Admin readiness' "${IP_BASE}/xxl-job-admin/actuator/health/readiness"
warn '当前入口使用 HTTP；浏览器可能拒收带 Secure 属性的 XXL Admin Cookie，请在浏览器 Network/Application 中取证'
finish
