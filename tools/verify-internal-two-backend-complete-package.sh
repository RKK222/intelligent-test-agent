#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_SCRIPT="${ROOT_DIR}/deploy/internal/package-two-backend-complete.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-complete-package-verify.XXXXXX")"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

sha256_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

write_checksum() {
  local file="$1"
  printf '%s  %s\n' "$(sha256_digest "${file}")" "$(basename "${file}")" \
    >"${file}.sha256"
}

RELEASE_ROOT="${TMP_ROOT}/release-root"
RELEASE_ARCHIVE="${TMP_ROOT}/test-agent-internal-release.zip"
NODES_DIR="${TMP_ROOT}/nodes"
OUTPUT_DIR="${TMP_ROOT}/output"
mkdir -p "${RELEASE_ROOT}/dist/backend/lib" "${RELEASE_ROOT}/deploy/internal" \
  "${RELEASE_ROOT}/.agents" \
  "${NODES_DIR}" "${OUTPUT_DIR}"
SIGNING_PRIVATE_KEY="${TMP_ROOT}/local-client-signing-private.pem"
SIGNING_PUBLIC_KEY="${TMP_ROOT}/local-client-signing-public.pem"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "${SIGNING_PRIVATE_KEY}" >/dev/null 2>&1
openssl pkey -in "${SIGNING_PRIVATE_KEY}" -pubout \
  -out "${SIGNING_PUBLIC_KEY}" >/dev/null 2>&1
SIGNING_PUBLIC_KEY_BASE64="$(openssl base64 -A -in "${SIGNING_PUBLIC_KEY}")"

JAR_ROOT="${TMP_ROOT}/jar-root"
mkdir -p "${JAR_ROOT}/BOOT-INF/classes"
printf 'fixture-rsa-private-key\n' >"${JAR_ROOT}/BOOT-INF/classes/rsa-private.key"
(cd "${JAR_ROOT}" && zip -qr "${RELEASE_ROOT}/dist/backend/test-agent-app.jar" .)
PERSISTENCE_JAR_ROOT="${TMP_ROOT}/persistence-jar-root"
mkdir -p "${PERSISTENCE_JAR_ROOT}/db"
# 使用当前完整迁移目录生成夹具，使完整包门禁能覆盖主链、兼容链、PostgreSQL 专用资源与 ClickHouse DDL。
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
printf 'frontend\n' >"${RELEASE_ROOT}/dist/test-agent-frontend-dist.tar.gz"
printf 'programs\n' >"${RELEASE_ROOT}/dist/test-agent-programs.tar.gz"
printf 'worker\n' >"${RELEASE_ROOT}/dist/test-agent-opencode-worker_internal-linux-amd64.tar"
printf 'it-tools\n' >"${RELEASE_ROOT}/dist/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar"
write_checksum "${RELEASE_ROOT}/dist/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar"
printf 'omni-tools\n' >"${RELEASE_ROOT}/dist/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar"
write_checksum "${RELEASE_ROOT}/dist/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar"
printf 'toolbox-source\n' >"${RELEASE_ROOT}/dist/test-agent-toolbox-source.tar.gz"
write_checksum "${RELEASE_ROOT}/dist/test-agent-toolbox-source.tar.gz"
printf 'toolbox-catalog\n' >"${RELEASE_ROOT}/dist/toolbox-catalog-v1.json"
write_checksum "${RELEASE_ROOT}/dist/toolbox-catalog-v1.json"
printf '#!/usr/bin/env bash\n' >"${RELEASE_ROOT}/deploy/internal/deploy-multi-backend-node.sh"
cp "${ROOT_DIR}/deploy/internal/run-analytics-clickhouse-backfill.sh" \
  "${RELEASE_ROOT}/deploy/internal/run-analytics-clickhouse-backfill.sh"
cp "${ROOT_DIR}/deploy/internal/toolbox.env.example" "${RELEASE_ROOT}/deploy/internal/toolbox.env.example"
printf '#!/usr/bin/env bash\n' >"${RELEASE_ROOT}/deploy/internal/toolbox-docker.sh"
printf '#!/usr/bin/env bash\n' >"${RELEASE_ROOT}/deploy/internal/diagnose-toolbox.sh"
printf '%s\n' \
  'TEST_AGENT_RELEASE_COMPONENT_MANIFEST_VERSION=1' \
  'TEST_AGENT_RELEASE_WORKER_RUNTIME=included' \
  'TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=fixture-worker' \
  'TEST_AGENT_RELEASE_TOOLBOX=included' \
  'TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT=fixture-toolbox' \
  'TEST_AGENT_RELEASE_LOBEHUB=disabled' \
  'TEST_AGENT_RELEASE_LOBEHUB_VERSION=none' \
  >"${RELEASE_ROOT}/deploy/internal/release-components.env"
