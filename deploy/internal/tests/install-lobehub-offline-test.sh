#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTERNAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-lobehub-install-fixture.XXXXXX")"
RUNTIME_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-lobehub-install-runtime.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}" "${RUNTIME_ROOT}"' EXIT

ARTIFACT_DIR="${FIXTURE_ROOT}/dist/lobehub"
FIXTURE_INTERNAL="${FIXTURE_ROOT}/deploy/internal"
FAKE_BIN="${FIXTURE_ROOT}/bin"
DOCKER_CALLS="${FIXTURE_ROOT}/docker.calls"
SYSTEMCTL_CALLS="${FIXTURE_ROOT}/systemctl.calls"
mkdir -p "${ARTIFACT_DIR}"/{images,clients,bin,sbom,source} \
  "${FIXTURE_INTERNAL}/systemd" "${FIXTURE_INTERNAL}/lobehub" "${FAKE_BIN}"
cp "${INTERNAL_DIR}/install-lobehub-offline.sh" "${FIXTURE_INTERNAL}/install-lobehub-offline.sh"
cp "${INTERNAL_DIR}/lobehub-docker.sh" "${FIXTURE_INTERNAL}/lobehub-docker.sh"
cp "${INTERNAL_DIR}/lobehub-backup.sh" "${FIXTURE_INTERNAL}/lobehub-backup.sh"
cp "${INTERNAL_DIR}/lobehub-platform-probe.mjs" \
  "${FIXTURE_INTERNAL}/lobehub-platform-probe.mjs"
cp "${INTERNAL_DIR}/lobehub-redis-acl.sh" "${FIXTURE_INTERNAL}/lobehub-redis-acl.sh"
cp "${INTERNAL_DIR}/lobehub-client-artifact-contract.sh" \
  "${FIXTURE_INTERNAL}/lobehub-client-artifact-contract.sh"
cp "${INTERNAL_DIR}/lobehub.env.example" "${FIXTURE_INTERNAL}/lobehub.env.example"
cp "${INTERNAL_DIR}/lobehub/version.env" "${FIXTURE_INTERNAL}/lobehub/version.env"
cp "${INTERNAL_DIR}/systemd/test-agent-lobehub.service" \
  "${FIXTURE_INTERNAL}/systemd/test-agent-lobehub.service"
chmod 0755 "${FIXTURE_INTERNAL}/install-lobehub-offline.sh"

for file in \
  images/lobehub-image.tar images/paradedb-image.tar images/rustfs-image.tar \
  clients/lobehub-windows-x64.exe clients/lobehub-linux-x86_64.tar.gz \
  bin/mc-linux-amd64 sbom/lobehub.spdx.json \
  source/lobehub-v2.2.11-platform.5.tar.gz \
  approved-resources.json LICENSES.txt; do
  printf 'installer-fixture:%s\n' "${file}" >"${ARTIFACT_DIR}/${file}"
done

cat >"${ARTIFACT_DIR}/release.env" <<'EOF'
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.5
LOBEHUB_UPSTREAM_VERSION=v2.2.11
LOBEHUB_UPSTREAM_COMMIT=5b4cef6
LOBEHUB_FORK_COMMIT=57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c
LOBEHUB_PLATFORM_CONTRACT_VERSION=2
LOBEHUB_PARADEDB_POSTGRES_MAJOR=17
LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=true
LOBEHUB_LINUX_CLIENT_APPROVED=true
LOBEHUB_LINUX_EXECUTION_DEFAULT=false
LOBEHUB_APP_IMAGE=test-agent/lobehub:v2.2.11-platform.5
LOBEHUB_APP_IMAGE_ID=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
LOBEHUB_PARADEDB_IMAGE=test-agent/paradedb:pg17-v2.2.11-platform.5
LOBEHUB_PARADEDB_IMAGE_ID=sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
LOBEHUB_RUSTFS_IMAGE=test-agent/rustfs:v2.2.11-platform.5
LOBEHUB_RUSTFS_IMAGE_ID=sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
EOF

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

write_checksums() {
  (
    cd "${ARTIFACT_DIR}"
    find . -type f ! -name SHA256SUMS | LC_ALL=C sort | while IFS= read -r file; do
      if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "${file#./}"
      else
        shasum -a 256 "${file#./}"
      fi
    done >SHA256SUMS
  )
}

