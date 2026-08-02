#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
WORKFLOW_DIR="${ROOT_DIR}/workflow-service"
BOOTSTRAP_SQL="${ROOT_DIR}/deploy/internal/workflow/bootstrap-workflow.sql"
LOG_ROOT="${TEST_AGENT_DEV_LOG_DIR:-${ROOT_DIR}/.tmp/dev-services}"
STATE_DIR="${LOG_ROOT}/workflow"
PLATFORM_ENV_FILE="${TEST_AGENT_WORKFLOW_DEV_ENV_FILE:-${STATE_DIR}/workflow-dev.env}"
RUNTIME_ENV_FILE="${STATE_DIR}/workflow-runtime.env"
SECRETS_ENV_FILE="${STATE_DIR}/workflow-secrets.env"
PRIVATE_KEY_FILE="${STATE_DIR}/runner-private.pem"
PUBLIC_KEY_FILE="${STATE_DIR}/runner-public.pem"
API_PID_FILE="${STATE_DIR}/workflow-api.pid"
WORKER_PID_FILE="${STATE_DIR}/workflow-worker.pid"
API_LOG_FILE="${STATE_DIR}/workflow-api.log"
WORKER_LOG_FILE="${STATE_DIR}/workflow-worker.log"
PREPARE_LOG_FILE="${STATE_DIR}/workflow-prepare.log"
WORKFLOW_VENV="${TEST_AGENT_WORKFLOW_VENV:-${ROOT_DIR}/.tmp/workflow-venv}"

usage() {
  cat <<'USAGE'
Usage: tools/workflow-dev-services.sh prepare|start|stop|status|--help

Manage the local Python workflow control plane only. This helper never starts
Analysis Runner, analysis-task containers, Docker networks, or firewall rules.

Commands:
  prepare  Generate/reuse local secrets, bootstrap PostgreSQL and Redis ACL,
           sync the locked Python environment, and initialize schema/checkpoints.
  start    Start workflow API on 127.0.0.1:8090 and the independent Worker.
  stop     Stop only PID-verified workflow API/Worker processes.
  status   Print process states without credentials.

State and logs default to .tmp/dev-services/workflow/ with mode 0700/0600.
USAGE
}

fail() {
  echo "Workflow development setup failed: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "缺少命令: $1"
}

secure_directory() {
  mkdir -p "$1"
  chmod 0700 "$1"
}

write_secret_file() {
  local target="$1" temporary
  temporary="$(mktemp "${STATE_DIR}/.workflow-secret.XXXXXX")"
  chmod 0600 "${temporary}"
  cat >"${temporary}"
  mv -f "${temporary}" "${target}"
  chmod 0600 "${target}"
}

# 生成文件由本助手维护，但仍按数据解析，避免 dotenv 内容被当作 shell 执行。
load_env_file() {
  local file="$1" line key value
  [[ -f "${file}" ]] || fail "缺少工作流配置文件: ${file}"
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    [[ "${line}" == *=* ]] || fail "工作流配置行格式无效: ${file}"
    key="${line%%=*}"
    value="${line#*=}"
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || fail "工作流配置键无效: ${key}"
    export "${key}=${value}"
  done <"${file}"
}

is_loopback_host() {
  case "$1" in
    127.0.0.1|localhost|::1|\[::1\]) return 0 ;;
    *) return 1 ;;
  esac
}

validate_port() {
  local name="$1" value="$2"
  [[ "${value}" =~ ^[0-9]{1,5}$ ]] && ((value >= 1 && value <= 65535)) \
    || fail "${name}端口无效"
}

validate_safe_db_value() {
  local name="$1" value="$2"
  [[ "${value}" =~ ^[A-Za-z0-9_.-]+$ ]] || fail "${name}格式无效"
}

random_hex() {
  openssl rand -hex 32
}

