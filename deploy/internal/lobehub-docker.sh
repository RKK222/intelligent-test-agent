#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="${TEST_AGENT_BASE_DIR:-/data/testagent}"
ENV_FILE="${LOBEHUB_ENV_FILE:-${BASE_DIR}/config/lobehub.env}"
NETWORK="test-agent-lobehub"
DB_CONTAINER="test-agent-lobehub-db"
RUSTFS_CONTAINER="test-agent-lobehub-rustfs"
APP_CONTAINER="test-agent-lobehub-app"
MC_BIN="${BASE_DIR}/lobehub/bin/mc-linux-amd64"

usage() {
  echo "Usage: $0 {validate-config|check-redis|start-db|migrate|start-rustfs|init-bucket|start-app|start|stop|status}" >&2
}

env_value() {
  local key="$1"
  awk -F= -v wanted="${key}" '$1 == wanted { print substr($0, index($0, "=") + 1); found=1 } END { if (!found) exit 1 }' "${ENV_FILE}"
}

require_env() {
  local key="$1" value
  value="$(env_value "${key}" 2>/dev/null || true)"
  if [[ -z "${value}" || "${value}" == REPLACE_* || "${value}" == *REPLACE_* ]]; then
    echo "Missing secure LobeHub setting in ${ENV_FILE}: ${key}" >&2
    exit 1
  fi
  printf '%s' "${value}"
}

require_exact_env() {
  local key="$1" expected="$2" actual
  actual="$(require_env "${key}")"
  [[ "${actual}" == "${expected}" ]] || {
    echo "Unsafe LobeHub setting in ${ENV_FILE}: ${key} must be ${expected}" >&2
    exit 1
  }
}

require_secret_bytes() {
  local key="$1" minimum="$2" value bytes
  value="$(require_env "${key}")"
  bytes="$(printf '%s' "${value}" | wc -c | tr -d '[:space:]')"
  [[ "${bytes}" -ge "${minimum}" ]] || {
    echo "LobeHub secret ${key} must contain at least ${minimum} bytes" >&2
    exit 1
  }
}

require_digest_image() {
  local key="$1" value expected
  value="$(require_env "${key}")"
  [[ "${value}" =~ ^[A-Za-z0-9._:/-]+@sha256:[0-9a-f]{64}$ ]] || {
    echo "LobeHub image ${key} must be pinned by a lowercase SHA-256 digest" >&2
    exit 1
  }
  if [[ -f "${BASE_DIR}/lobehub/release/release.env" ]]; then
    expected="$(awk -F= -v wanted="${key}" \
      '$1 == wanted { print substr($0, index($0, "=") + 1) }' \
      "${BASE_DIR}/lobehub/release/release.env")"
    [[ -n "${expected}" && "${value}" == "${expected}" ]] || {
      echo "LobeHub image ${key} does not match the installed release manifest" >&2
      exit 1
    }
  fi
}

validate_database_env() {
  require_digest_image LOBEHUB_PARADEDB_IMAGE
  require_env POSTGRES_DB >/dev/null
  require_env POSTGRES_USER >/dev/null
  require_secret_bytes POSTGRES_PASSWORD 16
  require_env DATABASE_URL >/dev/null
}

validate_redis_env() {
  require_env LOBEHUB_REDIS_HOST >/dev/null
  require_env LOBEHUB_REDIS_PORT >/dev/null
  require_env LOBEHUB_REDIS_USERNAME >/dev/null
  require_secret_bytes LOBEHUB_REDIS_PASSWORD 16
  require_env REDIS_URL >/dev/null
  require_exact_env REDIS_PREFIX 'lobehub:app:'
}

validate_rustfs_env() {
  require_digest_image LOBEHUB_RUSTFS_IMAGE
  require_secret_bytes RUSTFS_ACCESS_KEY 16
  require_secret_bytes RUSTFS_SECRET_KEY 32
  require_env LOBEHUB_S3_BUCKET >/dev/null
  require_env S3_ENDPOINT >/dev/null
  require_env S3_BUCKET >/dev/null
  require_env S3_ACCESS_KEY_ID >/dev/null
  require_secret_bytes S3_SECRET_ACCESS_KEY 32
  require_exact_env S3_SET_ACL 0
  require_env MC_HOST_lobehub >/dev/null
}

