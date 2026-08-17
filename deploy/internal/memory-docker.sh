#!/usr/bin/env bash
set -euo pipefail

MEMORY_ENV_FILE="${TEST_AGENT_MEMORY_ENV_FILE:-/data/testagent/config/memory.env}"
EMBEDDING_ENV_FILE="${TEST_AGENT_EMBEDDING_ENV_FILE:-/data/testagent/config/embedding.env}"
ARTIFACT_DIR="${TEST_AGENT_MEMORY_ARTIFACT_DIR:-/data/0709/memory}"
RUNTIME_DIR="${TEST_AGENT_MEMORY_RUNTIME_DIR:-/data/testagent/runtime/memory}"
POSTGRES_DATA_DIR="${TEST_AGENT_MEMORY_POSTGRES_DATA_DIR:-/data/testagent/memory/postgres}"
DB_CONTAINER=test-agent-memory-postgres
EMBEDDING_CONTAINER=test-agent-memory-embedding
VIP_CONTAINER=test-agent-memory-vip

usage() {
  cat <<'USAGE'
Usage: memory-docker.sh <command>

Commands:
  validate-memory-config  Validate /data/testagent/config/memory.env without Docker writes.
  validate-embedding-config
  verify-artifacts        Verify every file in the offline SHA256SUMS manifest.
  load-db|load-embedding|load-memory|load-vip
  start-db|stop-db|verify-db
  start-embedding|stop-embedding|verify-embedding
  migrate                 Run the single Alembic head against the independent memory database.
  start-memory|stop-memory|verify-memory
  start-vip|stop-vip|verify-vip
  status                  Show only memory data-plane containers on this host.

The role commands are intentionally separate so PostgreSQL, CPU embedding,
Mem0 replicas and the VIP can run on different physical nodes. Start commands
refuse to replace an existing container; stop the exact role explicitly first.
USAGE
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

require_secure_env() {
  local file="$1" mode duplicates
  [[ -f "${file}" && ! -L "${file}" ]] || {
    echo "Config must be a regular non-symlink file: ${file}" >&2
    exit 1
  }
  if stat -f '%Lp' "${file}" >/dev/null 2>&1; then
    mode="$(stat -f '%Lp' "${file}")"
  else
    mode="$(stat -c '%a' "${file}")"
  fi
  [[ "${mode}" == 600 ]] || {
    echo "Config permissions must be 0600: ${file}" >&2
    exit 1
  }
  duplicates="$(awk -F= '
    /^[[:space:]]*#/ || /^[[:space:]]*$/ { next }
    index($0, "=") { key=$1; gsub(/[[:space:]]/, "", key); if (++seen[key] == 2) print key }
  ' "${file}")"
  [[ -z "${duplicates}" ]] || {
    echo "Duplicate config key(s): ${duplicates//$'\n'/, }" >&2
    exit 1
  }
  if grep -Eq 'REPLACE_|`|\$\(' "${file}" || LC_ALL=C grep -q $'\r' "${file}"; then
    echo "Config contains a placeholder or executable/control syntax: ${file}" >&2
    exit 1
  fi
}

read_value() {
  local file="$1" requested="$2" line key
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* || "${line}" != *=* ]] && continue
    key="${line%%=*}"
    key="${key//[[:space:]]/}"
    [[ "${key}" == "${requested}" ]] || continue
    printf '%s' "${line#*=}"
    return 0
  done <"${file}"
}

required_value() {
  local file="$1" key="$2" value
  value="$(read_value "${file}" "${key}")"
  [[ -n "${value}" ]] || { echo "Missing required config key: ${key}" >&2; exit 1; }
  printf '%s' "${value}"
}

validate_port() {
  local key="$1" value="$2"
  [[ "${value}" =~ ^[0-9]+$ && "${value}" -ge 1 && "${value}" -le 65535 ]] || {
    echo "${key} must be between 1 and 65535" >&2
    exit 1
  }
}

validate_tag() {
  local key="$1" value="$2"
  [[ "${value}" =~ ^[a-z0-9][a-z0-9._/-]+:[A-Za-z0-9][A-Za-z0-9._-]+$ ]] \
    && [[ "${value}" != *:latest ]] || {
      echo "${key} must use an explicit non-latest Docker tag" >&2
      exit 1
    }
}

