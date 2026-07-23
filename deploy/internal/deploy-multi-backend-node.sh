#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROLE=""
CONFIG_DIR="${SCRIPT_DIR}/config"
CONFIG_DIR_EXPLICIT=0
RELEASE_ARCHIVE="/data/0709/test-agent-internal-release.zip"
INSTALL_ROOT="/data/testagent"
BACKEND_HOST=""
PEER_HOST=""
MODE="deploy"
SKIP_PEER_CHECK=0

usage() {
  cat <<'USAGE'
Usage: deploy-multi-backend-node.sh <backend|frontend> [options]

Apply one node's prepared configuration and reuse the standard enterprise release
scripts to deploy the enterprise multi-backend topology.

Options:
  --config-dir <path>       Prepared config directory. Default: <script-dir>/config.
  --release-archive <path>  Full release ZIP. Default: /data/0709/test-agent-internal-release.zip.
  --install-root <path>     Installation root. Default: /data/testagent.
  --backend-host <host>     Backend role only; defaults to backend.env.
  --peer-host <host>        Backend health-check peer. Defaults to the other seed node.
  --skip-peer-check         Backend role only; defer peer check for the first stopped-cluster node.
  --validate-only           Validate configuration, release checksum and embedded RSA only.
  --verify-only             Verify an already deployed node without changing it.
  -h, --help                Show this help.

Deployment backs up existing configuration, installs the prepared files, then invokes
the release ZIP's standard deployment script. Secrets are never printed.
USAGE
}

if [[ $# -gt 0 && "$1" != -* ]]; then
  ROLE="$1"
  shift
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --config-dir)
      CONFIG_DIR="$2"
      CONFIG_DIR_EXPLICIT=1
      shift 2
      ;;
    --release-archive)
      RELEASE_ARCHIVE="$2"
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
    --peer-host)
      PEER_HOST="$2"
      shift 2
      ;;
    --skip-peer-check)
      SKIP_PEER_CHECK=1
      shift
      ;;
    --validate-only)
      [[ "${MODE}" == "deploy" ]] || {
        echo "Only one of --validate-only and --verify-only may be used" >&2
        exit 2
      }
      MODE="validate"
      shift
      ;;
    --verify-only)
      [[ "${MODE}" == "deploy" ]] || {
        echo "Only one of --validate-only and --verify-only may be used" >&2
        exit 2
      }
      MODE="verify"
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

require_absolute_path() {
  local value="$1"
  local name="$2"
  if [[ ! "${value}" =~ ^/[A-Za-z0-9._/-]+$ ]]; then
    echo "${name} must be an absolute path: ${value}" >&2
    exit 1
  fi
}

# dotenv 只按文本读取最后一次赋值，不能 source 现场文件，避免执行其中的 shell 内容。
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

key_count() {
  local file="$1"
  local key="$2"
  grep -c "^${key}=" "${file}" || true
}

require_one_key() {
  local file="$1"
  local key="$2"
  if [[ "$(key_count "${file}" "${key}")" -ne 1 ]]; then
    echo "${file} must contain exactly one ${key}" >&2
    exit 1
  fi
}

require_exact_value() {
  local file="$1"
  local key="$2"
  local expected="$3"
  require_one_key "${file}" "${key}"
  if [[ "$(env_value "${file}" "${key}")" != "${expected}" ]]; then
    echo "Unexpected ${key} in ${file}" >&2
    exit 1
  fi
}

require_nonempty_value() {
  local file="$1"
  local key="$2"
  require_one_key "${file}" "${key}"
  if [[ -z "$(env_value "${file}" "${key}")" ]]; then
    echo "Required value is empty: ${key} in ${file}" >&2
    exit 1
  fi
}

server_id_from_host() {
  printf 'test-agent-backend-%s' "${1//./-}"
}

require_backend_site_ip() {
  local value="$1"
  local name="$2"
  if [[ ! "${value}" =~ ^122\.233\.30\.([1-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-4])$ ]] \
    || [[ "${value}" == "122.233.30.2" ]]; then
    echo "${name} must be one backend IP in 122.233.30.0/24: ${value:-<empty>}" >&2
    exit 1
  fi
}