windows_sha="$(sha256_file "${ARTIFACT_DIR}/clients/lobehub-windows-x64.exe")"
linux_sha="$(sha256_file "${ARTIFACT_DIR}/clients/lobehub-linux-x86_64.tar.gz")"
cat >"${ARTIFACT_DIR}/linux-client-build-evidence.txt" <<EOF
LINUX_BUILD_STATUS=Candidate
LINUX_BUILDER=linux-builder@example.internal
LINUX_CLIENT_FILE_SHA256=${linux_sha}
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
cat >"${ARTIFACT_DIR}/windows-authenticode-verification.txt" <<EOF
AUTHENTICODE_STATUS=Valid
AUTHENTICODE_SUBJECT=CN=Enterprise Release Signing
AUTHENTICODE_THUMBPRINT=0123456789ABCDEF0123456789ABCDEF01234567
AUTHENTICODE_FILE_SHA256=${windows_sha}
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.5
LOBEHUB_FORK_COMMIT=57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c
CLIENT_ARCHITECTURE=x64
CLIENT_EXECUTION_MODE=disabled
EOF
cat >"${ARTIFACT_DIR}/linux-client-acceptance-record.txt" <<'EOF'
VALIDATION_RESULT=Passed
CLIENT_LOGIN_RESULT=Passed
DEVICE_EXECUTION_RESULT=Disabled
PUBLIC_NETWORK_DEPENDENCY_RESULT=None
RUNTIME_DOWNLOAD_RESULT=Blocked
LOCAL_DATA_ISOLATION_RESULT=Passed
REVIEWER=security-reviewer@example.internal
APPROVAL_CHANGE_ID=CHG-20260731-LOBEHUB
EOF
acceptance_sha="$(sha256_file "${ARTIFACT_DIR}/linux-client-acceptance-record.txt")"
cat >"${ARTIFACT_DIR}/linux-client-verification.txt" <<EOF
LINUX_APPROVAL_STATUS=Approved
LINUX_APPROVER=security-reviewer@example.internal
LINUX_CLIENT_FILE_SHA256=${linux_sha}
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.5
LOBEHUB_FORK_COMMIT=57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c
CLIENT_ARCHITECTURE=x86_64
CLIENT_EXECUTION_MODE=disabled
LINUX_VALIDATION_OS=Enterprise Linux 9.6
LINUX_VALIDATION_KERNEL=5.14.0-570.26.1.el9_6.x86_64
LINUX_ACCEPTANCE_RECORD_SHA256=${acceptance_sha}
LINUX_BUILD_EVIDENCE_SHA256=$(sha256_file "${ARTIFACT_DIR}/linux-client-build-evidence.txt")
EOF
write_checksums

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
printf '%s\n' "$*" >>"${LOBEHUB_INSTALL_TEST_DOCKER_CALLS:?}"
if [[ "${1:-}" == load ]]; then exit 0; fi
if [[ "${1:-}" == image && "${2:-}" == inspect ]]; then
  if [[ "${3:-}" != -f ]]; then exit 0; fi
  format="${4:-}"
  image_ref="${5:-}"
  case "${format}" in
    '{{.Architecture}}') echo amd64 ;;
    '{{.Os}}') echo linux ;;
    '{{.Id}}')
      case "${image_ref}" in
        test-agent/lobehub:*) echo sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ;;
        test-agent/paradedb:*) echo sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb ;;
        test-agent/rustfs:*) echo sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc ;;
        *) exit 1 ;;
      esac
      ;;
    *) exit 1 ;;
  esac
  exit 0
fi
exit 1
EOF
cat >"${FAKE_BIN}/systemctl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${LOBEHUB_INSTALL_TEST_SYSTEMCTL_CALLS:?}"
EOF
chmod 0755 "${FAKE_BIN}/id" "${FAKE_BIN}/docker" "${FAKE_BIN}/systemctl"

export LOBEHUB_INSTALL_TEST_DOCKER_CALLS="${DOCKER_CALLS}"
export LOBEHUB_INSTALL_TEST_SYSTEMCTL_CALLS="${SYSTEMCTL_CALLS}"
PATH="${FAKE_BIN}:${PATH}" \
LOBEHUB_ARTIFACT_DIR="${ARTIFACT_DIR}" \
TEST_AGENT_BASE_DIR="${RUNTIME_ROOT}/testagent" \
TEST_AGENT_SYSTEMD_UNIT_DIR="${RUNTIME_ROOT}/systemd" \
  "${FIXTURE_INTERNAL}/install-lobehub-offline.sh" >/dev/null

