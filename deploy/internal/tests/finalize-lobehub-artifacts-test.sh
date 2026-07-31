#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTERNAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
FINALIZER="${INTERNAL_DIR}/finalize-lobehub-artifacts.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/lobehub-finalize-test.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

SERVER_DIR="${FIXTURE_ROOT}/server-only"
OUTPUT_DIR="${FIXTURE_ROOT}/complete"
PACKAGE_DIR="${FIXTURE_ROOT}/package"
CLIENT_DIR="${FIXTURE_ROOT}/clients"
mkdir -p "${SERVER_DIR}/images" "${SERVER_DIR}/bin" "${SERVER_DIR}/sbom" \
  "${SERVER_DIR}/source" "${CLIENT_DIR}"

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

write_manifest() {
  local directory="$1"
  (
    cd "${directory}"
    if command -v sha256sum >/dev/null 2>&1; then
      find . -type f ! -name SHA256SUMS | LC_ALL=C sort | while IFS= read -r file; do
        sha256sum "${file#./}"
      done >SHA256SUMS
    else
      find . -type f ! -name SHA256SUMS | LC_ALL=C sort | while IFS= read -r file; do
        shasum -a 256 "${file#./}"
      done >SHA256SUMS
    fi
  )
}

assert_rejected() {
  local description="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    echo "LobeHub finalizer unexpectedly accepted ${description}" >&2
    exit 1
  fi
}

for file in \
  images/lobehub-image.tar images/paradedb-image.tar images/rustfs-image.tar \
  bin/mc-linux-amd64 sbom/lobehub.spdx.json \
  source/lobehub-v2.2.11-platform.5.tar.gz \
  approved-resources.json LICENSES.txt; do
  printf 'fixture:%s\n' "${file}" >"${SERVER_DIR}/${file}"
done

# 保留早期 server-only 介质的兼容形态：尚无 Linux 审批状态字段。
cat >"${SERVER_DIR}/release.env" <<'EOF'
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.5
LOBEHUB_UPSTREAM_VERSION=v2.2.11
LOBEHUB_UPSTREAM_COMMIT=5b4cef6
LOBEHUB_FORK_COMMIT=57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c
LOBEHUB_PLATFORM_CONTRACT_VERSION=2
LOBEHUB_PARADEDB_POSTGRES_MAJOR=17
LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=false
LOBEHUB_LINUX_EXECUTION_DEFAULT=false
LOBEHUB_APP_IMAGE=test-agent/lobehub:v2.2.11-platform.5
LOBEHUB_APP_IMAGE_ID=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
LOBEHUB_PARADEDB_IMAGE=test-agent/paradedb:pg17-v2.2.11-platform.5
LOBEHUB_PARADEDB_IMAGE_ID=sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
LOBEHUB_RUSTFS_IMAGE=test-agent/rustfs:v2.2.11-platform.5
LOBEHUB_RUSTFS_IMAGE_ID=sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
EOF
write_manifest "${SERVER_DIR}"
cp "${SERVER_DIR}/release.env" "${FIXTURE_ROOT}/server-release.before"
cp "${SERVER_DIR}/SHA256SUMS" "${FIXTURE_ROOT}/server-sha.before"

