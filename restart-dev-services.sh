#!/usr/bin/env bash
# 如果用户误用 `sh restart-dev-services.sh`，立即重进 Bash；本脚本依赖数组、[[ ]] 和 printf -v 等 Bash 特性。
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi
case ":${SHELLOPTS:-}:" in
  *:posix:*) exec /usr/bin/env bash "$0" "$@" ;;
esac

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
BACKEND_DIR="${ROOT_DIR}/backend"
FRONTEND_DIR="${ROOT_DIR}/frontend"
BACKEND_JAR="${BACKEND_DIR}/test-agent-app/target/test-agent-app-0.1.0-SNAPSHOT.jar"
LOG_DIR="${ROOT_DIR}/.tmp/dev-services"
BACKEND_SCREEN_SESSION="test-agent-backend"
FRONTEND_SCREEN_SESSION="test-agent-frontend"

profile="local"
env_file=""
skip_backend_build=false
skip_frontend_build=false

usage() {
  cat <<'USAGE'
Usage: ./restart-dev-services.sh [--profile local|test] [--env-file <path>] [--log-dir <path>] [--skip-backend-build] [--skip-frontend-build] [--help]

Compile and restart the local platform backend and frontend services.

Defaults:
  backend profile: local
  backend env:     .env.local
  backend URL:     TEST_AGENT_BASE_URL or http://127.0.0.1:8080
  frontend URL:    TEST_AGENT_FRONTEND_URL or http://127.0.0.1:3000
  logs:            .tmp/dev-services/
  screen sessions: test-agent-backend, test-agent-frontend when screen is available

Options:
  --profile              Backend Spring profile, local or test. Default: local.
  --env-file             Backend dotenv file. Relative paths are resolved from the repo root.
  --log-dir              Service log directory. Relative paths are resolved from the repo root.
  --skip-backend-build   Restart backend without running Maven package first.
  --skip-frontend-build  Restart frontend without running pnpm build first.
  --help                 Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
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
    --skip-backend-build)
      skip_backend_build=true
      shift
      ;;
    --skip-frontend-build)
      skip_frontend_build=true
      shift
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

if [[ -z "${env_file}" ]]; then
  env_file="${ROOT_DIR}/.env.${profile}"
