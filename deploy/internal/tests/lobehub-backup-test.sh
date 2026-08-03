#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTERNAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKUP_SCRIPT="${INTERNAL_DIR}/lobehub-backup.sh"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-lobehub-backup-test.XXXXXX")"
trap 'rm -rf "${TEST_ROOT}"' EXIT

BASE_DIR="${TEST_ROOT}/testagent"
BACKUP_DIR="${TEST_ROOT}/backups"
FAKE_BIN="${TEST_ROOT}/bin"
mkdir -p "${BASE_DIR}/config" "${BASE_DIR}/lobehub/paradedb/base" \
  "${BASE_DIR}/lobehub/rustfs/data" "${BASE_DIR}/lobehub/rustfs/logs" \
  "${BASE_DIR}/lobehub/release" "${BACKUP_DIR}" "${FAKE_BIN}"

printf 'DATABASE_URL=secret\n' >"${BASE_DIR}/config/lobehub.env"
chmod 0600 "${BASE_DIR}/config/lobehub.env"
printf 'db-before-backup\n' >"${BASE_DIR}/lobehub/paradedb/base/proof.txt"
printf 'object-before-backup\n' >"${BASE_DIR}/lobehub/rustfs/data/proof.txt"
printf 'LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.7\nLOBEHUB_FORK_COMMIT=bf73f5f2c1e7f3309ecc1eb874ef58ca587b3a04\n' \
  >"${BASE_DIR}/lobehub/release/release.env"

cat >"${FAKE_BIN}/id" <<'EOF'
#!/usr/bin/env bash
if [[ "${1:-}" == -u ]]; then
  echo 0
  exit 0
fi
exec /usr/bin/id "$@"
EOF
cat >"${FAKE_BIN}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == info ]]; then
  [[ "${LOBEHUB_BACKUP_TEST_DOCKER_INFO:-available}" == available ]]
  exit
fi
if [[ "${1:-}" == inspect && "${2:-}" == -f && "${3:-}" == '{{.State.Running}}' ]]; then
  printf '%s\n' "${LOBEHUB_BACKUP_TEST_CONTAINER_RUNNING:-false}"
  exit 0
fi
exit 1
EOF
chmod 0755 "${FAKE_BIN}/id" "${FAKE_BIN}/docker"

PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_BASE_DIR="${BASE_DIR}" \
  "${BACKUP_SCRIPT}" create --output-dir "${BACKUP_DIR}" >/dev/null

archive="$(find "${BACKUP_DIR}" -maxdepth 1 -type f \
  -name 'test-agent-lobehub-backup-*.tar.gz' -print -quit)"
[[ -n "${archive}" && -f "${archive}.sha256" ]] || {
  echo 'LobeHub cold backup archive or checksum was not created' >&2
  exit 1
}
[[ "$(stat -c '%a' "${archive}" 2>/dev/null || stat -f '%Lp' "${archive}")" == 600 ]] || {
  echo 'LobeHub cold backup archive must use mode 0600' >&2
  exit 1
}
PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_BASE_DIR="${BASE_DIR}" \
  "${BACKUP_SCRIPT}" verify --archive "${archive}" >/dev/null
chmod 0644 "${archive}.sha256"
if PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_BASE_DIR="${BASE_DIR}" \
  "${BACKUP_SCRIPT}" verify --archive "${archive}" >/dev/null 2>&1; then
  echo 'LobeHub backup verifier unexpectedly accepted an exposed checksum file' >&2
  exit 1
fi
chmod 0600 "${archive}.sha256"

printf 'db-after-backup\n' >"${BASE_DIR}/lobehub/paradedb/base/proof.txt"
printf 'object-after-backup\n' >"${BASE_DIR}/lobehub/rustfs/data/proof.txt"
printf 'DATABASE_URL=changed\n' >"${BASE_DIR}/config/lobehub.env"
chmod 0600 "${BASE_DIR}/config/lobehub.env"

if PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_BASE_DIR="${BASE_DIR}" \
  "${BACKUP_SCRIPT}" restore --archive "${archive}" >/dev/null 2>&1; then
  echo 'LobeHub restore unexpectedly ran without explicit confirmation' >&2
  exit 1
