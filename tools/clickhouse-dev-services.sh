#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
DEV_LOG_DIR="${TEST_AGENT_DEV_LOG_DIR:-${ROOT_DIR}/.tmp/dev-services}"
ENV_FILE="${TEST_AGENT_CLICKHOUSE_DEV_ENV_FILE:-${DEV_LOG_DIR}/clickhouse/clickhouse-dev.env}"
BACKEND_ENV_FILE="${TEST_AGENT_CLICKHOUSE_BACKEND_ENV_FILE:-${DEV_LOG_DIR}/clickhouse/clickhouse-backend.env}"
USERS_CONFIG_FILE="${TEST_AGENT_CLICKHOUSE_USERS_CONFIG_FILE:-${DEV_LOG_DIR}/clickhouse/clickhouse-users.xml}"
CLICKHOUSE_VERSION="26.3.17.56"

usage() {
  cat <<'USAGE'
Usage: tools/clickhouse-dev-services.sh <prepare|pull|start|stop|restart|status|help>

Manage the opt-in local ClickHouse data plane used by operational analytics.
The helper owns only the test-agent-clickhouse-dev container and its versioned
Docker volume. Generated secrets stay under .tmp/dev-services/clickhouse.

Commands:
  prepare  Create or refresh 0600 runtime and backend dotenv files.
  pull     Ensure the pinned ClickHouse server image is available locally.
  start    Recreate the owned container, preserve its volume and verify readiness.
  stop     Stop the owned container and preserve its volume.
  restart  Prepare, ensure the image and start the complete local ClickHouse data plane.
  status   Show container status and verify authenticated HTTP access.

Environment overrides:
  TEST_AGENT_CLICKHOUSE_DEV_ENV_FILE
  TEST_AGENT_CLICKHOUSE_BACKEND_ENV_FILE
  TEST_AGENT_CLICKHOUSE_USERS_CONFIG_FILE
  TEST_AGENT_CLICKHOUSE_DEV_IMAGE       Default: clickhouse/clickhouse-server:26.3.17.56.
  TEST_AGENT_CLICKHOUSE_DEV_CONTAINER   Default: test-agent-clickhouse-dev.
  TEST_AGENT_CLICKHOUSE_DEV_PORT        Default: 18123.
  TEST_AGENT_CLICKHOUSE_DEV_VOLUME      Default: test-agent-clickhouse-dev-data-v1.
  TEST_AGENT_CLICKHOUSE_DEV_DATABASE    Default: testagent_analytics.
  TEST_AGENT_CLICKHOUSE_DEV_USERNAME    Default: testagent_analytics.
  TEST_AGENT_CLICKHOUSE_DEV_PASSWORD
USAGE
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

# 运行文件由本脚本生成，只按 KEY=VALUE 数据读取，禁止 source 或执行其中内容。
read_env_value() {
  local file="$1" requested="$2" line key value
  [[ -f "${file}" ]] || return 0
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* || "${line}" != *=* ]] && continue
    key="${line%%=*}"
    [[ "${key}" == "${requested}" ]] || continue
    value="${line#*=}"
    printf '%s' "${value}"
    return 0
  done <"${file}"
}

load_runtime_env() {
  local key value
  local keys=(
    TEST_AGENT_CLICKHOUSE_DEV_IMAGE
    TEST_AGENT_CLICKHOUSE_DEV_CONTAINER
    TEST_AGENT_CLICKHOUSE_DEV_PORT
    TEST_AGENT_CLICKHOUSE_DEV_VOLUME
    TEST_AGENT_CLICKHOUSE_DEV_DATABASE
    TEST_AGENT_CLICKHOUSE_DEV_USERNAME
    TEST_AGENT_CLICKHOUSE_DEV_PASSWORD
  )
  for key in "${keys[@]}"; do
    value="$(read_env_value "${ENV_FILE}" "${key}")"
    [[ -n "${value}" ]] && export "${key}=${value}"
  done
}

generate_secret() {
  require_command openssl
  openssl rand -hex 32
}

