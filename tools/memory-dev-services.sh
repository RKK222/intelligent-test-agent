#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
COMPOSE_FILE="${ROOT_DIR}/deploy/dev/memory-compose.yml"
DEV_LOG_DIR="${TEST_AGENT_DEV_LOG_DIR:-${ROOT_DIR}/.tmp/dev-services}"
ENV_FILE="${TEST_AGENT_MEMORY_DEV_ENV_FILE:-${DEV_LOG_DIR}/memory/memory-dev.env}"
BACKEND_ENV_FILE="${TEST_AGENT_MEMORY_BACKEND_ENV_FILE:-${DEV_LOG_DIR}/memory/memory-backend.env}"

usage() {
  cat <<'USAGE'
Usage: tools/memory-dev-services.sh <prepare|build|start|stop|restart|status|help>

Manage the opt-in local QA memory data plane. The helper owns only the
test-agent-memory-dev Compose project and never executes dotenv content.

Commands:
  prepare  Create or refresh 0600 runtime files under .tmp/dev-services/memory.
  build    Pull pgvector and build the fixed memory-service/BGE image.
  start    Start pgvector and memory-service without rebuilding, then verify /ready.
  stop     Stop only the two memory containers and retain both named volumes.
  restart  Build, stop and start the memory data plane.
  status   Show Compose status and verify authenticated readiness.

Environment overrides:
  TEST_AGENT_MEMORY_DEV_ENV_FILE       Compose dotenv path.
  TEST_AGENT_MEMORY_BACKEND_ENV_FILE   Java-only dotenv path.
  TEST_AGENT_MEMORY_SERVICE_API_KEY    Existing API key of at least 32 bytes.
  TEST_AGENT_MEMORY_DEV_POSTGRES_PASSWORD
  TEST_AGENT_MEMORY_SERVICE_PORT       Host port; default 18888.
  TEST_AGENT_MEMORY_POSTGRES_PORT      Host port; default 15433.
  TEST_AGENT_MEMORY_BACKEND_PORT       Host Java port; default 8080.
USAGE
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

# 生成文件只包含本脚本写入的简单 KEY=VALUE；读取时仍按数据解析，绝不 source/exec。
read_env_value() {
  local file="$1"
  local requested="$2"
  local line key value
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
    TEST_AGENT_MEMORY_SERVICE_API_KEY
    TEST_AGENT_MEMORY_DEV_POSTGRES_PASSWORD
    TEST_AGENT_MEMORY_SERVICE_PORT
    TEST_AGENT_MEMORY_POSTGRES_PORT
    TEST_AGENT_MEMORY_BACKEND_PORT
    TEST_AGENT_MEMORY_SERVICE_EXTRACTION_GATEWAY_URL
    TEST_AGENT_MEMORY_PGVECTOR_IMAGE
    TEST_AGENT_MEMORY_SERVICE_IMAGE
  )
  for key in "${keys[@]}"; do
    value="$(read_env_value "${ENV_FILE}" "${key}")"
    [[ -n "${value}" ]] && export "${key}=${value}"
  done
}

generate_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
    return
  fi
  require_command python3
  python3 -c 'import secrets; print(secrets.token_hex(32))'
}

validate_simple_value() {
  local name="$1"
  local value="$2"
  [[ "${value}" =~ ^[A-Za-z0-9._~-]+$ ]] || {
    echo "${name} must use URL/dotenv-safe characters only." >&2
    exit 1
  }
}

validate_port() {
  local name="$1"
  local value="$2"
  [[ "${value}" =~ ^[0-9]+$ && "${value}" -ge 1 && "${value}" -le 65535 ]] || {
    echo "${name} must be between 1 and 65535." >&2
    exit 1
  }
}

