#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${TEST_AGENT_TOOLBOX_ENV_FILE:-/data/testagent/config/toolbox.env}"
ACTION="${1:-deploy}"

usage() {
  echo "Usage: $0 [deploy|status|rollback|stop]" >&2
}

load_dotenv() {
  local file="$1" line key value
  [[ -f "${file}" ]] || { echo "Toolbox env not found: ${file}" >&2; exit 1; }
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* || "${line}" != *=* ]] && continue
    key="${line%%=*}"
    value="${line#*=}"
    key="${key//[[:space:]]/}"
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    [[ "${value}" == \"*\" && "${value}" == *\" ]] && value="${value:1:${#value}-2}"
    if [[ -z "${!key+x}" ]]; then
      printf -v "${key}" '%s' "${value}"
      export "${key}"
    fi
  done <"${file}"
}

load_dotenv "${ENV_FILE}"

IT_IMAGE="${TEST_AGENT_TOOLBOX_IT_TOOLS_IMAGE:-test-agent/it-tools:2024.10.22-7ca5933-platform.1}"
OMNI_IMAGE="${TEST_AGENT_TOOLBOX_OMNI_TOOLS_IMAGE:-test-agent/omni-tools:0.6.0-platform.1}"
BIND_ADDRESS="${TEST_AGENT_TOOLBOX_BIND_ADDRESS:-127.0.0.1}"
NETWORK="${TEST_AGENT_TOOLBOX_NETWORK:-test-agent-toolbox}"
ARTIFACT_DIR="${TEST_AGENT_TOOLBOX_ARTIFACT_DIR:-/data/testagent/dist}"
IT_TAR="${TEST_AGENT_TOOLBOX_IT_TOOLS_TAR:-${ARTIFACT_DIR}/test-agent_it-tools_2024.10.22-7ca5933-platform.1-linux-amd64.tar}"
OMNI_TAR="${TEST_AGENT_TOOLBOX_OMNI_TOOLS_TAR:-${ARTIFACT_DIR}/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar}"
IT_CONTAINER="test-agent-it-tools"
OMNI_CONTAINER="test-agent-omni-tools"
IT_ROLLBACK_IMAGE="test-agent/it-tools:toolbox-rollback"
OMNI_ROLLBACK_IMAGE="test-agent/omni-tools:toolbox-rollback"
ROLLBACK_AVAILABLE=0

[[ "${BIND_ADDRESS}" =~ ^[0-9a-fA-F:.]+$ ]] || { echo "Invalid bind address: ${BIND_ADDRESS}" >&2; exit 1; }
[[ "${NETWORK}" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo "Invalid Docker network: ${NETWORK}" >&2; exit 1; }

ensure_network() {
  local driver internal masquerade
  if docker network inspect "${NETWORK}" >/dev/null 2>&1; then
    driver="$(docker network inspect -f '{{.Driver}}' "${NETWORK}")"
    internal="$(docker network inspect -f '{{.Internal}}' "${NETWORK}")"
    masquerade="$(docker network inspect -f '{{index .Options "com.docker.network.bridge.enable_ip_masquerade"}}' "${NETWORK}")"
    [[ "${driver}" == "bridge" && "${internal}" == "false" && "${masquerade}" == "false" ]] || {
      echo "Existing Docker network must be a dedicated bridge with IP masquerade disabled: ${NETWORK}" >&2
      exit 1
    }
  else
    # --internal 会同时阻断独立工具节点的宿主端口发布；关闭 masquerade 只阻断容器外网 SNAT，仍允许前端 Nginx 访问绑定端口。
    docker network create --driver bridge \
      --opt com.docker.network.bridge.enable_ip_masquerade=false \
      "${NETWORK}" >/dev/null
  fi
}

verify_and_load() {
  local archive="$1" checksum="${1}.sha256"
  [[ -f "${archive}" ]] || { echo "Image archive not found: ${archive}" >&2; exit 1; }
  [[ -f "${checksum}" ]] || { echo "Image checksum not found: ${checksum}" >&2; exit 1; }
  (cd "$(dirname "${archive}")" && sha256sum -c "$(basename "${checksum}")")
  docker load -i "${archive}" >/dev/null
}

verify_image() {
  local image="$1" architecture
  docker image inspect "${image}" >/dev/null
  architecture="$(docker image inspect -f '{{.Architecture}}' "${image}")"
  [[ "${architecture}" == "amd64" ]] || { echo "Image ${image} must be amd64, got ${architecture}" >&2; exit 1; }
}

run_container() {
  local name="$1" image="$2" port="${3:-}" publish=(--expose 80)
  # Bash 3.2 在 set -u 下展开空数组会报未绑定；预检容器用 --expose 保持无宿主端口且兼容联网 Mac 验收。
  [[ -n "${port}" ]] && publish=(--publish "${BIND_ADDRESS}:${port}:80")
  # 镜像已在 deploy/rollback 前显式 inspect 校验；不使用新版 --pull，兼容企业 Docker 18.09。
  docker run -d \
    --name "${name}" \
    --network "${NETWORK}" \
    --read-only \
    --tmpfs /var/cache/nginx:rw,noexec,nosuid,size=16m \
    --tmpfs /var/run:rw,noexec,nosuid,size=1m \
    --tmpfs /tmp:rw,noexec,nosuid,size=16m \
    --cap-drop ALL \
    --cap-add CHOWN --cap-add DAC_OVERRIDE --cap-add SETGID --cap-add SETUID --cap-add NET_BIND_SERVICE \
    --security-opt no-new-privileges \
    --restart unless-stopped \
    --log-opt max-size=10m --log-opt max-file=3 \
    "${publish[@]}" \
    "${image}" >/dev/null
}

wait_healthy() {
  local name="$1" status attempt
  for attempt in $(seq 1 45); do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${name}" 2>/dev/null || true)"
    [[ "${status}" == "healthy" ]] && return 0
    [[ "${status}" == "exited" || "${status}" == "dead" || "${status}" == "unhealthy" ]] && break
    sleep 2
  done
  docker logs --tail 80 "${name}" >&2 || true
  return 1
}

remove_container() {
  docker rm -f "$1" >/dev/null 2>&1 || true
}

prepare_rollback_images() {
  local it_image_id omni_image_id
  it_image_id="$(docker inspect -f '{{.Image}}' "${IT_CONTAINER}" 2>/dev/null || true)"
  omni_image_id="$(docker inspect -f '{{.Image}}' "${OMNI_CONTAINER}" 2>/dev/null || true)"
  if [[ -n "${it_image_id}" && -n "${omni_image_id}" ]]; then
    docker image tag "${it_image_id}" "${IT_ROLLBACK_IMAGE}"
    docker image tag "${omni_image_id}" "${OMNI_ROLLBACK_IMAGE}"
    ROLLBACK_AVAILABLE=1
    return
  fi
  if [[ -n "${it_image_id}" || -n "${omni_image_id}" ]]; then
    echo "Existing toolbox deployment is incomplete; both containers are required before upgrade" >&2
    return 1
  fi

  # 首次部署必须清除历史遗留标签，避免失败时误恢复不成对的旧版本。
  docker image rm "${IT_ROLLBACK_IMAGE}" "${OMNI_ROLLBACK_IMAGE}" >/dev/null 2>&1 || true
  if docker image inspect "${IT_ROLLBACK_IMAGE}" >/dev/null 2>&1 \
      || docker image inspect "${OMNI_ROLLBACK_IMAGE}" >/dev/null 2>&1; then
    echo "Stale rollback image tags could not be cleared" >&2
    return 1
  fi
}

rollback_all() {
  if ! docker image inspect "${IT_ROLLBACK_IMAGE}" >/dev/null 2>&1 \
      || ! docker image inspect "${OMNI_ROLLBACK_IMAGE}" >/dev/null 2>&1; then
    echo "Both rollback images are required; no container was changed" >&2
    return 1
  fi
  remove_container "${IT_CONTAINER}"
  remove_container "${OMNI_CONTAINER}"
  if ! run_container "${IT_CONTAINER}" "${IT_ROLLBACK_IMAGE}" 18120 \
    || ! wait_healthy "${IT_CONTAINER}" \
    || ! run_container "${OMNI_CONTAINER}" "${OMNI_ROLLBACK_IMAGE}" 18121 \
    || ! wait_healthy "${OMNI_CONTAINER}"; then
    remove_container "${IT_CONTAINER}"
    remove_container "${OMNI_CONTAINER}"
    echo "Rollback failed; both containers were stopped to avoid a partial service" >&2
    return 1
  fi
  echo "Toolbox containers restored from rollback images"
}

preflight_image() {
  local image="$1" name="$2"
  remove_container "${name}"
  run_container "${name}" "${image}"
  if ! wait_healthy "${name}"; then
    remove_container "${name}"
    return 1
  fi
  remove_container "${name}"
}

deploy_all() {
  command -v docker >/dev/null
  command -v sha256sum >/dev/null
  ensure_network
  prepare_rollback_images
  verify_and_load "${IT_TAR}"
  verify_and_load "${OMNI_TAR}"
  verify_image "${IT_IMAGE}"
  verify_image "${OMNI_IMAGE}"

  # 两个候选镜像先在无宿主端口的内部网络中通过健康检查，避免提前影响旧容器和 Nginx 流量。
  preflight_image "${IT_IMAGE}" "${IT_CONTAINER}-preflight"
  preflight_image "${OMNI_IMAGE}" "${OMNI_CONTAINER}-preflight"

  remove_container "${IT_CONTAINER}"
  remove_container "${OMNI_CONTAINER}"
  if ! run_container "${IT_CONTAINER}" "${IT_IMAGE}" 18120 \
    || ! wait_healthy "${IT_CONTAINER}" \
    || ! run_container "${OMNI_CONTAINER}" "${OMNI_IMAGE}" 18121 \
    || ! wait_healthy "${OMNI_CONTAINER}"; then
    echo "Toolbox deployment failed; restoring previous images" >&2
    if [[ "${ROLLBACK_AVAILABLE}" -eq 1 ]]; then
      rollback_all
    else
      remove_container "${IT_CONTAINER}"
      remove_container "${OMNI_CONTAINER}"
      echo "No previous complete deployment was available for rollback" >&2
    fi
    return 1
  fi
  echo "Toolbox containers are healthy on ${BIND_ADDRESS}:18120 and ${BIND_ADDRESS}:18121"
}

status_all() {
  docker ps --filter "name=^/${IT_CONTAINER}$" --filter "name=^/${OMNI_CONTAINER}$" \
    --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
  [[ "$(docker inspect -f '{{.State.Health.Status}}' "${IT_CONTAINER}" 2>/dev/null || true)" == "healthy" ]]
  [[ "$(docker inspect -f '{{.State.Health.Status}}' "${OMNI_CONTAINER}" 2>/dev/null || true)" == "healthy" ]]
}

case "${ACTION}" in
  deploy) deploy_all ;;
  status) status_all ;;
  rollback) ensure_network; rollback_all ;;
  stop) remove_container "${IT_CONTAINER}"; remove_container "${OMNI_CONTAINER}" ;;
  *) usage; exit 2 ;;
esac
