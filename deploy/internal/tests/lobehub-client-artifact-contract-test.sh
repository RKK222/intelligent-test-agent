#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTERNAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
CONTRACT="${INTERNAL_DIR}/lobehub-client-artifact-contract.sh"
FIXTURE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/lobehub-client-contract.XXXXXX")"
trap 'rm -rf "${FIXTURE_DIR}"' EXIT

# 客户端制品门禁由构建、汇集和现场安装共用；缺少实现时测试必须直接失败。
source "${CONTRACT}"

INTERNAL_VERSION="v2.2.11-platform.7"
FORK_COMMIT="bf73f5f2c1e7f3309ecc1eb874ef58ca587b3a04"
WINDOWS_CLIENT="${FIXTURE_DIR}/lobehub-windows-x64.exe"
WINDOWS_EVIDENCE="${FIXTURE_DIR}/windows-authenticode-verification.txt"
LINUX_CLIENT="${FIXTURE_DIR}/lobehub-linux-x86_64.tar.gz"
LINUX_EVIDENCE="${FIXTURE_DIR}/linux-client-verification.txt"
LINUX_ACCEPTANCE_RECORD="${FIXTURE_DIR}/linux-client-acceptance-record.txt"
LINUX_BUILD_EVIDENCE="${FIXTURE_DIR}/linux-client-build-evidence.txt"

printf 'signed-windows-client\n' >"${WINDOWS_CLIENT}"
printf 'approved-linux-client\n' >"${LINUX_CLIENT}"

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

write_valid_evidence() {
  cat >"${LINUX_BUILD_EVIDENCE}" <<EOF
LINUX_BUILD_STATUS=Candidate
LINUX_BUILDER=linux-builder@example.internal
LINUX_CLIENT_FILE_SHA256=$(sha256_file "${LINUX_CLIENT}")
LOBEHUB_INTERNAL_VERSION=${INTERNAL_VERSION}
LOBEHUB_FORK_COMMIT=${FORK_COMMIT}
CLIENT_ARCHITECTURE=x86_64
CLIENT_EXECUTION_MODE=disabled
BUILD_OS=Enterprise Linux Builder 9.6
BUILD_KERNEL=5.14.0-builder.x86_64
BUILD_NODE_VERSION=v24.11.1
BUILD_BUN_VERSION=1.3.2
SOURCE_ARCHIVE_SHA256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
EOF
  cat >"${LINUX_ACCEPTANCE_RECORD}" <<'EOF'
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
LOBEHUB_INTERNAL_VERSION=${INTERNAL_VERSION}
LOBEHUB_FORK_COMMIT=${FORK_COMMIT}
CLIENT_ARCHITECTURE=x64
CLIENT_EXECUTION_MODE=disabled
EOF
  cat >"${LINUX_EVIDENCE}" <<EOF
LINUX_APPROVAL_STATUS=Approved
LINUX_APPROVER=security-reviewer@example.internal
LINUX_CLIENT_FILE_SHA256=$(sha256_file "${LINUX_CLIENT}")
LOBEHUB_INTERNAL_VERSION=${INTERNAL_VERSION}
LOBEHUB_FORK_COMMIT=${FORK_COMMIT}
CLIENT_ARCHITECTURE=x86_64
CLIENT_EXECUTION_MODE=disabled
LINUX_VALIDATION_OS=Enterprise Linux 9.6
LINUX_VALIDATION_KERNEL=5.14.0-570.26.1.el9_6.x86_64
LINUX_ACCEPTANCE_RECORD_SHA256=$(sha256_file "${LINUX_ACCEPTANCE_RECORD}")
LINUX_BUILD_EVIDENCE_SHA256=$(sha256_file "${LINUX_BUILD_EVIDENCE}")
EOF
}

assert_rejected() {
  local description="$1"
  if lobehub_verify_client_artifacts \
    "${WINDOWS_CLIENT}" "${WINDOWS_EVIDENCE}" \
    "${LINUX_CLIENT}" "${LINUX_EVIDENCE}" \
    "${LINUX_ACCEPTANCE_RECORD}" "${LINUX_BUILD_EVIDENCE}" \
    "${INTERNAL_VERSION}" "${FORK_COMMIT}" >/dev/null 2>&1; then
    echo "Client artifact contract unexpectedly accepted ${description}" >&2
    exit 1
  fi
}

write_valid_evidence
lobehub_verify_client_artifacts \
  "${WINDOWS_CLIENT}" "${WINDOWS_EVIDENCE}" \
  "${LINUX_CLIENT}" "${LINUX_EVIDENCE}" \
  "${LINUX_ACCEPTANCE_RECORD}" "${LINUX_BUILD_EVIDENCE}" \
  "${INTERNAL_VERSION}" "${FORK_COMMIT}"

