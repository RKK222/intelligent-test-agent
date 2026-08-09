#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
COMPOSE_FILE="${ROOT_DIR}/deploy/dev/memory-compose.yml"
DEV_LOG_DIR="${TEST_AGENT_DEV_LOG_DIR:-${ROOT_DIR}/.tmp/dev-services}"
ENV_FILE="${TEST_AGENT_MEMORY_DEV_ENV_FILE:-${DEV_LOG_DIR}/memory/memory-dev.env}"
BACKEND_ENV_FILE="${TEST_AGENT_MEMORY_BACKEND_ENV_FILE:-${DEV_LOG_DIR}/memory/memory-backend.env}"
MEMORY_REPLICAS=(memory-service-1 memory-service-2 memory-service-3)

usage() {
  cat <<'USAGE'
Usage: tools/memory-dev-services.sh <prepare|build|start|stop|restart|status|help>

Manage the opt-in local generic-memory data plane. It contains an independent
pgvector database, a CPU BGE service, three stateless Mem0 replicas and an
Nginx VIP. The helper owns only the test-agent-memory-dev Compose project.

Commands:
  prepare  Create or refresh 0600 runtime files under .tmp/dev-services/memory.
  build    Pull pinned infrastructure and build separate Mem0 and CPU images.
  start    Start in dependency order, run Alembic, then verify authenticated readiness.
  stop     Stop only this Compose project's services and retain the PostgreSQL volume.
  restart  Build, stop and start the complete memory data plane.
  status   Show Compose status and verify PostgreSQL plus both authenticated HTTP readiness endpoints.

Environment overrides:
  TEST_AGENT_MEMORY_DEV_ENV_FILE
  TEST_AGENT_MEMORY_BACKEND_ENV_FILE
  TEST_AGENT_MEMORY_SERVICE_API_KEY
  TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET
  TEST_AGENT_EMBEDDING_API_KEY
  TEST_AGENT_MEMORY_DEV_POSTGRES_PASSWORD
  TEST_AGENT_MEMORY_SERVICE_PORT       Mem0 VIP host port; default 18888.
  TEST_AGENT_EMBEDDING_SERVICE_PORT    CPU BGE host port; default 18989.
  TEST_AGENT_MEMORY_POSTGRES_PORT      Memory PostgreSQL host port; default 15433.
  TEST_AGENT_MEMORY_POSTGRES_VOLUME    Dedicated versioned Docker volume name.
  TEST_AGENT_MEMORY_BACKEND_PORT       Java model-gateway host port; default 8080.
  TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_MODEL_ID
  TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_DIMENSION
  TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_FINGERPRINT
                                      Optional enterprise profile; configure all three.
USAGE
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

# 生成文件只包含本脚本写入的简单 KEY=VALUE；读取时按数据解析，绝不 source/exec。
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
    TEST_AGENT_MEMORY_SERVICE_API_KEY
    TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET
    TEST_AGENT_EMBEDDING_API_KEY
    TEST_AGENT_MEMORY_DEV_POSTGRES_PASSWORD
    TEST_AGENT_MEMORY_SERVICE_PORT
    TEST_AGENT_EMBEDDING_SERVICE_PORT
    TEST_AGENT_MEMORY_POSTGRES_PORT
    TEST_AGENT_MEMORY_POSTGRES_VOLUME
    TEST_AGENT_MEMORY_BACKEND_PORT
    TEST_AGENT_MEMORY_SERVICE_MODEL_GATEWAY_URL
    TEST_AGENT_MEMORY_CHAT_MODEL_ID
    TEST_AGENT_MEMORY_CPU_EMBEDDING_MODEL_ID
    TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_MODEL_ID
    TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_DIMENSION
    TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_FINGERPRINT
    TEST_AGENT_MEMORY_PGVECTOR_IMAGE
    TEST_AGENT_MEMORY_NGINX_IMAGE
    TEST_AGENT_MEMORY_SERVICE_IMAGE
    TEST_AGENT_EMBEDDING_SERVICE_IMAGE
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
  local name="$1" value="$2"
  [[ "${value}" =~ ^[A-Za-z0-9._~:/@-]+$ ]] || {
    echo "${name} contains unsupported dotenv characters." >&2
    exit 1
  }
}

validate_port() {
  local name="$1" value="$2"
  [[ "${value}" =~ ^[0-9]+$ && "${value}" -ge 1 && "${value}" -le 65535 ]] || {
    echo "${name} must be between 1 and 65535." >&2
    exit 1
  }
}

