#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_SCRIPT="${ROOT_DIR}/deploy/internal/deploy-multi-backend-node.sh"
CONFIGURE_SCRIPT="${ROOT_DIR}/deploy/internal/configure-single-deployment.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-multi-node-verify.XXXXXX")"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

CONFIG_114="${TMP_ROOT}/config-114"
CONFIG_4="${TMP_ROOT}/config-4"
CONFIG_115="${TMP_ROOT}/config-115"
CONFIG_FRONTEND="${TMP_ROOT}/config-frontend"
CONFIG_FRONTEND_3="${TMP_ROOT}/config-frontend-3"
RELEASE_ROOT="${TMP_ROOT}/release-root"
RELEASE_ARCHIVE="${TMP_ROOT}/test-agent-internal-release.zip"
mkdir -p "${CONFIG_114}" "${CONFIG_4}" "${CONFIG_115}" "${CONFIG_FRONTEND}" \
  "${CONFIG_FRONTEND_3}" \
  "${RELEASE_ROOT}/dist/backend/lib" "${RELEASE_ROOT}/deploy/internal"

printf '%s\n' \
  'TEST_AGENT_DB_PASSWORD=database-secret-must-not-print' \
  'TEST_AGENT_REDIS_PASSWORD=' \
  'TEST_AGENT_API_TOKEN=' \
  'TEST_AGENT_OPENCODE_MANAGER_TOKEN=manager-secret-must-not-print' \
  'TEST_AGENT_INTERNAL_PROXY_API_KEY=proxy-secret-must-not-print' \
  'TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=xxl-mysql-secret-must-not-print' \
  'TEST_AGENT_XXL_JOB_ACCESS_TOKEN=xxl-access-secret-must-not-print' \
  >"${CONFIG_114}/backend.env"
printf '%s\n' \
  'TEST_AGENT_OPENCODE_MANAGER_TOKEN=manager-secret-must-not-print' \
  >"${CONFIG_114}/docker.env"

# 复用单后台配置渲染程序生成标准基线，再仅替换 .4 的节点身份。
bash "${CONFIGURE_SCRIPT}" backend \
  --backend-env "${CONFIG_114}/backend.env" \
  --docker-env "${CONFIG_114}/docker.env" \
  --backend-template "${ROOT_DIR}/deploy/internal/backend.env.example" \
  --docker-template "${ROOT_DIR}/deploy/internal/env.example" \
  >/dev/null
# 当前双后台现场每台 worker 固定发布 1000 个同号端口；通用单后台模板仍保留较小默认值。
sed -i.bak \
  -e 's/^OPENCODE_WORKER_PORT_START=.*/OPENCODE_WORKER_PORT_START=14096/' \
  -e 's/^OPENCODE_WORKER_PORT_END=.*/OPENCODE_WORKER_PORT_END=15095/' \
  "${CONFIG_114}/docker.env"
rm -f "${CONFIG_114}/docker.env.bak"
cp "${CONFIG_114}/backend.env" "${CONFIG_4}/backend.env"
cp "${CONFIG_114}/docker.env" "${CONFIG_4}/docker.env"
sed -i.bak \
  -e 's/TEST_AGENT_SERVER_ADVERTISED_HOST=122\.233\.30\.114/TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.4/' \
  -e 's/TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-114/TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-4/' \
  "${CONFIG_4}/backend.env"
rm -f "${CONFIG_4}/backend.env.bak"

