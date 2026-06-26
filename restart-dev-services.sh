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
OPENCODE_SCREEN_SESSION="test-agent-opencode"
OPENCODE_MANAGER_SCREEN_SESSION="test-agent-opencode-manager"

profile="test"
env_file=""
skip_backend_build=false
skip_frontend_build=false

usage() {
  cat <<'USAGE'
Usage: ./restart-dev-services.sh [--profile local|test|guo] [--env-file <path>] [--log-dir <path>] [--skip-backend-build] [--skip-frontend-build] [--help]

Compile and restart the local platform services one by one. Each service is
stopped (kill old process + screen session) before its new instance starts,
in dependency order: backend -> opencode-manager -> frontend.

Services managed by this script:
  backend           Spring Boot test-agent-app (java -jar, profile from --profile).
  opencode-manager  Go opencode-manager supervisor (./opencode-manager/bin/opencode-manager run).
                    Started by default when TEST_AGENT_OPENCODE_BASE_URL is a local URL.
                    Standalone `opencode serve` is NOT started separately when the
                    manager runs, because the manager spawns opencode child processes.
  frontend          agent-web Vite dev server (corepack pnpm dev).

Defaults:
  backend profile: test
  backend env:     .env.test
  backend URL:     TEST_AGENT_BASE_URL or http://127.0.0.1:8080
  frontend URL:    TEST_AGENT_FRONTEND_URL or http://127.0.0.1:3000
  manager token:   TEST_AGENT_OPENCODE_MANAGER_TOKEN or local-manager-token (dev/test default)
  logs:            .tmp/dev-services/
  screen sessions: test-agent-backend, test-agent-frontend, test-agent-opencode-manager when screen is available

Options:
  --profile              Backend Spring profile, local, test, or guo. Default: test.
  --env-file             Backend dotenv file. Relative paths are resolved from the repo root.
  --log-dir              Service log directory. Relative paths are resolved from the repo root.
  --skip-backend-build   Restart backend without running Maven package first.
  --skip-frontend-build  Restart frontend without running pnpm build first.
  --help                 Show this help.

Environment overrides:
  TEST_AGENT_START_OPENCODE_MANAGER  auto|true|false. Set false to skip the Go manager.
  TEST_AGENT_OPENCODE_MANAGER_TOKEN  Shared secret between manager and backend. Defaults to local-manager-token.
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
  local|test|guo)
    ;;
  *)
    echo "Unsupported profile: ${profile}. Expected local, test, or guo." >&2
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
frontend_host="127.0.0.1"
frontend_port="3000"

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

# 前端只匹配自研 agent-web Vite dev 进程，不处理 frontend-opencode 参考目录。
frontend_pids() {
  ps -eo pid=,command= | awk -v frontend="${FRONTEND_DIR}" '
    index($0, "@test-agent/agent-web dev") ||
    (index($0, frontend) && index($0, "vite")) { print $1 }
  '
}

url_port() {
  local url="$1"
  local hostport
  hostport="${url#*://}"
  hostport="${hostport%%/*}"
  if [[ "${hostport}" == *:* ]]; then
    echo "${hostport##*:}"
  elif [[ "${url}" == https://* ]]; then
    echo 443
  else
    echo 80
  fi
}

url_host() {
  local url="$1"
  local hostport host
  hostport="${url#*://}"
  hostport="${hostport%%/*}"
  host="${hostport%:*}"
  host="${host#[}"
  host="${host%]}"
  echo "${host}"
}

derive_frontend_runtime_settings() {
  local hostport
  hostport="${frontend_url#http://}"
  hostport="${hostport#https://}"
  hostport="${hostport%%/*}"
  if [[ "${hostport}" == *:* ]]; then
    frontend_port="${FRONTEND_PORT:-${hostport##*:}}"
  else
    frontend_port="${FRONTEND_PORT:-3000}"
  fi
  frontend_host="${FRONTEND_HOST:-$(url_host "${frontend_url}")}"
  if [[ -z "${FRONTEND_HOST:-}" && "${frontend_host}" != "127.0.0.1" && "${frontend_host}" != "localhost" && "${frontend_host}" != "::1" ]]; then
    frontend_host="0.0.0.0"
  fi
}

apply_frontend_origin_defaults() {
  local default_origins
  default_origins="http://localhost:3000,http://127.0.0.1:3000,http://localhost:4173,http://127.0.0.1:4173,http://localhost:4177,http://127.0.0.1:4177,http://localhost:4187,http://127.0.0.1:4187,http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174"
  if [[ -z "${TEST_AGENT_CORS_ALLOWED_ORIGINS:-}" ]]; then
    TEST_AGENT_CORS_ALLOWED_ORIGINS="${default_origins}"
  fi
  case ",${TEST_AGENT_CORS_ALLOWED_ORIGINS}," in
    *",${frontend_url},"*) ;;
    *) TEST_AGENT_CORS_ALLOWED_ORIGINS="${TEST_AGENT_CORS_ALLOWED_ORIGINS},${frontend_url}" ;;
  esac
  if [[ "${frontend_url}" != "http://127.0.0.1:${frontend_port}" && ",${TEST_AGENT_CORS_ALLOWED_ORIGINS}," != *",http://127.0.0.1:${frontend_port},"* ]]; then
    TEST_AGENT_CORS_ALLOWED_ORIGINS="${TEST_AGENT_CORS_ALLOWED_ORIGINS},http://127.0.0.1:${frontend_port}"
  fi
  export TEST_AGENT_CORS_ALLOWED_ORIGINS
}