WINDOWS_CLIENT="${CLIENT_DIR}/lobehub-windows-x64.exe"
WINDOWS_EVIDENCE="${CLIENT_DIR}/windows-authenticode-verification.txt"
LINUX_CLIENT="${CLIENT_DIR}/lobehub-linux-x86_64.tar.gz"
LINUX_EVIDENCE="${CLIENT_DIR}/linux-client-verification.txt"
LINUX_RECORD="${CLIENT_DIR}/linux-client-acceptance-record.txt"
LINUX_BUILD_EVIDENCE="${CLIENT_DIR}/linux-client-build-evidence.txt"
printf 'signed-windows-client\n' >"${WINDOWS_CLIENT}"
printf 'approved-linux-client\n' >"${LINUX_CLIENT}"
cat >"${LINUX_BUILD_EVIDENCE}" <<EOF
LINUX_BUILD_STATUS=Candidate
LINUX_BUILDER=linux-builder@example.internal
LINUX_CLIENT_FILE_SHA256=$(sha256_file "${LINUX_CLIENT}")
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.5
LOBEHUB_FORK_COMMIT=57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c
CLIENT_ARCHITECTURE=x86_64
CLIENT_EXECUTION_MODE=disabled
BUILD_OS=Enterprise Linux Builder 9.6
BUILD_KERNEL=5.14.0-builder.x86_64
BUILD_NODE_VERSION=v24.11.1
BUILD_BUN_VERSION=1.3.2
SOURCE_ARCHIVE_SHA256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
EOF
cat >"${LINUX_RECORD}" <<'EOF'
VALIDATION_RESULT=Passed
CLIENT_LOGIN_RESULT=Passed
DEVICE_EXECUTION_RESULT=Disabled
PUBLIC_NETWORK_DEPENDENCY_RESULT=None
RUNTIME_DOWNLOAD_RESULT=Blocked
LOCAL_DATA_ISOLATION_RESULT=Passed
REVIEWER=security-reviewer@example.internal
APPROVAL_CHANGE_ID=CHG-20260731-LOBEHUB
EOF
cat >"${WINDOWS_EVIDENCE}" <<EOF
AUTHENTICODE_STATUS=Valid
AUTHENTICODE_SUBJECT=CN=Enterprise Release Signing
AUTHENTICODE_THUMBPRINT=0123456789ABCDEF0123456789ABCDEF01234567
AUTHENTICODE_FILE_SHA256=$(sha256_file "${WINDOWS_CLIENT}")
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.5
LOBEHUB_FORK_COMMIT=57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c
CLIENT_ARCHITECTURE=x64
CLIENT_EXECUTION_MODE=disabled
EOF
cat >"${LINUX_EVIDENCE}" <<EOF
LINUX_APPROVAL_STATUS=Approved
LINUX_APPROVER=security-reviewer@example.internal
LINUX_CLIENT_FILE_SHA256=$(sha256_file "${LINUX_CLIENT}")
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.5
LOBEHUB_FORK_COMMIT=57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c
CLIENT_ARCHITECTURE=x86_64
CLIENT_EXECUTION_MODE=disabled
LINUX_VALIDATION_OS=Enterprise Linux 9.6
LINUX_VALIDATION_KERNEL=5.14.0-570.26.1.el9_6.x86_64
LINUX_ACCEPTANCE_RECORD_SHA256=$(sha256_file "${LINUX_RECORD}")
LINUX_BUILD_EVIDENCE_SHA256=$(sha256_file "${LINUX_BUILD_EVIDENCE}")
EOF

"${FINALIZER}" \
  --server-artifact-dir "${SERVER_DIR}" \
  --output-dir "${OUTPUT_DIR}" \
  --windows-client "${WINDOWS_CLIENT}" \
  --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" \
  --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}" \
  --linux-build-evidence "${LINUX_BUILD_EVIDENCE}" >/dev/null

grep -Fx 'LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=true' "${OUTPUT_DIR}/release.env" >/dev/null
grep -Fx 'LOBEHUB_LINUX_CLIENT_APPROVED=true' "${OUTPUT_DIR}/release.env" >/dev/null
test "$(grep -c '^LOBEHUB_LINUX_CLIENT_APPROVED=' "${OUTPUT_DIR}/release.env")" -eq 1
cmp "${SERVER_DIR}/release.env" "${FIXTURE_ROOT}/server-release.before"
cmp "${SERVER_DIR}/SHA256SUMS" "${FIXTURE_ROOT}/server-sha.before"
cmp "${WINDOWS_CLIENT}" "${OUTPUT_DIR}/clients/lobehub-windows-x64.exe"
cmp "${LINUX_CLIENT}" "${OUTPUT_DIR}/clients/lobehub-linux-x86_64.tar.gz"
cmp "${WINDOWS_EVIDENCE}" "${OUTPUT_DIR}/windows-authenticode-verification.txt"
cmp "${LINUX_EVIDENCE}" "${OUTPUT_DIR}/linux-client-verification.txt"
cmp "${LINUX_RECORD}" "${OUTPUT_DIR}/linux-client-acceptance-record.txt"
cmp "${LINUX_BUILD_EVIDENCE}" "${OUTPUT_DIR}/linux-client-build-evidence.txt"
EXPECTED_PATHS="$(cd "${OUTPUT_DIR}" && find . -type f ! -name SHA256SUMS -print | sed 's#^\./##' | LC_ALL=C sort)"
LISTED_PATHS="$(awk '{print $2}' "${OUTPUT_DIR}/SHA256SUMS" | sed 's/^\*//' | LC_ALL=C sort)"
test "${EXPECTED_PATHS}" = "${LISTED_PATHS}"
if command -v sha256sum >/dev/null 2>&1; then
  (cd "${OUTPUT_DIR}" && sha256sum -c SHA256SUMS >/dev/null)
