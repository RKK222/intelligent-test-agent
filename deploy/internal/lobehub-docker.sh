#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="${TEST_AGENT_BASE_DIR:-/data/testagent}"
ENV_FILE="${LOBEHUB_ENV_FILE:-${BASE_DIR}/config/lobehub.env}"
NETWORK="test-agent-lobehub"
DB_CONTAINER="test-agent-lobehub-db"
RUSTFS_CONTAINER="test-agent-lobehub-rustfs"
APP_CONTAINER="test-agent-lobehub-app"
MC_BIN="${BASE_DIR}/lobehub/bin/mc-linux-amd64"
PLATFORM_PROBE="${BASE_DIR}/deploy/internal/lobehub-platform-probe.mjs"
TEMP_ENV_FILES=()

cleanup_temp_env_files() {
  local file
  for file in "${TEMP_ENV_FILES[@]-}"; do
    [[ -n "${file}" ]] && rm -f "${file}"
  done
  return 0
}
trap cleanup_temp_env_files EXIT

usage() {
  echo "Usage: $0 {validate-config|check-redis|start-db|migrate|start-rustfs|init-bucket|start-app|start|stop|status|verify-platform|verify-deployment}" >&2
}

env_value() {
  local key="$1"
  awk -F= -v wanted="${key}" '$1 == wanted { print substr($0, index($0, "=") + 1); found=1; exit } END { if (!found) exit 1 }' "${ENV_FILE}"
}

validate_env_file_syntax() {
  local line line_number=0 key seen_keys=$'\n'
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line_number=$((line_number + 1))
    [[ "${line}" != *$'\r'* ]] || {
      echo "LobeHub env contains CR characters at line ${line_number}" >&2
      exit 1
    }
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    [[ "${line}" == *=* ]] || {
      echo "Invalid LobeHub dotenv line ${line_number}" >&2
      exit 1
    }
    key="${line%%=*}"
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || {
      echo "Invalid LobeHub dotenv key at line ${line_number}" >&2
      exit 1
    }
    [[ "${seen_keys}" != *$'\n'"${key}"$'\n'* ]] || {
      echo "Duplicate LobeHub dotenv key: ${key}" >&2
      exit 1
    }
    seen_keys+="${key}"$'\n'
  done <"${ENV_FILE}"
}

create_runtime_env_file() {
  local output_variable="$1" runtime_env key value
  shift
  umask 077
  runtime_env="$(mktemp "${TMPDIR:-/tmp}/test-agent-lobehub-env.XXXXXX")"
  for key in "$@"; do
    value="$(require_env "${key}")"
    printf '%s=%s\n' "${key}" "${value}" >>"${runtime_env}"
  done
  chmod 0600 "${runtime_env}"
  TEMP_ENV_FILES+=("${runtime_env}")
  printf -v "${output_variable}" '%s' "${runtime_env}"
}

create_app_runtime_env_file() {
  local output_variable="$1"
  create_runtime_env_file "${output_variable}" \
    APP_URL INTERNAL_APP_URL AUTH_SECRET KEY_VAULTS_SECRET \
    ENTERPRISE_INTERNAL_SCHEDULER_SECRET DATABASE_URL DATABASE_DRIVER REDIS_URL REDIS_PREFIX \
    S3_ENDPOINT S3_BUCKET S3_ACCESS_KEY_ID S3_SECRET_ACCESS_KEY S3_ENABLE_PATH_STYLE S3_SET_ACL \
    PLATFORM_SSO_ENABLED PLATFORM_LAUNCH_URL PLATFORM_SSO_REDEEM_URL PLATFORM_SSO_REVOKE_URL \
    PLATFORM_MODEL_GATEWAY_BASE_URL PLATFORM_SSO_HMAC_SECRET PLATFORM_MODEL_GRANT_ENCRYPTION_KEY \
    LOBEHUB_ENTERPRISE_OFFLINE AGENT_RUNTIME_MODE TELEMETRY_DISABLED LOBEHUB_DEVICE_EXECUTION_MODE
}

