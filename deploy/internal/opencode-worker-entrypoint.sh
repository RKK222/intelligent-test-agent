#!/usr/bin/env bash
set -euo pipefail

EXTERNAL_PROGRAM_ROOT="${TEST_AGENT_PROGRAM_ROOT:-/data/testagent/programs}"
EXTERNAL_MANAGER="${EXTERNAL_PROGRAM_ROOT}/bin/opencode-manager"
EXTERNAL_OPENCODE="${EXTERNAL_PROGRAM_ROOT}/opencode/bin/opencode"
BUILTIN_MANAGER="/usr/local/bin/opencode-manager"
BUILTIN_OPENCODE="/usr/local/bin/opencode"

manager_bin="${BUILTIN_MANAGER}"
if [[ -x "${EXTERNAL_MANAGER}" ]]; then
  manager_bin="${EXTERNAL_MANAGER}"
fi

if [[ -z "${OPENCODE_BIN:-}" || ! -x "${OPENCODE_BIN}" ]]; then
  if [[ -x "${EXTERNAL_OPENCODE}" ]]; then
    export OPENCODE_BIN="${EXTERNAL_OPENCODE}"
  else
    export OPENCODE_BIN="${BUILTIN_OPENCODE}"
  fi
fi

if [[ $# -eq 0 ]]; then
  set -- run
fi

exec "${manager_bin}" "$@"