cp "${LINUX_BUILD_EVIDENCE}" "${FIXTURE_DIR}/linux-build-valid.txt"
sed 's/^LINUX_BUILDER=.*$/LINUX_BUILDER=security-reviewer@example.internal/' \
  "${FIXTURE_DIR}/linux-build-valid.txt" >"${LINUX_BUILD_EVIDENCE}"
BUILD_EVIDENCE_SHA="$(sha256_file "${LINUX_BUILD_EVIDENCE}")"
sed "s/^LINUX_BUILD_EVIDENCE_SHA256=.*$/LINUX_BUILD_EVIDENCE_SHA256=${BUILD_EVIDENCE_SHA}/" \
  "${LINUX_EVIDENCE}" >"${FIXTURE_DIR}/linux-same-reviewer.txt"
mv "${FIXTURE_DIR}/linux-same-reviewer.txt" "${LINUX_EVIDENCE}"
assert_rejected 'the same Linux builder and approver identity'
mv "${FIXTURE_DIR}/linux-build-valid.txt" "${LINUX_BUILD_EVIDENCE}"
write_valid_evidence

cp "${LINUX_EVIDENCE}" "${FIXTURE_DIR}/linux-valid.txt"
sed 's/^LINUX_APPROVAL_STATUS=Approved$/LINUX_APPROVAL_STATUS=Pending/' \
  "${FIXTURE_DIR}/linux-valid.txt" >"${LINUX_EVIDENCE}"
assert_rejected 'a pending Linux approval'

cp "${FIXTURE_DIR}/linux-valid.txt" "${LINUX_EVIDENCE}"
printf 'LINUX_APPROVER=duplicate@example.internal\n' >>"${LINUX_EVIDENCE}"
assert_rejected 'a duplicate evidence key'

sed 's/^LINUX_APPROVER=.*$/LINUX_APPROVER=REPLACE_ME/' \
  "${FIXTURE_DIR}/linux-valid.txt" >"${LINUX_EVIDENCE}"
assert_rejected 'a placeholder Linux approver'

cp "${LINUX_ACCEPTANCE_RECORD}" "${FIXTURE_DIR}/linux-acceptance-valid.txt"
sed 's/^RUNTIME_DOWNLOAD_RESULT=Blocked$/RUNTIME_DOWNLOAD_RESULT=Allowed/' \
  "${FIXTURE_DIR}/linux-acceptance-valid.txt" >"${LINUX_ACCEPTANCE_RECORD}"
INVALID_RECORD_SHA="$(sha256_file "${LINUX_ACCEPTANCE_RECORD}")"
sed "s/^LINUX_ACCEPTANCE_RECORD_SHA256=.*$/LINUX_ACCEPTANCE_RECORD_SHA256=${INVALID_RECORD_SHA}/" \
  "${FIXTURE_DIR}/linux-valid.txt" >"${LINUX_EVIDENCE}"
assert_rejected 'an acceptance record that allows runtime downloads'
mv "${FIXTURE_DIR}/linux-acceptance-valid.txt" "${LINUX_ACCEPTANCE_RECORD}"

sed 's/^LOBEHUB_FORK_COMMIT=.*$/LOBEHUB_FORK_COMMIT=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/' \
  "${FIXTURE_DIR}/linux-valid.txt" >"${LINUX_EVIDENCE}"
assert_rejected 'Linux evidence for another fork commit'

cp "${FIXTURE_DIR}/linux-valid.txt" "${LINUX_EVIDENCE}"
cp "${WINDOWS_EVIDENCE}" "${FIXTURE_DIR}/windows-valid.txt"
sed 's/^CLIENT_ARCHITECTURE=x64$/CLIENT_ARCHITECTURE=arm64/' \
  "${FIXTURE_DIR}/windows-valid.txt" >"${WINDOWS_EVIDENCE}"
assert_rejected 'a Windows arm64 client'

sed 's/^AUTHENTICODE_THUMBPRINT=.*$/AUTHENTICODE_THUMBPRINT=0123456789ABCDEF/' \
  "${FIXTURE_DIR}/windows-valid.txt" >"${WINDOWS_EVIDENCE}"
assert_rejected 'a malformed signer thumbprint'

cp "${FIXTURE_DIR}/windows-valid.txt" "${WINDOWS_EVIDENCE}"
printf 'tampered\n' >>"${LINUX_CLIENT}"
assert_rejected 'a Linux client whose digest changed after approval'

printf 'approved-linux-client\n' >"${LINUX_CLIENT}"
printf 'tampered\n' >>"${LINUX_ACCEPTANCE_RECORD}"
assert_rejected 'a Linux acceptance record whose digest changed after approval'

write_valid_evidence
printf 'tampered\n' >>"${LINUX_BUILD_EVIDENCE}"
assert_rejected 'Linux build evidence changed after approval'

echo "LobeHub client artifact contract test passed"