existing_or_new_secret() {
  local key="$1" explicit="$2" existing
  existing="$(read_env_value "${ENV_FILE}" "${key}")"
  printf '%s' "${explicit:-${existing:-$(generate_secret)}}"
}

prepare() {
  local api_key hmac_secret embedding_key database_password
  local service_port embedding_port postgres_port postgres_volume backend_port gateway_url
  local enterprise_model enterprise_dimension enterprise_fingerprint
  local pgvector_image nginx_image service_image embedding_image compose_tmp backend_tmp

  api_key="$(existing_or_new_secret TEST_AGENT_MEMORY_SERVICE_API_KEY "${TEST_AGENT_MEMORY_SERVICE_API_KEY:-}")"
  hmac_secret="$(existing_or_new_secret TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET "${TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET:-}")"
  embedding_key="$(existing_or_new_secret TEST_AGENT_EMBEDDING_API_KEY "${TEST_AGENT_EMBEDDING_API_KEY:-}")"
  database_password="$(existing_or_new_secret TEST_AGENT_MEMORY_DEV_POSTGRES_PASSWORD "${TEST_AGENT_MEMORY_DEV_POSTGRES_PASSWORD:-}")"
  service_port="${TEST_AGENT_MEMORY_SERVICE_PORT:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_PORT)}"
  embedding_port="${TEST_AGENT_EMBEDDING_SERVICE_PORT:-$(read_env_value "${ENV_FILE}" TEST_AGENT_EMBEDDING_SERVICE_PORT)}"
  postgres_port="${TEST_AGENT_MEMORY_POSTGRES_PORT:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_POSTGRES_PORT)}"
  postgres_volume="${TEST_AGENT_MEMORY_POSTGRES_VOLUME:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_POSTGRES_VOLUME)}"
  backend_port="${TEST_AGENT_MEMORY_BACKEND_PORT:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_BACKEND_PORT)}"
  enterprise_model="${TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_MODEL_ID:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_MODEL_ID)}"
  enterprise_dimension="${TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_DIMENSION:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_DIMENSION)}"
  enterprise_fingerprint="${TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_FINGERPRINT:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_FINGERPRINT)}"
  service_port="${service_port:-18888}"
  embedding_port="${embedding_port:-18989}"
  postgres_port="${postgres_port:-15433}"
  postgres_volume="${postgres_volume:-test-agent-memory-dev-pgvector-v1}"
  backend_port="${backend_port:-8080}"
  gateway_url="http://host.docker.internal:${backend_port}/api/internal/platform/model-gateway/v1"
  pgvector_image="${TEST_AGENT_MEMORY_PGVECTOR_IMAGE:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_PGVECTOR_IMAGE)}"
  nginx_image="${TEST_AGENT_MEMORY_NGINX_IMAGE:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_NGINX_IMAGE)}"
  service_image="${TEST_AGENT_MEMORY_SERVICE_IMAGE:-$(read_env_value "${ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_IMAGE)}"
  embedding_image="${TEST_AGENT_EMBEDDING_SERVICE_IMAGE:-$(read_env_value "${ENV_FILE}" TEST_AGENT_EMBEDDING_SERVICE_IMAGE)}"
  pgvector_image="${pgvector_image:-pgvector/pgvector:0.8.1-pg16@sha256:33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337}"
  nginx_image="${nginx_image:-nginx:1.27.2-alpine3.20@sha256:d213b2a02ef4e7ec85882e8955343cdd08ab49d6548995ad18623f47017c65ee}"
  service_image="${service_image:-test-agent-memory-service:dev}"
  embedding_image="${embedding_image:-test-agent-embedding-bge-small-zh-v1.5:dev}"

  [[ "${#api_key}" -ge 32 ]] || { echo "Memory service API key must be at least 32 bytes." >&2; exit 1; }
  [[ "${#hmac_secret}" -ge 32 ]] || { echo "Memory HMAC secret must be at least 32 bytes." >&2; exit 1; }
  [[ "${#embedding_key}" -ge 32 ]] || { echo "Embedding API key must be at least 32 bytes." >&2; exit 1; }
  [[ "${#database_password}" -ge 32 ]] || { echo "Memory PostgreSQL password must be at least 32 bytes." >&2; exit 1; }
  validate_simple_value TEST_AGENT_MEMORY_SERVICE_API_KEY "${api_key}"
  validate_simple_value TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET "${hmac_secret}"
  validate_simple_value TEST_AGENT_EMBEDDING_API_KEY "${embedding_key}"
  validate_simple_value TEST_AGENT_MEMORY_DEV_POSTGRES_PASSWORD "${database_password}"
  validate_port TEST_AGENT_MEMORY_SERVICE_PORT "${service_port}"
  validate_port TEST_AGENT_EMBEDDING_SERVICE_PORT "${embedding_port}"
  validate_port TEST_AGENT_MEMORY_POSTGRES_PORT "${postgres_port}"
  validate_simple_value TEST_AGENT_MEMORY_POSTGRES_VOLUME "${postgres_volume}"
  validate_port TEST_AGENT_MEMORY_BACKEND_PORT "${backend_port}"
  if [[ -n "${enterprise_model}" || -n "${enterprise_dimension}" || -n "${enterprise_fingerprint}" ]]; then
    [[ -n "${enterprise_model}" && -n "${enterprise_dimension}" && -n "${enterprise_fingerprint}" ]] || {
      echo "Enterprise embedding model ID, dimension and fingerprint must be configured together." >&2
      exit 1
    }
    [[ "${enterprise_model}" =~ ^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$ ]] || {
      echo "Enterprise embedding model ID is invalid." >&2
      exit 1
    }
    [[ "${enterprise_dimension}" =~ ^[0-9]+$ && "${enterprise_dimension}" -ge 1 && "${enterprise_dimension}" -le 65535 ]] || {
      echo "Enterprise embedding dimension must be between 1 and 65535." >&2
      exit 1
    }
    [[ "${enterprise_fingerprint}" =~ ^[A-Za-z0-9._:-]{8,128}$ ]] || {
      echo "Enterprise embedding fingerprint is invalid." >&2
      exit 1
    }
  fi

  mkdir -p "$(dirname "${ENV_FILE}")" "$(dirname "${BACKEND_ENV_FILE}")"
  umask 077
  compose_tmp="$(mktemp "$(dirname "${ENV_FILE}")/memory-dev.env.XXXXXX")"
  backend_tmp="$(mktemp "$(dirname "${BACKEND_ENV_FILE}")/memory-backend.env.XXXXXX")"
  {
    printf 'TEST_AGENT_MEMORY_SERVICE_API_KEY=%s\n' "${api_key}"
    printf 'TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET=%s\n' "${hmac_secret}"
    printf 'TEST_AGENT_EMBEDDING_API_KEY=%s\n' "${embedding_key}"
    printf 'TEST_AGENT_MEMORY_DEV_POSTGRES_PASSWORD=%s\n' "${database_password}"
    printf 'TEST_AGENT_MEMORY_SERVICE_PORT=%s\n' "${service_port}"
    printf 'TEST_AGENT_EMBEDDING_SERVICE_PORT=%s\n' "${embedding_port}"
    printf 'TEST_AGENT_MEMORY_POSTGRES_PORT=%s\n' "${postgres_port}"
    printf 'TEST_AGENT_MEMORY_POSTGRES_VOLUME=%s\n' "${postgres_volume}"
    printf 'TEST_AGENT_MEMORY_BACKEND_PORT=%s\n' "${backend_port}"
    printf 'TEST_AGENT_MEMORY_SERVICE_MODEL_GATEWAY_URL=%s\n' "${gateway_url}"
    printf 'TEST_AGENT_MEMORY_CHAT_MODEL_ID=%s\n' "${TEST_AGENT_MEMORY_CHAT_MODEL_ID:-memory-chat}"
    printf 'TEST_AGENT_MEMORY_CPU_EMBEDDING_MODEL_ID=%s\n' "${TEST_AGENT_MEMORY_CPU_EMBEDDING_MODEL_ID:-memory-bge-small-zh-v1.5}"
    printf 'TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_MODEL_ID=%s\n' "${enterprise_model}"
    printf 'TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_DIMENSION=%s\n' "${enterprise_dimension}"
    printf 'TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_FINGERPRINT=%s\n' "${enterprise_fingerprint}"
    printf 'TEST_AGENT_MEMORY_PGVECTOR_IMAGE=%s\n' "${pgvector_image}"
    printf 'TEST_AGENT_MEMORY_NGINX_IMAGE=%s\n' "${nginx_image}"
    printf 'TEST_AGENT_MEMORY_SERVICE_IMAGE=%s\n' "${service_image}"
    printf 'TEST_AGENT_EMBEDDING_SERVICE_IMAGE=%s\n' "${embedding_image}"
  } >"${compose_tmp}"
  {
    printf 'TEST_AGENT_MEMORY_ENABLED=true\n'
    printf 'TEST_AGENT_MEMORY_SERVICE_URL=http://127.0.0.1:%s\n' "${service_port}"
    printf 'TEST_AGENT_MEMORY_SERVICE_API_KEY=%s\n' "${api_key}"
    printf 'TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_CLIENT_ID=mem0-cluster\n'
    printf 'TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET=%s\n' "${hmac_secret}"
  } >"${backend_tmp}"
  chmod 0600 "${compose_tmp}" "${backend_tmp}"
  mv -f "${compose_tmp}" "${ENV_FILE}"
  mv -f "${backend_tmp}" "${BACKEND_ENV_FILE}"
  echo "Prepared generic-memory development settings under $(dirname "${ENV_FILE}") (secrets hidden)."
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
  echo "Preparing pinned pgvector/Nginx and separate Mem0/CPU BGE images."
  compose pull memory-postgres memory-lb
  compose build memory-migrate memory-service-1 embedding-service
}