printf '%s\n' \
  'TEST_AGENT_NGINX_MODE=multi' \
  'TEST_AGENT_NGINX_BACKENDS=122.233.30.4:8080,122.233.30.114:8080' \
  'TEST_AGENT_NGINX_XXL_JOB_ADMINS=122.233.30.4:18080,122.233.30.114:18080' \
  'TEST_AGENT_NGINX_TOOLBOX_IT_TOOLS_UPSTREAM=122.233.30.4:18120,122.233.30.114:18120' \
  'TEST_AGENT_NGINX_TOOLBOX_OMNI_TOOLS_UPSTREAM=122.233.30.4:18121,122.233.30.114:18121' \
  'TEST_AGENT_NGINX_SERVER_ROUTES=test-agent-backend-122-233-30-4=122.233.30.4:8080,test-agent-backend-122-233-30-114=122.233.30.114:8080' \
  'TEST_AGENT_NGINX_LISTEN_PORT=80' \
  'TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS=9996' \
  'TEST_AGENT_NGINX_TLS_ENABLED=false' \
  'TEST_AGENT_FRONTEND_ROOT=/data/testagent/frontend' \
  'TEST_AGENT_NGINX_CONF_PATH=/data/apps/nginx/conf/test-agent.conf' \
  'TEST_AGENT_NGINX_BIN=/data/apps/nginx/sbin/nginx' \
  'TEST_AGENT_NGINX_PREFIX=/data/apps/nginx' \
  'TEST_AGENT_NGINX_MAIN_CONF=/data/apps/nginx/conf/nginx.conf' \
  'TEST_AGENT_NGINX_RELOAD_MODE=binary' \
  >"${CONFIG_FRONTEND}/nginx.env"

JAR_ROOT="${TMP_ROOT}/jar-root"
EMPTY_ROOT="${TMP_ROOT}/empty-root"
mkdir -p "${JAR_ROOT}/BOOT-INF/classes" "${EMPTY_ROOT}"
printf 'fixture-rsa-private-key\n' >"${JAR_ROOT}/BOOT-INF/classes/rsa-private.key"
(cd "${JAR_ROOT}" && zip -qr "${RELEASE_ROOT}/dist/backend/test-agent-app.jar" .)
printf 'fixture external library\n' >"${RELEASE_ROOT}/dist/backend/lib/fixture.jar"
tar -C "${EMPTY_ROOT}" -czf "${RELEASE_ROOT}/dist/test-agent-frontend-dist.tar.gz" .
tar -C "${EMPTY_ROOT}" -czf "${RELEASE_ROOT}/dist/test-agent-programs.tar.gz" .
printf 'fixture worker image\n' >"${RELEASE_ROOT}/dist/test-agent-opencode-worker_internal-linux-amd64.tar"
printf 'fixture it-tools image\n' >"${RELEASE_ROOT}/dist/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar"
printf 'fixture omni-tools image\n' >"${RELEASE_ROOT}/dist/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar"
cp "${ROOT_DIR}/deploy/internal/deploy-internal-release.sh" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/deploy-internal-frontend.sh" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/opencode-worker-docker.sh" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/ensure-opencode-runtime-gitignore.sh" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/opencode-runtime.gitignore" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/configure-nginx.sh" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/toolbox.env.example" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/toolbox-docker.sh" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/diagnose-toolbox.sh" "${RELEASE_ROOT}/deploy/internal/"
printf '%s\n' \
  'TEST_AGENT_RELEASE_COMPONENT_MANIFEST_VERSION=1' \
  'TEST_AGENT_RELEASE_WORKER_RUNTIME=included' \
  'TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=fixture-worker' \
  'TEST_AGENT_RELEASE_TOOLBOX=included' \
  'TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT=fixture-toolbox' \
  >"${RELEASE_ROOT}/deploy/internal/release-components.env"
(cd "${RELEASE_ROOT}" && zip -qr "${RELEASE_ARCHIVE}" .)
(cd "${TMP_ROOT}" && shasum -a 256 "$(basename "${RELEASE_ARCHIVE}")" \
  >"$(basename "${RELEASE_ARCHIVE}").sha256")

validate_without_secret_output() {
  local role="$1"
  local config_dir="$2"
  shift 2
  local output
  output="$(bash "${DEPLOY_SCRIPT}" "${role}" \
    --config-dir "${config_dir}" \
    --release-archive "${RELEASE_ARCHIVE}" \
    --validate-only "$@" 2>&1)"
  if grep -Eq 'database-secret|manager-secret|proxy-secret|xxl-mysql-secret|xxl-access-secret' <<<"${output}"; then
    echo "Validation output leaked a prepared secret" >&2
    exit 1
  fi
  grep -Fq 'validation passed' <<<"${output}"
}