validate_backend_config() {
  local config_dir="$1"
  local backend_env="${config_dir}/backend.env"
  local docker_env="${config_dir}/docker.env"
  local expected_server_id manager_token docker_manager_token

  require_file "${backend_env}"
  require_file "${docker_env}"
  if [[ -z "${BACKEND_HOST}" ]]; then
    BACKEND_HOST="$(env_value "${backend_env}" TEST_AGENT_SERVER_ADVERTISED_HOST)"
  fi
  require_backend_site_ip "${BACKEND_HOST}" BACKEND_HOST
  expected_server_id="$(server_id_from_host "${BACKEND_HOST}")"

  require_exact_value "${backend_env}" TEST_AGENT_SERVER_ADVERTISED_HOST "${BACKEND_HOST}"
  require_exact_value "${backend_env}" TEST_AGENT_LINUX_SERVER_ID "${expected_server_id}"
  require_exact_value "${backend_env}" SYS_DATA_ROOT_DIR /data/testagent/data
  require_exact_value "${backend_env}" TEST_AGENT_DB_URL jdbc:postgresql://122.233.30.147:5432/postgres
  require_exact_value "${backend_env}" TEST_AGENT_REDIS_HOST 122.233.30.20
  require_exact_value "${backend_env}" TEST_AGENT_XXL_JOB_ENABLED true
  require_exact_value "${backend_env}" TEST_AGENT_XXL_JOB_MYSQL_URL \
    'jdbc:mysql://122.210.106.43:3306/xxl_job?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
  require_exact_value "${backend_env}" TEST_AGENT_XXL_JOB_MYSQL_USERNAME root
  require_nonempty_value "${backend_env}" TEST_AGENT_XXL_JOB_MYSQL_PASSWORD
  require_nonempty_value "${backend_env}" TEST_AGENT_XXL_JOB_ACCESS_TOKEN
  require_exact_value "${backend_env}" TEST_AGENT_XXL_JOB_ADMIN_PORT 18080
  require_exact_value "${backend_env}" TEST_AGENT_XXL_JOB_EXECUTOR_PORT 9999
  require_exact_value "${backend_env}" TEST_AGENT_XXL_JOB_COOKIE_SECURE false
  require_exact_value "${backend_env}" TEST_AGENT_CORS_ALLOWED_ORIGINS \
    http://mimo.sdc.cs.icbc:9996,http://122.233.30.2:9996
  require_exact_value "${backend_env}" TEST_AGENT_SERVER_BROADCAST_ENABLED true
  require_exact_value "${backend_env}" TEST_AGENT_SERVER_TERMINAL_PUBLIC_WEBSOCKET_BASE_URL ""
  require_exact_value "${backend_env}" TEST_AGENT_SERVER_TERMINAL_ALLOW_INSECURE_WEBSOCKET true
  require_nonempty_value "${backend_env}" TEST_AGENT_DB_PASSWORD
  require_nonempty_value "${backend_env}" TEST_AGENT_OPENCODE_MANAGER_TOKEN
  require_nonempty_value "${backend_env}" TEST_AGENT_INTERNAL_PROXY_API_KEY

  if grep -q '^TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH=' "${backend_env}"; then
    echo "External RSA path is forbidden; the release JAR must provide rsa-private.key" >&2
    exit 1
  fi
  if grep -q 'REPLACE_' "${backend_env}" "${docker_env}"; then
    echo "Prepared backend configuration still contains a REPLACE_ placeholder" >&2
    exit 1
  fi

  require_exact_value "${docker_env}" TEST_AGENT_DATA_ROOT /data/testagent/data
  require_exact_value "${docker_env}" TEST_AGENT_PROGRAM_ROOT /data/testagent/programs
  require_exact_value "${docker_env}" OPENCODE_WORKER_BACKEND_PORT 8080
  require_exact_value "${docker_env}" OPENCODE_WORKER_PORT_START 4096
  require_exact_value "${docker_env}" OPENCODE_WORKER_PORT_END 4115
  require_exact_value "${docker_env}" OPENCODE_ALLOWED_CORS \
    http://mimo.sdc.cs.icbc:9996,http://122.233.30.2:9996
  require_nonempty_value "${docker_env}" TEST_AGENT_OPENCODE_MANAGER_TOKEN
  if grep -q '^TEST_AGENT_BACKEND=' "${docker_env}"; then
    echo "Obsolete TEST_AGENT_BACKEND must not be present; worker reads .serverhost" >&2
    exit 1
  fi
  if grep -q '^TEST_AGENT_INTERNAL_PROXY_API_KEY=' "${docker_env}"; then
    echo "Internal proxy key must stay in backend.env and must not be copied to docker.env" >&2
    exit 1
  fi

  manager_token="$(env_value "${backend_env}" TEST_AGENT_OPENCODE_MANAGER_TOKEN)"
  docker_manager_token="$(env_value "${docker_env}" TEST_AGENT_OPENCODE_MANAGER_TOKEN)"
  if [[ "${manager_token}" != "${docker_manager_token}" ]]; then
    echo "Manager tokens differ between backend.env and docker.env" >&2
    exit 1
  fi
}