detect_local_ipv4() {
  local iface ip

  # 优先使用默认路由网卡，避免把 loopback、Docker 网桥或隧道地址注册成运行服务器 IP。
  if command -v route >/dev/null 2>&1; then
    iface="$(route -n get default 2>/dev/null | awk '/interface:/{print $2; exit}')"
    if [[ -n "${iface}" && "$(uname -s)" == "Darwin" ]] && command -v ipconfig >/dev/null 2>&1; then
      ip="$(ipconfig getifaddr "${iface}" 2>/dev/null || true)"
      if is_routable_ipv4 "${ip}"; then
        echo "${ip}"
        return 0
      fi
    fi
  fi

  if command -v ip >/dev/null 2>&1; then
    ip="$(ip route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i <= NF; i++) if ($i == "src") {print $(i + 1); exit}}')"
    if is_routable_ipv4 "${ip}"; then
      echo "${ip}"
      return 0
    fi
  fi

  if command -v hostname >/dev/null 2>&1; then
    ip="$(hostname -I 2>/dev/null | awk '{for (i = 1; i <= NF; i++) if ($i !~ /^127\\./ && $i !~ /^169\\.254\\./) {print $i; exit}}')"
    if is_routable_ipv4 "${ip}"; then
      echo "${ip}"
      return 0
    fi
  fi

  return 1
}

is_routable_ipv4() {
  local ip="$1"
  [[ "${ip}" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || return 1
  [[ "${ip}" != 127.* && "${ip}" != 169.254.* && "${ip}" != 0.0.0.0 ]]
}

apply_detected_runtime_ip_defaults() {
  local local_ipv4 backend_port
  local_ipv4="$(detect_local_ipv4 || true)"
  if [[ -z "${local_ipv4}" ]]; then
    return
  fi

  if [[ -z "${TEST_AGENT_LINUX_SERVER_ID:-}" ]]; then
    export TEST_AGENT_LINUX_SERVER_ID="${local_ipv4}"
    echo "Defaulting TEST_AGENT_LINUX_SERVER_ID to detected local IPv4: ${TEST_AGENT_LINUX_SERVER_ID}"
  fi
  if [[ -z "${TEST_AGENT_BACKEND_LISTEN_URL:-}" ]]; then
    backend_port="$(url_port "${backend_url}")"
    export TEST_AGENT_BACKEND_LISTEN_URL="http://${local_ipv4}:${backend_port}"
    echo "Defaulting TEST_AGENT_BACKEND_LISTEN_URL to detected local IPv4: ${TEST_AGENT_BACKEND_LISTEN_URL}"
  fi
  if [[ -z "${OPENCODE_MANAGER_LINUX_SERVER_ID:-}" ]]; then
    export OPENCODE_MANAGER_LINUX_SERVER_ID="${TEST_AGENT_LINUX_SERVER_ID}"
  fi
}

is_local_opencode_url() {
  local url="$1"
  [[ "${url}" == http://127.0.0.1:* || "${url}" == http://localhost:* || "${url}" == http://[::1]:* ]]
}

should_start_opencode_manager() {
  case "${TEST_AGENT_START_OPENCODE_MANAGER:-auto}" in
    true|TRUE|1|yes|YES)
      return 0
      ;;
    false|FALSE|0|no|NO)
      return 1
      ;;
    auto|"")
      # token 已有默认值，这里改用 TEST_AGENT_OPENCODE_BASE_URL 是否配置且指向本机作为启动判据；
      # 校验环境的占位 env 不配置该地址，远端 opencode 环境也不应拉起本地 manager。
      [[ -n "${TEST_AGENT_OPENCODE_BASE_URL:-}" ]] && is_local_opencode_url "${TEST_AGENT_OPENCODE_BASE_URL}"
      ;;
    *)
      echo "Invalid TEST_AGENT_START_OPENCODE_MANAGER: ${TEST_AGENT_START_OPENCODE_MANAGER}" >&2
      exit 1
      ;;
  esac
}

