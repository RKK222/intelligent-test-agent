#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTERNAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BUILDER="${INTERNAL_DIR}/build-lobehub-fork-transfer.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/lobehub-fork-transfer-test.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

FORK_DIR="${FIXTURE_ROOT}/lobehub-platform"
OUTPUT_DIR="${FIXTURE_ROOT}/output"
EXTRACT_DIR="${FIXTURE_ROOT}/extracted"
CLONE_DIR="${FIXTURE_ROOT}/clone"
VERSION_FILE="${FIXTURE_ROOT}/version.env"
mkdir -p "${FORK_DIR}"
git -C "${FORK_DIR}" init -q -b main
printf '{"name":"lobehub","version":"2.2.11"}\n' >"${FORK_DIR}/package.json"
printf 'upstream\n' >"${FORK_DIR}/upstream.txt"
git -C "${FORK_DIR}" add package.json upstream.txt
git -C "${FORK_DIR}" -c user.name='Transfer Test' -c user.email='transfer@example.internal' \
  commit -q -m 'upstream baseline'
UPSTREAM_COMMIT="$(git -C "${FORK_DIR}" rev-parse HEAD)"
git -C "${FORK_DIR}" -c user.name='Transfer Test' -c user.email='transfer@example.internal' \
  tag -a v2.2.11 -m 'unrelated upstream tag'
printf '{"name":"lobehub","version":"2.2.11-platform.99"}\n' >"${FORK_DIR}/package.json"
printf 'enterprise fork\n' >"${FORK_DIR}/platform.txt"
git -C "${FORK_DIR}" add package.json platform.txt
git -C "${FORK_DIR}" -c user.name='Transfer Test' -c user.email='transfer@example.internal' \
  commit -q -m 'enterprise fork'
FORK_COMMIT="$(git -C "${FORK_DIR}" rev-parse HEAD)"
git -C "${FORK_DIR}" -c user.name='Transfer Test' -c user.email='transfer@example.internal' \
  tag -a v2.2.11-platform.99 -m 'enterprise release'

cat >"${VERSION_FILE}" <<EOF
LOBEHUB_UPSTREAM_VERSION=v2.2.11
LOBEHUB_UPSTREAM_COMMIT=${UPSTREAM_COMMIT}
LOBEHUB_FORK_COMMIT=${FORK_COMMIT}
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.99
LOBEHUB_PLATFORM_CONTRACT_VERSION=2
LOBEHUB_PARADEDB_POSTGRES_MAJOR=17
EOF

"${BUILDER}" --fork-dir "${FORK_DIR}" --version-file "${VERSION_FILE}" \
  --output-dir "${OUTPUT_DIR}" >/dev/null

ZIP_PATH="${OUTPUT_DIR}/lobehub-fork-transfer-v2.2.11-platform.99.zip"
SHA_PATH="${ZIP_PATH}.sha256"
test -s "${ZIP_PATH}"
test -s "${SHA_PATH}"
if command -v sha256sum >/dev/null 2>&1; then
  (cd "${OUTPUT_DIR}" && sha256sum -c "$(basename "${SHA_PATH}")" >/dev/null)
else
  (cd "${OUTPUT_DIR}" && shasum -a 256 -c "$(basename "${SHA_PATH}")" >/dev/null)
fi

mkdir -p "${EXTRACT_DIR}"
unzip -q "${ZIP_PATH}" -d "${EXTRACT_DIR}"
TRANSFER_ROOT="${EXTRACT_DIR}/lobehub-fork-transfer-v2.2.11-platform.99"
BUNDLE="${TRANSFER_ROOT}/lobehub-platform-v2.2.11-platform.99.bundle"
test -s "${BUNDLE}"
test -s "${TRANSFER_ROOT}/refs.txt"
test -s "${TRANSFER_ROOT}/IMPORT.md"
test -s "${TRANSFER_ROOT}/SHA256SUMS"
EXPECTED_PATHS="$(cd "${TRANSFER_ROOT}" && find . -type f ! -name SHA256SUMS -print | sed 's#^\./##' | LC_ALL=C sort)"
LISTED_PATHS="$(awk '{print $2}' "${TRANSFER_ROOT}/SHA256SUMS" | sed 's/^\*//' | LC_ALL=C sort)"
test "${EXPECTED_PATHS}" = "${LISTED_PATHS}"
if command -v sha256sum >/dev/null 2>&1; then
  (cd "${TRANSFER_ROOT}" && sha256sum -c SHA256SUMS >/dev/null)
else
  (cd "${TRANSFER_ROOT}" && shasum -a 256 -c SHA256SUMS >/dev/null)
fi

HEADS="$(git bundle list-heads "${BUNDLE}")"
test "$(printf '%s\n' "${HEADS}" | wc -l | tr -d ' ')" -eq 2
printf '%s\n' "${HEADS}" | grep -F ' refs/heads/main' >/dev/null
printf '%s\n' "${HEADS}" | grep -F ' refs/tags/v2.2.11-platform.99' >/dev/null
if printf '%s\n' "${HEADS}" | grep -E ' refs/tags/v2\.2\.11$' >/dev/null; then
  echo 'Fork transfer bundle unexpectedly exposed an unrelated upstream tag ref' >&2
  exit 1
fi
grep -Fx "BRANCH_COMMIT=${FORK_COMMIT}" "${TRANSFER_ROOT}/refs.txt" >/dev/null
grep -Fx "TAG_COMMIT=${FORK_COMMIT}" "${TRANSFER_ROOT}/refs.txt" >/dev/null
grep -Fx 'FORK_DELTA_CREDENTIAL_SCAN=Passed' "${TRANSFER_ROOT}/refs.txt" >/dev/null
grep -F 'git remote add origin <enterprise-git-url>' "${TRANSFER_ROOT}/IMPORT.md" >/dev/null

