#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${TEST_AGENT_TOOLBOX_ENV_FILE:-/data/testagent/config/toolbox.env}"
FRONTEND_ORIGIN="${TEST_AGENT_TOOLBOX_FRONTEND_ORIGIN:-}"

env_value() {
  local key="$1" line parsed_key value found=""
  # 与部署脚本保持同一 dotenv 兼容范围，避免带引号的合法镜像配置被误判为版本漂移。
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    [[ "${line}" == export\ * ]] && line="${line#export }"
    [[ "${line}" == *=* ]] || continue
    parsed_key="${line%%=*}"
    parsed_key="${parsed_key//[[:space:]]/}"
    [[ "${parsed_key}" == "${key}" ]] || continue
    value="${line#*=}"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    found="${value}"
  done <"${ENV_FILE}"
  printf '%s' "${found}"
}

require_platform_image() {
  local variable_name="$1" image_reference="$2" repository="$3" version="$4"
  if [[ "${image_reference}" != "${repository}:${version}" \
      && "${image_reference}" != */"${repository}:${version}" ]]; then
    echo "${variable_name} must end with ${repository}:${version}: ${image_reference}" >&2
    exit 1
  fi
}

verify_container_image() {
  local container="$1" expected="$2" actual
  actual="$(docker inspect -f '{{.Config.Image}}' "${container}")"
  [[ "${actual}" == "${expected}" ]] || {
    echo "${container} uses ${actual}, expected ${expected}" >&2
    exit 1
  }
}

[[ -f "${ENV_FILE}" ]] || { echo "Toolbox env not found: ${ENV_FILE}" >&2; exit 1; }
BIND_ADDRESS="$(env_value TEST_AGENT_TOOLBOX_BIND_ADDRESS)"
[[ -n "${BIND_ADDRESS}" && "${BIND_ADDRESS}" != "0.0.0.0" ]] || BIND_ADDRESS=127.0.0.1
IT_IMAGE="$(env_value TEST_AGENT_TOOLBOX_IT_TOOLS_IMAGE)"
OMNI_IMAGE="$(env_value TEST_AGENT_TOOLBOX_OMNI_TOOLS_IMAGE)"
IT_IMAGE="${IT_IMAGE:-test-agent/it-tools:2024.10.22-7ca5933-platform.2}"
OMNI_IMAGE="${OMNI_IMAGE:-test-agent/omni-tools:0.6.0-platform.1}"
require_platform_image "TEST_AGENT_TOOLBOX_IT_TOOLS_IMAGE" "${IT_IMAGE}" "test-agent/it-tools" "2024.10.22-7ca5933-platform.2"
require_platform_image "TEST_AGENT_TOOLBOX_OMNI_TOOLS_IMAGE" "${OMNI_IMAGE}" "test-agent/omni-tools" "0.6.0-platform.1"

for container in test-agent-it-tools test-agent-omni-tools; do
  status="$(docker inspect -f '{{.State.Health.Status}}' "${container}")"
  [[ "${status}" == "healthy" ]] || { echo "${container} is ${status}" >&2; exit 1; }
done
verify_container_image test-agent-it-tools "${IT_IMAGE}"
verify_container_image test-agent-omni-tools "${OMNI_IMAGE}"

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
