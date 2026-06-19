#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
LOG_DIR="${ROOT_DIR}/.tmp/phase11-real-e2e"

usage() {
  cat <<'USAGE'
Usage: tools/dev-phase11-real-e2e.sh [--start-services] [--profile local|test] [--env-file <path>] [--log-dir <path>] [--help]

Run the Phase 11 real frontend/backend/opencode integration suite. By default
the script reuses already running backend and opencode services. With
--start-services it starts missing local dependencies and keeps service logs
under .tmp/phase11-real-e2e/.

Environment:
  TEST_AGENT_FRONTEND_URL  default: http://127.0.0.1:3000
  TEST_AGENT_BASE_URL      default: http://127.0.0.1:8080
  OPENCODE_BASE_URL        default: http://127.0.0.1:4096
  TEST_AGENT_API_TOKEN     optional Bearer token for backend API calls

Options:
  --start-services  Start missing Postgres, opencode server and backend.
  --profile         Backend Spring profile for started backend. Default: local.
  --env-file        Env file passed to tools/dev-backend-run.sh.
  --log-dir         Directory for service logs. Default: .tmp/phase11-real-e2e.
  --help            Show this help.
USAGE
}

start_services=false
profile="local"
env_file=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --start-services)
      start_services=true
      shift
      ;;
    --profile)
      [[ $# -ge 2 ]] || {
        echo "--profile requires a value." >&2
        exit 2
      }
      profile="$2"
      shift 2
      ;;
    --env-file)
      [[ $# -ge 2 ]] || {
        echo "--env-file requires a path." >&2
        exit 2
      }
      env_file="$2"
      shift 2
      ;;
    --log-dir)
      [[ $# -ge 2 ]] || {
        echo "--log-dir requires a path." >&2
        exit 2
      }
      LOG_DIR="$2"
      shift 2
      ;;
    --help|-h)
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

case "${profile}" in
  local|test)
    ;;
  *)
    echo "Unsupported profile: ${profile}. Expected local or test." >&2
    exit 2
    ;;
esac

if [[ "${LOG_DIR}" != /* ]]; then
  LOG_DIR="${ROOT_DIR}/${LOG_DIR}"
fi

frontend_url="${TEST_AGENT_FRONTEND_URL:-http://127.0.0.1:3000}"
backend_url="${TEST_AGENT_BASE_URL:-http://127.0.0.1:8080}"
opencode_url="${OPENCODE_BASE_URL:-http://127.0.0.1:4096}"
opencode_hostport="${opencode_url#http://}"
opencode_hostport="${opencode_hostport#https://}"
opencode_hostport="${opencode_hostport%%/*}"
if [[ "${opencode_hostport}" == *:* ]]; then
  opencode_default_hostname="${opencode_hostport%%:*}"
  opencode_default_port="${opencode_hostport##*:}"
else
  opencode_default_hostname="${opencode_hostport}"
  opencode_default_port="4096"
fi
opencode_hostname="${OPENCODE_HOSTNAME:-${opencode_default_hostname}}"
opencode_port="${OPENCODE_PORT:-${opencode_default_port}}"
started_pids=()

cleanup() {
  local pid
  for pid in "${started_pids[@]:-}"; do
    if kill -0 "${pid}" >/dev/null 2>&1; then
      kill "${pid}" >/dev/null 2>&1 || true
    fi
  done
}
trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

docker_ready() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1
}

http_ok() {
  curl -fsS --max-time 5 "$1" >/dev/null 2>&1
}

backend_ready() {
  http_ok "${backend_url}/actuator/health"
}

opencode_ready() {
  http_ok "${opencode_url}/doc" || http_ok "${opencode_url}/health"
}

wait_until() {
  local label="$1"
  local command="$2"
  local attempts="${3:-60}"
  local i
  for ((i = 1; i <= attempts; i++)); do
    if eval "${command}"; then
      echo "OK ${label}"
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for ${label}." >&2
  return 1
}

start_opencode_if_needed() {
  if opencode_ready; then
    echo "OK ${opencode_url}"
    return
  fi
  if [[ "${start_services}" != "true" ]]; then
    echo "opencode server is not reachable at ${opencode_url}; rerun with --start-services or start it manually." >&2
    exit 1
  fi
  require_command opencode
  mkdir -p "${LOG_DIR}"
  echo "Starting opencode server; logs: ${LOG_DIR}/opencode.log"
  opencode serve \
    --hostname "${opencode_hostname}" \
    --port "${opencode_port}" \
    --cors "${frontend_url}" \
    --cors "http://localhost:3000" \
    --cors "http://127.0.0.1:3000" \
    --print-logs \
    >"${LOG_DIR}/opencode.log" 2>&1 &
  started_pids+=("$!")
  wait_until "${opencode_url}" opencode_ready 60
}

start_backend_if_needed() {
  if backend_ready; then
    echo "OK ${backend_url}/actuator/health"
    return
  fi
  if [[ "${start_services}" != "true" ]]; then
    echo "backend is not reachable at ${backend_url}; rerun with --start-services or start it manually." >&2
    exit 1
  fi
  mkdir -p "${LOG_DIR}"
  if ! docker_ready; then
    echo "Docker daemon is required to start local Postgres. Start Docker Desktop or run the backend manually, then retry without --start-services." >&2
    exit 1
  fi
  echo "Starting local Postgres dependency."
  "${ROOT_DIR}/tools/dev-local-up.sh" >"${LOG_DIR}/dev-local-up.log" 2>&1
  echo "Starting backend; logs: ${LOG_DIR}/backend.log"
  backend_args=("--profile" "${profile}")
  if [[ -n "${env_file}" ]]; then
    backend_args+=("--env-file" "${env_file}")
  fi
  "${ROOT_DIR}/tools/dev-backend-run.sh" "${backend_args[@]}" >"${LOG_DIR}/backend.log" 2>&1 &
  started_pids+=("$!")
  wait_until "${backend_url}/actuator/health" backend_ready 90 || {
    echo "Backend log tail:" >&2
    tail -n 80 "${LOG_DIR}/backend.log" >&2 || true
    exit 1
  }
}

require_command curl
require_command corepack

mkdir -p "${LOG_DIR}"
echo "Phase 11 real E2E log directory: ${LOG_DIR}"
echo "Sensitive environment values are not printed."

start_opencode_if_needed
start_backend_if_needed

export TEST_AGENT_FRONTEND_URL="${frontend_url}"
export TEST_AGENT_BASE_URL="${backend_url}"
export OPENCODE_BASE_URL="${opencode_url}"
export TEST_AGENT_RUN_REAL_E2E=1

cd "${ROOT_DIR}/frontend"
corepack pnpm e2e:real
