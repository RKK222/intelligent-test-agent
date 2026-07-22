#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="/data/testagent/config/mysql.env"
RELEASE_ARCHIVE=""
IMAGE_TAR=""
ACTION="deploy"

usage() {
  cat <<'USAGE'
Usage: deploy-xxl-job-mysql.sh [options] [validate|deploy|verify|stop|status|logs]

Deploy and verify the standalone XXL-JOB MySQL container without network access.

Options:
  --env-file <path>          Prepared mysql.env. Default: /data/testagent/config/mysql.env.
  --release-archive <path>   Full release ZIP containing dist/mysql_8.4-linux-amd64.tar.
  --image-tar <path>         Docker-loadable MySQL image tar; overrides --release-archive.
  -h, --help                 Show this help.

Existing database data is never deleted. MySQL initialization variables only take
effect for an empty data directory; credential drift therefore fails verification.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --release-archive)
      RELEASE_ARCHIVE="$2"
      shift 2
      ;;
    --image-tar)
      IMAGE_TAR="$2"
      shift 2
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

validate_config() {
  local key
  require_file "${ENV_FILE}"
  for key in \
    TEST_AGENT_XXL_JOB_MYSQL_IMAGE \
    TEST_AGENT_XXL_JOB_MYSQL_CONTAINER \
    TEST_AGENT_XXL_JOB_MYSQL_HOST_PORT \
    TEST_AGENT_XXL_JOB_MYSQL_DATA_ROOT \
    TEST_AGENT_XXL_JOB_MYSQL_ROOT_PASSWORD \
    TEST_AGENT_XXL_JOB_MYSQL_DATABASE \
    TEST_AGENT_XXL_JOB_MYSQL_USERNAME \
    TEST_AGENT_XXL_JOB_MYSQL_PASSWORD; do
    require_one_nonempty_key "${key}"
  done
  grep -q 'REPLACE_' "${ENV_FILE}" && {
    echo "Prepared mysql.env still contains a REPLACE_ placeholder" >&2
    exit 1
  }

  MYSQL_IMAGE="$(env_value "${ENV_FILE}" TEST_AGENT_XXL_JOB_MYSQL_IMAGE)"
  CONTAINER_NAME="$(env_value "${ENV_FILE}" TEST_AGENT_XXL_JOB_MYSQL_CONTAINER)"
  HOST_PORT="$(env_value "${ENV_FILE}" TEST_AGENT_XXL_JOB_MYSQL_HOST_PORT)"
  DATA_ROOT="$(env_value "${ENV_FILE}" TEST_AGENT_XXL_JOB_MYSQL_DATA_ROOT)"
  ROOT_PASSWORD="$(env_value "${ENV_FILE}" TEST_AGENT_XXL_JOB_MYSQL_ROOT_PASSWORD)"
  DATABASE="$(env_value "${ENV_FILE}" TEST_AGENT_XXL_JOB_MYSQL_DATABASE)"
  DATABASE_USER="$(env_value "${ENV_FILE}" TEST_AGENT_XXL_JOB_MYSQL_USERNAME)"
  DATABASE_PASSWORD="$(env_value "${ENV_FILE}" TEST_AGENT_XXL_JOB_MYSQL_PASSWORD)"

  [[ "${MYSQL_IMAGE}" =~ ^[A-Za-z0-9._/-]+:[A-Za-z0-9._-]+$ ]] || {
    echo "Invalid MySQL image name" >&2
    exit 1
  }
  [[ "${CONTAINER_NAME}" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]+$ ]] || {
    echo "Invalid MySQL container name" >&2
    exit 1
  }
  [[ "${HOST_PORT}" =~ ^[0-9]{1,5}$ ]] && (( HOST_PORT >= 1 && HOST_PORT <= 65535 )) || {
    echo "Invalid MySQL host port" >&2
    exit 1
  }
  [[ "${DATA_ROOT}" =~ ^/[A-Za-z0-9._/-]+$ ]] || {
    echo "TEST_AGENT_XXL_JOB_MYSQL_DATA_ROOT must be an absolute path" >&2
    exit 1
  }
  [[ "${DATABASE}" =~ ^[A-Za-z0-9_]+$ && "${DATABASE_USER}" =~ ^[A-Za-z0-9_]+$ ]] || {
    echo "Invalid MySQL database or user name" >&2
    exit 1
  }
  (( ${#ROOT_PASSWORD} >= 16 && ${#DATABASE_PASSWORD} >= 16 )) || {
    echo "MySQL root and application passwords must contain at least 16 characters" >&2
    exit 1
  }
}

resolve_image_tar() {
  local archive_entry="dist/mysql_8.4-linux-amd64.tar"
  if [[ -n "${IMAGE_TAR}" ]]; then
    require_file "${IMAGE_TAR}"
    return
  fi
  [[ -n "${RELEASE_ARCHIVE}" ]] || return 0
  require_command unzip
  require_file "${RELEASE_ARCHIVE}"
  unzip -tq "${RELEASE_ARCHIVE}" >/dev/null
  # 不能使用 grep -q：大 release 中匹配后提前退出会让 unzip 收到 SIGPIPE，
  # 在 pipefail 下被误判成镜像条目缺失。重定向普通 grep 会完整消费列表。
  unzip -Z1 "${RELEASE_ARCHIVE}" | grep -Fx "${archive_entry}" >/dev/null || {
    echo "Release archive is missing ${archive_entry}" >&2
    exit 1
  }
  EXTRACT_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-mysql-image.XXXXXX")"
  IMAGE_TAR="${EXTRACT_ROOT}/mysql_8.4-linux-amd64.tar"
  unzip -p "${RELEASE_ARCHIVE}" "${archive_entry}" >"${IMAGE_TAR}"
}

verify_container() {
  local running health architecture
  require_command docker
  running="$(docker inspect -f '{{.State.Running}}' "${CONTAINER_NAME}" 2>/dev/null || true)"
  health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "${CONTAINER_NAME}" 2>/dev/null || true)"
  [[ "${running}" == "true" ]] || {
    echo "MySQL container is not running: ${CONTAINER_NAME}" >&2
    exit 1
  }
  [[ -z "${health}" || "${health}" == "healthy" ]] || {
    echo "MySQL container health is ${health}" >&2
    exit 1
  }
  architecture="$(docker image inspect -f '{{.Architecture}}' "${MYSQL_IMAGE}")"
  [[ "${architecture}" == "amd64" ]] || {
    echo "MySQL image architecture must be amd64, got ${architecture}" >&2
    exit 1
  }
  # 密码只在容器内部从初始化环境读取，不进入命令参数或部署日志。
  docker exec "${CONTAINER_NAME}" sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -Nse "SELECT 1"' \
    | grep -Fxq 1
  printf 'MySQL verification passed: container=%s image=%s host_port=%s database=%s user=%s\n' \
    "${CONTAINER_NAME}" "${MYSQL_IMAGE}" "${HOST_PORT}" "${DATABASE}" "${DATABASE_USER}"
}