for session_log in "${ROOT_DIR}"/.agents/session-log*.md; do
  cp "${session_log}" "${RELEASE_ROOT}/.agents/$(basename "${session_log}")"
done
(cd "${RELEASE_ROOT}" && zip -qr "${RELEASE_ARCHIVE}" .)
write_checksum "${RELEASE_ARCHIVE}"

create_node_archive() {
  local node_dir="$1"
  local archive_name="$2"
  local config_name="$3"
  local root="${TMP_ROOT}/node-${node_dir}"
  mkdir -p "${root}/${node_dir}/config"
  printf '#!/usr/bin/env bash\n' >"${root}/${node_dir}/deploy-multi-backend-node.sh"
  printf '# fixture deployment guide\n' >"${root}/${node_dir}/MULTI-BACKEND.md"
  if [[ "${config_name}" == backend.env ]]; then
    printf '%s\n' \
      'TEST_AGENT_XXL_JOB_MYSQL_URL=jdbc:mysql://122.210.106.43:3306/xxl_job?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai' \
      'TEST_AGENT_XXL_JOB_MYSQL_USERNAME=root' \
      'TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=mysql-password-must-not-print' \
      'TEST_AGENT_XXL_JOB_ACCESS_TOKEN=access-token-must-not-print-0123456789' \
      >"${root}/${node_dir}/config/backend.env"
    printf 'TEST_AGENT_OPENCODE_MANAGER_TOKEN=must-not-print\n' \
      >"${root}/${node_dir}/config/docker.env"
  elif [[ "${config_name}" == nginx.env ]]; then
    printf 'SENSITIVE_VALUE=must-not-print\n' >"${root}/${node_dir}/config/nginx.env"
    printf 'TEST_AGENT_NGINX_TERMINAL_ROUTES=server-a=122.233.30.4:8080,server-b=122.233.30.114:8080\n' \
      >>"${root}/${node_dir}/config/nginx.env"
    printf 'TEST_AGENT_NGINX_XXL_JOB_ADMINS=122.233.30.4:18080,122.233.30.114:18080\n' \
      >>"${root}/${node_dir}/config/nginx.env"
  fi
  tar -C "${root}" -czf "${NODES_DIR}/${archive_name}" "${node_dir}"
  write_checksum "${NODES_DIR}/${archive_name}"
}

create_node_archive \
  test-agent-two-backend-122.233.30.4 \
  test-agent-two-backend-122.233.30.4-SENSITIVE.tar.gz \
  backend.env
create_node_archive \
  test-agent-two-backend-122.233.30.114 \
  test-agent-two-backend-122.233.30.114-SENSITIVE.tar.gz \
  backend.env
create_node_archive \
  test-agent-two-backend-122.233.30.2 \
  test-agent-two-backend-122.233.30.2.tar.gz \
  nginx.env
run_package() {
  TEST_AGENT_LOCAL_CLIENT_SIGNING_PUBLIC_KEY="${SIGNING_PUBLIC_KEY}" \
    bash "${PACKAGE_SCRIPT}" \
    --release-archive "${RELEASE_ARCHIVE}" \
    --nodes-dir "${NODES_DIR}" \
    --output-dir "${OUTPUT_DIR}"
}

if bash "${PACKAGE_SCRIPT}" \
  --release-archive "${RELEASE_ARCHIVE}" \
  --nodes-dir "${NODES_DIR}" \
  --output-dir "${OUTPUT_DIR}" >/dev/null 2>&1; then
  echo "Complete package unexpectedly accepted a missing local-client signing public key" >&2
  exit 1
fi
output="$(run_package 2>&1)"
if grep -Fq 'must-not-print' <<<"${output}"; then
  echo "Complete package output leaked node configuration" >&2
  exit 1
fi
grep -Fq 'test-agent-two-backend-complete.zip' <<<"${output}"

BUNDLE="${OUTPUT_DIR}/test-agent-two-backend-complete.zip"
CHECKSUM="${BUNDLE}.sha256"
test -s "${BUNDLE}"
test -s "${CHECKSUM}"
(cd "${OUTPUT_DIR}" && shasum -a 256 -c "$(basename "${CHECKSUM}")") >/dev/null
unzip -tq "${BUNDLE}" >/dev/null

