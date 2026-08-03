#!/usr/bin/env bash
set -euo pipefail

# Python workflow平台的纯Docker运行器。API/Worker永不挂载Docker Socket；只有Runner控制器可访问。

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="/data/testagent/config/workflow.env"
RELEASE_ENV="${SCRIPT_DIR}/../release.env"
COMMAND="status"
ROLE="all"
ROLE_EXPLICIT=0

usage() {
  cat <<'USAGE'
Usage: deploy/internal/workflow/workflow-docker.sh <command> [options]

Commands:
  initialize   Run Alembic migration and LangGraph checkpoint setup with the owner URL.
  start        Start configured control (API+Worker), Runner, or all roles.
  stop         Stop configured roles; Runner stop is blocked while task containers exist.
  restart      Stop then start configured roles.
  status       Show exact container and image status.
  verify       Verify images, key files, Docker version and Runner network policy.

Options:
  --env-file <path>      Runtime secret dotenv. Default: /data/testagent/config/workflow.env.
  --release-env <path>   release.env from the offline package.
  --role <control|runner|all>  Node role override. Default: TEST_AGENT_WORKFLOW_NODE_ROLE or all.
  -h, --help             Show this help.
USAGE
}

[[ $# -gt 0 ]] || {
  usage >&2
  exit 2
}
COMMAND="$1"
shift
while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --release-env)
      RELEASE_ENV="$2"
      shift 2
      ;;
    --role)
      ROLE="$2"
      ROLE_EXPLICIT=1
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