validate_memory_config() {
  local api_key hmac_secret database_password database_url database_credentials database_url_password gateway_url
  local enterprise_model enterprise_dimension enterprise_fingerprint
  require_secure_env "${MEMORY_ENV_FILE}"
  validate_tag TEST_AGENT_MEMORY_SERVICE_IMAGE \
    "$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_IMAGE)"
  validate_tag TEST_AGENT_MEMORY_PGVECTOR_IMAGE \
    "$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_PGVECTOR_IMAGE)"
  validate_tag TEST_AGENT_MEMORY_NGINX_IMAGE \
    "$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_NGINX_IMAGE)"
  api_key="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_API_KEY)"
  hmac_secret="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_MODEL_GATEWAY_HMAC_SECRET)"
  database_password="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_DB_PASSWORD)"
  [[ "${#api_key}" -ge 32 && "${#hmac_secret}" -ge 32 && "${#database_password}" -ge 32 ]] || {
    echo "Memory API, HMAC and database secrets must each be at least 32 characters" >&2
    exit 1
  }
  [[ "${database_password}" =~ ^[A-Za-z0-9._~-]+$ ]] || {
    echo "Memory database password must use URL-safe random characters" >&2
    exit 1
  }
  database_url="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_DATABASE_URL)"
  [[ "${database_url}" =~ ^postgresql://[^[:space:]@]+:[^[:space:]@]+@[^[:space:]/]+:[0-9]+/[A-Za-z0-9_]+$ ]] || {
    echo "Memory database URL must be an explicit postgresql URL without whitespace" >&2
    exit 1
  }
  database_credentials="${database_url#postgresql://}"
  database_credentials="${database_credentials%%@*}"
  database_url_password="${database_credentials#*:}"
  [[ "${database_url_password}" == "${database_password}" ]] || {
    echo "Memory database URL password must match TEST_AGENT_MEMORY_DB_PASSWORD" >&2
    exit 1
  }
  gateway_url="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_MODEL_GATEWAY_URL)"
  [[ "${gateway_url}" =~ ^https?://[^/[:space:]]+(:[0-9]+)?/api/internal/platform/model-gateway/v1$ ]] || {
    echo "Memory model gateway URL must use the fixed internal base path" >&2
    exit 1
  }
  validate_port TEST_AGENT_MEMORY_DB_HOST_PORT \
    "$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_DB_HOST_PORT)"
  validate_port TEST_AGENT_MEMORY_HOST_PORT \
    "$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_HOST_PORT)"
  validate_port TEST_AGENT_MEMORY_VIP_HOST_PORT \
    "$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_VIP_HOST_PORT)"
  enterprise_model="$(read_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_ENTERPRISE_EMBEDDING_MODEL_ID)"
  enterprise_dimension="$(read_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_ENTERPRISE_EMBEDDING_DIMENSION)"
  enterprise_fingerprint="$(read_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_ENTERPRISE_EMBEDDING_FINGERPRINT)"
  if [[ -n "${enterprise_model}${enterprise_dimension}${enterprise_fingerprint}" ]]; then
    [[ "${enterprise_model}" =~ ^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$ \
      && "${enterprise_dimension}" =~ ^[1-9][0-9]*$ && "${enterprise_dimension}" -le 65535 \
      && "${enterprise_fingerprint}" =~ ^[A-Za-z0-9._:-]{8,128}$ ]] || {
        echo "Enterprise embedding model, dimension and fingerprint must be configured together" >&2
        exit 1
      }
  fi
  echo "Memory configuration is valid (secrets hidden)."
}

validate_embedding_config() {
  local api_key
  require_secure_env "${EMBEDDING_ENV_FILE}"
  validate_tag TEST_AGENT_EMBEDDING_IMAGE \
    "$(required_value "${EMBEDDING_ENV_FILE}" TEST_AGENT_EMBEDDING_IMAGE)"
  api_key="$(required_value "${EMBEDDING_ENV_FILE}" TEST_AGENT_EMBEDDING_API_KEY)"
  [[ "${#api_key}" -ge 32 ]] || {
    echo "Embedding API key must be at least 32 characters" >&2
    exit 1
  }
  validate_port TEST_AGENT_EMBEDDING_HOST_PORT \
    "$(required_value "${EMBEDDING_ENV_FILE}" TEST_AGENT_EMBEDDING_HOST_PORT)"
  echo "Embedding configuration is valid (secret hidden)."
}

verify_artifacts() {
  [[ -f "${ARTIFACT_DIR}/SHA256SUMS" ]] || {
    echo "Memory SHA256SUMS not found under ${ARTIFACT_DIR}" >&2
    exit 1
  }
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "${ARTIFACT_DIR}" && sha256sum -c SHA256SUMS)
  elif command -v shasum >/dev/null 2>&1; then
    (cd "${ARTIFACT_DIR}" && shasum -a 256 -c SHA256SUMS)
  else
    echo "Neither sha256sum nor shasum is available" >&2
    exit 1
  fi
}

load_image() {
  local tar_name="$1"
  require_command docker
  verify_artifacts >/dev/null
  [[ -f "${ARTIFACT_DIR}/images/${tar_name}" ]] || {
    echo "Image artifact not found: ${tar_name}" >&2
    exit 1
  }
  docker load -i "${ARTIFACT_DIR}/images/${tar_name}"
}

ensure_container_absent() {
  local name="$1"
  if docker container inspect "${name}" >/dev/null 2>&1; then
    echo "Container already exists; stop the exact role before replacement: ${name}" >&2
    exit 1
  fi
}

write_runtime_env() {
  local source="$1" destination="$2" prefix="$3" line key
  mkdir -p "${RUNTIME_DIR}"
  umask 077
  : >"${destination}"
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* || "${line}" != *=* ]] && continue
    key="${line%%=*}"
    key="${key//[[:space:]]/}"
    [[ "${key}" == "${prefix}"* ]] || continue
    printf '%s\n' "${line}" >>"${destination}"
  done <"${source}"
  chmod 0600 "${destination}"
}

