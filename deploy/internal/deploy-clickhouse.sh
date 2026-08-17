#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="/data/testagent/config/clickhouse.env"
USERS_CONFIG_FILE="/data/testagent/config/clickhouse-users.xml"
IMAGE_TAR=""
ACTION="deploy"
REPLACE_EXISTING=false

usage() {
  cat <<'USAGE'
Usage: deploy-clickhouse.sh [options] [validate|deploy|verify|stop|status|logs]

Options:
  --env-file <path>          Prepared clickhouse.env.
  --users-config <path>      Prepared clickhouse-users.xml.
  --image-tar <path>         Docker-loadable ClickHouse image tar.
  --replace-existing         Replace the container; persistent data is never deleted.
  -h, --help                 Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --users-config) USERS_CONFIG_FILE="$2"; shift 2 ;;
    --image-tar) IMAGE_TAR="$2"; shift 2 ;;
    --replace-existing) REPLACE_EXISTING=true; shift ;;
    validate|deploy|verify|stop|status|logs) ACTION="$1"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

require_command() { command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 1; }; }
require_file() { [[ -f "$1" ]] || { echo "Required file not found: $1" >&2; exit 1; }; }
env_value() {
  local wanted="$2" line key value result=""
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"; [[ "${line}" == *=* ]] || continue
    key="${line%%=*}"; [[ "${key}" == "${wanted}" ]] || continue
    value="${line#*=}"
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then value="${value:1:${#value}-2}"; fi
    result="${value}"
  done <"$1"
  printf '%s' "${result}"
}

validate_config() {
  local key digest
  require_command openssl
  require_file "${ENV_FILE}"; require_file "${USERS_CONFIG_FILE}"
  grep -q 'REPLACE_' "${ENV_FILE}" "${USERS_CONFIG_FILE}" && {
    echo "ClickHouse configuration still contains a REPLACE_ placeholder" >&2; exit 1;
  }
  for key in TEST_AGENT_CLICKHOUSE_IMAGE TEST_AGENT_CLICKHOUSE_CONTAINER TEST_AGENT_CLICKHOUSE_HOST_PORT \
    TEST_AGENT_CLICKHOUSE_DATA_ROOT TEST_AGENT_CLICKHOUSE_LOG_ROOT TEST_AGENT_CLICKHOUSE_DATABASE \
    TEST_AGENT_CLICKHOUSE_USERNAME TEST_AGENT_CLICKHOUSE_PASSWORD; do
    [[ "$(grep -c "^${key}=" "${ENV_FILE}" || true)" -eq 1 && -n "$(env_value "${ENV_FILE}" "${key}")" ]] || {
      echo "${ENV_FILE} must contain exactly one non-empty ${key}" >&2; exit 1;
    }
  done
  IMAGE="$(env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_IMAGE)"
  CONTAINER="$(env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_CONTAINER)"
  HOST_PORT="$(env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_HOST_PORT)"
  DATA_ROOT="$(env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_DATA_ROOT)"
  LOG_ROOT="$(env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_LOG_ROOT)"
  DATABASE="$(env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_DATABASE)"
  USERNAME="$(env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_USERNAME)"
  PASSWORD="$(env_value "${ENV_FILE}" TEST_AGENT_CLICKHOUSE_PASSWORD)"
  [[ "${IMAGE}" == "test-agent-clickhouse:26.3.17.56" ]] || { echo "Unexpected ClickHouse image" >&2; exit 1; }
  [[ "${HOST_PORT}" =~ ^[0-9]{1,5}$ ]] && (( HOST_PORT >= 1 && HOST_PORT <= 65535 )) || {
    echo "Invalid ClickHouse host port" >&2; exit 1;
  }
  [[ "${DATA_ROOT}" =~ ^/[A-Za-z0-9._/-]+$ && "${LOG_ROOT}" =~ ^/[A-Za-z0-9._/-]+$ ]] || {
    echo "ClickHouse data/log roots must be absolute paths" >&2; exit 1;
  }
  (( ${#PASSWORD} >= 8 )) || { echo "ClickHouse password must contain at least 8 characters" >&2; exit 1; }
  digest="$(printf '%s' "${PASSWORD}" | openssl dgst -sha256 | awk '{print $NF}')"
  grep -Fq "<password_sha256_hex>${digest}</password_sha256_hex>" "${USERS_CONFIG_FILE}" || {
    echo "ClickHouse password and users config digest do not match" >&2; exit 1;
  }
  grep -Fq "<${USERNAME}>" "${USERS_CONFIG_FILE}" || { echo "ClickHouse user is missing" >&2; exit 1; }
  printf 'ClickHouse configuration validation passed\n'
}

verify_container() {
  require_command docker
  [[ "$(docker inspect -f '{{.State.Running}}' "${CONTAINER}" 2>/dev/null || true)" == "true" ]] || {
    echo "ClickHouse container is not running" >&2; exit 1;
  }
  # 宿主经 Docker DNAT 访问映射端口时，来源地址不保证仍是 127.0.0.1；
  # 本机校验在容器内完成，远端连通性继续由 .4/.114 按白名单独立验证。
  version="$(docker exec "${CONTAINER}" clickhouse-client \
    --user "${USERNAME}" --password "${PASSWORD}" --query 'select version()')"
  [[ "${version}" == "26.3.17.56" ]] || { echo "Unexpected ClickHouse version: ${version}" >&2; exit 1; }
  docker exec "${CONTAINER}" clickhouse-client \
    --user "${USERNAME}" --password "${PASSWORD}" \
    --query "select count() from system.databases where name='${DATABASE}'" | grep -Fxq '1'
  printf 'ClickHouse verification passed: container=%s version=%s database=%s\n' \
    "${CONTAINER}" "${version}" "${DATABASE}"
}

validate_config
case "${ACTION}" in
  validate) exit 0 ;;
  status) require_command docker; docker ps -a --filter "name=^/${CONTAINER}$"; exit 0 ;;
  logs) require_command docker; docker logs --tail 200 "${CONTAINER}"; exit 0 ;;
  stop) require_command docker; docker stop "${CONTAINER}"; exit 0 ;;
  verify) verify_container; exit 0 ;;
