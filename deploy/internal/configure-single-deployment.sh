#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROLE=""

BACKEND_ENV="/data/testagent/config/backend.env"
DOCKER_ENV="/data/testagent/config/docker.env"
NGINX_ENV="/data/testagent/config/nginx.env"
BACKEND_TEMPLATE="${SCRIPT_DIR}/backend.env.example"
DOCKER_TEMPLATE="${SCRIPT_DIR}/env.example"

NGINX_HOME="/data/apps/nginx"
NGINX_BIN=""
NGINX_MAIN_CONF=""
NGINX_GATEWAY_CONF=""

usage() {
  cat <<'USAGE'
Usage: deploy/internal/configure-single-deployment.sh <backend|frontend> [options]

Create the complete configuration files for the current single-backend deployment.

Backend role (run on 122.233.30.114):
  - rewrites /data/testagent/config/backend.env and docker.env from release templates;
  - preserves the existing database password, Redis password, API token, manager token,
    and internal proxy key without printing them;
  - removes duplicate and obsolete keys while keeping the current fixed IPs and paths.

Frontend role (run on 122.233.30.2):
  - detects the included .conf directory of /data/apps/nginx;
  - writes /data/testagent/config/nginx.env with the real Nginx binary, prefix,
    main configuration and binary reload mode.

Options:
  --backend-env <path>       Backend dotenv path.
  --docker-env <path>        Worker dotenv path.
  --nginx-env <path>         Frontend Nginx dotenv path.
  --backend-template <path>  backend.env template path.
  --docker-template <path>   docker.env template path.
  --nginx-home <path>        Nginx installation root. Default: /data/apps/nginx.
  --nginx-bin <path>         Nginx executable. Default: <nginx-home>/sbin/nginx.
  --nginx-main-conf <path>   Nginx main config. Default: <nginx-home>/conf/nginx.conf.
  --gateway-conf <path>      Included gateway .conf path. Auto-detected by default.
  -h, --help                 Show this help.

The backend role intentionally fails when any required existing secret is missing.
It never generates or prints production secrets.
USAGE
}

if [[ $# -gt 0 && "$1" != -* ]]; then
  ROLE="$1"
  shift
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backend-env)
      BACKEND_ENV="$2"
      shift 2
      ;;
    --docker-env)
      DOCKER_ENV="$2"
      shift 2
      ;;
    --nginx-env)
      NGINX_ENV="$2"
      shift 2
      ;;
    --backend-template)
      BACKEND_TEMPLATE="$2"
      shift 2
      ;;
    --docker-template)
      DOCKER_TEMPLATE="$2"
      shift 2
      ;;
    --nginx-home)
      NGINX_HOME="$2"
      shift 2
      ;;
    --nginx-bin)
      NGINX_BIN="$2"
      shift 2
      ;;
    --nginx-main-conf)
      NGINX_MAIN_CONF="$2"
      shift 2
      ;;
    --gateway-conf)
      NGINX_GATEWAY_CONF="$2"
      shift 2
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

# dotenv 以最后一次赋值为准；只读取值，不 source 文件，避免执行现场内容。
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

backup_file() {
  local file="$1"
  local timestamp="$2"
  if [[ -f "${file}" ]]; then
    cp -a "${file}" "${file}.bak.${timestamp}"
  fi
}

render_backend_template() {
  local output="$1"
  local db_password="$2"
  local redis_password="$3"
  local api_token="$4"
  local manager_token="$5"
  local proxy_key="$6"
  local line key value

  : >"${output}"
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    if [[ "${line}" != *=* ]]; then
      printf '%s\n' "${line}" >>"${output}"
      continue
    fi
    key="${line%%=*}"
    case "${key}" in
      TEST_AGENT_DB_PASSWORD) value="${db_password}" ;;
      TEST_AGENT_REDIS_PASSWORD) value="${redis_password}" ;;
      TEST_AGENT_API_TOKEN) value="${api_token}" ;;
      TEST_AGENT_OPENCODE_MANAGER_TOKEN) value="${manager_token}" ;;
      TEST_AGENT_INTERNAL_PROXY_API_KEY) value="${proxy_key}" ;;
      *)
        printf '%s\n' "${line}" >>"${output}"
        continue
        ;;
    esac
    printf '%s=%s\n' "${key}" "${value}" >>"${output}"
  done <"${BACKEND_TEMPLATE}"
}

