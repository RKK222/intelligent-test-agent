#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTERNAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ROOT_DIR="$(cd "${INTERNAL_DIR}/../.." && pwd)"
FORK_DIR="${TEST_AGENT_LOBEHUB_FORK_DIR:-${ROOT_DIR}/../lobehub-platform}"
KIT_BUILDER="${INTERNAL_DIR}/build-lobehub-client-kit.sh"
LINUX_BUILDER="${INTERNAL_DIR}/clients/build-lobehub-linux-client.sh"
LINUX_APPROVER="${INTERNAL_DIR}/clients/approve-lobehub-linux-client.sh"
WINDOWS_BUILDER="${INTERNAL_DIR}/clients/Build-LobeHubWindowsClient.ps1"
OUTPUT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/lobehub-client-kit-output.XXXXXX")"
EXTRACT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/lobehub-client-kit-extract.XXXXXX")"
trap 'rm -rf "${OUTPUT_DIR}" "${EXTRACT_DIR}"' EXIT

"${KIT_BUILDER}" --help | grep -F -- '--fork-dir' >/dev/null
"${LINUX_BUILDER}" --help | grep -F -- '--output-dir' >/dev/null
"${LINUX_BUILDER}" --help | grep -F -- '--builder' >/dev/null
"${LINUX_APPROVER}" --help | grep -F -- '--acceptance-record' >/dev/null
grep -F 'pnpm@10.33.0 install --frozen-lockfile --node-linker=hoisted' \
  "${LINUX_BUILDER}" >/dev/null
grep -F 'LINUX_BUILD_STATUS=Candidate' "${LINUX_BUILDER}" >/dev/null
grep -F 'LINUX_BUILDER=' "${LINUX_BUILDER}" >/dev/null
grep -F -- '--linux tar.gz --x64' "${LINUX_BUILDER}" >/dev/null
grep -F -- '--confirm-device-execution-disabled' "${LINUX_APPROVER}" >/dev/null
grep -F 'lobehub_verify_linux_client_artifact' "${LINUX_APPROVER}" >/dev/null
grep -F 'Linux builder and approver must be different identities' "${LINUX_APPROVER}" >/dev/null
grep -F 'Get-AuthenticodeSignature' "${WINDOWS_BUILDER}" >/dev/null
grep -F 'signtool.exe' "${WINDOWS_BUILDER}" >/dev/null
grep -F "'--frozen-lockfile', '--node-linker=hoisted'" "${WINDOWS_BUILDER}" >/dev/null
grep -F "'--win', 'nsis', '--x64'" "${WINDOWS_BUILDER}" >/dev/null
grep -F 'AUTHENTICODE_FILE_SHA256' "${WINDOWS_BUILDER}" >/dev/null
grep -F 'CLIENT_EXECUTION_MODE=disabled' "${WINDOWS_BUILDER}" >/dev/null

"${KIT_BUILDER}" --fork-dir "${FORK_DIR}" --output-dir "${OUTPUT_DIR}" >/dev/null

KIT_ZIP="${OUTPUT_DIR}/lobehub-client-build-kit-v2.2.11-platform.5.zip"
test -s "${KIT_ZIP}"
test -s "${KIT_ZIP}.sha256"
(
  cd "${OUTPUT_DIR}"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum -c "$(basename "${KIT_ZIP}.sha256")"
  else
    shasum -a 256 -c "$(basename "${KIT_ZIP}.sha256")"
  fi
) >/dev/null

unzip -q "${KIT_ZIP}" -d "${EXTRACT_DIR}"
KIT_ROOT="${EXTRACT_DIR}/lobehub-client-build-kit-v2.2.11-platform.5"
test -f "${KIT_ROOT}/version.env"
test -f "${KIT_ROOT}/SOURCE_SHA256SUMS"
test -f "${KIT_ROOT}/BUILD_KIT_SHA256SUMS"
test -f "${KIT_ROOT}/source/lobehub-v2.2.11-platform.5.tar.gz"
test -f "${KIT_ROOT}/scripts/Build-LobeHubWindowsClient.ps1"
test -x "${KIT_ROOT}/scripts/build-lobehub-linux-client.sh"
test -x "${KIT_ROOT}/scripts/approve-lobehub-linux-client.sh"
test -f "${KIT_ROOT}/scripts/lobehub-client-artifact-contract.sh"
test -f "${KIT_ROOT}/BUILDING.md"
grep -Fx 'LOBEHUB_FORK_COMMIT=57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c' \
  "${KIT_ROOT}/version.env" >/dev/null
(
  cd "${KIT_ROOT}"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum -c BUILD_KIT_SHA256SUMS
  else
    shasum -a 256 -c BUILD_KIT_SHA256SUMS
  fi
) >/dev/null

if unzip -Z1 "${KIT_ZIP}" | grep -E '(^|/)(\.git|node_modules)(/|$)' >/dev/null; then
  echo "Client build kit unexpectedly contains a Git checkout or node_modules" >&2
  exit 1
fi

if "${KIT_BUILDER}" --fork-dir "${FORK_DIR}" --output-dir "${OUTPUT_DIR}" \
  >/dev/null 2>&1; then
  echo "Client build kit builder unexpectedly overwrote an existing kit without --force" >&2
  exit 1
fi

BEFORE_SHA="$(awk '{print $1}' "${KIT_ZIP}.sha256")"
"${KIT_BUILDER}" --fork-dir "${FORK_DIR}" --output-dir "${OUTPUT_DIR}" --force >/dev/null
AFTER_SHA="$(awk '{print $1}' "${KIT_ZIP}.sha256")"
test "${BEFORE_SHA}" = "${AFTER_SHA}"

echo "LobeHub client build kit test passed"
