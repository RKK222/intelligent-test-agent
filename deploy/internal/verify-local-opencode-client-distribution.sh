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

Verify an installed or packaged Kylin ARM64 local OpenCode client HTTP
distribution without jq or network access.

Options:
  --root <path>                       Distribution root.
  --expected-version <version>        Expected 14-digit release version.
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

file_size() {
  if stat -f %z "$1" >/dev/null 2>&1; then
    stat -f %z "$1"
  else
    stat -c %s "$1"
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
    ""|/*|*..*|*//*|*\\*)
      echo "Unsafe local client ${label} path: ${path}" >&2
      exit 1
      ;;
  esac
}

artifact_field() {
  local line="$1" key="$2"
  printf '%s\n' "${line}" | sed -n "s/.*\"${key}\": \"\([^\"]*\)\".*/\1/p"
}

artifact_size() {
  printf '%s\n' "$1" | sed -n 's/.*"size": \([0-9][0-9]*\).*/\1/p'
}

verify_artifact() {
  local kind="$1" count line path signature_path expected_sha expected_size actual_sha actual_size
  count="$(grep -F -c "\"kind\": \"${kind}\"" "${MANIFEST}" || true)"
  [[ "${count}" -eq 1 ]] || {
    echo "Local client manifest must contain exactly one ${kind} artifact" >&2
    exit 1
  }
  line="$(grep -F "\"kind\": \"${kind}\"" "${MANIFEST}")"
  path="$(artifact_field "${line}" path)"
  signature_path="$(artifact_field "${line}" signaturePath)"
  expected_sha="$(artifact_field "${line}" sha256)"
  expected_size="$(artifact_size "${line}")"
  validate_relative_path "${path}" "${kind} artifact"
  validate_relative_path "${signature_path}" "${kind} signature"
  require_sha256 "${expected_sha}" "${kind} artifact"
  [[ "${expected_size}" =~ ^[0-9]+$ ]] || {
    echo "Invalid expected size for ${kind} artifact" >&2
    exit 1
  }
  require_file "${ROOT}/${path}"
  require_file "${ROOT}/${signature_path}"
  actual_sha="$(sha256_file "${ROOT}/${path}")"
  actual_size="$(file_size "${ROOT}/${path}")"
  [[ "${actual_sha}" == "${expected_sha}" && "${actual_size}" == "${expected_size}" ]] || {
    echo "Local client artifact integrity mismatch: ${path}" >&2
    exit 1
  }
}

[[ -n "${ROOT}" && -n "${EXPECTED_VERSION}" ]] || {
  echo "--root and --expected-version are required" >&2
  exit 2
}
[[ "${EXPECTED_VERSION}" =~ ^[0-9]{14}$ ]] || {
  echo "Expected local client version must be a 14-digit release version" >&2
  exit 2
}
require_sha256 "${EXPECTED_MANIFEST_SHA256}" manifest
require_sha256 "${EXPECTED_SIGNATURE_SHA256}" signature
require_sha256 "${EXPECTED_INSTALL_SHA256}" install.sh

MANIFEST="${ROOT}/stable/manifest.json"
SIGNATURE="${ROOT}/stable/manifest.json.sig"
INSTALL_SCRIPT="${ROOT}/install.sh"
CATALOG="${ROOT}/catalog.json"
CATALOG_SIGNATURE="${ROOT}/catalog.json.sig"
RELEASE_MANIFEST="${ROOT}/releases/${EXPECTED_VERSION}/manifest.json"
RELEASE_SIGNATURE="${ROOT}/releases/${EXPECTED_VERSION}/manifest.json.sig"
VERSIONED_DEB="${ROOT}/test-agent-local-client_${EXPECTED_VERSION}_arm64.deb"
DOWNLOAD_ALIAS="${ROOT}/TestAgent-Local-Client-Kylin-arm64.deb"
for required in "${MANIFEST}" "${SIGNATURE}" "${INSTALL_SCRIPT}" "${CATALOG}" \
  "${CATALOG_SIGNATURE}" "${RELEASE_MANIFEST}" "${RELEASE_SIGNATURE}" \
  "${VERSIONED_DEB}" "${DOWNLOAD_ALIAS}"; do
  require_file "${required}"
done

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
cmp "${MANIFEST}" "${RELEASE_MANIFEST}" >/dev/null || {
  echo "Stable and versioned local client manifests differ" >&2
  exit 1
}
cmp "${SIGNATURE}" "${RELEASE_SIGNATURE}" >/dev/null || {
  echo "Stable and versioned local client manifest signatures differ" >&2
  exit 1
}
cmp "${VERSIONED_DEB}" "${DOWNLOAD_ALIAS}" >/dev/null || {
  echo "Local client download alias does not match the active versioned DEB" >&2
  exit 1
}

schema_count="$(grep -Ec '^[[:space:]]*"schemaVersion":[[:space:]]*2,' "${MANIFEST}" || true)"
[[ "${schema_count}" -eq 1 \
  && "$(manifest_value "${MANIFEST}" version)" == "${EXPECTED_VERSION}" \
  && "$(manifest_value "${MANIFEST}" platform)" == "linux" \
  && "$(manifest_value "${MANIFEST}" architecture)" == "arm64" ]] || {
  echo "Local client manifest platform, schema or version mismatch" >&2
  exit 1
}

artifact_count="$(grep -F -c '"kind":' "${MANIFEST}" || true)"
[[ "${artifact_count}" -eq 4 ]] || {
  echo "Local client manifest must contain exactly four runtime artifacts" >&2
  exit 1
}
verify_artifact CLIENT_JAR
verify_artifact JDK
verify_artifact OPENCODE
# 公共 Agent/Skill/Tool 能力是离线客户端的受签名运行制品，不能只检查 manifest 中是否声明。
verify_artifact PUBLIC_CAPABILITIES

catalog_line="$(grep -F "\"version\": \"${EXPECTED_VERSION}\"" "${CATALOG}" || true)"
[[ -n "${catalog_line}" ]] || {
  echo "Local client catalog does not contain the expected release" >&2
  exit 1
}
catalog_manifest_path="$(artifact_field "${catalog_line}" manifestPath)"
catalog_manifest_sha="$(artifact_field "${catalog_line}" manifestSha256)"
catalog_signature_path="$(artifact_field "${catalog_line}" manifestSignaturePath)"
[[ "${catalog_manifest_path}" == "releases/${EXPECTED_VERSION}/manifest.json" \
  && "${catalog_signature_path}" == "releases/${EXPECTED_VERSION}/manifest.json.sig" \
  && "${catalog_manifest_sha}" == "${EXPECTED_MANIFEST_SHA256}" ]] || {
  echo "Local client catalog entry does not match the active release" >&2
  exit 1
}

printf 'Local OpenCode client distribution verified: version=%s manifestSha256=%s\n' \
  "${EXPECTED_VERSION}" "${EXPECTED_MANIFEST_SHA256}"