validate_hmac() {
  local name="$1" value="$2"
  (( ${#value} >= 64 )) || fail "${name}至少需要32字节"
}

prepare_secret_material() {
  local existing_count=0
  [[ -f "${SECRETS_ENV_FILE}" ]] && existing_count=$((existing_count + 1))
  [[ -f "${PRIVATE_KEY_FILE}" ]] && existing_count=$((existing_count + 1))
  [[ -f "${PUBLIC_KEY_FILE}" ]] && existing_count=$((existing_count + 1))

  if ((existing_count == 0)); then
    WORKFLOW_DEV_DB_OWNER_PASSWORD="$(random_hex)"
    WORKFLOW_DEV_DB_RUNTIME_PASSWORD="$(random_hex)"
    WORKFLOW_DEV_REDIS_PASSWORD="$(random_hex)"
    WORKFLOW_DEV_PLATFORM_HMAC_SECRET="$(random_hex)"
    WORKFLOW_DEV_RUNNER_HMAC_SECRET="$(random_hex)"
    WORKFLOW_DEV_RUNNER_PLATFORM_HMAC_SECRET="$(random_hex)"
    write_secret_file "${SECRETS_ENV_FILE}" <<EOF
WORKFLOW_DEV_DB_OWNER_PASSWORD=${WORKFLOW_DEV_DB_OWNER_PASSWORD}
WORKFLOW_DEV_DB_RUNTIME_PASSWORD=${WORKFLOW_DEV_DB_RUNTIME_PASSWORD}
WORKFLOW_DEV_REDIS_PASSWORD=${WORKFLOW_DEV_REDIS_PASSWORD}
WORKFLOW_DEV_PLATFORM_HMAC_SECRET=${WORKFLOW_DEV_PLATFORM_HMAC_SECRET}
WORKFLOW_DEV_RUNNER_HMAC_SECRET=${WORKFLOW_DEV_RUNNER_HMAC_SECRET}
WORKFLOW_DEV_RUNNER_PLATFORM_HMAC_SECRET=${WORKFLOW_DEV_RUNNER_PLATFORM_HMAC_SECRET}
EOF
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "${PRIVATE_KEY_FILE}" \
      >>"${PREPARE_LOG_FILE}" 2>&1
    openssl pkey -in "${PRIVATE_KEY_FILE}" -pubout -out "${PUBLIC_KEY_FILE}" \
      >>"${PREPARE_LOG_FILE}" 2>&1
    chmod 0600 "${PRIVATE_KEY_FILE}" "${PUBLIC_KEY_FILE}"
  elif ((existing_count != 3)); then
    fail "本地工作流密钥不完整；拒绝静默生成半套配置"
  else
    load_env_file "${SECRETS_ENV_FILE}"
  fi

  local required_secret
  for required_secret in \
    WORKFLOW_DEV_DB_OWNER_PASSWORD \
    WORKFLOW_DEV_DB_RUNTIME_PASSWORD \
    WORKFLOW_DEV_REDIS_PASSWORD \
    WORKFLOW_DEV_PLATFORM_HMAC_SECRET \
    WORKFLOW_DEV_RUNNER_HMAC_SECRET \
    WORKFLOW_DEV_RUNNER_PLATFORM_HMAC_SECRET; do
    [[ -n "${!required_secret:-}" ]] || fail "本地工作流密钥文件缺少${required_secret}"
  done
  validate_hmac WORKFLOW_DEV_PLATFORM_HMAC_SECRET "${WORKFLOW_DEV_PLATFORM_HMAC_SECRET}"
  validate_hmac WORKFLOW_DEV_RUNNER_HMAC_SECRET "${WORKFLOW_DEV_RUNNER_HMAC_SECRET}"
  validate_hmac WORKFLOW_DEV_RUNNER_PLATFORM_HMAC_SECRET "${WORKFLOW_DEV_RUNNER_PLATFORM_HMAC_SECRET}"
  [[ "${WORKFLOW_DEV_PLATFORM_HMAC_SECRET}" != "${WORKFLOW_DEV_RUNNER_HMAC_SECRET}" \
    && "${WORKFLOW_DEV_PLATFORM_HMAC_SECRET}" != "${WORKFLOW_DEV_RUNNER_PLATFORM_HMAC_SECRET}" \
    && "${WORKFLOW_DEV_RUNNER_HMAC_SECRET}" != "${WORKFLOW_DEV_RUNNER_PLATFORM_HMAC_SECRET}" ]] \
    || fail "三组工作流HMAC不得复用"
  chmod 0600 "${SECRETS_ENV_FILE}" "${PRIVATE_KEY_FILE}" "${PUBLIC_KEY_FILE}"
}

prepare_python_environment() {
  local base_python="${TEST_AGENT_WORKFLOW_PYTHON:-}"
  if [[ ! -x "${WORKFLOW_VENV}/bin/python" ]]; then
    if [[ -z "${base_python}" ]]; then
      base_python="$(command -v python3.12 || true)"
    fi
    [[ -n "${base_python}" && -x "${base_python}" ]] \
      || fail "需要Python 3.12；请设置TEST_AGENT_WORKFLOW_PYTHON"
    "${base_python}" -c 'import sys; raise SystemExit(sys.version_info[:2] != (3, 12))' \
      || fail "工作流解释器必须是Python 3.12"
    "${base_python}" -m venv "${WORKFLOW_VENV}" >>"${PREPARE_LOG_FILE}" 2>&1
  fi

  WORKFLOW_PYTHON="${WORKFLOW_VENV}/bin/python"
  "${WORKFLOW_PYTHON}" -c 'import sys; raise SystemExit(sys.version_info[:2] != (3, 12))' \
    || fail "工作流虚拟环境必须是Python 3.12"
  if [[ ! -x "${WORKFLOW_VENV}/bin/uv" ]]; then
    "${WORKFLOW_PYTHON}" -m pip install --disable-pip-version-check 'uv==0.8.14' \
      >>"${PREPARE_LOG_FILE}" 2>&1 \
      || fail "无法安装固定版本uv；详见${PREPARE_LOG_FILE}"
  fi
  UV_PROJECT_ENVIRONMENT="${WORKFLOW_VENV}" "${WORKFLOW_VENV}/bin/uv" sync \
    --project "${WORKFLOW_DIR}" --frozen --no-dev >>"${PREPARE_LOG_FILE}" 2>&1 \
    || fail "工作流Python锁定依赖同步失败；详见${PREPARE_LOG_FILE}"
  PYTHONPATH="${WORKFLOW_DIR}/src" "${WORKFLOW_PYTHON}" -c \
    'import agentscope, asyncpg, fastapi, langgraph, redis, testagent_workflow' \
    >>"${PREPARE_LOG_FILE}" 2>&1 \
    || fail "工作流Python依赖校验失败；详见${PREPARE_LOG_FILE}"
}

resolve_platform_hmac() {
  local python_value="${TEST_AGENT_WORKFLOW_PLATFORM_HMAC_SECRET:-}"
  local java_value="${TEST_AGENT_WORKFLOW_CAPABILITY_HMAC_SECRET:-}"
  if [[ -n "${python_value}" && -n "${java_value}" && "${python_value}" != "${java_value}" ]]; then
    fail "Python与Java工作流平台HMAC不一致"
  fi
  RESOLVED_PLATFORM_HMAC="${python_value:-${java_value:-${WORKFLOW_DEV_PLATFORM_HMAC_SECRET}}}"
  validate_hmac TEST_AGENT_WORKFLOW_PLATFORM_HMAC_SECRET "${RESOLVED_PLATFORM_HMAC}"
}

resolve_runner_configuration() {
  local values=(
    "${TEST_AGENT_WORKFLOW_RUNNER_BASE_URL:-}"
    "${TEST_AGENT_WORKFLOW_RUNNER_ID:-}"
    "${TEST_AGENT_WORKFLOW_RUNNER_PUBLIC_KEY_PATH:-}"
    "${TEST_AGENT_WORKFLOW_RUNNER_HMAC_SECRET:-}"
    "${TEST_AGENT_WORKFLOW_RUNNER_PLATFORM_HMAC_SECRET:-}"
    "${TEST_AGENT_WORKFLOW_MODEL_GATEWAY_URL:-}"
  )
  local present=0 value
  for value in "${values[@]}"; do
    [[ -z "${value}" ]] || present=$((present + 1))
  done

  if ((present != 0 && present != 6)); then
    fail "外部Runner配置必须成组提供"
  fi
  if ((present == 6)); then
    RESOLVED_RUNNER_BASE_URL="${values[0]}"
    RESOLVED_RUNNER_ID="${values[1]}"
    RESOLVED_RUNNER_PUBLIC_KEY_PATH="${values[2]}"
    RESOLVED_RUNNER_HMAC="${values[3]}"
    RESOLVED_RUNNER_PLATFORM_HMAC="${values[4]}"
    RESOLVED_MODEL_GATEWAY_URL="${values[5]}"
    [[ -f "${RESOLVED_RUNNER_PUBLIC_KEY_PATH}" ]] || fail "外部Runner公钥文件不存在"
  else
    RESOLVED_RUNNER_BASE_URL="http://127.0.0.1:8091"
    RESOLVED_RUNNER_ID="runner-local-control"
    RESOLVED_RUNNER_PUBLIC_KEY_PATH="${PUBLIC_KEY_FILE}"
    RESOLVED_RUNNER_HMAC="${WORKFLOW_DEV_RUNNER_HMAC_SECRET}"
    RESOLVED_RUNNER_PLATFORM_HMAC="${WORKFLOW_DEV_RUNNER_PLATFORM_HMAC_SECRET}"
    RESOLVED_MODEL_GATEWAY_URL="${TEST_AGENT_BASE_URL:-http://127.0.0.1:8080}/api/internal/platform/model-gateway/v1"
  fi
  [[ "${RESOLVED_RUNNER_ID}" =~ ^runner-[A-Za-z0-9_-]{3,80}$ ]] || fail "Runner ID格式无效"
  validate_hmac TEST_AGENT_WORKFLOW_RUNNER_HMAC_SECRET "${RESOLVED_RUNNER_HMAC}"
  validate_hmac TEST_AGENT_WORKFLOW_RUNNER_PLATFORM_HMAC_SECRET "${RESOLVED_RUNNER_PLATFORM_HMAC}"
  [[ "${RESOLVED_RUNNER_BASE_URL}" == http://* || "${RESOLVED_RUNNER_BASE_URL}" == https://* ]] \
    || fail "Runner地址必须是HTTP(S)绝对地址"
  [[ "${RESOLVED_MODEL_GATEWAY_URL}" == http://* || "${RESOLVED_MODEL_GATEWAY_URL}" == https://* ]] \
    || fail "模型网关地址必须是HTTP(S)绝对地址"
}

postgres_url_host() {
  case "$1" in
    ::1|\[::1\]) printf '[::1]' ;;
    *) printf '%s' "$1" ;;
  esac
}

bootstrap_postgres() {
  local explicit_runtime="${TEST_AGENT_WORKFLOW_DATABASE_URL:-}"
  local explicit_migration="${TEST_AGENT_WORKFLOW_MIGRATION_DATABASE_URL:-}"
  if [[ -n "${explicit_runtime}" || -n "${explicit_migration}" ]]; then
    [[ -n "${explicit_runtime}" && -n "${explicit_migration}" ]] \
      || fail "外部工作流数据库必须同时提供runtime与migration URL"
    WORKFLOW_DATABASE_URL="${explicit_runtime}"
    WORKFLOW_MIGRATION_DATABASE_URL="${explicit_migration}"
  else
    local host="${TEST_AGENT_TEST_DB_HOST:-}" port="${TEST_AGENT_TEST_DB_PORT:-5432}"
    local admin_database="${TEST_AGENT_TEST_DB_NAME:-}" admin_user="${TEST_AGENT_TEST_DB_USERNAME:-}"
    [[ -n "${host}" && -n "${admin_database}" && -n "${admin_user}" ]] \
      || fail "缺少TEST_AGENT_TEST_DB_HOST/NAME/USERNAME"
    is_loopback_host "${host}" || fail "自动工作流PostgreSQL初始化只允许回环主机"
    validate_port PostgreSQL "${port}"
    validate_safe_db_value PostgreSQL数据库名 "${admin_database}"
    validate_safe_db_value PostgreSQL用户名 "${admin_user}"
    require_command psql

    local escaped_bootstrap="${BOOTSTRAP_SQL//\'/\'\'}"
    printf "\\set workflow_owner_password '%s'\n\\set workflow_runtime_password '%s'\n\\i '%s'\n" \
      "${WORKFLOW_DEV_DB_OWNER_PASSWORD}" "${WORKFLOW_DEV_DB_RUNTIME_PASSWORD}" "${escaped_bootstrap}" \
      | PGPASSWORD="${TEST_AGENT_TEST_DB_PASSWORD:-}" psql --no-psqlrc --set ON_ERROR_STOP=on \
        -h "${host}" -p "${port}" -U "${admin_user}" -d "${admin_database}" \
        >>"${PREPARE_LOG_FILE}" 2>&1 \
      || fail "工作流PostgreSQL初始化失败；详见${PREPARE_LOG_FILE}"

    local url_host
    url_host="$(postgres_url_host "${host}")"
    WORKFLOW_DATABASE_URL="postgresql+asyncpg://test_agent_workflow:${WORKFLOW_DEV_DB_RUNTIME_PASSWORD}@${url_host}:${port}/test_agent_workflow"
    WORKFLOW_MIGRATION_DATABASE_URL="postgresql+asyncpg://test_agent_workflow_owner:${WORKFLOW_DEV_DB_OWNER_PASSWORD}@${url_host}:${port}/test_agent_workflow"
  fi
}

bootstrap_redis_acl() {
  if [[ -n "${TEST_AGENT_WORKFLOW_REDIS_URL:-}" ]]; then
    WORKFLOW_REDIS_URL="${TEST_AGENT_WORKFLOW_REDIS_URL}"
    return
  fi

  local host="${TEST_AGENT_REDIS_HOST:-127.0.0.1}" port="${TEST_AGENT_REDIS_PORT:-6379}"
  local admin_user="${TEST_AGENT_REDIS_USERNAME:-default}" admin_password="${TEST_AGENT_REDIS_PASSWORD:-}"
  validate_port Redis "${port}"
  if [[ -z "${admin_password}" ]] && ! is_loopback_host "${host}"; then
    fail "非回环Redis禁止无密码管理员连接"
  fi

  WORKFLOW_REDIS_HOST="${host}" \
  WORKFLOW_REDIS_PORT="${port}" \
  WORKFLOW_REDIS_ADMIN_USER="${admin_user}" \
  WORKFLOW_REDIS_ADMIN_PASSWORD="${admin_password}" \
  WORKFLOW_REDIS_PASSWORD="${WORKFLOW_DEV_REDIS_PASSWORD}" \
  WORKFLOW_REDIS_ALLOW_NOAUTH_FALLBACK="$(is_loopback_host "${host}" && printf true || printf false)" \
    "${WORKFLOW_PYTHON}" - <<'PY' >>"${PREPARE_LOG_FILE}" 2>&1
import os

from redis import Redis
from redis.exceptions import AuthenticationError, ResponseError

host = os.environ["WORKFLOW_REDIS_HOST"]
port = int(os.environ["WORKFLOW_REDIS_PORT"])
admin_user = os.environ.get("WORKFLOW_REDIS_ADMIN_USER") or "default"
admin_password = os.environ.get("WORKFLOW_REDIS_ADMIN_PASSWORD") or None
allow_fallback = os.environ["WORKFLOW_REDIS_ALLOW_NOAUTH_FALLBACK"] == "true"


def administrator(password: str | None) -> Redis:
    return Redis(
        host=host,
        port=port,
        username=admin_user if password else None,
        password=password,
        decode_responses=True,
    )


client = administrator(admin_password)
try:
    client.ping()
except AuthenticationError:
    if not admin_password or not allow_fallback:
        raise
    client.close()
    client = administrator(None)
    client.ping()

password = os.environ["WORKFLOW_REDIS_PASSWORD"]
client.execute_command(
    "ACL",
    "SETUSER",
    "workflow-dev",
    "reset",
    "on",
    f">{password}",
    "~test-agent:token:*",
    "-@all",
    "+ping",
    "+get",
    "+pttl",
)
client.close()

restricted = Redis(
    host=host,
    port=port,
    username="workflow-dev",
    password=password,
    decode_responses=True,
)
assert restricted.ping()
restricted.get("test-agent:token:acl-verification")
restricted.pttl("test-agent:token:acl-verification")
for command in (
    ("SCAN", "0"),
    ("KEYS", "*"),
    ("SET", "test-agent:token:acl-verification", "forbidden"),
    ("GET", "unrelated:key"),
):
    try:
        restricted.execute_command(*command)
    except ResponseError:
        continue
    raise RuntimeError(f"workflow Redis ACL unexpectedly permits {command[0]}")
restricted.close()
PY
  WORKFLOW_REDIS_URL="redis://workflow-dev:${WORKFLOW_DEV_REDIS_PASSWORD}@${host}:${port}/0"
}

write_runtime_environment() {
  write_secret_file "${PLATFORM_ENV_FILE}" <<EOF
TEST_AGENT_WORKFLOW_CAPABILITY_HMAC_SECRET=${RESOLVED_PLATFORM_HMAC}
TEST_AGENT_WORKFLOW_RUNNER_PLATFORM_HMAC_SECRET=${RESOLVED_RUNNER_PLATFORM_HMAC}
TEST_AGENT_WORKFLOW_RUNNER_ID=${RESOLVED_RUNNER_ID}
TEST_AGENT_WORKFLOW_RUNNER_PUBLIC_KEY_PATH=${RESOLVED_RUNNER_PUBLIC_KEY_PATH}
TEST_AGENT_WORKFLOW_MODEL_GATEWAY_URL=${RESOLVED_MODEL_GATEWAY_URL}
EOF
  write_secret_file "${RUNTIME_ENV_FILE}" <<EOF
TEST_AGENT_WORKFLOW_DEV_PYTHON=${WORKFLOW_PYTHON}
TEST_AGENT_WORKFLOW_DATABASE_URL=${WORKFLOW_DATABASE_URL}
TEST_AGENT_WORKFLOW_REDIS_URL=${WORKFLOW_REDIS_URL}
TEST_AGENT_WORKFLOW_PLATFORM_BASE_URL=${TEST_AGENT_BASE_URL:-http://127.0.0.1:8080}
TEST_AGENT_WORKFLOW_PLATFORM_HMAC_SECRET=${RESOLVED_PLATFORM_HMAC}
TEST_AGENT_WORKFLOW_RUNNER_BASE_URL=${RESOLVED_RUNNER_BASE_URL}
TEST_AGENT_WORKFLOW_RUNNER_HMAC_SECRET=${RESOLVED_RUNNER_HMAC}
TEST_AGENT_WORKFLOW_RUNNER_ID=${RESOLVED_RUNNER_ID}
TEST_AGENT_WORKFLOW_RUNNER_PUBLIC_KEY_PATH=${RESOLVED_RUNNER_PUBLIC_KEY_PATH}
TEST_AGENT_WORKFLOW_WORKER_ID=workflow-worker-dev
EOF
}

initialize_workflow_database() {
  load_env_file "${RUNTIME_ENV_FILE}"
  local runtime_url="${TEST_AGENT_WORKFLOW_DATABASE_URL}"
  TEST_AGENT_WORKFLOW_DATABASE_URL="${WORKFLOW_MIGRATION_DATABASE_URL}" \
  PYTHONPATH="${WORKFLOW_DIR}/src" "${WORKFLOW_PYTHON}" -m testagent_workflow.cli migrate \
    >>"${PREPARE_LOG_FILE}" 2>&1 \
    || fail "工作流Alembic迁移失败；详见${PREPARE_LOG_FILE}"
  TEST_AGENT_WORKFLOW_DATABASE_URL="${WORKFLOW_MIGRATION_DATABASE_URL}" \
  PYTHONPATH="${WORKFLOW_DIR}/src" "${WORKFLOW_PYTHON}" -m testagent_workflow.cli checkpoint-setup \
    >>"${PREPARE_LOG_FILE}" 2>&1 \
    || fail "LangGraph checkpoint初始化失败；详见${PREPARE_LOG_FILE}"
  export TEST_AGENT_WORKFLOW_DATABASE_URL="${runtime_url}"
}

prepare() {
  secure_directory "${STATE_DIR}"
  : >"${PREPARE_LOG_FILE}"
  chmod 0600 "${PREPARE_LOG_FILE}"
  require_command openssl
  prepare_secret_material
  prepare_python_environment
  resolve_platform_hmac
  resolve_runner_configuration
  bootstrap_postgres
  bootstrap_redis_acl
  write_runtime_environment
  initialize_workflow_database
  echo "Workflow development control plane prepared."
}

# macOS venv 的 bin/python 可能被 ps 展开为 Python.app；这里解析符号链接，供 PID 身份校验复用。
resolve_executable_path() {
  local path="$1" link base_dir
  [[ -n "${path}" ]] || return 1
  if [[ "${path}" != /* ]]; then
    path="$(command -v "${path}")" || return 1
  fi
  while [[ -L "${path}" ]]; do
    link="$(readlink "${path}")" || return 1
    if [[ "${link}" == /* ]]; then
      path="${link}"
    else
      base_dir="$(cd -P "$(dirname "${path}")" 2>/dev/null && pwd -P)" || return 1
      path="${base_dir}/${link}"
    fi
  done
  base_dir="$(cd -P "$(dirname "${path}")" 2>/dev/null && pwd -P)" || return 1
  printf '%s/%s\n' "${base_dir}" "$(basename "${path}")"
}

process_matches() {
  local pid="$1" role="$2" python_path="$3" command resolved_python_path runtime_python_path
  local python_framework_root
  local python_matches=false
  [[ "${pid}" =~ ^[0-9]+$ ]] || return 1
  kill -0 "${pid}" >/dev/null 2>&1 || return 1
  command="$(ps -p "${pid}" -o command= 2>/dev/null || true)"
  if [[ "${command}" == *"${python_path}"* ]]; then
    python_matches=true
  else
    # 优先使用解释器自报的基础路径，兼容 macOS 将 venv Python 显示为系统启动器的情况。
    runtime_python_path="$("${python_path}" -c "import sys; print(getattr(sys, '_base_executable', sys.executable))" 2>/dev/null \
      | awk 'NF {value = $0} END {print value}' || true)"
    if [[ -n "${runtime_python_path}" && "${command}" == *"${runtime_python_path}"* ]]; then
      python_matches=true
    fi
  fi
  if [[ "${python_matches}" != true ]]; then
    resolved_python_path="$(resolve_executable_path "${python_path}" 2>/dev/null || true)"
    [[ -n "${resolved_python_path}" && "${command}" == *"${resolved_python_path}"* ]] \
      && python_matches=true
    if [[ "${python_matches}" != true && "${resolved_python_path}" == */bin/* ]]; then
      python_framework_root="${resolved_python_path%/bin/*}"
      [[ -n "${python_framework_root}" && "${command}" == *"${python_framework_root}/"* ]] \
        && python_matches=true
    fi
  fi
  [[ "${python_matches}" == true \
    && "${command}" == *"testagent_workflow.cli"* \
    && " ${command} " == *" ${role} "* ]]
}

stop_process() {
  local label="$1" role="$2" pid_file="$3" python_path="$4" pid i
  [[ -f "${pid_file}" ]] || return 0
  pid="$(tr -d '[:space:]' <"${pid_file}")"
  if ! kill -0 "${pid}" >/dev/null 2>&1; then
    rm -f "${pid_file}"
    return 0
  fi
  if ! process_matches "${pid}" "${role}" "${python_path}"; then
    echo "拒绝停止PID不匹配的${label}: ${pid}" >&2
    return 1
  fi
  kill "${pid}" >/dev/null 2>&1 || true
  for ((i = 1; i <= 30; i++)); do
    kill -0 "${pid}" >/dev/null 2>&1 || break
    sleep 0.2
  done
  if kill -0 "${pid}" >/dev/null 2>&1; then
    kill -9 "${pid}" >/dev/null 2>&1 || true
  fi
  rm -f "${pid_file}"
}

stop() {
  secure_directory "${STATE_DIR}"
  local python_path="${TEST_AGENT_WORKFLOW_DEV_PYTHON:-}"
  if [[ -f "${RUNTIME_ENV_FILE}" ]]; then
    load_env_file "${RUNTIME_ENV_FILE}"
    python_path="${TEST_AGENT_WORKFLOW_DEV_PYTHON}"
  fi
  [[ -n "${python_path}" ]] || python_path="${WORKFLOW_VENV}/bin/python"
  local failed=0
  stop_process "workflow Worker" worker "${WORKER_PID_FILE}" "${python_path}" || failed=1
  stop_process "workflow API" api "${API_PID_FILE}" "${python_path}" || failed=1
  return "${failed}"
}

wait_ready() {
  local url="$1" log_file="$2" attempt
  for ((attempt = 1; attempt <= 60; attempt++)); do
    if curl -fsS --max-time 2 "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "工作流API就绪超时，日志: ${log_file}" >&2
  tail -n 80 "${log_file}" >&2 || true
  return 1
}

start() {
  secure_directory "${STATE_DIR}"
  load_env_file "${PLATFORM_ENV_FILE}"
  load_env_file "${RUNTIME_ENV_FILE}"
  WORKFLOW_PYTHON="${TEST_AGENT_WORKFLOW_DEV_PYTHON}"
  [[ -x "${WORKFLOW_PYTHON}" ]] || fail "工作流Python解释器不存在"
  require_command curl

  if [[ -f "${API_PID_FILE}" ]] && process_matches "$(cat "${API_PID_FILE}")" api "${WORKFLOW_PYTHON}"; then
    echo "workflow-api already running."
  else
    rm -f "${API_PID_FILE}"
    : >"${API_LOG_FILE}"
    chmod 0600 "${API_LOG_FILE}"
    PYTHONPATH="${WORKFLOW_DIR}/src" nohup "${WORKFLOW_PYTHON}" \
      -m testagent_workflow.cli api --host 127.0.0.1 --port 8090 \
      >>"${API_LOG_FILE}" 2>&1 &
    printf '%s\n' "$!" >"${API_PID_FILE}"
    chmod 0600 "${API_PID_FILE}"
  fi
  if ! wait_ready "http://127.0.0.1:8090/workflow-api/v1/ready" "${API_LOG_FILE}"; then
    stop_process "workflow API" api "${API_PID_FILE}" "${WORKFLOW_PYTHON}" || true
    return 1
  fi

  if [[ -f "${WORKER_PID_FILE}" ]] && process_matches "$(cat "${WORKER_PID_FILE}")" worker "${WORKFLOW_PYTHON}"; then
    echo "workflow-worker already running."
  else
    rm -f "${WORKER_PID_FILE}"
    : >"${WORKER_LOG_FILE}"
    chmod 0600 "${WORKER_LOG_FILE}"
    PYTHONPATH="${WORKFLOW_DIR}/src" nohup "${WORKFLOW_PYTHON}" \
      -m testagent_workflow.cli worker >>"${WORKER_LOG_FILE}" 2>&1 &
    printf '%s\n' "$!" >"${WORKER_PID_FILE}"
    chmod 0600 "${WORKER_PID_FILE}"
    sleep 1
  fi
  process_matches "$(cat "${WORKER_PID_FILE}")" worker "${WORKFLOW_PYTHON}" \
    || fail "工作流Worker启动失败；详见${WORKER_LOG_FILE}"
  echo "Workflow API and Worker started."
}

status_line() {
  local label="$1" role="$2" pid_file="$3" python_path="$4" pid=""
  [[ ! -f "${pid_file}" ]] || pid="$(tr -d '[:space:]' <"${pid_file}")"
  if [[ -n "${pid}" ]] && process_matches "${pid}" "${role}" "${python_path}"; then
    echo "${label}: RUNNING (pid ${pid})"
  else
    echo "${label}: STOPPED"
  fi
}

status() {
  local python_path="${WORKFLOW_VENV}/bin/python"
  if [[ -f "${RUNTIME_ENV_FILE}" ]]; then
    load_env_file "${RUNTIME_ENV_FILE}"
    python_path="${TEST_AGENT_WORKFLOW_DEV_PYTHON}"
  fi
  status_line workflow-api api "${API_PID_FILE}" "${python_path}"
  status_line workflow-worker worker "${WORKER_PID_FILE}" "${python_path}"
}

command_name="${1:---help}"
case "${command_name}" in
  prepare) prepare ;;
  start) start ;;
  stop) stop ;;
  status) status ;;
  --help|-h|help) usage ;;
  *) usage >&2; exit 2 ;;
esac
