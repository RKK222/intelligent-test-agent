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
  'TEST_AGENT_LOBEHUB_HMAC_SECRET=lobehub-secret-must-not-print' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_URL=jdbc:clickhouse://122.233.30.147:8123/testagent_analytics' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_USERNAME=ck' \
  'TEST_AGENT_ANALYTICS_CLICKHOUSE_PASSWORD=clickhouse-secret-must-not-print' \
  'TEST_AGENT_MEMORY_SERVICE_API_KEY=memory-service-secret-must-not-print' \
  'TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET=memory-hmac-secret-must-not-print' \
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
grep -Fxq 'TEST_AGENT_LOBEHUB_HMAC_SECRET=lobehub-secret-must-not-print' \
  "${CONFIG_114}/backend.env"
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
PROGRAMS_ROOT="${TMP_ROOT}/programs-root"
PROGRAMS_RUNTIME="${PROGRAMS_ROOT}/programs/opencode"
mkdir -p "${JAR_ROOT}/BOOT-INF/classes" "${EMPTY_ROOT}" "${PROGRAMS_RUNTIME}/node_modules"
printf 'fixture-rsa-private-key\n' >"${JAR_ROOT}/BOOT-INF/classes/rsa-private.key"
(cd "${JAR_ROOT}" && zip -qr "${RELEASE_ROOT}/dist/backend/test-agent-app.jar" .)
PERSISTENCE_JAR_ROOT="${TMP_ROOT}/persistence-jar-root"
mkdir -p "${PERSISTENCE_JAR_ROOT}/db"
cp -a "${ROOT_DIR}/backend/test-agent-persistence/src/main/resources/db/." \
  "${PERSISTENCE_JAR_ROOT}/db/"
(cd "${PERSISTENCE_JAR_ROOT}" && zip -qr \
  "${RELEASE_ROOT}/dist/backend/lib/test-agent-persistence-0.1.0-SNAPSHOT.jar" .)
XXL_JAR_ROOT="${TMP_ROOT}/xxl-jar-root"
mkdir -p "${XXL_JAR_ROOT}/xxl-job/db"
cp -a "${ROOT_DIR}/backend/test-agent-xxl-job-integration/src/main/resources/xxl-job/db/." \
  "${XXL_JAR_ROOT}/xxl-job/db/"
(cd "${XXL_JAR_ROOT}" && zip -qr \
  "${RELEASE_ROOT}/dist/backend/lib/test-agent-xxl-job-integration-0.1.0-SNAPSHOT.jar" .)
tar -C "${EMPTY_ROOT}" -czf "${RELEASE_ROOT}/dist/test-agent-frontend-dist.tar.gz" .
# 多后台节点预校验与正式发布共用本地客户端制品门禁，夹具必须覆盖完整目录形态。
mkdir -p "${RELEASE_ROOT}/dist/local-opencode-client/stable"
printf '#!/usr/bin/env bash\nexit 0\n' \
  >"${RELEASE_ROOT}/dist/local-opencode-client/install.sh"
printf 'fixture pkg\n' \
  >"${RELEASE_ROOT}/dist/local-opencode-client/TestAgent-Local-Client-macOS-arm64.pkg"
printf 'fixture deb\n' \
  >"${RELEASE_ROOT}/dist/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.deb"
printf '{\n  "version": "fixture-local-client"\n}\n' \
  >"${RELEASE_ROOT}/dist/local-opencode-client/stable/manifest.json"
printf 'fixture signature\n' \
  >"${RELEASE_ROOT}/dist/local-opencode-client/stable/manifest.json.sig"
cp "${ROOT_DIR}/deploy/internal/opencode-node-runtime.package.json" \
  "${PROGRAMS_RUNTIME}/package.json"
cp "${ROOT_DIR}/deploy/internal/opencode-node-runtime.package-lock.json" \
  "${PROGRAMS_RUNTIME}/package-lock.json"