else
  (cd "${OUTPUT_DIR}" && shasum -a 256 -c SHA256SUMS >/dev/null)
fi

# 由最终打包程序再次验证同一套门禁，证明定稿目录可直接进入 LobeHub-only 介质。
TEST_AGENT_LOBEHUB_ARTIFACT_DIR="${OUTPUT_DIR}" \
  "${INTERNAL_DIR}/package-release.sh" --env-file /dev/null --lobehub-only \
  --output-dir "${PACKAGE_DIR}" >/dev/null
test -f "${PACKAGE_DIR}/test-agent-lobehub-offline.zip"

assert_rejected 'an existing output without --force' \
  "${FINALIZER}" --server-artifact-dir "${SERVER_DIR}" --output-dir "${OUTPUT_DIR}" \
  --windows-client "${WINDOWS_CLIENT}" --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}" --linux-build-evidence "${LINUX_BUILD_EVIDENCE}"

assert_rejected 'a forced output equal to the signed Windows client input' \
  "${FINALIZER}" --force --server-artifact-dir "${SERVER_DIR}" \
  --output-dir "${WINDOWS_CLIENT}" \
  --windows-client "${WINDOWS_CLIENT}" --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}" --linux-build-evidence "${LINUX_BUILD_EVIDENCE}"
test -f "${WINDOWS_CLIENT}"

assert_rejected 'a forced output containing all external client inputs' \
  "${FINALIZER}" --force --server-artifact-dir "${SERVER_DIR}" \
  --output-dir "${CLIENT_DIR}" \
  --windows-client "${WINDOWS_CLIENT}" --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}" --linux-build-evidence "${LINUX_BUILD_EVIDENCE}"
test -f "${WINDOWS_CLIENT}"
test -f "${LINUX_CLIENT}"

TAMPERED_SERVER="${FIXTURE_ROOT}/server-tampered"
cp -a "${SERVER_DIR}" "${TAMPERED_SERVER}"
printf 'tampered\n' >>"${TAMPERED_SERVER}/images/lobehub-image.tar"
assert_rejected 'a server artifact changed after SHA256SUMS generation' \
  "${FINALIZER}" --server-artifact-dir "${TAMPERED_SERVER}" \
  --output-dir "${FIXTURE_ROOT}/tampered-output" \
  --windows-client "${WINDOWS_CLIENT}" --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}" --linux-build-evidence "${LINUX_BUILD_EVIDENCE}"

SPECIAL_SERVER="${FIXTURE_ROOT}/server-special-file"
cp -a "${SERVER_DIR}" "${SPECIAL_SERVER}"
mkfifo "${SPECIAL_SERVER}/untracked.pipe"
assert_rejected 'a special file outside the regular-file checksum manifest' \
  "${FINALIZER}" --server-artifact-dir "${SPECIAL_SERVER}" \
  --output-dir "${FIXTURE_ROOT}/special-file-output" \
  --windows-client "${WINDOWS_CLIENT}" --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}" --linux-build-evidence "${LINUX_BUILD_EVIDENCE}"

COMPLETE_SOURCE="${FIXTURE_ROOT}/already-complete"
cp -a "${SERVER_DIR}" "${COMPLETE_SOURCE}"
sed -e 's/^LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=false$/LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=true/' \
  -e '/^LOBEHUB_LINUX_EXECUTION_DEFAULT=false$/i\
LOBEHUB_LINUX_CLIENT_APPROVED=true' \
  "${SERVER_DIR}/release.env" >"${COMPLETE_SOURCE}/release.env"