create_platform_probe_env_file() {
  local output_variable="$1"
  # 只把探针必需的目标地址和服务密钥放入临时 0600 env 文件，禁止通过 argv 泄漏。
  create_runtime_env_file "${output_variable}" \
    PLATFORM_SSO_REVOKE_URL PLATFORM_SSO_HMAC_SECRET
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

require_matching_env() {
  local left_key="$1" right_key="$2" left_value right_value
  left_value="$(require_env "${left_key}")"
  right_value="$(require_env "${right_key}")"
  [[ "${left_value}" == "${right_value}" ]] || {
    echo "Cross-wired LobeHub settings in ${ENV_FILE}: ${left_key} must match ${right_key}" >&2
    exit 1
  }
}

url_encode_component() {
  local value="$1" encoded="" char decimal hex index LC_ALL=C
  for ((index = 0; index < ${#value}; index++)); do
    char="${value:index:1}"
    case "${char}" in
      [A-Za-z0-9.~_-]) encoded+="${char}" ;;
      *)
        printf -v decimal '%d' "'${char}"
        if ((decimal < 0)); then
          decimal=$((decimal + 256))
        fi
        printf -v hex '%02X' "${decimal}"
        encoded+="%${hex}"
        ;;
    esac
  done
  printf '%s' "${encoded}"
}

require_url_target() {
  local key="$1" expected_prefix="$2" expected_suffix="$3" expected_credential="$4"
  local value credential
  value="$(require_env "${key}")"
  [[ "${value}" == "${expected_prefix}"* && "${value}" == *"${expected_suffix}" ]] || {
    echo "Cross-wired LobeHub URL in ${ENV_FILE}: ${key} must target the configured service" >&2
    exit 1
  }
  credential="${value#"${expected_prefix}"}"
  credential="${credential%"${expected_suffix}"}"
  # 密码必须先 URL encode，否则 @/路径/查询字符会改变实际连接目标。
  [[ -n "${credential}" && "${credential}" != *'@'* && "${credential}" != */* \
    && "${credential}" != *'?'* && "${credential}" != *'#'* ]] || {
    echo "Unsafe or empty URL-encoded credential in ${ENV_FILE}: ${key}" >&2
    exit 1
  }
  [[ "${credential}" == "${expected_credential}" ]] || {
    echo "Cross-wired LobeHub credential in ${ENV_FILE}: ${key}" >&2
    exit 1
  }
}

http_origin() {
  local key="$1" value remainder authority
  value="$(require_env "${key}")"
  remainder="${value#http://}"
  authority="${remainder%%/*}"
  printf 'http://%s' "${authority}"
}

require_same_http_origin() {
  local left_key="$1" right_key="$2" left_origin right_origin
  left_origin="$(http_origin "${left_key}")"
  right_origin="$(http_origin "${right_key}")"
  [[ "${left_origin}" == "${right_origin}" ]] || {
    echo "Cross-wired platform origins in ${ENV_FILE}: ${left_key} must match ${right_key}" >&2
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

require_base64_secret_bytes() {
  local key="$1" expected="$2" value bytes
  value="$(require_env "${key}")"
  command -v openssl >/dev/null 2>&1 || {
    echo "openssl is required to validate ${key}" >&2
    exit 1
  }
  if ! bytes="$(printf '%s' "${value}" | openssl base64 -d -A 2>/dev/null | wc -c | tr -d '[:space:]')"; then
    echo "LobeHub secret ${key} must be valid Base64" >&2
    exit 1
  fi
  [[ "${bytes}" == "${expected}" ]] || {
    echo "LobeHub secret ${key} must decode to exactly ${expected} bytes" >&2
    exit 1
  }
}

require_http_origin() {
  local key="$1" value remainder
  value="$(require_env "${key}")"
  remainder="${value#http://}"
  [[ "${value}" == http://* && -n "${remainder}" && "${remainder}" != */* \
    && "${remainder}" != *\?* && "${remainder}" != *\#* && "${remainder}" != *@* ]] || {
    echo "LobeHub setting ${key} must be a plain HTTP origin without credentials or path" >&2
    exit 1
  }
}

require_http_fixed_path() {
  local key="$1" expected_path="$2" value remainder authority actual_path
  value="$(require_env "${key}")"
  remainder="${value#http://}"
  authority="${remainder%%/*}"
  actual_path="/${remainder#*/}"
  [[ "${value}" == http://* && -n "${authority}" && "${remainder}" == */* \
    && "${authority}" != *@* && "${actual_path}" == "${expected_path}" \
    && "${value}" != *\?* && "${value}" != *\#* ]] || {
    echo "LobeHub setting ${key} must use fixed HTTP path ${expected_path}" >&2
    exit 1
  }
}

require_ipv4_bind_address() {
  local key="$1" value first second third fourth extra octet
  value="$(require_env "${key}")"
  IFS=. read -r first second third fourth extra <<<"${value}"
  [[ -n "${first}" && -n "${second}" && -n "${third}" && -n "${fourth}" && -z "${extra}" ]] || {
    echo "LobeHub setting ${key} must be a concrete IPv4 address" >&2
    exit 1
  }
  for octet in "${first}" "${second}" "${third}" "${fourth}"; do
    [[ "${octet}" =~ ^[0-9]{1,3}$ ]] || {
      echo "LobeHub setting ${key} contains an invalid IPv4 octet" >&2
      exit 1
    }
    ((10#${octet} <= 255)) || {
      echo "LobeHub setting ${key} contains an invalid IPv4 octet" >&2
      exit 1
    }
  done
  [[ "${value}" != 0.0.0.0 && $((10#${first})) -ge 1 && $((10#${first})) -le 223 ]] || {
    echo "LobeHub setting ${key} must not use wildcard, unspecified or multicast binding" >&2
    exit 1
  }
}

require_release_image() {
  local key="$1" value expected leaf tag image_id
  value="$(require_env "${key}")"
  leaf="${value##*/}"
  tag="${leaf#*:}"
  [[ "${value}" =~ ^[A-Za-z0-9._:/-]+$ && "${value}" != *@* \
    && "${leaf}" == *:* && -n "${tag}" && "${tag}" != latest ]] || {
    echo "LobeHub image ${key} must use an immutable non-latest tag" >&2
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
    image_id="$(awk -F= -v wanted="${key}_ID" \
      '$1 == wanted { print substr($0, index($0, "=") + 1) }' \
      "${BASE_DIR}/lobehub/release/release.env")"
    [[ "${image_id}" =~ ^sha256:[0-9a-f]{64}$ ]] || {
      echo "LobeHub release manifest has an invalid ${key}_ID" >&2
      exit 1
    }
  fi
}

verify_loaded_release_images() {
  local manifest="${BASE_DIR}/lobehub/release/release.env"
  local key image_ref expected_id actual_id architecture
  [[ -f "${manifest}" ]] || return 0
  for key in LOBEHUB_APP_IMAGE LOBEHUB_PARADEDB_IMAGE LOBEHUB_RUSTFS_IMAGE; do
    image_ref="$(require_env "${key}")"
    expected_id="$(awk -F= -v wanted="${key}_ID" \
      '$1 == wanted { print substr($0, index($0, "=") + 1) }' "${manifest}")"
    actual_id="$(docker image inspect -f '{{.Id}}' "${image_ref}" 2>/dev/null || true)"
    [[ -n "${actual_id}" && "${actual_id}" == "${expected_id}" ]] || {
      echo "Loaded LobeHub image ID mismatch for ${image_ref}" >&2
      exit 1
    }
    architecture="$(docker image inspect -f '{{.Architecture}}' "${image_ref}")"
    [[ "${architecture}" == amd64 ]] || {
      echo "LobeHub image must be linux/amd64, got ${architecture}: ${image_ref}" >&2
      exit 1
    }
  done
}

validate_database_env() {
  local database_user database_name database_password
  require_release_image LOBEHUB_PARADEDB_IMAGE
  require_env POSTGRES_DB >/dev/null
  require_env POSTGRES_USER >/dev/null
  require_secret_bytes POSTGRES_PASSWORD 16
  require_env DATABASE_URL >/dev/null
  require_exact_env DATABASE_DRIVER node
  database_user="$(require_env POSTGRES_USER)"
  database_name="$(require_env POSTGRES_DB)"
  database_password="$(require_env POSTGRES_PASSWORD)"
  require_url_target DATABASE_URL "postgresql://$(url_encode_component "${database_user}"):" \
    "@${DB_CONTAINER}:5432/$(url_encode_component "${database_name}")" \
    "$(url_encode_component "${database_password}")"
}

validate_redis_env() {
  local redis_host redis_port redis_username redis_password
  require_env LOBEHUB_REDIS_HOST >/dev/null
  require_env LOBEHUB_REDIS_PORT >/dev/null
  require_env LOBEHUB_REDIS_USERNAME >/dev/null
  require_secret_bytes LOBEHUB_REDIS_PASSWORD 16
  require_env REDIS_URL >/dev/null
  # LobeHub's Redis wrapper appends one ':' to REDIS_PREFIX.
  require_exact_env REDIS_PREFIX 'lobehub:app'
  redis_host="$(require_env LOBEHUB_REDIS_HOST)"
  redis_port="$(require_env LOBEHUB_REDIS_PORT)"
  redis_username="$(require_env LOBEHUB_REDIS_USERNAME)"
  redis_password="$(require_env LOBEHUB_REDIS_PASSWORD)"
  require_url_target REDIS_URL "redis://$(url_encode_component "${redis_username}"):" \
    "@${redis_host}:${redis_port}/0" "$(url_encode_component "${redis_password}")"
}

validate_rustfs_env() {
  local mc_credential
  require_release_image LOBEHUB_RUSTFS_IMAGE
  require_secret_bytes RUSTFS_ACCESS_KEY 16
  require_secret_bytes RUSTFS_SECRET_KEY 32
  require_env LOBEHUB_S3_BUCKET >/dev/null
  require_env S3_ENDPOINT >/dev/null
  require_env S3_BUCKET >/dev/null
  require_env S3_ACCESS_KEY_ID >/dev/null
  require_secret_bytes S3_SECRET_ACCESS_KEY 32
  require_exact_env S3_ENABLE_PATH_STYLE 1
  require_exact_env S3_SET_ACL 0
  require_env MC_HOST_lobehub >/dev/null
  require_exact_env S3_ENDPOINT "http://${RUSTFS_CONTAINER}:9000"
  require_matching_env S3_BUCKET LOBEHUB_S3_BUCKET
  require_matching_env S3_ACCESS_KEY_ID RUSTFS_ACCESS_KEY
  require_matching_env S3_SECRET_ACCESS_KEY RUSTFS_SECRET_KEY
  mc_credential="$(url_encode_component "$(require_env RUSTFS_ACCESS_KEY)"):$(
    url_encode_component "$(require_env RUSTFS_SECRET_KEY)"
  )"
  require_url_target MC_HOST_lobehub 'http://' "@${RUSTFS_CONTAINER}:9000" "${mc_credential}"
}

validate_app_env() {
  require_release_image LOBEHUB_APP_IMAGE
  validate_database_env
  validate_redis_env
  validate_rustfs_env
  require_http_origin APP_URL
  require_http_origin INTERNAL_APP_URL
  require_exact_env INTERNAL_APP_URL "http://${APP_CONTAINER}:3210"
  require_ipv4_bind_address LOBEHUB_APP_BIND_ADDRESS
  require_http_fixed_path PLATFORM_LAUNCH_URL /lobehub/launch
  require_http_fixed_path PLATFORM_SSO_REDEEM_URL /api/internal/platform/lobehub-sso/tickets/redeem
  require_http_fixed_path PLATFORM_SSO_REVOKE_URL /api/internal/platform/lobehub-sso/grants/revoke
  require_http_fixed_path PLATFORM_MODEL_GATEWAY_BASE_URL /api/internal/platform/model-gateway/v1
  require_same_http_origin PLATFORM_LAUNCH_URL PLATFORM_SSO_REDEEM_URL
  require_same_http_origin PLATFORM_LAUNCH_URL PLATFORM_SSO_REVOKE_URL
  require_same_http_origin PLATFORM_LAUNCH_URL PLATFORM_MODEL_GATEWAY_BASE_URL
  require_secret_bytes PLATFORM_SSO_HMAC_SECRET 32
  require_base64_secret_bytes PLATFORM_MODEL_GRANT_ENCRYPTION_KEY 32
  require_base64_secret_bytes KEY_VAULTS_SECRET 32
  require_secret_bytes ENTERPRISE_INTERNAL_SCHEDULER_SECRET 32
  require_secret_bytes AUTH_SECRET 32
  require_exact_env PLATFORM_SSO_ENABLED 1
  require_exact_env LOBEHUB_ENTERPRISE_OFFLINE 1
  require_exact_env AGENT_RUNTIME_MODE local
  require_exact_env TELEMETRY_DISABLED 1
  # 当前 fork 尚未交付目标内核沙箱，所有终端必须失败关闭；不能用旧布尔变量绕过。
  require_exact_env LOBEHUB_DEVICE_EXECUTION_MODE disabled
}

ensure_env_file() {
  local owner_uid
  [[ -f "${ENV_FILE}" ]] || { echo "LobeHub env file not found: ${ENV_FILE}" >&2; exit 1; }
  [[ ! -L "${ENV_FILE}" ]] || { echo "LobeHub env file must not be a symbolic link: ${ENV_FILE}" >&2; exit 1; }
  [[ "$(stat -c '%a' "${ENV_FILE}" 2>/dev/null || stat -f '%Lp' "${ENV_FILE}")" == "600" ]] || {
    echo "LobeHub env file must have mode 0600: ${ENV_FILE}" >&2
    exit 1
  }
  owner_uid="$(stat -c '%u' "${ENV_FILE}" 2>/dev/null || stat -f '%u' "${ENV_FILE}")"
  [[ "${owner_uid}" == "$(id -u)" ]] || {
    echo "LobeHub env file must be owned by the invoking service account" >&2
    exit 1
  }
  validate_env_file_syntax
}

ensure_prerequisites() {
  ensure_env_file
  command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }
  verify_loaded_release_images
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
  require_redis_noperm() {
    local description="$1"
    shift
    outside_output="$(redis_call "$@" 2>&1 || true)"
    [[ "${outside_output}" == *NOPERM* ]] || {
      echo "LobeHub Redis ACL unexpectedly permits ${description}" >&2
      exit 1
    }
  }
  redis_call PING | grep -Fx PONG >/dev/null
  redis_call SET "lobehub:app:acl-preflight:$$" 1 EX 30 | grep -Fx OK >/dev/null
  # 客户端认证的 request/code/session 状态机使用经过测试的固定 Lua 脚本做原子转换。
  redis_call EVAL 'return redis.call("GET", KEYS[1])' 1 \
    "lobehub:app:acl-preflight:$$" | grep -Fx 1 >/dev/null
  redis_call DEL "lobehub:app:acl-preflight:$$" >/dev/null
  redis_call PUBLISH "lobehub:app:acl-preflight" 1 >/dev/null
  require_redis_noperm 'keys outside lobehub:app:*' \
    SET "test-agent:lobehub-acl-preflight:$$" 1 EX 30
  require_redis_noperm 'Lua access outside lobehub:app:*' \
    EVAL 'return redis.call("GET", KEYS[1])' 1 "test-agent:lobehub-acl-preflight:$$"
  require_redis_noperm 'channels outside lobehub:app:*' \
    PUBLISH "test-agent:lobehub-acl-preflight" 1
  require_redis_noperm 'CONFIG' CONFIG GET '*'
  require_redis_noperm 'ACL administration' ACL WHOAMI
  require_redis_noperm 'MODULE administration' MODULE LIST
  require_redis_noperm 'database flush' FLUSHDB
  require_redis_noperm 'unscoped KEYS' KEYS '*'
  require_redis_noperm 'SCRIPT FLUSH' SCRIPT FLUSH
}

start_db() {
  local image server_version db_env
  validate_database_env
  image="$(require_env LOBEHUB_PARADEDB_IMAGE)"
  create_runtime_env_file db_env POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD
  mkdir -p "${BASE_DIR}/lobehub/paradedb"
  docker rm -f "${DB_CONTAINER}" >/dev/null 2>&1 || true
  docker run -d --name "${DB_CONTAINER}" --restart unless-stopped \
    --network "${NETWORK}" --env-file "${db_env}" \
    -v "${BASE_DIR}/lobehub/paradedb:/var/lib/postgresql/data" \
    "${image}" >/dev/null
  local attempt
  for attempt in {1..60}; do
    # 官方 entrypoint 初始化时会短暂启动一个临时 PostgreSQL；只看 pg_isready 会在它
    # 随后关闭、正式进程尚未 exec 成 PID 1 的窗口提前放行迁移。
    if docker exec "${DB_CONTAINER}" sh -c \
      'test "$(cat /proc/1/comm)" = postgres' >/dev/null 2>&1 \
      && docker exec "${DB_CONTAINER}" pg_isready \
        -U "$(require_env POSTGRES_USER)" -d "$(require_env POSTGRES_DB)" >/dev/null 2>&1; then
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
  local app_env
  validate_app_env
  create_app_runtime_env_file app_env
  # 生产镜像是 scratch + Node，既不包含 pnpm 也不包含 shell；直接运行镜像内迁移入口。
  docker run --rm --network "${NETWORK}" --env-file "${app_env}" \
    "$(require_env LOBEHUB_APP_IMAGE)" /app/docker.cjs
}

start_rustfs() {
  local rustfs_env
  validate_rustfs_env
  create_runtime_env_file rustfs_env RUSTFS_ACCESS_KEY RUSTFS_SECRET_KEY
  mkdir -p "${BASE_DIR}/lobehub/rustfs/data" "${BASE_DIR}/lobehub/rustfs/logs"
  # RustFS official container uses UID 10001. Refuse to hide permission failures behind a root container.
  chown -R 10001:10001 "${BASE_DIR}/lobehub/rustfs/data" "${BASE_DIR}/lobehub/rustfs/logs"
  docker rm -f "${RUSTFS_CONTAINER}" >/dev/null 2>&1 || true
  docker run -d --name "${RUSTFS_CONTAINER}" --restart unless-stopped \
    --network "${NETWORK}" --env-file "${rustfs_env}" \
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
  local bind_address attempt app_env
  validate_app_env
  command -v curl >/dev/null 2>&1 || { echo "curl is required for the LobeHub app readiness check" >&2; exit 1; }
  bind_address="$(require_env LOBEHUB_APP_BIND_ADDRESS)"
  create_app_runtime_env_file app_env
  docker rm -f "${APP_CONTAINER}" >/dev/null 2>&1 || true
  docker run -d --name "${APP_CONTAINER}" --restart unless-stopped \
    --network "${NETWORK}" --env-file "${app_env}" \
    -p "${bind_address}:3210:3210" \
    "$(require_env LOBEHUB_APP_IMAGE)" >/dev/null
  for attempt in {1..120}; do
    if curl -fs --max-time 3 -o /dev/null "http://${bind_address}:3210/"; then
      echo "LobeHub app is ready on ${bind_address}:3210"
      return
    fi
    if [[ "$(docker inspect -f '{{.State.Running}}' "${APP_CONTAINER}" 2>/dev/null || true)" != true ]]; then
      break
    fi
    sleep 2
  done
  docker rm -f "${APP_CONTAINER}" >/dev/null 2>&1 || true
  echo "LobeHub app did not become ready on ${bind_address}:3210" >&2
  exit 1
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
  local bind_address
  bind_address="$(require_env LOBEHUB_APP_BIND_ADDRESS)"
  docker ps --filter "name=test-agent-lobehub" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
  command -v curl >/dev/null 2>&1 || { echo "curl is required for the LobeHub app status check" >&2; exit 1; }
  curl -fsS --max-time 3 -o /dev/null "http://${bind_address}:3210/" || {
    echo "LobeHub app readiness failed on ${bind_address}:3210" >&2
    exit 1
  }
  echo "LobeHub app readiness passed on ${bind_address}:3210"
}

require_running_container() {
  local container="$1" running restart_policy
  running="$(docker inspect -f '{{.State.Running}}' "${container}" 2>/dev/null || true)"
  [[ "${running}" == true ]] || {
    echo "Required LobeHub container is not running: ${container}" >&2
    exit 1
  }
  restart_policy="$(docker inspect -f '{{.HostConfig.RestartPolicy.Name}}' "${container}")"
  [[ "${restart_policy}" == unless-stopped ]] || {
    echo "Unsafe restart policy for ${container}: ${restart_policy}" >&2
    exit 1
  }
}

require_container_image() {
  local container="$1" image_key="$2" actual_id expected_id
  actual_id="$(docker inspect -f '{{.Image}}' "${container}")"
  expected_id="$(docker image inspect -f '{{.Id}}' "$(require_env "${image_key}")")"
  [[ "${actual_id}" == "${expected_id}" ]] || {
    echo "Running container image does not match ${image_key}: ${container}" >&2
    exit 1
  }
}

require_container_env_absent() {
  local container="$1" key="$2"
  if docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "${container}" \
    | grep -q "^${key}="; then
    echo "Container ${container} unexpectedly received ${key}" >&2
    exit 1
  fi
}

require_container_env_exact() {
  local container="$1" key="$2" expected="$3" actual
  actual="$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "${container}" \
    | awk -F= -v wanted="${key}" '$1 == wanted { print substr($0, index($0, "=") + 1); exit }')"
  [[ "${actual}" == "${expected}" ]] || {
    echo "Running container ${container} has an unsafe or stale ${key}" >&2
    exit 1
  }
}

require_host_binding() {
  local container="$1" container_port="$2" expected_host="$3" actual_host
  actual_host="$(docker inspect -f \
    "{{with index .NetworkSettings.Ports \"${container_port}/tcp\"}}{{(index . 0).HostIp}}{{end}}" \
    "${container}" 2>/dev/null || true)"
  [[ "${actual_host}" == "${expected_host}" ]] || {
    echo "Unsafe host binding for ${container}:${container_port}; expected ${expected_host}" >&2
    exit 1
  }
}

require_unpublished_port() {
  local container="$1" container_port="$2" published
  published="$(docker port "${container}" "${container_port}/tcp" 2>/dev/null || true)"
  [[ -z "${published}" ]] || {
    echo "Container port must remain unpublished: ${container}:${container_port}" >&2
    exit 1
  }
}

verify_database_runtime() {
  local server_version
  require_running_container "${DB_CONTAINER}"
  require_container_image "${DB_CONTAINER}" LOBEHUB_PARADEDB_IMAGE
  require_unpublished_port "${DB_CONTAINER}" 5432
  docker exec "${DB_CONTAINER}" sh -c 'test "$(cat /proc/1/comm)" = postgres'
  docker exec "${DB_CONTAINER}" pg_isready \
    -U "$(require_env POSTGRES_USER)" -d "$(require_env POSTGRES_DB)" >/dev/null
  server_version="$(docker exec "${DB_CONTAINER}" psql \
    -U "$(require_env POSTGRES_USER)" -d "$(require_env POSTGRES_DB)" \
    -Atqc 'show server_version_num')"
  [[ "${server_version}" =~ ^17[0-9]{4}$ ]] || {
    echo "LobeHub database verification expected PostgreSQL 17, got ${server_version}" >&2
    exit 1
  }
}

verify_rustfs_runtime() {
  local bucket policy
  require_running_container "${RUSTFS_CONTAINER}"
  require_container_image "${RUSTFS_CONTAINER}" LOBEHUB_RUSTFS_IMAGE
  require_host_binding "${RUSTFS_CONTAINER}" 9000 127.0.0.1
  require_host_binding "${RUSTFS_CONTAINER}" 9001 127.0.0.1
  [[ -x "${MC_BIN}" ]] || { echo "Offline mc binary not found: ${MC_BIN}" >&2; exit 1; }
  export MC_HOST_lobehub
  MC_HOST_lobehub="$(require_env MC_HOST_lobehub)"
  bucket="lobehub/$(require_env LOBEHUB_S3_BUCKET)"
  "${MC_BIN}" ls "${bucket}" >/dev/null
  policy="$("${MC_BIN}" anonymous get "${bucket}" 2>&1)"
  printf '%s\n' "${policy}" | grep -Eiq 'private|none' || {
    echo "RustFS bucket is not private: ${bucket}" >&2
    exit 1
  }
}

require_http_status() {
  local expected="$1" method="$2" url="$3" description="$4" actual
  actual="$(curl -sS --max-time 20 -o /dev/null -w '%{http_code}' -X "${method}" "${url}")"
  [[ "${actual}" == "${expected}" ]] || {
    echo "${description} expected HTTP ${expected}, got ${actual}" >&2
    exit 1
  }
}

verify_app_runtime() {
  local bind_address
  bind_address="$(require_env LOBEHUB_APP_BIND_ADDRESS)"
  require_running_container "${APP_CONTAINER}"
  require_container_image "${APP_CONTAINER}" LOBEHUB_APP_IMAGE
  require_host_binding "${APP_CONTAINER}" 3210 "${bind_address}"
  require_container_env_exact "${APP_CONTAINER}" PLATFORM_SSO_ENABLED 1
  require_container_env_exact "${APP_CONTAINER}" LOBEHUB_ENTERPRISE_OFFLINE 1
  require_container_env_exact "${APP_CONTAINER}" AGENT_RUNTIME_MODE local
  require_container_env_exact "${APP_CONTAINER}" REDIS_PREFIX 'lobehub:app'
  require_container_env_exact "${APP_CONTAINER}" TELEMETRY_DISABLED 1
  require_container_env_exact "${APP_CONTAINER}" LOBEHUB_DEVICE_EXECUTION_MODE disabled
  status_all
  require_http_status 403 POST \
    "http://${bind_address}:3210/api/workflows/task/schedule-dispatch" \
    'Public workflow endpoint offline policy'
  require_http_status 401 POST \
    "http://${bind_address}:3210/api/agent/enterprise/schedule-dispatch" \
    'Internal scheduler authentication'
}

verify_container_secret_isolation() {
  local key
  for key in PLATFORM_SSO_HMAC_SECRET RUSTFS_SECRET_KEY \
    ENTERPRISE_INTERNAL_SCHEDULER_SECRET AUTH_SECRET; do
    require_container_env_absent "${DB_CONTAINER}" "${key}"
  done
  for key in POSTGRES_PASSWORD PLATFORM_SSO_HMAC_SECRET \
    ENTERPRISE_INTERNAL_SCHEDULER_SECRET AUTH_SECRET; do
    require_container_env_absent "${RUSTFS_CONTAINER}" "${key}"
  done
  for key in POSTGRES_PASSWORD RUSTFS_ACCESS_KEY RUSTFS_SECRET_KEY MC_HOST_lobehub; do
    require_container_env_absent "${APP_CONTAINER}" "${key}"
  done
}

verify_platform_integration() {
  local probe_env
  validate_app_env
  [[ -f "${PLATFORM_PROBE}" && ! -L "${PLATFORM_PROBE}" ]] || {
    echo "Installed LobeHub platform probe must be a regular non-symlink file: ${PLATFORM_PROBE}" >&2
    exit 1
  }
  create_platform_probe_env_file probe_env
  docker run --rm --read-only \
    --network "${NETWORK}" \
    --env-file "${probe_env}" \
    --cap-drop ALL \
    --security-opt no-new-privileges \
    --pids-limit 64 \
    -v "${PLATFORM_PROBE}:/app/lobehub-platform-probe.mjs:ro" \
    "$(require_env LOBEHUB_APP_IMAGE)" \
    /app/lobehub-platform-probe.mjs
}

verify_deployment() {
  validate_app_env
  check_redis
  verify_database_runtime
  verify_rustfs_runtime
  verify_app_runtime
  verify_container_secret_isolation
  verify_platform_integration
  echo 'LobeHub deployment verification passed'
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
  verify-platform) verify_platform_integration ;;
  verify-deployment) verify_deployment ;;
  *) usage; exit 2 ;;
esac