validate_without_secret_output backend "${CONFIG_4}" --backend-host 122.233.30.4
validate_without_secret_output backend "${CONFIG_114}" --backend-host 122.233.30.114
validate_without_secret_output frontend "${CONFIG_FRONTEND}"

# 未更新的 Manager/Codex/OpenCode runtime 由现场复用时，增量包不携带 programs 和 worker tar，
# 但节点预校验仍必须接受清单明确声明的 reuse 模式。
REUSE_RELEASE_ROOT="${TMP_ROOT}/reuse-release-root"
REUSE_RELEASE_ARCHIVE="${TMP_ROOT}/test-agent-incremental-release.zip"
cp -R "${RELEASE_ROOT}" "${REUSE_RELEASE_ROOT}"
rm -f "${REUSE_RELEASE_ROOT}/dist/test-agent-programs.tar.gz" \
  "${REUSE_RELEASE_ROOT}/dist/test-agent-opencode-worker_internal-linux-amd64.tar"
sed -i.bak 's/TEST_AGENT_RELEASE_WORKER_RUNTIME=included/TEST_AGENT_RELEASE_WORKER_RUNTIME=reuse/' \
  "${REUSE_RELEASE_ROOT}/deploy/internal/release-components.env"
rm -f "${REUSE_RELEASE_ROOT}/deploy/internal/release-components.env.bak"
(cd "${REUSE_RELEASE_ROOT}" && zip -qr "${REUSE_RELEASE_ARCHIVE}" .)
(cd "${TMP_ROOT}" && shasum -a 256 "$(basename "${REUSE_RELEASE_ARCHIVE}")" \
  >"$(basename "${REUSE_RELEASE_ARCHIVE}").sha256")
reuse_output="$(bash "${DEPLOY_SCRIPT}" backend \
  --config-dir "${CONFIG_4}" \
  --release-archive "${REUSE_RELEASE_ARCHIVE}" \
  --backend-host 122.233.30.4 \
  --validate-only 2>&1)"
grep -Fq 'worker runtime component: reuse' <<<"${reuse_output}"
grep -Fq 'validation passed' <<<"${reuse_output}"

# 真正升级前必须核对目标机已安装的 worker runtime 指纹，不能只凭旧容器仍在运行就复用。
REUSE_INSTALL_ROOT="${TMP_ROOT}/reuse-install"
REUSE_BIN="${TMP_ROOT}/reuse-bin"
mkdir -p "${REUSE_INSTALL_ROOT}/config" "${REUSE_BIN}"
printf 'fixture docker env\n' >"${REUSE_INSTALL_ROOT}/config/docker.env"
printf '%s\n' \
  'TEST_AGENT_RELEASE_COMPONENT_STATE_VERSION=1' \
  'TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=wrong-worker' \
  >"${REUSE_INSTALL_ROOT}/config/release-component-state.env"
printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${REUSE_BIN}/curl"
printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${REUSE_BIN}/systemctl"
printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${REUSE_BIN}/docker"
chmod +x "${REUSE_BIN}/curl" "${REUSE_BIN}/systemctl" "${REUSE_BIN}/docker"
if mismatch_output="$(PATH="${REUSE_BIN}:${PATH}" \
  bash "${ROOT_DIR}/deploy/internal/deploy-internal-release.sh" \
    --archive "${REUSE_RELEASE_ARCHIVE}" \
    --extract-dir "${TMP_ROOT}/reuse-extract" \
    --install-root "${REUSE_INSTALL_ROOT}" \
    --docker-env "${REUSE_INSTALL_ROOT}/config/docker.env" \
    --skip-frontend 2>&1)"; then
  echo 'Worker runtime reuse unexpectedly accepted a mismatched installed fingerprint' >&2
  exit 1
