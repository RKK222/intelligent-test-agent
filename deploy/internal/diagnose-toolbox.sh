#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${TEST_AGENT_TOOLBOX_ENV_FILE:-/data/testagent/config/toolbox.env}"
FRONTEND_ORIGIN="${TEST_AGENT_TOOLBOX_FRONTEND_ORIGIN:-}"

env_value() {
  local key="$1"
  sed -n "s/^${key}=//p" "${ENV_FILE}" | tail -n 1 | tr -d '\r'
}

[[ -f "${ENV_FILE}" ]] || { echo "Toolbox env not found: ${ENV_FILE}" >&2; exit 1; }
BIND_ADDRESS="$(env_value TEST_AGENT_TOOLBOX_BIND_ADDRESS)"
[[ -n "${BIND_ADDRESS}" && "${BIND_ADDRESS}" != "0.0.0.0" ]] || BIND_ADDRESS=127.0.0.1

for container in test-agent-it-tools test-agent-omni-tools; do
  status="$(docker inspect -f '{{.State.Health.Status}}' "${container}")"
  [[ "${status}" == "healthy" ]] || { echo "${container} is ${status}" >&2; exit 1; }
done

curl -fsS "http://${BIND_ADDRESS}:18120/healthz" >/dev/null
curl -fsS "http://${BIND_ADDRESS}:18121/healthz" >/dev/null
curl -fsSI "http://${BIND_ADDRESS}:18120/token-generator" >/dev/null
curl -fsSI "http://${BIND_ADDRESS}:18121/audio/change-speed" >/dev/null

if [[ -n "${FRONTEND_ORIGIN}" ]]; then
  FRONTEND_ORIGIN="${FRONTEND_ORIGIN%/}"
  curl -fsSI "${FRONTEND_ORIGIN}/toolbox/apps/it-tools/token-generator" \
    | tr -d '\r' | grep -qi '^content-security-policy:'
  curl -fsSI "${FRONTEND_ORIGIN}/toolbox/apps/omni-tools/audio/change-speed" \
    | tr -d '\r' | grep -qi '^cross-origin-embedder-policy: require-corp'
  root_headers="$(curl -sSI "${FRONTEND_ORIGIN}/toolbox/apps/it-tools" | tr -d '\r')"
  grep -Eq '^HTTP/[^ ]+ 308' <<<"${root_headers}"
  grep -qi '^location: /toolbox$' <<<"${root_headers}"
fi

echo "Toolbox containers, health endpoints and deep links verified"
