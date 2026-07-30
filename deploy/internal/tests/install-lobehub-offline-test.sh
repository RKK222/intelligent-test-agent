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
  "${FIXTURE_INTERNAL}/systemd" "${FAKE_BIN}"
cp "${INTERNAL_DIR}/install-lobehub-offline.sh" "${FIXTURE_INTERNAL}/install-lobehub-offline.sh"
cp "${INTERNAL_DIR}/lobehub-docker.sh" "${FIXTURE_INTERNAL}/lobehub-docker.sh"
cp "${INTERNAL_DIR}/lobehub-backup.sh" "${FIXTURE_INTERNAL}/lobehub-backup.sh"
cp "${INTERNAL_DIR}/lobehub.env.example" "${FIXTURE_INTERNAL}/lobehub.env.example"
cp "${INTERNAL_DIR}/systemd/test-agent-lobehub.service" \
  "${FIXTURE_INTERNAL}/systemd/test-agent-lobehub.service"
chmod 0755 "${FIXTURE_INTERNAL}/install-lobehub-offline.sh"

for file in \
  images/lobehub-image.tar images/paradedb-image.tar images/rustfs-image.tar \
  clients/lobehub-windows-x64.exe clients/lobehub-linux-x86_64.tar.gz \
  bin/mc-linux-amd64 sbom/lobehub.spdx.json \
  source/lobehub-v2.2.11-platform.1.tar.gz \
  approved-resources.json LICENSES.txt; do
  printf 'installer-fixture:%s\n' "${file}" >"${ARTIFACT_DIR}/${file}"
done

cat >"${ARTIFACT_DIR}/release.env" <<'EOF'
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.1
LOBEHUB_UPSTREAM_VERSION=v2.2.11
LOBEHUB_UPSTREAM_COMMIT=5b4cef6
LOBEHUB_FORK_COMMIT=7d16863c88b8acbacda6d9ee15df0840749e0aaa
LOBEHUB_PLATFORM_CONTRACT_VERSION=1
LOBEHUB_PARADEDB_POSTGRES_MAJOR=17
LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=true
LOBEHUB_LINUX_EXECUTION_DEFAULT=false
LOBEHUB_APP_IMAGE=test-agent/lobehub:v2.2.11-platform.1
LOBEHUB_APP_IMAGE_ID=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
LOBEHUB_PARADEDB_IMAGE=test-agent/paradedb:pg17-v2.2.11-platform.1
LOBEHUB_PARADEDB_IMAGE_ID=sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
LOBEHUB_RUSTFS_IMAGE=test-agent/rustfs:v2.2.11-platform.1
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
cat >"${ARTIFACT_DIR}/windows-authenticode-verification.txt" <<EOF
AUTHENTICODE_STATUS=Valid
AUTHENTICODE_SUBJECT=CN=TestAgent Installer Fixture
AUTHENTICODE_THUMBPRINT=0123456789ABCDEF
AUTHENTICODE_FILE_SHA256=${windows_sha}
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
test -f "${RUNTIME_ROOT}/testagent/lobehub/clients/lobehub-windows-x64.exe"
test -f "${RUNTIME_ROOT}/testagent/lobehub/clients/lobehub-linux-x86_64.tar.gz"
test -f "${RUNTIME_ROOT}/testagent/lobehub/release/windows-authenticode-verification.txt"
test -f "${RUNTIME_ROOT}/systemd/test-agent-lobehub.service"
test "$(stat -c '%a' "${RUNTIME_ROOT}/testagent/config/lobehub.env" 2>/dev/null || \
  stat -f '%Lp' "${RUNTIME_ROOT}/testagent/config/lobehub.env")" = 600
test "$(grep -c '^load -i ' "${DOCKER_CALLS}")" = 3
grep -Fx 'daemon-reload' "${SYSTEMCTL_CALLS}" >/dev/null

# 服务端阶段目录不得在导入任何镜像或写入目标目录后才失败。
docker_calls_before="$(wc -l <"${DOCKER_CALLS}" | tr -d '[:space:]')"
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