existing_or_new_secret() {
  local existing
  existing="$(read_env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_DEV_PASSWORD)"
  printf '%s' "${TEST_AGENT_CLICKHOUSE_DEV_PASSWORD:-${existing:-$(generate_secret)}}"
}

validate_identifier() {
  local name="$1" value="$2"
  [[ "${value}" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ ]] || {
    echo "${name} must be a simple ClickHouse identifier." >&2
    exit 1
  }
}

validate_simple_value() {
  local name="$1" value="$2"
  [[ "${value}" =~ ^[A-Za-z0-9._:/@-]+$ ]] || {
    echo "${name} contains unsupported dotenv characters." >&2
    exit 1
  }
}

validate_port() {
  local value="$1"
  [[ "${value}" =~ ^[0-9]+$ && "${value}" -ge 1 && "${value}" -le 65535 ]] || {
    echo "TEST_AGENT_CLICKHOUSE_DEV_PORT must be between 1 and 65535." >&2
    exit 1
  }
}

prepare() {
  local image container port volume database username password password_sha256
  local env_tmp backend_tmp users_tmp

  image="${TEST_AGENT_CLICKHOUSE_DEV_IMAGE:-$(read_env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_DEV_IMAGE)}"
  container="${TEST_AGENT_CLICKHOUSE_DEV_CONTAINER:-$(read_env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_DEV_CONTAINER)}"
  port="${TEST_AGENT_CLICKHOUSE_DEV_PORT:-$(read_env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_DEV_PORT)}"
  volume="${TEST_AGENT_CLICKHOUSE_DEV_VOLUME:-$(read_env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_DEV_VOLUME)}"
  database="${TEST_AGENT_CLICKHOUSE_DEV_DATABASE:-$(read_env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_DEV_DATABASE)}"
  username="${TEST_AGENT_CLICKHOUSE_DEV_USERNAME:-$(read_env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_DEV_USERNAME)}"
  password="$(existing_or_new_secret)"

  image="${image:-clickhouse/clickhouse-server:${CLICKHOUSE_VERSION}}"
  container="${container:-test-agent-clickhouse-dev}"
  port="${port:-18123}"
  volume="${volume:-test-agent-clickhouse-dev-data-v1}"
  database="${database:-testagent_analytics}"
  username="${username:-testagent_analytics}"

  [[ "${image}" == "clickhouse/clickhouse-server:${CLICKHOUSE_VERSION}" ]] || {
    echo "Local ClickHouse must use clickhouse/clickhouse-server:${CLICKHOUSE_VERSION}." >&2
    exit 1
  }
  validate_simple_value TEST_AGENT_CLICKHOUSE_DEV_CONTAINER "${container}"
  validate_port "${port}"
  validate_simple_value TEST_AGENT_CLICKHOUSE_DEV_VOLUME "${volume}"
  validate_identifier TEST_AGENT_CLICKHOUSE_DEV_DATABASE "${database}"
  validate_identifier TEST_AGENT_CLICKHOUSE_DEV_USERNAME "${username}"
  [[ "${#password}" -ge 32 ]] || {
    echo "Local ClickHouse password must contain at least 32 characters." >&2
    exit 1
  }
  validate_simple_value TEST_AGENT_CLICKHOUSE_DEV_PASSWORD "${password}"
  password_sha256="$(printf '%s' "${password}" | openssl dgst -sha256 | awk '{print $NF}')"

  mkdir -p "$(dirname "${ENV_FILE}")" "$(dirname "${BACKEND_ENV_FILE}")" "$(dirname "${USERS_CONFIG_FILE}")"
  umask 077
  env_tmp="$(mktemp "$(dirname "${ENV_FILE}")/clickhouse-dev.env.XXXXXX")"
  backend_tmp="$(mktemp "$(dirname "${BACKEND_ENV_FILE}")/clickhouse-backend.env.XXXXXX")"
  users_tmp="$(mktemp "$(dirname "${USERS_CONFIG_FILE}")/clickhouse-users.xml.XXXXXX")"
  {
    printf 'TEST_AGENT_CLICKHOUSE_DEV_IMAGE=%s\n' "${image}"
    printf 'TEST_AGENT_CLICKHOUSE_DEV_CONTAINER=%s\n' "${container}"
    printf 'TEST_AGENT_CLICKHOUSE_DEV_PORT=%s\n' "${port}"
    printf 'TEST_AGENT_CLICKHOUSE_DEV_VOLUME=%s\n' "${volume}"
    printf 'TEST_AGENT_CLICKHOUSE_DEV_DATABASE=%s\n' "${database}"
    printf 'TEST_AGENT_CLICKHOUSE_DEV_USERNAME=%s\n' "${username}"
    printf 'TEST_AGENT_CLICKHOUSE_DEV_PASSWORD=%s\n' "${password}"
  } >"${env_tmp}"
  {
    printf 'TEST_AGENT_ANALYTICS_CLICKHOUSE_ENABLED=true\n'
    printf 'TEST_AGENT_ANALYTICS_CLICKHOUSE_URL=jdbc:clickhouse://127.0.0.1:%s/%s\n' "${port}" "${database}"
    printf 'TEST_AGENT_ANALYTICS_CLICKHOUSE_USERNAME=%s\n' "${username}"
    printf 'TEST_AGENT_ANALYTICS_CLICKHOUSE_PASSWORD=%s\n' "${password}"
    printf 'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=false\n'
    printf 'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_START=\n'
    printf 'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_END=\n'
    printf 'TEST_AGENT_ANALYTICS_CLICKHOUSE_CLEANUP_LEGACY_ROLLUPS=false\n'
  } >"${backend_tmp}"
  # 容器端口只绑定 loopback，因此本地开发用户可允许 Docker 网桥来源，外部网络仍不可访问。
  cat >"${users_tmp}" <<EOF
<clickhouse>
    <users>
        <${username}>
            <password_sha256_hex>${password_sha256}</password_sha256_hex>
            <networks><ip>0.0.0.0/0</ip><ip>::/0</ip></networks>
            <profile>default</profile><quota>default</quota><access_management>0</access_management>
        </${username}>
    </users>
</clickhouse>
EOF
  chmod 0600 "${env_tmp}" "${backend_tmp}" "${users_tmp}"
  mv -f "${env_tmp}" "${ENV_FILE}"
  mv -f "${backend_tmp}" "${BACKEND_ENV_FILE}"
  mv -f "${users_tmp}" "${USERS_CONFIG_FILE}"
  echo "Prepared local ClickHouse settings under $(dirname "${ENV_FILE}") (secrets hidden)."
}