test -x "${RUNTIME_ROOT}/testagent/lobehub/bin/mc-linux-amd64"
test -x "${RUNTIME_ROOT}/testagent/deploy/internal/lobehub-backup.sh"
test -f "${RUNTIME_ROOT}/testagent/deploy/internal/lobehub-platform-probe.mjs"
test -x "${RUNTIME_ROOT}/testagent/deploy/internal/lobehub-redis-acl.sh"
test -f "${RUNTIME_ROOT}/testagent/lobehub/clients/lobehub-windows-x64.exe"
test -f "${RUNTIME_ROOT}/testagent/lobehub/clients/lobehub-linux-x86_64.tar.gz"
test -f "${RUNTIME_ROOT}/testagent/lobehub/release/windows-authenticode-verification.txt"
test -f "${RUNTIME_ROOT}/testagent/lobehub/release/linux-client-verification.txt"
test -f "${RUNTIME_ROOT}/testagent/lobehub/release/linux-client-acceptance-record.txt"
test -f "${RUNTIME_ROOT}/testagent/lobehub/release/linux-client-build-evidence.txt"
test -f "${RUNTIME_ROOT}/systemd/test-agent-lobehub.service"
test "$(stat -c '%a' "${RUNTIME_ROOT}/testagent/config/lobehub.env" 2>/dev/null || \
  stat -f '%Lp' "${RUNTIME_ROOT}/testagent/config/lobehub.env")" = 600
test "$(grep -c '^load -i ' "${DOCKER_CALLS}")" = 3
grep -Fx 'daemon-reload' "${SYSTEMCTL_CALLS}" >/dev/null

# 介质根目录只允许目录和普通文件；FIFO、socket、设备文件都必须在导入镜像前失败。
docker_calls_before="$(wc -l <"${DOCKER_CALLS}" | tr -d '[:space:]')"
mkfifo "${ARTIFACT_DIR}/unexpected.pipe"
if PATH="${FAKE_BIN}:${PATH}" \
  LOBEHUB_ARTIFACT_DIR="${ARTIFACT_DIR}" \
  TEST_AGENT_BASE_DIR="${RUNTIME_ROOT}/special-file" \
  TEST_AGENT_SYSTEMD_UNIT_DIR="${RUNTIME_ROOT}/special-file-systemd" \
    "${FIXTURE_INTERNAL}/install-lobehub-offline.sh" >/dev/null 2>&1; then
  echo "Installer unexpectedly accepted a non-regular artifact" >&2
  exit 1
fi
test "$(wc -l <"${DOCKER_CALLS}" | tr -d '[:space:]')" = "${docker_calls_before}"
test ! -e "${RUNTIME_ROOT}/special-file"
rm "${ARTIFACT_DIR}/unexpected.pipe"

# 现场安装器必须把 release.env 与随包版本锁逐项绑定，不能接受自报的新契约版本。
cp "${ARTIFACT_DIR}/release.env" "${FIXTURE_ROOT}/release.env.valid"
sed 's/^LOBEHUB_PLATFORM_CONTRACT_VERSION=2$/LOBEHUB_PLATFORM_CONTRACT_VERSION=999/' \
  "${FIXTURE_ROOT}/release.env.valid" >"${ARTIFACT_DIR}/release.env"
write_checksums
if PATH="${FAKE_BIN}:${PATH}" \
  LOBEHUB_ARTIFACT_DIR="${ARTIFACT_DIR}" \
  TEST_AGENT_BASE_DIR="${RUNTIME_ROOT}/lock-mismatch" \
  TEST_AGENT_SYSTEMD_UNIT_DIR="${RUNTIME_ROOT}/lock-mismatch-systemd" \
    "${FIXTURE_INTERNAL}/install-lobehub-offline.sh" >/dev/null 2>&1; then
  echo "Installer unexpectedly accepted a release/version-lock mismatch" >&2
  exit 1
