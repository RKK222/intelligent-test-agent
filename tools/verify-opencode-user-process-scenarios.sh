#!/usr/bin/env bash
# 使用本地 test 环境验证用户 opencode 进程初始化、旧数据迁移和 workspace 文件路由。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
LOG_DIR="${ROOT_DIR}/.tmp/dev-services"
ENV_FILE="${ROOT_DIR}/.env.test"
BACKEND_URL="${TEST_AGENT_BASE_URL:-http://127.0.0.1:8080}"
PASSWORD_HASH='$2a$10$rhtTcWH3HZM/cw2d0QqYeeH8gKqDhieqlLoLCD7X1viDtefE9Sj16'
PASSWORD="123456"
USER_PREFIX="usr_codex_opencode_"
USERNAME_PREFIX="codex_opencode_"
WORKSPACE_PREFIX="wrk_codex_opencode_"
OLD_SERVER_ID="10.250.250.200"
TRACE_ID="trace_codex_opencode_scenario"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

load_env_file() {
  local file="$1"
  local line key value
  if [[ ! -f "${file}" ]]; then
    echo "Missing env file: ${file}" >&2
    exit 1
  fi
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    if [[ "${line}" == export\ * ]]; then
      line="${line#export }"
    fi
    [[ "${line}" == *=* ]] || continue
    key="${line%%=*}"
    value="${line#*=}"
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    export "${key}=${value}"
  done < "${file}"
}

psql_cmd() {
  PGPASSWORD="${TEST_AGENT_TEST_DB_PASSWORD}" psql \
    -h "${TEST_AGENT_TEST_DB_HOST}" \
    -p "${TEST_AGENT_TEST_DB_PORT:-5432}" \
    -U "${TEST_AGENT_TEST_DB_USERNAME}" \
    -d "${TEST_AGENT_TEST_DB_NAME}" \
    -v ON_ERROR_STOP=1 "$@"
}

sql_value() {
  psql_cmd -Atc "$1"
}

api() {
  local method="$1"
  local path="$2"
  local token="${3:-}"
  local body="${4:-}"
  local tmp code
  tmp="$(mktemp)"
  if [[ -n "${body}" ]]; then
    code="$(curl -sS -o "${tmp}" -w '%{http_code}' -X "${method}" "${BACKEND_URL}${path}" \
      -H 'Content-Type: application/json' \
      ${token:+-H "Authorization: Bearer ${token}"} \
      --data "${body}")"
  else
    code="$(curl -sS -o "${tmp}" -w '%{http_code}' -X "${method}" "${BACKEND_URL}${path}" \
      ${token:+-H "Authorization: Bearer ${token}"})"
  fi
  if [[ "${code}" -lt 200 || "${code}" -ge 300 ]]; then
    echo "HTTP ${method} ${path} failed with ${code}" >&2
    cat "${tmp}" >&2
    rm -f "${tmp}"
    exit 1
  fi
  cat "${tmp}"
  rm -f "${tmp}"
}

login_token() {
  local username="$1"
  api POST "/api/auth/login" "" "{\"username\":\"${username}\",\"password\":\"${PASSWORD}\"}" | jq -r '.data.token'
}

stop_ports_for_tool_users() {
  local port pid
  while IFS= read -r port; do
    [[ -n "${port}" ]] || continue
    pid="$(lsof -nP -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -n "${pid}" ]]; then
      kill ${pid} >/dev/null 2>&1 || true
    fi
    rm -f "${LOG_DIR}/opencode-manager-state/processes/${port}.json" >/dev/null 2>&1 || true
  done < <(sql_value "select port from opencode_server_processes where user_id like '${USER_PREFIX}%';")
}

