#!/usr/bin/env bash
set -euo pipefail

ROLE=""
OUTPUT_DIR="/data/0709"
INSTALL_ROOT="/data/testagent"
NGINX_HOME="/data/apps/nginx"
BACKEND_SERVICE="test-agent-backend"
WORKER_CONTAINER="test-agent-opencode-worker"
NODE_LABEL=""
LOG_HOURS=24
INCLUDE_SENSITIVE=0
SKIP_NETWORK=0

usage() {
  cat <<'USAGE'
Usage: collect-multi-backend-context.sh <backend|frontend> --include-sensitive [options]

Collect the current enterprise deployment into one sensitive diagnostic archive.
The command is read-only except for its temporary directory and output tar.gz.

Options:
  --include-sensitive       Required explicit consent. Includes raw env files, logs,
                            backend jar, embedded RSA private key and deployed frontend.
  --output-dir <path>       Archive output directory. Default: /data/0709.
  --node-label <label>      Archive node label. Defaults to .serverhost or hostname.
  --install-root <path>     Test Agent install root. Default: /data/testagent.
  --nginx-home <path>       Frontend Nginx home. Default: /data/apps/nginx.
  --backend-service <name>  Backend systemd service. Default: test-agent-backend.
  --worker-container <name> Worker container. Default: test-agent-opencode-worker.
  --log-hours <hours>       Collect service logs from recent hours. Default: 24.
  --skip-network            Skip fixed-site connectivity probes; intended for tests.
  -h, --help                Show this help.

The result intentionally contains passwords, tokens, Cookie/Authorization-bearing logs,
the delivery JAR and RSA private key. Keep the SENSITIVE archive access-controlled.
USAGE
}

if [[ $# -gt 0 && "$1" != -* ]]; then
  ROLE="$1"
  shift
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --include-sensitive)
      INCLUDE_SENSITIVE=1
      shift
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --node-label)
      NODE_LABEL="$2"
      shift 2
      ;;
    --install-root)
      INSTALL_ROOT="$2"
      shift 2
      ;;
    --nginx-home)
      NGINX_HOME="$2"
      shift 2
      ;;
    --backend-service)
      BACKEND_SERVICE="$2"
      shift 2
      ;;
    --worker-container)
      WORKER_CONTAINER="$2"
      shift 2
      ;;
    --log-hours)
      LOG_HOURS="$2"
      shift 2
      ;;
    --skip-network)
      SKIP_NETWORK=1
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

if [[ "${ROLE}" != "backend" && "${ROLE}" != "frontend" ]]; then
  echo "The first argument must be backend or frontend" >&2
  usage >&2
  exit 2
fi
if [[ "${INCLUDE_SENSITIVE}" -ne 1 ]]; then
  echo "Refusing to collect secrets without the explicit --include-sensitive flag" >&2
  exit 2
fi
if [[ ! "${OUTPUT_DIR}" =~ ^/[A-Za-z0-9._/-]+$ ]]; then
  echo "--output-dir must be an absolute path: ${OUTPUT_DIR}" >&2
  exit 2
fi
if [[ ! "${INSTALL_ROOT}" =~ ^/[A-Za-z0-9._/-]+$ ]]; then
  echo "--install-root must be an absolute path: ${INSTALL_ROOT}" >&2
  exit 2
fi
if [[ ! "${NGINX_HOME}" =~ ^/[A-Za-z0-9._/-]+$ ]]; then
  echo "--nginx-home must be an absolute path: ${NGINX_HOME}" >&2
  exit 2
fi
if [[ ! "${LOG_HOURS}" =~ ^[0-9]+$ ]] || (( LOG_HOURS < 1 || LOG_HOURS > 168 )); then
  echo "--log-hours must be an integer between 1 and 168" >&2
  exit 2
fi

umask 077
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-context.XXXXXX")"
BUNDLE_ROOT="${TMP_ROOT}/bundle"
mkdir -p "${OUTPUT_DIR}"
mkdir -p "${BUNDLE_ROOT}/files" "${BUNDLE_ROOT}/commands" "${BUNDLE_ROOT}/logs"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

# 现场 dotenv 只按文本读取，不 source，避免执行其中的命令替换或其它 shell 内容。
env_value() {
  local file="$1"
  local wanted_key="$2"
  local line key value result=""
  [[ -f "${file}" ]] || return 0
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ "${line}" == *=* ]] || continue
    key="${line%%=*}"
    [[ "${key}" == "${wanted_key}" ]] || continue
    value="${line#*=}"
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    result="${value}"
  done <"${file}"
  printf '%s' "${result}"
}