should_start_opencode() {
  case "${TEST_AGENT_START_OPENCODE:-auto}" in
    true|TRUE|1|yes|YES)
      return 0
      ;;
    false|FALSE|0|no|NO)
      return 1
      ;;
    auto|"")
      if should_start_opencode_manager; then
        return 1
      fi
      is_local_opencode_url "${TEST_AGENT_OPENCODE_BASE_URL:-}"
      ;;
    *)
      echo "Invalid TEST_AGENT_START_OPENCODE: ${TEST_AGENT_START_OPENCODE}" >&2
      exit 1
      ;;
  esac
}

opencode_bin() {
  if [[ -n "${TEST_AGENT_OPENCODE_BIN:-}" ]]; then
    echo "${TEST_AGENT_OPENCODE_BIN}"
    return
  fi
  if [[ -x "${HOME}/.opencode/bin/opencode" ]]; then
    echo "${HOME}/.opencode/bin/opencode"
    return
  fi
  command -v opencode || true
}

# 只清理脚本管理端口上的 opencode serve，避免误杀其他 opencode 客户端。
opencode_pids() {
  local port
  port="$(url_port "${TEST_AGENT_OPENCODE_BASE_URL}")"
  ps -eo pid=,command= | awk -v port="${port}" '
    index($0, "opencode serve") && index($0, "--port " port) { print $1 }
  '
}

opencode_manager_pids() {
  ps -eo pid=,command= | awk -v root="${ROOT_DIR}" '
    index($0, root "/opencode-manager/bin/opencode-manager run") ||
    index($0, "./opencode-manager/bin/opencode-manager run") { print $1 }
  '
}

should_seed_demo_workspaces() {
  case "${TEST_AGENT_SEED_DEMO_WORKSPACES:-auto}" in
    true|TRUE|1|yes|YES)
      return 0
      ;;
    false|FALSE|0|no|NO)
      return 1
      ;;
    auto|"")
      should_start_opencode
      ;;
    *)
      echo "Invalid TEST_AGENT_SEED_DEMO_WORKSPACES: ${TEST_AGENT_SEED_DEMO_WORKSPACES}" >&2
      exit 1
      ;;
  esac
}

# 本地种子数据中的 F-COSS 工作区指向 /tmp/test-agent/fcoss/*；启动时补齐缺失目录，避免默认入口无法运行。
seed_demo_workspaces() {
  if ! should_seed_demo_workspaces; then
    return
  fi

  local source_dir="${ROOT_DIR}/test-workspaces/F-COSS"
  local version dest

  # V10 种子数据：F-COSS 主服务两个版本（src/main 子目录）
  for version in 20260620 20260701; do
    dest="/tmp/test-agent/fcoss/${version}"
    mkdir -p "${dest}/src/main"
    if [[ -d "${source_dir}" ]] && [[ ! -e "${dest}/README.md" ]]; then
      cp -R "${source_dir}/." "${dest}/"
    elif [[ ! -e "${dest}/README.md" ]]; then
      echo "# F-COSS Demo Workspace" > "${dest}/README.md"
    fi
  done

  # V13 种子数据：F-COSS 移动端（src/mobile）、数据同步（sync）、报表（reports）
  local workspace_dirs=(
    "/tmp/test-agent/fcoss/mobile/20260705:src/mobile"
    "/tmp/test-agent/fcoss/sync/20260710:sync"
    "/tmp/test-agent/fcoss/report/20260715:reports"
  )
  for entry in "${workspace_dirs[@]}"; do
    dest="${entry%%:*}"
    local sub_dir="${entry##*:}"
    mkdir -p "${dest}/${sub_dir}"
    if [[ -d "${source_dir}" ]] && [[ ! -e "${dest}/README.md" ]]; then
      cp -R "${source_dir}/." "${dest}/"
    elif [[ ! -e "${dest}/README.md" ]]; then
      echo "# F-COSS Demo Workspace" > "${dest}/README.md"
    fi
  done
}