fi
grep -Fx 'db-after-backup' "${BASE_DIR}/lobehub/paradedb/base/proof.txt" >/dev/null

PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_BASE_DIR="${BASE_DIR}" \
  "${BACKUP_SCRIPT}" restore --archive "${archive}" --confirm-restore >/dev/null
grep -Fx 'db-before-backup' "${BASE_DIR}/lobehub/paradedb/base/proof.txt" >/dev/null
grep -Fx 'object-before-backup' "${BASE_DIR}/lobehub/rustfs/data/proof.txt" >/dev/null
grep -Fx 'DATABASE_URL=secret' "${BASE_DIR}/config/lobehub.env" >/dev/null
[[ "$(stat -c '%a' "${BASE_DIR}/config/lobehub.env" 2>/dev/null || \
  stat -f '%Lp' "${BASE_DIR}/config/lobehub.env")" == 600 ]] || {
  echo 'Restored LobeHub env must use mode 0600' >&2
  exit 1
}
find "${BASE_DIR}/lobehub/restore-rollback" -mindepth 1 -maxdepth 1 -type d -print -quit \
  | grep -q . || {
    echo 'Restore did not retain a recoverable pre-restore copy' >&2
    exit 1
  }

if PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_BASE_DIR="${BASE_DIR}" \
  LOBEHUB_BACKUP_TEST_CONTAINER_RUNNING=true \
  "${BACKUP_SCRIPT}" create --output-dir "${TEST_ROOT}/running-backup" >/dev/null 2>&1; then
  echo 'LobeHub backup unexpectedly accepted a running container' >&2
  exit 1
fi

if PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_BASE_DIR="${BASE_DIR}" \
  LOBEHUB_BACKUP_TEST_DOCKER_INFO=unavailable \
  "${BACKUP_SCRIPT}" create --output-dir "${TEST_ROOT}/daemon-unavailable" >/dev/null 2>&1; then
  echo 'LobeHub backup unexpectedly accepted an unverifiable Docker daemon state' >&2
  exit 1
fi

mkdir -p "${TEST_ROOT}/malicious"
printf 'unexpected\n' >"${TEST_ROOT}/malicious/unexpected.txt"
tar -C "${TEST_ROOT}/malicious" -czf "${TEST_ROOT}/malicious.tar.gz" unexpected.txt
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "${TEST_ROOT}/malicious.tar.gz" >"${TEST_ROOT}/malicious.tar.gz.sha256"
else
  shasum -a 256 "${TEST_ROOT}/malicious.tar.gz" >"${TEST_ROOT}/malicious.tar.gz.sha256"
fi
chmod 0600 "${TEST_ROOT}/malicious.tar.gz" "${TEST_ROOT}/malicious.tar.gz.sha256"
if PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_BASE_DIR="${BASE_DIR}" \
  "${BACKUP_SCRIPT}" verify --archive "${TEST_ROOT}/malicious.tar.gz" >/dev/null 2>&1; then
  echo 'LobeHub backup verifier unexpectedly accepted an unknown archive path' >&2
  exit 1
fi

mkdir -p "${TEST_ROOT}/linked/lobehub/paradedb"
ln -s "${TEST_ROOT}" "${TEST_ROOT}/linked/lobehub/paradedb/escape"
tar -C "${TEST_ROOT}/linked" -czf "${TEST_ROOT}/linked.tar.gz" lobehub/paradedb/escape
if command -v sha256sum >/dev/null 2>&1; then
  (cd "${TEST_ROOT}" && sha256sum linked.tar.gz >linked.tar.gz.sha256)
else
  (cd "${TEST_ROOT}" && shasum -a 256 linked.tar.gz >linked.tar.gz.sha256)
fi
chmod 0600 "${TEST_ROOT}/linked.tar.gz" "${TEST_ROOT}/linked.tar.gz.sha256"
if PATH="${FAKE_BIN}:${PATH}" TEST_AGENT_BASE_DIR="${BASE_DIR}" \
  "${BACKUP_SCRIPT}" verify --archive "${TEST_ROOT}/linked.tar.gz" >/dev/null 2>&1; then
  echo 'LobeHub backup verifier unexpectedly accepted a symbolic link' >&2
  exit 1
fi

echo 'LobeHub cold backup and restore tests passed'