cleanup_data() {
  stop_ports_for_tool_users
  psql_cmd >/dev/null <<SQL
delete from user_opencode_process_bindings where user_id like '${USER_PREFIX}%';
delete from opencode_server_processes where user_id like '${USER_PREFIX}%' or process_id like 'ocp_codex_opencode_%';
delete from user_login_logs where user_id like '${USER_PREFIX}%';
delete from user_roles where user_id like '${USER_PREFIX}%';
delete from users where user_id like '${USER_PREFIX}%';
delete from workspaces where workspace_id like '${WORKSPACE_PREFIX}%';
delete from opencode_manager_backend_connections where manager_id like 'mgr_codex_opencode_%';
delete from opencode_container_managers where manager_id like 'mgr_codex_opencode_%' or container_id like 'ctr_codex_opencode_%';
delete from opencode_containers where container_id like 'ctr_codex_opencode_%';
delete from linux_servers where linux_server_id = '${OLD_SERVER_ID}';
SQL
}

create_user() {
  local suffix="$1"
  local user_id="${USER_PREFIX}${suffix}"
  local username="${USERNAME_PREFIX}${suffix}"
  psql_cmd >/dev/null <<SQL
insert into users(user_id, unified_auth_id, username, password_hash, organization, rd_department, department, status, created_at, updated_at)
values ('${user_id}', 'CODEX_${suffix}', '${username}', '${PASSWORD_HASH}', '自动化验证', '测试研发部', '测试部门', 'ACTIVE', now(), now());
insert into user_roles(user_id, dict_id, created_at)
values ('${user_id}', 'dict_role_user', now())
on conflict do nothing;
SQL
  echo "${username}"
}

create_workspace() {
  local suffix="$1"
  local linux_server_id="$2"
  local dir="/tmp/test-agent/opencode-scenarios/${suffix}"
  mkdir -p "${dir}"
  psql_cmd >/dev/null <<SQL
insert into workspaces(workspace_id, name, root_path, status, linux_server_id, trace_id, created_at, updated_at)
values ('${WORKSPACE_PREFIX}${suffix}', 'opencode scenario ${suffix}', '${dir}', 'ACTIVE', '${linux_server_id}', '${TRACE_ID}', now(), now());
SQL
}

insert_old_dirty_binding() {
  local suffix="$1"
  local user_id="${USER_PREFIX}${suffix}"
  local process_id="ocp_codex_opencode_${suffix}"
  local container_id="ctr_codex_opencode_${suffix}"
  psql_cmd >/dev/null <<SQL
insert into linux_servers(linux_server_id, name, status, capacity_summary_json, last_heartbeat_at, trace_id, created_at, updated_at)
values ('${OLD_SERVER_ID}', 'old-test-server', 'READY', '{}', now(), '${TRACE_ID}', now(), now())
on conflict (linux_server_id) do update set updated_at = excluded.updated_at;
insert into opencode_containers(container_id, linux_server_id, container_name, port_start, port_end, max_processes, current_processes, status, last_heartbeat_at, trace_id, created_at, updated_at)
values ('${container_id}', '${OLD_SERVER_ID}', '${container_id}', 4096, 4096, 1, 1, 'READY', now(), '${TRACE_ID}', now(), now());
insert into opencode_server_processes(
  process_id, user_id, linux_server_id, container_id, port, pid, base_url, status,
  session_path, config_path, started_at, last_health_check_at, health_message, trace_id, created_at, updated_at
) values (
  '${process_id}', '${user_id}', '${OLD_SERVER_ID}', '${container_id}', 4096, 999999,
  'http://${OLD_SERVER_ID}:4096', 'UNHEALTHY',
  '/tmp/test-agent/stale-session/4096', '/tmp/test-agent/stale-config/', now(), now(),
  'dirty legacy process', '${TRACE_ID}', now() + interval '1 hour', now()
);
insert into user_opencode_process_bindings(user_id, agent_id, process_id, linux_server_id, port, status, trace_id, created_at, updated_at)
values ('${user_id}', 'opencode', '${process_id}', '${OLD_SERVER_ID}', 4096, 'ACTIVE', '${TRACE_ID}', now(), now());
SQL
}

