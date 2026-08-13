#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/verify-clickhouse-package.XXXXXX")"
trap 'rm -rf "${TMP_ROOT}"' EXIT
mkdir -p "${TMP_ROOT}/image"
printf '[{"Config":"fixture.json","RepoTags":["test-agent-clickhouse:26.3.17.56"],"Layers":[]}]' \
  >"${TMP_ROOT}/image/manifest.json"
printf '{}' >"${TMP_ROOT}/image/fixture.json"
tar -C "${TMP_ROOT}/image" -cf "${TMP_ROOT}/image.tar" manifest.json fixture.json

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
grep -Fq 'cd ~/Desktop/mimoagent/0709' "${bundle}/START-HERE.md"
grep -Fq 'ClickHouse 26.3.17.56' "${bundle}/START-HERE.md"
printf 'ClickHouse offline package verification passed\n'
