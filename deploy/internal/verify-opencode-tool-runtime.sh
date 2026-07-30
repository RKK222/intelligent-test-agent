#!/usr/bin/env bash
set -euo pipefail

ARCHIVE=""
RUNTIME_ROOT=""
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PINNED_RUNTIME_MANIFEST="${SCRIPT_DIR}/opencode-node-runtime.package.json"

usage() {
  cat <<'USAGE'
Usage: verify-opencode-tool-runtime.sh (--archive <programs.tar.gz> | --root <opencode-runtime-dir>)

Verify the pinned OpenCode runtime manifest, lockfile and direct dependencies used
by custom Tools. Exactly one source must be provided.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --archive)
      ARCHIVE="$2"
      shift 2
      ;;
    --root)
      RUNTIME_ROOT="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -n "${ARCHIVE}" && -n "${RUNTIME_ROOT}" ]] || [[ -z "${ARCHIVE}" && -z "${RUNTIME_ROOT}" ]]; then
  echo "Exactly one of --archive and --root is required" >&2
  exit 2
fi

# 依赖名覆盖受控 runtime manifest 的全部直接依赖；版本继续以既有 package.json 为单一来源，
# 避免部署脚本再维护一份版本号。Tool 基线四件套不能只在镜像构建时验证。
DEPENDENCIES=(
  '@modelcontextprotocol/sdk|dist/esm/server/mcp.js'
  '@opencode-ai/plugin|dist/index.js'
  '@opencode-ai/sdk|dist/index.js'
  'effect|dist/index.js'
  'jsonc-parser|lib/esm/main.js'
  'zod|index.js'
)

[[ -s "${PINNED_RUNTIME_MANIFEST}" ]] || {
  echo "Pinned OpenCode runtime manifest not found: ${PINNED_RUNTIME_MANIFEST}" >&2
  exit 1
}

pinned_dependency_version() {
  awk -F'"' -v wanted="$1" '$2 == wanted { print $4; exit }' "${PINNED_RUNTIME_MANIFEST}"
}

TEMP_ROOT=""
cleanup() {
  [[ -z "${TEMP_ROOT}" ]] || rm -rf "${TEMP_ROOT}"
}
trap cleanup EXIT

if [[ -n "${ARCHIVE}" ]]; then
  [[ -f "${ARCHIVE}" ]] || {
    echo "OpenCode programs archive not found: ${ARCHIVE}" >&2
    exit 1
  }
  SOURCE_LABEL="archive=${ARCHIVE}"
  TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-opencode-runtime-verify.XXXXXX")"
  archive_members=(
    programs/opencode/package.json
    programs/opencode/package-lock.json
  )
  for dependency_entry in "${DEPENDENCIES[@]}"; do
    dependency="${dependency_entry%%|*}"
    dependency_entrypoint="${dependency_entry#*|}"
    archive_members+=(
      "programs/opencode/node_modules/${dependency}/package.json"
      "programs/opencode/node_modules/${dependency}/${dependency_entrypoint}"
    )
  done
  # programs 归档较大，只在一次 tar 流中提取需要核对的少量 manifest，避免逐包重复解压。
  if ! tar -xzf "${ARCHIVE}" -C "${TEMP_ROOT}" "${archive_members[@]}"; then
    echo "OpenCode programs archive is missing required Tool runtime dependencies: ${ARCHIVE}" >&2
    exit 1
  fi
  RUNTIME_ROOT="${TEMP_ROOT}/programs/opencode"
else
  [[ -d "${RUNTIME_ROOT}" ]] || {
    echo "OpenCode runtime directory not found: ${RUNTIME_ROOT}" >&2
    exit 1
  }
  SOURCE_LABEL="root=${RUNTIME_ROOT}"
fi

runtime_path() {
  printf '%s/%s' "${RUNTIME_ROOT%/}" "$1"
}

read_runtime_file() {
  command cat "$(runtime_path "$1")"
}

require_runtime_file() {
  local absolute_path
  absolute_path="$(runtime_path "$1")"
  if [[ ! -f "${absolute_path}" || -L "${absolute_path}" || ! -s "${absolute_path}" ]]; then
    echo "Required OpenCode runtime dependency file must be a non-empty regular file: ${absolute_path}" >&2
    exit 1
  fi
}

require_runtime_file package.json
require_runtime_file package-lock.json
runtime_manifest="$(read_runtime_file package.json)"
runtime_lock="$(read_runtime_file package-lock.json)"

for dependency_entry in "${DEPENDENCIES[@]}"; do
  dependency="${dependency_entry%%|*}"
  dependency_entrypoint="${dependency_entry#*|}"
  expected_version="$(pinned_dependency_version "${dependency}")"
  [[ -n "${expected_version}" ]] || {
    echo "Pinned OpenCode runtime manifest does not declare ${dependency}" >&2
    exit 1
  }
  package_file="node_modules/${dependency}/package.json"
  require_runtime_file "${package_file}"
  require_runtime_file "node_modules/${dependency}/${dependency_entrypoint}"
  package_manifest="$(read_runtime_file "${package_file}")"

  grep -Fq "\"${dependency}\": \"${expected_version}\"" <<<"${runtime_manifest}" || {
    echo "OpenCode runtime manifest does not pin ${dependency}=${expected_version}" >&2
    exit 1
  }
  grep -Fq "\"node_modules/${dependency}\": {" <<<"${runtime_lock}" || {
    echo "OpenCode runtime lockfile does not contain ${dependency}" >&2
    exit 1
  }
  grep -Fq "\"name\": \"${dependency}\"" <<<"${package_manifest}" || {
    echo "Installed OpenCode dependency has the wrong package name: ${dependency}" >&2
    exit 1
  }
  grep -Fq "\"version\": \"${expected_version}\"" <<<"${package_manifest}" || {
    echo "Installed OpenCode dependency has the wrong version: ${dependency}, expected ${expected_version}" >&2
    exit 1
  }
done

printf 'OpenCode Tool runtime dependencies verified: %s; plugin/sdk/effect/zod are present\n' \
  "${SOURCE_LABEL}"
