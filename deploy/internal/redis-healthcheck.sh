#!/usr/bin/env sh
set -eu

CONFIG_FILE="${TEST_AGENT_REDIS_CONFIG_FILE:-/usr/local/etc/redis/redis.conf}"

# 从只读配置读取密码，避免密码出现在 docker inspect 的 healthcheck 参数中。
password="$(awk '$1 == "requirepass" {sub(/^[^[:space:]]+[[:space:]]+/, ""); print; exit}' "${CONFIG_FILE}")"
if [ -z "${password}" ]; then
  echo "Redis requirepass is missing" >&2
  exit 1
fi

REDISCLI_AUTH="${password}" redis-cli -h 127.0.0.1 -p 6379 ping 2>/dev/null | grep -Fxq PONG
