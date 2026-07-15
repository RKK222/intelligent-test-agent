#!/usr/bin/env bash
set -euo pipefail

ARCHIVE="/data/0709/internal.zip"
EXTRACT_DIR="/data/0709/test-agent-internal-release"
INSTALL_ROOT="/data/testagent"
BACKEND_HOST="122.233.30.114"
FRONTEND_HOST="122.233.30.2"
FRONTEND_USER=""
FRONTEND_ROOT="/data/testagent"
BACKEND_SERVICE="test-agent-backend"
BACKEND_HEALTH_URL=""
BACKEND_READINESS_URL=""
FRONTEND_HEALTH_URL="http://122.233.30.2/health"
FRONTEND_URL="http://122.233.30.2/"
DOCKER_ENV="/data/testagent/config/docker.env"
EXPECTED_SERVER_ID=""
EXPECTED_SERVER_HOST=""
SKIP_FRONTEND=0
SKIP_WORKER=0
KEEP_EXTRACT=0
VALIDATE_ONLY=0
SYSTEMD_UNIT_DIR="${TEST_AGENT_SYSTEMD_UNIT_DIR:-/etc/systemd/system}"

usage() {
  cat <<'USAGE'
Usage: deploy/internal/deploy-internal-release.sh [options]

Deploy an enterprise internal release zip from a backend server. The default
matches the current single-backend enterprise deployment:
  - release zip: /data/0709/internal.zip
  - backend/worker server: local 122.233.30.114
  - frontend server: 122.233.30.2
  - install root: /data/testagent

Options:
  --archive <path>              Release zip path. Default: /data/0709/internal.zip.
  --extract-dir <path>          Temporary unzip directory. Default: /data/0709/test-agent-internal-release.
  --install-root <path>         Backend install root. Default: /data/testagent.
  --backend-host <host>         Local backend advertised host. Default: 122.233.30.114.
  --frontend-host <host>        Frontend SSH/SCP host. Default: 122.233.30.2.
  --frontend-user <user>        SSH user for frontend host. Default: current ssh config user.
  --frontend-root <path>        Frontend install root. Default: /data/testagent.
  --backend-service <name>      systemd service name. Default: test-agent-backend.
  --backend-health-url <url>    Backend health URL.
  --backend-readiness-url <url> Backend readiness URL.
  --frontend-health-url <url>   Frontend health URL.
  --frontend-url <url>          Frontend page URL.
  --docker-env <path>           Worker docker.env path. Default: /data/testagent/config/docker.env.
  --expected-server-id <id>     Expected /data/testagent/data/.serverid value.
  --expected-server-host <host> Expected /data/testagent/data/.serverhost value.
  --skip-frontend              Do not scp or reload frontend.
  --skip-worker                Do not docker load or restart opencode-worker.
  --keep-extract               Keep extracted temporary files after success.
  --validate-only              Only unzip and validate release artifacts, without deploying.
  -h, --help                   Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --archive)
      ARCHIVE="$2"
      shift 2
      ;;
    --extract-dir)
      EXTRACT_DIR="$2"
      shift 2
      ;;
    --install-root)
      INSTALL_ROOT="$2"
      shift 2
      ;;
    --backend-host)
      BACKEND_HOST="$2"
      shift 2
      ;;
    --frontend-host)
      FRONTEND_HOST="$2"
      shift 2
      ;;
    --frontend-user)
      FRONTEND_USER="$2"
      shift 2
      ;;
    --frontend-root)
      FRONTEND_ROOT="$2"
      shift 2
      ;;
    --backend-service)
      BACKEND_SERVICE="$2"
      shift 2
      ;;
    --backend-health-url)
      BACKEND_HEALTH_URL="$2"
      shift 2
      ;;
    --backend-readiness-url)
      BACKEND_READINESS_URL="$2"
      shift 2
      ;;
    --frontend-health-url)
      FRONTEND_HEALTH_URL="$2"
      shift 2
      ;;
    --frontend-url)
      FRONTEND_URL="$2"
      shift 2
      ;;
    --docker-env)
      DOCKER_ENV="$2"
      shift 2
      ;;
    --expected-server-id)
      EXPECTED_SERVER_ID="$2"
      shift 2
      ;;
    --expected-server-host)
      EXPECTED_SERVER_HOST="$2"
      shift 2
      ;;
    --skip-frontend)
      SKIP_FRONTEND=1
      shift
      ;;
    --skip-worker)
      SKIP_WORKER=1
      shift
      ;;
    --keep-extract)
      KEEP_EXTRACT=1
      shift
      ;;
    --validate-only)
      VALIDATE_ONLY=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

