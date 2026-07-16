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
grep -Fxq 'TEST_AGENT_DB_URL=jdbc:postgresql://122.233.30.147:5432/postgres' "${BACKEND_ENV}"
test "$(grep -c '^TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT=' "${BACKEND_ENV}")" = 1
grep -Fxq 'TEST_AGENT_OPENCODE_MANAGER_TOKEN=current-manager-token' "${DOCKER_ENV}"
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
http { include ${LOADED_DIR}/*.conf; }
EOF
cat >"${NGINX_HOME}/sbin/nginx" <<EOF
#!/usr/bin/env bash
set -euo pipefail
for arg in "\$@"; do
  if [[ "\${arg}" == "-T" ]]; then
    printf '# configuration file %s:\n' '${NGINX_HOME}/conf/nginx.conf'
    printf '# configuration file %s:\n' "\${TEST_AGENT_NGINX_CONF_PATH:-${LOADED_DIR}/existing.conf}"
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
bash "${ROOT_DIR}/deploy/internal/configure-nginx.sh" --env-file "${NGINX_ENV}"
grep -Fq 'server 122.233.30.114:8080 max_fails=3 fail_timeout=10s;' \
  "${LOADED_DIR}/test-agent-gateway.conf"

echo 'Single-backend backend.env, docker.env and custom Nginx configuration generation verified'
