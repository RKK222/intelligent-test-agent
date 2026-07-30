#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/deploy/internal/verify-opencode-tool-runtime.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-opencode-tool-runtime-test.XXXXXX")"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

write_runtime_fixture() {
  local runtime_root="$1"
  local dependency_entry dependency version entrypoint
  local dependencies=(
    '@modelcontextprotocol/sdk|1.29.0|dist/esm/server/mcp.js'
    '@opencode-ai/plugin|1.18.4|dist/index.js'
    '@opencode-ai/sdk|1.18.4|dist/index.js'
    'effect|4.0.0-beta.83|dist/index.js'
    'jsonc-parser|3.3.1|lib/esm/main.js'
    'zod|4.1.8|index.js'
  )

  mkdir -p "${runtime_root}/node_modules"
  cp "${ROOT_DIR}/deploy/internal/opencode-node-runtime.package.json" \
    "${runtime_root}/package.json"
  cp "${ROOT_DIR}/deploy/internal/opencode-node-runtime.package-lock.json" \
    "${runtime_root}/package-lock.json"
  for dependency_entry in "${dependencies[@]}"; do
    dependency="${dependency_entry%%|*}"
    version="${dependency_entry#*|}"
    version="${version%%|*}"
    entrypoint="${dependency_entry##*|}"
    mkdir -p "${runtime_root}/node_modules/${dependency}"
    printf '{\n  "name": "%s",\n  "version": "%s"\n}\n' "${dependency}" "${version}" \
      >"${runtime_root}/node_modules/${dependency}/package.json"
    mkdir -p "$(dirname "${runtime_root}/node_modules/${dependency}/${entrypoint}")"
    printf 'export const fixture = true;\n' \
      >"${runtime_root}/node_modules/${dependency}/${entrypoint}"
  done
}

PROGRAMS_ROOT="${TMP_ROOT}/programs-root"
RUNTIME_ROOT="${PROGRAMS_ROOT}/programs/opencode"
PROGRAMS_ARCHIVE="${TMP_ROOT}/test-agent-programs.tar.gz"
write_runtime_fixture "${RUNTIME_ROOT}"
tar -C "${PROGRAMS_ROOT}" -czf "${PROGRAMS_ARCHIVE}" programs

root_output="$(bash "${VERIFY_SCRIPT}" --root "${RUNTIME_ROOT}")"
grep -Fq 'plugin/sdk/effect/zod are present' <<<"${root_output}"
archive_output="$(bash "${VERIFY_SCRIPT}" --archive "${PROGRAMS_ARCHIVE}")"
grep -Fq 'plugin/sdk/effect/zod are present' <<<"${archive_output}"

# `@opencode-ai/plugin` 是所有 TypeScript Tool 的定义入口，缺失时必须明确失败。
rm -f "${RUNTIME_ROOT}/node_modules/@opencode-ai/plugin/package.json"
if missing_plugin_output="$(bash "${VERIFY_SCRIPT}" --root "${RUNTIME_ROOT}" 2>&1)"; then
  echo 'Runtime verification unexpectedly accepted a missing @opencode-ai/plugin package' >&2
  exit 1
fi
grep -Fq 'node_modules/@opencode-ai/plugin/package.json' <<<"${missing_plugin_output}"

write_runtime_fixture "${RUNTIME_ROOT}"
rm -f "${RUNTIME_ROOT}/node_modules/@opencode-ai/plugin/dist/index.js"
if missing_entrypoint_output="$(bash "${VERIFY_SCRIPT}" --root "${RUNTIME_ROOT}" 2>&1)"; then
  echo 'Runtime verification unexpectedly accepted @opencode-ai/plugin without its entrypoint' >&2
  exit 1
fi
grep -Fq 'node_modules/@opencode-ai/plugin/dist/index.js' <<<"${missing_entrypoint_output}"

write_runtime_fixture "${RUNTIME_ROOT}"
sed -i.bak 's/"version": "1.18.4"/"version": "0.0.0"/' \
  "${RUNTIME_ROOT}/node_modules/@opencode-ai/sdk/package.json"
rm -f "${RUNTIME_ROOT}/node_modules/@opencode-ai/sdk/package.json.bak"
if wrong_version_output="$(bash "${VERIFY_SCRIPT}" --root "${RUNTIME_ROOT}" 2>&1)"; then
  echo 'Runtime verification unexpectedly accepted the wrong @opencode-ai/sdk version' >&2
  exit 1
fi
grep -Fq 'wrong version: @opencode-ai/sdk, expected 1.18.4' <<<"${wrong_version_output}"

rm -f "${RUNTIME_ROOT}/node_modules/zod/package.json" "${PROGRAMS_ARCHIVE}"
tar -C "${PROGRAMS_ROOT}" -czf "${PROGRAMS_ARCHIVE}" programs
if missing_archive_output="$(bash "${VERIFY_SCRIPT}" --archive "${PROGRAMS_ARCHIVE}" 2>&1)"; then
  echo 'Archive verification unexpectedly accepted a missing zod package' >&2
  exit 1
fi
grep -Fq 'missing required Tool runtime dependencies' <<<"${missing_archive_output}"

echo 'OpenCode Tool runtime archive/install dependency gates verified'