write_manifest "${COMPLETE_SOURCE}"
assert_rejected 'an artifact set already claiming client approval' \
  "${FINALIZER}" --server-artifact-dir "${COMPLETE_SOURCE}" \
  --output-dir "${FIXTURE_ROOT}/complete-source-output" \
  --windows-client "${WINDOWS_CLIENT}" --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}" --linux-build-evidence "${LINUX_BUILD_EVIDENCE}"

PENDING_EVIDENCE="${CLIENT_DIR}/linux-client-verification-pending.txt"
sed 's/^LINUX_APPROVAL_STATUS=Approved$/LINUX_APPROVAL_STATUS=Pending/' \
  "${LINUX_EVIDENCE}" >"${PENDING_EVIDENCE}"
assert_rejected 'pending Linux approval evidence' \
  "${FINALIZER}" --server-artifact-dir "${SERVER_DIR}" \
  --output-dir "${FIXTURE_ROOT}/pending-output" \
  --windows-client "${WINDOWS_CLIENT}" --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${PENDING_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}" --linux-build-evidence "${LINUX_BUILD_EVIDENCE}"

# 输出父目录位于受保护输入内时，必须在创建任何目录之前失败。
NESTED_OUTPUT="${SERVER_DIR}/must-not-be-created/complete"
assert_rejected 'an output nested below the server source' \
  "${FINALIZER}" --server-artifact-dir "${SERVER_DIR}" --output-dir "${NESTED_OUTPUT}" \
  --windows-client "${WINDOWS_CLIENT}" --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}" --linux-build-evidence "${LINUX_BUILD_EVIDENCE}"
test ! -e "${SERVER_DIR}/must-not-be-created"

# 校验后的复制内容若被改变，不能通过删除旧清单并生成新清单把篡改“洗白”。
FAKE_CP_DIR="${FIXTURE_ROOT}/fake-cp-bin"
mkdir "${FAKE_CP_DIR}"
cat >"${FAKE_CP_DIR}/cp" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
"${LOBEHUB_FINALIZER_REAL_CP:?}" "$@"
if [[ "${1:-}" == -a && "${2:-}" == "${LOBEHUB_FINALIZER_TAMPER_SOURCE:?}/." ]]; then
  printf 'changed-after-verification\n' >>"${3%/}/images/lobehub-image.tar"
fi
EOF
chmod 0755 "${FAKE_CP_DIR}/cp"
assert_rejected 'server bytes changed between verification and final manifest generation' \
  env PATH="${FAKE_CP_DIR}:${PATH}" LOBEHUB_FINALIZER_REAL_CP="$(command -v cp)" \
  LOBEHUB_FINALIZER_TAMPER_SOURCE="$(cd "${SERVER_DIR}" && pwd -P)" \
  "${FINALIZER}" --server-artifact-dir "${SERVER_DIR}" \
  --output-dir "${FIXTURE_ROOT}/toctou-output" \
  --windows-client "${WINDOWS_CLIENT}" --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}" --linux-build-evidence "${LINUX_BUILD_EVIDENCE}"
test ! -e "${FIXTURE_ROOT}/toctou-output"

# 外部客户端与其文字证据不能在第一次门禁通过后被成对替换。
CLIENT_TAMPER_DIR="${FIXTURE_ROOT}/client-toctou"
CLIENT_TAMPER_BIN="${FIXTURE_ROOT}/client-toctou-bin"
mkdir "${CLIENT_TAMPER_DIR}" "${CLIENT_TAMPER_BIN}"
cp "${WINDOWS_CLIENT}" "${CLIENT_TAMPER_DIR}/lobehub-windows-x64.exe"
cp "${WINDOWS_EVIDENCE}" "${CLIENT_TAMPER_DIR}/windows-authenticode-verification.txt"
cat >"${CLIENT_TAMPER_BIN}/cp" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
"${LOBEHUB_FINALIZER_REAL_CP:?}" "$@"
if [[ "${1:-}" == -a && "${2:-}" == "${LOBEHUB_FINALIZER_TAMPER_SOURCE:?}/." ]]; then
  printf 'replacement-client-after-verification\n' >"${LOBEHUB_FINALIZER_TAMPER_CLIENT:?}"
  if command -v sha256sum >/dev/null 2>&1; then
    replacement_sha="$(sha256sum "${LOBEHUB_FINALIZER_TAMPER_CLIENT}" | awk '{print $1}')"
  else
    replacement_sha="$(shasum -a 256 "${LOBEHUB_FINALIZER_TAMPER_CLIENT}" | awk '{print $1}')"
  fi
  sed "s/^AUTHENTICODE_FILE_SHA256=.*$/AUTHENTICODE_FILE_SHA256=${replacement_sha}/" \
    "${LOBEHUB_FINALIZER_TAMPER_EVIDENCE:?}" >"${LOBEHUB_FINALIZER_TAMPER_EVIDENCE}.new"
  mv "${LOBEHUB_FINALIZER_TAMPER_EVIDENCE}.new" "${LOBEHUB_FINALIZER_TAMPER_EVIDENCE}"
