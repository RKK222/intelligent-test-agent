#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd -P)"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-memory-package.XXXXXX")"
trap 'rm -rf "${TMP_ROOT}"' EXIT

bash -n \
  "${ROOT_DIR}/deploy/internal/package-memory-offline.sh" \
  "${ROOT_DIR}/deploy/internal/memory-docker.sh" \
  "${ROOT_DIR}/deploy/internal/package-release.sh"
grep -Fq '"normalized": True' "${ROOT_DIR}/deploy/internal/package-memory-offline.sh"
grep -Fq 'type=docker,dest=' "${ROOT_DIR}/deploy/internal/package-memory-offline.sh"
grep -Fq -- "--exclude='__pycache__/'" "${ROOT_DIR}/deploy/internal/package-memory-offline.sh"
grep -Fq '*.pyc' "${ROOT_DIR}/memory-service/.dockerignore"
grep -Fq '*.pyc' "${ROOT_DIR}/embedding-service/.dockerignore"
grep -Fq -- '--user 101:101 --cap-drop ALL' "${ROOT_DIR}/deploy/internal/memory-docker.sh"
grep -Fq 'PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1' \
  "${ROOT_DIR}/deploy/internal/memory-docker.sh"
if grep -Fq 'docker pull --platform "${PLATFORM}" "${PGVECTOR_SOURCE_IMAGE}"' \
  "${ROOT_DIR}/deploy/internal/package-memory-offline.sh"; then
  echo "offline package can collide with an existing Mac arm64 infrastructure image" >&2
  exit 1
fi
if grep -Fq '"normalize_embeddings": True' "${ROOT_DIR}/deploy/internal/package-memory-offline.sh"; then
  echo "offline package validates the wrong model identity key" >&2
  exit 1
fi

"${ROOT_DIR}/deploy/internal/package-memory-offline.sh" --validate-only \
  --output-dir "${TMP_ROOT}/dist" >/dev/null
help_output="$("${ROOT_DIR}/deploy/internal/package-release.sh" --help)"
grep -Fq -- '--memory-only' <<<"${help_output}"
grep -Fq -- '--with-memory' <<<"${help_output}"

memory_secret="$(printf 'm%.0s' {1..64})"
hmac_secret="$(printf 'h%.0s' {1..64})"
db_secret="$(printf 'd%.0s' {1..64})"
embedding_secret="$(printf 'e%.0s' {1..64})"
sed \
  -e "s/^TEST_AGENT_MEMORY_DB_PASSWORD=.*/TEST_AGENT_MEMORY_DB_PASSWORD=${db_secret}/" \
  -e "s#^TEST_AGENT_MEMORY_SERVICE_DATABASE_URL=.*#TEST_AGENT_MEMORY_SERVICE_DATABASE_URL=postgresql://testagent_memory:${db_secret}@memory-db.internal:5432/testagent_memory#" \
  -e "s/^TEST_AGENT_MEMORY_SERVICE_API_KEY=.*/TEST_AGENT_MEMORY_SERVICE_API_KEY=${memory_secret}/" \
  -e "s/^TEST_AGENT_MEMORY_SERVICE_MODEL_GATEWAY_HMAC_SECRET=.*/TEST_AGENT_MEMORY_SERVICE_MODEL_GATEWAY_HMAC_SECRET=${hmac_secret}/" \
  "${ROOT_DIR}/deploy/internal/memory.env.example" >"${TMP_ROOT}/memory.env"
sed "s/REPLACE_WITH_AT_LEAST_32_RANDOM_CHARACTERS/${embedding_secret}/" \
  "${ROOT_DIR}/deploy/internal/embedding.env.example" >"${TMP_ROOT}/embedding.env"
chmod 0600 "${TMP_ROOT}/memory.env" "${TMP_ROOT}/embedding.env"

TEST_AGENT_MEMORY_ENV_FILE="${TMP_ROOT}/memory.env" \
  "${ROOT_DIR}/deploy/internal/memory-docker.sh" validate-memory-config >/dev/null
TEST_AGENT_EMBEDDING_ENV_FILE="${TMP_ROOT}/embedding.env" \
  "${ROOT_DIR}/deploy/internal/memory-docker.sh" validate-embedding-config >/dev/null

printf 'TEST_AGENT_MEMORY_NODE_ID=duplicate\n' >>"${TMP_ROOT}/memory.env"
if TEST_AGENT_MEMORY_ENV_FILE="${TMP_ROOT}/memory.env" \
  "${ROOT_DIR}/deploy/internal/memory-docker.sh" validate-memory-config >/dev/null 2>&1; then
  echo "Memory config validation accepted a duplicate key" >&2
  exit 1
fi

old_migration="${ROOT_DIR}/backend/test-agent-persistence/src/main/resources/db/migration/V20260809120000__create_qa_memory_governance.sql"
actual="$(shasum -a 256 "${old_migration}" | awk '{print $1}')"
[[ "${actual}" == b2ae5639284208be8bc09952d9143c3dd0d8a2bf649b6601aed4225e586af18a ]]

echo "Memory offline package and config validation checks passed."