git clone -q "${BUNDLE}" "${CLONE_DIR}"
test "$(git -C "${CLONE_DIR}" rev-parse HEAD)" = "${FORK_COMMIT}"
test "$(git -C "${CLONE_DIR}" rev-parse 'refs/tags/v2.2.11-platform.99^{}')" = "${FORK_COMMIT}"
test "$(git -C "${CLONE_DIR}" branch --show-current)" = main

if "${BUILDER}" --fork-dir "${FORK_DIR}" --version-file "${VERSION_FILE}" \
  --output-dir "${OUTPUT_DIR}" >/dev/null 2>&1; then
  echo 'Fork transfer builder unexpectedly overwrote an existing output directory' >&2
  exit 1
fi

if "${BUILDER}" --force --fork-dir "${FORK_DIR}" --version-file "${VERSION_FILE}" \
  --output-dir "${VERSION_FILE}" >/dev/null 2>&1; then
  echo 'Fork transfer builder unexpectedly replaced its version lock input' >&2
  exit 1
fi
test -f "${VERSION_FILE}"

NESTED_OUTPUT="${FORK_DIR}/must-not-be-created/transfer"
if "${BUILDER}" --fork-dir "${FORK_DIR}" --version-file "${VERSION_FILE}" \
  --output-dir "${NESTED_OUTPUT}" >/dev/null 2>&1; then
  echo 'Fork transfer builder unexpectedly accepted output inside the fork' >&2
  exit 1
fi
test ! -e "${FORK_DIR}/must-not-be-created"

RACE_BIN="${FIXTURE_ROOT}/race-bin"
RACE_PRESERVED="${FIXTURE_ROOT}/output-original"
mkdir "${RACE_BIN}"
cat >"${RACE_BIN}/zip" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
"${LOBEHUB_TRANSFER_REAL_ZIP:?}" "$@"
mv "${LOBEHUB_TRANSFER_RACE_OUTPUT:?}" "${LOBEHUB_TRANSFER_RACE_PRESERVED:?}"
mkdir "${LOBEHUB_TRANSFER_RACE_OUTPUT}"
printf 'concurrent\n' >"${LOBEHUB_TRANSFER_RACE_OUTPUT}/concurrent-marker"
EOF
chmod 0755 "${RACE_BIN}/zip"
if env PATH="${RACE_BIN}:${PATH}" LOBEHUB_TRANSFER_REAL_ZIP="$(command -v zip)" \
  LOBEHUB_TRANSFER_RACE_OUTPUT="${OUTPUT_DIR}" \
  LOBEHUB_TRANSFER_RACE_PRESERVED="${RACE_PRESERVED}" \
  "${BUILDER}" --force --fork-dir "${FORK_DIR}" --version-file "${VERSION_FILE}" \
  --output-dir "${OUTPUT_DIR}" >/dev/null 2>&1; then
  echo 'Fork transfer builder unexpectedly replaced a concurrent output object' >&2
  exit 1
fi
test -f "${OUTPUT_DIR}/concurrent-marker"
test -f "${RACE_PRESERVED}/$(basename "${ZIP_PATH}")"

# 删除工作树中的密钥不等于从 Git 历史删除；转运前必须检查 fork 增量的全部可达 blob。
SECRET_FORK="${FIXTURE_ROOT}/secret-fork"
SECRET_VERSION_FILE="${FIXTURE_ROOT}/secret-version.env"
git clone -q "${FORK_DIR}" "${SECRET_FORK}"
cat >"${SECRET_FORK}/temporary-private-key.pem" <<'EOF'
-----BEGIN PRIVATE KEY-----
fixture-private-key-material
-----END PRIVATE KEY-----
EOF
git -C "${SECRET_FORK}" add temporary-private-key.pem
git -C "${SECRET_FORK}" -c user.name='Transfer Test' -c user.email='transfer@example.internal' \
  commit -q -m 'accidentally add a private key'
git -C "${SECRET_FORK}" rm -q temporary-private-key.pem
git -C "${SECRET_FORK}" -c user.name='Transfer Test' -c user.email='transfer@example.internal' \
  commit -q -m 'remove private key from the worktree'
SECRET_FORK_COMMIT="$(git -C "${SECRET_FORK}" rev-parse HEAD)"
git -C "${SECRET_FORK}" -c user.name='Transfer Test' -c user.email='transfer@example.internal' \
  tag -f -a v2.2.11-platform.99 -m 'enterprise release with leaked history'
cat >"${SECRET_VERSION_FILE}" <<EOF
LOBEHUB_UPSTREAM_VERSION=v2.2.11
LOBEHUB_UPSTREAM_COMMIT=${UPSTREAM_COMMIT}
LOBEHUB_FORK_COMMIT=${SECRET_FORK_COMMIT}
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.99
LOBEHUB_PLATFORM_CONTRACT_VERSION=2
LOBEHUB_PARADEDB_POSTGRES_MAJOR=17
EOF
if "${BUILDER}" --fork-dir "${SECRET_FORK}" --version-file "${SECRET_VERSION_FILE}" \
  --output-dir "${FIXTURE_ROOT}/secret-output" >/dev/null 2>&1; then
  echo 'Fork transfer builder unexpectedly exported a private key retained in Git history' >&2
  exit 1
fi

printf 'dirty\n' >"${FORK_DIR}/dirty.txt"
if "${BUILDER}" --fork-dir "${FORK_DIR}" --version-file "${VERSION_FILE}" \
  --output-dir "${FIXTURE_ROOT}/dirty-output" >/dev/null 2>&1; then
  echo 'Fork transfer builder unexpectedly accepted a dirty fork' >&2
  exit 1
fi

echo "LobeHub fork transfer bundle test passed"
