#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="$(mktemp -d)"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

INSTALL_ROOT="${TMP_ROOT}/data/testagent"
NGINX_HOME="${TMP_ROOT}/data/apps/nginx"
OUTPUT_DIR="${TMP_ROOT}/output"
FAKE_BIN="${TMP_ROOT}/bin"
mkdir -p \
  "${INSTALL_ROOT}/config" \
  "${INSTALL_ROOT}/data" \
  "${INSTALL_ROOT}/dist/backend" \
  "${INSTALL_ROOT}/frontend" \
  "${NGINX_HOME}/conf" \
  "${NGINX_HOME}/logs" \
  "${OUTPUT_DIR}" \
  "${FAKE_BIN}"

printf '%s\n' \
  'SPRING_PROFILES_ACTIVE=prod' \
  'TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.4' \
  'TEST_AGENT_DB_PASSWORD=backend-db-password-raw' \
  'TEST_AGENT_OPENCODE_MANAGER_TOKEN=manager-token-raw' \
  'TEST_AGENT_INTERNAL_PROXY_API_KEY=proxy-key-raw' \
  >"${INSTALL_ROOT}/config/backend.env"
printf '%s\n' \
  'TEST_AGENT_OPENCODE_MANAGER_TOKEN=manager-token-raw' \
  'OPENCODE_WORKER_PORT_START=4096' \
  'OPENCODE_WORKER_PORT_END=4115' \
  >"${INSTALL_ROOT}/config/docker.env"
printf 'test-agent-backend-122-233-30-4\n' >"${INSTALL_ROOT}/data/.serverid"
printf '122.233.30.4\n' >"${INSTALL_ROOT}/data/.serverhost"
printf 'jar-must-not-be-collected\n' >"${INSTALL_ROOT}/dist/backend/test-agent-app.jar"
printf 'frontend-must-not-be-collected\n' >"${INSTALL_ROOT}/frontend/index.html"
printf 'logs-must-not-be-collected\n' >"${NGINX_HOME}/logs/error.log"

printf '%s\n' \
  'TEST_AGENT_NGINX_MODE=single' \
  'TEST_AGENT_NGINX_BACKENDS=122.233.30.114:8080' \
  "TEST_AGENT_NGINX_MAIN_CONF=${NGINX_HOME}/conf/nginx.conf" \
  "TEST_AGENT_NGINX_CONF_PATH=${NGINX_HOME}/conf/test-agent.conf" \
  'TEST_AGENT_NGINX_PRIVATE_TOKEN=nginx-token-raw' \
  >"${INSTALL_ROOT}/config/nginx.env"
printf 'events {}\nhttp { include %s; }\n' "${NGINX_HOME}/conf/test-agent.conf" \
  >"${NGINX_HOME}/conf/nginx.conf"
printf 'server { listen 80; proxy_pass http://122.233.30.114:8080; }\n' \
  >"${NGINX_HOME}/conf/test-agent.conf"

cat >"${FAKE_BIN}/systemctl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' \
  '[Service]' \
  'Environment=UNIT_SECRET=unit-secret-raw' \
  'EnvironmentFile=/data/testagent/config/backend.env' \
  'ExecStart=/usr/bin/java -jar /data/testagent/dist/backend/test-agent-app.jar'
EOF
chmod +x "${FAKE_BIN}/systemctl"

COLLECTOR="${ROOT_DIR}/deploy/internal/collect-multi-backend-context.sh"
if PATH="${FAKE_BIN}:${PATH}" bash "${COLLECTOR}" backend \
  --output-dir "${OUTPUT_DIR}" \
  --install-root "${INSTALL_ROOT}" \
  --nginx-home "${NGINX_HOME}" \
  --node-label test-backend; then
  echo 'Collector unexpectedly accepted raw secrets without explicit consent' >&2
  exit 1
fi

PATH="${FAKE_BIN}:${PATH}" bash "${COLLECTOR}" backend \
  --include-sensitive \
  --output-dir "${OUTPUT_DIR}" \
  --install-root "${INSTALL_ROOT}" \
  --nginx-home "${NGINX_HOME}" \
  --node-label test-backend

BACKEND_ARCHIVE="$(find "${OUTPUT_DIR}" -maxdepth 1 -type f \
  -name 'test-agent-config-SENSITIVE-backend-test-backend-*.tar.gz' | head -n 1)"