embedding_ready() {
  local port api_key body i
  load_runtime_env
  require_command curl
  port="${TEST_AGENT_EMBEDDING_SERVICE_PORT:-18989}"
  api_key="${TEST_AGENT_EMBEDDING_API_KEY:-}"
  for ((i = 1; i <= 210; i++)); do
    body="$(printf 'header = "Authorization: Bearer %s"\n' "${api_key}" \
      | curl -fsS --max-time 5 --config - "http://127.0.0.1:${port}/ready" 2>/dev/null || true)"
    if [[ "${body}" =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]]; then
      echo "OK embedding-service: fixed CPU BGE readiness UP."
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for authenticated CPU embedding readiness." >&2
  compose logs --tail 120 embedding-service >&2 || true
  return 1
}

postgres_ready() {
  local body i
  load_runtime_env
  for ((i = 1; i <= 60; i++)); do
    body="$(compose exec -T memory-postgres sh -ec '
      PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 \
        -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc "select 1"
    ' 2>/dev/null || true)"
    if [[ "${body}" == 1 ]]; then
      echo "OK memory-postgres: authenticated role and database readiness UP."
      return 0
    fi
    sleep 2
  done
  echo "Memory PostgreSQL authentication did not become ready; preserve incompatible old volumes and use the configured versioned volume." >&2
  compose logs --tail 120 memory-postgres >&2 || true
  return 1
}

