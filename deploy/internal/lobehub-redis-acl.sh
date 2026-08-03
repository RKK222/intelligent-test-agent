#!/usr/bin/env bash

# LobeHub 只获得运行期所需的数据命令；禁止用 +@all 继承 Redis 新版本新增的管理命令。
LOBEHUB_REDIS_ACL_COMMANDS=(
  +ping +echo +quit +info '+client|setinfo' '+client|setname'
  +get +set +setex +setnx +getdel +del +unlink +exists +type
  +expire +pexpire +expireat +pexpireat +ttl +pttl +persist
  +incr +incrby +decr +decrby +mget +mset +append
  +hget +hset +hdel +hgetall +hmget +hmset +hexists +hincrby +hlen
  +lpush +rpush +lpop +rpop +lrange +llen +ltrim +blpop +brpop
  +sadd +srem +smembers +scard +sismember
  +zadd +zcard +zrange +zrevrange +zrangebyscore +zrem +zremrangebyscore
  +xadd +xread +xrange +xrevrange +xdel +xlen
  +multi +exec +discard +watch +unwatch
  +eval +evalsha '+script|load' '+script|exists'
  +scan
  +publish +subscribe +unsubscribe +psubscribe +punsubscribe
)

lobehub_redis_acl_fail() {
  printf 'LobeHub Redis ACL configuration failed: %s\n' "$1" >&2
  return 1
}

lobehub_redis_acl_env_value() {
  local env_file="$1" key="$2"
  awk -F= -v wanted="${key}" '
    $1 == wanted { value=substr($0, index($0, "=") + 1); count++ }
    END { if (count != 1 || value == "") exit 1; print value }
  ' "${env_file}"
}

lobehub_redis_acl_apply() {
  local env_file="$1" admin_user="$2" owner_uid mode host port app_user app_password prefix
  [[ -f "${env_file}" && ! -L "${env_file}" ]] ||
    lobehub_redis_acl_fail "env file must be a regular non-symlink file: ${env_file}" || return 1
  mode="$(stat -c '%a' "${env_file}" 2>/dev/null || stat -f '%Lp' "${env_file}")"
  owner_uid="$(stat -c '%u' "${env_file}" 2>/dev/null || stat -f '%u' "${env_file}")"
  [[ "${mode}" == 600 && "${owner_uid}" == "$(id -u)" ]] ||
    lobehub_redis_acl_fail 'env file must be mode 0600 and owned by the invoking account' || return 1
  [[ -n "${REDISCLI_AUTH:-}" ]] ||
    lobehub_redis_acl_fail 'REDISCLI_AUTH must contain the shared Redis administrator password' || return 1
  [[ "${admin_user}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]] ||
    lobehub_redis_acl_fail 'administrator user is malformed' || return 1

  host="$(lobehub_redis_acl_env_value "${env_file}" LOBEHUB_REDIS_HOST)" ||
    lobehub_redis_acl_fail 'LOBEHUB_REDIS_HOST is missing or duplicated' || return 1
  port="$(lobehub_redis_acl_env_value "${env_file}" LOBEHUB_REDIS_PORT)" ||
    lobehub_redis_acl_fail 'LOBEHUB_REDIS_PORT is missing or duplicated' || return 1
  app_user="$(lobehub_redis_acl_env_value "${env_file}" LOBEHUB_REDIS_USERNAME)" ||
    lobehub_redis_acl_fail 'LOBEHUB_REDIS_USERNAME is missing or duplicated' || return 1
  app_password="$(lobehub_redis_acl_env_value "${env_file}" LOBEHUB_REDIS_PASSWORD)" ||
    lobehub_redis_acl_fail 'LOBEHUB_REDIS_PASSWORD is missing or duplicated' || return 1
  prefix="$(lobehub_redis_acl_env_value "${env_file}" REDIS_PREFIX)" ||
    lobehub_redis_acl_fail 'REDIS_PREFIX is missing or duplicated' || return 1

  [[ "${port}" =~ ^[0-9]{1,5}$ && "${port}" -ge 1 && "${port}" -le 65535 ]] ||
    lobehub_redis_acl_fail 'Redis port is invalid' || return 1
  [[ "${app_user}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]] ||
    lobehub_redis_acl_fail 'LobeHub Redis user is malformed' || return 1
  [[ "${prefix}" == 'lobehub:app' ]] ||
    lobehub_redis_acl_fail 'REDIS_PREFIX must be lobehub:app' || return 1
  [[ "$(printf '%s' "${app_password}" | wc -c | tr -d '[:space:]')" -ge 16 ]] ||
    lobehub_redis_acl_fail 'LobeHub Redis password must contain at least 16 bytes' || return 1
  command -v redis-cli >/dev/null 2>&1 ||
    lobehub_redis_acl_fail 'redis-cli is required' || return 1

  # redis-cli -x 将 stdin 作为最后一个参数，避免应用密码出现在进程 argv 中。
  printf '%s' ">${app_password}" | REDISCLI_AUTH="${REDISCLI_AUTH}" \
    redis-cli --no-auth-warning --user "${admin_user}" -h "${host}" -p "${port}" -x \
    ACL SETUSER "${app_user}" reset on \
    '~lobehub:app:*' '&lobehub:app:*' -@all \
    "${LOBEHUB_REDIS_ACL_COMMANDS[@]}" >/dev/null
  printf 'LobeHub Redis ACL applied for user %s.\n' "${app_user}"
}

lobehub_redis_acl_main() {
  local command="${1:-}" env_file="${LOBEHUB_ENV_FILE:-/data/testagent/config/lobehub.env}"
  local admin_user=default
  shift || true
  case "${command}" in
    apply)
      while [[ $# -gt 0 ]]; do
        case "$1" in
          --env-file) env_file="${2:-}"; shift 2 ;;
          --admin-user) admin_user="${2:-}"; shift 2 ;;
          *) lobehub_redis_acl_fail "unknown option: $1"; return 2 ;;
        esac
      done
      lobehub_redis_acl_apply "${env_file}" "${admin_user}"
      ;;
    *)
      printf 'Usage: %s apply [--env-file <lobehub.env>] [--admin-user <redis-admin>]\n' "$0" >&2
      return 2
      ;;
  esac
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -euo pipefail
  lobehub_redis_acl_main "$@"
fi