listing="$(unzip -Z1 "${BUNDLE}")"
grep -Fxq 'test-agent-two-backend-complete/START-HERE.md' <<<"${listing}"
grep -Fxq 'test-agent-two-backend-complete/deploy-node-common.sh' <<<"${listing}"
grep -Fxq 'test-agent-two-backend-complete/deploy-backend-node.sh' <<<"${listing}"
grep -Fxq 'test-agent-two-backend-complete/deploy-frontend-node.sh' <<<"${listing}"
grep -Fxq 'test-agent-two-backend-complete/archive-common.sh' <<<"${listing}"
grep -Fxq 'test-agent-two-backend-complete/init-backend-node-config.sh' <<<"${listing}"
grep -Fxq 'test-agent-two-backend-complete/register-backend-on-frontend.sh' <<<"${listing}"
grep -Fxq 'test-agent-two-backend-complete/test-agent-internal-release.zip' <<<"${listing}"
grep -Fxq 'test-agent-two-backend-complete/nodes/test-agent-two-backend-122.233.30.4-SENSITIVE.tar.gz' <<<"${listing}"
grep -Fxq 'test-agent-two-backend-complete/nodes/test-agent-two-backend-122.233.30.114-SENSITIVE.tar.gz' <<<"${listing}"
grep -Fxq 'test-agent-two-backend-complete/nodes/test-agent-two-backend-122.233.30.2.tar.gz' <<<"${listing}"
START_HERE="${TMP_ROOT}/START-HERE.md"
unzip -p "${BUNDLE}" 'test-agent-two-backend-complete/START-HERE.md' >"${START_HERE}"
grep -Fq 'V20260728160800__create_toolbox_click_tracking.sql' "${START_HERE}"
cmp -s "${ROOT_DIR}/deploy/internal/MULTI-BACKEND.md" "${START_HERE}" || {
  echo 'Complete package START-HERE.md differs from the current multi-backend runbook' >&2
  exit 1
}
INNER_RELEASE="${TMP_ROOT}/inner-release.zip"
unzip -p "${BUNDLE}" 'test-agent-two-backend-complete/test-agent-internal-release.zip' >"${INNER_RELEASE}"
inner_listing="$(unzip -Z1 "${INNER_RELEASE}")"
grep -Fxq 'deploy/internal/run-analytics-clickhouse-backfill.sh' <<<"${inner_listing}"
for session_log in "${ROOT_DIR}"/.agents/session-log*.md; do
  grep -Fxq ".agents/$(basename "${session_log}")" <<<"${inner_listing}"
done
if grep -Eq 'mysql_8\.4|deploy-mysql-node|122\.233\.30\.147-mysql' <<<"${listing}"; then
  echo "Platform bundle unexpectedly contains standalone MySQL artifacts" >&2
  exit 1
fi
if grep -Eq '202[0-9]|-v[0-9]+/' <<<"${listing}"; then
  echo "Fixed-name bundle unexpectedly contains a dated/versioned root" >&2
  exit 1
fi

# 旧节点包可以复用，但完整新包内必须只保留统一 server route 键。
FRONTEND_NODE_ARCHIVE="${TMP_ROOT}/frontend-node.tar.gz"
unzip -p "${BUNDLE}" \
  'test-agent-two-backend-complete/nodes/test-agent-two-backend-122.233.30.2.tar.gz' \
  >"${FRONTEND_NODE_ARCHIVE}"
frontend_nginx_env="$(tar -xOzf "${FRONTEND_NODE_ARCHIVE}" \
  'test-agent-two-backend-122.233.30.2/config/nginx.env')"
grep -Fq 'TEST_AGENT_NGINX_SERVER_ROUTES=server-a=122.233.30.4:8080,server-b=122.233.30.114:8080' \
  <<<"${frontend_nginx_env}"
grep -Fxq 'TEST_AGENT_NGINX_TOOLBOX_IT_TOOLS_UPSTREAM=122.233.30.4:18120,122.233.30.114:18120' \
  <<<"${frontend_nginx_env}"
grep -Fxq 'TEST_AGENT_NGINX_TOOLBOX_OMNI_TOOLS_UPSTREAM=122.233.30.4:18121,122.233.30.114:18121' \
  <<<"${frontend_nginx_env}"
if grep -Fq 'TEST_AGENT_NGINX_TERMINAL_ROUTES=' <<<"${frontend_nginx_env}"; then
  echo "Complete package unexpectedly retained the legacy terminal route key" >&2
  exit 1
fi

# 复用的旧后台节点包应在临时副本中自动补齐当前固定配置。
BACKEND_NODE_ARCHIVE="${TMP_ROOT}/backend-node.tar.gz"
unzip -p "${BUNDLE}" \
  'test-agent-two-backend-complete/nodes/test-agent-two-backend-122.233.30.4-SENSITIVE.tar.gz' \
  >"${BACKEND_NODE_ARCHIVE}"