validate_frontend_config() {
  local config_dir="$1"
  local nginx_env="${config_dir}/nginx.env"
  local backends routes admins entry host expected_route expected_routes="" expected_admins=""
  local -a backend_entries=()

  require_file "${nginx_env}"
  if grep -q 'REPLACE_' "${nginx_env}"; then
    echo "Prepared frontend configuration still contains a REPLACE_ placeholder" >&2
    exit 1
  fi
  require_exact_value "${nginx_env}" TEST_AGENT_NGINX_MODE multi
  require_one_key "${nginx_env}" TEST_AGENT_NGINX_BACKENDS
  require_one_key "${nginx_env}" TEST_AGENT_NGINX_SERVER_ROUTES
  require_one_key "${nginx_env}" TEST_AGENT_NGINX_XXL_JOB_ADMINS
  backends="$(env_value "${nginx_env}" TEST_AGENT_NGINX_BACKENDS)"
  routes="$(env_value "${nginx_env}" TEST_AGENT_NGINX_SERVER_ROUTES)"
  admins="$(env_value "${nginx_env}" TEST_AGENT_NGINX_XXL_JOB_ADMINS)"
  IFS=',' read -r -a backend_entries <<<"${backends}"
  [[ "${#backend_entries[@]}" -ge 2 ]] || {
    echo "TEST_AGENT_NGINX_BACKENDS must contain at least two backends" >&2
    exit 1
  }
  for entry in "${backend_entries[@]}"; do
    [[ "${entry}" =~ ^(122\.233\.30\.[0-9]+):8080$ ]] || {
      echo "Invalid backend entry in nginx.env: ${entry}" >&2
      exit 1
    }
    host="${BASH_REMATCH[1]}"
    require_backend_site_ip "${host}" TEST_AGENT_NGINX_BACKENDS
    expected_route="$(server_id_from_host "${host}")=${entry}"
    expected_routes="${expected_routes:+${expected_routes},}${expected_route}"
    expected_admins="${expected_admins:+${expected_admins},}${host}:18080"
  done
  [[ ",${backends}," == *",122.233.30.4:8080,"* \
    && ",${backends}," == *",122.233.30.114:8080,"* ]] || {
    echo "nginx.env must retain seed backends 122.233.30.4 and 122.233.30.114" >&2
    exit 1
  }
  [[ "${routes}" == "${expected_routes}" ]] || {
    echo "TEST_AGENT_NGINX_SERVER_ROUTES must match TEST_AGENT_NGINX_BACKENDS in order" >&2
    exit 1
  }
  [[ "${admins}" == "${expected_admins}" ]] || {
    echo "TEST_AGENT_NGINX_XXL_JOB_ADMINS must match TEST_AGENT_NGINX_BACKENDS in order" >&2
    exit 1
  }
  require_exact_value "${nginx_env}" TEST_AGENT_NGINX_LISTEN_PORT 80
  require_exact_value "${nginx_env}" TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS 9996
  require_exact_value "${nginx_env}" TEST_AGENT_NGINX_TLS_ENABLED false
  require_exact_value "${nginx_env}" TEST_AGENT_NGINX_CONF_PATH /data/apps/nginx/conf/test-agent.conf
  require_exact_value "${nginx_env}" TEST_AGENT_NGINX_BIN /data/apps/nginx/sbin/nginx
  require_exact_value "${nginx_env}" TEST_AGENT_NGINX_PREFIX /data/apps/nginx
  require_exact_value "${nginx_env}" TEST_AGENT_NGINX_MAIN_CONF /data/apps/nginx/conf/nginx.conf
  require_exact_value "${nginx_env}" TEST_AGENT_NGINX_RELOAD_MODE binary
}