require_docker() {
  require_command docker
  docker info >/dev/null 2>&1 || {
    echo "Docker is required for --with-clickhouse." >&2
    exit 1
  }
}

pull_image() {
  [[ -f "${ENV_FILE}" ]] || prepare
  load_runtime_env
  require_docker
  # 本地已存在固定镜像时无需再次访问外部 registry；start 仍会重建并核验容器。
  if docker image inspect "${TEST_AGENT_CLICKHOUSE_DEV_IMAGE}" >/dev/null 2>&1; then
    echo "Using existing ClickHouse ${CLICKHOUSE_VERSION} development image."
    return
  fi
  docker pull "${TEST_AGENT_CLICKHOUSE_DEV_IMAGE}" >/dev/null
  echo "Pulled ClickHouse ${CLICKHOUSE_VERSION} development image."
}

ready() {
  local container port database username password body attempt
  load_runtime_env
  require_command curl
  container="${TEST_AGENT_CLICKHOUSE_DEV_CONTAINER}"
  port="${TEST_AGENT_CLICKHOUSE_DEV_PORT}"
  database="${TEST_AGENT_CLICKHOUSE_DEV_DATABASE}"
  username="${TEST_AGENT_CLICKHOUSE_DEV_USERNAME}"
  password="${TEST_AGENT_CLICKHOUSE_DEV_PASSWORD}"
  for ((attempt = 1; attempt <= 90; attempt++)); do
    body="$(printf 'user = "%s:%s"\n' "${username}" "${password}" \
      | curl --fail --silent --show-error --max-time 5 --config - \
        --data-binary 'select version(), currentDatabase()' \
        "http://127.0.0.1:${port}/?database=${database}" 2>/dev/null || true)"
    if [[ "${body}" == *"${CLICKHOUSE_VERSION}"* && "${body}" == *"${database}"* ]]; then
      echo "OK ClickHouse: version=${CLICKHOUSE_VERSION}, database=${database}, HTTP port=${port}."
      return 0
    fi
    sleep 1
  done
  echo "Timed out waiting for authenticated ClickHouse readiness." >&2
  docker logs --tail 120 "${container}" >&2 || true
  return 1
}