backend_env="$(tar -xOzf "${BACKEND_NODE_ARCHIVE}" \
  'test-agent-two-backend-122.233.30.4/config/backend.env')"
docker_env="$(tar -xOzf "${BACKEND_NODE_ARCHIVE}" \
  'test-agent-two-backend-122.233.30.4/config/docker.env')"
toolbox_env="$(tar -xOzf "${BACKEND_NODE_ARCHIVE}" \
  'test-agent-two-backend-122.233.30.4/config/toolbox.env')"
node_deploy_script="$(tar -xOzf "${BACKEND_NODE_ARCHIVE}" \
  'test-agent-two-backend-122.233.30.4/deploy-multi-backend-node.sh')"
node_guide="$(tar -xOzf "${BACKEND_NODE_ARCHIVE}" \
  'test-agent-two-backend-122.233.30.4/MULTI-BACKEND.md')"
grep -Fxq 'TEST_AGENT_XXL_JOB_COOKIE_SECURE=false' <<<"${backend_env}"
grep -Fxq 'TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=true' <<<"${backend_env}"
grep -Fxq 'TEST_AGENT_MAX_PREVIEW_BYTES=5242880' <<<"${backend_env}"
grep -Fxq 'TEST_AGENT_UPLOAD_CHUNK_BYTES=262144' <<<"${backend_env}"
grep -Fxq 'TEST_AGENT_SKILLHUB_ENABLED=true' <<<"${backend_env}"
grep -Fxq 'TEST_AGENT_SKILLHUB_BASE_URL=http://ai-code.sdc.icbc/icbc/skill' <<<"${backend_env}"
grep -Fxq 'TEST_AGENT_SKILLHUB_ACCESS_KEY=__PRESERVE_FROM_INSTALLED_BACKEND_ENV__' \
  <<<"${backend_env}"
grep -Fxq \
  "TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_SIGNING_PUBLIC_KEY_BASE64=${SIGNING_PUBLIC_KEY_BASE64}" \
  <<<"${backend_env}"
BACKEND_114_NODE_ARCHIVE="${TMP_ROOT}/backend-114-node.tar.gz"
unzip -p "${BUNDLE}" \
  'test-agent-two-backend-complete/nodes/test-agent-two-backend-122.233.30.114-SENSITIVE.tar.gz' \
  >"${BACKEND_114_NODE_ARCHIVE}"
backend_114_env="$(tar -xOzf "${BACKEND_114_NODE_ARCHIVE}" \
  'test-agent-two-backend-122.233.30.114/config/backend.env')"
grep -Fxq \
  "TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_SIGNING_PUBLIC_KEY_BASE64=${SIGNING_PUBLIC_KEY_BASE64}" \
  <<<"${backend_114_env}"
grep -Fxq 'TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=true' <<<"${backend_114_env}"
grep -Fxq 'TEST_AGENT_SKILLHUB_ENABLED=true' <<<"${backend_114_env}"
grep -Fxq 'TEST_AGENT_SKILLHUB_BASE_URL=http://ai-code.sdc.icbc/icbc/skill' <<<"${backend_114_env}"
grep -Fxq 'TEST_AGENT_SKILLHUB_ACCESS_KEY=__PRESERVE_FROM_INSTALLED_BACKEND_ENV__' \
  <<<"${backend_114_env}"
grep -Fxq 'OPENCODE_WORKER_PORT_START=14096' <<<"${docker_env}"
grep -Fxq 'OPENCODE_WORKER_PORT_END=15095' <<<"${docker_env}"
grep -Fxq 'TEST_AGENT_TOOLBOX_BIND_ADDRESS=122.233.30.4' <<<"${toolbox_env}"
grep -Fq 'require_exact_value "${docker_env}" OPENCODE_WORKER_PORT_START 14096' \
  <<<"${node_deploy_script}"
grep -Fq 'require_exact_value "${docker_env}" OPENCODE_WORKER_PORT_END 15095' \
  <<<"${node_deploy_script}"
grep -Fq 'ANALYTICS_BACKFILL_PRIMARY_HOST="122.233.30.4"' <<<"${node_deploy_script}"
grep -Fq -- '--run-analytics-backfill' <<<"${node_deploy_script}"
grep -Fq '14096-15095' <<<"${node_guide}"