safe_label() {
  printf '%s' "$1" | tr -cs 'A-Za-z0-9._-' '-' | sed 's/^-*//; s/-*$//'
}

sha256_file() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${file}"
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${file}"
  else
    printf 'sha256 unavailable: %s\n' "${file}"
  fi
}

capture() {
  local output="$1"
  shift
  {
    printf '$'
    printf ' %q' "$@"
    printf '\n'
    "$@"
  } >"${output}" 2>&1 || {
    local code=$?
    printf '\n[command exited %s]\n' "${code}" >>"${output}"
    return 0
  }
}

copy_file() {
  local source="$1"
  local relative="$2"
  [[ -f "${source}" ]] || return 0
  mkdir -p "$(dirname "${BUNDLE_ROOT}/files/${relative}")"
  cp -a "${source}" "${BUNDLE_ROOT}/files/${relative}"
}

copy_directory() {
  local source="$1"
  local relative="$2"
  [[ -d "${source}" ]] || return 0
  mkdir -p "$(dirname "${BUNDLE_ROOT}/files/${relative}")"
  cp -a "${source}" "${BUNDLE_ROOT}/files/${relative}"
}

write_system_context() {
  {
    printf 'collected_at=%s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')"
    printf 'role=%s\n' "${ROLE}"
    printf 'node_label=%s\n' "${NODE_LABEL}"
    printf 'install_root=%s\n' "${INSTALL_ROOT}"
    printf 'nginx_home=%s\n' "${NGINX_HOME}"
    printf 'log_hours=%s\n' "${LOG_HOURS}"
    printf 'kernel=%s\n' "$(uname -srm 2>/dev/null || true)"
    printf 'effective_uid=%s\n' "$(id -u 2>/dev/null || true)"
    if [[ -f /etc/os-release ]]; then
      grep -E '^(ID|VERSION_ID|PRETTY_NAME)=' /etc/os-release || true
    fi
    java -version 2>&1 || true
    docker --version 2>&1 || true
  } >"${BUNDLE_ROOT}/commands/system.txt"

  if command -v ss >/dev/null 2>&1; then
    capture "${BUNDLE_ROOT}/commands/listeners.txt" ss -lntp
  elif command -v netstat >/dev/null 2>&1; then
    capture "${BUNDLE_ROOT}/commands/listeners.txt" netstat -lntp
  fi
  capture "${BUNDLE_ROOT}/commands/disk.txt" df -h "${INSTALL_ROOT}" "${OUTPUT_DIR}"
}

tcp_probe() {
  local host="$1"
  local port="$2"
  if command -v nc >/dev/null 2>&1; then
    if nc -z -w 3 "${host}" "${port}" >/dev/null 2>&1; then
      printf '%s:%s=reachable\n' "${host}" "${port}"
    else
      printf '%s:%s=unreachable\n' "${host}" "${port}"
    fi
  else
    printf '%s:%s=skipped-nc-unavailable\n' "${host}" "${port}"
  fi
}

http_probe() {
  local name="$1"
  local url="$2"
  local code
  if ! command -v curl >/dev/null 2>&1; then
    printf '%s=skipped-curl-unavailable\n' "${name}"
    return
  fi
  code="$(curl -sS -o /dev/null --max-time 5 -w '%{http_code}' "${url}" 2>/dev/null || true)"
  printf '%s=%s\n' "${name}" "${code:-failed}"
}

write_network_context() {
  [[ "${SKIP_NETWORK}" -eq 0 ]] || return 0
  {
    http_probe frontend_ip_health http://122.233.30.2:9996/health
    http_probe backend_4_readiness http://122.233.30.4:8080/actuator/health/readiness
    http_probe backend_114_readiness http://122.233.30.114:8080/actuator/health/readiness
    tcp_probe 122.233.30.20 6379
    tcp_probe 122.233.30.147 5432
    tcp_probe ai-code.sdc.enterprise 9070
  } >"${BUNDLE_ROOT}/commands/network.txt"
}

