#!/usr/bin/env bash
set -euo pipefail

ROOT=""
EXPECTED_VERSION=""
EXPECTED_MANIFEST_SHA256=""
EXPECTED_SIGNATURE_SHA256=""
EXPECTED_INSTALL_SHA256=""

usage() {
  cat <<'USAGE'
Usage: verify-local-opencode-client-distribution.sh [options]

Verify an installed or packaged local OpenCode client HTTP distribution without
jq or network access.

Options:
  --root <path>                       Distribution root.
  --expected-version <version>        Expected manifest version.
  --expected-manifest-sha256 <sha>    Expected stable/manifest.json SHA-256.
  --expected-signature-sha256 <sha>   Expected stable/manifest.json.sig SHA-256.
  --expected-install-sha256 <sha>     Expected install.sh SHA-256.
  -h, --help                          Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --root) ROOT="$2"; shift 2 ;;
    --expected-version) EXPECTED_VERSION="$2"; shift 2 ;;
    --expected-manifest-sha256) EXPECTED_MANIFEST_SHA256="$2"; shift 2 ;;
    --expected-signature-sha256) EXPECTED_SIGNATURE_SHA256="$2"; shift 2 ;;
    --expected-install-sha256) EXPECTED_INSTALL_SHA256="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

require_file() {
  [[ -f "$1" && ! -L "$1" ]] || {
    echo "Required local client file is missing or is a symlink: $1" >&2
    exit 1
  }
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    echo "Neither sha256sum nor shasum is available" >&2
    exit 1
  fi
}

require_sha256() {
  [[ "$1" =~ ^[0-9a-f]{64}$ ]] || {
    echo "Invalid expected SHA-256 for $2" >&2
    exit 1
  }
}

manifest_value() {
  local manifest="$1" key="$2" count value
  count="$(awk -F'"' -v wanted="${key}" '$2 == wanted { count++ } END { print count + 0 }' "${manifest}")"
  [[ "${count}" -eq 1 ]] || {
    echo "Local client manifest must contain exactly one ${key}" >&2
    exit 1
  }
  value="$(awk -F'"' -v wanted="${key}" '$2 == wanted { print $4; exit }' "${manifest}")"
  [[ -n "${value}" ]] || {
    echo "Local client manifest has an empty ${key}" >&2
    exit 1
  }
  printf '%s\n' "${value}"
}

validate_relative_path() {
  local path="$1" label="$2"
  case "${path}" in
    /*|*..*|*//*|*\\*)
      echo "Unsafe local client ${label} path: ${path}" >&2
      exit 1
      ;;
  esac
}

verify_manifest_artifact() {
  local path_key="$1" sha_key="$2" path expected actual
  path="$(manifest_value "${MANIFEST}" "${path_key}")"
  expected="$(manifest_value "${MANIFEST}" "${sha_key}")"
  validate_relative_path "${path}" "${path_key}"
  require_sha256 "${expected}" "${sha_key}"
  require_file "${ROOT}/${path}"
  actual="$(sha256_file "${ROOT}/${path}")"
  [[ "${actual}" == "${expected}" ]] || {
    echo "Local client artifact SHA-256 mismatch: ${path}" >&2
    exit 1
  }
}

[[ -n "${ROOT}" && -n "${EXPECTED_VERSION}" ]] || {
  echo "--root and --expected-version are required" >&2
  exit 2
}
require_sha256 "${EXPECTED_MANIFEST_SHA256}" manifest
require_sha256 "${EXPECTED_SIGNATURE_SHA256}" signature
require_sha256 "${EXPECTED_INSTALL_SHA256}" install.sh

MANIFEST="${ROOT}/stable/manifest.json"
SIGNATURE="${ROOT}/stable/manifest.json.sig"
INSTALL_SCRIPT="${ROOT}/install.sh"
require_file "${MANIFEST}"
require_file "${SIGNATURE}"
require_file "${INSTALL_SCRIPT}"

[[ "$(sha256_file "${MANIFEST}")" == "${EXPECTED_MANIFEST_SHA256}" ]] || {
  echo "Local client manifest SHA-256 mismatch" >&2
  exit 1
}
[[ "$(sha256_file "${SIGNATURE}")" == "${EXPECTED_SIGNATURE_SHA256}" ]] || {
  echo "Local client manifest signature SHA-256 mismatch" >&2
  exit 1
}
[[ "$(sha256_file "${INSTALL_SCRIPT}")" == "${EXPECTED_INSTALL_SHA256}" ]] || {
  echo "Local client install.sh SHA-256 mismatch" >&2
  exit 1
}
[[ "$(manifest_value "${MANIFEST}" version)" == "${EXPECTED_VERSION}" ]] || {
  echo "Local client version mismatch" >&2
  exit 1
}

# manifest 字节已由发布包固定；继续逐项核对，防止目标机只保留清单而安装器或版本制品已经漂移。
verify_manifest_artifact clientJarPath clientJarSha256
verify_manifest_artifact darwinArm64JrePath darwinArm64JreSha256
verify_manifest_artifact darwinArm64OpencodePath darwinArm64OpencodeSha256
verify_manifest_artifact linuxArm64GlibcJrePath linuxArm64GlibcJreSha256
verify_manifest_artifact linuxArm64GlibcOpencodePath linuxArm64GlibcOpencodeSha256
verify_manifest_artifact darwinArm64InstallerPath darwinArm64InstallerSha256
verify_manifest_artifact linuxArm64GlibcInstallerPath linuxArm64GlibcInstallerSha256

printf 'Local OpenCode client distribution verified: version=%s manifestSha256=%s\n' \
  "${EXPECTED_VERSION}" "${EXPECTED_MANIFEST_SHA256}"