prepare() {
  local existing_api_key existing_database_password
  local api_key database_password service_port postgres_port backend_port gateway_url
  local pgvector_image service_image compose_tmp backend_tmp

  existing_api_key="$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_API_KEY)"
  existing_database_password="$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_DEV_POSTGRES_PASSWORD)"
  api_key="${TEST_AGENT_MEMORY_SERVICE_API_KEY:-${existing_api_key:-$(generate_secret)}}"
  database_password="${TEST_AGENT_MEMORY_DEV_POSTGRES_PASSWORD:-${existing_database_password:-$(generate_secret)}}"
  service_port="${TEST_AGENT_MEMORY_SERVICE_PORT:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_PORT)}"
  postgres_port="${TEST_AGENT_MEMORY_POSTGRES_PORT:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_POSTGRES_PORT)}"
  backend_port="${TEST_AGENT_MEMORY_BACKEND_PORT:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_BACKEND_PORT)}"
  service_port="${service_port:-18888}"
  postgres_port="${postgres_port:-15433}"
  backend_port="${backend_port:-8080}"
  gateway_url="http://host.docker.internal:${backend_port}/api/internal/platform/model-gateway/v1"
  pgvector_image="${TEST_AGENT_MEMORY_PGVECTOR_IMAGE:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_PGVECTOR_IMAGE)}"
  service_image="${TEST_AGENT_MEMORY_SERVICE_IMAGE:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_IMAGE)}"
  pgvector_image="${pgvector_image:-pgvector/pgvector:0.8.1-pg16}"
  service_image="${service_image:-test-agent-memory-service:dev}"

  if [[ "${#api_key}" -lt 32 ]]; then
    echo "TEST_AGENT_MEMORY_SERVICE_API_KEY must contain at least 32 bytes." >&2
    exit 1
  fi
  validate_simple_value TEST_AGENT_MEMORY_SERVICE_API_KEY "${api_key}"
  validate_simple_value TEST_AGENT_MEMORY_DEV_POSTGRES_PASSWORD "${database_password}"
  validate_port TEST_AGENT_MEMORY_SERVICE_PORT "${service_port}"
  validate_port TEST_AGENT_MEMORY_POSTGRES_PORT "${postgres_port}"
  validate_port TEST_AGENT_MEMORY_BACKEND_PORT "${backend_port}"

  mkdir -p "$(dirname "${ENV_FILE}")" "$(dirname "${BACKEND_ENV_FILE}")"
  umask 077
  compose_tmp="$(mktemp "$(dirname "${ENV_FILE}")/memory-dev.env.XXXXXX")"
  backend_tmp="$(mktemp "$(dirname "${BACKEND_ENV_FILE}")/memory-backend.env.XXXXXX")"
  {
    printf 'TEST_AGENT_MEMORY_SERVICE_API_KEY=%s\n' "${api_key}"
    printf 'TEST_AGENT_MEMORY_DEV_POSTGRES_PASSWORD=%s\n' "${database_password}"
    printf 'TEST_AGENT_MEMORY_SERVICE_PORT=%s\n' "${service_port}"
    printf 'TEST_AGENT_MEMORY_POSTGRES_PORT=%s\n' "${postgres_port}"
    printf 'TEST_AGENT_MEMORY_BACKEND_PORT=%s\n' "${backend_port}"
    printf 'TEST_AGENT_MEMORY_SERVICE_EXTRACTION_GATEWAY_URL=%s\n' "${gateway_url}"
    printf 'TEST_AGENT_MEMORY_PGVECTOR_IMAGE=%s\n' "${pgvector_image}"
    printf 'TEST_AGENT_MEMORY_SERVICE_IMAGE=%s\n' "${service_image}"
  } >"${compose_tmp}"
  {
    printf 'TEST_AGENT_MEMORY_ENABLED=true\n'
    printf 'TEST_AGENT_MEMORY_SERVICE_URL=http://127.0.0.1:%s\n' "${service_port}"
    printf 'TEST_AGENT_MEMORY_SERVICE_API_KEY=%s\n' "${api_key}"
  } >"${backend_tmp}"
  chmod 0600 "${compose_tmp}" "${backend_tmp}"
  mv -f "${compose_tmp}" "${ENV_FILE}"
  mv -f "${backend_tmp}" "${BACKEND_ENV_FILE}"
  echo "Prepared QA memory development settings under $(dirname "${ENV_FILE}") (secrets hidden)."
}

require_docker() {
  require_command docker
  docker compose version >/dev/null 2>&1 || {
    echo "Docker Compose v2 is required for --with-memory." >&2
    exit 1
  }
}

compose() {
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

build() {
  [[ -f "${ENV_FILE}" ]] || prepare
  load_runtime_env
  require_docker
  echo "Preparing pinned pgvector image and fixed Mem0/BGE service image."
  compose pull memory-postgres
  compose build memory-service
}

ready() {
  local service_port api_key body i
  load_runtime_env
  require_command curl
  service_port="${TEST_AGENT_MEMORY_SERVICE_PORT:-18888}"
  api_key="${TEST_AGENT_MEMORY_SERVICE_API_KEY:-}"
  # 冷启动会导入 Torch 并加载 CPU Embedding；繁忙的 Docker Desktop 可能超过
  # 容器普通 health 的启动窗口，因此统一以更严格的鉴权 readiness 等待至 7 分钟。
  for ((i = 1; i <= 210; i++)); do
    # API key 通过 curl stdin config 传入，不出现在进程参数、xtrace 或错误日志。
    body="$(printf 'header = "X-Memory-Service-Key: %s"\n' "${api_key}" \
      | curl -fsS --max-time 5 --config - \
        "http://127.0.0.1:${service_port}/memory-api/v1/ready" 2>/dev/null || true)"
    if [[ "${body}" =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]] \
      && [[ "${body}" =~ \"rawMessageCount\"[[:space:]]*:[[:space:]]*0 ]]; then
      echo "OK memory-service: authenticated readiness UP; rawMessageCount=0."
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for authenticated memory-service readiness." >&2
  compose logs --tail 120 memory-service >&2 || true
  return 1
}

start() {
  [[ -f "${ENV_FILE}" ]] || prepare
  load_runtime_env
  require_docker
  # 不使用 compose --wait：普通 /health 可能在 BGE 尚未完成加载时先变为
  # unhealthy，而下方带鉴权的 readiness 才是平台真实的启动门禁。
  compose up -d --no-build memory-postgres memory-service
  ready
}

stop() {
  if [[ ! -f "${ENV_FILE}" ]]; then
    echo "No QA memory development settings; nothing to stop."
    return
  fi
  load_runtime_env
  require_docker
  # 首次卸载 CPU Embedding 模型时可能超过 Compose 默认的 10 秒窗口；
  # 给服务正常退出的时间，同时仍只停止本项目容器并保留数据卷。
  compose stop --timeout 60 memory-service memory-postgres
}

status() {
  [[ -f "${ENV_FILE}" ]] || {
    echo "QA memory development settings are not prepared." >&2
    exit 1
  }
  load_runtime_env
  require_docker
  compose ps
  ready
}

command="${1:-help}"
case "${command}" in
  prepare) prepare ;;
  build) build ;;
  start) start ;;
  stop) stop ;;
  restart) prepare; build; stop; start ;;
  status) status ;;
  help|--help|-h) usage ;;
  *)
    echo "Unknown command: ${command}" >&2
    usage >&2
    exit 2
    ;;
esac
