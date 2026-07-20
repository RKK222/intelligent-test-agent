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
  "${INSTALL_ROOT}/dist/backend/lib" \
  "${INSTALL_ROOT}/frontend/assets" \
  "${NGINX_HOME}/sbin" \
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

JAR_ROOT="${TMP_ROOT}/jar-root"
mkdir -p "${JAR_ROOT}/BOOT-INF/classes"
printf '%s\n' \
  '-----BEGIN PRIVATE KEY-----' \
  'rsa-private-key-raw' \
  '-----END PRIVATE KEY-----' \
  >"${JAR_ROOT}/BOOT-INF/classes/rsa-private.key"
(cd "${JAR_ROOT}" && zip -qr "${INSTALL_ROOT}/dist/backend/test-agent-app.jar" .)
printf 'fake-library\n' >"${INSTALL_ROOT}/dist/backend/lib/example.jar"

printf '%s\n' \
  'TEST_AGENT_NGINX_MODE=single' \
  'TEST_AGENT_NGINX_BACKENDS=122.233.30.114:8080' \
  "TEST_AGENT_NGINX_BIN=${NGINX_HOME}/sbin/nginx" \
  "TEST_AGENT_NGINX_PREFIX=${NGINX_HOME}" \
  "TEST_AGENT_NGINX_MAIN_CONF=${NGINX_HOME}/conf/nginx.conf" \
  "TEST_AGENT_NGINX_CONF_PATH=${NGINX_HOME}/conf/test-agent.conf" \
  'TEST_AGENT_NGINX_PRIVATE_TOKEN=nginx-token-raw' \
  >"${INSTALL_ROOT}/config/nginx.env"
printf 'events {}\nhttp { include %s; }\n' "${NGINX_HOME}/conf/test-agent.conf" \
  >"${NGINX_HOME}/conf/nginx.conf"
printf 'server { listen 80; location /api { proxy_pass http://122.233.30.114:8080; } }\n' \
  >"${NGINX_HOME}/conf/test-agent.conf"
printf '<html>deployed frontend</html>\n' >"${INSTALL_ROOT}/frontend/index.html"
printf 'frontend asset\n' >"${INSTALL_ROOT}/frontend/assets/app.js"
printf 'cookie=frontend-cookie-raw\n' >"${NGINX_HOME}/logs/access.log"
printf 'authorization=frontend-authorization-raw\n' >"${NGINX_HOME}/logs/error.log"

cat >"${NGINX_HOME}/sbin/nginx" <<EOF
#!/usr/bin/env bash
set -euo pipefail
if [[ " \$* " == *" -T "* ]]; then
  printf '# configuration file %s:\n' '${NGINX_HOME}/conf/nginx.conf'
  cat '${NGINX_HOME}/conf/nginx.conf'
  printf '# configuration file %s:\n' '${NGINX_HOME}/conf/test-agent.conf'
  cat '${NGINX_HOME}/conf/test-agent.conf'
fi
EOF
chmod +x "${NGINX_HOME}/sbin/nginx"

cat >"${FAKE_BIN}/systemctl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "show" ]]; then
  printf '%s\n' \
    'LoadState=loaded' \
    'ActiveState=active' \
    'MainPID=1234' \
    'ExecStart={ path=/usr/bin/java ; argv[]=/usr/bin/java -jar /data/testagent/dist/backend/test-agent-app.jar ; }'
else
  printf '%s\n' \
    '[Service]' \
    'Environment=UNIT_SECRET=unit-secret-raw' \
    'ExecStart=/usr/bin/java -jar /data/testagent/dist/backend/test-agent-app.jar'
fi
EOF
cat >"${FAKE_BIN}/journalctl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'backend business log token=journal-token-raw\n'
EOF
cat >"${FAKE_BIN}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
case "${1:-}" in
  --version) printf 'Docker version test\n' ;;
  ps) printf 'test-agent-opencode-worker running\n' ;;
  images) printf 'test-agent-opencode-worker:internal image-id\n' ;;
  inspect) printf '[{"Config":{"Env":["TOKEN=docker-inspect-token-raw"]}}]\n' ;;
  logs) printf 'worker business log cookie=docker-log-cookie-raw\n' ;;
  *) printf 'fake docker command\n' ;;
esac
EOF
cat >"${FAKE_BIN}/ss" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'LISTEN 0 128 0.0.0.0:8080 0.0.0.0:* users:(("java",pid=1234,fd=1))\n'
EOF
cat >"${FAKE_BIN}/java" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'openjdk version "21-test"\n' >&2
EOF
chmod +x "${FAKE_BIN}/systemctl" "${FAKE_BIN}/journalctl" \
  "${FAKE_BIN}/docker" "${FAKE_BIN}/ss" "${FAKE_BIN}/java"