fi
grep -Fq 'worker runtime fingerprint does not match the installed component' <<<"${mismatch_output}"

# 新后台沿用同一配置字段，只替换本机 advertised host 和稳定 server ID。
cp "${CONFIG_4}/backend.env" "${CONFIG_115}/backend.env"
cp "${CONFIG_4}/docker.env" "${CONFIG_115}/docker.env"
sed -i.bak \
  -e 's/TEST_AGENT_SERVER_ADVERTISED_HOST=122\.233\.30\.4/TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.115/' \
  -e 's/TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-4/TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-115/' \
  "${CONFIG_115}/backend.env"
rm -f "${CONFIG_115}/backend.env.bak"
validate_without_secret_output backend "${CONFIG_115}" --backend-host 122.233.30.115

cp "${CONFIG_FRONTEND}/nginx.env" "${CONFIG_FRONTEND_3}/nginx.env"
sed -i.bak \
  -e 's#TEST_AGENT_NGINX_BACKENDS=.*#TEST_AGENT_NGINX_BACKENDS=122.233.30.4:8080,122.233.30.114:8080,122.233.30.115:8080#' \
  -e 's#TEST_AGENT_NGINX_XXL_JOB_ADMINS=.*#TEST_AGENT_NGINX_XXL_JOB_ADMINS=122.233.30.4:18080,122.233.30.114:18080,122.233.30.115:18080#' \
  -e 's#TEST_AGENT_NGINX_SERVER_ROUTES=.*#TEST_AGENT_NGINX_SERVER_ROUTES=test-agent-backend-122-233-30-4=122.233.30.4:8080,test-agent-backend-122-233-30-114=122.233.30.114:8080,test-agent-backend-122-233-30-115=122.233.30.115:8080#' \
  "${CONFIG_FRONTEND_3}/nginx.env"
rm -f "${CONFIG_FRONTEND_3}/nginx.env.bak"
validate_without_secret_output frontend "${CONFIG_FRONTEND_3}"

BAD_BACKEND="${TMP_ROOT}/bad-backend"
mkdir -p "${BAD_BACKEND}"
cp "${CONFIG_4}/backend.env" "${BAD_BACKEND}/backend.env"
cp "${CONFIG_4}/docker.env" "${BAD_BACKEND}/docker.env"
printf 'TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH=/forbidden/rsa-private.key\n' \
  >>"${BAD_BACKEND}/backend.env"
if bash "${DEPLOY_SCRIPT}" backend \
  --config-dir "${BAD_BACKEND}" \
  --release-archive "${RELEASE_ARCHIVE}" \
  --backend-host 122.233.30.4 \
  --validate-only >/dev/null 2>&1; then
  echo "Validation unexpectedly accepted an external RSA path" >&2
  exit 1
fi

BAD_FRONTEND="${TMP_ROOT}/bad-frontend"
mkdir -p "${BAD_FRONTEND}"
sed 's/TEST_AGENT_NGINX_MODE=multi/TEST_AGENT_NGINX_MODE=single/' \
  "${CONFIG_FRONTEND}/nginx.env" >"${BAD_FRONTEND}/nginx.env"
if bash "${DEPLOY_SCRIPT}" frontend \
  --config-dir "${BAD_FRONTEND}" \
  --release-archive "${RELEASE_ARCHIVE}" \
  --validate-only >/dev/null 2>&1; then
  echo "Validation unexpectedly accepted single-backend Nginx configuration" >&2
  exit 1
fi

# 用假的 systemctl/curl/docker 执行真实 --verify-only 分支，防止结构化 manager 日志被误报失败。
VERIFY_INSTALL_ROOT="${TMP_ROOT}/verify-install"
VERIFY_BIN="${TMP_ROOT}/verify-bin"
mkdir -p "${VERIFY_INSTALL_ROOT}/config" "${VERIFY_INSTALL_ROOT}/data" \
  "${VERIFY_INSTALL_ROOT}/dist/backend" "${VERIFY_BIN}"
