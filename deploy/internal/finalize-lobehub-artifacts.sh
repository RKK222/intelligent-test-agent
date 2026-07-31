#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
VERSION_FILE="${SCRIPT_DIR}/lobehub/version.env"
CLIENT_CONTRACT_FILE="${SCRIPT_DIR}/lobehub-client-artifact-contract.sh"

SERVER_ARTIFACT_DIR=""
OUTPUT_DIR=""
WINDOWS_CLIENT=""
WINDOWS_SIGNATURE_EVIDENCE=""
LINUX_CLIENT=""
LINUX_APPROVAL_EVIDENCE=""
LINUX_ACCEPTANCE_RECORD=""
FORCE=0
STAGING_DIR=""

usage() {
  cat <<'USAGE'
Usage: deploy/internal/finalize-lobehub-artifacts.sh [options]

Complete an already verified LobeHub server-only staging set with externally built clients.
This operation performs no Docker or network access and never mutates the server-only source.

Required options:
  --server-artifact-dir <path>         Existing server-only artifact directory.
  --output-dir <path>                  New complete artifact directory.
  --windows-client <path>              Enterprise-signed Windows x64 EXE.
  --windows-signature-evidence <path>  KEY=value Authenticode verification evidence.
  --linux-client <path>                Approved Linux x86_64 client tar.gz.
  --linux-approval-evidence <path>     KEY=value Linux approval evidence.
  --linux-acceptance-record <path>     Independent Linux acceptance record.

Other options:
  --force                              Replace only the exact output directory after validation.
  -h, --help                           Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --server-artifact-dir) SERVER_ARTIFACT_DIR="$2"; shift 2 ;;
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    --windows-client) WINDOWS_CLIENT="$2"; shift 2 ;;
    --windows-signature-evidence) WINDOWS_SIGNATURE_EVIDENCE="$2"; shift 2 ;;
    --linux-client) LINUX_CLIENT="$2"; shift 2 ;;
    --linux-approval-evidence) LINUX_APPROVAL_EVIDENCE="$2"; shift 2 ;;
    --linux-acceptance-record) LINUX_ACCEPTANCE_RECORD="$2"; shift 2 ;;
    --force) FORCE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