insert_unbound_dirty_process() {
  local suffix="$1"
  local user_id="${USER_PREFIX}${suffix}"
  local process_id="ocp_codex_opencode_${suffix}"
  local container_id="ctr_codex_opencode_${suffix}"
  psql_cmd >/dev/null <<SQL
insert into linux_servers(linux_server_id, name, status, capacity_summary_json, last_heartbeat_at, trace_id, created_at, updated_at)
values ('${OLD_SERVER_ID}', 'old-test-server', 'READY', '{}', now(), '${TRACE_ID}', now(), now())
on conflict (linux_server_id) do update set updated_at = excluded.updated_at;
insert into opencode_containers(container_id, linux_server_id, container_name, port_start, port_end, max_processes, current_processes, status, last_heartbeat_at, trace_id, created_at, updated_at)
values ('${container_id}', '${OLD_SERVER_ID}', '${container_id}', 4100, 4100, 1, 1, 'READY', now(), '${TRACE_ID}', now(), now());
insert into opencode_server_processes(
  process_id, user_id, linux_server_id, container_id, port, pid, base_url, status,
  session_path, config_path, started_at, last_health_check_at, health_message, trace_id, created_at, updated_at
) values (
  '${process_id}', '${user_id}', '${OLD_SERVER_ID}', '${container_id}', 4100, 999998,
  'http://${OLD_SERVER_ID}:4100', 'UNHEALTHY',
  '/tmp/test-agent/stale-session/4100', '/tmp/test-agent/stale-config/', now(), now(),
  'unbound dirty process', '${TRACE_ID}', now() + interval '1 hour', now()
);
SQL
}

wait_ready() {
  local token="$1"
  local response status i
  for i in {1..30}; do
    response="$(api GET "/api/internal/agent/opencode/processes/me" "${token}")"
    status="$(jq -r '.data.status' <<<"${response}")"
    if [[ "${status}" == "READY" ]]; then
      jq -r '.data.baseUrl' <<<"${response}"
      return 0
    fi
    sleep 1
  done
  echo "opencode process did not become READY" >&2
  exit 1
}

verify_scenario() {
  local label="$1"
  local suffix="$2"
  local dirty_kind="$3"
  local workspace_server="$4"
  local username token status base_url route_linux runtime_health
  username="$(create_user "${suffix}")"
  create_workspace "${suffix}" "${workspace_server}"
  case "${dirty_kind}" in
    old_dirty) insert_old_dirty_binding "${suffix}" ;;
    new_dirty) insert_unbound_dirty_process "${suffix}" ;;
    clean) ;;
    *) echo "Unknown dirty kind: ${dirty_kind}" >&2; exit 1 ;;
  esac
  token="$(login_token "${username}")"
  status="$(api GET "/api/internal/agent/opencode/processes/me" "${token}" | jq -r '.data.status')"
  if [[ "${status}" != "READY" ]]; then
    api POST "/api/internal/agent/opencode/processes/me/initialize" "${token}" >/dev/null
  fi
  base_url="$(wait_ready "${token}")"
  runtime_health="$(api GET "/api/internal/agent/opencode/api/status?workspaceId=${WORKSPACE_PREFIX}${suffix}" "${token}" | jq -r '.data.healthy // .data.status // empty')"
  route_linux="$(api POST "/api/workspaces/${WORKSPACE_PREFIX}${suffix}/file-ws-route" "${token}" | jq -r '.data.linuxServerId')"
  curl -fsS "${base_url%/}/global/health" >/dev/null
  echo "OK ${label}: process=${base_url} routeLinuxServer=${route_linux} runtime=${runtime_health:-ok}"
}

require_command curl
require_command jq
require_command lsof
require_command psql

load_env_file "${ENV_FILE}"
BACKEND_URL="${TEST_AGENT_BASE_URL:-${BACKEND_URL}}"
current_server="$(cat "${LOG_DIR}/.serverip")"

cleanup_data
trap cleanup_data EXIT

verify_scenario "new-user clean data" "new_clean" "clean" "${current_server}"
verify_scenario "new-user dirty unbound data" "new_dirty" "new_dirty" "${current_server}"
verify_scenario "old-user clean data" "old_clean" "clean" "${current_server}"
verify_scenario "old-user dirty stale binding and stale workspace" "old_dirty" "old_dirty" "${OLD_SERVER_ID}"

echo "All opencode user-process scenarios passed."
