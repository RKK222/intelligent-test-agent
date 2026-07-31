#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KIT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
VERSION_FILE="${KIT_ROOT}/version.env"
CONTRACT_FILE="${SCRIPT_DIR}/lobehub-client-artifact-contract.sh"
[[ -f "${CONTRACT_FILE}" ]] || CONTRACT_FILE="${KIT_ROOT}/lobehub-client-artifact-contract.sh"
CLIENT=""
BUILD_EVIDENCE=""
ACCEPTANCE_RECORD=""
APPROVER=""
OUTPUT_EVIDENCE=""
CONFIRM_EXECUTION_DISABLED=0
FORCE=0
EXTRACT_DIR=""

usage() {
  cat <<'USAGE'
Usage: scripts/approve-lobehub-linux-client.sh [options]

Validate a native Linux x86_64 candidate and bind a separately completed acceptance record to
final release evidence. Run this command as the named reviewer on the approved target OS/kernel.

Required:
  --client <path>                         Candidate lobehub-linux-x86_64.tar.gz.
  --build-evidence <path>                 Pending evidence from the native builder.
  --acceptance-record <path>              Completed KEY=value acceptance checklist.
  --approver <internal-id>                Reviewer identity; must match REVIEWER in the record.
  --confirm-device-execution-disabled     Explicit confirmation for the current release boundary.

Options:
  --version-file <path>                   Version lock (default: ../version.env).
  --output-evidence <path>                Final evidence output.
  --force                                 Replace only the exact final evidence file.
  -h, --help                              Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --client) CLIENT="$2"; shift 2 ;;
    --build-evidence) BUILD_EVIDENCE="$2"; shift 2 ;;
    --acceptance-record) ACCEPTANCE_RECORD="$2"; shift 2 ;;
    --approver) APPROVER="$2"; shift 2 ;;
    --version-file) VERSION_FILE="$2"; shift 2 ;;
    --output-evidence) OUTPUT_EVIDENCE="$2"; shift 2 ;;
    --confirm-device-execution-disabled) CONFIRM_EXECUTION_DISABLED=1; shift ;;
    --force) FORCE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ "$(uname -s)" == Linux ]] || { echo "Linux approval must run on Linux" >&2; exit 1; }
case "$(uname -m)" in
  x86_64|amd64) ;;
  *) echo "Linux approval must run natively on x86_64, got $(uname -m)" >&2; exit 1 ;;
esac
[[ "${CONFIRM_EXECUTION_DISABLED}" -eq 1 ]] || {
  echo "--confirm-device-execution-disabled is required" >&2
  exit 1
}
[[ "${APPROVER}" =~ ^[A-Za-z0-9][A-Za-z0-9@._+-]{2,127}$ ]] || {
  echo "Approver must be a stable internal identity without whitespace" >&2
  exit 1
}

for command_name in awk file find sha256sum tar; do
  command -v "${command_name}" >/dev/null 2>&1 || {
    echo "Required command not found: ${command_name}" >&2
    exit 1
  }
done
for required in "${CLIENT}" "${BUILD_EVIDENCE}" "${ACCEPTANCE_RECORD}" \
  "${VERSION_FILE}" "${CONTRACT_FILE}"; do
  [[ -f "${required}" && ! -L "${required}" && -s "${required}" ]] || {
    echo "Required non-empty regular file not found: ${required}" >&2
    exit 1
  }
done

# 验收脚本复用最终打包门禁的语法、摘要和占位值规则。
source "${CONTRACT_FILE}"
lobehub_client_validate_evidence_syntax "${BUILD_EVIDENCE}" 'Linux candidate build evidence'
lobehub_client_validate_evidence_syntax "${ACCEPTANCE_RECORD}" 'Linux client acceptance record'

state_value() {
  lobehub_client_require_value "$1" "$2" "$3"
}

INTERNAL_VERSION="$(state_value "${VERSION_FILE}" LOBEHUB_INTERNAL_VERSION 'version lock')"
FORK_COMMIT="$(state_value "${VERSION_FILE}" LOBEHUB_FORK_COMMIT 'version lock')"
[[ "$(state_value "${BUILD_EVIDENCE}" LINUX_BUILD_STATUS 'Linux candidate build evidence')" == Candidate ]] || {
  echo "Linux build evidence status must be Candidate" >&2
  exit 1
}
[[ "$(state_value "${BUILD_EVIDENCE}" LOBEHUB_INTERNAL_VERSION 'Linux candidate build evidence')" == "${INTERNAL_VERSION}" \
  && "$(state_value "${BUILD_EVIDENCE}" LOBEHUB_FORK_COMMIT 'Linux candidate build evidence')" == "${FORK_COMMIT}" ]] || {
  echo "Linux candidate does not match the locked release" >&2
  exit 1
}
[[ "$(state_value "${BUILD_EVIDENCE}" CLIENT_ARCHITECTURE 'Linux candidate build evidence')" == x86_64 \
  && "$(state_value "${BUILD_EVIDENCE}" CLIENT_EXECUTION_MODE 'Linux candidate build evidence')" == disabled ]] || {
  echo "Linux candidate architecture or execution mode is not release eligible" >&2
  exit 1
}
CLIENT_SHA="$(lobehub_client_sha256_file "${CLIENT}")"
[[ "${CLIENT_SHA}" == \
  "$(state_value "${BUILD_EVIDENCE}" LINUX_CLIENT_FILE_SHA256 'Linux candidate build evidence')" ]] || {
  echo "Linux candidate digest does not match its build evidence" >&2
  exit 1
}