test -n "${BACKEND_ARCHIVE}"
test "$(stat -f '%z' "${BACKEND_ARCHIVE}" 2>/dev/null || stat -c '%s' "${BACKEND_ARCHIVE}")" -le 1048576
BACKEND_EXTRACT="${TMP_ROOT}/backend-extract"
mkdir -p "${BACKEND_EXTRACT}"
tar -C "${BACKEND_EXTRACT}" -xzf "${BACKEND_ARCHIVE}"
grep -Fq 'backend-db-password-raw' \
  "${BACKEND_EXTRACT}/files/data/testagent/config/backend.env"
grep -Fq 'manager-token-raw' \
  "${BACKEND_EXTRACT}/files/data/testagent/config/docker.env"
grep -Fq 'unit-secret-raw' \
  "${BACKEND_EXTRACT}/files/etc/systemd/system/test-agent-backend.effective.service"
test -f "${BACKEND_EXTRACT}/files/data/testagent/data/.serverid"
test -f "${BACKEND_EXTRACT}/files/data/testagent/data/.serverhost"
test ! -e "${BACKEND_EXTRACT}/files/data/testagent/dist"
test ! -e "${BACKEND_EXTRACT}/files/data/testagent/frontend"
test ! -e "${BACKEND_EXTRACT}/files/data/apps/nginx/logs"
if find "${BACKEND_EXTRACT}" -type f \( -name '*.jar' -o -name '*.log' -o -name '*rsa*' \) \
  | grep -q .; then
  echo 'Backend configuration archive contains a forbidden large or log file' >&2
  exit 1
fi

PATH="${FAKE_BIN}:${PATH}" bash "${COLLECTOR}" frontend \
  --include-sensitive \
  --output-dir "${OUTPUT_DIR}" \
  --install-root "${INSTALL_ROOT}" \
  --nginx-home "${NGINX_HOME}" \
  --node-label test-frontend

FRONTEND_ARCHIVE="$(find "${OUTPUT_DIR}" -maxdepth 1 -type f \
  -name 'test-agent-config-SENSITIVE-frontend-test-frontend-*.tar.gz' | head -n 1)"
test -n "${FRONTEND_ARCHIVE}"
test "$(stat -f '%z' "${FRONTEND_ARCHIVE}" 2>/dev/null || stat -c '%s' "${FRONTEND_ARCHIVE}")" -le 1048576
FRONTEND_EXTRACT="${TMP_ROOT}/frontend-extract"
mkdir -p "${FRONTEND_EXTRACT}"
tar -C "${FRONTEND_EXTRACT}" -xzf "${FRONTEND_ARCHIVE}"
grep -Fq 'nginx-token-raw' \
  "${FRONTEND_EXTRACT}/files/data/testagent/config/nginx.env"
grep -Fq '122.233.30.114:8080' \
  "${FRONTEND_EXTRACT}/files/data/apps/nginx/conf/test-agent.conf"
test -f "${FRONTEND_EXTRACT}/files/data/apps/nginx/conf/nginx.conf"
test ! -e "${FRONTEND_EXTRACT}/files/data/testagent/frontend"
test ! -e "${FRONTEND_EXTRACT}/files/data/apps/nginx/logs"

if command -v sha256sum >/dev/null 2>&1; then
  (cd "${OUTPUT_DIR}" && sha256sum -c "$(basename "${BACKEND_ARCHIVE}").sha256")
else
  (cd "${OUTPUT_DIR}" && shasum -a 256 -c "$(basename "${BACKEND_ARCHIVE}").sha256")
fi

# 构造不可压缩的大配置，确认超过 1 MiB 时脚本删除归档并明确失败。
LARGE_ROOT="${TMP_ROOT}/large-testagent"
LARGE_OUTPUT="${TMP_ROOT}/large-output"
mkdir -p "${LARGE_ROOT}/config" "${LARGE_ROOT}/data" "${LARGE_OUTPUT}"
dd if=/dev/urandom bs=1048576 count=2 2>/dev/null | base64 \
  >"${LARGE_ROOT}/config/backend.env"
printf 'TEST_AGENT_OPENCODE_MANAGER_TOKEN=value\n' >"${LARGE_ROOT}/config/docker.env"
if PATH="${FAKE_BIN}:${PATH}" bash "${COLLECTOR}" backend \
  --include-sensitive \
  --output-dir "${LARGE_OUTPUT}" \
  --install-root "${LARGE_ROOT}" \
  --nginx-home "${NGINX_HOME}" \
  --node-label oversized; then
  echo 'Collector unexpectedly accepted an archive larger than 1 MiB' >&2
  exit 1
fi
test -z "$(find "${LARGE_OUTPUT}" -maxdepth 1 -type f -name '*.tar.gz' -print -quit)"

echo 'Configuration-only backend/frontend collection and 1 MiB limit verified'
