#!/usr/bin/env bash
set -euo pipefail

ARCHIVE=""
RUNTIME_ROOT=""

usage() {
  cat <<'USAGE'
Usage: verify-opencode-tool-runtime.sh (--archive <programs.tar.gz> | --root <opencode-runtime-dir>)

Verify the OpenCode runtime manifest, lockfile and direct dependencies used by custom Tools.
The manifest selects either the V2 @opencode/* ABI or the V1 rollback @opencode-ai/* ABI.
Exactly one source must be provided.
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
  # 先取 manifest 决定 V2/V1 ABI，再取对应插件和直接依赖，避免回滚包被 V2 清单误判。
  if ! tar -xzf "${ARCHIVE}" -C "${TEMP_ROOT}" \
    programs/opencode/package.json programs/opencode/package-lock.json programs/opencode/VERSION; then
    echo "OpenCode programs archive is missing runtime manifest: ${ARCHIVE}" >&2
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

if grep -Fq '"@opencode/plugin"' <<<"${runtime_manifest}"; then
  ABI="V2"
  DEPENDENCIES=(
    '@modelcontextprotocol/sdk|dist/esm/server/mcp.js'
    '@opencode/plugin|dist/promise/index.js'
    '@opencode/client|dist/promise/index.js'
    'effect|dist/index.js'
    'jsonc-parser|lib/esm/main.js'
    'playwright-core|index.js'
    'zod|index.js'
  )
  PLUGIN_FILES=(
    opencode-observability-plugin.mjs
    opencode-observability-plugin/index.mjs
    opencode-rtk-plugin/index.mjs
  )
else
  ABI="V1"
  DEPENDENCIES=(
    '@modelcontextprotocol/sdk|dist/esm/server/mcp.js'
    '@opencode-ai/plugin|dist/index.js'
    '@opencode-ai/sdk|dist/index.js'
    'effect|dist/index.js'
    'jsonc-parser|lib/esm/main.js'
    'playwright-core|index.js'
    'zod|index.js'
  )
  PLUGIN_FILES=(
    opencode-observability-plugin-v1.mjs
    opencode-rtk-plugin-v1.mjs
  )
fi

if [[ -n "${ARCHIVE}" ]]; then
  archive_members=()
  for plugin_file in "${PLUGIN_FILES[@]}"; do
    archive_members+=("programs/opencode/${plugin_file}")
  done
  for dependency_entry in "${DEPENDENCIES[@]}"; do
    dependency="${dependency_entry%%|*}"
    dependency_entrypoint="${dependency_entry#*|}"
    archive_members+=(
      "programs/opencode/node_modules/${dependency}/package.json"
      "programs/opencode/node_modules/${dependency}/${dependency_entrypoint}"
    )
  done
  if ! tar -xzf "${ARCHIVE}" -C "${TEMP_ROOT}" "${archive_members[@]}"; then
    echo "OpenCode programs archive is missing required Tool runtime dependencies (${ABI}): ${ARCHIVE}" >&2
    exit 1
  fi
fi

runtime_dependency_version() {
  awk -F'"' -v wanted="$1" '$2 == wanted { print $4; exit }' "$(runtime_path package.json)"
}

for plugin_file in "${PLUGIN_FILES[@]}"; do
  require_runtime_file "${plugin_file}"
done

for dependency_entry in "${DEPENDENCIES[@]}"; do
  dependency="${dependency_entry%%|*}"
  dependency_entrypoint="${dependency_entry#*|}"
  expected_version="$(runtime_dependency_version "${dependency}")"
  [[ -n "${expected_version}" ]] || {
    echo "OpenCode ${ABI} runtime manifest does not declare ${dependency}" >&2
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

printf 'OpenCode %s Tool runtime dependencies verified: %s; plugin/sdk/effect/playwright/zod are present\n' \
  "${ABI}" "${SOURCE_LABEL}"