start() {
  local image container port volume database
  [[ -f "${ENV_FILE}" && -f "${USERS_CONFIG_FILE}" ]] || prepare
  load_runtime_env
  require_docker
  image="${TEST_AGENT_CLICKHOUSE_DEV_IMAGE}"
  container="${TEST_AGENT_CLICKHOUSE_DEV_CONTAINER}"
  port="${TEST_AGENT_CLICKHOUSE_DEV_PORT}"
  volume="${TEST_AGENT_CLICKHOUSE_DEV_VOLUME}"
  database="${TEST_AGENT_CLICKHOUSE_DEV_DATABASE}"
  docker image inspect "${image}" >/dev/null 2>&1 || pull_image

  # 原子更新 users.xml 后必须重建容器才能刷新 bind mount；只删除容器，版本化数据卷始终保留。
  if docker inspect "${container}" >/dev/null 2>&1; then
    docker stop --time 60 "${container}" >/dev/null 2>&1 || true
    docker rm "${container}" >/dev/null
  fi
  docker run -d \
    --name "${container}" \
    --hostname "${container}" \
    --restart unless-stopped \
    --ulimit nofile=262144:262144 \
    -p "127.0.0.1:${port}:8123" \
    -v "${volume}:/var/lib/clickhouse" \
    -v "${USERS_CONFIG_FILE}:/etc/clickhouse-server/users.d/testagent-analytics.xml:ro" \
    "${image}" >/dev/null

  for ((attempt = 1; attempt <= 90; attempt++)); do
    if docker exec "${container}" clickhouse-client --query 'select 1' >/dev/null 2>&1; then
      break
    fi
    [[ "${attempt}" -lt 90 ]] || {
      docker logs --tail 120 "${container}" >&2
      exit 1
    }
    sleep 1
  done
  docker exec "${container}" clickhouse-client \
    --query "create database if not exists ${database}" >/dev/null
  ready
}

stop() {
  [[ -f "${ENV_FILE}" ]] || {
    echo "No local ClickHouse settings; nothing to stop."
    return
  }
  load_runtime_env
  require_docker
  if docker inspect "${TEST_AGENT_CLICKHOUSE_DEV_CONTAINER}" >/dev/null 2>&1; then
    docker stop --time 60 "${TEST_AGENT_CLICKHOUSE_DEV_CONTAINER}" >/dev/null
  fi
  echo "Stopped local ClickHouse; volume ${TEST_AGENT_CLICKHOUSE_DEV_VOLUME} is retained."
}

status() {
  [[ -f "${ENV_FILE}" ]] || {
    echo "Local ClickHouse settings are not prepared." >&2
    exit 1
  }
  load_runtime_env
  require_docker
  docker ps -a --filter "name=^/${TEST_AGENT_CLICKHOUSE_DEV_CONTAINER}$"
  ready
}

command="${1:-help}"
case "${command}" in
  prepare) prepare ;;
  pull) pull_image ;;
  start) start ;;
  stop) stop ;;
  restart) prepare; pull_image; start ;;
  status) status ;;
  help|--help|-h) usage ;;
  *)
    echo "Unknown command: ${command}" >&2
    usage >&2
    exit 2
    ;;
esac