wait_until_ready() {
  local attempt health
  for attempt in $(seq 1 120); do
    health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "${CONTAINER_NAME}" 2>/dev/null || true)"
    [[ "${health}" == "healthy" ]] && return 0
    sleep 1
  done
  docker logs --tail 80 "${CONTAINER_NAME}" >&2 || true
  echo "MySQL did not become healthy within 120 seconds" >&2
  exit 1
}

cleanup() {
  [[ -z "${EXTRACT_ROOT:-}" ]] || rm -rf "${EXTRACT_ROOT}"
  [[ -z "${DOCKER_ENV_FILE:-}" ]] || rm -f "${DOCKER_ENV_FILE}"
}
trap cleanup EXIT

validate_config

case "${ACTION}" in
  validate)
    resolve_image_tar
    printf 'MySQL configuration and offline image validation passed\n'
    ;;
  verify)
    verify_container
    ;;
  stop)
    require_command docker
    docker stop "${CONTAINER_NAME}" >/dev/null
    printf 'MySQL container stopped: %s\n' "${CONTAINER_NAME}"
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
    resolve_image_tar
    if ! docker image inspect "${MYSQL_IMAGE}" >/dev/null 2>&1; then
      [[ -n "${IMAGE_TAR}" ]] || {
        echo "MySQL image is absent and no offline image tar or release archive was provided" >&2
        exit 1
      }
      docker load -i "${IMAGE_TAR}" >/dev/null
    fi
    [[ "$(docker image inspect -f '{{.Architecture}}' "${MYSQL_IMAGE}")" == "amd64" ]] || {
      echo "Loaded MySQL image is not linux/amd64" >&2
      exit 1
    }

    install -d -m 0700 "${DATA_ROOT}"
    DOCKER_ENV_FILE="$(mktemp "${TMPDIR:-/tmp}/test-agent-mysql-env.XXXXXX")"
    chmod 0600 "${DOCKER_ENV_FILE}"
    printf '%s\n' \
      "MYSQL_ROOT_PASSWORD=${ROOT_PASSWORD}" \
      "MYSQL_DATABASE=${DATABASE}" \
      "MYSQL_USER=${DATABASE_USER}" \
      "MYSQL_PASSWORD=${DATABASE_PASSWORD}" \
      >"${DOCKER_ENV_FILE}"

    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
    docker run -d \
      --platform linux/amd64 \
      --name "${CONTAINER_NAME}" \
      --hostname "${CONTAINER_NAME}" \
      --restart unless-stopped \
      --env-file "${DOCKER_ENV_FILE}" \
      -p "${HOST_PORT}:3306" \
      -v "${DATA_ROOT}:/var/lib/mysql" \
      --health-cmd 'mysqladmin ping -h 127.0.0.1 --silent' \
      --health-interval 5s \
      --health-timeout 3s \
      --health-retries 24 \
      "${MYSQL_IMAGE}" \
      --character-set-server=utf8mb4 \
      --collation-server=utf8mb4_unicode_ci \
      >/dev/null
    wait_until_ready
    verify_container
    printf 'MySQL deployment completed; existing data was preserved at %s\n' "${DATA_ROOT}"
    ;;
esac
