#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-memory-dev.XXXXXX")"
trap 'rm -rf "${TMP_ROOT}"' EXIT

mkdir -p "${TMP_ROOT}/bin" "${TMP_ROOT}/state"
DOCKER_CALLS="${TMP_ROOT}/docker.calls"
cat >"${TMP_ROOT}/bin/docker" <<'SCRIPT'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"${MEMORY_TEST_DOCKER_CALLS}"
[[ "$*" == *"psql"* ]] && printf '1\n'
exit 0
SCRIPT
cat >"${TMP_ROOT}/bin/curl" <<'SCRIPT'
#!/usr/bin/env bash
printf '{"status":"UP","rawMessageCount":0,"model":"BAAI/bge-small-zh-v1.5"}\n'
SCRIPT
chmod +x "${TMP_ROOT}/bin/docker" "${TMP_ROOT}/bin/curl"

export PATH="${TMP_ROOT}/bin:${PATH}"
export MEMORY_TEST_DOCKER_CALLS="${DOCKER_CALLS}"
export TEST_AGENT_MEMORY_DEV_ENV_FILE="${TMP_ROOT}/state/memory-dev.env"
export TEST_AGENT_MEMORY_BACKEND_ENV_FILE="${TMP_ROOT}/state/memory-backend.env"
export TEST_AGENT_MEMORY_BACKEND_PORT=18080
export TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_MODEL_ID=enterprise-embedding
export TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_DIMENSION=1024
export TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_FINGERPRINT=sha256:1234567890abcdef

prepare_output="$(bash "${ROOT_DIR}/tools/memory-dev-services.sh" prepare)"
api_key="$(awk -F= '$1 == "TEST_AGENT_MEMORY_SERVICE_API_KEY" {print $2}' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}")"
hmac_secret="$(awk -F= '$1 == "TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET" {print $2}' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}")"
embedding_key="$(awk -F= '$1 == "TEST_AGENT_EMBEDDING_API_KEY" {print $2}' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}")"
[[ "${#api_key}" -ge 32 ]] || { echo "generated memory API key is too short" >&2; exit 1; }
[[ "${#hmac_secret}" -ge 32 ]] || { echo "generated memory HMAC secret is too short" >&2; exit 1; }
[[ "${#embedding_key}" -ge 32 ]] || { echo "generated embedding API key is too short" >&2; exit 1; }
for secret in "${api_key}" "${hmac_secret}" "${embedding_key}"; do
  [[ "${prepare_output}" != *"${secret}"* ]] || { echo "memory secret leaked to output" >&2; exit 1; }
done
grep -Fq 'TEST_AGENT_MEMORY_ENABLED=true' "${TEST_AGENT_MEMORY_BACKEND_ENV_FILE}"
grep -Fq 'TEST_AGENT_MEMORY_SERVICE_URL=http://127.0.0.1:18888' "${TEST_AGENT_MEMORY_BACKEND_ENV_FILE}"
grep -Fq 'TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_CLIENT_ID=mem0-cluster' "${TEST_AGENT_MEMORY_BACKEND_ENV_FILE}"
grep -Fq 'TEST_AGENT_MEMORY_SERVICE_MODEL_GATEWAY_URL=http://host.docker.internal:18080/api/internal/platform/model-gateway/v1' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}"
grep -Fq 'TEST_AGENT_MEMORY_POSTGRES_VOLUME=test-agent-memory-dev-pgvector-v1' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}"
grep -Fq 'TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_MODEL_ID=enterprise-embedding' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}"
grep -Fq 'TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_DIMENSION=1024' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}"
grep -Fq 'TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_FINGERPRINT=sha256:1234567890abcdef' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}"
if stat -f '%Lp' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}" >/dev/null 2>&1; then
  [[ "$(stat -f '%Lp' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}")" == "600" ]]
else
  [[ "$(stat -c '%a' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}")" == "600" ]]
fi

unset TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_MODEL_ID
unset TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_DIMENSION
unset TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_FINGERPRINT
if TEST_AGENT_MEMORY_DEV_ENV_FILE="${TMP_ROOT}/state/partial.env" \
  TEST_AGENT_MEMORY_BACKEND_ENV_FILE="${TMP_ROOT}/state/partial-backend.env" \
  TEST_AGENT_MEMORY_ENTERPRISE_EMBEDDING_MODEL_ID=partial-model \
  bash "${ROOT_DIR}/tools/memory-dev-services.sh" prepare >/dev/null 2>&1; then
  echo "partial enterprise embedding identity must be rejected" >&2
  exit 1
fi

bash "${ROOT_DIR}/tools/memory-dev-services.sh" build >/dev/null
bash "${ROOT_DIR}/tools/memory-dev-services.sh" start >/dev/null
bash "${ROOT_DIR}/tools/memory-dev-services.sh" stop >/dev/null
grep -Fq 'pull memory-postgres memory-lb' "${DOCKER_CALLS}"
grep -Fq 'build memory-migrate memory-service-1 embedding-service' "${DOCKER_CALLS}"
grep -Fq 'up -d --no-build memory-postgres' "${DOCKER_CALLS}"
grep -Fq 'exec -T memory-postgres sh -ec' "${DOCKER_CALLS}"
grep -Fq 'up -d --no-build embedding-service' "${DOCKER_CALLS}"
grep -Fq 'up --no-build --abort-on-container-exit --exit-code-from memory-migrate memory-migrate' "${DOCKER_CALLS}"
grep -Fq 'up -d --no-build memory-service-1 memory-service-2 memory-service-3 memory-lb' "${DOCKER_CALLS}"
if grep -Fq -- '--wait memory-postgres' "${DOCKER_CALLS}"; then
  echo "memory helper must use authenticated readiness instead of compose --wait" >&2
  exit 1
fi
grep -Fq 'stop --timeout 60 memory-lb memory-service-1 memory-service-2 memory-service-3 embedding-service memory-postgres' "${DOCKER_CALLS}"
if grep -Eq '(^| )down( |$)' "${DOCKER_CALLS}"; then
  echo "memory helper must not destroy the Compose project or volumes" >&2
  exit 1
fi

echo "Memory development service helper checks passed."
