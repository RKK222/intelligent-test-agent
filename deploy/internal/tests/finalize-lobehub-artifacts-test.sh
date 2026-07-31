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
  source/lobehub-v2.2.11-platform.3.tar.gz \
  approved-resources.json LICENSES.txt; do
  printf 'fixture:%s\n' "${file}" >"${SERVER_DIR}/${file}"
done

# 保留早期 platform.3 server-only 介质的真实兼容形态：尚无 Linux 审批状态字段。
cat >"${SERVER_DIR}/release.env" <<'EOF'
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.3
LOBEHUB_UPSTREAM_VERSION=v2.2.11
LOBEHUB_UPSTREAM_COMMIT=5b4cef6
LOBEHUB_FORK_COMMIT=ccd0400fbe934ba929de637a315d25e969977c76
LOBEHUB_PLATFORM_CONTRACT_VERSION=2
LOBEHUB_PARADEDB_POSTGRES_MAJOR=17
LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=false
LOBEHUB_LINUX_EXECUTION_DEFAULT=false
LOBEHUB_APP_IMAGE=test-agent/lobehub:v2.2.11-platform.3
LOBEHUB_APP_IMAGE_ID=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
LOBEHUB_PARADEDB_IMAGE=test-agent/paradedb:pg17-v2.2.11-platform.3
LOBEHUB_PARADEDB_IMAGE_ID=sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
LOBEHUB_RUSTFS_IMAGE=test-agent/rustfs:v2.2.11-platform.3
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
printf 'signed-windows-client\n' >"${WINDOWS_CLIENT}"
printf 'approved-linux-client\n' >"${LINUX_CLIENT}"
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
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.3
LOBEHUB_FORK_COMMIT=ccd0400fbe934ba929de637a315d25e969977c76
CLIENT_ARCHITECTURE=x64
CLIENT_EXECUTION_MODE=disabled
EOF
cat >"${LINUX_EVIDENCE}" <<EOF
LINUX_APPROVAL_STATUS=Approved
LINUX_APPROVER=security-reviewer@example.internal
LINUX_CLIENT_FILE_SHA256=$(sha256_file "${LINUX_CLIENT}")
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.3
LOBEHUB_FORK_COMMIT=ccd0400fbe934ba929de637a315d25e969977c76
CLIENT_ARCHITECTURE=x86_64
CLIENT_EXECUTION_MODE=disabled
LINUX_VALIDATION_OS=Enterprise Linux 9.6
LINUX_VALIDATION_KERNEL=5.14.0-570.26.1.el9_6.x86_64
LINUX_ACCEPTANCE_RECORD_SHA256=$(sha256_file "${LINUX_RECORD}")
EOF

"${FINALIZER}" \
  --server-artifact-dir "${SERVER_DIR}" \
  --output-dir "${OUTPUT_DIR}" \
  --windows-client "${WINDOWS_CLIENT}" \
  --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" \
  --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}" >/dev/null

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
  --linux-acceptance-record "${LINUX_RECORD}"

assert_rejected 'a forced output equal to the signed Windows client input' \
  "${FINALIZER}" --force --server-artifact-dir "${SERVER_DIR}" \
  --output-dir "${WINDOWS_CLIENT}" \
  --windows-client "${WINDOWS_CLIENT}" --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}"
test -f "${WINDOWS_CLIENT}"

assert_rejected 'a forced output containing all external client inputs' \
  "${FINALIZER}" --force --server-artifact-dir "${SERVER_DIR}" \
  --output-dir "${CLIENT_DIR}" \
  --windows-client "${WINDOWS_CLIENT}" --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}"
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
  --linux-acceptance-record "${LINUX_RECORD}"

SPECIAL_SERVER="${FIXTURE_ROOT}/server-special-file"
cp -a "${SERVER_DIR}" "${SPECIAL_SERVER}"
mkfifo "${SPECIAL_SERVER}/untracked.pipe"
assert_rejected 'a special file outside the regular-file checksum manifest' \
  "${FINALIZER}" --server-artifact-dir "${SPECIAL_SERVER}" \
  --output-dir "${FIXTURE_ROOT}/special-file-output" \
  --windows-client "${WINDOWS_CLIENT}" --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${LINUX_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}"

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
  --linux-acceptance-record "${LINUX_RECORD}"

PENDING_EVIDENCE="${CLIENT_DIR}/linux-client-verification-pending.txt"
sed 's/^LINUX_APPROVAL_STATUS=Approved$/LINUX_APPROVAL_STATUS=Pending/' \
  "${LINUX_EVIDENCE}" >"${PENDING_EVIDENCE}"
assert_rejected 'pending Linux approval evidence' \
  "${FINALIZER}" --server-artifact-dir "${SERVER_DIR}" \
  --output-dir "${FIXTURE_ROOT}/pending-output" \
  --windows-client "${WINDOWS_CLIENT}" --windows-signature-evidence "${WINDOWS_EVIDENCE}" \
  --linux-client "${LINUX_CLIENT}" --linux-approval-evidence "${PENDING_EVIDENCE}" \
  --linux-acceptance-record "${LINUX_RECORD}"

echo "LobeHub offline artifact finalizer test passed"