render_docker_template() {
  local output="$1"
  local manager_token="$2"
  local line key

  : >"${output}"
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    if [[ "${line}" == TEST_AGENT_OPENCODE_MANAGER_TOKEN=* ]]; then
      key="${line%%=*}"
      printf '%s=%s\n' "${key}" "${manager_token}" >>"${output}"
    else
      printf '%s\n' "${line}" >>"${output}"
    fi
  done <"${DOCKER_TEMPLATE}"
}

configure_backend() {
  local timestamp config_dir backend_tmp docker_tmp
  local db_password redis_password api_token backend_manager_token docker_manager_token manager_token proxy_key

  require_file "${BACKEND_TEMPLATE}"
  require_file "${DOCKER_TEMPLATE}"
  require_file "${BACKEND_ENV}"
  require_absolute_path "${BACKEND_ENV}" BACKEND_ENV
  require_absolute_path "${DOCKER_ENV}" DOCKER_ENV

  db_password="$(env_value "${BACKEND_ENV}" TEST_AGENT_DB_PASSWORD)"
  redis_password="$(env_value "${BACKEND_ENV}" TEST_AGENT_REDIS_PASSWORD)"
  api_token="$(env_value "${BACKEND_ENV}" TEST_AGENT_API_TOKEN)"
  backend_manager_token="$(env_value "${BACKEND_ENV}" TEST_AGENT_OPENCODE_MANAGER_TOKEN)"
  docker_manager_token="$(env_value "${DOCKER_ENV}" TEST_AGENT_OPENCODE_MANAGER_TOKEN)"
  proxy_key="$(env_value "${BACKEND_ENV}" TEST_AGENT_INTERNAL_PROXY_API_KEY)"

  [[ -n "${db_password}" ]] || {
    echo "TEST_AGENT_DB_PASSWORD is missing from ${BACKEND_ENV}" >&2
    exit 1
  }
  [[ -n "${backend_manager_token}" || -n "${docker_manager_token}" ]] || {
    echo "TEST_AGENT_OPENCODE_MANAGER_TOKEN is missing from backend.env and docker.env" >&2
    exit 1
  }
  if [[ -n "${backend_manager_token}" && -n "${docker_manager_token}" && "${backend_manager_token}" != "${docker_manager_token}" ]]; then
    echo "Manager tokens differ between backend.env and docker.env; no file was changed" >&2
    exit 1
  fi
  manager_token="${backend_manager_token:-${docker_manager_token}}"
  [[ -n "${proxy_key}" ]] || {
    echo "TEST_AGENT_INTERNAL_PROXY_API_KEY is missing from ${BACKEND_ENV}" >&2
    exit 1
  }

  config_dir="$(dirname "${BACKEND_ENV}")"
  [[ "$(dirname "${DOCKER_ENV}")" == "${config_dir}" ]] || {
    echo "backend.env and docker.env must be in the same config directory" >&2
    exit 1
  }
  install -d -m 0755 "${config_dir}"
  backend_tmp="$(mktemp "${config_dir}/.backend.env.new.XXXXXX")"
  docker_tmp="$(mktemp "${config_dir}/.docker.env.new.XXXXXX")"
  trap 'rm -f "${backend_tmp:-}" "${docker_tmp:-}"' EXIT

  render_backend_template "${backend_tmp}" "${db_password}" "${redis_password}" "${api_token}" "${manager_token}" "${proxy_key}"
  render_docker_template "${docker_tmp}" "${manager_token}"

  if grep -q 'REPLACE_' "${backend_tmp}" "${docker_tmp}"; then
    echo "Rendered configuration still contains REPLACE_ placeholders" >&2
    exit 1
  fi
  [[ "$(grep -c '^TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT=' "${backend_tmp}")" -eq 1 ]] || {
    echo "backend.env must contain exactly one manager command timeout" >&2
    exit 1
  }
  grep -Fxq 'TEST_AGENT_DB_URL=jdbc:postgresql://122.233.30.147:5432/postgres' "${backend_tmp}"
  grep -Fxq 'TEST_AGENT_DB_USERNAME=postgres' "${backend_tmp}"
  grep -Fxq 'TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.114' "${backend_tmp}"
  grep -Fxq 'SYS_DATA_ROOT_DIR=/data/testagent/data' "${backend_tmp}"
  grep -Fxq 'TEST_AGENT_SERVER_TERMINAL_ENABLED=true' "${backend_tmp}"
  grep -Eq '^TEST_AGENT_SERVER_TERMINAL_PUBLIC_WEBSOCKET_BASE_URL=wss://[^[:space:]]+$' "${backend_tmp}"
  grep -Fxq 'TEST_AGENT_DATA_ROOT=/data/testagent/data' "${docker_tmp}"
  if grep -q '^TEST_AGENT_INTERNAL_PROXY_API_KEY=' "${docker_tmp}"; then
    echo "Internal proxy key must not be written to docker.env" >&2
    exit 1
  fi

  timestamp="$(date +%Y%m%d%H%M%S)"
  backup_file "${BACKEND_ENV}" "${timestamp}"
  backup_file "${DOCKER_ENV}" "${timestamp}"
  chmod 0600 "${backend_tmp}" "${docker_tmp}"
  mv "${backend_tmp}" "${BACKEND_ENV}"
  mv "${docker_tmp}" "${DOCKER_ENV}"
  trap - EXIT

  printf 'Updated backend configuration: %s\n' "${BACKEND_ENV}"
  printf 'Updated worker configuration: %s\n' "${DOCKER_ENV}"
  printf 'Preserved existing secrets without printing them. Restart Java before the worker.\n'
}

