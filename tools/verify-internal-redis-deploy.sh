#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_SCRIPT="${ROOT_DIR}/deploy/internal/deploy-redis.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-redis-deploy-verify.XXXXXX")"
cleanup() { rm -rf "${TMP_ROOT}"; }
trap cleanup EXIT

ENV_FILE="${TMP_ROOT}/redis.env"
CONFIG_FILE="${TMP_ROOT}/redis.conf"
printf '%s\n' \
  'TEST_AGENT_REDIS_IMAGE=test-agent-redis:7.4.9-alpine' \
  'TEST_AGENT_REDIS_CONTAINER=test-agent-redis-verify' \
  'TEST_AGENT_REDIS_HOST_PORT=16380' \
  "TEST_AGENT_REDIS_DATA_ROOT=${TMP_ROOT}/data" \
  >"${ENV_FILE}"
awk -v password='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef' \
  '{gsub(/REPLACE_REDIS_PASSWORD/, password); print}' \
  "${ROOT_DIR}/deploy/internal/redis.conf.example" >"${CONFIG_FILE}"

"${DEPLOY_SCRIPT}" --env-file "${ENV_FILE}" --config-file "${CONFIG_FILE}" validate >/dev/null

cp "${ROOT_DIR}/deploy/internal/redis.conf.example" "${TMP_ROOT}/placeholder.conf"
if "${DEPLOY_SCRIPT}" --env-file "${ENV_FILE}" --config-file "${TMP_ROOT}/placeholder.conf" validate >/dev/null 2>&1; then
  echo "Placeholder Redis password unexpectedly passed validation" >&2
  exit 1
fi

sed 's/appendonly yes/appendonly no/' "${CONFIG_FILE}" >"${TMP_ROOT}/unsafe.conf"
if "${DEPLOY_SCRIPT}" --env-file "${ENV_FILE}" --config-file "${TMP_ROOT}/unsafe.conf" validate >/dev/null 2>&1; then
  echo "Unsafe Redis persistence unexpectedly passed validation" >&2
  exit 1
fi

# 运行前已经强制校验镜像为 linux/amd64；运行命令不再携带旧 daemon 不支持的 --platform。
if grep -Fq -- '--platform' "${DEPLOY_SCRIPT}"; then
  echo "Redis deploy script must support Docker daemons without runtime --platform" >&2
  exit 1
fi
grep -Fq 'Loaded Redis image is not linux/amd64' "${DEPLOY_SCRIPT}"
grep -Fq 'test-agent-redis.conf' "${DEPLOY_SCRIPT}"
grep -Fq '/usr/bin/setpriv --reuid redis --regid redis --clear-groups' "${DEPLOY_SCRIPT}"
grep -Fq 'chown redis:redis' "${DEPLOY_SCRIPT}"
grep -Fq -- '--restart unless-stopped' "${DEPLOY_SCRIPT}"
grep -Fq -- '--replace-existing' "${DEPLOY_SCRIPT}"
grep -Fq 'GETDEL' "${DEPLOY_SCRIPT}"
grep -Fq 'convert_rdb_to_aof' "${DEPLOY_SCRIPT}"
grep -Fq 'CONFIG SET appendonly yes' "${DEPLOY_SCRIPT}"
if grep -Eq 'rm[[:space:]]+-rf.*DATA_ROOT|docker volume rm' "${DEPLOY_SCRIPT}"; then
  echo "Deploy script contains a destructive Redis data operation" >&2
  exit 1
fi

printf 'Redis deploy script verification passed\n'
