#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
BACKEND_URL="${TEST_AGENT_BASE_URL:-http://127.0.0.1:8080}"
TIMEOUT_SECONDS="${VERIFY_TIMEOUT_SECONDS:-5}"
MANAGER_TOKEN="${TEST_AGENT_OPENCODE_MANAGER_TOKEN:-${OPENCODE_MANAGER_TOKEN:-}}"
SUPER_ADMIN_TOKEN="${TEST_AGENT_SUPER_ADMIN_TOKEN:-${TEST_AGENT_AUTH_TOKEN:-}}"
REQUIRE_MANAGER=false
REQUIRE_MANAGEMENT=false

usage() {
  cat <<'EOF'
Usage: tools/verify-opencode-process-deployment.sh [options]

Run read-only smoke checks for the opencode user process deployment control plane.

Options:
  --backend-url <url>      Backend direct or load-balanced URL. Default: TEST_AGENT_BASE_URL or http://127.0.0.1:8080.
  --manager-token <token>  Manager control token for /manager-backends. Default: TEST_AGENT_OPENCODE_MANAGER_TOKEN or OPENCODE_MANAGER_TOKEN.
  --auth-token <token>     SUPER_ADMIN user JWT for /management/overview. Default: TEST_AGENT_SUPER_ADMIN_TOKEN or TEST_AGENT_AUTH_TOKEN.
  --require-manager        Fail when manager token is absent instead of skipping discovery.
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
      MANAGER_TOKEN="$2"
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

print_json_summary() {
  local file="$1"
  if ! command -v python3 >/dev/null 2>&1; then
    return 0
  fi
  python3 - "$file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    body = json.load(handle)
data = body.get("data")
if isinstance(data, list):
    print(f"OK manager discovery returned {len(data)} backend instance(s)")
elif isinstance(data, dict):
    summary = data.get("summary", {})
    print(
        "OK management overview summary: "
        f"linuxServers={summary.get('linuxServers', 0)}, "
        f"backendProcesses={summary.get('backendProcesses', 0)}, "
        f"containers={summary.get('containers', 0)}, "
        f"managers={summary.get('managers', 0)}, "
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

if [[ -n "${MANAGER_TOKEN}" ]]; then
  discovery_file="${TMP_DIR}/manager-backends.json"
  curl_get "manager discovery" \
    "${BACKEND_URL}/api/internal/platform/opencode-runtime/manager-backends" \
    "${MANAGER_TOKEN}" \
    "${discovery_file}"
  require_api_success "manager discovery" "${discovery_file}"
  print_json_summary "${discovery_file}"
elif [[ "${REQUIRE_MANAGER}" == "true" ]]; then
  fail "manager token is required; set TEST_AGENT_OPENCODE_MANAGER_TOKEN or pass --manager-token"
else
  info "SKIP manager discovery: no manager token supplied"
fi

if [[ -n "${SUPER_ADMIN_TOKEN}" ]]; then
  overview_file="${TMP_DIR}/management-overview.json"
  curl_get "management overview" \
    "${BACKEND_URL}/api/internal/platform/opencode-runtime/management/overview?page=1&size=1" \
    "${SUPER_ADMIN_TOKEN}" \
    "${overview_file}"
  require_api_success "management overview" "${overview_file}"
  print_json_summary "${overview_file}"
elif [[ "${REQUIRE_MANAGEMENT}" == "true" ]]; then
  fail "SUPER_ADMIN token is required; set TEST_AGENT_SUPER_ADMIN_TOKEN or pass --auth-token"
else
  info "SKIP management overview: no SUPER_ADMIN token supplied"
fi

info "Opencode process deployment smoke check completed."