log() {
  printf '\n[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

server_id_from_host() {
  local host="$1"
  printf 'test-agent-backend-%s' "${host//./-}"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_file() {
  if [[ ! -f "$1" ]]; then
    echo "Required file not found: $1" >&2
    exit 1
  fi
}

ssh_target() {
  if [[ -n "${FRONTEND_USER}" ]]; then
    printf '%s@%s' "${FRONTEND_USER}" "${FRONTEND_HOST}"
  else
    printf '%s' "${FRONTEND_HOST}"
  fi
}

configure_backend_defaults() {
  if [[ "${BACKEND_SERVICE}" != *.service ]]; then
    BACKEND_SERVICE="${BACKEND_SERVICE}.service"
  fi
  if [[ ! "${BACKEND_SERVICE}" =~ ^[A-Za-z0-9_.@-]+\.service$ ]]; then
    echo "Invalid backend systemd service name: ${BACKEND_SERVICE}" >&2
    exit 1
  fi
  if [[ -z "${BACKEND_HEALTH_URL}" ]]; then
    BACKEND_HEALTH_URL="http://${BACKEND_HOST}:8080/actuator/health"
  fi
  if [[ -z "${BACKEND_READINESS_URL}" ]]; then
    BACKEND_READINESS_URL="http://${BACKEND_HOST}:8080/actuator/health/readiness"
  fi
  if [[ -z "${EXPECTED_SERVER_HOST}" ]]; then
    EXPECTED_SERVER_HOST="${BACKEND_HOST}"
  fi
  if [[ -z "${EXPECTED_SERVER_ID}" ]]; then
    EXPECTED_SERVER_ID="$(server_id_from_host "${BACKEND_HOST}")"
  fi
}

ensure_backend_service() {
  local backend_env="${INSTALL_ROOT}/config/backend.env"
  local java_bin unit_path

  # 升级已有环境时保留现场 unit；只有首次部署且 unit 缺失时才安装标准服务。
  if systemctl cat "${BACKEND_SERVICE}" >/dev/null 2>&1; then
    return 0
  fi

  require_file "${backend_env}"
  java_bin="$(command -v java || true)"
  if [[ -z "${java_bin}" || "${java_bin}" != /* ]]; then
    echo "Java executable not found; install JDK 21 before creating ${BACKEND_SERVICE}" >&2
    exit 1
  fi

  unit_path="${SYSTEMD_UNIT_DIR}/${BACKEND_SERVICE}"
  log "Install missing backend systemd unit: ${unit_path}"
  mkdir -p "${SYSTEMD_UNIT_DIR}"
  {
    printf '%s\n' \
      '[Unit]' \
      'Description=Test Agent Backend' \
      'Wants=network-online.target' \
      'After=network-online.target' \
      '' \
      '[Service]' \
      'Type=simple' \
      "WorkingDirectory=${INSTALL_ROOT}" \
      "EnvironmentFile=${backend_env}" \
      "ExecStart=${java_bin} -jar ${INSTALL_ROOT}/dist/backend/test-agent-app.jar" \
      'Restart=always' \
      'RestartSec=5' \
      'TimeoutStopSec=60' \
      'LimitNOFILE=65536' \
      '' \
      '[Install]' \
      'WantedBy=multi-user.target'
  } >"${unit_path}"
  chmod 0644 "${unit_path}"
  systemctl daemon-reload
  systemctl enable "${BACKEND_SERVICE}"
}

find_first_file() {
  local root="$1"
  local name="$2"
  # 交付 zip 可能包住 deploy/internal，也可能只包 dist；按文件名在有限层级内定位产物。
  find "${root}" -maxdepth 6 -type f -name "${name}" | sort | head -n 1
}

find_first_tar() {
  local root="$1"
  find "${root}" -maxdepth 6 -type f -name 'test-agent-opencode-worker*linux-amd64.tar' | sort | head -n 1
}

wait_http() {
  local url="$1"
  local label="$2"
  local timeout_seconds="${3:-90}"
  local deadline=$((SECONDS + timeout_seconds))

  until curl -fsS "${url}" >/dev/null; do
    if (( SECONDS >= deadline )); then
      echo "Timed out waiting for ${label}: ${url}" >&2
      return 1
    fi
    sleep 3
  done
}

assert_identity_file() {
  local path="$1"
  local expected="$2"
  local label="$3"
  local actual

  # Java 写出的服务器身份是 worker 连接本机 Java 的来源，值不对时必须先中断部署。
  require_file "${path}"
  actual="$(tr -d '\r\n' <"${path}")"
  if [[ -n "${expected}" && "${actual}" != "${expected}" ]]; then
    echo "${label} mismatch: expected '${expected}', got '${actual}'" >&2
    exit 1
  fi
  printf '%s=%s\n' "${label}" "${actual}"
}

run_frontend_update() {
  local frontend_target="$1"
  local frontend_archive="$2"
  local deploy_internal_src="$3"
  local remote_deploy_tmp="${FRONTEND_ROOT}/deploy/internal.new"

  # 前端服务器只接收静态包和 deploy/internal 模板；不把后端 jar 或 worker 镜像传过去。
  log "Copy frontend artifacts to ${frontend_target}"
  if ! ssh -o BatchMode=yes -o ConnectTimeout=10 "${frontend_target}" true >/dev/null 2>&1; then
    cat >&2 <<EOF
Cannot access frontend server with non-interactive ssh: ${frontend_target}

If unified login or bastion policy blocks direct ssh/scp from this backend host,
copy the same release zip to the frontend server through the approved channel,
then deploy the frontend on 122.233.30.2 itself:

  unzip -p ${ARCHIVE} deploy/internal/deploy-internal-frontend.sh > /tmp/deploy-internal-frontend.sh
  bash /tmp/deploy-internal-frontend.sh --archive ${ARCHIVE}

Then deploy this backend node without touching the frontend:

  bash $0 --archive ${ARCHIVE} --backend-host ${BACKEND_HOST} --skip-frontend

EOF
    exit 1
  fi
  ssh "${frontend_target}" "mkdir -p '${FRONTEND_ROOT}/dist' '${FRONTEND_ROOT}/deploy'"
  scp "${frontend_archive}" "${frontend_target}:${FRONTEND_ROOT}/dist/test-agent-frontend-dist.tar.gz"

  if [[ -d "${deploy_internal_src}" ]]; then
    ssh "${frontend_target}" "rm -rf '${remote_deploy_tmp}'"
    scp -r "${deploy_internal_src}" "${frontend_target}:${remote_deploy_tmp}"
  fi

  log "Update frontend files and reload nginx on ${frontend_target}"
  # 远程更新先备份旧目录，再解压新静态资源；nginx 校验失败会阻断 reload。
  ssh "${frontend_target}" "FRONTEND_ROOT='${FRONTEND_ROOT}' FRONTEND_HEALTH_URL='${FRONTEND_HEALTH_URL}' FRONTEND_URL='${FRONTEND_URL}' bash -s" <<'REMOTE_FRONTEND'
set -euo pipefail
timestamp="$(date +%Y%m%d%H%M%S)"
mkdir -p "${FRONTEND_ROOT}/frontend" "${FRONTEND_ROOT}/dist" "${FRONTEND_ROOT}/deploy"

if [[ -d "${FRONTEND_ROOT}/deploy/internal.new" ]]; then
  if [[ -d "${FRONTEND_ROOT}/deploy/internal" ]]; then
    rm -rf "${FRONTEND_ROOT}/deploy/internal.bak.${timestamp}"
    mv "${FRONTEND_ROOT}/deploy/internal" "${FRONTEND_ROOT}/deploy/internal.bak.${timestamp}"
  fi
  mv "${FRONTEND_ROOT}/deploy/internal.new" "${FRONTEND_ROOT}/deploy/internal"
fi

if [[ -d "${FRONTEND_ROOT}/frontend" ]]; then
  rm -rf "${FRONTEND_ROOT}/frontend.bak.${timestamp}"
  cp -a "${FRONTEND_ROOT}/frontend" "${FRONTEND_ROOT}/frontend.bak.${timestamp}"
fi

tar -C "${FRONTEND_ROOT}" -xzf "${FRONTEND_ROOT}/dist/test-agent-frontend-dist.tar.gz"
nginx -t
systemctl reload nginx
curl -fsS "${FRONTEND_HEALTH_URL}" >/dev/null
curl -fsS "${FRONTEND_URL}" >/dev/null
REMOTE_FRONTEND
}

wait_worker_config_update() {
  local timeout_seconds="${1:-90}"
  local deadline=$((SECONDS + timeout_seconds))

  # worker 重启后必须等 manager 收到 Java 下发配置，否则用户进程启动会缺少运行参数。
  until docker logs --tail 200 test-agent-opencode-worker 2>&1 | grep -q 'manager config update applied'; do
    if (( SECONDS >= deadline )); then
      echo "Timed out waiting for worker manager config update" >&2
      docker logs --tail 120 test-agent-opencode-worker || true
      return 1
    fi
    sleep 3
  done
}

configure_backend_defaults

require_command unzip
require_command find
require_command tar
require_file "${ARCHIVE}"

if [[ "${VALIDATE_ONLY}" -eq 0 ]]; then
  require_command curl
  require_command systemctl
fi

if [[ "${VALIDATE_ONLY}" -eq 0 && "${SKIP_FRONTEND}" -eq 0 ]]; then
  require_command ssh
  require_command scp
fi
if [[ "${VALIDATE_ONLY}" -eq 0 && "${SKIP_WORKER}" -eq 0 ]]; then
  require_command docker
  require_file "${DOCKER_ENV}"
fi

log "Extract release archive"
rm -rf "${EXTRACT_DIR}"
mkdir -p "${EXTRACT_DIR}"
unzip -q "${ARCHIVE}" -d "${EXTRACT_DIR}"

FRONTEND_ARCHIVE="$(find_first_file "${EXTRACT_DIR}" 'test-agent-frontend-dist.tar.gz')"
BACKEND_JAR="$(find_first_file "${EXTRACT_DIR}" 'test-agent-app.jar')"
BACKEND_LIB_DIR="$(find "${EXTRACT_DIR}" -maxdepth 6 -type d -path '*/backend/lib' | sort | head -n 1)"
PROGRAMS_ARCHIVE="$(find_first_file "${EXTRACT_DIR}" 'test-agent-programs.tar.gz')"
WORKER_IMAGE_TAR="$(find_first_tar "${EXTRACT_DIR}")"
DEPLOY_WORKER_SCRIPT="$(find_first_file "${EXTRACT_DIR}" 'opencode-worker-docker.sh')"
DEPLOY_INTERNAL_SRC=""
if [[ -n "${DEPLOY_WORKER_SCRIPT}" ]]; then
  DEPLOY_INTERNAL_SRC="$(cd "$(dirname "${DEPLOY_WORKER_SCRIPT}")" && pwd)"
fi

require_file "${FRONTEND_ARCHIVE}"
require_file "${BACKEND_JAR}"
[[ -n "${BACKEND_LIB_DIR}" && -n "$(find "${BACKEND_LIB_DIR}" -maxdepth 1 -type f -name '*.jar' -print -quit)" ]] || {
  echo "backend external lib directory not found in archive" >&2
  exit 1
}
require_file "${PROGRAMS_ARCHIVE}"
if [[ "${SKIP_WORKER}" -eq 0 ]]; then
  require_file "${WORKER_IMAGE_TAR}"
fi
if [[ -z "${DEPLOY_INTERNAL_SRC}" || ! -d "${DEPLOY_INTERNAL_SRC}" ]]; then
  echo "deploy/internal directory not found in archive" >&2
  exit 1
fi

if [[ "${VALIDATE_ONLY}" -eq 1 ]]; then
  log "Release archive validation passed"
  printf 'frontend archive: %s\n' "${FRONTEND_ARCHIVE}"
  printf 'backend jar: %s\n' "${BACKEND_JAR}"
  printf 'backend lib: %s\n' "${BACKEND_LIB_DIR}"
  printf 'programs archive: %s\n' "${PROGRAMS_ARCHIVE}"
  if [[ "${SKIP_WORKER}" -eq 0 ]]; then
    printf 'worker image tar: %s\n' "${WORKER_IMAGE_TAR}"
  fi
  printf 'deploy internal: %s\n' "${DEPLOY_INTERNAL_SRC}"
  if [[ "${KEEP_EXTRACT}" -eq 0 ]]; then
    rm -rf "${EXTRACT_DIR}"
  fi
  exit 0
fi

if [[ "${SKIP_FRONTEND}" -eq 0 ]]; then
  run_frontend_update "$(ssh_target)" "${FRONTEND_ARCHIVE}" "${DEPLOY_INTERNAL_SRC}"
fi

log "Install backend artifacts under ${INSTALL_ROOT}"
timestamp="$(date +%Y%m%d%H%M%S)"
mkdir -p "${INSTALL_ROOT}/dist/backend" "${INSTALL_ROOT}/dist" "${INSTALL_ROOT}/programs" "${INSTALL_ROOT}/deploy"

cp "${BACKEND_JAR}" "${INSTALL_ROOT}/dist/backend/test-agent-app.jar.new"
rm -rf "${INSTALL_ROOT}/dist/backend/lib.new"
cp -a "${BACKEND_LIB_DIR}" "${INSTALL_ROOT}/dist/backend/lib.new"
cp "${PROGRAMS_ARCHIVE}" "${INSTALL_ROOT}/dist/test-agent-programs.tar.gz"
if [[ "${SKIP_WORKER}" -eq 0 ]]; then
  cp "${WORKER_IMAGE_TAR}" "${INSTALL_ROOT}/dist/test-agent-opencode-worker_internal-linux-amd64.tar"
fi

rm -rf "${INSTALL_ROOT}/deploy/internal.new"
cp -a "${DEPLOY_INTERNAL_SRC}" "${INSTALL_ROOT}/deploy/internal.new"
if [[ -d "${INSTALL_ROOT}/deploy/internal" ]]; then
  rm -rf "${INSTALL_ROOT}/deploy/internal.bak.${timestamp}"
  mv "${INSTALL_ROOT}/deploy/internal" "${INSTALL_ROOT}/deploy/internal.bak.${timestamp}"
fi
mv "${INSTALL_ROOT}/deploy/internal.new" "${INSTALL_ROOT}/deploy/internal"
chmod +x "${INSTALL_ROOT}/deploy/internal/opencode-worker-docker.sh" || true

ensure_backend_service

log "Stop backend service and replace jar"
systemctl stop "${BACKEND_SERVICE}"
if [[ -f "${INSTALL_ROOT}/dist/backend/test-agent-app.jar" ]]; then
  cp -a "${INSTALL_ROOT}/dist/backend/test-agent-app.jar" "${INSTALL_ROOT}/dist/backend/test-agent-app.jar.bak.${timestamp}"
fi
if [[ -d "${INSTALL_ROOT}/dist/backend/lib" ]]; then
  rm -rf "${INSTALL_ROOT}/dist/backend/lib.bak.${timestamp}"
  mv "${INSTALL_ROOT}/dist/backend/lib" "${INSTALL_ROOT}/dist/backend/lib.bak.${timestamp}"
fi
mv "${INSTALL_ROOT}/dist/backend/test-agent-app.jar.new" "${INSTALL_ROOT}/dist/backend/test-agent-app.jar"
mv "${INSTALL_ROOT}/dist/backend/lib.new" "${INSTALL_ROOT}/dist/backend/lib"

log "Extract external programs"
tar -C "${INSTALL_ROOT}" -xzf "${INSTALL_ROOT}/dist/test-agent-programs.tar.gz"

if [[ "${SKIP_WORKER}" -eq 0 ]]; then
  log "Load opencode-worker docker image"
  docker load -i "${INSTALL_ROOT}/dist/test-agent-opencode-worker_internal-linux-amd64.tar"
fi

log "Start backend service and verify health"
systemctl start "${BACKEND_SERVICE}"
journalctl -u "${BACKEND_SERVICE}" -n 120 --no-pager || true
wait_http "${BACKEND_HEALTH_URL}" "backend health" 120
wait_http "${BACKEND_READINESS_URL}" "backend readiness" 120
assert_identity_file "${INSTALL_ROOT}/data/.serverid" "${EXPECTED_SERVER_ID}" "serverid"
assert_identity_file "${INSTALL_ROOT}/data/.serverhost" "${EXPECTED_SERVER_HOST}" "serverhost"

if [[ "${SKIP_WORKER}" -eq 0 ]]; then
  log "Restart opencode-worker"
  (cd "${INSTALL_ROOT}/deploy/internal" && ./opencode-worker-docker.sh --env-file "${DOCKER_ENV}" restart)
  (cd "${INSTALL_ROOT}/deploy/internal" && ./opencode-worker-docker.sh --env-file "${DOCKER_ENV}" status)
  wait_worker_config_update 120
  docker logs --tail 120 test-agent-opencode-worker
fi

if [[ "${SKIP_FRONTEND}" -eq 0 ]]; then
  log "Verify frontend from backend server"
  wait_http "${FRONTEND_HEALTH_URL}" "frontend health" 60
  wait_http "${FRONTEND_URL}" "frontend page" 60
fi

if [[ "${KEEP_EXTRACT}" -eq 0 ]]; then
  rm -rf "${EXTRACT_DIR}"
fi

log "Deployment finished"