collect_backend() {
  local backend_env="${INSTALL_ROOT}/config/backend.env"
  local docker_env="${INSTALL_ROOT}/config/docker.env"
  local backend_jar="${INSTALL_ROOT}/dist/backend/test-agent-app.jar"
  local backend_lib="${INSTALL_ROOT}/dist/backend/lib"

  copy_file "${backend_env}" data/testagent/config/backend.env
  copy_file "${docker_env}" data/testagent/config/docker.env
  copy_file "${INSTALL_ROOT}/data/.serverid" data/testagent/data/.serverid
  copy_file "${INSTALL_ROOT}/data/.serverhost" data/testagent/data/.serverhost
  copy_file "${backend_jar}" data/testagent/dist/backend/test-agent-app.jar

  if [[ -f "${backend_jar}" ]]; then
    sha256_file "${backend_jar}" >"${BUNDLE_ROOT}/commands/backend-jar.sha256"
    if command -v unzip >/dev/null 2>&1 \
      && unzip -Z1 "${backend_jar}" | grep -Fx 'BOOT-INF/classes/rsa-private.key' >/dev/null; then
      mkdir -p "${BUNDLE_ROOT}/files/data/testagent/dist/backend"
      unzip -p "${backend_jar}" BOOT-INF/classes/rsa-private.key \
        >"${BUNDLE_ROOT}/files/data/testagent/dist/backend/rsa-private.key"
      sha256_file "${BUNDLE_ROOT}/files/data/testagent/dist/backend/rsa-private.key" \
        >"${BUNDLE_ROOT}/commands/rsa-private-key.sha256"
    fi
  fi

  if [[ -d "${backend_lib}" ]]; then
    find "${backend_lib}" -maxdepth 1 -type f -name '*.jar' -print0 \
      | while IFS= read -r -d '' library; do sha256_file "${library}"; done \
      >"${BUNDLE_ROOT}/commands/backend-lib.sha256"
  fi

  if command -v systemctl >/dev/null 2>&1; then
    capture "${BUNDLE_ROOT}/commands/backend-systemd-show.txt" systemctl show "${BACKEND_SERVICE}" \
      -p FragmentPath -p LoadState -p ActiveState -p SubState -p MainPID \
      -p User -p Group -p WorkingDirectory -p ExecStart -p EnvironmentFiles
    capture "${BUNDLE_ROOT}/commands/backend-systemd-unit.txt" systemctl cat "${BACKEND_SERVICE}"
  fi
  if command -v journalctl >/dev/null 2>&1; then
    journalctl -u "${BACKEND_SERVICE}" --since "${LOG_HOURS} hours ago" --no-pager \
      >"${BUNDLE_ROOT}/logs/test-agent-backend.log" 2>&1 || true
  fi

  if command -v docker >/dev/null 2>&1; then
    capture "${BUNDLE_ROOT}/commands/docker-ps.txt" docker ps -a --no-trunc
    capture "${BUNDLE_ROOT}/commands/docker-images.txt" docker images --no-trunc
    capture "${BUNDLE_ROOT}/commands/worker-inspect.json" docker inspect "${WORKER_CONTAINER}"
    docker logs --since "${LOG_HOURS}h" "${WORKER_CONTAINER}" \
      >"${BUNDLE_ROOT}/logs/${WORKER_CONTAINER}.log" 2>&1 || true
  fi

  {
    printf 'backend_manager_token=%s\n' "$(env_value "${backend_env}" TEST_AGENT_OPENCODE_MANAGER_TOKEN)"
    printf 'docker_manager_token=%s\n' "$(env_value "${docker_env}" TEST_AGENT_OPENCODE_MANAGER_TOKEN)"
    printf 'embedded_rsa_expected=BOOT-INF/classes/rsa-private.key\n'
  } >"${BUNDLE_ROOT}/commands/config-extracted-values.txt"

  if [[ -d "${INSTALL_ROOT}/deploy/internal" ]]; then
    mkdir -p "${BUNDLE_ROOT}/files/data/testagent/deploy/internal"
    find "${INSTALL_ROOT}/deploy/internal" -maxdepth 1 -type f \
      \( -name '*.sh' -o -name '*.md' -o -name '*.example' \) -print0 \
      | while IFS= read -r -d '' deploy_file; do
          cp -a "${deploy_file}" "${BUNDLE_ROOT}/files/data/testagent/deploy/internal/"
        done
  fi

  capture "${BUNDLE_ROOT}/commands/install-root-listing.txt" ls -la "${INSTALL_ROOT}" \
    "${INSTALL_ROOT}/config" "${INSTALL_ROOT}/data" "${INSTALL_ROOT}/dist/backend"
}