fail() {
  echo "LobeHub artifact finalization failed: $1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

require_regular_file() {
  local path="$1" description="$2"
  [[ -f "${path}" && ! -L "${path}" && -s "${path}" ]] ||
    fail "${description} must be a non-empty regular file: ${path}"
}

canonicalize_input_file() {
  local variable_name="$1" description="$2" path parent
  path="${!variable_name}"
  require_regular_file "${path}" "${description}"
  parent="$(cd "$(dirname "${path}")" && pwd -P)"
  printf -v "${variable_name}" '%s/%s' "${parent}" "$(basename "${path}")"
}

validate_state_file() {
  local path="$1" description="$2"
  require_regular_file "${path}" "${description}"
  awk -v description="${description}" '
    {
      sub(/\r$/, "", $0)
      if ($0 !~ /^[A-Z][A-Z0-9_]*=/) {
        printf "%s contains an invalid line\n", description > "/dev/stderr"
        exit 1
      }
      key = substr($0, 1, index($0, "=") - 1)
      if (seen[key]++) {
        printf "%s contains duplicate key %s\n", description, key > "/dev/stderr"
        exit 1
      }
    }
    END { if (NR == 0) exit 1 }
  ' "${path}" || fail "invalid state file: ${path}"
}

state_value() {
  local path="$1" key="$2" description="$3"
  awk -F= -v wanted="${key}" '
    $1 == wanted { print substr($0, index($0, "=") + 1); found++ }
    END { if (found != 1) exit 1 }
  ' "${path}" || fail "${description} must contain ${key} exactly once"
}

optional_state_value() {
  local path="$1" key="$2"
  awk -F= -v wanted="${key}" '
    $1 == wanted { print substr($0, index($0, "=") + 1); found++ }
    END { if (found > 1) exit 1 }
  ' "${path}"
}

require_immutable_tag() {
  local key="$1" value="$2" leaf tag
  leaf="${value##*/}"
  tag="${leaf#*:}"
  [[ "${value}" =~ ^[A-Za-z0-9._:/-]+$ && "${value}" != *@* \
    && "${leaf}" == *:* && -n "${tag}" && "${tag}" != latest ]] ||
    fail "${key} must use an immutable non-latest tag"
}

verify_checksum_manifest() {
  local directory="$1" line path listed_paths expected_paths
  local checksum_line_pattern='^([0-9a-f]{64})[[:space:]]+\*?([^[:space:]]+)$'
  listed_paths=""
  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ "${line}" =~ ${checksum_line_pattern} ]] || fail 'malformed line in SHA256SUMS'
    path="${BASH_REMATCH[2]}"
    [[ -n "${path}" && "${path}" != /* && "${path}" != ../* && "${path}" != */../* \
      && "${path}" != SHA256SUMS && -f "${directory}/${path}" \
      && ! -L "${directory}/${path}" ]] || fail "unsafe or missing SHA256SUMS path: ${path}"
    listed_paths+="${path}"$'\n'
  done <"${directory}/SHA256SUMS"
  listed_paths="$(printf '%s' "${listed_paths}" | LC_ALL=C sort)"
  [[ -z "$(printf '%s\n' "${listed_paths}" | uniq -d)" ]] ||
    fail 'SHA256SUMS contains duplicate paths'
  expected_paths="$(cd "${directory}" && find . -type f ! -name SHA256SUMS -print \
    | sed 's#^\./##' | LC_ALL=C sort)"
  [[ "${listed_paths}" == "${expected_paths}" ]] ||
    fail 'SHA256SUMS must cover every artifact exactly once'
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "${directory}" && sha256sum -c SHA256SUMS)
  elif command -v shasum >/dev/null 2>&1; then
    (cd "${directory}" && shasum -a 256 -c SHA256SUMS)
  else
    fail 'neither sha256sum nor shasum is available'
  fi
}

write_checksum_manifest() {
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

verify_locked_value() {
  local release_key="$1" expected="$2" actual
  actual="$(state_value "${SERVER_ARTIFACT_DIR}/release.env" "${release_key}" 'server release manifest')"
  [[ "${actual}" == "${expected}" ]] ||
    fail "server release ${release_key} does not match the version lock"
}

for value in SERVER_ARTIFACT_DIR OUTPUT_DIR WINDOWS_CLIENT WINDOWS_SIGNATURE_EVIDENCE \
  LINUX_CLIENT LINUX_APPROVAL_EVIDENCE LINUX_ACCEPTANCE_RECORD; do
  [[ -n "${!value}" ]] || fail "missing required option for ${value}"
done
require_command awk
require_command find
require_command cp
require_command mktemp
require_regular_file "${VERSION_FILE}" 'LobeHub version lock'
require_regular_file "${CLIENT_CONTRACT_FILE}" 'LobeHub client artifact contract'
validate_state_file "${VERSION_FILE}" 'LobeHub version lock'
canonicalize_input_file WINDOWS_CLIENT 'Windows x64 client'
canonicalize_input_file WINDOWS_SIGNATURE_EVIDENCE 'Windows Authenticode evidence'
canonicalize_input_file LINUX_CLIENT 'Linux x86_64 client'
canonicalize_input_file LINUX_APPROVAL_EVIDENCE 'Linux approval evidence'
canonicalize_input_file LINUX_ACCEPTANCE_RECORD 'Linux acceptance record'

[[ -d "${SERVER_ARTIFACT_DIR}" && ! -L "${SERVER_ARTIFACT_DIR}" ]] ||
  fail "server artifact source must be a real directory: ${SERVER_ARTIFACT_DIR}"
SERVER_ARTIFACT_DIR="$(cd "${SERVER_ARTIFACT_DIR}" && pwd -P)"
if [[ -n "$(find "${SERVER_ARTIFACT_DIR}" -type l -print -quit)" ]]; then
  fail 'server artifact source must not contain symbolic links'
fi
if [[ -n "$(find "${SERVER_ARTIFACT_DIR}" ! -type d ! -type f -print -quit)" ]]; then
  fail 'server artifact source may contain only directories and regular files'
fi
if [[ -n "$(find "${SERVER_ARTIFACT_DIR}" -type f -name '*[[:space:]]*' -print -quit)" ]]; then
  fail 'server artifact filenames must not contain whitespace'
fi

OUTPUT_BASENAME="$(basename "${OUTPUT_DIR}")"
[[ "${OUTPUT_BASENAME}" != . && "${OUTPUT_BASENAME}" != .. && -n "${OUTPUT_BASENAME}" ]] ||
  fail "unsafe output directory: ${OUTPUT_DIR}"
OUTPUT_PARENT="$(dirname "${OUTPUT_DIR}")"
mkdir -p "${OUTPUT_PARENT}"
OUTPUT_PARENT="$(cd "${OUTPUT_PARENT}" && pwd -P)"
OUTPUT_DIR="${OUTPUT_PARENT}/${OUTPUT_BASENAME}"
case "${OUTPUT_DIR}" in
  /|"${HOME}"|"${ROOT_DIR}"|"${SCRIPT_DIR}") fail "unsafe output directory: ${OUTPUT_DIR}" ;;
esac
case "${OUTPUT_DIR}" in
  "${SERVER_ARTIFACT_DIR}"|"${SERVER_ARTIFACT_DIR}"/*)
    fail 'output directory must not be the server source or one of its children' ;;
esac
case "${SERVER_ARTIFACT_DIR}" in
  "${OUTPUT_DIR}"/*) fail 'output directory must not contain the server source' ;;
esac
for protected_input in \
  "${WINDOWS_CLIENT}" "${WINDOWS_SIGNATURE_EVIDENCE}" \
  "${LINUX_CLIENT}" "${LINUX_APPROVAL_EVIDENCE}" "${LINUX_ACCEPTANCE_RECORD}" \
  "${VERSION_FILE}" "${CLIENT_CONTRACT_FILE}" "${BASH_SOURCE[0]}"; do
  case "${protected_input}" in
    "${OUTPUT_DIR}"|"${OUTPUT_DIR}"/*)
      fail "output directory must not equal or contain protected input: ${protected_input}" ;;
  esac
done
case "${ROOT_DIR}" in
  "${OUTPUT_DIR}"/*) fail 'output directory must not contain the platform repository' ;;
esac
case "${SCRIPT_DIR}" in
  "${OUTPUT_DIR}"/*) fail 'output directory must not contain the deployment scripts' ;;
esac
[[ ! -L "${OUTPUT_DIR}" ]] || fail 'output directory must not be a symbolic link'
if [[ -e "${OUTPUT_DIR}" && "${FORCE}" -ne 1 ]]; then
  fail "output already exists; use --force for this exact directory: ${OUTPUT_DIR}"
fi

LOCKED_INTERNAL_VERSION="$(state_value "${VERSION_FILE}" LOBEHUB_INTERNAL_VERSION 'version lock')"
LOCKED_UPSTREAM_VERSION="$(state_value "${VERSION_FILE}" LOBEHUB_UPSTREAM_VERSION 'version lock')"
LOCKED_UPSTREAM_COMMIT="$(state_value "${VERSION_FILE}" LOBEHUB_UPSTREAM_COMMIT 'version lock')"
LOCKED_FORK_COMMIT="$(state_value "${VERSION_FILE}" LOBEHUB_FORK_COMMIT 'version lock')"
LOCKED_CONTRACT_VERSION="$(state_value "${VERSION_FILE}" LOBEHUB_PLATFORM_CONTRACT_VERSION 'version lock')"
LOCKED_POSTGRES_MAJOR="$(state_value "${VERSION_FILE}" LOBEHUB_PARADEDB_POSTGRES_MAJOR 'version lock')"
[[ "${LOCKED_INTERNAL_VERSION}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+-platform\.[0-9]+$ \
  && "${LOCKED_UPSTREAM_VERSION}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ \
  && "${LOCKED_UPSTREAM_COMMIT}" =~ ^[0-9a-f]{7}([0-9a-f]{33})?$ \
  && "${LOCKED_FORK_COMMIT}" =~ ^[0-9a-f]{40}$ \
  && "${LOCKED_CONTRACT_VERSION}" =~ ^[0-9]+$ \
  && "${LOCKED_POSTGRES_MAJOR}" =~ ^[0-9]+$ ]] || fail 'malformed LobeHub version lock'

for required_file in \
  release.env SHA256SUMS approved-resources.json LICENSES.txt \
  images/lobehub-image.tar images/paradedb-image.tar images/rustfs-image.tar \
  bin/mc-linux-amd64 sbom/lobehub.spdx.json \
  "source/lobehub-${LOCKED_INTERNAL_VERSION}.tar.gz"; do
  require_regular_file "${SERVER_ARTIFACT_DIR}/${required_file}" "server artifact ${required_file}"
done
for forbidden_file in \
  clients/lobehub-windows-x64.exe clients/lobehub-linux-x86_64.tar.gz \
  windows-authenticode-verification.txt linux-client-verification.txt \
  linux-client-acceptance-record.txt; do
  [[ ! -e "${SERVER_ARTIFACT_DIR}/${forbidden_file}" ]] ||
    fail "server-only source already contains client artifact ${forbidden_file}"
done
if [[ -d "${SERVER_ARTIFACT_DIR}/clients" \
  && -n "$(find "${SERVER_ARTIFACT_DIR}/clients" -type f -print -quit)" ]]; then
  fail 'server-only source contains unexpected client files'
fi

validate_state_file "${SERVER_ARTIFACT_DIR}/release.env" 'server release manifest'
verify_checksum_manifest "${SERVER_ARTIFACT_DIR}"
verify_locked_value LOBEHUB_INTERNAL_VERSION "${LOCKED_INTERNAL_VERSION}"
verify_locked_value LOBEHUB_UPSTREAM_VERSION "${LOCKED_UPSTREAM_VERSION}"
RELEASE_UPSTREAM_COMMIT="$(state_value "${SERVER_ARTIFACT_DIR}/release.env" \
  LOBEHUB_UPSTREAM_COMMIT 'server release manifest')"
if [[ "${#LOCKED_UPSTREAM_COMMIT}" -eq 7 ]]; then
  [[ "${RELEASE_UPSTREAM_COMMIT}" == "${LOCKED_UPSTREAM_COMMIT}" \
    || "${RELEASE_UPSTREAM_COMMIT}" =~ ^${LOCKED_UPSTREAM_COMMIT}[0-9a-f]{33}$ ]] ||
    fail 'server release upstream commit does not match the version lock'
else
  [[ "${RELEASE_UPSTREAM_COMMIT}" == "${LOCKED_UPSTREAM_COMMIT}" ]] ||
    fail 'server release upstream commit does not match the version lock'
fi
verify_locked_value LOBEHUB_FORK_COMMIT "${LOCKED_FORK_COMMIT}"
verify_locked_value LOBEHUB_PLATFORM_CONTRACT_VERSION "${LOCKED_CONTRACT_VERSION}"
verify_locked_value LOBEHUB_PARADEDB_POSTGRES_MAJOR "${LOCKED_POSTGRES_MAJOR}"
[[ "$(state_value "${SERVER_ARTIFACT_DIR}/release.env" \
  LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED 'server release manifest')" == false ]] ||
  fail 'server-only source must record Windows Authenticode as false'
LINUX_APPROVED="$(optional_state_value "${SERVER_ARTIFACT_DIR}/release.env" \
  LOBEHUB_LINUX_CLIENT_APPROVED)" || fail 'invalid Linux approval state in server release manifest'
[[ -z "${LINUX_APPROVED}" || "${LINUX_APPROVED}" == false ]] ||
  fail 'server-only source must not claim Linux client approval'
[[ "$(state_value "${SERVER_ARTIFACT_DIR}/release.env" \
  LOBEHUB_LINUX_EXECUTION_DEFAULT 'server release manifest')" == false ]] ||
  fail 'Linux local execution must remain disabled by default'
for image_key in LOBEHUB_APP_IMAGE LOBEHUB_PARADEDB_IMAGE LOBEHUB_RUSTFS_IMAGE; do
  IMAGE_REF="$(state_value "${SERVER_ARTIFACT_DIR}/release.env" "${image_key}" \
    'server release manifest')"
  IMAGE_ID="$(state_value "${SERVER_ARTIFACT_DIR}/release.env" "${image_key}_ID" \
    'server release manifest')"
  require_immutable_tag "${image_key}" "${IMAGE_REF}"
  [[ "${IMAGE_ID}" =~ ^sha256:[0-9a-f]{64}$ ]] || fail "${image_key}_ID is malformed"
done

# 外部构建客户端必须先通过与最终打包、现场安装完全相同的真实性门禁。
source "${CLIENT_CONTRACT_FILE}"
lobehub_verify_client_artifacts \
  "${WINDOWS_CLIENT}" "${WINDOWS_SIGNATURE_EVIDENCE}" \
  "${LINUX_CLIENT}" "${LINUX_APPROVAL_EVIDENCE}" \
  "${LINUX_ACCEPTANCE_RECORD}" "${LOCKED_INTERNAL_VERSION}" "${LOCKED_FORK_COMMIT}"

STAGING_DIR="$(mktemp -d "${OUTPUT_PARENT}/.lobehub-finalize.XXXXXX")"
cleanup() {
  [[ -n "${STAGING_DIR}" && -d "${STAGING_DIR}" ]] && rm -rf "${STAGING_DIR}"
}
trap cleanup EXIT
cp -a "${SERVER_ARTIFACT_DIR}/." "${STAGING_DIR}/"
mkdir -p "${STAGING_DIR}/clients"
cp "${WINDOWS_CLIENT}" "${STAGING_DIR}/clients/lobehub-windows-x64.exe"
cp "${LINUX_CLIENT}" "${STAGING_DIR}/clients/lobehub-linux-x86_64.tar.gz"
cp "${WINDOWS_SIGNATURE_EVIDENCE}" "${STAGING_DIR}/windows-authenticode-verification.txt"
cp "${LINUX_APPROVAL_EVIDENCE}" "${STAGING_DIR}/linux-client-verification.txt"
cp "${LINUX_ACCEPTANCE_RECORD}" "${STAGING_DIR}/linux-client-acceptance-record.txt"

REWRITTEN_RELEASE="${STAGING_DIR}/release.env.new"
awk -v has_linux_key="$([[ -n "${LINUX_APPROVED}" ]] && printf 1 || printf 0)" '
  /^LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=/ {
    print "LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=true"
    if (has_linux_key == 0) print "LOBEHUB_LINUX_CLIENT_APPROVED=true"
    next
  }
  /^LOBEHUB_LINUX_CLIENT_APPROVED=/ {
    print "LOBEHUB_LINUX_CLIENT_APPROVED=true"
    next
  }
  { print }
' "${STAGING_DIR}/release.env" >"${REWRITTEN_RELEASE}"
mv "${REWRITTEN_RELEASE}" "${STAGING_DIR}/release.env"
validate_state_file "${STAGING_DIR}/release.env" 'complete release manifest'
[[ "$(state_value "${STAGING_DIR}/release.env" LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED \
  'complete release manifest')" == true ]] || fail 'failed to record Windows verification'
[[ "$(state_value "${STAGING_DIR}/release.env" LOBEHUB_LINUX_CLIENT_APPROVED \
  'complete release manifest')" == true ]] || fail 'failed to record Linux approval'

rm "${STAGING_DIR}/SHA256SUMS"
write_checksum_manifest "${STAGING_DIR}"
verify_checksum_manifest "${STAGING_DIR}"
lobehub_verify_client_artifacts \
  "${STAGING_DIR}/clients/lobehub-windows-x64.exe" \
  "${STAGING_DIR}/windows-authenticode-verification.txt" \
  "${STAGING_DIR}/clients/lobehub-linux-x86_64.tar.gz" \
  "${STAGING_DIR}/linux-client-verification.txt" \
  "${STAGING_DIR}/linux-client-acceptance-record.txt" \
  "${LOCKED_INTERNAL_VERSION}" "${LOCKED_FORK_COMMIT}"

if [[ -e "${OUTPUT_DIR}" ]]; then
  rm -rf "${OUTPUT_DIR}"
fi
mv "${STAGING_DIR}" "${OUTPUT_DIR}"
STAGING_DIR=""
trap - EXIT

echo "Complete LobeHub artifact set finalized at ${OUTPUT_DIR}."
echo "Next: TEST_AGENT_LOBEHUB_ARTIFACT_DIR=${OUTPUT_DIR} deploy/internal/package-release.sh --lobehub-only"
