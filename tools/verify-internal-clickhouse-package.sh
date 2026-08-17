#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/verify-clickhouse-package.XXXXXX")"
trap 'rm -rf "${TMP_ROOT}"' EXIT
mkdir -p "${TMP_ROOT}/image"
printf '[{"Config":"fixture.json","RepoTags":["test-agent-clickhouse:26.3.17.56"],"Layers":[]}]' \
  >"${TMP_ROOT}/image/manifest.json"
printf '{"architecture":"amd64","os":"linux"}' >"${TMP_ROOT}/image/fixture.json"
tar -C "${TMP_ROOT}/image" -cf "${TMP_ROOT}/image.tar" manifest.json fixture.json

TEST_AGENT_CLICKHOUSE_PACKAGE_USERNAME=ck \
TEST_AGENT_CLICKHOUSE_PACKAGE_PASSWORD=ck_fixture_2026 \
  "${ROOT_DIR}/deploy/internal/package-clickhouse-offline.sh" \
  --image-tar "${TMP_ROOT}/image.tar" --output-dir "${TMP_ROOT}/dist" >/dev/null
archive="${TMP_ROOT}/dist/test-agent-clickhouse-offline.zip"
sha256sum -c "${archive}.sha256" --ignore-missing
unzip -tq "${archive}" >/dev/null
unzip -q "${archive}" -d "${TMP_ROOT}/bundle"
bundle="${TMP_ROOT}/bundle/test-agent-clickhouse-offline"
for file in START-HERE.md deploy-clickhouse.sh config/clickhouse.env config/clickhouse-users.xml \
  config/backend-clickhouse.env test-agent-clickhouse_26.3.17.56-linux-amd64.tar; do
  test -f "${bundle}/${file}"
done
! grep -R 'REPLACE_CLICKHOUSE_PASSWORD' "${bundle}/config"
grep -Fxq 'TEST_AGENT_CLICKHOUSE_USERNAME=ck' "${bundle}/config/clickhouse.env"
grep -Fxq 'TEST_AGENT_CLICKHOUSE_PASSWORD=ck_fixture_2026' "${bundle}/config/clickhouse.env"
grep -Fq '<ck>' "${bundle}/config/clickhouse-users.xml"
grep -Fq '<ip>127.0.0.1</ip>' "${bundle}/config/clickhouse-users.xml"
grep -Fxq 'TEST_AGENT_ANALYTICS_CLICKHOUSE_USERNAME=ck' \
  "${bundle}/config/backend-clickhouse.env"
! grep -Fq 'TEST_AGENT_CLICKHOUSE_USERNAME=testagent_analytics' \
  "${bundle}/config/clickhouse.env"
! grep -Fq '<testagent_analytics>' "${bundle}/config/clickhouse-users.xml"
"${bundle}/deploy-clickhouse.sh" \
  --env-file "${bundle}/config/clickhouse.env" \
  --users-config "${bundle}/config/clickhouse-users.xml" validate \
  | grep -Fq 'ClickHouse configuration validation passed'
grep -Fq 'cd ~/Desktop/mimoagent/0709' "${bundle}/START-HERE.md"
grep -Fq 'ClickHouse 26.3.17.56' "${bundle}/START-HERE.md"
grep -Fq 'CLICKHOUSE_HOST=122.233.30.147' "${bundle}/START-HERE.md"
! grep -Fq 'CLICKHOUSE_HOST=122.233.30.134' "${bundle}/START-HERE.md"
printf 'ClickHouse offline package verification passed\n'