fi
EOF
chmod 0755 "${CLIENT_TAMPER_BIN}/cp"
assert_rejected 'a client and its evidence replaced after initial verification' \
  env PATH="${CLIENT_TAMPER_BIN}:${PATH}" LOBEHUB_FINALIZER_REAL_CP="$(command -v cp)" \
  LOBEHUB_FINALIZER_TAMPER_SOURCE="$(cd "${SERVER_DIR}" && pwd -P)" \
  LOBEHUB_FINALIZER_TAMPER_CLIENT="${CLIENT_TAMPER_DIR}/lobehub-windows-x64.exe" \
  LOBEHUB_FINALIZER_TAMPER_EVIDENCE="${CLIENT_TAMPER_DIR}/windows-authenticode-verification.txt" \
  "${FINALIZER}" --server-artifact-dir "${SERVER_DIR}" \
  --output-dir "${FIXTURE_ROOT}/client-toctou-output" \
  --windows-client "${CLIENT_TAMPER_DIR}/lobehub-windows-x64.exe" \
  --windows-signature-evidence "${CLIENT_TAMPER_DIR}/windows-authenticode-verification.txt" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}" --linux-build-evidence "${LINUX_BUILD_EVIDENCE}"
test ! -e "${FIXTURE_ROOT}/client-toctou-output"

# --force 只授权替换最初检查到的那个目录，不能删除并发出现的另一个对象。
RACE_OUTPUT="${FIXTURE_ROOT}/race-output"
RACE_PRESERVED="${FIXTURE_ROOT}/race-output-original"
RACE_BIN="${FIXTURE_ROOT}/race-bin"
mkdir "${RACE_OUTPUT}" "${RACE_BIN}"
printf 'original\n' >"${RACE_OUTPUT}/original-marker"
cat >"${RACE_BIN}/cp" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
"${LOBEHUB_FINALIZER_REAL_CP:?}" "$@"
if [[ "${1:-}" == -a && "${2:-}" == "${LOBEHUB_FINALIZER_TAMPER_SOURCE:?}/." ]]; then
  mv "${LOBEHUB_FINALIZER_RACE_OUTPUT:?}" "${LOBEHUB_FINALIZER_RACE_PRESERVED:?}"
  mkdir "${LOBEHUB_FINALIZER_RACE_OUTPUT}"
  printf 'concurrent\n' >"${LOBEHUB_FINALIZER_RACE_OUTPUT}/concurrent-marker"
fi
EOF
chmod 0755 "${RACE_BIN}/cp"
assert_rejected 'a forced output object replaced concurrently' \
  env PATH="${RACE_BIN}:${PATH}" LOBEHUB_FINALIZER_REAL_CP="$(command -v cp)" \
  LOBEHUB_FINALIZER_TAMPER_SOURCE="$(cd "${SERVER_DIR}" && pwd -P)" \
  LOBEHUB_FINALIZER_RACE_OUTPUT="${RACE_OUTPUT}" \
  LOBEHUB_FINALIZER_RACE_PRESERVED="${RACE_PRESERVED}" \
  "${FINALIZER}" --force --server-artifact-dir "${SERVER_DIR}" --output-dir "${RACE_OUTPUT}" \
  --windows-client "${WINDOWS_CLIENT}" --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}" --linux-build-evidence "${LINUX_BUILD_EVIDENCE}"
test -f "${RACE_OUTPUT}/concurrent-marker"
test -f "${RACE_PRESERVED}/original-marker"

echo "LobeHub offline artifact finalizer test passed"
