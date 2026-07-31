#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTERNAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPOSITORY_ROOT="$(cd "${INTERNAL_DIR}/../.." && pwd)"
ARTIFACT_DIR="${TEST_AGENT_LOBEHUB_ARTIFACT_DIR:-${REPOSITORY_ROOT}/deploy/internal/dist-lobehub-server}"
REDIS_IMAGE="${LOBEHUB_SMOKE_REDIS_IMAGE:-redis:7.4-alpine}"
REDIS_CONTAINER="test-agent-lobehub-smoke-redis"
NETWORK="test-agent-lobehub"
TEMP_ROOT="${TMPDIR:-/tmp}"
TEMP_ROOT="${TEMP_ROOT%/}"
RUNTIME_CONTAINERS=(
  test-agent-lobehub-app
  test-agent-lobehub-rustfs
  test-agent-lobehub-db
)
SMOKE_ROOT=""
NETWORK_CREATED=false

cleanup() {
  docker rm -f "${RUNTIME_CONTAINERS[@]}" "${REDIS_CONTAINER}" >/dev/null 2>&1 || true
  if [[ "${NETWORK_CREATED}" == true ]]; then
    docker network rm "${NETWORK}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${SMOKE_ROOT}" && "${SMOKE_ROOT}" == "${TEMP_ROOT}/"* ]]; then
    rm -rf "${SMOKE_ROOT}"
  fi
}
trap cleanup EXIT

release_value() {
  local key="$1"
  awk -F= -v wanted="${key}" \
    '$1 == wanted { print substr($0, index($0, "=") + 1); found=1; exit } END { if (!found) exit 1 }' \
    "${ARTIFACT_DIR}/release.env"
}

require_file() {
  [[ -f "$1" ]] || { echo "Required runtime smoke artifact not found: $1" >&2; exit 1; }
}

require_free_container_name() {
  local name="$1"
  if docker container inspect "${name}" >/dev/null 2>&1; then
    echo "Refusing to replace an existing container during runtime smoke: ${name}" >&2
    exit 1
  fi
}

require_free_tcp_port() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "TCP port ${port} is already in use; runtime smoke did not start" >&2
    exit 1
  fi
  if command -v ss >/dev/null 2>&1 && ss -ltn | awk '{print $4}' | grep -Eq "(^|:)${port}$"; then
    echo "TCP port ${port} is already in use; runtime smoke did not start" >&2
    exit 1
  fi
}

http_code() {
  local output_file="$1"
  shift
  curl -sS --max-time 20 -o "${output_file}" -w '%{http_code}' "$@"
}