check_release_checksum() {
  local checksum="${RELEASE_ARCHIVE}.sha256"
  local archive_dir archive_name checksum_name
  require_file "${RELEASE_ARCHIVE}"
  require_file "${checksum}"
  archive_dir="$(cd "$(dirname "${RELEASE_ARCHIVE}")" && pwd)"
  archive_name="$(basename "${RELEASE_ARCHIVE}")"
  checksum_name="$(basename "${checksum}")"
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "${archive_dir}" && sha256sum -c "${checksum_name}")
  elif command -v shasum >/dev/null 2>&1; then
    (cd "${archive_dir}" && shasum -a 256 -c "${checksum_name}")
  else
    echo "Neither sha256sum nor shasum is available" >&2
    exit 1
  fi
  # 校验文件必须指向实际上传的固定文件名，避免校验了同目录中的另一份 ZIP。
  grep -Eq "[[:space:]]+\\*?${archive_name}$" "${checksum}" || {
    echo "Release checksum file does not name ${archive_name}" >&2
    exit 1
  }
}

TMP_ROOT=""
BACKEND_DEPLOY_SCRIPT=""
FRONTEND_DEPLOY_SCRIPT=""
cleanup() {
  [[ -z "${TMP_ROOT}" ]] || rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

prepare_release_scripts() {
  local release_jar
  require_command unzip
  check_release_checksum
  unzip -tq "${RELEASE_ARCHIVE}" >/dev/null

  TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-two-backend-release.XXXXXX")"
  chmod 0700 "${TMP_ROOT}"
  BACKEND_DEPLOY_SCRIPT="${TMP_ROOT}/deploy-internal-release.sh"
  FRONTEND_DEPLOY_SCRIPT="${TMP_ROOT}/deploy-internal-frontend.sh"
  release_jar="${TMP_ROOT}/test-agent-app.jar"
  unzip -p "${RELEASE_ARCHIVE}" deploy/internal/deploy-internal-release.sh \
    >"${BACKEND_DEPLOY_SCRIPT}"
  unzip -p "${RELEASE_ARCHIVE}" deploy/internal/deploy-internal-frontend.sh \
    >"${FRONTEND_DEPLOY_SCRIPT}"
  unzip -p "${RELEASE_ARCHIVE}" dist/backend/test-agent-app.jar >"${release_jar}"
  require_file "${BACKEND_DEPLOY_SCRIPT}"
  require_file "${FRONTEND_DEPLOY_SCRIPT}"
  require_file "${release_jar}"
  if ! unzip -Z1 "${release_jar}" \
    | grep -Fx 'BOOT-INF/classes/rsa-private.key' >/dev/null; then
    echo "Release JAR does not contain BOOT-INF/classes/rsa-private.key" >&2
    exit 1
  fi
}

validate_standard_release() {
  if [[ "${ROLE}" == "backend" ]]; then
    bash "${BACKEND_DEPLOY_SCRIPT}" \
      --archive "${RELEASE_ARCHIVE}" \
      --backend-host "${BACKEND_HOST}" \
      --skip-frontend \
      --validate-only
  else
    bash "${FRONTEND_DEPLOY_SCRIPT}" \
      --archive "${RELEASE_ARCHIVE}" \
      --validate-only
  fi
}

backup_and_install() {
  local source="$1"
  local target="$2"
  local mode="$3"
  local timestamp="$4"
  local target_tmp

  install -d -m 0755 "$(dirname "${target}")"
  if [[ -f "${target}" ]]; then
    cp -a "${target}" "${target}.bak.${timestamp}"
  fi
  target_tmp="$(mktemp "${target}.new.XXXXXX")"
  install -m "${mode}" "${source}" "${target_tmp}"
  mv "${target_tmp}" "${target}"
}

install_prepared_config() {
  local timestamp
  timestamp="$(date +%Y%m%d%H%M%S)"
  if [[ "${ROLE}" == "backend" ]]; then
    backup_and_install "${CONFIG_DIR}/backend.env" "${INSTALL_ROOT}/config/backend.env" 0600 "${timestamp}"
    backup_and_install "${CONFIG_DIR}/docker.env" "${INSTALL_ROOT}/config/docker.env" 0600 "${timestamp}"
  else
    backup_and_install "${CONFIG_DIR}/nginx.env" "${INSTALL_ROOT}/config/nginx.env" 0644 "${timestamp}"
  fi
  printf 'Configuration installed; backups use suffix .bak.%s\n' "${timestamp}"
}

verify_backend() {
  local installed_config="${INSTALL_ROOT}/config"
  local expected_server_id peer_host actual_id actual_host jar_digest worker_state worker_health worker_ports

  validate_backend_config "${installed_config}"
  expected_server_id="$(server_id_from_host "${BACKEND_HOST}")"
  peer_host="deferred"
  if [[ "${SKIP_PEER_CHECK}" -eq 0 ]]; then
    peer_host="${PEER_HOST}"
    if [[ -z "${peer_host}" ]]; then
      peer_host="122.233.30.4"
      [[ "${BACKEND_HOST}" == "122.233.30.4" ]] && peer_host="122.233.30.114"
    fi
    require_backend_site_ip "${peer_host}" PEER_HOST
    [[ "${peer_host}" != "${BACKEND_HOST}" ]] || {
      echo "PEER_HOST must differ from BACKEND_HOST" >&2
      exit 1
    }
  fi

  require_command curl
  require_command systemctl
  require_command docker
  require_command unzip
  systemctl is-active --quiet test-agent-backend
  curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null
  curl -fsS http://127.0.0.1:8080/actuator/health/readiness >/dev/null
  curl -fsS http://127.0.0.1:18080/xxl-job-admin/actuator/health/readiness >/dev/null
  if [[ "${SKIP_PEER_CHECK}" -eq 0 ]]; then
    curl -fsS "http://${peer_host}:8080/actuator/health" >/dev/null
  fi

  require_file "${INSTALL_ROOT}/data/.serverid"
  require_file "${INSTALL_ROOT}/data/.serverhost"
  actual_id="$(tr -d '\r\n' <"${INSTALL_ROOT}/data/.serverid")"
  actual_host="$(tr -d '\r\n' <"${INSTALL_ROOT}/data/.serverhost")"
  [[ "${actual_id}" == "${expected_server_id}" ]] || {
    echo "Unexpected .serverid: ${actual_id}" >&2
    exit 1
  }
  [[ "${actual_host}" == "${BACKEND_HOST}" ]] || {
    echo "Unexpected .serverhost: ${actual_host}" >&2
    exit 1
  }

  require_file "${INSTALL_ROOT}/dist/backend/test-agent-app.jar"
  unzip -Z1 "${INSTALL_ROOT}/dist/backend/test-agent-app.jar" \
    | grep -Fx 'BOOT-INF/classes/rsa-private.key' >/dev/null
  if command -v sha256sum >/dev/null 2>&1; then
    jar_digest="$(sha256sum "${INSTALL_ROOT}/dist/backend/test-agent-app.jar" | awk '{print $1}')"
  else
    jar_digest="$(shasum -a 256 "${INSTALL_ROOT}/dist/backend/test-agent-app.jar" | awk '{print $1}')"
  fi

  worker_state="$(docker inspect -f '{{.State.Running}}' test-agent-opencode-worker 2>/dev/null || true)"
  worker_health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' test-agent-opencode-worker 2>/dev/null || true)"
  [[ "${worker_state}" == "true" ]] || {
    echo "Worker container is not running" >&2
    exit 1
  }
  [[ -z "${worker_health}" || "${worker_health}" == "healthy" ]] || {
    echo "Worker health is ${worker_health}" >&2
    exit 1
  }
  worker_ports="$(docker port test-agent-opencode-worker)"
  grep -Eq '^4096/tcp[[:space:]]+->' <<<"${worker_ports}"
  grep -Eq '^4115/tcp[[:space:]]+->' <<<"${worker_ports}"
  # 当前 manager 输出结构化事件；保留旧文本兼容，避免升级期间把健康节点误报为失败。
  docker logs --tail 200 test-agent-opencode-worker 2>&1 \
    | grep -E 'event=manager_config_update status=applied|manager config update applied' >/dev/null

  printf 'Backend verification passed: host=%s id=%s peer=%s jar_sha256=%s\n' \
    "${BACKEND_HOST}" "${expected_server_id}" "${peer_host}" "${jar_digest}"
}

verify_frontend() {
  local installed_config="${INSTALL_ROOT}/config"
  local nginx_env="${installed_config}/nginx.env"
  local nginx_bin nginx_prefix nginx_main_conf nginx_dump backends admins entry
  local -a backend_entries=() admin_entries=()

  validate_frontend_config "${installed_config}"
  require_command curl
  backends="$(env_value "${nginx_env}" TEST_AGENT_NGINX_BACKENDS)"
  IFS=',' read -r -a backend_entries <<<"${backends}"
  for entry in "${backend_entries[@]}"; do
    curl -fsS "http://${entry}/actuator/health/readiness" >/dev/null
  done
  admins="$(env_value "${nginx_env}" TEST_AGENT_NGINX_XXL_JOB_ADMINS)"
  IFS=',' read -r -a admin_entries <<<"${admins}"
  for entry in "${admin_entries[@]}"; do
    curl -fsS "http://${entry}/xxl-job-admin/actuator/health/readiness" >/dev/null
  done

  nginx_bin="$(env_value "${nginx_env}" TEST_AGENT_NGINX_BIN)"
  nginx_prefix="$(env_value "${nginx_env}" TEST_AGENT_NGINX_PREFIX)"
  nginx_main_conf="$(env_value "${nginx_env}" TEST_AGENT_NGINX_MAIN_CONF)"
  [[ -x "${nginx_bin}" ]] || {
    echo "Nginx executable not found: ${nginx_bin}" >&2
    exit 1
  }
  "${nginx_bin}" -p "${nginx_prefix%/}/" -c "${nginx_main_conf}" -t
  nginx_dump="$("${nginx_bin}" -p "${nginx_prefix%/}/" -c "${nginx_main_conf}" -T 2>&1)"
  for entry in "${backend_entries[@]}"; do
    grep -Fq "server ${entry} max_fails=3 fail_timeout=10s;" <<<"${nginx_dump}"
  done
  grep -Fq 'listen 80;' <<<"${nginx_dump}"
  grep -Fq 'listen 9996;' <<<"${nginx_dump}"
  curl -fsS http://127.0.0.1/health | grep -Fxq ok
  curl -fsS http://127.0.0.1:9996/health | grep -Fxq ok
  printf 'Frontend verification passed: %s backends ready; Nginx listens on 80 and 9996\n' \
    "${#backend_entries[@]}"
}

require_absolute_path "${RELEASE_ARCHIVE}" RELEASE_ARCHIVE
require_absolute_path "${INSTALL_ROOT}" INSTALL_ROOT
if [[ "${MODE}" == "verify" && "${CONFIG_DIR_EXPLICIT}" -eq 0 ]]; then
  CONFIG_DIR="${INSTALL_ROOT}/config"
fi

if [[ "${ROLE}" == "backend" ]]; then
  validate_backend_config "${CONFIG_DIR}"
else
  validate_frontend_config "${CONFIG_DIR}"
fi

if [[ "${MODE}" == "verify" ]]; then
  if [[ "${ROLE}" == "backend" ]]; then
    verify_backend
  else
    verify_frontend
  fi
  exit 0
fi

prepare_release_scripts
validate_standard_release
if [[ "${MODE}" == "validate" ]]; then
  printf '%s node configuration and release validation passed\n' "${ROLE}"
  exit 0
fi

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Deployment must run as root because it updates /data and system services" >&2
  exit 1
fi

if [[ "${ROLE}" == "frontend" ]]; then
  # 前端最后切流；任一后台未 ready 时先失败，不改 nginx.env 或静态资源。
  require_command curl
  frontend_backends="$(env_value "${CONFIG_DIR}/nginx.env" TEST_AGENT_NGINX_BACKENDS)"
  IFS=',' read -r -a frontend_backend_entries <<<"${frontend_backends}"
  for frontend_backend in "${frontend_backend_entries[@]}"; do
    curl -fsS "http://${frontend_backend}/actuator/health/readiness" >/dev/null
  done
fi

install_prepared_config
if [[ "${ROLE}" == "backend" ]]; then
  bash "${BACKEND_DEPLOY_SCRIPT}" \
    --archive "${RELEASE_ARCHIVE}" \
    --install-root "${INSTALL_ROOT}" \
    --backend-host "${BACKEND_HOST}" \
    --expected-server-id "$(server_id_from_host "${BACKEND_HOST}")" \
    --expected-server-host "${BACKEND_HOST}" \
    --skip-frontend
  verify_backend
else
  bash "${FRONTEND_DEPLOY_SCRIPT}" \
    --archive "${RELEASE_ARCHIVE}" \
    --frontend-root "${INSTALL_ROOT}" \
    --nginx-env "${INSTALL_ROOT}/config/nginx.env"
  verify_frontend
fi
