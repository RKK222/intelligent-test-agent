#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="/data/testagent/config/redis.env"
CONFIG_FILE="/data/testagent/config/redis.conf"
IMAGE_TAR=""
ACTION="deploy"
REPLACE_EXISTING=false

usage() {
  cat <<'USAGE'
Usage: deploy-redis.sh [options] [validate|deploy|verify|stop|status|logs]

Deploy and verify the standalone Redis 7.4.9 linux/amd64 container offline.

Options:
  --env-file <path>       Prepared redis.env. Default: /data/testagent/config/redis.env.
  --config-file <path>    Prepared redis.conf. Default: /data/testagent/config/redis.conf.
  --image-tar <path>      Docker-loadable Redis image tar.
  --replace-existing      Explicitly replace an existing container; data is never deleted.
  -h, --help              Show this help.

The script never removes TEST_AGENT_REDIS_DATA_ROOT. Before upgrading Redis 5,
stop both Java backends and make a verified backup of the existing RDB/AOF data.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --config-file)
      CONFIG_FILE="$2"
      shift 2
      ;;
    --image-tar)
      IMAGE_TAR="$2"
      shift 2
      ;;
    --replace-existing)
      REPLACE_EXISTING=true
      shift
      ;;
    validate|deploy|verify|stop|status|logs)
      ACTION="$1"
      shift
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
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

require_file() {
  [[ -f "$1" ]] || {
    echo "Required file not found: $1" >&2
    exit 1
  }
}