fi
test "$(wc -l <"${DOCKER_CALLS}" | tr -d '[:space:]')" = "${docker_calls_before}"
test ! -e "${RUNTIME_ROOT}/lock-mismatch"
mv "${FIXTURE_ROOT}/release.env.valid" "${ARTIFACT_DIR}/release.env"
write_checksums

# 服务端阶段目录不得在导入任何镜像或写入目标目录后才失败。
rm "${ARTIFACT_DIR}/clients/lobehub-linux-x86_64.tar.gz"
write_checksums
if PATH="${FAKE_BIN}:${PATH}" \
  LOBEHUB_ARTIFACT_DIR="${ARTIFACT_DIR}" \
  TEST_AGENT_BASE_DIR="${RUNTIME_ROOT}/incomplete" \
  TEST_AGENT_SYSTEMD_UNIT_DIR="${RUNTIME_ROOT}/incomplete-systemd" \
    "${FIXTURE_INTERNAL}/install-lobehub-offline.sh" >/dev/null 2>&1; then
  echo "Installer unexpectedly accepted a server-only artifact set" >&2
  exit 1
fi
test "$(wc -l <"${DOCKER_CALLS}" | tr -d '[:space:]')" = "${docker_calls_before}"
test ! -e "${RUNTIME_ROOT}/incomplete"

printf 'installer-fixture:clients/lobehub-linux-x86_64.tar.gz\n' \
  >"${ARTIFACT_DIR}/clients/lobehub-linux-x86_64.tar.gz"
linux_sha="$(sha256_file "${ARTIFACT_DIR}/clients/lobehub-linux-x86_64.tar.gz")"
sed "s/^LINUX_CLIENT_FILE_SHA256=.*$/LINUX_CLIENT_FILE_SHA256=${linux_sha}/" \
  "${ARTIFACT_DIR}/linux-client-verification.txt" >"${FIXTURE_ROOT}/linux-client-verification.valid"
sed 's/^LINUX_APPROVAL_STATUS=Approved$/LINUX_APPROVAL_STATUS=Pending/' \
  "${FIXTURE_ROOT}/linux-client-verification.valid" \
  >"${ARTIFACT_DIR}/linux-client-verification.txt"
write_checksums
if PATH="${FAKE_BIN}:${PATH}" \
  LOBEHUB_ARTIFACT_DIR="${ARTIFACT_DIR}" \
  TEST_AGENT_BASE_DIR="${RUNTIME_ROOT}/pending-linux" \
  TEST_AGENT_SYSTEMD_UNIT_DIR="${RUNTIME_ROOT}/pending-linux-systemd" \
    "${FIXTURE_INTERNAL}/install-lobehub-offline.sh" >/dev/null 2>&1; then
  echo "Installer unexpectedly accepted pending Linux approval evidence" >&2
  exit 1
fi
test "$(wc -l <"${DOCKER_CALLS}" | tr -d '[:space:]')" = "${docker_calls_before}"
test ! -e "${RUNTIME_ROOT}/pending-linux"
mv "${FIXTURE_ROOT}/linux-client-verification.valid" \
  "${ARTIFACT_DIR}/linux-client-verification.txt"

sed 's/^AUTHENTICODE_STATUS=Valid$/AUTHENTICODE_STATUS=Invalid/' \
  "${ARTIFACT_DIR}/windows-authenticode-verification.txt" \
  >"${ARTIFACT_DIR}/windows-authenticode-verification.invalid"
mv "${ARTIFACT_DIR}/windows-authenticode-verification.invalid" \
  "${ARTIFACT_DIR}/windows-authenticode-verification.txt"
write_checksums
if PATH="${FAKE_BIN}:${PATH}" \
  LOBEHUB_ARTIFACT_DIR="${ARTIFACT_DIR}" \
  TEST_AGENT_BASE_DIR="${RUNTIME_ROOT}/invalid-signature" \
  TEST_AGENT_SYSTEMD_UNIT_DIR="${RUNTIME_ROOT}/invalid-signature-systemd" \
    "${FIXTURE_INTERNAL}/install-lobehub-offline.sh" >/dev/null 2>&1; then
  echo "Installer unexpectedly accepted invalid Authenticode evidence" >&2
  exit 1
fi
test "$(wc -l <"${DOCKER_CALLS}" | tr -d '[:space:]')" = "${docker_calls_before}"
test ! -e "${RUNTIME_ROOT}/invalid-signature"

echo "LobeHub offline installer contract test passed"
