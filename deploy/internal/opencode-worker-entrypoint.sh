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

maintenance_pid=""
manager_pid=""

stop_children() {
  if [[ -n "${maintenance_pid}" ]]; then
    kill -TERM "${maintenance_pid}" 2>/dev/null || true
  fi
  if [[ -n "${manager_pid}" ]]; then
    kill -TERM "${manager_pid}" 2>/dev/null || true
  fi
}

trap stop_children INT TERM EXIT

if [[ "${1:-}" == "run" ]]; then
  # 递归工作区检查由每台 worker 唯一的后台维护器承担，不能阻塞或按用户重复执行启动链路。
  "${OPENCODE_BIN}" __maintain-project-config --root "$(pwd)" &
  maintenance_pid="$!"
fi

"${manager_bin}" "$@" &
manager_pid="$!"
set +e
wait "${manager_pid}"
manager_status="$?"
set -e

stop_children
if [[ -n "${maintenance_pid}" ]]; then
  wait "${maintenance_pid}" 2>/dev/null || true
fi
trap - INT TERM EXIT
exit "${manager_status}"