# dotenv 只作为文本读取，不能 source，避免现场配置中的 shell 片段被执行。
env_value() {
  local file="$1"
  local wanted_key="$2"
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

require_one_nonempty_key() {
  local key="$1"
  [[ "$(grep -c "^${key}=" "${ENV_FILE}" || true)" -eq 1 ]] || {
    echo "${ENV_FILE} must contain exactly one ${key}" >&2
    exit 1
  }
  [[ -n "$(env_value "${ENV_FILE}" "${key}")" ]] || {
    echo "Required value is empty: ${key}" >&2
    exit 1
  }
}

config_value() {
  local key="$1"
  awk -v wanted="${key}" '$1 == wanted {sub(/^[^[:space:]]+[[:space:]]+/, ""); print; exit}' "${CONFIG_FILE}"
}

validate_config() {
  local key password
  require_file "${ENV_FILE}"
  require_file "${CONFIG_FILE}"
  for key in \
    TEST_AGENT_REDIS_IMAGE \
    TEST_AGENT_REDIS_CONTAINER \
    TEST_AGENT_REDIS_HOST_PORT \
    TEST_AGENT_REDIS_DATA_ROOT; do
    require_one_nonempty_key "${key}"
  done
  grep -q 'REPLACE_' "${ENV_FILE}" "${CONFIG_FILE}" && {
    echo "Redis configuration still contains a REPLACE_ placeholder" >&2
    exit 1
  }

  REDIS_IMAGE="$(env_value "${ENV_FILE}" TEST_AGENT_REDIS_IMAGE)"
  CONTAINER_NAME="$(env_value "${ENV_FILE}" TEST_AGENT_REDIS_CONTAINER)"
  HOST_PORT="$(env_value "${ENV_FILE}" TEST_AGENT_REDIS_HOST_PORT)"
  DATA_ROOT="$(env_value "${ENV_FILE}" TEST_AGENT_REDIS_DATA_ROOT)"
  PASSWORD="$(config_value requirepass)"

  [[ "${REDIS_IMAGE}" == "test-agent-redis:7.4.9-alpine" ]] || {
    echo "Redis image must be test-agent-redis:7.4.9-alpine" >&2
    exit 1
  }
  [[ "${CONTAINER_NAME}" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]+$ ]] || {
    echo "Invalid Redis container name" >&2
    exit 1
  }
  [[ "${HOST_PORT}" =~ ^[0-9]{1,5}$ ]] && (( HOST_PORT >= 1 && HOST_PORT <= 65535 )) || {
    echo "Invalid Redis host port" >&2
    exit 1
  }
  [[ "${DATA_ROOT}" =~ ^/[A-Za-z0-9._/-]+$ ]] || {
    echo "TEST_AGENT_REDIS_DATA_ROOT must be an absolute path" >&2
    exit 1
  }
  (( ${#PASSWORD} >= 32 )) || {
    echo "Redis requirepass must contain at least 32 characters" >&2
    exit 1
  }
  [[ "$(config_value protected-mode)" == "yes" ]] || {
    echo "Redis protected-mode must be yes" >&2
    exit 1
  }
  [[ "$(config_value appendonly)" == "yes" ]] || {
    echo "Redis appendonly must be yes" >&2
    exit 1
  }
  [[ "$(config_value appendfsync)" == "everysec" ]] || {
    echo "Redis appendfsync must be everysec" >&2
    exit 1
  }
  [[ "$(config_value maxmemory-policy)" == "noeviction" ]] || {
    echo "Redis maxmemory-policy must be noeviction" >&2
    exit 1
  }
}

report_container_state() {
  echo "Redis container state (docker ps -a):" >&2
  docker ps -a --filter "name=^/${CONTAINER_NAME}$" \
    --format 'table {{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' >&2 || true
  if docker inspect "${CONTAINER_NAME}" >/dev/null 2>&1; then
    docker inspect -f \
      'container={{.Name}} id={{.Id}} running={{.State.Running}} status={{.State.Status}} exit_code={{.State.ExitCode}} error={{.State.Error}}' \
      "${CONTAINER_NAME}" >&2 || true
    echo "Redis container logs (last 80 lines):" >&2
    docker logs --tail 80 "${CONTAINER_NAME}" >&2 || true
  fi
}

redis_cli() {
  # 密码由容器内只读配置加载，不进入宿主机命令参数或 docker exec 环境参数。
  docker exec "${CONTAINER_NAME}" sh -c '
    password="$(awk '\''$1 == "requirepass" {sub(/^[^[:space:]]+[[:space:]]+/, ""); print; exit}'\'' /usr/local/etc/redis/redis.conf)"
    REDISCLI_AUTH="${password}" redis-cli -h 127.0.0.1 -p 6379 "$@" 2>/dev/null
  ' sh "$@"
}

verify_container() {
  local running health architecture version smoke_key
  require_command docker
  running="$(docker inspect -f '{{.State.Running}}' "${CONTAINER_NAME}" 2>/dev/null || true)"
  health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "${CONTAINER_NAME}" 2>/dev/null || true)"
  [[ "${running}" == "true" ]] || {
    echo "Redis container is not running: ${CONTAINER_NAME}" >&2
    report_container_state
    exit 1
  }
  [[ -z "${health}" || "${health}" == "healthy" ]] || {
    echo "Redis container health is ${health}" >&2
    report_container_state
    exit 1
  }
  architecture="$(docker image inspect -f '{{.Architecture}}' "${REDIS_IMAGE}")"
  [[ "${architecture}" == "amd64" ]] || {
    echo "Redis image architecture must be amd64, got ${architecture}" >&2
    exit 1
  }
  version="$(redis_cli INFO server | awk -F: '$1 == "redis_version" {gsub(/\r/, "", $2); print $2}')"
  [[ "${version}" == "7.4.9" ]] || {
    echo "Redis server version must be 7.4.9, got ${version:-unknown}" >&2
    exit 1
  }

  # 使用唯一临时 key 验证项目依赖的 GETDEL；最后一次读取必须为空。
  smoke_key="test-agent:deploy-smoke:${CONTAINER_NAME}:$$"
  [[ "$(redis_cli SET "${smoke_key}" ready EX 60)" == "OK" ]]
  [[ "$(redis_cli GETDEL "${smoke_key}")" == "ready" ]]
  [[ -z "$(redis_cli GETDEL "${smoke_key}")" ]]
  printf 'Redis verification passed: container=%s image=%s host_port=%s version=%s\n' \
    "${CONTAINER_NAME}" "${REDIS_IMAGE}" "${HOST_PORT}" "${version}"
}

wait_until_ready() {
  local attempt health
  for attempt in $(seq 1 60); do
    health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "${CONTAINER_NAME}" 2>/dev/null || true)"
    [[ "${health}" == "healthy" ]] && return 0
    sleep 1
  done
  report_container_state
  echo "Redis did not become healthy within 60 seconds" >&2
  exit 1
}

