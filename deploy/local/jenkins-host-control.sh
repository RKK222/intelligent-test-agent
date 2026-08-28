#!/usr/bin/env bash
set -Eeuo pipefail

# 该脚本由 root 固定安装到 /usr/local/sbin，流水线只能调用无参数的 status/stop-legacy。
# 它只接管当前测试机上两个明确端口，不修改 abc 的源码工作树，也不停止 4096 OpenCode 进程。
readonly BACKEND_PORT=18082
readonly FRONTEND_PORT=3000
readonly LEGACY_USER=abc
readonly LEGACY_ROOT=/data/offload/home/abc/intelligent-test-agent
readonly DEPLOY_BACKEND_CONTAINER=test-agent-jenkins-backend
readonly DEPLOY_FRONTEND_CONTAINER=test-agent-jenkins-frontend

usage() {
    echo "Usage: $0 status|stop-legacy" >&2
    exit 2
}

port_pids() {
    local port=$1
    ss -lntp "sport = :${port}" 2>/dev/null \
        | sed -n -E 's/.*pid=([0-9]+).*/\1/p' \
        | sort -u
}

container_owns_port() {
    local port=$1 expected_container=$2
    [[ "$(docker inspect -f '{{.State.Running}}' "${expected_container}" 2>/dev/null || true)" == true ]]
}

legacy_pid_is_allowed() {
    local pid=$1 port=$2 expected_fragment cwd user command_line
    [[ -d "/proc/${pid}" ]] || return 1
    user=$(ps -o user= -p "${pid}" | tr -d '[:space:]')
    cwd=$(readlink -f "/proc/${pid}/cwd" 2>/dev/null || true)
    command_line=$(tr '\0' ' ' <"/proc/${pid}/cmdline" 2>/dev/null || true)
    [[ "${user}" == "${LEGACY_USER}" ]] || return 1
    [[ "${cwd}" == "${LEGACY_ROOT}" || "${cwd}" == "${LEGACY_ROOT}/"* ]] || return 1
    if [[ "${port}" == "${BACKEND_PORT}" ]]; then
        expected_fragment='/.tmp/dev-services/backend-runtime/test-agent-app.'
    else
        expected_fragment='/frontend/apps/agent-web/'
    fi
    [[ "${command_line}" == *"${expected_fragment}"* ]]
}

describe_port() {
    local port=$1 container=$2 pids pid
    if container_owns_port "${port}" "${container}"; then
        echo "port=${port} owner=jenkins-container container=${container}"
        return 0
    fi
    pids=$(port_pids "${port}")
    if [[ -z "${pids}" ]]; then
        echo "port=${port} owner=free"
        return 0
    fi
    for pid in ${pids}; do
        if legacy_pid_is_allowed "${pid}" "${port}"; then
            echo "port=${port} owner=legacy-abc pid=${pid}"
        else
            echo "Refusing unknown listener on port ${port}: pid=${pid}" >&2
            return 1
        fi
    done
}

stop_legacy_port() {
    local port=$1 container=$2 pids pid attempt
    if container_owns_port "${port}" "${container}"; then
        echo "Jenkins container already owns port ${port}; legacy takeover is not needed."
        return 0
    fi
    pids=$(port_pids "${port}")
    [[ -n "${pids}" ]] || return 0
    for pid in ${pids}; do
        legacy_pid_is_allowed "${pid}" "${port}" || {
            echo "Refusing to stop unknown listener on port ${port}: pid=${pid}" >&2
            return 1
        }
    done
    for pid in ${pids}; do
        kill -TERM "${pid}"
    done
    for attempt in $(seq 1 20); do
        [[ -z "$(port_pids "${port}")" ]] && return 0
        sleep 1
    done
    for pid in ${pids}; do
        if legacy_pid_is_allowed "${pid}" "${port}"; then
            kill -KILL "${pid}"
        fi
    done
    [[ -z "$(port_pids "${port}")" ]] || {
        echo "Port ${port} is still occupied after stopping the validated legacy process." >&2
        return 1
    }
}

[[ $# -eq 1 ]] || usage
case "$1" in
    status)
        describe_port "${BACKEND_PORT}" "${DEPLOY_BACKEND_CONTAINER}"
        describe_port "${FRONTEND_PORT}" "${DEPLOY_FRONTEND_CONTAINER}"
        ;;
    stop-legacy)
        stop_legacy_port "${BACKEND_PORT}" "${DEPLOY_BACKEND_CONTAINER}"
        stop_legacy_port "${FRONTEND_PORT}" "${DEPLOY_FRONTEND_CONTAINER}"
        ;;
    *)
        usage
        ;;
esac