elif [[ "${env_file}" != /* ]]; then
  env_file="${ROOT_DIR}/${env_file}"
fi

if [[ "${LOG_DIR}" != /* ]]; then
  LOG_DIR="${ROOT_DIR}/${LOG_DIR}"
fi

frontend_url="${TEST_AGENT_FRONTEND_URL:-http://127.0.0.1:3000}"
backend_url="${TEST_AGENT_BASE_URL:-http://127.0.0.1:8080}"
frontend_hostport="${frontend_url#http://}"
frontend_hostport="${frontend_hostport#https://}"
frontend_hostport="${frontend_hostport%%/*}"
if [[ "${frontend_hostport}" == *:* ]]; then
  frontend_port="${FRONTEND_PORT:-${frontend_hostport##*:}}"
else
  frontend_port="${FRONTEND_PORT:-3000}"
fi

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

# 安全读取 dotenv，只解析 KEY=VALUE，不执行文件内容，避免本地密钥文件被当作 shell 脚本运行。
load_env_file() {
  local file="$1"
  local line key value

  if [[ ! -f "${file}" ]]; then
    echo "Missing env file: ${file}" >&2
    echo "Create it from the backend README example. Do not commit it." >&2
    exit 1
  fi

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    if [[ "${line}" == export\ * ]]; then
      line="${line#export }"
    fi
    if [[ "${line}" != *=* ]]; then
      echo "Invalid env line in ${file}: expected KEY=VALUE." >&2
      exit 1
    fi

    key="${line%%=*}"
    value="${line#*=}"
    if [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      echo "Invalid env key in ${file}: ${key}" >&2
      exit 1
    fi

    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi

    export "${key}=${value}"
  done < "${file}"
}

# 只匹配本仓库 test-agent-app 的可执行 jar，避免误杀其他 Java 服务。
backend_pids() {
  ps -eo pid=,command= | awk -v jar="${BACKEND_JAR}" '
    index($0, jar) && index($0, "java -jar") { print $1 }
  '
}

# 前端只匹配自研 agent-web dev 进程和它的 Next 子进程，不处理 frontend-opencode 参考目录。
frontend_pids() {
  ps -eo pid=,command= | awk -v frontend="${FRONTEND_DIR}" '
    index($0, "@test-agent/agent-web dev") ||
    (index($0, frontend) && index($0, "next dev")) ||
    (index($0, frontend) && index($0, ".next/dev/")) { print $1 }
  '
}

# 先温和停止，超时后强制清理，避免端口 8080/3000 被旧进程占用。
stop_pids() {
  local label="$1"
  shift
  local pids=("$@")
  local pid alive i

  if [[ "${#pids[@]}" -eq 0 ]]; then
    echo "No ${label} process to stop."
    return
  fi

  echo "Stopping ${label}: ${pids[*]}"
  kill "${pids[@]}" >/dev/null 2>&1 || true
  for ((i = 1; i <= 30; i++)); do
    alive=false
    for pid in "${pids[@]}"; do
      if kill -0 "${pid}" >/dev/null 2>&1; then
        alive=true
        break
      fi
    done
    [[ "${alive}" == "false" ]] && return
    sleep 0.5
  done

  echo "Force stopping ${label}: ${pids[*]}"
  kill -9 "${pids[@]}" >/dev/null 2>&1 || true
}

screen_session_exists() {
  local session="$1"
  screen -list 2>/dev/null | awk -v session="${session}" '
    index($0, "." session) { found = 1 }
    END { exit found ? 0 : 1 }
  '
}

# 停止脚本管理的 screen 会话，防止残留会话继续持有旧服务进程。
stop_screen_session() {
  local session="$1"
  if command -v screen >/dev/null 2>&1 && screen_session_exists "${session}"; then
    echo "Stopping screen session: ${session}"
    screen -S "${session}" -X quit >/dev/null 2>&1 || true
  fi
}

http_ok() {
  curl -fsS --max-time 5 "$1" >/dev/null 2>&1
}

# 等待 HTTP 入口可访问；失败时输出对应服务日志尾部便于定位。
wait_until_http_ok() {
  local label="$1"
  local url="$2"
  local log_file="$3"
  local attempts="${4:-90}"
  local i

  for ((i = 1; i <= attempts; i++)); do
    if http_ok "${url}"; then
      echo "OK ${label}: ${url}"
      return 0
    fi
    sleep 2
  done

  echo "Timed out waiting for ${label}: ${url}" >&2
  echo "${label} log tail:" >&2
  tail -n 120 "${log_file}" >&2 || true
  exit 1
}

build_backend() {
  if [[ "${skip_backend_build}" == "true" ]]; then
    echo "Skipping backend build."
    return
  fi

  echo "Building backend: mvn clean package -DskipTests"
  (cd "${BACKEND_DIR}" && mvn clean package -DskipTests)
}

build_frontend() {
  if [[ "${skip_frontend_build}" == "true" ]]; then
    echo "Skipping frontend build."
    return
  fi

  echo "Building frontend: corepack pnpm build"
  (cd "${FRONTEND_DIR}" && corepack pnpm build)
}

start_backend() {
  if [[ -z "${TEST_AGENT_OPENCODE_BASE_URL:-}" ]]; then
    echo "TEST_AGENT_OPENCODE_BASE_URL is required in ${env_file}." >&2
    exit 1
  fi

  mkdir -p "${LOG_DIR}"
  echo "Starting backend with profile '${profile}'. Logs: ${LOG_DIR}/backend.log"
  : >"${LOG_DIR}/backend.log"
  if command -v screen >/dev/null 2>&1; then
    local backend_cmd
    printf -v backend_cmd 'cd %q && exec java -jar %q --spring.profiles.active=%q >>%q 2>&1' \
      "${BACKEND_DIR}" "${BACKEND_JAR}" "${profile}" "${LOG_DIR}/backend.log"
    screen -dmS "${BACKEND_SCREEN_SESSION}" bash -lc "${backend_cmd}"
  else
    (
      cd "${BACKEND_DIR}"
      nohup java -jar "${BACKEND_JAR}" --spring.profiles.active="${profile}" \
        >>"${LOG_DIR}/backend.log" 2>&1 &
      echo "$!" >"${LOG_DIR}/backend.pid"
    )
  fi
  wait_until_http_ok "backend" "${backend_url}/actuator/health" "${LOG_DIR}/backend.log" 90
  backend_pids >"${LOG_DIR}/backend.pid"
}

start_frontend() {
  mkdir -p "${LOG_DIR}"
  echo "Starting frontend on port ${frontend_port}. Logs: ${LOG_DIR}/frontend.log"
  : >"${LOG_DIR}/frontend.log"
  if command -v screen >/dev/null 2>&1; then
    local frontend_cmd
    printf -v frontend_cmd 'cd %q && export PORT=%q && exec corepack pnpm dev >>%q 2>&1' \
      "${FRONTEND_DIR}" "${frontend_port}" "${LOG_DIR}/frontend.log"
    screen -dmS "${FRONTEND_SCREEN_SESSION}" bash -lc "${frontend_cmd}"
  else
    (
      cd "${FRONTEND_DIR}"
      PORT="${frontend_port}" nohup corepack pnpm dev >>"${LOG_DIR}/frontend.log" 2>&1 &
      echo "$!" >"${LOG_DIR}/frontend.pid"
    )
  fi
  wait_until_http_ok "frontend" "${frontend_url}" "${LOG_DIR}/frontend.log" 90
  frontend_pids >"${LOG_DIR}/frontend.pid"
}

require_command awk
require_command corepack
require_command curl
require_command java
require_command mvn

load_env_file "${env_file}"
export SPRING_PROFILES_ACTIVE="${profile}"

echo "Sensitive environment values are loaded but not printed."
echo "Builds run before stopping existing services; failed builds leave current services untouched."

build_backend
build_frontend

old_frontend_pids=()
# PID 输出只包含数字，使用普通 word splitting 避免 process substitution 被 sh 预解析时报错。
for pid in $(frontend_pids); do
  old_frontend_pids+=("${pid}")
done

old_backend_pids=()
for pid in $(backend_pids); do
  old_backend_pids+=("${pid}")
done

if [[ "${#old_frontend_pids[@]}" -gt 0 ]]; then
  stop_pids "frontend" "${old_frontend_pids[@]}"
else
  stop_pids "frontend"
fi

if [[ "${#old_backend_pids[@]}" -gt 0 ]]; then
  stop_pids "backend" "${old_backend_pids[@]}"
else
  stop_pids "backend"
fi
stop_screen_session "${FRONTEND_SCREEN_SESSION}"
stop_screen_session "${BACKEND_SCREEN_SESSION}"

start_backend
start_frontend

echo "Restart complete."
echo "Backend:  ${backend_url}"
echo "Frontend: ${frontend_url}"
echo "Logs:     ${LOG_DIR}"
if command -v screen >/dev/null 2>&1; then
  echo "Screen:   ${BACKEND_SCREEN_SESSION}, ${FRONTEND_SCREEN_SESSION}"
fi
