#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: tools/dev-runnable-loop-check.sh [--api] [--help]

Probe the already running frontend, backend and opencode server used by the
Phase 06-08 runnable loop. This script does not start long-running services.

Expected local commands:
  cd backend && mvn spring-boot:run -pl test-agent-app -Dspring-boot.run.profiles=local
  cd frontend && corepack pnpm dev
  opencode serve --hostname 127.0.0.1 --port 4096 --cors http://localhost:3000

Environment:
  TEST_AGENT_FRONTEND_URL  default: http://127.0.0.1:3000
  TEST_AGENT_BASE_URL      default: http://127.0.0.1:8080
  OPENCODE_BASE_URL        default: http://127.0.0.1:4096
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

if curl -fsS --max-time 5 "${opencode_url}/doc" >/dev/null; then
  echo "OK ${opencode_url}/doc"
elif curl -fsS --max-time 5 "${opencode_url}/health" >/dev/null; then
  echo "OK ${opencode_url}/health"
else
  echo "opencode server is not reachable at ${opencode_url}/doc or /health" >&2
  exit 1
fi

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