total_key_count() {
  redis_cli INFO keyspace | awk -F'[=,]' '/^db[0-9]+:keys=/{sum += $2} END {print sum + 0}'
}

persistence_value() {
  local key="$1"
  redis_cli INFO persistence | awk -F: -v wanted="${key}" \
    '$1 == wanted {gsub(/\r/, "", $2); print $2; exit}'
}

run_redis_container() {
  local appendonly_override="${1:-}"
  # 配置文件含 Redis 密码且宿主机包保持 0600；容器启动时先在容器内复制并交给 redis 用户读取。
  # 不改变宿主机敏感文件权限，也兼容 Linux Docker 的 bind mount 权限检查。
  local -a command=(
    sh -c '
      install -m 0600 /usr/local/etc/redis/redis.conf /tmp/test-agent-redis.conf &&
      chown redis:redis /tmp/test-agent-redis.conf &&
      exec /usr/bin/setpriv --reuid redis --regid redis --clear-groups \
        redis-server /tmp/test-agent-redis.conf "$@"
    ' sh
  )
  if [[ -n "${appendonly_override}" ]]; then
    command+=(--appendonly "${appendonly_override}")
  fi
  # 目标机可能是未启用 experimental features 的旧 Docker；镜像架构已在 deploy 前强校验。
  docker run -d \
    --name "${CONTAINER_NAME}" \
    --hostname "${CONTAINER_NAME}" \
    --restart unless-stopped \
    -p "${HOST_PORT}:6379" \
    -v "${data_volume}" \
    -v "${config_volume}" \
    -v "${health_volume}" \
    --health-cmd /usr/local/bin/redis-healthcheck \
    --health-interval 5s \
    --health-timeout 3s \
    --health-retries 12 \
    "${REDIS_IMAGE}" \
    "${command[@]}"
}

# Redis 7 在 appendonly=yes 且 AOF 尚不存在时不会用旧 dump.rdb 作为迁移源。
# 因此先只读入 RDB，再动态生成 Redis 7 multipart AOF，重启后核对 key 总数。
convert_rdb_to_aof() {
  local before_keys after_keys attempt current_size rewrite_in_progress rewrite_status
  printf 'Existing RDB detected without Redis 7 AOF; converting the copied RDB to AOF...\n'
  run_redis_container no >/dev/null
  wait_until_ready
  before_keys="$(total_key_count)"
  [[ "$(redis_cli CONFIG SET appendonly yes)" == "OK" ]] || {
    echo "Redis could not enable AOF for the copied RDB" >&2
    exit 1
  }
  for attempt in $(seq 1 120); do
    current_size="$(persistence_value aof_current_size)"
    rewrite_in_progress="$(persistence_value aof_rewrite_in_progress)"
    rewrite_status="$(persistence_value aof_last_bgrewrite_status)"
    if [[ "${rewrite_in_progress}" == "0" && "${rewrite_status}" == "ok" && "${current_size:-0}" =~ ^[0-9]+$ ]] \
      && (( current_size > 0 )); then
      break
    fi
    sleep 1
  done
  [[ "${rewrite_in_progress:-}" == "0" && "${rewrite_status:-}" == "ok" && "${current_size:-0}" =~ ^[0-9]+$ ]] \
    && (( current_size > 0 )) || {
      echo "Redis AOF conversion did not complete within 120 seconds" >&2
      report_container_state
      exit 1
    }
  after_keys="$(total_key_count)"
  [[ "${after_keys}" == "${before_keys}" ]] || {
    echo "Redis key count changed during RDB-to-AOF conversion" >&2
    exit 1
  }
  docker rm -f "${CONTAINER_NAME}" >/dev/null
  run_redis_container >/dev/null
  wait_until_ready
  after_keys="$(total_key_count)"
  [[ "${after_keys}" == "${before_keys}" ]] || {
    echo "Redis key count changed after restarting from the generated AOF" >&2
    exit 1
  }
  printf 'RDB-to-AOF conversion passed: keys=%s\n' "${before_keys}"
}

