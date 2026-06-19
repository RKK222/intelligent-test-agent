#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: tools/dev-health-check.sh [--api] [--help]

Probe a locally running backend.

Environment:
  TEST_AGENT_BASE_URL   default: http://127.0.0.1:8080
  TEST_AGENT_API_TOKEN  optional Bearer token for /api probes

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

base_url="${TEST_AGENT_BASE_URL:-http://127.0.0.1:8080}"

curl -fsS --max-time 5 "${base_url}/actuator/health" >/dev/null
echo "OK ${base_url}/actuator/health"

if [[ "${with_api}" == "true" ]]; then
  auth_headers=()
  if [[ -n "${TEST_AGENT_API_TOKEN:-}" ]]; then
    auth_headers=(-H "Authorization: Bearer ${TEST_AGENT_API_TOKEN}")
  fi
  curl -fsS --max-time 5 "${auth_headers[@]}" \
    "${base_url}/api/workspaces?page=1&size=1" >/dev/null
  echo "OK ${base_url}/api/workspaces?page=1&size=1"
fi