[[ "$(state_value "${ACCEPTANCE_RECORD}" REVIEWER 'Linux client acceptance record')" == "${APPROVER}" ]] || {
  echo "Acceptance record reviewer must match --approver" >&2
  exit 1
}
for result in \
  VALIDATION_RESULT=Passed \
  CLIENT_LOGIN_RESULT=Passed \
  DEVICE_EXECUTION_RESULT=Disabled \
  PUBLIC_NETWORK_DEPENDENCY_RESULT=None \
  RUNTIME_DOWNLOAD_RESULT=Blocked \
  LOCAL_DATA_ISOLATION_RESULT=Passed; do
  key="${result%%=*}"
  expected="${result#*=}"
  [[ "$(state_value "${ACCEPTANCE_RECORD}" "${key}" 'Linux client acceptance record')" == "${expected}" ]] || {
    echo "Acceptance record requires ${result}" >&2
    exit 1
  }
done
CHANGE_ID="$(state_value "${ACCEPTANCE_RECORD}" APPROVAL_CHANGE_ID 'Linux client acceptance record')"
! lobehub_client_is_placeholder "${CHANGE_ID}" || {
  echo "Acceptance record change ID is still a placeholder" >&2
  exit 1
}

# 先拒绝归档路径穿越，再证明包内至少包含一个 x86-64 ELF 可执行文件。
if ! tar -tzf "${CLIENT}" | awk '
  /^\// { exit 1 }
  /(^|\/)\.\.($|\/)/ { exit 1 }
  END { if (NR == 0) exit 1 }
'; then
  echo "Linux client archive contains an unsafe path" >&2
  exit 1
fi
EXTRACT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/lobehub-linux-approval.XXXXXX")"
trap '[[ -n "${EXTRACT_DIR}" && -d "${EXTRACT_DIR}" ]] && rm -rf "${EXTRACT_DIR}"' EXIT
tar -xzf "${CLIENT}" -C "${EXTRACT_DIR}"
if ! find "${EXTRACT_DIR}" -type f -perm /111 -exec file {} + \
  | grep -E 'ELF 64-bit LSB.*(x86-64|x86_64)' >/dev/null; then
  echo "Linux client archive does not contain an x86-64 ELF executable" >&2
  exit 1
fi

OUTPUT_EVIDENCE="${OUTPUT_EVIDENCE:-$(dirname "${CLIENT}")/linux-client-verification.txt}"
OUTPUT_PARENT="$(dirname "${OUTPUT_EVIDENCE}")"
mkdir -p "${OUTPUT_PARENT}"
OUTPUT_PARENT="$(cd "${OUTPUT_PARENT}" && pwd)"
OUTPUT_EVIDENCE="${OUTPUT_PARENT}/$(basename "${OUTPUT_EVIDENCE}")"
[[ "${FORCE}" -eq 1 || ! -e "${OUTPUT_EVIDENCE}" ]] || {
  echo "Final evidence already exists; use --force for this exact file" >&2
  exit 1
}

OS_NAME="$(. /etc/os-release && printf '%s' "${PRETTY_NAME:-${ID:-Linux}}")"
OS_NAME="$(printf '%s' "${OS_NAME}" | tr '\r\n' '  ')"
ACCEPTANCE_SHA="$(lobehub_client_sha256_file "${ACCEPTANCE_RECORD}")"
EVIDENCE_TMP="$(mktemp "${OUTPUT_PARENT}/.linux-client-verification.XXXXXX")"
cat >"${EVIDENCE_TMP}" <<EOF
LINUX_APPROVAL_STATUS=Approved
LINUX_APPROVER=${APPROVER}
LINUX_CLIENT_FILE_SHA256=${CLIENT_SHA}
LOBEHUB_INTERNAL_VERSION=${INTERNAL_VERSION}
LOBEHUB_FORK_COMMIT=${FORK_COMMIT}
CLIENT_ARCHITECTURE=x86_64
CLIENT_EXECUTION_MODE=disabled
LINUX_VALIDATION_OS=${OS_NAME}
LINUX_VALIDATION_KERNEL=$(uname -r)
LINUX_ACCEPTANCE_RECORD_SHA256=${ACCEPTANCE_SHA}
LINUX_BUILD_EVIDENCE_SHA256=$(lobehub_client_sha256_file "${BUILD_EVIDENCE}")
EOF

lobehub_verify_linux_client_artifact \
  "${CLIENT}" "${EVIDENCE_TMP}" "${ACCEPTANCE_RECORD}" "${INTERNAL_VERSION}" "${FORK_COMMIT}"
chmod 0644 "${EVIDENCE_TMP}"
mv "${EVIDENCE_TMP}" "${OUTPUT_EVIDENCE}"

echo "Linux client approval evidence created at ${OUTPUT_EVIDENCE}"
echo "Keep the unchanged acceptance record beside the final evidence during artifact collection."