for dependency_entry in \
  '@modelcontextprotocol/sdk|1.29.0|dist/esm/server/mcp.js' \
  '@opencode-ai/plugin|1.18.4|dist/index.js' \
  '@opencode-ai/sdk|1.18.4|dist/index.js' \
  'effect|4.0.0-beta.83|dist/index.js' \
  'jsonc-parser|3.3.1|lib/esm/main.js' \
  'zod|4.1.8|index.js'; do
  dependency="${dependency_entry%%|*}"
  dependency_version="${dependency_entry#*|}"
  dependency_version="${dependency_version%%|*}"
  dependency_entrypoint="${dependency_entry##*|}"
  mkdir -p "${PROGRAMS_RUNTIME}/node_modules/${dependency}"
  printf '{\n  "name": "%s",\n  "version": "%s"\n}\n' \
    "${dependency}" "${dependency_version}" \
    >"${PROGRAMS_RUNTIME}/node_modules/${dependency}/package.json"
  mkdir -p "$(dirname "${PROGRAMS_RUNTIME}/node_modules/${dependency}/${dependency_entrypoint}")"
  printf 'export const fixture = true;\n' \
    >"${PROGRAMS_RUNTIME}/node_modules/${dependency}/${dependency_entrypoint}"
done
tar -C "${PROGRAMS_ROOT}" -czf "${RELEASE_ROOT}/dist/test-agent-programs.tar.gz" programs
printf 'fixture worker image\n' >"${RELEASE_ROOT}/dist/test-agent-opencode-worker_internal-linux-amd64.tar"
printf 'fixture it-tools image\n' >"${RELEASE_ROOT}/dist/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar"
printf 'fixture omni-tools image\n' >"${RELEASE_ROOT}/dist/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar"
cp "${ROOT_DIR}/deploy/internal/deploy-internal-release.sh" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/deploy-internal-frontend.sh" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/opencode-worker-docker.sh" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/deploy-python-libs.sh" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/tools/verify-python-libs.sh" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/ensure-opencode-runtime-gitignore.sh" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/opencode-runtime.gitignore" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/ensure-experience-workspace-content.sh" "${RELEASE_ROOT}/deploy/internal/"
cp -R "${ROOT_DIR}/deploy/internal/experience-workspace-template" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/verify-opencode-tool-runtime.sh" "${RELEASE_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/opencode-node-runtime.package.json" "${RELEASE_ROOT}/deploy/internal/"
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

# programs 包缺少 Tool 定义插件时必须在节点预校验阶段失败，不能等到 worker 重启后才暴露。
BAD_TOOL_RUNTIME_ROOT="${TMP_ROOT}/bad-tool-runtime-root"
BAD_TOOL_RELEASE_ROOT="${TMP_ROOT}/bad-tool-release-root"
BAD_TOOL_RELEASE_ARCHIVE="${TMP_ROOT}/test-agent-bad-tool-runtime-release.zip"
cp -R "${PROGRAMS_ROOT}" "${BAD_TOOL_RUNTIME_ROOT}"
cp -R "${RELEASE_ROOT}" "${BAD_TOOL_RELEASE_ROOT}"
rm -f "${BAD_TOOL_RUNTIME_ROOT}/programs/opencode/node_modules/@opencode-ai/plugin/package.json"
tar -C "${BAD_TOOL_RUNTIME_ROOT}" -czf \
  "${BAD_TOOL_RELEASE_ROOT}/dist/test-agent-programs.tar.gz" programs
(cd "${BAD_TOOL_RELEASE_ROOT}" && zip -qr "${BAD_TOOL_RELEASE_ARCHIVE}" .)
(cd "${TMP_ROOT}" && shasum -a 256 "$(basename "${BAD_TOOL_RELEASE_ARCHIVE}")" \
  >"$(basename "${BAD_TOOL_RELEASE_ARCHIVE}").sha256")
if bad_tool_output="$(bash "${DEPLOY_SCRIPT}" backend \
  --config-dir "${CONFIG_4}" \
  --release-archive "${BAD_TOOL_RELEASE_ARCHIVE}" \
  --backend-host 122.233.30.4 \
  --validate-only 2>&1)"; then
  echo 'Validation unexpectedly accepted programs without @opencode-ai/plugin' >&2
  exit 1
fi
grep -Fq 'missing required Tool runtime dependencies' <<<"${bad_tool_output}"

# Flyway SQL 位于外置 persistence JAR；即使 app JAR、ZIP 和节点配置都合法，错误字节也必须在启动前拒绝。
BAD_RELEASE_ROOT="${TMP_ROOT}/bad-release-root"
BAD_RELEASE_ARCHIVE="${TMP_ROOT}/test-agent-bad-flyway-release.zip"
BAD_PERSISTENCE_ROOT="${TMP_ROOT}/bad-persistence-jar-root"
cp -R "${RELEASE_ROOT}" "${BAD_RELEASE_ROOT}"
mkdir -p "${BAD_PERSISTENCE_ROOT}/db/migration"
printf 'wrong enterprise migration fixture\n' \
  >"${BAD_PERSISTENCE_ROOT}/db/migration/V20260728160800__create_toolbox_click_tracking.sql"
rm -f "${BAD_RELEASE_ROOT}/dist/backend/lib/test-agent-persistence-0.1.0-SNAPSHOT.jar"
(cd "${BAD_PERSISTENCE_ROOT}" && zip -qr \
  "${BAD_RELEASE_ROOT}/dist/backend/lib/test-agent-persistence-0.1.0-SNAPSHOT.jar" .)
(cd "${BAD_RELEASE_ROOT}" && zip -qr "${BAD_RELEASE_ARCHIVE}" .)
(cd "${TMP_ROOT}" && shasum -a 256 "$(basename "${BAD_RELEASE_ARCHIVE}")" \
  >"$(basename "${BAD_RELEASE_ARCHIVE}").sha256")
if bad_flyway_output="$(bash "${DEPLOY_SCRIPT}" backend \
  --config-dir "${CONFIG_4}" \
  --release-archive "${BAD_RELEASE_ARCHIVE}" \
  --backend-host 122.233.30.4 \
  --validate-only 2>&1)"; then
  echo 'Validation unexpectedly accepted a persistence JAR with the wrong Flyway migration' >&2
  exit 1
fi
grep -Fq 'contains the wrong release Flyway migration' <<<"${bad_flyway_output}"

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

# 已部署 release 的源码、包摘要和 worker 指纹一致时，先验真实 runtime/容器，再补写缺失门禁状态。
BASELINE_RELEASE_ROOT="${TMP_ROOT}/baseline-release-root"
BASELINE_RELEASE_ARCHIVE="${TMP_ROOT}/test-agent-baseline-reuse-release.zip"
BASELINE_WORKER_FINGERPRINT="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
cp -R "${REUSE_RELEASE_ROOT}" "${BASELINE_RELEASE_ROOT}"
sed -i.bak \
  "s/TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=fixture-worker/TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=${BASELINE_WORKER_FINGERPRINT}/" \
  "${BASELINE_RELEASE_ROOT}/deploy/internal/release-components.env"
rm -f "${BASELINE_RELEASE_ROOT}/deploy/internal/release-components.env.bak"
printf '%s\n' \
  'TEST_AGENT_RELEASE_WORKER_RUNTIME_BASELINE_SOURCE_COMMIT=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
  'TEST_AGENT_RELEASE_WORKER_RUNTIME_BASELINE_RELEASE_SHA256=cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc' \
  >>"${BASELINE_RELEASE_ROOT}/deploy/internal/release-components.env"
(cd "${BASELINE_RELEASE_ROOT}" && zip -qr "${BASELINE_RELEASE_ARCHIVE}" .)
mkdir -p "${REUSE_INSTALL_ROOT}/programs/bin" "${REUSE_INSTALL_ROOT}/programs/codex/bin"
cp -R "${PROGRAMS_RUNTIME}" "${REUSE_INSTALL_ROOT}/programs/opencode"
mkdir -p "${REUSE_INSTALL_ROOT}/programs/opencode/bin"
printf 'fixture manager\n' >"${REUSE_INSTALL_ROOT}/programs/bin/opencode-manager"
printf 'fixture opencode\n' >"${REUSE_INSTALL_ROOT}/programs/opencode/bin/opencode"
printf 'fixture codex\n' >"${REUSE_INSTALL_ROOT}/programs/codex/bin/codex-official"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'if [[ "$*" == *".State.Running"* ]]; then printf "true\n"; else printf "healthy\n"; fi' \
  >"${REUSE_BIN}/docker"
printf '%s\n' '#!/usr/bin/env bash' 'exit 1' >"${REUSE_BIN}/systemctl"
chmod +x "${REUSE_BIN}/docker" "${REUSE_BIN}/systemctl"
if baseline_output="$(PATH="${REUSE_BIN}:${PATH}" \
  TEST_AGENT_SYSTEMD_UNIT_DIR="${TMP_ROOT}/systemd" \
  bash "${ROOT_DIR}/deploy/internal/deploy-internal-release.sh" \
    --archive "${BASELINE_RELEASE_ARCHIVE}" \
    --extract-dir "${TMP_ROOT}/baseline-extract" \
    --install-root "${REUSE_INSTALL_ROOT}" \
    --docker-env "${REUSE_INSTALL_ROOT}/config/docker.env" \
    --skip-frontend 2>&1)"; then
  echo 'Baseline recovery fixture unexpectedly completed the full deployment' >&2
  exit 1
fi
grep -Fq 'Recovered installed worker runtime fingerprint from deployed release baseline' \
  <<<"${baseline_output}"
grep -Fxq "TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=${BASELINE_WORKER_FINGERPRINT}" \
  "${REUSE_INSTALL_ROOT}/config/release-component-state.env"
grep -Fxq 'TEST_AGENT_RELEASE_WORKER_RUNTIME_BASELINE_SOURCE_COMMIT=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
  "${REUSE_INSTALL_ROOT}/config/release-component-state.env"

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
  "${VERIFY_INSTALL_ROOT}/dist/backend/lib" "${VERIFY_INSTALL_ROOT}/deploy/internal" \
  "${VERIFY_INSTALL_ROOT}/programs" "${VERIFY_BIN}"
cp "${CONFIG_4}/backend.env" "${VERIFY_INSTALL_ROOT}/config/backend.env"
cp "${CONFIG_4}/docker.env" "${VERIFY_INSTALL_ROOT}/config/docker.env"
cp "${RELEASE_ROOT}/dist/backend/test-agent-app.jar" \
  "${VERIFY_INSTALL_ROOT}/dist/backend/test-agent-app.jar"
cp "${RELEASE_ROOT}/dist/backend/lib/test-agent-persistence-0.1.0-SNAPSHOT.jar" \
  "${VERIFY_INSTALL_ROOT}/dist/backend/lib/"
cp "${ROOT_DIR}/deploy/internal/verify-opencode-tool-runtime.sh" \
  "${VERIFY_INSTALL_ROOT}/deploy/internal/"
cp "${ROOT_DIR}/deploy/internal/opencode-node-runtime.package.json" \
  "${VERIFY_INSTALL_ROOT}/deploy/internal/"
cp -R "${PROGRAMS_RUNTIME}" "${VERIFY_INSTALL_ROOT}/programs/opencode"
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

echo 'Two-backend per-node validation, Flyway persistence JAR gate, toolbox connectivity, embedded RSA, secret redaction and manager log compatibility verified'
