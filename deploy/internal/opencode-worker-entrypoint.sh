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

maintenance_interval_seconds="${OPENCODE_PROJECT_CONFIG_MAINTENANCE_INTERVAL_SECONDS:-60}"
if [[ ! "${maintenance_interval_seconds}" =~ ^[1-9][0-9]*$ ]]; then
  echo "OPENCODE_PROJECT_CONFIG_MAINTENANCE_INTERVAL_SECONDS must be a positive integer" >&2
  exit 64
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

run_project_config_maintenance() {
  local active_pid=""
  local scan_status=0
  local workspace_root="$1"

  stop_maintenance_child() {
    if [[ -n "${active_pid}" ]]; then
      kill -TERM "${active_pid}" 2>/dev/null || true
      wait "${active_pid}" 2>/dev/null || true
    fi
    exit 0
  }
  trap stop_maintenance_child INT TERM

  while true; do
    sleep "${maintenance_interval_seconds}" &
    active_pid="$!"
    wait "${active_pid}"
    active_pid=""

    # 每轮只启动一个短生命周期 Node 扫描，结束后立即释放内存和文件句柄。
    "${OPENCODE_BIN}" __reconcile-project-config --root "${workspace_root}" &
    active_pid="$!"
    if wait "${active_pid}"; then
      scan_status=0
    else
      scan_status="$?"
    fi
    active_pid=""
    if [[ "${scan_status}" -ne 0 ]]; then
      echo "event=opencode_project_config_reconcile_failed exitCode=${scan_status}" >&2
    fi
  done
}

cgroup_oom_kill_count() {
  local value=""
  if [[ -r /sys/fs/cgroup/memory.events ]]; then
    value="$(awk '$1 == "oom_kill" { print $2 }' /sys/fs/cgroup/memory.events 2>/dev/null || true)"
  elif [[ -r /sys/fs/cgroup/memory/memory.oom_control ]]; then
    value="$(awk '$1 == "oom_kill" { print $2 }' /sys/fs/cgroup/memory/memory.oom_control 2>/dev/null || true)"
  fi
  printf '%s' "${value:-unavailable}"
}

initial_cgroup_oom_kill_count="$(cgroup_oom_kill_count)"

# manager 必须先启动；维护循环第一轮先等待完整周期，不能与开机争用资源。
"${manager_bin}" "$@" &
manager_pid="$!"
if [[ "${1:-}" == "run" ]]; then
  run_project_config_maintenance "$(pwd -P)" &
  maintenance_pid="$!"
fi

set +e
wait "${manager_pid}"
manager_status="$?"
set -e

if [[ "${manager_status}" -ne 0 && "${manager_status}" -ne 130 && "${manager_status}" -ne 143 ]]; then
  manager_signal="none"
  if [[ "${manager_status}" -gt 128 ]]; then
    manager_signal="signal-$((manager_status - 128))"
  fi
  if [[ "${manager_status}" -eq 137 ]]; then
    manager_signal="SIGKILL"
  fi
  current_cgroup_oom_kill_count="$(cgroup_oom_kill_count)"
  cgroup_oom_kill_delta="unavailable"
  if [[ "${initial_cgroup_oom_kill_count}" =~ ^[0-9]+$ \
    && "${current_cgroup_oom_kill_count}" =~ ^[0-9]+$ \
    && "${current_cgroup_oom_kill_count}" -ge "${initial_cgroup_oom_kill_count}" ]]; then
    cgroup_oom_kill_delta="$((current_cgroup_oom_kill_count - initial_cgroup_oom_kill_count))"
  fi
  echo "event=opencode_manager_exited exitCode=${manager_status} signal=${manager_signal} cgroupOomKillCount=${current_cgroup_oom_kill_count} cgroupOomKillDelta=${cgroup_oom_kill_delta}" >&2
fi

stop_children
if [[ -n "${maintenance_pid}" ]]; then
  wait "${maintenance_pid}" 2>/dev/null || true
fi
trap - INT TERM EXIT
exit "${manager_status}"