# 先温和停止，超时后强制清理，避免端口 8080/3000/4096 被旧进程占用。
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

# 逐个服务的「先 kill 原进程再启动」停止步骤：清理进程 + 对应 screen 会话。
stop_backend_service() {
  local pids=()
  local pid
  for pid in $(backend_pids); do
    pids+=("${pid}")
  done
  if [[ "${#pids[@]}" -gt 0 ]]; then
    stop_pids "backend" "${pids[@]}"
  else
    stop_pids "backend"
  fi
  stop_screen_session "${BACKEND_SCREEN_SESSION}"
}

stop_frontend_service() {
  local pids=()
  local pid
  for pid in $(frontend_pids); do
    pids+=("${pid}")
  done
  if [[ "${#pids[@]}" -gt 0 ]]; then
    stop_pids "frontend" "${pids[@]}"
  else
    stop_pids "frontend"
  fi
  stop_screen_session "${FRONTEND_SCREEN_SESSION}"
}

# manager 接管 opencode 子进程后，需同时清理可能残留的 standalone opencode serve，避免 4096 端口冲突。
stop_opencode_manager_service() {
  local pids=()
  local pid
  for pid in $(opencode_manager_pids); do
    pids+=("${pid}")
  done
  if [[ "${#pids[@]}" -gt 0 ]]; then
    stop_pids "opencode-manager" "${pids[@]}"
  else
    stop_pids "opencode-manager"
  fi
  stop_screen_session "${OPENCODE_MANAGER_SCREEN_SESSION}"

  local opencode_pids=()
  for pid in $(opencode_pids); do
    opencode_pids+=("${pid}")
  done
  if [[ "${#opencode_pids[@]}" -gt 0 ]]; then
    stop_pids "opencode serve" "${opencode_pids[@]}"
  fi
  stop_screen_session "${OPENCODE_SCREEN_SESSION}"
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

  echo "Building backend: mvn clean package -Dmaven.test.skip=true"
  (cd "${BACKEND_DIR}" && mvn clean package -Dmaven.test.skip=true)
}

build_frontend() {
  if [[ "${skip_frontend_build}" == "true" ]]; then
    echo "Skipping frontend build."
    return
  fi

  echo "Building frontend: corepack pnpm build"
  (cd "${FRONTEND_DIR}" && corepack pnpm build)
}

build_opencode_manager() {
  if ! should_start_opencode_manager; then
    return
  fi
  require_command go
  echo "Building opencode-manager: go build"
  (cd "${ROOT_DIR}/opencode-manager" && go build -o bin/opencode-manager ./cmd/opencode-manager)
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
  wait_until_http_ok "backend" "${backend_url}/actuator/health/readiness" "${LOG_DIR}/backend.log" 90
  backend_pids >"${LOG_DIR}/backend.pid"
}

