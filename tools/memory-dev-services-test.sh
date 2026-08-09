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
exit 0
SCRIPT
cat >"${TMP_ROOT}/bin/curl" <<'SCRIPT'
#!/usr/bin/env bash
printf '{"data":{"status":"UP","rawMessageCount":0}}\n'
SCRIPT
chmod +x "${TMP_ROOT}/bin/docker" "${TMP_ROOT}/bin/curl"

export PATH="${TMP_ROOT}/bin:${PATH}"
export MEMORY_TEST_DOCKER_CALLS="${DOCKER_CALLS}"
export TEST_AGENT_MEMORY_DEV_ENV_FILE="${TMP_ROOT}/state/memory-dev.env"
export TEST_AGENT_MEMORY_BACKEND_ENV_FILE="${TMP_ROOT}/state/memory-backend.env"
export TEST_AGENT_MEMORY_BACKEND_PORT=18080

prepare_output="$(bash "${ROOT_DIR}/tools/memory-dev-services.sh" prepare)"
api_key="$(awk -F= '$1 == "TEST_AGENT_MEMORY_SERVICE_API_KEY" {print $2}' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}")"
[[ "${#api_key}" -ge 32 ]] || { echo "generated memory API key is too short" >&2; exit 1; }
[[ "${prepare_output}" != *"${api_key}"* ]] || { echo "memory API key leaked to output" >&2; exit 1; }
grep -Fq 'TEST_AGENT_MEMORY_ENABLED=true' "${TEST_AGENT_MEMORY_BACKEND_ENV_FILE}"
grep -Fq 'TEST_AGENT_MEMORY_SERVICE_URL=http://127.0.0.1:18888' "${TEST_AGENT_MEMORY_BACKEND_ENV_FILE}"
grep -Fq 'TEST_AGENT_MEMORY_SERVICE_EXTRACTION_GATEWAY_URL=http://host.docker.internal:18080/api/internal/platform/model-gateway/v1' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}"
if stat -f '%Lp' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}" >/dev/null 2>&1; then
  [[ "$(stat -f '%Lp' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}")" == "600" ]]
else
  [[ "$(stat -c '%a' "${TEST_AGENT_MEMORY_DEV_ENV_FILE}")" == "600" ]]
fi

bash "${ROOT_DIR}/tools/memory-dev-services.sh" build >/dev/null
bash "${ROOT_DIR}/tools/memory-dev-services.sh" start >/dev/null
bash "${ROOT_DIR}/tools/memory-dev-services.sh" stop >/dev/null
grep -Fq 'pull memory-postgres' "${DOCKER_CALLS}"
grep -Fq 'build memory-service' "${DOCKER_CALLS}"
grep -Fq 'up -d --no-build memory-postgres memory-service' "${DOCKER_CALLS}"
if grep -Fq -- '--wait memory-postgres memory-service' "${DOCKER_CALLS}"; then
  echo "memory helper must use authenticated readiness instead of compose --wait" >&2
  exit 1
fi
grep -Fq 'stop --timeout 60 memory-service memory-postgres' "${DOCKER_CALLS}"
if grep -Eq '(^| )down( |$)' "${DOCKER_CALLS}"; then
  echo "memory helper must not destroy the Compose project or volumes" >&2
  exit 1
fi

echo "Memory development service helper checks passed."
