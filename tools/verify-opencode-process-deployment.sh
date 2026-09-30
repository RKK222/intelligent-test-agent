#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
BACKEND_URL="${TEST_AGENT_BASE_URL:-http://127.0.0.1:8080}"
TIMEOUT_SECONDS="${VERIFY_TIMEOUT_SECONDS:-5}"
SUPER_ADMIN_TOKEN="${TEST_AGENT_SUPER_ADMIN_TOKEN:-${TEST_AGENT_AUTH_TOKEN:-}}"
REQUIRE_MANAGER=false
REQUIRE_MANAGEMENT=false
LEGACY_MANAGER_TOKEN_SUPPLIED=false
LINUX_SERVER_ID=""

usage() {
  cat <<'EOF'
Usage: tools/verify-opencode-process-deployment.sh [options]

Run read-only smoke checks for the opencode user process deployment control plane.

Options:
  --backend-url <url>      Backend direct or load-balanced URL. Default: TEST_AGENT_BASE_URL or http://127.0.0.1:8080.
  --manager-token <token>  Deprecated compatibility option; never sent to HTTP. Use --auth-token.
  --auth-token <token>     SUPER_ADMIN user JWT for /management/overview. Default: TEST_AGENT_SUPER_ADMIN_TOKEN or TEST_AGENT_AUTH_TOKEN.
  --linux-server-id <id>  Match a CONNECTED manager on this server when --require-manager is set.
  --require-manager        Require a CONNECTED manager and backend connection; needs --linux-server-id.
  --require-management     Fail when SUPER_ADMIN token is absent instead of skipping overview.
  --timeout <seconds>      Curl timeout. Default: VERIFY_TIMEOUT_SECONDS or 5.
  --help                   Show this help.

The script never prints supplied tokens. It does not start, stop, restart, or health-check user processes.
EOF
}

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

info() {
  echo "$*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is required"
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --backend-url)
      [[ "$#" -ge 2 ]] || fail "--backend-url requires a value"
      BACKEND_URL="$2"
      shift 2
      ;;
    --manager-token)
      [[ "$#" -ge 2 ]] || fail "--manager-token requires a value"
      LEGACY_MANAGER_TOKEN_SUPPLIED=true
      shift 2
      ;;
    --auth-token)
      [[ "$#" -ge 2 ]] || fail "--auth-token requires a value"
      SUPER_ADMIN_TOKEN="$2"
      shift 2
      ;;
    --require-manager)
      REQUIRE_MANAGER=true
      shift
      ;;
    --linux-server-id)
      [[ "$#" -ge 2 ]] || fail "--linux-server-id requires a value"
      LINUX_SERVER_ID="$2"
      shift 2
      ;;
    --require-management)
      REQUIRE_MANAGEMENT=true
      shift
      ;;
    --timeout)
      [[ "$#" -ge 2 ]] || fail "--timeout requires a value"
      TIMEOUT_SECONDS="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      fail "Unknown option: $1"
      ;;
  esac
done

require_command curl
if [[ "${REQUIRE_MANAGER}" == "true" && -z "${LINUX_SERVER_ID}" ]]; then
  fail "--require-manager needs --linux-server-id to avoid matching another server"
fi

BACKEND_URL="${BACKEND_URL%/}"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-opencode-process.XXXXXX")"
cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

curl_get() {
  local label="$1"
  local url="$2"
  local token="${3:-}"
  local output="$4"
  local args=(-fsS --max-time "${TIMEOUT_SECONDS}" -H "Accept: application/json")
  if [[ -n "${token}" ]]; then
    args+=(-H "Authorization: Bearer ${token}")
  fi
  if ! curl "${args[@]}" "${url}" >"${output}"; then
    fail "${label} request failed: ${url}"
  fi
}

require_api_success() {
  local label="$1"
  local file="$2"
  if ! grep -Eq '"success"[[:space:]]*:[[:space:]]*true' "${file}"; then
    echo "Response body:" >&2
    sed -n '1,20p' "${file}" >&2
    fail "${label} did not return success=true"
  fi
}

# 将 manager、连接和 READY Java 后端按身份关联，避免其它服务器的在线 manager 冒充目标服务器。
check_management_overview() {
  local file="$1"
  python3 - "$file" "$REQUIRE_MANAGER" "$LINUX_SERVER_ID" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    body = json.load(handle)
data = body.get("data")
if not isinstance(data, dict) or not isinstance(data.get("summary"), dict):
    sys.exit("FAIL: management overview has no summary")
summary = data["summary"]
managers = data.get("managers")
connections = data.get("managerBackendConnections")
backends = data.get("backendProcesses")
if not isinstance(managers, list) or not isinstance(connections, list) or not isinstance(backends, list):
    sys.exit("FAIL: management overview has no manager topology")
server_id = sys.argv[3]
backend_ids = {backend.get("backendProcessId") for backend in backends if isinstance(backend, dict)
               and backend.get("status") == "READY"
               and (not server_id or backend.get("linuxServerId") == server_id)}
connected = [manager for manager in managers if isinstance(manager, dict)
             and manager.get("connectionStatus") == "CONNECTED"
             and (not server_id or manager.get("linuxServerId") == server_id)]
manager_ids = {manager.get("managerId") for manager in connected}
linked = [connection for connection in connections if isinstance(connection, dict)
          and connection.get("status") == "CONNECTED"
          and connection.get("managerId") in manager_ids
          and connection.get("backendProcessId") in backend_ids]
if sys.argv[2] == "true" and (not connected or not linked):
    sys.exit("FAIL: no CONNECTED manager with a backend connection for the selected server")
print(
    "OK management overview summary: "
    f"linuxServers={summary.get('linuxServers', 0)}, "
    f"backendProcesses={summary.get('backendProcesses', 0)}, "
    f"containers={summary.get('containers', 0)}, "
    f"managers={summary.get('managers', 0)}, "
    f"connectedManagers={len(connected)}, "
    f"managerBackendConnections={len(linked)}, "
    f"opencodeProcesses={summary.get('opencodeProcesses', 0)}"
)
PY
}

health_file="${TMP_DIR}/health.json"
curl_get "backend health" "${BACKEND_URL}/actuator/health" "" "${health_file}"
if ! grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "${health_file}"; then
  sed -n '1,20p' "${health_file}" >&2
  fail "backend health is not UP"
fi
info "OK ${BACKEND_URL}/actuator/health"

if [[ "${LEGACY_MANAGER_TOKEN_SUPPLIED}" == "true" ]]; then
  info "Deprecated --manager-token is ignored; manager status is read from SUPER_ADMIN overview."
fi

if [[ -n "${SUPER_ADMIN_TOKEN}" ]]; then
  require_command python3
  overview_file="${TMP_DIR}/management-overview.json"
  curl_get "management overview" \
    "${BACKEND_URL}/api/internal/platform/opencode-runtime/management/overview?page=1&size=1" \
    "${SUPER_ADMIN_TOKEN}" \
    "${overview_file}"
  require_api_success "management overview" "${overview_file}"
  check_management_overview "${overview_file}"
elif [[ "${REQUIRE_MANAGEMENT}" == "true" || "${REQUIRE_MANAGER}" == "true"
    || "${LEGACY_MANAGER_TOKEN_SUPPLIED}" == "true" ]]; then
  fail "SUPER_ADMIN token is required for manager overview; set TEST_AGENT_SUPER_ADMIN_TOKEN or pass --auth-token"
else
  info "SKIP management overview and manager status: no SUPER_ADMIN token supplied"
fi

info "Opencode process deployment smoke check completed."