start_opencode() {
  if ! should_start_opencode; then
    echo "Skipping opencode startup for non-local TEST_AGENT_OPENCODE_BASE_URL=${TEST_AGENT_OPENCODE_BASE_URL:-}."
    return
  fi

  local bin host port config_url version
  bin="$(opencode_bin)"
  if [[ -z "${bin}" || ! -x "${bin}" ]]; then
    echo "opencode binary not found or not executable. Set TEST_AGENT_OPENCODE_BIN in ${env_file}." >&2
    exit 1
  fi
  host="$(url_host "${TEST_AGENT_OPENCODE_BASE_URL}")"
  port="$(url_port "${TEST_AGENT_OPENCODE_BASE_URL}")"
  config_url="${TEST_AGENT_OPENCODE_BASE_URL%/}/config"
  version="$("${bin}" --version 2>/dev/null || true)"

  mkdir -p "${LOG_DIR}"
  echo "Starting opencode ${version:-unknown} on ${TEST_AGENT_OPENCODE_BASE_URL}. Logs: ${LOG_DIR}/opencode.log"
  : >"${LOG_DIR}/opencode.log"
  if command -v screen >/dev/null 2>&1; then
    local opencode_cmd
    printf -v opencode_cmd 'cd %q && exec %q serve --hostname %q --port %q --cors %q --cors %q --print-logs >>%q 2>&1' \
      "${ROOT_DIR}" "${bin}" "${host}" "${port}" "http://localhost:${frontend_port}" "http://127.0.0.1:${frontend_port}" "${LOG_DIR}/opencode.log"
    screen -dmS "${OPENCODE_SCREEN_SESSION}" bash -lc "${opencode_cmd}"
  else
    (
      cd "${ROOT_DIR}"
      nohup "${bin}" serve --hostname "${host}" --port "${port}" \
        --cors "http://localhost:${frontend_port}" --cors "http://127.0.0.1:${frontend_port}" --print-logs \
        >>"${LOG_DIR}/opencode.log" 2>&1 &
      echo "$!" >"${LOG_DIR}/opencode.pid"
    )
  fi
  wait_until_http_ok "opencode" "${config_url}" "${LOG_DIR}/opencode.log" 60
  opencode_pids >"${LOG_DIR}/opencode.pid"
}

