#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARCHIVE="${1:-${ROOT_DIR}/deploy/internal/dist/test-agent-internal-release.zip}"
TMP_ROOT="$(mktemp -d)"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

FAKE_BIN="${TMP_ROOT}/bin"
INSTALL_ROOT="${TMP_ROOT}/testagent"
UNIT_DIR="${TMP_ROOT}/systemd"
CALL_LOG="${TMP_ROOT}/systemctl.log"
DEPLOY_SCRIPT="${TMP_ROOT}/deploy-internal-release.sh"
mkdir -p "${FAKE_BIN}" "${INSTALL_ROOT}/config" "${INSTALL_ROOT}/data" "${UNIT_DIR}"

# 必须验证最终 zip 内的脚本，避免只测到工作区源码而漏掉重新打包。
unzip -p "${ARCHIVE}" deploy/internal/deploy-internal-release.sh >"${DEPLOY_SCRIPT}"
chmod +x "${DEPLOY_SCRIPT}"

printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${FAKE_BIN}/java"
printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${FAKE_BIN}/curl"
printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${FAKE_BIN}/journalctl"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'if [[ "$1" == "cat" ]]; then' \
  '  test -f "${TEST_AGENT_SYSTEMD_UNIT_DIR}/$2"' \
  '  exit' \
  'fi' \
  'printf "%s\n" "$*" >>"${TEST_AGENT_SYSTEMCTL_CALL_LOG}"' \
  'exit 0' >"${FAKE_BIN}/systemctl"
chmod +x "${FAKE_BIN}/java" "${FAKE_BIN}/curl" "${FAKE_BIN}/journalctl" "${FAKE_BIN}/systemctl"

printf '%s\n' 'SPRING_PROFILES_ACTIVE=prod' >"${INSTALL_ROOT}/config/backend.env"
printf '%s\n' 'test-agent-backend-127-0-0-1' >"${INSTALL_ROOT}/data/.serverid"
printf '%s\n' '127.0.0.1' >"${INSTALL_ROOT}/data/.serverhost"

run_deploy() {
  PATH="${FAKE_BIN}:${PATH}" \
  TEST_AGENT_SYSTEMD_UNIT_DIR="${UNIT_DIR}" \
  TEST_AGENT_SYSTEMCTL_CALL_LOG="${CALL_LOG}" \
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