validate_app_env() {
  local linux_execution approval_file
  require_digest_image LOBEHUB_APP_IMAGE
  validate_database_env
  validate_redis_env
  validate_rustfs_env
  for key in APP_URL NEXTAUTH_URL LOBEHUB_PLATFORM_BASE_URL LOBEHUB_PLATFORM_LAUNCH_URL \
    LOBEHUB_PLATFORM_REDEEM_URL LOBEHUB_PLATFORM_REVOKE_URL \
    LOBEHUB_ENTERPRISE_MODEL_BASE_URL; do
    require_env "${key}" >/dev/null
  done
  require_secret_bytes LOBEHUB_PLATFORM_HMAC_SECRET 32
  require_secret_bytes LOBEHUB_MODEL_GRANT_ENCRYPTION_KEY 32
  require_secret_bytes KEY_VAULTS_SECRET 32
  require_secret_bytes NEXT_AUTH_SECRET 32
  require_exact_env LOBEHUB_SESSION_MAX_AGE_SECONDS 86400
  require_exact_env LOBEHUB_COOKIE_SECURE false
  require_exact_env LOBEHUB_COOKIE_SAME_SITE lax
  require_exact_env LOBEHUB_COOKIE_HOST_ONLY true
  require_exact_env LOBEHUB_ENTERPRISE_PROVIDER_ONLY true
  require_exact_env LOBEHUB_ENTERPRISE_MODEL_GRANT_STORAGE server-encrypted
  for key in LOBEHUB_OFFLINE_MODE LOBEHUB_DISABLE_PUBLIC_SEARCH LOBEHUB_DISABLE_SAAS_CONNECTORS \
    LOBEHUB_DISABLE_BYOK LOBEHUB_DISABLE_TELEMETRY LOBEHUB_DISABLE_UPDATE_CHECK \
    LOBEHUB_DISABLE_RUNTIME_DOWNLOADS LOBEHUB_DISABLE_LOCAL_REGISTRATION \
    LOBEHUB_DISABLE_PASSWORD_LOGIN LOBEHUB_DISABLE_CUSTOM_IDENTITY_PROVIDERS; do
    require_exact_env "${key}" true
  done
  # Windows 执行能力永远不可开启；Linux 开启时必须绑定本机真实逃逸边界验收证据。
  require_exact_env LOBEHUB_WINDOWS_EXECUTION_ENABLED false
  linux_execution="$(require_env LOBEHUB_LINUX_EXECUTION_ENABLED)"
  if [[ "${linux_execution}" == true ]]; then
    approval_file="$(require_env LOBEHUB_LINUX_SANDBOX_APPROVAL_FILE)"
    [[ "${approval_file}" == /* && -f "${approval_file}" && ! -L "${approval_file}" ]] || {
      echo "Linux execution approval must be an absolute regular file" >&2
      exit 1
    }
    [[ "$(stat -c '%a' "${approval_file}" 2>/dev/null || stat -f '%Lp' "${approval_file}")" == 600 ]] || {
      echo "Linux execution approval file must have mode 0600: ${approval_file}" >&2
      exit 1
    }
    [[ "$(stat -c '%u' "${approval_file}" 2>/dev/null || stat -f '%u' "${approval_file}")" == 0 ]] || {
      echo "Linux execution approval file must be owned by root: ${approval_file}" >&2
      exit 1
    }
    grep -Fx 'LINUX_SANDBOX_STATUS=PASSED' "${approval_file}" >/dev/null || {
      echo "Linux execution approval is not PASSED" >&2
      exit 1
    }
    grep -Eq '^LINUX_SANDBOX_TARGET_DISTRIBUTION=.+$' "${approval_file}" || {
      echo "Linux execution approval does not record the target distribution" >&2
      exit 1
    }
    grep -Fx "LINUX_SANDBOX_KERNEL=$(uname -r)" "${approval_file}" >/dev/null || {
      echo "Linux execution approval does not match the running kernel" >&2
      exit 1
    }
  elif [[ "${linux_execution}" != false ]]; then
    echo "LOBEHUB_LINUX_EXECUTION_ENABLED must be true or false" >&2
    exit 1
  fi
}

ensure_env_file() {
  [[ -f "${ENV_FILE}" ]] || { echo "LobeHub env file not found: ${ENV_FILE}" >&2; exit 1; }
  [[ "$(stat -c '%a' "${ENV_FILE}" 2>/dev/null || stat -f '%Lp' "${ENV_FILE}")" == "600" ]] || {
    echo "LobeHub env file must have mode 0600: ${ENV_FILE}" >&2
    exit 1
  }
}

ensure_prerequisites() {
  ensure_env_file
  command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }
  docker network inspect "${NETWORK}" >/dev/null 2>&1 || docker network create "${NETWORK}" >/dev/null
}

check_redis() {
  local host port username password outside_output
  validate_redis_env
  command -v redis-cli >/dev/null 2>&1 || { echo "redis-cli is required for the shared Redis preflight" >&2; exit 1; }
  host="$(require_env LOBEHUB_REDIS_HOST)"
  port="$(require_env LOBEHUB_REDIS_PORT)"
  username="$(require_env LOBEHUB_REDIS_USERNAME)"
  password="$(require_env LOBEHUB_REDIS_PASSWORD)"
  redis_call() {
    REDISCLI_AUTH="${password}" redis-cli --no-auth-warning --user "${username}" \
      -h "${host}" -p "${port}" "$@"
  }
  redis_call PING | grep -Fx PONG >/dev/null
  redis_call SET "lobehub:app:acl-preflight:$$" 1 EX 30 | grep -Fx OK >/dev/null
  redis_call DEL "lobehub:app:acl-preflight:$$" >/dev/null
  redis_call PUBLISH "lobehub:app:acl-preflight" 1 >/dev/null
  outside_output="$(redis_call SET "test-agent:lobehub-acl-preflight:$$" 1 EX 30 2>&1 || true)"
  [[ "${outside_output}" == *NOPERM* ]] || {
    echo "LobeHub Redis ACL unexpectedly permits keys outside lobehub:app:*" >&2
    exit 1
  }
  outside_output="$(redis_call PUBLISH "test-agent:lobehub-acl-preflight" 1 2>&1 || true)"
  [[ "${outside_output}" == *NOPERM* ]] || {
    echo "LobeHub Redis ACL unexpectedly permits channels outside lobehub:app:*" >&2
    exit 1
  }
  outside_output="$(redis_call CONFIG GET '*' 2>&1 || true)"
  [[ "${outside_output}" == *NOPERM* ]] || {
    echo "LobeHub Redis ACL unexpectedly permits CONFIG" >&2
    exit 1
  }
}

start_db() {
  local image server_version
  validate_database_env
  image="$(require_env LOBEHUB_PARADEDB_IMAGE)"
  mkdir -p "${BASE_DIR}/lobehub/paradedb"
  docker rm -f "${DB_CONTAINER}" >/dev/null 2>&1 || true
  docker run -d --name "${DB_CONTAINER}" --restart unless-stopped \
    --network "${NETWORK}" --env-file "${ENV_FILE}" \
    -v "${BASE_DIR}/lobehub/paradedb:/var/lib/postgresql/data" \
    "${image}" >/dev/null
  local attempt
  for attempt in {1..60}; do
    if docker exec "${DB_CONTAINER}" pg_isready -U "$(require_env POSTGRES_USER)" -d "$(require_env POSTGRES_DB)" >/dev/null 2>&1; then
      server_version="$(docker exec "${DB_CONTAINER}" psql \
        -U "$(require_env POSTGRES_USER)" -d "$(require_env POSTGRES_DB)" \
        -Atqc 'show server_version_num')"
      [[ "${server_version}" =~ ^17[0-9]{4}$ ]] || {
        echo "LobeHub database must run PostgreSQL 17, got server_version_num=${server_version}" >&2
        exit 1
      }
      return
    fi
    sleep 1
  done
  echo "ParadeDB did not become ready" >&2
  exit 1
}

migrate() {
  validate_app_env
  docker run --rm --network "${NETWORK}" --env-file "${ENV_FILE}" \
    "$(require_env LOBEHUB_APP_IMAGE)" pnpm db:migrate
}

start_rustfs() {
  validate_rustfs_env
  mkdir -p "${BASE_DIR}/lobehub/rustfs/data" "${BASE_DIR}/lobehub/rustfs/logs"
  # RustFS official container uses UID 10001. Refuse to hide permission failures behind a root container.
  chown -R 10001:10001 "${BASE_DIR}/lobehub/rustfs/data" "${BASE_DIR}/lobehub/rustfs/logs"
  docker rm -f "${RUSTFS_CONTAINER}" >/dev/null 2>&1 || true
  docker run -d --name "${RUSTFS_CONTAINER}" --restart unless-stopped \
    --network "${NETWORK}" --env-file "${ENV_FILE}" \
    -p 127.0.0.1:9000:9000 -p 127.0.0.1:9001:9001 \
    -v "${BASE_DIR}/lobehub/rustfs/data:/data" \
    -v "${BASE_DIR}/lobehub/rustfs/logs:/logs" \
    "$(require_env LOBEHUB_RUSTFS_IMAGE)" /data >/dev/null
}

init_bucket() {
  validate_rustfs_env
  [[ -x "${MC_BIN}" ]] || { echo "Offline mc binary not found: ${MC_BIN}" >&2; exit 1; }
  local attempt
  export MC_HOST_lobehub
  MC_HOST_lobehub="$(require_env MC_HOST_lobehub)"
  for attempt in {1..60}; do
    if "${MC_BIN}" ls lobehub >/dev/null 2>&1; then
      local bucket="lobehub/$(require_env LOBEHUB_S3_BUCKET)"
      "${MC_BIN}" mb --ignore-existing "${bucket}" >/dev/null
      # 即使制品或对象存储默认策略变化，也显式将附件桶恢复为禁止匿名访问。
      "${MC_BIN}" anonymous set none "${bucket}" >/dev/null
      return
    fi
    sleep 1
  done
  echo "RustFS did not become ready for private bucket initialization" >&2
  exit 1
}

start_app() {
  validate_app_env
  docker rm -f "${APP_CONTAINER}" >/dev/null 2>&1 || true
  docker run -d --name "${APP_CONTAINER}" --restart unless-stopped \
    --network "${NETWORK}" --env-file "${ENV_FILE}" \
    -p 127.0.0.1:3210:3210 \
    "$(require_env LOBEHUB_APP_IMAGE)" >/dev/null
}

start_all() {
  check_redis
  start_db
  migrate
  start_rustfs
  init_bucket
  start_app
}

stop_all() {
  docker stop "${APP_CONTAINER}" "${RUSTFS_CONTAINER}" "${DB_CONTAINER}" >/dev/null 2>&1 || true
}

status_all() {
  docker ps --filter "name=test-agent-lobehub" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
}

if [[ "${1:-}" == validate-config ]]; then
  ensure_env_file
  validate_app_env
  echo "LobeHub configuration contract passed"
  exit 0
fi

ensure_prerequisites
case "${1:-}" in
  check-redis) check_redis ;;
  start-db) start_db ;;
  migrate) migrate ;;
  start-rustfs) start_rustfs ;;
  init-bucket) init_bucket ;;
  start-app) start_app ;;
  start) start_all ;;
  stop) stop_all ;;
  status) status_all ;;
  *) usage; exit 2 ;;
esac