start_opencode_manager() {
  if ! should_start_opencode_manager; then
    echo "Skipping opencode-manager startup."
    return
  fi

  local bin port_start port_end max_processes manager_id container_id linux_server_id manager_state_dir manager_session_root opencode_config_dir discovery_url version
  bin="$(opencode_bin)"
  if [[ -z "${bin}" || ! -x "${bin}" ]]; then
    echo "opencode binary not found or not executable. Set TEST_AGENT_OPENCODE_BIN in ${env_file}." >&2
    exit 1
  fi

  port_start="${OPENCODE_MANAGER_PORT_START:-$(url_port "${TEST_AGENT_OPENCODE_BASE_URL:-http://127.0.0.1:4096}")}"
  port_end="${OPENCODE_MANAGER_PORT_END:-$((port_start + 9))}"
  max_processes="${OPENCODE_MANAGER_MAX_PROCESSES:-$((port_end - port_start + 1))}"
  manager_id="${OPENCODE_MANAGER_ID:-mgr_local_opencode}"
  container_id="${OPENCODE_MANAGER_CONTAINER_ID:-ctr_local_opencode}"
  linux_server_id="${OPENCODE_MANAGER_LINUX_SERVER_ID:-127.0.0.1}"
  manager_state_dir="${OPENCODE_MANAGER_STATE_DIR:-${LOG_DIR}/opencode-manager-state}"
  manager_session_root="${OPENCODE_SESSION_ROOT:-${LOG_DIR}/opencode-manager-session}"
  opencode_config_dir="${OPENCODE_CONFIG_DIR:-${HOME}/.config/opencode/}"
  discovery_url="${OPENCODE_MANAGER_BACKEND_DISCOVERY_URL:-${backend_url%/}/api/internal/platform/opencode-runtime/manager-backends}"
  version="$("${bin}" --version 2>/dev/null || true)"

  mkdir -p "${LOG_DIR}" "${manager_state_dir}" "${manager_session_root}"
  echo "Starting opencode-manager for ${container_id} (${version:-opencode unknown}). Logs: ${LOG_DIR}/opencode-manager.log"
  : >"${LOG_DIR}/opencode-manager.log"
  if command -v screen >/dev/null 2>&1; then
    local manager_cmd
    printf -v manager_cmd 'cd %q && export OPENCODE_MANAGER_CONTAINER_ID=%q OPENCODE_MANAGER_LINUX_SERVER_ID=%q OPENCODE_MANAGER_PORT_START=%q OPENCODE_MANAGER_PORT_END=%q OPENCODE_MANAGER_MAX_PROCESSES=%q OPENCODE_MANAGER_ID=%q OPENCODE_MANAGER_BACKEND_DISCOVERY_URL=%q OPENCODE_MANAGER_TOKEN="$TEST_AGENT_OPENCODE_MANAGER_TOKEN" OPENCODE_MANAGER_STATE_DIR=%q OPENCODE_SESSION_ROOT=%q OPENCODE_CONFIG_DIR=%q OPENCODE_BIN=%q OPENCODE_ALLOWED_CORS=%q OPENCODE_MANAGER_DISCOVERY_INTERVAL="${OPENCODE_MANAGER_DISCOVERY_INTERVAL:-2s}" OPENCODE_MANAGER_HEARTBEAT_INTERVAL="${OPENCODE_MANAGER_HEARTBEAT_INTERVAL:-2s}" OPENCODE_MANAGER_RECONNECT_INTERVAL="${OPENCODE_MANAGER_RECONNECT_INTERVAL:-1s}" && exec ./opencode-manager/bin/opencode-manager run >>%q 2>&1' \
      "${ROOT_DIR}" "${container_id}" "${linux_server_id}" "${port_start}" "${port_end}" "${max_processes}" "${manager_id}" "${discovery_url}" "${manager_state_dir}" "${manager_session_root}" "${opencode_config_dir}" "${bin}" "http://localhost:${frontend_port},http://127.0.0.1:${frontend_port}" "${LOG_DIR}/opencode-manager.log"
    screen -dmS "${OPENCODE_MANAGER_SCREEN_SESSION}" bash -lc "${manager_cmd}"
  else
    (
      cd "${ROOT_DIR}"
      export OPENCODE_MANAGER_CONTAINER_ID="${container_id}"
      export OPENCODE_MANAGER_LINUX_SERVER_ID="${linux_server_id}"
      export OPENCODE_MANAGER_PORT_START="${port_start}"
      export OPENCODE_MANAGER_PORT_END="${port_end}"
      export OPENCODE_MANAGER_MAX_PROCESSES="${max_processes}"
      export OPENCODE_MANAGER_ID="${manager_id}"
      export OPENCODE_MANAGER_BACKEND_DISCOVERY_URL="${discovery_url}"
      export OPENCODE_MANAGER_TOKEN="${TEST_AGENT_OPENCODE_MANAGER_TOKEN}"
      export OPENCODE_MANAGER_STATE_DIR="${manager_state_dir}"
      export OPENCODE_SESSION_ROOT="${manager_session_root}"
      export OPENCODE_CONFIG_DIR="${opencode_config_dir}"
      export OPENCODE_BIN="${bin}"
      export OPENCODE_ALLOWED_CORS="http://localhost:${frontend_port},http://127.0.0.1:${frontend_port}"
      export OPENCODE_MANAGER_DISCOVERY_INTERVAL="${OPENCODE_MANAGER_DISCOVERY_INTERVAL:-2s}"
      export OPENCODE_MANAGER_HEARTBEAT_INTERVAL="${OPENCODE_MANAGER_HEARTBEAT_INTERVAL:-2s}"
      export OPENCODE_MANAGER_RECONNECT_INTERVAL="${OPENCODE_MANAGER_RECONNECT_INTERVAL:-1s}"
      nohup ./opencode-manager/bin/opencode-manager run >>"${LOG_DIR}/opencode-manager.log" 2>&1 &
      echo "$!" >"${LOG_DIR}/opencode-manager.pid"
    )
  fi
  sleep 3
  opencode_manager_pids >"${LOG_DIR}/opencode-manager.pid"
  if [[ ! -s "${LOG_DIR}/opencode-manager.pid" ]]; then
    echo "opencode-manager failed to stay running." >&2
    tail -n 120 "${LOG_DIR}/opencode-manager.log" >&2 || true
    exit 1
  fi
}

start_frontend() {
  mkdir -p "${LOG_DIR}"
  echo "Starting frontend on ${frontend_host}:${frontend_port}. Logs: ${LOG_DIR}/frontend.log"
  : >"${LOG_DIR}/frontend.log"
  if command -v screen >/dev/null 2>&1; then
    local frontend_cmd
    printf -v frontend_cmd 'cd %q && export HOST=%q PORT=%q VITE_TEST_AGENT_API_BASE_URL=%q && exec corepack pnpm dev >>%q 2>&1' \
      "${FRONTEND_DIR}" "${frontend_host}" "${frontend_port}" "${backend_url}" "${LOG_DIR}/frontend.log"
    screen -dmS "${FRONTEND_SCREEN_SESSION}" bash -lc "${frontend_cmd}"
  else
    (
      cd "${FRONTEND_DIR}"
      HOST="${frontend_host}" PORT="${frontend_port}" VITE_TEST_AGENT_API_BASE_URL="${backend_url}" \
        nohup corepack pnpm dev >>"${LOG_DIR}/frontend.log" 2>&1 &
      echo "$!" >"${LOG_DIR}/frontend.pid"
    )
  fi
  wait_until_http_ok "frontend" "${frontend_url}" "${LOG_DIR}/frontend.log" 90
  frontend_pids >"${LOG_DIR}/frontend.pid"
}

load_env_file "${env_file}"
backend_url="${TEST_AGENT_BASE_URL:-${backend_url}}"
frontend_url="${TEST_AGENT_FRONTEND_URL:-${frontend_url}}"
derive_frontend_runtime_settings
apply_frontend_origin_defaults
apply_detected_runtime_ip_defaults
export SPRING_PROFILES_ACTIVE="${profile}"

# 开发和测试默认给 opencode-manager 一个与后端共享的 token，避免每次手配本机 dotenv。
# 与 application-guo.yml 的 local-manager-token 约定一致；local/test profile 后端从同一环境变量读取，自动匹配。
if [[ -z "${TEST_AGENT_OPENCODE_MANAGER_TOKEN:-}" ]]; then
  export TEST_AGENT_OPENCODE_MANAGER_TOKEN="local-manager-token"
  echo "Defaulting TEST_AGENT_OPENCODE_MANAGER_TOKEN to local-manager-token for local opencode-manager."
fi

# 设置 JAVA_HOME
java_version="${JAVA_VERSION:-21}"
if [[ -n "${JAVA_VERSION:-}" ]] || [[ -z "${JAVA_HOME:-}" ]]; then
  # 如果指定了 JAVA_VERSION 或 JAVA_HOME 未设置，则自动查找
  if [[ "$(uname -s)" == "Darwin" ]]; then
    detected_home="$(/usr/libexec/java_home -v "${java_version}" 2>/dev/null || true)"
    if [[ -n "${detected_home}" ]]; then
      JAVA_HOME="${detected_home}"
    fi
  fi
  if [[ -z "${JAVA_HOME:-}" ]]; then
    # macOS 以外或 java_home 未找到时的常见路径
    for candidate in \
      "${HOME}/Library/Java/JavaVirtualMachines/openjdk-${java_version}.0.1/Contents/Home" \
      "/Library/Java/JavaVirtualMachines/openjdk-${java_version}/Contents/Home" \
      "/usr/lib/jvm/java-${java_version}" \
      "/usr/lib/jvm/openjdk-${java_version}" \
      "${HOME}/.sdkman/candidates/java/current"; do
      if [[ -d "${candidate}" ]]; then
        JAVA_HOME="${candidate}"
        break
      fi
    done
  fi
  if [[ -n "${JAVA_HOME:-}" ]]; then
    export JAVA_HOME
    echo "JAVA_HOME set to: ${JAVA_HOME}"
  fi
fi

require_command awk
require_command corepack
require_command curl
require_command java
require_command mvn

seed_demo_workspaces

echo "Sensitive environment values are loaded but not printed."
echo "Builds run before stopping existing services; failed builds leave current services untouched."

# 先统一构建：任一构建失败则直接退出，不会动到现有运行中的服务。
build_backend
build_opencode_manager
build_frontend

# 逐个服务「先 kill 原进程再启动」，按依赖顺序：后端 -> opencode-manager -> 前端。
# 后端最先：opencode-manager 要发现后端实例，前端要调用后端 API。

# 1) 后端
stop_backend_service
start_backend

# 2) opencode-manager（Go 管理进程）。非本地环境 should_start_opencode_manager 为 false 时自动跳过，
#    start_opencode_manager 内部也会跳过；stop 步骤仍会清理残留 manager 与 standalone opencode serve。
stop_opencode_manager_service
start_opencode_manager

# 3) 前端
stop_frontend_service
start_frontend

echo "Restart complete."
echo "Backend:  ${backend_url}"
echo "Frontend: ${frontend_url}"
echo "Logs:     ${LOG_DIR}"
if command -v screen >/dev/null 2>&1; then
  echo "Screen:   ${BACKEND_SCREEN_SESSION}, ${OPENCODE_MANAGER_SCREEN_SESSION}, ${FRONTEND_SCREEN_SESSION}"
fi