assert_http_code() {
  local expected="$1" actual="$2" body_file="$3" description="$4"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "${description}: expected HTTP ${expected}, got ${actual}" >&2
    sed -n '1,80p' "${body_file}" >&2 || true
    exit 1
  fi
}

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "curl is required" >&2; exit 1; }
command -v openssl >/dev/null 2>&1 || { echo "openssl is required" >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker daemon is not available" >&2; exit 1; }

require_file "${ARTIFACT_DIR}/release.env"
require_file "${ARTIFACT_DIR}/bin/mc-linux-amd64"
for image_key in LOBEHUB_APP_IMAGE LOBEHUB_PARADEDB_IMAGE LOBEHUB_RUSTFS_IMAGE; do
  image_ref="$(release_value "${image_key}")"
  expected_id="$(release_value "${image_key}_ID")"
  actual_id="$(docker image inspect -f '{{.Id}}' "${image_ref}" 2>/dev/null || true)"
  [[ "${actual_id}" == "${expected_id}" ]] || {
    echo "Runtime smoke requires the verified image ${image_ref} (${expected_id}) to be loaded" >&2
    exit 1
  }
done
docker image inspect "${REDIS_IMAGE}" >/dev/null 2>&1 || {
  echo "Runtime smoke requires a local ${REDIS_IMAGE} image; enterprise deployment still reuses shared Redis" >&2
  exit 1
}

for name in "${RUNTIME_CONTAINERS[@]}" "${REDIS_CONTAINER}"; do
  require_free_container_name "${name}"
done
for port in 3210 9000 9001; do
  require_free_tcp_port "${port}"
done

SMOKE_ROOT="$(mktemp -d "${TEMP_ROOT}/test-agent-lobehub-runtime-smoke.XXXXXX")"
BASE_DIR="${SMOKE_ROOT}/testagent"
ENV_FILE="${BASE_DIR}/config/lobehub.env"
TEST_BIN_DIR="${SMOKE_ROOT}/bin"
install -d -m 0700 "${TEST_BIN_DIR}" "${BASE_DIR}/config" \
  "${BASE_DIR}/lobehub/bin" "${BASE_DIR}/lobehub/release"
install -m 0755 "${ARTIFACT_DIR}/bin/mc-linux-amd64" "${BASE_DIR}/lobehub/bin/mc-linux-amd64"
install -m 0644 "${ARTIFACT_DIR}/release.env" "${BASE_DIR}/lobehub/release/release.env"

if ! docker network inspect "${NETWORK}" >/dev/null 2>&1; then
  docker network create "${NETWORK}" >/dev/null
  NETWORK_CREATED=true
fi

redis_admin_password="$(openssl rand -hex 32)"
redis_password="$(openssl rand -hex 32)"
docker run -d --name "${REDIS_CONTAINER}" --network "${NETWORK}" \
  "${REDIS_IMAGE}" redis-server --appendonly no --save '' \
  --requirepass "${redis_admin_password}" >/dev/null
for attempt in {1..30}; do
  if docker exec -e "REDISCLI_AUTH=${redis_admin_password}" "${REDIS_CONTAINER}" \
    redis-cli --no-auth-warning PING 2>/dev/null | grep -Fx PONG >/dev/null; then
    break
  fi
  [[ "${attempt}" -lt 30 ]] || { echo "Smoke Redis did not become ready" >&2; exit 1; }
  sleep 1
done
docker exec -e "REDISCLI_AUTH=${redis_admin_password}" "${REDIS_CONTAINER}" \
  redis-cli --no-auth-warning ACL SETUSER lobehub reset on \
  ">${redis_password}" '~lobehub:app:*' '&lobehub:app:*' \
  '+@all' '-acl' '-config' '-module' '-flushall' '-flushdb' >/dev/null

# The production preflight intentionally requires redis-cli on the host. This wrapper only lets the
# same command contract target the isolated smoke Redis from macOS Docker Desktop.
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'exec docker exec -i -e "REDISCLI_AUTH=${REDISCLI_AUTH:-}" "${LOBEHUB_SMOKE_REDIS_CONTAINER:?}" redis-cli "$@"' \
  >"${TEST_BIN_DIR}/redis-cli"
chmod 0755 "${TEST_BIN_DIR}/redis-cli"

# The enterprise systemd unit runs as root and performs the real UID 10001 ownership change. Docker
# Desktop bind mounts do not permit an unprivileged macOS process to chown to that Linux UID.
if [[ "$(uname -s)" == Darwin ]]; then
  printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${TEST_BIN_DIR}/chown"
  chmod 0755 "${TEST_BIN_DIR}/chown"
  echo "Runtime smoke: macOS Docker Desktop uses a chown shim; Linux deployment keeps the real UID 10001 check."

  mc_image="$(release_value LOBEHUB_MC_SOURCE_IMAGE)"
  docker image inspect "${mc_image}" >/dev/null 2>&1 || {
    echo "macOS runtime smoke requires the already-pulled build image ${mc_image}" >&2
    exit 1
  }
  # The release binary is Linux/amd64 and cannot execute directly on macOS. Keep production behavior
  # unchanged while running that exact upstream image through Docker for the local smoke only.
  printf '%s\n' \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'exec docker run --rm -i --network test-agent-lobehub -e "MC_HOST_lobehub=${MC_HOST_lobehub:-}" "${LOBEHUB_SMOKE_MC_IMAGE:?}" "$@"' \
    >"${BASE_DIR}/lobehub/bin/mc-linux-amd64"
  chmod 0755 "${BASE_DIR}/lobehub/bin/mc-linux-amd64"
  export LOBEHUB_SMOKE_MC_IMAGE="${mc_image}"
  mc_endpoint="test-agent-lobehub-rustfs"
else
  mc_endpoint="127.0.0.1"
fi

postgres_password="$(openssl rand -hex 32)"
rustfs_access_key="$(openssl rand -hex 16)"
rustfs_secret_key="$(openssl rand -hex 32)"
auth_secret="$(openssl rand -hex 32)"
key_vault_secret="$(openssl rand -base64 32 | tr -d '\n')"
scheduler_secret="$(openssl rand -hex 32)"
model_grant_secret="$(openssl rand -base64 32 | tr -d '\n')"
hmac_secret="$(openssl rand -hex 32)"

printf '%s\n' \
  "LOBEHUB_APP_IMAGE=$(release_value LOBEHUB_APP_IMAGE)" \
  "LOBEHUB_PARADEDB_IMAGE=$(release_value LOBEHUB_PARADEDB_IMAGE)" \
  "LOBEHUB_RUSTFS_IMAGE=$(release_value LOBEHUB_RUSTFS_IMAGE)" \
  'POSTGRES_DB=lobehub' \
  'POSTGRES_USER=lobehub' \
  "POSTGRES_PASSWORD=${postgres_password}" \
  "DATABASE_URL=postgresql://lobehub:${postgres_password}@test-agent-lobehub-db:5432/lobehub" \
  'DATABASE_DRIVER=node' \
  "LOBEHUB_REDIS_HOST=${REDIS_CONTAINER}" \
  'LOBEHUB_REDIS_PORT=6379' \
  'LOBEHUB_REDIS_USERNAME=lobehub' \
  "LOBEHUB_REDIS_PASSWORD=${redis_password}" \
  "REDIS_URL=redis://lobehub:${redis_password}@${REDIS_CONTAINER}:6379/0" \
  'REDIS_PREFIX=lobehub:app' \
  "RUSTFS_ACCESS_KEY=${rustfs_access_key}" \
  "RUSTFS_SECRET_KEY=${rustfs_secret_key}" \
  'LOBEHUB_S3_BUCKET=lobehub-private' \
  'S3_ENDPOINT=http://test-agent-lobehub-rustfs:9000' \
  'S3_BUCKET=lobehub-private' \
  "S3_ACCESS_KEY_ID=${rustfs_access_key}" \
  "S3_SECRET_ACCESS_KEY=${rustfs_secret_key}" \
  'S3_ENABLE_PATH_STYLE=1' \
  'S3_SET_ACL=0' \
  "MC_HOST_lobehub=http://${rustfs_access_key}:${rustfs_secret_key}@${mc_endpoint}:9000" \
  'APP_URL=http://127.0.0.1:3210' \
  'INTERNAL_APP_URL=http://test-agent-lobehub-app:3210' \
  'LOBEHUB_APP_BIND_ADDRESS=127.0.0.1' \
  "AUTH_SECRET=${auth_secret}" \
  "KEY_VAULTS_SECRET=${key_vault_secret}" \
  "ENTERPRISE_INTERNAL_SCHEDULER_SECRET=${scheduler_secret}" \
  'PLATFORM_SSO_ENABLED=1' \
  'LOBEHUB_ENTERPRISE_OFFLINE=1' \
  'AGENT_RUNTIME_MODE=local' \
  'PLATFORM_LAUNCH_URL=http://host.docker.internal:8080/lobehub/launch' \
  'PLATFORM_SSO_REDEEM_URL=http://host.docker.internal:8080/api/internal/platform/lobehub-sso/tickets/redeem' \
  'PLATFORM_SSO_REVOKE_URL=http://host.docker.internal:8080/api/internal/platform/lobehub-sso/grants/revoke' \
  'PLATFORM_MODEL_GATEWAY_BASE_URL=http://host.docker.internal:8080/api/internal/platform/model-gateway/v1' \
  "PLATFORM_SSO_HMAC_SECRET=${hmac_secret}" \
  "PLATFORM_MODEL_GRANT_ENCRYPTION_KEY=${model_grant_secret}" \
  'TELEMETRY_DISABLED=1' \
  'LOBEHUB_DEVICE_EXECUTION_MODE=disabled' \
  >"${ENV_FILE}"
chmod 0600 "${ENV_FILE}"

export TEST_AGENT_BASE_DIR="${BASE_DIR}"
export LOBEHUB_ENV_FILE="${ENV_FILE}"
export LOBEHUB_SMOKE_REDIS_CONTAINER="${REDIS_CONTAINER}"
export PATH="${TEST_BIN_DIR}:${PATH}"

"${INTERNAL_DIR}/lobehub-docker.sh" validate-config
"${INTERNAL_DIR}/lobehub-docker.sh" start
"${INTERNAL_DIR}/lobehub-docker.sh" status
"${INTERNAL_DIR}/lobehub-docker.sh" verify-deployment \
  | grep -Fx 'LobeHub deployment verification passed'

body_file="${SMOKE_ROOT}/response.body"
code="$(http_code "${body_file}" http://127.0.0.1:3210/)"
assert_http_code 307 "${code}" "${body_file}" 'LobeHub root redirect'

code="$(http_code "${body_file}" -X POST http://127.0.0.1:3210/api/workflows/task/schedule-dispatch)"
assert_http_code 403 "${code}" "${body_file}" 'Public workflow endpoint offline block'

code="$(http_code "${body_file}" -X POST http://127.0.0.1:3210/api/agent/enterprise/schedule-dispatch)"
assert_http_code 401 "${code}" "${body_file}" 'Internal scheduler authentication'

code="$(http_code "${body_file}" -X POST \
  -H "Authorization: Bearer ${scheduler_secret}" \
  http://127.0.0.1:3210/api/agent/enterprise/schedule-dispatch)"
assert_http_code 200 "${code}" "${body_file}" 'Internal offline scheduler dispatch'

db_env="$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' test-agent-lobehub-db)"
rustfs_env="$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' test-agent-lobehub-rustfs)"
app_env="$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' test-agent-lobehub-app)"
for forbidden in PLATFORM_SSO_HMAC_SECRET RUSTFS_SECRET_KEY ENTERPRISE_INTERNAL_SCHEDULER_SECRET; do
  [[ "${db_env}" != *"${forbidden}="* ]] || {
    echo "Database container unexpectedly received ${forbidden}" >&2
    exit 1
  }
done
for forbidden in POSTGRES_PASSWORD PLATFORM_SSO_HMAC_SECRET ENTERPRISE_INTERNAL_SCHEDULER_SECRET; do
  [[ "${rustfs_env}" != *"${forbidden}="* ]] || {
    echo "RustFS container unexpectedly received ${forbidden}" >&2
    exit 1
  }
done
for forbidden in POSTGRES_PASSWORD RUSTFS_ACCESS_KEY RUSTFS_SECRET_KEY MC_HOST_lobehub; do
  [[ "${app_env}" != *"${forbidden}="* ]] || {
    echo "LobeHub app container unexpectedly received control-plane variable ${forbidden}" >&2
    exit 1
  }
done
[[ "${app_env}" == *'AGENT_RUNTIME_MODE=local'* ]] || {
  echo "LobeHub app did not receive the offline local scheduler mode" >&2
  exit 1
}

export MC_HOST_lobehub="http://${rustfs_access_key}:${rustfs_secret_key}@${mc_endpoint}:9000"
bucket_policy="$(${BASE_DIR}/lobehub/bin/mc-linux-amd64 anonymous get lobehub/lobehub-private 2>&1)"
printf '%s\n' "${bucket_policy}" | grep -Eiq 'private|none' || {
  echo "RustFS bucket is not reported as private: ${bucket_policy}" >&2
  exit 1
}

# 在真实 PostgreSQL/RustFS 中写入证明数据，再做停机快照与恢复演练。
docker exec test-agent-lobehub-db psql -U lobehub -d lobehub -v ON_ERROR_STOP=1 \
  -c 'create table if not exists enterprise_backup_proof (value text primary key)' \
  -c "insert into enterprise_backup_proof(value) values ('database-before-backup') on conflict do nothing" \
  >/dev/null
printf 'object-before-backup' \
  | "${BASE_DIR}/lobehub/bin/mc-linux-amd64" pipe \
    lobehub/lobehub-private/enterprise-backup-proof.txt >/dev/null

"${INTERNAL_DIR}/lobehub-docker.sh" stop
BACKUP_DIR="${SMOKE_ROOT}/backups"
BACKUP_TEST_BIN="${SMOKE_ROOT}/backup-bin"
mkdir -p "${BACKUP_TEST_BIN}"
if [[ "$(uname -s)" == Darwin ]]; then
  printf '%s\n' \
    '#!/usr/bin/env bash' \
    'if [[ "${1:-}" == -u ]]; then echo 0; exit 0; fi' \
    'exec /usr/bin/id "$@"' \
    >"${BACKUP_TEST_BIN}/id"
  chmod 0755 "${BACKUP_TEST_BIN}/id"
fi
PATH="${BACKUP_TEST_BIN}:${PATH}" "${INTERNAL_DIR}/lobehub-backup.sh" create \
  --output-dir "${BACKUP_DIR}" >/dev/null
backup_archive="$(find "${BACKUP_DIR}" -maxdepth 1 -type f \
  -name 'test-agent-lobehub-backup-*.tar.gz' -print -quit)"
[[ -n "${backup_archive}" ]] || { echo 'Runtime smoke backup archive was not created' >&2; exit 1; }
PATH="${BACKUP_TEST_BIN}:${PATH}" "${INTERNAL_DIR}/lobehub-backup.sh" verify \
  --archive "${backup_archive}" >/dev/null

# 只移走冒烟临时目录，不删除任何宿主现有数据。
mv "${BASE_DIR}/lobehub/paradedb" "${SMOKE_ROOT}/discarded-paradedb"
mv "${BASE_DIR}/lobehub/rustfs" "${SMOKE_ROOT}/discarded-rustfs"
PATH="${BACKUP_TEST_BIN}:${PATH}" "${INTERNAL_DIR}/lobehub-backup.sh" restore \
  --archive "${backup_archive}" --confirm-restore >/dev/null
"${INTERNAL_DIR}/lobehub-docker.sh" start
"${INTERNAL_DIR}/lobehub-docker.sh" verify-deployment >/dev/null

database_value="$(docker exec test-agent-lobehub-db psql -U lobehub -d lobehub -Atqc \
  "select value from enterprise_backup_proof where value = 'database-before-backup'")"
[[ "${database_value}" == database-before-backup ]] || {
  echo 'Restored ParadeDB did not retain the runtime smoke proof row' >&2
  exit 1
}
object_value="$("${BASE_DIR}/lobehub/bin/mc-linux-amd64" cat \
  lobehub/lobehub-private/enterprise-backup-proof.txt)"
[[ "${object_value}" == object-before-backup ]] || {
  echo "Restored RustFS object mismatch; received ${#object_value} bytes" >&2
  "${BASE_DIR}/lobehub/bin/mc-linux-amd64" stat \
    lobehub/lobehub-private/enterprise-backup-proof.txt >&2 || true
  exit 1
}

echo "LobeHub real-image runtime smoke test passed"
