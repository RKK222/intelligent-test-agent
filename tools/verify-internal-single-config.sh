#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="$(mktemp -d)"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

CONFIG_DIR="${TMP_ROOT}/config"
BACKEND_ENV="${CONFIG_DIR}/backend.env"
DOCKER_ENV="${CONFIG_DIR}/docker.env"
mkdir -p "${CONFIG_DIR}"
cat >"${BACKEND_ENV}" <<'EOF'
TEST_AGENT_DB_PASSWORD=current-db-password
TEST_AGENT_REDIS_PASSWORD=current-redis-password
TEST_AGENT_API_TOKEN=current-api-token
TEST_AGENT_OPENCODE_MANAGER_TOKEN=current-manager-token
TEST_AGENT_INTERNAL_PROXY_API_KEY=current-proxy-key
TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=current-xxl-mysql-password
TEST_AGENT_XXL_JOB_ACCESS_TOKEN=current-xxl-access-token
TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT=30s
TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT=10s
EOF
cat >"${DOCKER_ENV}" <<'EOF'
TEST_AGENT_OPENCODE_MANAGER_TOKEN=current-manager-token
EOF

bash "${ROOT_DIR}/deploy/internal/configure-single-deployment.sh" backend \
  --backend-env "${BACKEND_ENV}" \
  --docker-env "${DOCKER_ENV}" \
  --backend-template "${ROOT_DIR}/deploy/internal/backend.env.example" \
  --docker-template "${ROOT_DIR}/deploy/internal/env.example"

grep -Fxq 'TEST_AGENT_DB_PASSWORD=current-db-password' "${BACKEND_ENV}"
grep -Fxq 'TEST_AGENT_REDIS_PASSWORD=current-redis-password' "${BACKEND_ENV}"
grep -Fxq 'TEST_AGENT_API_TOKEN=current-api-token' "${BACKEND_ENV}"
grep -Fxq 'TEST_AGENT_OPENCODE_MANAGER_TOKEN=current-manager-token' "${BACKEND_ENV}"
grep -Fxq 'TEST_AGENT_INTERNAL_PROXY_API_KEY=current-proxy-key' "${BACKEND_ENV}"
grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=current-xxl-mysql-password' "${BACKEND_ENV}"
grep -Fxq 'TEST_AGENT_XXL_JOB_ACCESS_TOKEN=current-xxl-access-token' "${BACKEND_ENV}"
grep -Fxq 'TEST_AGENT_XXL_JOB_COOKIE_SECURE=false' "${BACKEND_ENV}"
grep -Fxq 'TEST_AGENT_CORS_ALLOWED_ORIGINS=http://mimo.sdc.cs.icbc:9996,http://122.233.30.2:9996' "${BACKEND_ENV}"
grep -Fxq 'TEST_AGENT_SERVER_TERMINAL_PUBLIC_WEBSOCKET_BASE_URL=' "${BACKEND_ENV}"
grep -Fxq 'TEST_AGENT_SERVER_TERMINAL_ALLOW_INSECURE_WEBSOCKET=true' "${BACKEND_ENV}"
if grep -q '^TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH=' "${BACKEND_ENV}"; then
  echo 'backend.env unexpectedly contains an external RSA private key path' >&2
  exit 1
fi
grep -Fxq 'TEST_AGENT_DB_URL=jdbc:postgresql://122.233.30.147:5432/postgres' "${BACKEND_ENV}"
test "$(grep -c '^TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT=' "${BACKEND_ENV}")" = 1
grep -Fxq 'TEST_AGENT_OPENCODE_MANAGER_TOKEN=current-manager-token' "${DOCKER_ENV}"
grep -Fxq 'VITE_TEST_AGENT_API_BASE_URL=' "${DOCKER_ENV}"
grep -Fxq 'OPENCODE_ALLOWED_CORS=http://mimo.sdc.cs.icbc:9996,http://122.233.30.2:9996' "${DOCKER_ENV}"
grep -Fxq 'OPENCODE_WORKER_PORT_START=4096' "${DOCKER_ENV}"
grep -Fxq 'OPENCODE_WORKER_PORT_END=4115' "${DOCKER_ENV}"
if grep -q '^TEST_AGENT_INTERNAL_PROXY_API_KEY=' "${DOCKER_ENV}"; then
  echo 'docker.env unexpectedly contains the internal proxy key' >&2
  exit 1
fi
test -n "$(find "${CONFIG_DIR}" -maxdepth 1 -name 'backend.env.bak.*' -print -quit)"
test -n "$(find "${CONFIG_DIR}" -maxdepth 1 -name 'docker.env.bak.*' -print -quit)"