validate_config

case "${ACTION}" in
  validate)
    [[ -z "${IMAGE_TAR}" ]] || require_file "${IMAGE_TAR}"
    printf 'Redis configuration validation passed\n'
    ;;
  verify)
    verify_container
    ;;
  stop)
    require_command docker
    docker stop "${CONTAINER_NAME}" >/dev/null
    printf 'Redis container stopped: %s\n' "${CONTAINER_NAME}"
    ;;
  status)
    require_command docker
    docker ps -a --filter "name=^/${CONTAINER_NAME}$" --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
    ;;
  logs)
    require_command docker
    docker logs --tail 200 "${CONTAINER_NAME}"
    ;;
  deploy)
    require_command docker
    if ! docker image inspect "${REDIS_IMAGE}" >/dev/null 2>&1; then
      [[ -n "${IMAGE_TAR}" ]] || {
        echo "Redis image is absent and --image-tar was not provided" >&2
        exit 1
      }
      require_file "${IMAGE_TAR}"
      docker load -i "${IMAGE_TAR}" >/dev/null
    fi
    [[ "$(docker image inspect -f '{{.Os}}/{{.Architecture}}' "${REDIS_IMAGE}")" == "linux/amd64" ]] || {
      echo "Loaded Redis image is not linux/amd64" >&2
      exit 1
    }

    if docker inspect "${CONTAINER_NAME}" >/dev/null 2>&1; then
      [[ "${REPLACE_EXISTING}" == "true" ]] || {
        echo "Redis container already exists: ${CONTAINER_NAME}; inspect and back it up, then rerun with --replace-existing" >&2
        exit 1
      }
      docker rm -f "${CONTAINER_NAME}" >/dev/null
    fi

    install -d -m 0700 "${DATA_ROOT}"
    data_volume="${DATA_ROOT}:/data"
    config_volume="${CONFIG_FILE}:/usr/local/etc/redis/redis.conf:ro"
    health_volume="${SCRIPT_DIR}/redis-healthcheck.sh:/usr/local/bin/redis-healthcheck:ro"
    if command -v getenforce >/dev/null 2>&1 && [[ "$(getenforce 2>/dev/null || true)" != "Disabled" ]]; then
      data_volume="${data_volume}:Z"
      config_volume="${CONFIG_FILE}:/usr/local/etc/redis/redis.conf:ro,Z"
      health_volume="${SCRIPT_DIR}/redis-healthcheck.sh:/usr/local/bin/redis-healthcheck:ro,Z"
    fi

    printf 'Preparing Redis container: name=%s image=%s host_port=%s data_root=%s\n' \
      "${CONTAINER_NAME}" "${REDIS_IMAGE}" "${HOST_PORT}" "${DATA_ROOT}"
    if [[ -f "${DATA_ROOT}/dump.rdb" && ! -e "${DATA_ROOT}/appendonlydir" ]]; then
      convert_rdb_to_aof
    else
      if ! run_redis_container >/dev/null; then
        echo "docker run failed for Redis container: ${CONTAINER_NAME}" >&2
        report_container_state
        exit 1
      fi
      wait_until_ready
    fi
    verify_container
    ;;
esac
