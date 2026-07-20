#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
COMPOSE_FILE="${ROOT_DIR}/deploy/local/docker-compose.yml"

usage() {
  cat <<'USAGE'
Usage: tools/dev-local-up.sh [--redis] [--help]

Start personal offline development PostgreSQL and XXL-JOB MySQL dependencies with Docker Compose.
Research/test and production environments must use external PostgreSQL, MySQL and Redis addresses.

Environment:
  TEST_AGENT_POSTGRES_DB        default: test_agent
  TEST_AGENT_POSTGRES_USER      default: test_agent
  TEST_AGENT_POSTGRES_PASSWORD  default: test_agent
  TEST_AGENT_POSTGRES_PORT      default: 15432
  TEST_AGENT_XXL_JOB_MYSQL_DATABASE       default: xxl_job
  TEST_AGENT_XXL_JOB_MYSQL_USERNAME       default: xxl_job
  TEST_AGENT_XXL_JOB_MYSQL_PASSWORD       default: xxl_job
  TEST_AGENT_XXL_JOB_MYSQL_ROOT_PASSWORD  default: xxl_job_root
  TEST_AGENT_XXL_JOB_MYSQL_PORT           default: 13306
  TEST_AGENT_REDIS_PORT         default: 16379

Options:
  --redis  Also start the optional Redis service.
  --help   Show this help.
USAGE
}

with_redis=false
for arg in "$@"; do
  case "${arg}" in
    --redis)
      with_redis=true
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

if docker compose version >/dev/null 2>&1; then
  compose=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose=(docker-compose)
else
  echo "Docker Compose is required." >&2
  exit 1
fi

if [[ "${with_redis}" == "true" ]]; then
  "${compose[@]}" -f "${COMPOSE_FILE}" --profile redis up -d postgres mysql redis
else
  "${compose[@]}" -f "${COMPOSE_FILE}" up -d postgres mysql
fi

"${compose[@]}" -f "${COMPOSE_FILE}" ps