resolve_relative_include_dir() {
  local pattern="$1"
  local main_dir candidate
  main_dir="$(dirname "${NGINX_MAIN_CONF}")"
  pattern="${pattern%\;}"
  pattern="${pattern%%\**}"
  pattern="${pattern%/}"
  if [[ "${pattern}" == /* ]]; then
    printf '%s' "${pattern}"
    return 0
  fi
  candidate="${main_dir}/${pattern}"
  if [[ -d "${candidate}" ]]; then
    printf '%s' "${candidate}"
    return 0
  fi
  candidate="${NGINX_HOME}/${pattern}"
  if [[ -d "${candidate}" ]]; then
    printf '%s' "${candidate}"
    return 0
  fi
  return 1
}

# 在候选目录写入仅含注释的临时配置，并以 nginx -T 确认新文件确实会被加载。
# 不能只因为同目录已有一个显式 include 文件，就推断新建的同级文件也会被 include。
gateway_directory_accepts_new_conf() {
  local candidate_dir="$1" probe probe_dump included=1
  [[ -d "${candidate_dir}" && -w "${candidate_dir}" ]] || return 1

  probe="${candidate_dir}/test-agent-include-probe.$$.$RANDOM.conf"
  printf '# test-agent include probe\n' >"${probe}" || return 1
  if probe_dump="$("${NGINX_BIN}" -p "${NGINX_HOME%/}/" -c "${NGINX_MAIN_CONF}" -T 2>&1)"; then
    if printf '%s\n' "${probe_dump}" | grep -F "# configuration file ${probe}:" >/dev/null; then
      included=0
    fi
  fi
  rm -f "${probe}"
  return "${included}"
}

# 优先检查 Nginx 实际已加载的子配置目录，但必须用临时文件证明该目录接受新的 *.conf。
# 没有现存子配置时，再解析生效配置和主配置中的 *.conf include，并执行同样的探测。
detect_gateway_conf() {
  local dump loaded_path loaded_dir include_pattern include_dir
  if ! dump="$("${NGINX_BIN}" -p "${NGINX_HOME%/}/" -c "${NGINX_MAIN_CONF}" -T 2>&1)"; then
    echo "Unable to inspect Nginx with ${NGINX_BIN}; check ${NGINX_MAIN_CONF}" >&2
    printf '%s\n' "${dump}" >&2
    return 1
  fi

  while IFS= read -r loaded_path; do
    [[ -n "${loaded_path}" ]] || continue
    [[ "${loaded_path}" == "${NGINX_MAIN_CONF}" ]] && continue
    [[ "${loaded_path}" == *.conf ]] || continue
    loaded_dir="$(dirname "${loaded_path}")"
    if [[ "${loaded_dir}" == "${NGINX_HOME}"/* || "${loaded_dir}" == "${NGINX_HOME}" ]]; then
      if gateway_directory_accepts_new_conf "${loaded_dir}"; then
        printf '%s/test-agent-gateway.conf' "${loaded_dir}"
        return 0
      fi
    fi
  done < <(printf '%s\n' "${dump}" | sed -n 's/^# configuration file \(.*\.conf\):$/\1/p')

  while IFS= read -r include_pattern; do
    [[ -n "${include_pattern}" ]] || continue
    if include_dir="$(resolve_relative_include_dir "${include_pattern}")" \
        && gateway_directory_accepts_new_conf "${include_dir}"; then
      printf '%s/test-agent-gateway.conf' "${include_dir}"
      return 0
    fi
  done < <(printf '%s\n' "${dump}" \
    | sed -nE 's/^[[:space:]]*include[[:space:]]+([^;]*\/\*\.conf)[[:space:]]*;.*/\1/p')

  while IFS= read -r include_pattern; do
    [[ -n "${include_pattern}" ]] || continue
    if include_dir="$(resolve_relative_include_dir "${include_pattern}")" \
        && gateway_directory_accepts_new_conf "${include_dir}"; then
      printf '%s/test-agent-gateway.conf' "${include_dir}"
      return 0
    fi
  done < <(sed -nE 's/^[[:space:]]*include[[:space:]]+([^;]*\/\*\.conf)[[:space:]]*;.*/\1/p' "${NGINX_MAIN_CONF}")

  echo "No directory that loads newly added *.conf files was detected under ${NGINX_HOME}" >&2
  echo "Add a dedicated wildcard include inside nginx.conf, or pass --gateway-conf in an existing wildcard include directory" >&2
  return 1
}

configure_frontend() {
  local timestamp config_dir nginx_tmp gateway_dir

  NGINX_BIN="${NGINX_BIN:-${NGINX_HOME%/}/sbin/nginx}"
  NGINX_MAIN_CONF="${NGINX_MAIN_CONF:-${NGINX_HOME%/}/conf/nginx.conf}"
  require_absolute_path "${NGINX_HOME}" NGINX_HOME
  require_absolute_path "${NGINX_BIN}" NGINX_BIN
  require_absolute_path "${NGINX_MAIN_CONF}" NGINX_MAIN_CONF
  require_absolute_path "${NGINX_ENV}" NGINX_ENV
  [[ -x "${NGINX_BIN}" ]] || {
    echo "Nginx executable not found: ${NGINX_BIN}" >&2
    exit 1
  }
  require_file "${NGINX_MAIN_CONF}"

  if [[ -z "${NGINX_GATEWAY_CONF}" ]]; then
    NGINX_GATEWAY_CONF="$(detect_gateway_conf)"
  fi
  require_absolute_path "${NGINX_GATEWAY_CONF}" NGINX_GATEWAY_CONF
  [[ "${NGINX_GATEWAY_CONF}" == *.conf ]] || {
    echo "Gateway config path must end in .conf: ${NGINX_GATEWAY_CONF}" >&2
    exit 1
  }

  gateway_dir="$(dirname "${NGINX_GATEWAY_CONF}")"
  install -d -m 0755 "$(dirname "${NGINX_ENV}")" "${gateway_dir}"
  config_dir="$(dirname "${NGINX_ENV}")"
  nginx_tmp="$(mktemp "${config_dir}/.nginx.env.new.XXXXXX")"
  trap 'rm -f "${nginx_tmp:-}"' EXIT
  cat >"${nginx_tmp}" <<EOF
TEST_AGENT_NGINX_MODE=single
TEST_AGENT_NGINX_BACKENDS=122.233.30.114:8080
TEST_AGENT_NGINX_TERMINAL_ROUTES=test-agent-backend-122-233-30-114=122.233.30.114:8080
TEST_AGENT_NGINX_LISTEN_PORT=80
TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS=
TEST_AGENT_NGINX_TLS_ENABLED=false
TEST_AGENT_FRONTEND_ROOT=/data/testagent/frontend
TEST_AGENT_NGINX_CONF_PATH=${NGINX_GATEWAY_CONF}
TEST_AGENT_NGINX_BIN=${NGINX_BIN}
TEST_AGENT_NGINX_PREFIX=${NGINX_HOME%/}
TEST_AGENT_NGINX_MAIN_CONF=${NGINX_MAIN_CONF}
TEST_AGENT_NGINX_RELOAD_MODE=binary
EOF

  timestamp="$(date +%Y%m%d%H%M%S)"
  backup_file "${NGINX_ENV}" "${timestamp}"
  chmod 0644 "${nginx_tmp}"
  mv "${nginx_tmp}" "${NGINX_ENV}"
  trap - EXIT

  printf 'Updated frontend Nginx configuration: %s\n' "${NGINX_ENV}"
  printf 'Nginx binary: %s\n' "${NGINX_BIN}"
  printf 'Nginx main config: %s\n' "${NGINX_MAIN_CONF}"
  printf 'Gateway config: %s\n' "${NGINX_GATEWAY_CONF}"
}

if [[ "${ROLE}" == "backend" ]]; then
  configure_backend
else
  configure_frontend
fi
