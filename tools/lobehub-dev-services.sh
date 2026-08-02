#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
FORK_DIR="${TEST_AGENT_LOBEHUB_FORK_DIR:-$(cd "${ROOT_DIR}/.." && pwd -P)/lobehub-platform}"
LOG_DIR="${TEST_AGENT_DEV_LOG_DIR:-${ROOT_DIR}/.tmp/dev-services}"
ENV_FILE="${LOBEHUB_DEV_ENV_FILE:-${LOG_DIR}/lobehub-dev.env}"
COMPOSE_FILE="${ROOT_DIR}/deploy/dev/lobehub-compose.yml"
SCREEN_SESSION="test-agent-lobehub"
SCHEDULER_SCREEN_SESSION="test-agent-lobehub-scheduler"
APP_URL="${LOBEHUB_DEV_APP_URL:-http://127.0.0.1:3210}"

usage() {
  cat <<'USAGE'
Usage: tools/lobehub-dev-services.sh <prepare|restart|stop|status|--help>

Manages the opt-in LobeHub local development service. `prepare` creates a
0600 runtime env under .tmp without changing .env.local. `restart` starts the
dev-only ParadeDB/RustFS dependencies, runs the fork migration, and starts the
fork on http://127.0.0.1:3210. Redis is reused from TEST_AGENT_REDIS_* with the
dedicated REDIS_PREFIX=lobehub:app setting (actual keys are lobehub:app:*).
The platform restart script calls this helper only when --with-lobehub is supplied.
The generated env also opts test/local backend profiles into an audited common-
parameter bootstrap; that bootstrap rejects non-loopback platform PostgreSQL.

Overrides:
  TEST_AGENT_LOBEHUB_FORK_DIR  Independent fork directory (default: ../lobehub-platform).
  LOBEHUB_DEV_ENV_FILE         Generated runtime env path.
  LOBEHUB_DEV_APP_URL          Local LobeHub URL (default: http://127.0.0.1:3210).
  TEST_AGENT_LOBEHUB_DEV_OWNER_UNIFIED_AUTH_ID
                               Explicit local owner when no unique eligible super admin exists.
USAGE
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

urlencode() {
  LOBEHUB_URL_VALUE="$1" node -e 'process.stdout.write(encodeURIComponent(process.env.LOBEHUB_URL_VALUE || ""))'
}

random_hex() {
  openssl rand -hex "$1"
}

random_base64() {
  openssl rand -base64 "$1" | tr -d '\n'
}

read_env_value() {
  local key="$1"
  awk -F= -v wanted="${key}" '$1 == wanted {sub(/^[^=]*=/, ""); print; exit}' "${ENV_FILE}"
}

validate_generated_value() {
  local key="$1" value="$2"
  [[ "${value}" != *$'\n'* && "${value}" != *$'\r'* ]] || {
    echo "LobeHub development setting ${key} must be a single-line value" >&2
    exit 1
  }
}

write_env_file() {
  require_command node
  require_command openssl
  mkdir -p "${LOG_DIR}"
  [[ ! -L "${ENV_FILE}" ]] || {
    echo "LobeHub development env must not be a symbolic link: ${ENV_FILE}" >&2
    exit 1
  }

  local hmac encryption auth_secret vault_secret scheduler_secret db_password rustfs_access rustfs_secret
  if [[ -f "${ENV_FILE}" ]]; then
    hmac="$(read_env_value PLATFORM_SSO_HMAC_SECRET)"
    encryption="$(read_env_value PLATFORM_MODEL_GRANT_ENCRYPTION_KEY)"
    auth_secret="$(read_env_value AUTH_SECRET)"
    vault_secret="$(read_env_value KEY_VAULTS_SECRET)"
    scheduler_secret="$(read_env_value ENTERPRISE_INTERNAL_SCHEDULER_SECRET)"
    db_password="$(read_env_value LOBEHUB_DEV_DB_PASSWORD)"
    rustfs_access="$(read_env_value RUSTFS_ACCESS_KEY)"
    rustfs_secret="$(read_env_value RUSTFS_SECRET_KEY)"
  fi

  hmac="${hmac:-$(random_hex 32)}"
  encryption="${encryption:-$(random_base64 32)}"
  auth_secret="${auth_secret:-$(random_hex 32)}"
  vault_secret="${vault_secret:-$(random_base64 32)}"
  scheduler_secret="${scheduler_secret:-$(random_hex 32)}"
  db_password="${db_password:-$(random_hex 20)}"
  rustfs_access="${rustfs_access:-dev$(random_hex 8)}"
  rustfs_secret="${rustfs_secret:-$(random_hex 24)}"

  local redis_host redis_port redis_password redis_username redis_url
  redis_host="${TEST_AGENT_REDIS_HOST:-127.0.0.1}"
  redis_port="${TEST_AGENT_REDIS_PORT:-6379}"
  redis_password="${TEST_AGENT_REDIS_PASSWORD:-}"
  redis_username="${TEST_AGENT_REDIS_USERNAME:-}"
  if [[ -n "${redis_password}" ]]; then
    redis_url="redis://"
    if [[ -n "${redis_username}" ]]; then
      redis_url+="$(urlencode "${redis_username}")"
    fi
    redis_url+=":$(urlencode "${redis_password}")@${redis_host}:${redis_port}/0"
  else
    redis_url="redis://${redis_host}:${redis_port}/0"
  fi

  local app_origin platform_frontend platform_backend db_port s3_port
  app_origin="${APP_URL%/}"
  platform_frontend="${TEST_AGENT_FRONTEND_URL:-http://127.0.0.1:3000}"
  platform_backend="${TEST_AGENT_BASE_URL:-http://127.0.0.1:8080}"
  db_port="${LOBEHUB_DEV_DB_PORT:-55432}"
  s3_port="${LOBEHUB_DEV_S3_PORT:-59000}"

  local pair
  for pair in \
    "hmac=${hmac}" "encryption=${encryption}" "auth_secret=${auth_secret}" \
    "vault_secret=${vault_secret}" "scheduler_secret=${scheduler_secret}" \
    "db_password=${db_password}" \
    "rustfs_access=${rustfs_access}" "rustfs_secret=${rustfs_secret}" \
    "redis_url=${redis_url}" "app_origin=${app_origin}" \
    "platform_frontend=${platform_frontend}" "platform_backend=${platform_backend}"; do
    validate_generated_value "${pair%%=*}" "${pair#*=}"
  done

  local env_dir temporary
  env_dir="$(cd "$(dirname "${ENV_FILE}")" && pwd -P)"
  temporary="$(mktemp "${env_dir}/.lobehub-dev.XXXXXX")"
  chmod 0600 "${temporary}"
  umask 077
  {
    printf 'LOBEHUB_DEV_DB_PASSWORD=%s\n' "${db_password}"
    printf 'LOBEHUB_DEV_DB_PORT=%s\n' "${db_port}"
    printf 'LOBEHUB_DEV_S3_PORT=%s\n' "${s3_port}"
    printf 'LOBEHUB_DEV_S3_CONSOLE_PORT=%s\n' "${LOBEHUB_DEV_S3_CONSOLE_PORT:-59001}"
    # fork 启动序列据此向 Next.js 传 -H；仅修改访问 URL 不会阻止 0.0.0.0 监听。
    printf 'LOBEHUB_DEV_HOST=127.0.0.1\n'
    printf 'RUSTFS_ACCESS_KEY=%s\n' "${rustfs_access}"
    printf 'RUSTFS_SECRET_KEY=%s\n' "${rustfs_secret}"
    printf 'APP_URL=%s\n' "${app_origin}"
    printf 'INTERNAL_APP_URL=%s\n' "${app_origin}"
    printf 'NEXTAUTH_URL=%s/api/auth\n' "${app_origin}"
    printf 'AUTH_SECRET=%s\n' "${auth_secret}"
    printf 'KEY_VAULTS_SECRET=%s\n' "${vault_secret}"
    printf 'ENTERPRISE_INTERNAL_SCHEDULER_SECRET=%s\n' "${scheduler_secret}"
    printf 'DATABASE_URL=postgresql://lobehub:%s@127.0.0.1:%s/lobehub\n' "$(urlencode "${db_password}")" "${db_port}"
    printf 'DATABASE_DRIVER=node\n'
    printf 'REDIS_URL=%s\n' "${redis_url}"
    # 上游 Redis 客户端会自动追加分隔冒号；这里不带尾冒号，实际 key 才是 lobehub:app:*。
    printf 'REDIS_PREFIX=lobehub:app\n'
    printf 'S3_ENDPOINT=http://127.0.0.1:%s\n' "${s3_port}"
    printf 'S3_BUCKET=lobehub-private\n'
    printf 'S3_ACCESS_KEY_ID=%s\n' "${rustfs_access}"
    printf 'S3_SECRET_ACCESS_KEY=%s\n' "${rustfs_secret}"
    printf 'S3_ENABLE_PATH_STYLE=1\n'
    printf 'S3_SET_ACL=0\n'
    printf 'PLATFORM_SSO_ENABLED=1\n'
    printf 'LOBEHUB_ENTERPRISE_OFFLINE=1\n'
    printf 'AGENT_RUNTIME_MODE=local\n'
    printf 'PLATFORM_LAUNCH_URL=%s/lobehub/launch\n' "${platform_frontend%/}"
    printf 'PLATFORM_SSO_REDEEM_URL=%s/api/internal/platform/lobehub-sso/tickets/redeem\n' "${platform_backend%/}"
    printf 'PLATFORM_SSO_REVOKE_URL=%s/api/internal/platform/lobehub-sso/grants/revoke\n' "${platform_backend%/}"
    printf 'PLATFORM_MODEL_GATEWAY_BASE_URL=%s/api/internal/platform/model-gateway/v1\n' "${platform_backend%/}"
    printf 'PLATFORM_SSO_HMAC_SECRET=%s\n' "${hmac}"
    printf 'PLATFORM_MODEL_GRANT_ENCRYPTION_KEY=%s\n' "${encryption}"
    printf 'TEST_AGENT_LOBEHUB_HMAC_SECRET=%s\n' "${hmac}"
    # 仅供 test/local 后端 Runner 使用；Runner 仍会拒绝非回环 PostgreSQL，不能影响共享数据库。
    printf 'TEST_AGENT_LOBEHUB_DEV_BOOTSTRAP_ENABLED=true\n'
    printf 'TEST_AGENT_LOBEHUB_DEV_BASE_URL=%s\n' "${app_origin}"
    printf 'TEST_AGENT_LOBEHUB_DEV_EMAIL_DOMAIN=lobehub.local\n'
    printf 'TELEMETRY_DISABLED=1\n'
    printf 'LOBEHUB_DEVICE_EXECUTION_MODE=disabled\n'
    printf 'PORT=%s\n' "${app_origin##*:}"
  } >"${temporary}"
  mv -f "${temporary}" "${ENV_FILE}"
  chmod 0600 "${ENV_FILE}"
  echo "Prepared LobeHub development environment: ${ENV_FILE}"
}

load_runtime_env() {
  local line key value
  [[ -f "${ENV_FILE}" ]] || write_env_file
  [[ ! -L "${ENV_FILE}" ]] || {
    echo "LobeHub development env must not be a symbolic link: ${ENV_FILE}" >&2
    exit 1
  }
  [[ "$(stat -c '%a' "${ENV_FILE}" 2>/dev/null || stat -f '%Lp' "${ENV_FILE}")" == 600 ]] || {
    echo "LobeHub development env must have mode 0600: ${ENV_FILE}" >&2
    exit 1
  }
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    [[ "${line}" == *=* ]] || {
      echo "Invalid LobeHub development dotenv line" >&2
      exit 1
    }
    key="${line%%=*}"
    value="${line#*=}"
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || {
      echo "Invalid LobeHub development dotenv key: ${key}" >&2
      exit 1
    }
    export "${key}=${value}"
  done <"${ENV_FILE}"
}

compose() {
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

verify_pnpm_version() {
  local package_manager expected_version actual_version
  package_manager="$(cd "${FORK_DIR}" && node -p 'require("./package.json").packageManager || ""')"
  [[ "${package_manager}" == pnpm@* ]] || {
    echo "LobeHub fork packageManager must pin pnpm: ${package_manager:-missing}" >&2
    exit 1
  }
  expected_version="${package_manager#pnpm@}"
  expected_version="${expected_version%%+*}"
  actual_version="$(cd "${FORK_DIR}" && corepack pnpm --version)" || {
    echo "Unable to run pnpm through Corepack for the LobeHub fork" >&2
    exit 1
  }
  [[ "${actual_version}" == "${expected_version}" ]] || {
    echo "LobeHub fork requires pnpm ${expected_version}, but Corepack resolved ${actual_version}" >&2
    exit 1
  }
}

stop_app() {
  local session screen_list line screen_id screen_pid screen_name child_pgid pid_file pid
  if command -v screen >/dev/null 2>&1; then
    screen_list="$(screen -list 2>/dev/null || true)"
    for session in "${SCREEN_SESSION}" "${SCHEDULER_SCREEN_SESSION}"; do
      while IFS= read -r line; do
        read -r screen_id _ <<<"${line}"
        [[ "${screen_id}" == *.* ]] || continue
        screen_pid="${screen_id%%.*}"
        screen_name="${screen_id#*.}"
        [[ "${screen_pid}" =~ ^[0-9]+$ && "${screen_name}" == "${session}" ]] || continue
        while IFS= read -r child_pgid; do
          [[ "${child_pgid}" =~ ^[0-9]+$ && "${child_pgid}" -gt 1 ]] || continue
          # macOS screen 的 login 包装进程可能在 session 退出后成为孤儿；先结束其完整进程组。
          kill -TERM "-${child_pgid}" >/dev/null 2>&1 || true
        done < <(
          ps -axo ppid=,pgid= 2>/dev/null \
            | awk -v parent="${screen_pid}" '$1 == parent { print $2 }' \
            | LC_ALL=C sort -u
        )
        # 使用完整 PID.name，避免主会话名被 scheduler 前缀匹配成多个候选而无法停止。
        screen -S "${screen_id}" -X quit >/dev/null 2>&1 || true
      done <<<"${screen_list}"
    done
  fi
  for pid_file in "${LOG_DIR}/lobehub.pid" "${LOG_DIR}/lobehub-scheduler.pid"; do
    [[ -f "${pid_file}" ]] || continue
    pid="$(cat "${pid_file}" 2>/dev/null || true)"
    if [[ "${pid}" =~ ^[0-9]+$ ]]; then kill "${pid}" >/dev/null 2>&1 || true; fi
    rm -f "${pid_file}"
  done
}

start_scheduler() {
  local scheduler_js command_line
  # 复用 fork 生产 launcher 的同一实现；命令行只包含变量名，Bearer secret 仅从子进程环境读取。
  scheduler_js='const launcher = require("./scripts/serverLauncher/startServer.js"); launcher.startEnterpriseOfflineScheduler(); setInterval(() => {}, 2147483647);'
  : >"${LOG_DIR}/lobehub-scheduler.log"
  if command -v screen >/dev/null 2>&1; then
    printf -v command_line 'cd %q && exec node -e %q >>%q 2>&1' \
      "${FORK_DIR}" "${scheduler_js}" "${LOG_DIR}/lobehub-scheduler.log"
    screen -dmS "${SCHEDULER_SCREEN_SESSION}" bash -lc "${command_line}"
  else
    (
      cd "${FORK_DIR}"
      nohup node -e "${scheduler_js}" >>"${LOG_DIR}/lobehub-scheduler.log" 2>&1 &
      echo "$!" >"${LOG_DIR}/lobehub-scheduler.pid"
    )
  fi
}

wait_for_app() {
  local attempt
  for attempt in {1..180}; do
    if curl -fsS --max-time 3 "${APP_URL}" >/dev/null 2>&1; then
      echo "OK LobeHub: ${APP_URL}"
      return
    fi
    sleep 2
  done
  echo "Timed out waiting for LobeHub: ${APP_URL}" >&2
  tail -n 160 "${LOG_DIR}/lobehub.log" >&2 || true
  exit 1
}

restart_all() {
  [[ -d "${FORK_DIR}/.git" ]] || {
    echo "Independent LobeHub fork not found: ${FORK_DIR}" >&2
    exit 1
  }
  require_command corepack
  require_command curl
  require_command docker
  write_env_file
  load_runtime_env
  # 统一使用 `corepack pnpm`，既兼容仓库的临时 shim，也由 fork 的 packageManager 锁定版本。
  verify_pnpm_version
  compose up -d --wait paradedb rustfs
  # one-shot 任务成功退出是预期状态，不能交给 compose --wait 当成长运行服务判断。
  compose run --rm rustfs-init
  (cd "${FORK_DIR}" && corepack pnpm install --frozen-lockfile)
  (cd "${FORK_DIR}" && corepack pnpm db:migrate)
  stop_app
  : >"${LOG_DIR}/lobehub.log"
  if command -v screen >/dev/null 2>&1; then
    local command_line
    printf -v command_line 'cd %q && exec corepack pnpm dev >>%q 2>&1' \
      "${FORK_DIR}" "${LOG_DIR}/lobehub.log"
    screen -dmS "${SCREEN_SESSION}" bash -lc "${command_line}"
  else
    (
      cd "${FORK_DIR}"
      nohup corepack pnpm dev >>"${LOG_DIR}/lobehub.log" 2>&1 &
      echo "$!" >"${LOG_DIR}/lobehub.pid"
    )
  fi
  wait_for_app
  start_scheduler
}

status_all() {
  load_runtime_env
  compose ps
  curl -sS -I --max-time 3 "${APP_URL}" | sed -n '1,5p' || true
}

case "${1:-}" in
  prepare) write_env_file ;;
  restart) restart_all ;;
  stop)
    stop_app
    if [[ -f "${ENV_FILE}" ]]; then load_runtime_env; compose stop paradedb rustfs; fi
    ;;
  status) status_all ;;
  --help|-h) usage ;;
  *) usage >&2; exit 2 ;;
esac
