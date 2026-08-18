#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTERNAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=../deploy-node-common.sh
source "${INTERNAL_DIR}/deploy-node-common.sh"

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-runtime-secret-test.XXXXXX")"
cleanup() { rm -rf "${TMP_ROOT}"; }
trap cleanup EXIT

marker='__PRESERVE_FROM_INSTALLED_BACKEND_ENV__'
prepared="${TMP_ROOT}/prepared.env"
installed="${TMP_ROOT}/installed.env"

printf '%s\n' \
  "TEST_AGENT_ANALYTICS_CLICKHOUSE_PASSWORD=${marker}" \
  "TEST_AGENT_MEMORY_SERVICE_API_KEY=${marker}" \
  "TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET=${marker}" \
  >"${prepared}"
printf '%s\n' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_PASSWORD=clickhouse-secret-123' \
  'TEST_AGENT_MEMORY_SERVICE_API_KEY=memory-service-key-123456789012345' \
  'TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET=memory-hmac-secret-123456789012345' \
  >"${installed}"
chmod 0600 "${prepared}" "${installed}"

hydrate_preserved_env_value "${prepared}" "${installed}" \
  TEST_AGENT_ANALYTICS_CLICKHOUSE_PASSWORD "${marker}" 8
hydrate_preserved_env_value "${prepared}" "${installed}" \
  TEST_AGENT_MEMORY_SERVICE_API_KEY "${marker}" 32
hydrate_preserved_env_value "${prepared}" "${installed}" \
  TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET "${marker}" 32

! grep -qF "${marker}" "${prepared}"
[[ "$(dotenv_value "${prepared}" TEST_AGENT_ANALYTICS_CLICKHOUSE_PASSWORD)" == \
  'clickhouse-secret-123' ]]
[[ "$(dotenv_value "${prepared}" TEST_AGENT_MEMORY_SERVICE_API_KEY)" == \
  'memory-service-key-123456789012345' ]]
[[ "$(dotenv_value "${prepared}" TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET)" == \
  'memory-hmac-secret-123456789012345' ]]

printf 'TEST_AGENT_MEMORY_SERVICE_API_KEY=short\n' >"${installed}"
replace_env_value "${prepared}" TEST_AGENT_MEMORY_SERVICE_API_KEY "${marker}"
if hydrate_preserved_env_value "${prepared}" "${installed}" \
  TEST_AGENT_MEMORY_SERVICE_API_KEY "${marker}" 32 2>/dev/null; then
  echo 'Short installed secret unexpectedly passed' >&2
  exit 1
fi

printf 'Runtime secret carry-forward checks passed.\n'
