#!/usr/bin/env bash
set -euo pipefail

# 用法：通过环境变量注入管理凭据与新账号密码，敏感值不会出现在进程参数或输出中。
REDIS_HOST="${TEST_AGENT_REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${TEST_AGENT_REDIS_PORT:-6379}"
REDIS_ADMIN_USER="${TEST_AGENT_REDIS_ADMIN_USER:-default}"
REDIS_ADMIN_PASSWORD="${TEST_AGENT_REDIS_ADMIN_PASSWORD:-}"
WORKFLOW_USER="${TEST_AGENT_WORKFLOW_REDIS_USER:-workflow-auth}"
WORKFLOW_PASSWORD="${TEST_AGENT_WORKFLOW_REDIS_PASSWORD:-}"
REDIS_TLS="${TEST_AGENT_REDIS_TLS:-false}"

[[ "${REDIS_HOST}" =~ ^[A-Za-z0-9.-]+$ ]] || {
  echo "Invalid TEST_AGENT_REDIS_HOST" >&2
  exit 1
}
[[ "${REDIS_PORT}" =~ ^[0-9]{1,5}$ ]] && (( REDIS_PORT >= 1 && REDIS_PORT <= 65535 )) || {
  echo "Invalid TEST_AGENT_REDIS_PORT" >&2
  exit 1
}
[[ "${WORKFLOW_USER}" =~ ^[A-Za-z0-9._-]{3,64}$ ]] || {
  echo "Invalid TEST_AGENT_WORKFLOW_REDIS_USER" >&2
  exit 1
}
[[ "${REDIS_TLS}" == "true" || "${REDIS_TLS}" == "false" ]] || {
  echo "TEST_AGENT_REDIS_TLS must be true or false" >&2
  exit 1
}
[[ "${#WORKFLOW_PASSWORD}" -ge 32 ]] || {
  echo "TEST_AGENT_WORKFLOW_REDIS_PASSWORD must contain at least 32 characters" >&2
  exit 1
}
command -v redis-cli >/dev/null 2>&1 || {
  echo "redis-cli is required" >&2
  exit 1
}

redis_base=(--no-auth-warning -h "${REDIS_HOST}" -p "${REDIS_PORT}")
[[ "${REDIS_TLS}" == "false" ]] || redis_base+=(--tls)

# redis-cli -x 将stdin作为最后一个ACL参数，避免新密码进入ps命令行。
printf '>%s' "${WORKFLOW_PASSWORD}" \
  | REDISCLI_AUTH="${REDIS_ADMIN_PASSWORD}" redis-cli "${redis_base[@]}" \
      --user "${REDIS_ADMIN_USER}" -x ACL SETUSER "${WORKFLOW_USER}" \
      reset on '~test-agent:token:*' -@all +ping +get +pttl >/dev/null

workflow_redis() {
  REDISCLI_AUTH="${WORKFLOW_PASSWORD}" redis-cli "${redis_base[@]}" \
    --user "${WORKFLOW_USER}" --raw "$@"
}

[[ "$(workflow_redis PING)" == "PONG" ]]
workflow_redis GET test-agent:token:acl-verification >/dev/null
workflow_redis PTTL test-agent:token:acl-verification >/dev/null

if workflow_redis SCAN 0 >/dev/null 2>&1; then
  echo "Workflow Redis ACL unexpectedly permits SCAN" >&2
  exit 1
fi
if workflow_redis KEYS '*' >/dev/null 2>&1; then
  echo "Workflow Redis ACL unexpectedly permits KEYS" >&2
  exit 1
fi
if workflow_redis SET test-agent:token:acl-verification forbidden >/dev/null 2>&1; then
  echo "Workflow Redis ACL unexpectedly permits writes" >&2
  exit 1
fi
if workflow_redis GET unrelated:key >/dev/null 2>&1; then
  echo "Workflow Redis ACL unexpectedly permits another key prefix" >&2
  exit 1
fi

echo "Workflow Redis ACL is limited to PING and exact-prefix GET/PTTL operations."