cp "${CONFIG_4}/backend.env" "${VERIFY_INSTALL_ROOT}/config/backend.env"
cp "${CONFIG_4}/docker.env" "${VERIFY_INSTALL_ROOT}/config/docker.env"
cp "${RELEASE_ROOT}/dist/backend/test-agent-app.jar" \
  "${VERIFY_INSTALL_ROOT}/dist/backend/test-agent-app.jar"
printf 'test-agent-backend-122-233-30-4\n' >"${VERIFY_INSTALL_ROOT}/data/.serverid"
printf '122.233.30.4\n' >"${VERIFY_INSTALL_ROOT}/data/.serverhost"

printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${VERIFY_BIN}/systemctl"
printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"${VERIFY_BIN}/curl"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'case "$1" in' \
  '  inspect)' \
  '    if [[ "$*" == *".State.Running"* ]]; then printf "true\n"; else printf "healthy\n"; fi' \
  '    ;;' \
  '  port)' \
  '    printf "14096/tcp -> 0.0.0.0:14096\n15095/tcp -> 0.0.0.0:15095\n"' \
  '    ;;' \
  '  logs)' \
  '    printf "%s\n" "${TEST_AGENT_WORKER_LOG_LINE}"' \
  '    ;;' \
  '  *) exit 1 ;;' \
  'esac' >"${VERIFY_BIN}/docker"
chmod +x "${VERIFY_BIN}/systemctl" "${VERIFY_BIN}/curl" "${VERIFY_BIN}/docker"

verify_worker_log_format() {
  local log_line="$1"
  local output
  output="$(PATH="${VERIFY_BIN}:${PATH}" \
    TEST_AGENT_WORKER_LOG_LINE="${log_line}" \
    bash "${DEPLOY_SCRIPT}" backend \
      --install-root "${VERIFY_INSTALL_ROOT}" \
      --backend-host 122.233.30.4 \
      --verify-only 2>&1)"
  grep -Fq 'Backend verification passed: host=122.233.30.4' <<<"${output}"
}

verify_worker_log_format \
  'event=manager_config_update status=applied traceId=fixture previousMaxProcesses=20 appliedMaxProcesses=30 requestedMaxProcesses=30'
verify_worker_log_format 'manager config update applied'
skip_output="$(PATH="${VERIFY_BIN}:${PATH}" \
  TEST_AGENT_WORKER_LOG_LINE='event=manager_config_update status=applied' \
  bash "${DEPLOY_SCRIPT}" backend \
    --install-root "${VERIFY_INSTALL_ROOT}" \
    --backend-host 122.233.30.4 \
    --skip-peer-check \
    --verify-only 2>&1)"
grep -Fq 'peer=deferred' <<<"${skip_output}"
grep -Fq "grep -E 'event=manager_config_update status=applied|manager config update applied'" \
  "${ROOT_DIR}/deploy/internal/deploy-internal-release.sh"

# 前端验收必须真实探测两类工具的每个 upstream 和统一入口深链，不能只检查 Nginx 配置文本。
grep -Fq 'probe_toolbox_upstream IT-Tools "${entry}" /token-generator' "${DEPLOY_SCRIPT}"
grep -Fq 'probe_toolbox_upstream OmniTools "${entry}" /audio/change-speed' "${DEPLOY_SCRIPT}"
grep -Fq 'http://127.0.0.1/toolbox/apps/it-tools/token-generator' "${DEPLOY_SCRIPT}"
grep -Fq 'http://127.0.0.1/toolbox/apps/omni-tools/audio/change-speed' "${DEPLOY_SCRIPT}"
grep -Fq 'toolbox upstream health is unreachable from frontend node' "${DEPLOY_SCRIPT}"
grep -Fq 'toolbox deep link is unreachable from frontend node' "${DEPLOY_SCRIPT}"

echo 'Two-backend per-node validation, toolbox connectivity gates, embedded RSA, secret redaction and manager log compatibility verified'