NGINX_HOME="${TMP_ROOT}/data/apps/nginx"
NGINX_ENV="${CONFIG_DIR}/nginx.env"
LOADED_DIR="${NGINX_HOME}/conf/vhosts"
mkdir -p "${NGINX_HOME}/sbin" "${LOADED_DIR}"
cat >"${NGINX_HOME}/conf/nginx.conf" <<EOF
events {}
http {
  include ${LOADED_DIR}/*.conf;
}
EOF
cat >"${NGINX_HOME}/sbin/nginx" <<EOF
#!/usr/bin/env bash
set -euo pipefail
for arg in "\$@"; do
  if [[ "\${arg}" == "-T" ]]; then
    printf '# configuration file %s:\n' '${NGINX_HOME}/conf/nginx.conf'
    while IFS= read -r loaded_conf; do
      printf '# configuration file %s:\n' "\${loaded_conf}"
    done < <(find '${LOADED_DIR}' -maxdepth 1 -type f -name '*.conf' | sort)
  fi
done
EOF
chmod +x "${NGINX_HOME}/sbin/nginx"

bash "${ROOT_DIR}/deploy/internal/configure-single-deployment.sh" frontend \
  --nginx-env "${NGINX_ENV}" \
  --nginx-home "${NGINX_HOME}"

grep -Fxq "TEST_AGENT_NGINX_BIN=${NGINX_HOME}/sbin/nginx" "${NGINX_ENV}"
grep -Fxq "TEST_AGENT_NGINX_PREFIX=${NGINX_HOME}" "${NGINX_ENV}"
grep -Fxq "TEST_AGENT_NGINX_MAIN_CONF=${NGINX_HOME}/conf/nginx.conf" "${NGINX_ENV}"
grep -Fxq "TEST_AGENT_NGINX_CONF_PATH=${LOADED_DIR}/test-agent-gateway.conf" "${NGINX_ENV}"
grep -Fxq 'TEST_AGENT_NGINX_RELOAD_MODE=binary' "${NGINX_ENV}"
grep -Fxq 'TEST_AGENT_NGINX_SERVER_ROUTES=test-agent-backend-122-233-30-114=122.233.30.114:8080' "${NGINX_ENV}"
grep -Fxq 'TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS=9996' "${NGINX_ENV}"
grep -Fxq 'TEST_AGENT_NGINX_XXL_JOB_ADMINS=122.233.30.114:18080' "${NGINX_ENV}"
bash "${ROOT_DIR}/deploy/internal/configure-nginx.sh" --env-file "${NGINX_ENV}"
grep -Fq 'server 122.233.30.114:8080 max_fails=3 fail_timeout=10s;' \
  "${LOADED_DIR}/test-agent-gateway.conf"
grep -Fq 'server 122.233.30.114:18080 max_fails=3 fail_timeout=10s;' \
  "${LOADED_DIR}/test-agent-gateway.conf"
grep -Fq 'location /xxl-job-admin/ {' "${LOADED_DIR}/test-agent-gateway.conf"
grep -Fq 'listen 80;' "${LOADED_DIR}/test-agent-gateway.conf"
grep -Fq 'listen 9996;' "${LOADED_DIR}/test-agent-gateway.conf"

# 显式 include 单个文件时，同目录新文件并不会自动生效，自动探测必须拒绝该目录。
EXPLICIT_HOME="${TMP_ROOT}/data/apps/nginx-explicit"
EXPLICIT_ENV="${CONFIG_DIR}/nginx-explicit.env"
EXPLICIT_CONF="${EXPLICIT_HOME}/conf/existing.conf"
mkdir -p "${EXPLICIT_HOME}/sbin" "${EXPLICIT_HOME}/conf"
printf '# existing explicit include\n' >"${EXPLICIT_CONF}"
cat >"${EXPLICIT_HOME}/conf/nginx.conf" <<EOF
events {}
http { include ${EXPLICIT_CONF}; }
EOF
cat >"${EXPLICIT_HOME}/sbin/nginx" <<EOF
#!/usr/bin/env bash
set -euo pipefail
for arg in "\$@"; do
  if [[ "\${arg}" == "-T" ]]; then
    printf '# configuration file %s:\n' '${EXPLICIT_HOME}/conf/nginx.conf'
    printf '# configuration file %s:\n' '${EXPLICIT_CONF}'
  fi
done
EOF
chmod +x "${EXPLICIT_HOME}/sbin/nginx"

if bash "${ROOT_DIR}/deploy/internal/configure-single-deployment.sh" frontend \
  --nginx-env "${EXPLICIT_ENV}" \
  --nginx-home "${EXPLICIT_HOME}"; then
  echo 'Explicit single-file include was incorrectly treated as a wildcard directory' >&2
  exit 1
fi
test ! -e "${EXPLICIT_ENV}"

echo 'Single-backend backend.env, docker.env and custom Nginx configuration generation verified'
