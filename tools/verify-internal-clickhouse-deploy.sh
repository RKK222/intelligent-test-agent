#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${ROOT_DIR}/deploy/internal/deploy-clickhouse.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/verify-clickhouse-deploy.XXXXXX")"
trap 'rm -rf "${TMP_ROOT}"' EXIT
password='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
digest="$(printf '%s' "${password}" | openssl dgst -sha256 | awk '{print $NF}')"
sed "s/REPLACE_CLICKHOUSE_PASSWORD/${password}/" \
  "${ROOT_DIR}/deploy/internal/clickhouse.env.example" >"${TMP_ROOT}/clickhouse.env"
sed "s/REPLACE_CLICKHOUSE_PASSWORD_SHA256/${digest}/" \
  "${ROOT_DIR}/deploy/internal/clickhouse-users.xml.example" >"${TMP_ROOT}/users.xml"
"${SCRIPT}" --env-file "${TMP_ROOT}/clickhouse.env" --users-config "${TMP_ROOT}/users.xml" validate \
  | grep -Fq 'ClickHouse configuration validation passed'
grep -Fq -- '--ulimit nofile=262144:262144' "${SCRIPT}"
grep -Fq -- '--privileged' "${SCRIPT}"
grep -Fq 'Loaded ClickHouse image is not linux/amd64' "${SCRIPT}"
grep -Fq '/var/lib/clickhouse' "${SCRIPT}"
grep -Fq -- '--user "${USERNAME}" --password "${PASSWORD}"' "${SCRIPT}"
! grep -Fq 'http://127.0.0.1:${HOST_PORT}' "${SCRIPT}"
! grep -Fq -- '--env-file "${ENV_FILE}"' "${SCRIPT}"
printf 'ClickHouse deploy script verification passed\n'
