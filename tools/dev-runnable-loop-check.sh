#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"

usage() {
  cat <<'USAGE'
Usage: tools/dev-runnable-loop-check.sh [--api] [--help]

Probe the already running frontend, backend and opencode server used by the
Phase 06-08 runnable loop. This script does not start long-running services.

Expected local commands:
  tools/dev-backend-run.sh --profile local
  cd frontend && corepack pnpm dev
  opencode serve --hostname 127.0.0.1 --port 4096 --cors http://localhost:3000

Environment:
  TEST_AGENT_FRONTEND_URL  default: http://127.0.0.1:3000
  TEST_AGENT_BASE_URL      default: http://127.0.0.1:8080
  OPENCODE_BASE_URL        default: http://127.0.0.1:4096
  TEST_AGENT_OPENCODE_SERVER_PASSWORD  optional V2 Basic auth password
  TEST_AGENT_API_TOKEN     optional Bearer token for /api probes

Options:
  --api   Also call GET /api/workspaces?page=1&size=1.
  --help  Show this help.
USAGE
}

with_api=false
for arg in "$@"; do
  case "${arg}" in
    --api)
      with_api=true
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: ${arg}" >&2
      usage >&2
      exit 2
      ;;
  esac
done

frontend_url="${TEST_AGENT_FRONTEND_URL:-http://127.0.0.1:3000}"
backend_url="${TEST_AGENT_BASE_URL:-http://127.0.0.1:8080}"
opencode_url="${OPENCODE_BASE_URL:-http://127.0.0.1:4096}"

curl -fsS --max-time 5 "${frontend_url}" >/dev/null
echo "OK ${frontend_url}"

curl -fsS --max-time 5 "${backend_url}/actuator/health" >/dev/null
echo "OK ${backend_url}/actuator/health"

OPENCODE_BASE_URL="${opencode_url}" node "${ROOT_DIR}/tools/probe-opencode-v2-info.mjs"
echo "OK ${opencode_url}/api/info"

if [[ "${with_api}" == "true" ]]; then
  if [[ -n "${TEST_AGENT_API_TOKEN:-}" ]]; then
    curl -fsS --max-time 5 -H "Authorization: Bearer ${TEST_AGENT_API_TOKEN}" \
      "${backend_url}/api/workspaces?page=1&size=1" >/dev/null
  else
    curl -fsS --max-time 5 \
      "${backend_url}/api/workspaces?page=1&size=1" >/dev/null
  fi
  echo "OK ${backend_url}/api/workspaces?page=1&size=1"
fi
