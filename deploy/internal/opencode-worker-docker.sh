#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="/data/testagent/config/docker.env"
ACTION="start"
CONTAINER_NAME="test-agent-opencode-worker"

usage() {
  cat <<'USAGE'
Usage: deploy/internal/opencode-worker-docker.sh [options] [start|restart|stop|status|logs]

Manage the enterprise opencode-worker container with plain docker commands.

Options:
  --env-file <path>       Dotenv file to read. Defaults to /data/testagent/config/docker.env.
  --name <name>           Docker container name. Defaults to test-agent-opencode-worker.
  -h, --help              Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --name)
      CONTAINER_NAME="$2"
      shift 2
      ;;
    start|restart|stop|status|logs)
      ACTION="$1"
      shift
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
  local file="$1"
  [[ -f "${file}" ]] || {
    echo "Env file not found: ${file}" >&2
    exit 1
  }
  local line key value
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    [[ "${line}" == export\ * ]] && line="${line#export }"
    [[ "${line}" == *=* ]] || continue
    key="${line%%=*}"
    value="${line#*=}"
    key="${key//[[:space:]]/}"
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    if [[ -z "${!key+x}" ]]; then
      printf -v "${key}" '%s' "${value}"
      export "${key}"
    fi
  done <"${file}"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_value() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Required env value is empty: ${name}" >&2
    exit 1
  fi
}

stop_container() {
  docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
}

start_container() {
  require_value TEST_AGENT_OPENCODE_MANAGER_TOKEN
  require_value TEST_AGENT_DATA_ROOT
  require_value TEST_AGENT_PROGRAM_ROOT

  mkdir -p "${TEST_AGENT_DATA_ROOT}" "${TEST_AGENT_PROGRAM_ROOT}"
  stop_container

  docker run -d \
    --name "${CONTAINER_NAME}" \
    --hostname "${CONTAINER_NAME}" \
    --restart unless-stopped \
    -p "${OPENCODE_WORKER_PORT_START}-${OPENCODE_WORKER_PORT_END}:${OPENCODE_WORKER_PORT_START}-${OPENCODE_WORKER_PORT_END}" \
    -e "OPENCODE_MANAGER_BACKEND_PORT=${OPENCODE_WORKER_BACKEND_PORT}" \
    -e "OPENCODE_MANAGER_PORT_START=${OPENCODE_WORKER_PORT_START}" \
    -e "OPENCODE_MANAGER_PORT_END=${OPENCODE_WORKER_PORT_END}" \
    -e "OPENCODE_MANAGER_TOKEN=${TEST_AGENT_OPENCODE_MANAGER_TOKEN}" \
    -e "SYS_DATA_ROOT_DIR=/data/testagent/data" \
    -e "OPENCODE_MANAGER_STATE_DIR=/data/testagent/data/agent-opencode/manager/worker" \
    -e "OPENCODE_BIN=/data/testagent/programs/opencode/bin/opencode" \
    -e "TEST_AGENT_PROGRAM_ROOT=/data/testagent/programs" \
    -e "OPENCODE_ALLOWED_CORS=${OPENCODE_ALLOWED_CORS}" \
    -e "OPENCODE_MANAGER_HEARTBEAT_INTERVAL=${OPENCODE_MANAGER_HEARTBEAT_INTERVAL}" \
    -e "OPENCODE_MANAGER_RECONNECT_INTERVAL=${OPENCODE_MANAGER_RECONNECT_INTERVAL}" \
    -v "${TEST_AGENT_DATA_ROOT}:/data/testagent/data" \
    -v "${TEST_AGENT_PROGRAM_ROOT}:/data/testagent/programs:ro" \
    --health-cmd "pgrep -f 'opencode-manager run' >/dev/null" \
    --health-interval 10s \
    --health-timeout 3s \
    --health-retries 12 \
    "${TEST_AGENT_OPENCODE_WORKER_IMAGE}"
}

require_command docker
load_dotenv "${ENV_FILE}"

TEST_AGENT_OPENCODE_WORKER_IMAGE="${TEST_AGENT_OPENCODE_WORKER_IMAGE:-test-agent-opencode-worker:internal}"
OPENCODE_WORKER_BACKEND_PORT="${OPENCODE_WORKER_BACKEND_PORT:-8080}"
OPENCODE_WORKER_PORT_START="${OPENCODE_WORKER_PORT_START:-4096}"
OPENCODE_WORKER_PORT_END="${OPENCODE_WORKER_PORT_END:-4105}"
TEST_AGENT_DATA_ROOT="${TEST_AGENT_DATA_ROOT:-/data/testagent/data}"
TEST_AGENT_PROGRAM_ROOT="${TEST_AGENT_PROGRAM_ROOT:-/data/testagent/programs}"
OPENCODE_ALLOWED_CORS="${OPENCODE_ALLOWED_CORS:-}"
OPENCODE_MANAGER_HEARTBEAT_INTERVAL="${OPENCODE_MANAGER_HEARTBEAT_INTERVAL:-5s}"
OPENCODE_MANAGER_RECONNECT_INTERVAL="${OPENCODE_MANAGER_RECONNECT_INTERVAL:-10s}"

case "${ACTION}" in
  start)
    start_container
    ;;
  restart)
    start_container
    ;;
  stop)
    stop_container
    ;;
  status)
    docker ps -a --filter "name=^/${CONTAINER_NAME}$"
    ;;
  logs)
    docker logs --tail 200 -f "${CONTAINER_NAME}"
    ;;
esac
