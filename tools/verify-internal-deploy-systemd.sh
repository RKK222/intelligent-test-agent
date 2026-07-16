#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARCHIVE="${1:-${ROOT_DIR}/deploy/internal/dist/test-agent-internal-release.zip}"
TMP_ROOT="$(mktemp -d)"

cleanup() {
  if [[ -n "${ORPHAN_PID:-}" ]]; then
    kill "${ORPHAN_PID}" 2>/dev/null || true
  fi
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

FAKE_BIN="${TMP_ROOT}/bin"
INSTALL_ROOT="${TMP_ROOT}/testagent"
UNIT_DIR="${TMP_ROOT}/systemd"
CALL_LOG="${TMP_ROOT}/systemctl.log"
SYSTEMD_STATE="${TMP_ROOT}/systemctl.state"
PROC_ROOT="${TMP_ROOT}/proc"
ORPHAN_LISTENER_FILE="${TMP_ROOT}/orphan-listener"
DEPLOY_SCRIPT="${TMP_ROOT}/deploy-internal-release.sh"
mkdir -p "${FAKE_BIN}" "${INSTALL_ROOT}/config" "${INSTALL_ROOT}/data" "${UNIT_DIR}" "${PROC_ROOT}"

# 必须验证最终 zip 内的脚本，避免只测到工作区源码而漏掉重新打包。
unzip -p "${ARCHIVE}" deploy/internal/deploy-internal-release.sh >"${DEPLOY_SCRIPT}"
chmod +x "${DEPLOY_SCRIPT}"

printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${FAKE_BIN}/java"
printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${FAKE_BIN}/curl"
printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${FAKE_BIN}/journalctl"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'case "$1" in' \
  'cat)' \
  '  test -f "${TEST_AGENT_SYSTEMD_UNIT_DIR}/$2"' \
  '  exit' \
  '  ;;' \
  'show)' \
  '  case "$3" in' \
  '    --property=ExecStart) sed -n "s/^ExecStart=//p" "${TEST_AGENT_SYSTEMD_UNIT_DIR}/$2" ;;' \
  '    --property=EnvironmentFiles) sed -n "s/^EnvironmentFile=//p" "${TEST_AGENT_SYSTEMD_UNIT_DIR}/$2" ;;' \
  '    --property=MainPID) printf "4242\n" ;;' \
  '  esac' \
  '  exit' \
  '  ;;' \
  'is-active)' \
  '  [[ "$(cat "${TEST_AGENT_SYSTEMCTL_STATE}")" == "running" ]]' \
  '  exit' \
  '  ;;' \
  'stop)' \
  '  printf "stopped\n" >"${TEST_AGENT_SYSTEMCTL_STATE}"' \
  '  ;;' \
  'start)' \
  '  printf "running\n" >"${TEST_AGENT_SYSTEMCTL_STATE}"' \
  '  ;;' \
  'esac' \
  'printf "%s\n" "$*" >>"${TEST_AGENT_SYSTEMCTL_CALL_LOG}"' \
  'exit 0' >"${FAKE_BIN}/systemctl"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'if [[ "$(cat "${TEST_AGENT_SYSTEMCTL_STATE}")" == "running" ]]; then' \
  '  printf "4242\n"' \
  '  exit 0' \
  'fi' \
  'if [[ -n "${TEST_AGENT_ORPHAN_PID:-}" && -f "${TEST_AGENT_ORPHAN_LISTENER_FILE}" ]] && kill -0 "${TEST_AGENT_ORPHAN_PID}" 2>/dev/null; then' \
  '  printf "%s\n" "${TEST_AGENT_ORPHAN_PID}"' \
  '  rm -f "${TEST_AGENT_ORPHAN_LISTENER_FILE}"' \
  '  exit 0' \
  'fi' \
  'exit 1' >"${FAKE_BIN}/lsof"
chmod +x "${FAKE_BIN}/java" "${FAKE_BIN}/curl" "${FAKE_BIN}/journalctl" "${FAKE_BIN}/systemctl" "${FAKE_BIN}/lsof"

printf 'stopped\n' >"${SYSTEMD_STATE}"

printf '%s\n' 'SPRING_PROFILES_ACTIVE=prod' >"${INSTALL_ROOT}/config/backend.env"
printf '%s\n' 'test-agent-backend-127-0-0-1' >"${INSTALL_ROOT}/data/.serverid"
printf '%s\n' '127.0.0.1' >"${INSTALL_ROOT}/data/.serverhost"

# 模拟历史上由手工 java -jar 启动、systemd stop 后仍占用 8080 的同一交付 JAR 进程。
bash -c 'exec -a "$1" sleep 300' _ "java -jar ${INSTALL_ROOT}/dist/backend/test-agent-app.jar" &
ORPHAN_PID=$!
touch "${ORPHAN_LISTENER_FILE}"
mkdir -p "${PROC_ROOT}/${ORPHAN_PID}"
printf 'java\0-jar\0%s\0' "${INSTALL_ROOT}/dist/backend/test-agent-app.jar" >"${PROC_ROOT}/${ORPHAN_PID}/cmdline"

run_deploy() {
  PATH="${FAKE_BIN}:${PATH}" \
  TEST_AGENT_SYSTEMD_UNIT_DIR="${UNIT_DIR}" \
  TEST_AGENT_SYSTEMCTL_CALL_LOG="${CALL_LOG}" \
  TEST_AGENT_SYSTEMCTL_STATE="${SYSTEMD_STATE}" \
  TEST_AGENT_ORPHAN_PID="${ORPHAN_PID}" \
  TEST_AGENT_ORPHAN_LISTENER_FILE="${ORPHAN_LISTENER_FILE}" \
  TEST_AGENT_PROC_ROOT="${PROC_ROOT}" \
  bash "${DEPLOY_SCRIPT}" \
    --archive "${ARCHIVE}" \
    --extract-dir "${TMP_ROOT}/extract" \
    --install-root "${INSTALL_ROOT}" \
    --backend-host 127.0.0.1 \
    --skip-frontend \
    --skip-worker
}

# 用假的 systemctl/curl 走完整后端安装分支，验证首次安装会创建并启用 unit。
run_deploy
if kill -0 "${ORPHAN_PID}" 2>/dev/null; then
  echo "Expected orphan backend process was not stopped" >&2
  exit 1
fi
wait "${ORPHAN_PID}" 2>/dev/null || true

UNIT_FILE="${UNIT_DIR}/test-agent-backend.service"
test -s "${INSTALL_ROOT}/dist/backend/test-agent-app.jar"
test -x "${INSTALL_ROOT}/programs/bin/opencode-manager"
grep -Fq "EnvironmentFile=${INSTALL_ROOT}/config/backend.env" "${UNIT_FILE}"
grep -Fq "ExecStart=${FAKE_BIN}/java -jar ${INSTALL_ROOT}/dist/backend/test-agent-app.jar" "${UNIT_FILE}"
grep -Fxq 'daemon-reload' "${CALL_LOG}"
grep -Fxq 'enable test-agent-backend.service' "${CALL_LOG}"
grep -Fxq 'stop test-agent-backend.service' "${CALL_LOG}"
grep -Fxq 'start test-agent-backend.service' "${CALL_LOG}"

# 第二次升级必须识别已加载 unit，不能覆盖现场追加的配置，也不能再次 enable。
printf '%s\n' '# existing unit configuration must be preserved' >>"${UNIT_FILE}"
run_deploy
grep -Fq '# existing unit configuration must be preserved' "${UNIT_FILE}"
test "$(grep -Fxc 'enable test-agent-backend.service' "${CALL_LOG}")" = 1

echo "Internal backend first-install and existing-systemd upgrade flows verified"