esac

require_command docker; require_file "${IMAGE_TAR}"
docker load -i "${IMAGE_TAR}" >/dev/null
[[ "$(docker image inspect -f '{{.Os}}/{{.Architecture}}' "${IMAGE}")" == "linux/amd64" ]] || {
  echo "Loaded ClickHouse image is not linux/amd64" >&2; exit 1;
}
if docker inspect "${CONTAINER}" >/dev/null 2>&1; then
  [[ "${REPLACE_EXISTING}" == "true" ]] || { echo "Container already exists; pass --replace-existing" >&2; exit 1; }
  docker stop "${CONTAINER}" >/dev/null 2>&1 || true
  docker rm "${CONTAINER}" >/dev/null
fi
install -d -m 0750 "${DATA_ROOT}" "${LOG_ROOT}" /data/testagent/config
chown 101:101 "${DATA_ROOT}" "${LOG_ROOT}"
installed_config="/data/testagent/config/clickhouse-users.xml"
install -m 0600 "${USERS_CONFIG_FILE}" "${installed_config}"
docker run -d \
  --name "${CONTAINER}" \
  --hostname "${CONTAINER}" \
  --restart unless-stopped \
  --ulimit nofile=262144:262144 \
  -p "${HOST_PORT}:8123" \
  -v "${DATA_ROOT}:/var/lib/clickhouse" \
  -v "${LOG_ROOT}:/var/log/clickhouse-server" \
  -v "${installed_config}:/etc/clickhouse-server/users.d/testagent-analytics.xml:ro" \
  "${IMAGE}" >/dev/null

for attempt in $(seq 1 90); do
  docker exec "${CONTAINER}" clickhouse-client --query 'select 1' >/dev/null 2>&1 && break
  [[ "${attempt}" -lt 90 ]] || { docker logs --tail 120 "${CONTAINER}" >&2; exit 1; }
  sleep 1
done
docker exec "${CONTAINER}" clickhouse-client \
  --user "${USERNAME}" --password "${PASSWORD}" \
  --query "create database if not exists ${DATABASE}"
verify_container