memory_ready() {
  local port api_key body i
  load_runtime_env
  require_command curl
  port="${TEST_AGENT_MEMORY_SERVICE_PORT:-18888}"
  api_key="${TEST_AGENT_MEMORY_SERVICE_API_KEY:-}"
  for ((i = 1; i <= 90; i++)); do
    body="$(printf 'header = "X-Memory-Service-Key: %s"\n' "${api_key}" \
      | curl -fsS --max-time 5 --config - "http://127.0.0.1:${port}/ready" 2>/dev/null || true)"
    if [[ "${body}" =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]] \
      && [[ "${body}" =~ \"rawMessageCount\"[[:space:]]*:[[:space:]]*0 ]]; then
      echo "OK memory VIP: three stateless replicas share PostgreSQL; rawMessageCount=0."
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for authenticated memory VIP readiness." >&2
  compose logs --tail 120 memory-lb "${MEMORY_REPLICAS[@]}" >&2 || true
  return 1
}

ready() {
  postgres_ready
  embedding_ready
  memory_ready
}

start() {
  [[ -f "${ENV_FILE}" ]] || prepare
  load_runtime_env
  require_docker
  compose up -d --no-build memory-postgres
  postgres_ready
  compose up -d --no-build embedding-service
  embedding_ready
  compose up --no-build --abort-on-container-exit --exit-code-from memory-migrate memory-migrate
  compose up -d --no-build "${MEMORY_REPLICAS[@]}" memory-lb
  memory_ready
}

stop() {
  if [[ ! -f "${ENV_FILE}" ]]; then
    echo "No generic-memory development settings; nothing to stop."
    return
  fi
  load_runtime_env
  require_docker
  compose stop --timeout 60 memory-lb "${MEMORY_REPLICAS[@]}" embedding-service memory-postgres
}

status() {
  [[ -f "${ENV_FILE}" ]] || {
    echo "Generic-memory development settings are not prepared." >&2
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