COLLECTOR="${ROOT_DIR}/deploy/internal/collect-multi-backend-context.sh"
if PATH="${FAKE_BIN}:${PATH}" bash "${COLLECTOR}" backend \
  --output-dir "${OUTPUT_DIR}" \
  --install-root "${INSTALL_ROOT}" \
  --nginx-home "${NGINX_HOME}" \
  --node-label test-backend \
  --skip-network; then
  echo 'Collector unexpectedly accepted a sensitive run without explicit consent' >&2
  exit 1
fi

PATH="${FAKE_BIN}:${PATH}" bash "${COLLECTOR}" backend \
  --include-sensitive \
  --output-dir "${OUTPUT_DIR}" \
  --install-root "${INSTALL_ROOT}" \
  --nginx-home "${NGINX_HOME}" \
  --node-label test-backend \
  --all-logs \
  --skip-network

BACKEND_ARCHIVE="$(find "${OUTPUT_DIR}" -maxdepth 1 -type f \
  -name 'test-agent-context-SENSITIVE-backend-test-backend-*.tar.gz' | head -n 1)"
test -n "${BACKEND_ARCHIVE}"
if command -v sha256sum >/dev/null 2>&1; then
  (cd "${OUTPUT_DIR}" && sha256sum -c "$(basename "${BACKEND_ARCHIVE}").sha256")
else
  (cd "${OUTPUT_DIR}" && shasum -a 256 -c "$(basename "${BACKEND_ARCHIVE}").sha256")
fi
BACKEND_EXTRACT="${TMP_ROOT}/backend-extract"
mkdir -p "${BACKEND_EXTRACT}"
tar -C "${BACKEND_EXTRACT}" -xzf "${BACKEND_ARCHIVE}"
grep -Fq 'backend-db-password-raw' \
  "${BACKEND_EXTRACT}/files/data/testagent/config/backend.env"
grep -Fq 'manager-token-raw' \
  "${BACKEND_EXTRACT}/files/data/testagent/config/docker.env"
grep -Fq 'rsa-private-key-raw' \
  "${BACKEND_EXTRACT}/files/data/testagent/dist/backend/rsa-private.key"
test -f "${BACKEND_EXTRACT}/files/data/testagent/dist/backend/test-agent-app.jar"
grep -Fq 'journal-token-raw' "${BACKEND_EXTRACT}/logs/test-agent-backend.log"
grep -Fq 'docker-inspect-token-raw' \
  "${BACKEND_EXTRACT}/commands/worker-inspect.json"
grep -Fq 'docker-log-cookie-raw' \
  "${BACKEND_EXTRACT}/logs/test-agent-opencode-worker.log"
grep -Fq 'unit-secret-raw' \
  "${BACKEND_EXTRACT}/commands/backend-systemd-unit.txt"

if stat -f '%Lp' "${BACKEND_ARCHIVE}" >/dev/null 2>&1; then
  test "$(stat -f '%Lp' "${BACKEND_ARCHIVE}")" = 600
else
  test "$(stat -c '%a' "${BACKEND_ARCHIVE}")" = 600
fi

PATH="${FAKE_BIN}:${PATH}" bash "${COLLECTOR}" frontend \
  --include-sensitive \
  --output-dir "${OUTPUT_DIR}" \
  --install-root "${INSTALL_ROOT}" \
  --nginx-home "${NGINX_HOME}" \
  --node-label test-frontend \
  --skip-network

FRONTEND_ARCHIVE="$(find "${OUTPUT_DIR}" -maxdepth 1 -type f \
  -name 'test-agent-context-SENSITIVE-frontend-test-frontend-*.tar.gz' | head -n 1)"
test -n "${FRONTEND_ARCHIVE}"
FRONTEND_EXTRACT="${TMP_ROOT}/frontend-extract"
mkdir -p "${FRONTEND_EXTRACT}"
tar -C "${FRONTEND_EXTRACT}" -xzf "${FRONTEND_ARCHIVE}"
grep -Fq 'nginx-token-raw' \
  "${FRONTEND_EXTRACT}/files/data/testagent/config/nginx.env"
grep -Fq '122.233.30.114:8080' \
  "${FRONTEND_EXTRACT}/commands/nginx-active-config.txt"
grep -Fq 'deployed frontend' \
  "${FRONTEND_EXTRACT}/files/data/testagent/frontend/index.html"
grep -Fq 'frontend-cookie-raw' \
  "${FRONTEND_EXTRACT}/files/data/apps/nginx/logs/access.log"

echo 'Sensitive backend/frontend deployment context collection verified'