# 增量内层包省略未变化的 worker runtime（含 Manager/Codex/programs）和 toolbox 大制品时，
# 外层封装仍应接受，并原样保留 reuse 清单，不能强制重新塞回历史 tar。
rm -f \
  "${RELEASE_ROOT}/dist/test-agent-programs.tar.gz" \
  "${RELEASE_ROOT}/dist/test-agent-opencode-worker_internal-linux-amd64.tar" \
  "${RELEASE_ROOT}/dist/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar" \
  "${RELEASE_ROOT}/dist/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar.sha256" \
  "${RELEASE_ROOT}/dist/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar" \
  "${RELEASE_ROOT}/dist/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar.sha256" \
  "${RELEASE_ROOT}/dist/test-agent-toolbox-source.tar.gz" \
  "${RELEASE_ROOT}/dist/test-agent-toolbox-source.tar.gz.sha256" \
  "${RELEASE_ROOT}/dist/toolbox-catalog-v1.json" \
  "${RELEASE_ROOT}/dist/toolbox-catalog-v1.json.sha256"
sed -i.bak \
  -e 's/TEST_AGENT_RELEASE_WORKER_RUNTIME=included/TEST_AGENT_RELEASE_WORKER_RUNTIME=reuse/' \
  -e 's/TEST_AGENT_RELEASE_TOOLBOX=included/TEST_AGENT_RELEASE_TOOLBOX=reuse/' \
  "${RELEASE_ROOT}/deploy/internal/release-components.env"
rm -f "${RELEASE_ROOT}/deploy/internal/release-components.env.bak" "${RELEASE_ARCHIVE}" "${RELEASE_ARCHIVE}.sha256"
(cd "${RELEASE_ROOT}" && zip -qr "${RELEASE_ARCHIVE}" .)
write_checksum "${RELEASE_ARCHIVE}"
run_package >/dev/null
REUSED_INNER="${TMP_ROOT}/reused-inner-release.zip"
unzip -p "${BUNDLE}" 'test-agent-two-backend-complete/test-agent-internal-release.zip' >"${REUSED_INNER}"
reused_listing="$(unzip -Z1 "${REUSED_INNER}")"
if grep -Eq '^dist/(test-agent-programs|test-agent-opencode-worker|test-agent_(it|omni)-tools|test-agent-toolbox-source|toolbox-catalog)' <<<"${reused_listing}"; then
  echo 'Outer bundle unexpectedly restored reused runtime/toolbox artifacts' >&2
  exit 1
fi
grep -Fxq 'TEST_AGENT_RELEASE_WORKER_RUNTIME=reuse' \
  < <(unzip -p "${REUSED_INNER}" deploy/internal/release-components.env)
grep -Fxq 'TEST_AGENT_RELEASE_TOOLBOX=reuse' \
  < <(unzip -p "${REUSED_INNER}" deploy/internal/release-components.env)

# 第二次执行必须无交互覆盖固定文件名，不能生成日期或版本后缀的新包。
run_package >/dev/null
test "$(find "${OUTPUT_DIR}" -maxdepth 1 -type f -name 'test-agent-two-backend-complete*.zip' | wc -l | tr -d '[:space:]')" = 1

# 外层封装不能只相信内层 ZIP SHA；内层 persistence JAR 的企业 migration 字节错误时必须拒绝。
BAD_PERSISTENCE_ROOT="${TMP_ROOT}/bad-persistence-root"
mkdir -p "${BAD_PERSISTENCE_ROOT}/db/migration"
printf 'wrong enterprise migration fixture\n' \
  >"${BAD_PERSISTENCE_ROOT}/db/migration/V20260728160800__create_toolbox_click_tracking.sql"
rm -f "${RELEASE_ROOT}/dist/backend/lib/test-agent-persistence-0.1.0-SNAPSHOT.jar"
(cd "${BAD_PERSISTENCE_ROOT}" && zip -qr \
  "${RELEASE_ROOT}/dist/backend/lib/test-agent-persistence-0.1.0-SNAPSHOT.jar" .)
rm -f "${RELEASE_ARCHIVE}" "${RELEASE_ARCHIVE}.sha256"
(cd "${RELEASE_ROOT}" && zip -qr "${RELEASE_ARCHIVE}" .)
write_checksum "${RELEASE_ARCHIVE}"
if bad_flyway_output="$(run_package 2>&1)"; then
  echo 'Complete package unexpectedly accepted the wrong enterprise Flyway migration' >&2
  exit 1
fi
grep -Fq 'Inner release contains the wrong release Flyway migration' <<<"${bad_flyway_output}"

echo 'Fixed-name platform bundle, Flyway persistence JAR gate, session logs, node normalization, checksum, structure, MySQL separation, redaction and overwrite verified'