load_dotenv() {
  local file="$1" overwrite="$2" line key value duplicates
  [[ -f "${file}" ]] || {
    echo "Required dotenv is missing: ${file}" >&2
    exit 1
  }
  duplicates="$(awk '
    {
      line = $0
      sub(/\r$/, "", line)
      sub(/^[[:space:]]+/, "", line)
      if (line == "" || line ~ /^#/) next
      sub(/^export[[:space:]]+/, "", line)
      separator = index(line, "=")
      if (!separator) next
      key = substr(line, 1, separator - 1)
      gsub(/[[:space:]]/, "", key)
      if (key ~ /^[A-Za-z_][A-Za-z0-9_]*$/ && ++seen[key] == 2) print key
    }
  ' "${file}")"
  [[ -z "${duplicates}" ]] || {
    echo "Duplicate workflow dotenv key(s): ${duplicates//$'\n'/, }" >&2
    exit 1
  }
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    [[ "${line}" == export\ * ]] && line="${line#export }"
    [[ "${line}" == *=* ]] || continue
    key="${line%%=*}"
    value="${line#*=}"
    key="${key//[[:space:]]/}"
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    [[ "${key}" == TEST_AGENT_* ]] || {
      echo "Unsupported workflow dotenv key: ${key}" >&2
      exit 1
    }
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    if [[ "${overwrite}" == "1" || -z "${!key+x}" ]]; then
      printf -v "${key}" '%s' "${value}"
      # Docker只从下方的按进程白名单env文件取值；不要让docker/iptables子进程
      # 从当前shell继承整份数据库、Redis和HMAC配置。
      export -n "${key}" 2>/dev/null || true
    fi
  done <"${file}"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

sha256_text() {
  if command -v sha256sum >/dev/null 2>&1; then
    printf '%s' "$1" | sha256sum | awk '{print $1}'
  else
    printf '%s' "$1" | shasum -a 256 | awk '{print $1}'
  fi
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

load_dotenv "${ENV_FILE}" 1
load_dotenv "${RELEASE_ENV}" 1
[[ "${ROLE_EXPLICIT}" == "1" ]] || ROLE="${TEST_AGENT_WORKFLOW_NODE_ROLE:-all}"
[[ "${ROLE}" == "control" || "${ROLE}" == "runner" || "${ROLE}" == "all" ]] || {
  echo "Role must be control, runner or all" >&2
  exit 1
}
[[ "${COMMAND}" =~ ^(initialize|start|stop|restart|status|verify)$ ]] || {
  echo "Unknown workflow Docker command: ${COMMAND}" >&2
  exit 2
}

require_command docker
docker_version="$(docker version --format '{{.Server.Version}}')"
[[ "${docker_version}" =~ ^([0-9]+)\.([0-9]+) ]] || {
  echo "Unable to read Docker Server version" >&2
  exit 1
}
docker_major="${BASH_REMATCH[1]}"
docker_minor="${BASH_REMATCH[2]}"
(( docker_major > 18 || (docker_major == 18 && docker_minor >= 9) )) || {
  echo "Workflow runtime requires Docker 18.09 or newer" >&2
  exit 1
}

WORKFLOW_IMAGE="${TEST_AGENT_WORKFLOW_SERVICE_IMAGE:?release.env is missing workflow image}"
WORKFLOW_IMAGE_ID="${TEST_AGENT_WORKFLOW_SERVICE_IMAGE_ID:?release.env is missing workflow image ID}"
RUNNER_IMAGE="${TEST_AGENT_RUNNER_CONTROLLER_IMAGE:?release.env is missing Runner image}"
RUNNER_IMAGE_ID="${TEST_AGENT_RUNNER_CONTROLLER_IMAGE_ID:?release.env is missing Runner image ID}"
ANALYSIS_IMAGE="${TEST_AGENT_ANALYSIS_TASK_IMAGE:?release.env is missing analysis image}"
ANALYSIS_IMAGE_ID="${TEST_AGENT_ANALYSIS_TASK_IMAGE_ID:?release.env is missing analysis image ID}"

require_secret() {
  local name="$1" value="${!1:-}"
  [[ "${#value}" -ge 32 && "${value}" != *REPLACE_* ]] || {
    echo "${name} must be a non-placeholder secret with at least 32 characters" >&2
    exit 1
  }
}

require_equal() {
  local left="$1" right="$2"
  [[ "${!left:-}" == "${!right:-}" && -n "${!left:-}" ]] || {
    echo "${left} and ${right} must contain the same deployment value" >&2
    exit 1
  }
}

for secret_name in \
  TEST_AGENT_WORKFLOW_PLATFORM_HMAC_SECRET \
  TEST_AGENT_WORKFLOW_RUNNER_HMAC_SECRET \
  TEST_AGENT_RUNNER_WORKER_HMAC_SECRET \
  TEST_AGENT_RUNNER_PLATFORM_HMAC_SECRET; do
  require_secret "${secret_name}"
done
require_equal TEST_AGENT_WORKFLOW_RUNNER_HMAC_SECRET TEST_AGENT_RUNNER_WORKER_HMAC_SECRET
require_equal TEST_AGENT_WORKFLOW_RUNNER_ID TEST_AGENT_RUNNER_RUNNER_ID
[[ "${TEST_AGENT_WORKFLOW_PLATFORM_HMAC_SECRET}" != "${TEST_AGENT_WORKFLOW_RUNNER_HMAC_SECRET}" \
  && "${TEST_AGENT_WORKFLOW_PLATFORM_HMAC_SECRET}" != "${TEST_AGENT_RUNNER_PLATFORM_HMAC_SECRET}" \
  && "${TEST_AGENT_WORKFLOW_RUNNER_HMAC_SECRET}" != "${TEST_AGENT_RUNNER_PLATFORM_HMAC_SECRET}" ]] || {
  echo "Workflow capability, Worker-to-Runner and Runner-to-platform HMAC secrets must be distinct" >&2
  exit 1
}

for pair in \
  "${WORKFLOW_IMAGE}|${WORKFLOW_IMAGE_ID}" \
  "${RUNNER_IMAGE}|${RUNNER_IMAGE_ID}" \
  "${ANALYSIS_IMAGE}|${ANALYSIS_IMAGE_ID}"; do
  image="${pair%%|*}"
  expected="${pair#*|}"
  actual="$(docker image inspect --format '{{.Id}}' "${image}")"
  [[ "${actual}" == "${expected}" ]] || {
    echo "Loaded image ID mismatch for ${image}" >&2
    exit 1
  }
done

CONTROL_NETWORK="${TEST_AGENT_WORKFLOW_CONTROL_NETWORK:-test-agent-workflow-control}"
[[ "${CONTROL_NETWORK}" =~ ^test-agent-workflow-[A-Za-z0-9_.-]{1,80}$ ]] || {
  echo "Invalid TEST_AGENT_WORKFLOW_CONTROL_NETWORK" >&2
  exit 1
}
API_BIND="${TEST_AGENT_WORKFLOW_API_BIND_ADDRESS:-127.0.0.1}"
API_PORT="${TEST_AGENT_WORKFLOW_API_PORT:-8090}"
RUNNER_BIND="${TEST_AGENT_WORKFLOW_RUNNER_BIND_ADDRESS:-127.0.0.1}"
RUNNER_PORT="${TEST_AGENT_WORKFLOW_RUNNER_PORT:-8091}"
[[ "${API_BIND}" =~ ^[0-9a-fA-F:.]+$ && "${RUNNER_BIND}" =~ ^[0-9a-fA-F:.]+$ ]] || {
  echo "Workflow bind addresses must be literal IP addresses" >&2
  exit 1
}
[[ "${API_PORT}" =~ ^[0-9]{1,5}$ ]] && (( API_PORT >= 1 && API_PORT <= 65535 ))
[[ "${RUNNER_PORT}" =~ ^[0-9]{1,5}$ ]] && (( RUNNER_PORT >= 1 && RUNNER_PORT <= 65535 ))

PUBLIC_KEY_HOST="${TEST_AGENT_WORKFLOW_RUNNER_PUBLIC_KEY_HOST_PATH:-/data/testagent/config/workflow-runner-public.pem}"
PUBLIC_KEY_CONTAINER="${TEST_AGENT_WORKFLOW_RUNNER_PUBLIC_KEY_PATH:-/run/secrets/workflow-runner-public.pem}"
PRIVATE_KEY_HOST="${TEST_AGENT_WORKFLOW_RUNNER_PRIVATE_KEY_HOST_PATH:-/data/testagent/config/workflow-runner-private.pem}"
PRIVATE_KEY_CONTAINER="${TEST_AGENT_RUNNER_PRIVATE_KEY_PATH:-/run/secrets/workflow-runner-private.pem}"
KNOWN_HOSTS_HOST="${TEST_AGENT_WORKFLOW_GIT_KNOWN_HOSTS_HOST_PATH:-/data/testagent/config/workflow-git-known-hosts}"
KNOWN_HOSTS_CONTAINER="${TEST_AGENT_RUNNER_KNOWN_HOSTS_PATH:-/run/secrets/workflow-git-known-hosts}"
RUNNER_ROOT="${TEST_AGENT_RUNNER_ROOT:-/data/testagent/workflow-runner}"
CREDENTIAL_ROOT="${TEST_AGENT_RUNNER_CREDENTIAL_ROOT:-/run/test-agent-workflow-credentials}"
[[ -f "${PUBLIC_KEY_HOST}" ]] || {
  echo "Runner public key file is missing: ${PUBLIC_KEY_HOST}" >&2
  exit 1
}
[[ "${ROLE}" == "control" ]] || [[ -f "${PRIVATE_KEY_HOST}" ]] || {
  echo "Runner private key file is missing: ${PRIVATE_KEY_HOST}" >&2
  exit 1
}
if [[ "${ROLE}" == "runner" || "${ROLE}" == "all" ]]; then
  private_mode="$(stat -c '%a' "${PRIVATE_KEY_HOST}")"
  private_owner="$(stat -c '%u' "${PRIVATE_KEY_HOST}")"
  [[ "${private_mode}" == "400" || "${private_mode}" == "600" ]] || {
    echo "Runner private key must use mode 0400 or 0600" >&2
    exit 1
  }
  [[ "${private_owner}" == "10003" ]] || {
    echo "Runner private key must be owned by container uid 10003" >&2
    exit 1
  }
  [[ -s "${KNOWN_HOSTS_HOST}" && ! -L "${KNOWN_HOSTS_HOST}" ]] || {
    echo "Runner known_hosts must be a non-empty regular file, not a symlink" >&2
    exit 1
  }
  known_hosts_mode="$(stat -c '%a' "${KNOWN_HOSTS_HOST}")"
  known_hosts_size="$(stat -c '%s' "${KNOWN_HOSTS_HOST}")"
  [[ "${known_hosts_mode}" == "444" || "${known_hosts_mode}" == "644" ]] || {
    echo "Runner known_hosts must use mode 0444 or 0644" >&2
    exit 1
  }
  (( known_hosts_size <= 1048576 )) || {
    echo "Runner known_hosts exceeds the 1 MiB limit" >&2
    exit 1
  }
fi
[[ "${PUBLIC_KEY_CONTAINER}" == /* && "${PRIVATE_KEY_CONTAINER}" == /* \
    && "${KNOWN_HOSTS_HOST}" == /* && "${KNOWN_HOSTS_CONTAINER}" == /* \
    && "${RUNNER_ROOT}" == /* \
    && "${CREDENTIAL_ROOT}" == /* ]] || {
  echo "Workflow key and Runner paths must be absolute" >&2
  exit 1
}

config_material="env=$(sha256_file "${ENV_FILE}")|release=$(sha256_file "${RELEASE_ENV}")|public=$(sha256_file "${PUBLIC_KEY_HOST}")"
if [[ -f "${PRIVATE_KEY_HOST}" ]]; then
  config_material="${config_material}|private=$(sha256_file "${PRIVATE_KEY_HOST}")"
fi
if [[ -f "${KNOWN_HOSTS_HOST}" ]]; then
  config_material="${config_material}|known-hosts=$(sha256_file "${KNOWN_HOSTS_HOST}")"
fi
config_digest="$(sha256_text "${config_material}")"
unset config_material

# 统一配置文件只作为部署输入。长期容器分别接收最小白名单，特别是数据库owner
# URL永不进入API/Worker，平台能力HMAC也永不进入Runner。
SCOPED_ENV_DIR="$(mktemp -d)"
CONTROL_ENV_FILE="${SCOPED_ENV_DIR}/control.env"
RUNNER_ENV_FILE="${SCOPED_ENV_DIR}/runner.env"
trap 'rm -rf "${SCOPED_ENV_DIR}"' EXIT

write_env_subset() {
  local output="$1" name value
  shift
  : >"${output}"
  chmod 0600 "${output}"
  for name in "$@"; do
    [[ -n "${!name+x}" ]] || continue
    value="${!name}"
    [[ "${value}" != *$'\n'* && "${value}" != *$'\r'* ]] || {
      echo "${name} cannot contain a newline" >&2
      exit 1
    }
    printf '%s=%s\n' "${name}" "${value}" >>"${output}"
  done
}

write_env_subset "${CONTROL_ENV_FILE}" \
  TEST_AGENT_WORKFLOW_DATABASE_URL \
  TEST_AGENT_WORKFLOW_REDIS_URL \
  TEST_AGENT_WORKFLOW_PLATFORM_BASE_URL \
  TEST_AGENT_WORKFLOW_PLATFORM_HMAC_SECRET \
  TEST_AGENT_WORKFLOW_INTENT_MODEL_NAME \
  TEST_AGENT_WORKFLOW_SYNTHESIS_MODEL_NAME \
  TEST_AGENT_WORKFLOW_REPORT_QA_MODEL_NAME \
  TEST_AGENT_WORKFLOW_ANALYSIS_MODEL_NAME \
  TEST_AGENT_WORKFLOW_RUNNER_BASE_URL \
  TEST_AGENT_WORKFLOW_RUNNER_HMAC_SECRET \
  TEST_AGENT_WORKFLOW_RUNNER_ID \
  TEST_AGENT_WORKFLOW_WORKER_ID \
  TEST_AGENT_WORKFLOW_WORKER_LEASE_SECONDS \
  TEST_AGENT_WORKFLOW_WORKSPACE_RETENTION_HOURS

write_env_subset "${RUNNER_ENV_FILE}" \
  TEST_AGENT_RUNNER_RUNNER_ID \
  TEST_AGENT_RUNNER_WORKER_HMAC_SECRET \
  TEST_AGENT_RUNNER_PLATFORM_HMAC_SECRET \
  TEST_AGENT_RUNNER_PLATFORM_BASE_URL \
  TEST_AGENT_RUNNER_ROOT \
  TEST_AGENT_RUNNER_CREDENTIAL_ROOT \
  TEST_AGENT_RUNNER_PRIVATE_KEY_PATH \
  TEST_AGENT_RUNNER_KNOWN_HOSTS_PATH \
  TEST_AGENT_RUNNER_ANALYSIS_NETWORK \
  TEST_AGENT_RUNNER_ANALYSIS_NETWORK_SUBNET \
  TEST_AGENT_RUNNER_MODEL_GATEWAY_URL \
  TEST_AGENT_RUNNER_MODEL_GATEWAY_CIDR \
  TEST_AGENT_RUNNER_MODEL_GATEWAY_PORT \
  TEST_AGENT_RUNNER_MINIMUM_FREE_BYTES

ensure_control_network() {
  if ! docker network inspect "${CONTROL_NETWORK}" >/dev/null 2>&1; then
    docker network create --driver bridge \
      --label com.enterprise.testagent.purpose=workflow-control \
      "${CONTROL_NETWORK}" >/dev/null
  fi
  actual="$(docker network inspect --format '{{.Driver}}|{{index .Labels "com.enterprise.testagent.purpose"}}' "${CONTROL_NETWORK}")"
  [[ "${actual}" == "bridge|workflow-control" ]] || {
    echo "Existing workflow control network is not trusted" >&2
    exit 1
  }
}

container_matches() {
  local name="$1" image_id="$2" actual_image actual_config
  docker container inspect "${name}" >/dev/null 2>&1 || return 1
  actual_image="$(docker container inspect --format '{{.Image}}' "${name}")"
  actual_config="$(docker container inspect --format '{{index .Config.Labels "com.enterprise.testagent.config-sha256"}}' "${name}")"
  [[ "${actual_image}" == "${image_id}" && "${actual_config}" == "${config_digest}" ]]
}

replace_exact_container() {
  local name="$1" image_id="$2"
  if docker container inspect "${name}" >/dev/null 2>&1; then
    if container_matches "${name}" "${image_id}"; then
      if docker start "${name}" >/dev/null 2>&1; then
        return 1
      fi
    fi
    docker rm -f "${name}" >/dev/null
  fi
  return 0
}

start_control() {
  ensure_control_network
  if replace_exact_container test-agent-workflow-api "${WORKFLOW_IMAGE_ID}"; then
    docker run -d --name test-agent-workflow-api --restart unless-stopped \
      --label "com.enterprise.testagent.config-sha256=${config_digest}" \
      --network "${CONTROL_NETWORK}" \
      --read-only --cap-drop ALL --security-opt no-new-privileges \
      --pids-limit 512 --memory 2g --cpus 2 \
      --tmpfs /tmp:rw,noexec,nosuid,nodev,size=256m,uid=10002,gid=10002,mode=1700 \
      --env-file "${CONTROL_ENV_FILE}" \
      --env "TEST_AGENT_WORKFLOW_RUNNER_PUBLIC_KEY_PATH=${PUBLIC_KEY_CONTAINER}" \
      --volume "${PUBLIC_KEY_HOST}:${PUBLIC_KEY_CONTAINER}:ro" \
      --publish "${API_BIND}:${API_PORT}:8090" \
      "${WORKFLOW_IMAGE_ID}" api --host 0.0.0.0 --port 8090 >/dev/null
  fi
  if replace_exact_container test-agent-workflow-worker "${WORKFLOW_IMAGE_ID}"; then
    docker run -d --name test-agent-workflow-worker --restart unless-stopped \
      --label "com.enterprise.testagent.config-sha256=${config_digest}" \
      --network "${CONTROL_NETWORK}" \
      --read-only --cap-drop ALL --security-opt no-new-privileges \
      --pids-limit 1024 --memory 4g --cpus 4 \
      --tmpfs /tmp:rw,noexec,nosuid,nodev,size=512m,uid=10002,gid=10002,mode=1700 \
      --env-file "${CONTROL_ENV_FILE}" \
      --env "TEST_AGENT_WORKFLOW_RUNNER_PUBLIC_KEY_PATH=${PUBLIC_KEY_CONTAINER}" \
      --volume "${PUBLIC_KEY_HOST}:${PUBLIC_KEY_CONTAINER}:ro" \
      "${WORKFLOW_IMAGE_ID}" worker >/dev/null
  fi
}

start_runner() {
  ensure_control_network
  # iptables规则不随Docker/宿主机重启持久化。Runner启动前先验证，缺失时只在
  # 没有分析任务容器的安全窗口自动恢复，避免Runner先于出站隔离规则恢复。
  if ! "${SCRIPT_DIR}/analysis-network.sh" --env-file "${ENV_FILE}" --verify-only; then
    echo "Restoring workflow analysis egress policy before Runner startup." >&2
    "${SCRIPT_DIR}/analysis-network.sh" --env-file "${ENV_FILE}" --apply
  fi
  # 全新分析节点不要求预先创建宿主机用户；目录只需与容器内 Runner 的数值 UID/GID 对齐。
  install -d -m 0700 "${RUNNER_ROOT}"
  chown 10003:10003 "${RUNNER_ROOT}"
  socket_gid="$(stat -c '%g' /var/run/docker.sock)"
  [[ "${socket_gid}" =~ ^[0-9]+$ ]] || {
    echo "Unable to resolve Docker Socket group" >&2
    exit 1
  }
  if replace_exact_container test-agent-workflow-runner "${RUNNER_IMAGE_ID}"; then
    docker run -d --name test-agent-workflow-runner --restart no \
      --label "com.enterprise.testagent.config-sha256=${config_digest}" \
      --network "${CONTROL_NETWORK}" \
      --read-only --cap-drop ALL --security-opt no-new-privileges \
      --group-add "${socket_gid}" \
      --pids-limit 1024 --memory 3g --cpus 3 \
      --ulimit nofile=65536:65536 \
      --tmpfs /tmp:rw,noexec,nosuid,nodev,size=256m,uid=10003,gid=10003,mode=1700 \
      --tmpfs "${CREDENTIAL_ROOT}:rw,noexec,nosuid,nodev,size=16m,uid=10003,gid=10003,mode=1700" \
      --env-file "${RUNNER_ENV_FILE}" \
      --env "TEST_AGENT_RUNNER_ANALYSIS_IMAGE=${ANALYSIS_IMAGE_ID}" \
      --env "TEST_AGENT_RUNNER_PRIVATE_KEY_PATH=${PRIVATE_KEY_CONTAINER}" \
      --env "TEST_AGENT_RUNNER_KNOWN_HOSTS_PATH=${KNOWN_HOSTS_CONTAINER}" \
      --volume /var/run/docker.sock:/var/run/docker.sock \
      --volume "${RUNNER_ROOT}:${RUNNER_ROOT}:rw" \
      --volume "${PRIVATE_KEY_HOST}:${PRIVATE_KEY_CONTAINER}:ro" \
      --volume "${KNOWN_HOSTS_HOST}:${KNOWN_HOSTS_CONTAINER}:ro" \
      --publish "${RUNNER_BIND}:${RUNNER_PORT}:8091" \
      "${RUNNER_IMAGE_ID}" --host 0.0.0.0 --port 8091 >/dev/null
  fi
}

initialize_control() {
  [[ "${ROLE}" != "runner" ]] || {
    echo "Database initialization belongs to a control node" >&2
    exit 1
  }
  migration_url="${TEST_AGENT_WORKFLOW_MIGRATION_DATABASE_URL:-}"
  [[ -n "${migration_url}" ]] || {
    echo "TEST_AGENT_WORKFLOW_MIGRATION_DATABASE_URL is required for initialize" >&2
    exit 1
  }
  override_file="${SCOPED_ENV_DIR}/migration.env"
  chmod 0600 "${override_file}"
  printf 'TEST_AGENT_WORKFLOW_DATABASE_URL=%s\n' "${migration_url}" >"${override_file}"
  for operation in migrate checkpoint-setup; do
    docker run --rm --network "${CONTROL_NETWORK}" \
      --read-only --cap-drop ALL --security-opt no-new-privileges \
      --tmpfs /tmp:rw,noexec,nosuid,nodev,size=256m,uid=10002,gid=10002,mode=1700 \
      --env-file "${CONTROL_ENV_FILE}" --env-file "${override_file}" \
      --env "TEST_AGENT_WORKFLOW_RUNNER_PUBLIC_KEY_PATH=${PUBLIC_KEY_CONTAINER}" \
      --volume "${PUBLIC_KEY_HOST}:${PUBLIC_KEY_CONTAINER}:ro" \
      "${WORKFLOW_IMAGE_ID}" "${operation}"
  done
  rm -f "${override_file}"
}

stop_exact() {
  local name="$1"
  docker stop --time 30 "${name}" >/dev/null 2>&1 || true
}

stop_roles() {
  if [[ "${ROLE}" == "control" || "${ROLE}" == "all" ]]; then
    stop_exact test-agent-workflow-worker
    stop_exact test-agent-workflow-api
  fi
  if [[ "${ROLE}" == "runner" || "${ROLE}" == "all" ]]; then
    if docker ps --format '{{.Names}}' | grep -Eq '^test-agent-analysis-'; then
      echo "Runner stop blocked: active analysis task containers still exist" >&2
      exit 1
    fi
    stop_exact test-agent-workflow-runner
  fi
}

status_roles() {
  for name in test-agent-workflow-api test-agent-workflow-worker test-agent-workflow-runner; do
    docker container inspect --format '{{.Name}}|{{.State.Status}}|{{.Image}}' "${name}" 2>/dev/null \
      || echo "/${name}|missing|none"
  done
}

verify_roles() {
  if [[ "${ROLE}" == "runner" || "${ROLE}" == "all" ]]; then
    "${SCRIPT_DIR}/analysis-network.sh" --env-file "${ENV_FILE}" --verify-only
  fi
  echo "Workflow images, key files and Docker baseline are valid."
}

case "${COMMAND}" in
  initialize)
    ensure_control_network
    initialize_control
    ;;
  start)
    [[ "${ROLE}" == "runner" ]] || start_control
    [[ "${ROLE}" == "control" ]] || start_runner
    ;;
  stop)
    stop_roles
    ;;
  restart)
    stop_roles
    [[ "${ROLE}" == "runner" ]] || start_control
    [[ "${ROLE}" == "control" ]] || start_runner
    ;;
  status)
    status_roles
    ;;
  verify)
    verify_roles
    ;;
esac