collect_frontend() {
  local nginx_env="${INSTALL_ROOT}/config/nginx.env"
  local nginx_bin="${NGINX_HOME}/sbin/nginx"
  local nginx_prefix="${NGINX_HOME}"
  local nginx_main_conf="${NGINX_HOME}/conf/nginx.conf"
  local nginx_gateway_conf="${NGINX_HOME}/conf/test-agent.conf"

  copy_file "${nginx_env}" data/testagent/config/nginx.env
  if [[ -f "${nginx_env}" ]]; then
    nginx_bin="$(env_value "${nginx_env}" TEST_AGENT_NGINX_BIN)"
    nginx_prefix="$(env_value "${nginx_env}" TEST_AGENT_NGINX_PREFIX)"
    nginx_main_conf="$(env_value "${nginx_env}" TEST_AGENT_NGINX_MAIN_CONF)"
    nginx_gateway_conf="$(env_value "${nginx_env}" TEST_AGENT_NGINX_CONF_PATH)"
    nginx_bin="${nginx_bin:-${NGINX_HOME}/sbin/nginx}"
    nginx_prefix="${nginx_prefix:-${NGINX_HOME}}"
    nginx_main_conf="${nginx_main_conf:-${NGINX_HOME}/conf/nginx.conf}"
    nginx_gateway_conf="${nginx_gateway_conf:-${NGINX_HOME}/conf/test-agent.conf}"
  fi

  copy_file "${nginx_main_conf}" data/apps/nginx/conf/nginx.conf
  copy_file "${nginx_gateway_conf}" data/apps/nginx/conf/test-agent.conf
  copy_directory "${INSTALL_ROOT}/frontend" data/testagent/frontend

  if [[ -x "${nginx_bin}" ]]; then
    capture "${BUNDLE_ROOT}/commands/nginx-test.txt" "${nginx_bin}" \
      -p "${nginx_prefix%/}/" -c "${nginx_main_conf}" -t
    capture "${BUNDLE_ROOT}/commands/nginx-active-config.txt" "${nginx_bin}" \
      -p "${nginx_prefix%/}/" -c "${nginx_main_conf}" -T
  fi

  copy_file "${NGINX_HOME}/logs/access.log" data/apps/nginx/logs/access.log
  copy_file "${NGINX_HOME}/logs/error.log" data/apps/nginx/logs/error.log

  if [[ -f "${INSTALL_ROOT}/frontend/index.html" ]]; then
    sha256_file "${INSTALL_ROOT}/frontend/index.html" \
      >"${BUNDLE_ROOT}/commands/frontend-index.sha256"
  fi
  capture "${BUNDLE_ROOT}/commands/frontend-listing.txt" ls -la "${INSTALL_ROOT}/frontend" \
    "${INSTALL_ROOT}/config" "${NGINX_HOME}/conf" "${NGINX_HOME}/logs"
}

if [[ -z "${NODE_LABEL}" && "${ROLE}" == "backend" && -f "${INSTALL_ROOT}/data/.serverhost" ]]; then
  NODE_LABEL="$(head -n 1 "${INSTALL_ROOT}/data/.serverhost")"
fi
if [[ -z "${NODE_LABEL}" ]]; then
  NODE_LABEL="$(hostname -s 2>/dev/null || printf unknown)"
fi
NODE_LABEL="$(safe_label "${NODE_LABEL}")"
NODE_LABEL="${NODE_LABEL:-unknown}"

write_system_context
write_network_context
if [[ "${ROLE}" == "backend" ]]; then
  collect_backend
else
  collect_frontend
fi

cat >"${BUNDLE_ROOT}/README-SENSITIVE.txt" <<EOF
This archive intentionally contains production secrets and sensitive runtime evidence.

Included by explicit user request:
- raw backend.env/docker.env/nginx.env where present;
- deployed backend JAR and extracted embedded RSA private key on backend nodes;
- full systemd unit/properties, Docker inspect and recent service logs;
- active Nginx configuration, Nginx logs and deployed frontend on the frontend node.

Role: ${ROLE}
Node: ${NODE_LABEL}
Collected: $(date '+%Y-%m-%dT%H:%M:%S%z')

Keep this archive mode 0600, transfer it through an approved channel, and delete temporary
copies after the deployment scripts have been generated and verified.
EOF

find "${BUNDLE_ROOT}" -type f -print | sed "s#^${BUNDLE_ROOT}/##" | sort \
  >"${BUNDLE_ROOT}/CONTENTS.txt"

timestamp="$(date '+%Y%m%d-%H%M%S')"
archive="${OUTPUT_DIR}/test-agent-context-SENSITIVE-${ROLE}-${NODE_LABEL}-${timestamp}.tar.gz"
tar -C "${BUNDLE_ROOT}" -czf "${archive}" .
chmod 0600 "${archive}"
archive_digest="$(sha256_file "${archive}" | awk '{print $1}')"
printf '%s  %s\n' "${archive_digest}" "$(basename "${archive}")" >"${archive}.sha256"
chmod 0600 "${archive}.sha256"

printf 'Sensitive context archive: %s\n' "${archive}"
printf 'Checksum: %s.sha256\n' "${archive}"
printf 'Transfer both files and delete unneeded copies after analysis.\n'