stop_and_remove() {
  local name="$1"
  if docker container inspect "${name}" >/dev/null 2>&1; then
    docker stop --time 60 "${name}" >/dev/null
    docker rm "${name}" >/dev/null
  fi
}

memory_container_name() {
  local node_id
  node_id="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_NODE_ID)"
  [[ "${node_id}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{1,31}$ ]] || {
    echo "TEST_AGENT_MEMORY_NODE_ID is invalid" >&2
    exit 1
  }
  printf 'test-agent-memory-%s' "${node_id}"
}

start_db() {
  local env_file image bind port
  validate_memory_config >/dev/null
  require_command docker
  ensure_container_absent "${DB_CONTAINER}"
  mkdir -p "${POSTGRES_DATA_DIR}" "${RUNTIME_DIR}"
  env_file="${RUNTIME_DIR}/postgres.env"
  umask 077
  {
    printf 'POSTGRES_DB=%s\n' "$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_DB_NAME)"
    printf 'POSTGRES_USER=%s\n' "$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_DB_USER)"
    printf 'POSTGRES_PASSWORD=%s\n' "$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_DB_PASSWORD)"
  } >"${env_file}"
  chmod 0600 "${env_file}"
  image="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_PGVECTOR_IMAGE)"
  bind="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_DB_BIND_ADDRESS)"
  port="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_DB_HOST_PORT)"
  docker run -d --name "${DB_CONTAINER}" --restart unless-stopped \
    --env-file "${env_file}" -p "${bind}:${port}:5432" \
    -v "${POSTGRES_DATA_DIR}:/var/lib/postgresql/data" "${image}" >/dev/null
  echo "Started independent memory PostgreSQL container ${DB_CONTAINER}."
}

verify_db() {
  local attempt
  for ((attempt = 1; attempt <= 60; attempt++)); do
    if docker exec "${DB_CONTAINER}" sh -ec '
      PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 \
        -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc "select 1"
    ' 2>/dev/null | grep -qx 1; then
      echo "Memory PostgreSQL authenticated role and database are ready."
      return 0
    fi
    sleep 2
  done
  echo "Memory PostgreSQL did not become ready within 120 seconds." >&2
  docker logs --tail 80 "${DB_CONTAINER}" >&2 || true
  return 1
}

start_embedding() {
  local env_file image bind port
  validate_embedding_config >/dev/null
  require_command docker
  ensure_container_absent "${EMBEDDING_CONTAINER}"
  env_file="${RUNTIME_DIR}/embedding-runtime.env"
  write_runtime_env "${EMBEDDING_ENV_FILE}" "${env_file}" TEST_AGENT_EMBEDDING_
  image="$(required_value "${EMBEDDING_ENV_FILE}" TEST_AGENT_EMBEDDING_IMAGE)"
  bind="$(required_value "${EMBEDDING_ENV_FILE}" TEST_AGENT_EMBEDDING_BIND_ADDRESS)"
  port="$(required_value "${EMBEDDING_ENV_FILE}" TEST_AGENT_EMBEDDING_HOST_PORT)"
  docker run -d --name "${EMBEDDING_CONTAINER}" --restart unless-stopped \
    --read-only --tmpfs /tmp:rw,noexec,nosuid,size=256m \
    --cap-drop ALL --security-opt no-new-privileges \
    --env-file "${env_file}" -p "${bind}:${port}:18989" "${image}" >/dev/null
  echo "Started fixed CPU embedding container ${EMBEDDING_CONTAINER}."
}

verify_embedding() {
  local api_key port body attempt
  require_command curl
  api_key="$(required_value "${EMBEDDING_ENV_FILE}" TEST_AGENT_EMBEDDING_API_KEY)"
  port="$(required_value "${EMBEDDING_ENV_FILE}" TEST_AGENT_EMBEDDING_HOST_PORT)"
  for ((attempt = 1; attempt <= 90; attempt++)); do
    body="$(printf 'header = "Authorization: Bearer %s"\n' "${api_key}" \
      | curl -fsS --max-time 10 --config - "http://127.0.0.1:${port}/ready" 2>/dev/null || true)"
    if [[ "${body}" == *'"status":"UP"'* && "${body}" == *'"dimension":512'* \
      && "${body}" == *'7999e1d3359715c523056ef9478215996d62a620'* ]]; then
      echo "CPU embedding is ready with the fixed 512-dimensional model."
      return 0
    fi
    sleep 2
  done
  echo "CPU embedding did not expose the fixed model identity within 180 seconds." >&2
  docker logs --tail 80 "${EMBEDDING_CONTAINER}" >&2 || true
  return 1
}

memory_runtime_env() {
  local destination="${RUNTIME_DIR}/memory-runtime.env"
  write_runtime_env "${MEMORY_ENV_FILE}" "${destination}" TEST_AGENT_MEMORY_SERVICE_
  printf '%s' "${destination}"
}

migrate() {
  local image env_file
  validate_memory_config >/dev/null
  require_command docker
  image="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_IMAGE)"
  env_file="$(memory_runtime_env)"
  docker run --rm --name test-agent-memory-migrate --read-only \
    --tmpfs /tmp:rw,noexec,nosuid,size=64m \
    --cap-drop ALL --security-opt no-new-privileges --env-file "${env_file}" \
    --entrypoint alembic "${image}" -c /opt/test-agent-memory/alembic.ini upgrade head
  echo "Memory Alembic migration reached head."
}

start_memory() {
  local name image env_file bind port
  validate_memory_config >/dev/null
  require_command docker
  name="$(memory_container_name)"
  ensure_container_absent "${name}"
  image="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_IMAGE)"
  env_file="$(memory_runtime_env)"
  bind="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_BIND_ADDRESS)"
  port="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_HOST_PORT)"
  docker run -d --name "${name}" --restart unless-stopped \
    --read-only --tmpfs /tmp:rw,noexec,nosuid,size=256m \
    --cap-drop ALL --security-opt no-new-privileges \
    --env-file "${env_file}" -p "${bind}:${port}:18888" "${image}" >/dev/null
  echo "Started stateless memory replica ${name}."
}

verify_memory_url() {
  local url="$1" api_key body attempt
  require_command curl
  api_key="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_SERVICE_API_KEY)"
  for ((attempt = 1; attempt <= 60; attempt++)); do
    body="$(printf 'header = "X-Memory-Service-Key: %s"\n' "${api_key}" \
      | curl -fsS --max-time 10 --config - "${url}/ready" 2>/dev/null || true)"
    if [[ "${body}" == *'"status":"UP"'* && "${body}" == *'"rawMessageCount":0'* ]]; then
      echo "Memory endpoint is ready and reports rawMessageCount=0."
      return 0
    fi
    sleep 2
  done
  echo "Memory endpoint did not become ready within 120 seconds or reported raw messages." >&2
  return 1
}

start_vip() {
  local image bind port upstreams config server
  validate_memory_config >/dev/null
  require_command docker
  ensure_container_absent "${VIP_CONTAINER}"
  image="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_NGINX_IMAGE)"
  bind="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_VIP_BIND_ADDRESS)"
  port="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_VIP_HOST_PORT)"
  upstreams="$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_UPSTREAMS)"
  config="${RUNTIME_DIR}/memory-nginx.conf"
  mkdir -p "${RUNTIME_DIR}"
  umask 077
  {
    printf 'events {}\nhttp {\n  upstream memory_nodes {\n    least_conn;\n'
    IFS=',' read -r -a servers <<<"${upstreams}"
    [[ "${#servers[@]}" -ge 1 ]] || { echo "At least one memory upstream is required" >&2; exit 1; }
    for server in "${servers[@]}"; do
      [[ "${server}" =~ ^[A-Za-z0-9.-]+:[0-9]+$ ]] || {
        echo "Invalid memory upstream: ${server}" >&2
        exit 1
      }
      printf '    server %s max_fails=2 fail_timeout=5s;\n' "${server}"
    done
    printf '    keepalive 64;\n  }\n  server {\n    listen 18888;\n'
    printf '    client_max_body_size 2m;\n'
    printf '    location / { proxy_pass http://memory_nodes; proxy_http_version 1.1; proxy_set_header Connection ""; proxy_connect_timeout 500ms; proxy_read_timeout 125s; proxy_send_timeout 10s; proxy_next_upstream error timeout http_502 http_503 http_504; proxy_next_upstream_tries 2; }\n'
    printf '  }\n}\n'
  } >"${config}"
  chmod 0644 "${config}"
  docker run -d --name "${VIP_CONTAINER}" --restart unless-stopped --read-only \
    --user 101:101 --cap-drop ALL --security-opt no-new-privileges \
    --tmpfs /var/cache/nginx:rw,noexec,nosuid,nodev,size=32m,uid=101,gid=101,mode=0700 \
    --tmpfs /var/run:rw,noexec,nosuid,nodev,size=1m,uid=101,gid=101,mode=0700 \
    --tmpfs /tmp:rw,noexec,nosuid,nodev,size=16m,uid=101,gid=101,mode=0700 \
    -p "${bind}:${port}:18888" -v "${config}:/etc/nginx/nginx.conf:ro" "${image}" >/dev/null
  echo "Started memory VIP ${VIP_CONTAINER}."
}

status() {
  require_command docker
  docker ps -a --filter 'name=^/test-agent-memory-' \
    --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
}

command_name="${1:-help}"
case "${command_name}" in
  validate-memory-config) validate_memory_config ;;
  validate-embedding-config) validate_embedding_config ;;
  verify-artifacts) verify_artifacts ;;
  load-db) load_image test-agent-pgvector_0.8.1-pg16_internal-linux-amd64.tar ;;
  load-embedding) load_image test-agent-embedding-bge-small-zh-v1.5_internal-linux-amd64.tar ;;
  load-memory) load_image test-agent-memory-service_internal-linux-amd64.tar ;;
  load-vip) load_image test-agent-memory-nginx_1.27.2_internal-linux-amd64.tar ;;
  start-db) start_db ;;
  stop-db) require_command docker; stop_and_remove "${DB_CONTAINER}" ;;
  verify-db) verify_db ;;
  start-embedding) start_embedding ;;
  stop-embedding) require_command docker; stop_and_remove "${EMBEDDING_CONTAINER}" ;;
  verify-embedding) verify_embedding ;;
  migrate) migrate ;;
  start-memory) start_memory ;;
  stop-memory) require_command docker; stop_and_remove "$(memory_container_name)" ;;
  verify-memory)
    verify_memory_url "http://127.0.0.1:$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_HOST_PORT)" ;;
  start-vip) start_vip ;;
  stop-vip) require_command docker; stop_and_remove "${VIP_CONTAINER}" ;;
  verify-vip)
    verify_memory_url "http://127.0.0.1:$(required_value "${MEMORY_ENV_FILE}" TEST_AGENT_MEMORY_VIP_HOST_PORT)" ;;
  status) status ;;
  help|-h|--help) usage ;;
  *) echo "Unknown command: ${command_name}" >&2; usage >&2; exit 2 ;;
esac
