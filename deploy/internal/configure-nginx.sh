#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="/data/testagent/config/nginx.env"
TEMPLATE="${SCRIPT_DIR}/nginx/gateway.conf.template"
VALIDATE_ONLY=0

usage() {
  cat <<'USAGE'
Usage: deploy/internal/configure-nginx.sh [options]

Render and install the enterprise frontend gateway for one or multiple Java backends.

Options:
  --env-file <path>   Nginx dotenv path. Default: /data/testagent/config/nginx.env.
  --template <path>   Gateway template path. Default: deploy/internal/nginx/gateway.conf.template.
  --validate-only     Render and validate values without changing Nginx.
  -h, --help          Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --template)
      TEMPLATE="$2"
      shift 2
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

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_executable() {
  local executable="$1"
  if [[ "${executable}" == */* ]]; then
    if [[ ! -x "${executable}" ]]; then
      echo "Required executable not found: ${executable}" >&2
      exit 1
    fi
  else
    require_command "${executable}"
  fi
}

require_file() {
  if [[ ! -f "$1" ]]; then
    echo "Required file not found: $1" >&2
    exit 1
  fi
}

load_dotenv() {
  local file="$1"
  local line key value
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    [[ "${line}" == export\ * ]] && line="${line#export }"
    [[ "${line}" == *=* ]] || continue
    key="${line%%=*}"
    value="${line#*=}"
    key="${key//[[:space:]]/}"
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    if [[ -z "${!key+x}" ]]; then
      printf -v "${key}" '%s' "${value}"
      export "${key}"
    fi
  done <"${file}"
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "${value}"
}

require_file "${ENV_FILE}"
require_file "${TEMPLATE}"
load_dotenv "${ENV_FILE}"

NGINX_MODE="${TEST_AGENT_NGINX_MODE:-single}"
NGINX_LISTEN_PORT="${TEST_AGENT_NGINX_LISTEN_PORT:-80}"
NGINX_ADDITIONAL_LISTEN_PORTS="${TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS:-}"
FRONTEND_ROOT="${TEST_AGENT_FRONTEND_ROOT:-/data/testagent/frontend}"
NGINX_BACKENDS="${TEST_AGENT_NGINX_BACKENDS:-}"
NGINX_TERMINAL_ROUTES="${TEST_AGENT_NGINX_TERMINAL_ROUTES:-}"
NGINX_TLS_ENABLED="${TEST_AGENT_NGINX_TLS_ENABLED:-false}"
NGINX_TLS_CERTIFICATE="${TEST_AGENT_NGINX_TLS_CERTIFICATE:-}"
NGINX_TLS_CERTIFICATE_KEY="${TEST_AGENT_NGINX_TLS_CERTIFICATE_KEY:-}"
NGINX_CONF_PATH="${TEST_AGENT_NGINX_CONF_PATH:-/etc/nginx/conf.d/test-agent-gateway.conf}"
NGINX_BIN="${TEST_AGENT_NGINX_BIN:-nginx}"
NGINX_PREFIX="${TEST_AGENT_NGINX_PREFIX:-}"
NGINX_MAIN_CONF="${TEST_AGENT_NGINX_MAIN_CONF:-}"
NGINX_RELOAD_MODE="${TEST_AGENT_NGINX_RELOAD_MODE:-systemd}"
NGINX_SYSTEMD_SERVICE="${TEST_AGENT_NGINX_SYSTEMD_SERVICE:-nginx}"

[[ "${NGINX_MODE}" == "single" || "${NGINX_MODE}" == "multi" ]] || {
  echo "TEST_AGENT_NGINX_MODE must be single or multi" >&2
  exit 1
}
[[ "${NGINX_LISTEN_PORT}" =~ ^[0-9]{1,5}$ ]] && (( NGINX_LISTEN_PORT >= 1 && NGINX_LISTEN_PORT <= 65535 )) || {
  echo "Invalid TEST_AGENT_NGINX_LISTEN_PORT: ${NGINX_LISTEN_PORT}" >&2
  exit 1
}

# 一个 server 块可同时承接企业网关转发端口和实体 IP 直连端口；端口必须唯一，避免 Nginx 重复 listen。
listen_ports=("${NGINX_LISTEN_PORT}")
additional_listen_ports=()
if [[ -n "${NGINX_ADDITIONAL_LISTEN_PORTS}" ]]; then
  IFS=',' read -r -a raw_additional_listen_ports <<<"${NGINX_ADDITIONAL_LISTEN_PORTS}"
  for raw_listen_port in "${raw_additional_listen_ports[@]}"; do
    listen_port="$(trim "${raw_listen_port}")"
    [[ "${listen_port}" =~ ^[0-9]{1,5}$ ]] && (( listen_port >= 1 && listen_port <= 65535 )) || {
      echo "Invalid TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS entry: ${listen_port}" >&2
      exit 1
    }
    for configured_listen_port in "${listen_ports[@]}"; do
      [[ "${listen_port}" != "${configured_listen_port}" ]] || {
        echo "Duplicate Nginx listen port: ${listen_port}" >&2
        exit 1
      }
    done
    additional_listen_ports+=("${listen_port}")
    listen_ports+=("${listen_port}")
  done
fi
[[ "${FRONTEND_ROOT}" =~ ^/[A-Za-z0-9._/-]+$ ]] || {
  echo "Invalid TEST_AGENT_FRONTEND_ROOT: ${FRONTEND_ROOT}" >&2
  exit 1
}
[[ "${NGINX_CONF_PATH}" =~ ^/[A-Za-z0-9._/-]+\.conf$ ]] || {
  echo "Invalid TEST_AGENT_NGINX_CONF_PATH: ${NGINX_CONF_PATH}" >&2
  exit 1
}
[[ -z "${NGINX_PREFIX}" || "${NGINX_PREFIX}" =~ ^/[A-Za-z0-9._/-]+$ ]] || {
  echo "Invalid TEST_AGENT_NGINX_PREFIX: ${NGINX_PREFIX}" >&2
  exit 1
}
[[ -z "${NGINX_MAIN_CONF}" || "${NGINX_MAIN_CONF}" =~ ^/[A-Za-z0-9._/-]+\.conf$ ]] || {
  echo "Invalid TEST_AGENT_NGINX_MAIN_CONF: ${NGINX_MAIN_CONF}" >&2
  exit 1
}
[[ "${NGINX_RELOAD_MODE}" == "systemd" || "${NGINX_RELOAD_MODE}" == "binary" ]] || {
  echo "TEST_AGENT_NGINX_RELOAD_MODE must be systemd or binary" >&2
  exit 1
}
[[ "${NGINX_SYSTEMD_SERVICE}" =~ ^[A-Za-z0-9_.@-]+$ ]] || {
  echo "Invalid TEST_AGENT_NGINX_SYSTEMD_SERVICE: ${NGINX_SYSTEMD_SERVICE}" >&2
  exit 1
}
[[ "${NGINX_TLS_ENABLED}" == "true" || "${NGINX_TLS_ENABLED}" == "false" ]] || {
  echo "TEST_AGENT_NGINX_TLS_ENABLED must be true or false" >&2
  exit 1
}
if [[ "${NGINX_TLS_ENABLED}" == "true" ]]; then
  [[ "${NGINX_TLS_CERTIFICATE}" =~ ^/[A-Za-z0-9._/-]+$ ]] || {
    echo "Invalid TEST_AGENT_NGINX_TLS_CERTIFICATE" >&2
    exit 1
  }
  [[ "${NGINX_TLS_CERTIFICATE_KEY}" =~ ^/[A-Za-z0-9._/-]+$ ]] || {
    echo "Invalid TEST_AGENT_NGINX_TLS_CERTIFICATE_KEY" >&2
    exit 1
  }
  require_file "${NGINX_TLS_CERTIFICATE}"
  require_file "${NGINX_TLS_CERTIFICATE_KEY}"
fi

nginx_command=("${NGINX_BIN}")
if [[ -n "${NGINX_PREFIX}" ]]; then
  nginx_command+=("-p" "${NGINX_PREFIX%/}/")
fi
if [[ -n "${NGINX_MAIN_CONF}" ]]; then
  nginx_command+=("-c" "${NGINX_MAIN_CONF}")
fi

run_nginx() {
  "${nginx_command[@]}" "$@"
}

IFS=',' read -r -a raw_backends <<<"${NGINX_BACKENDS}"
backend_directives=()
backend_endpoints=()
for raw_backend in "${raw_backends[@]}"; do
  backend="$(trim "${raw_backend}")"
  [[ "${backend}" =~ ^([A-Za-z0-9.-]+):([0-9]{1,5})$ ]] || {
    echo "Invalid backend endpoint: ${backend}" >&2
    exit 1
  }
  backend_port="${BASH_REMATCH[2]}"
  (( backend_port >= 1 && backend_port <= 65535 )) || {
    echo "Invalid backend port: ${backend}" >&2
    exit 1
  }
  backend_directives+=("server ${backend} max_fails=3 fail_timeout=10s;")
  backend_endpoints+=("${backend}")
done

if [[ "${NGINX_MODE}" == "single" && "${#backend_directives[@]}" -ne 1 ]]; then
  echo "single mode requires exactly one TEST_AGENT_NGINX_BACKENDS endpoint" >&2
  exit 1
fi
if [[ "${NGINX_MODE}" == "multi" && "${#backend_directives[@]}" -lt 2 ]]; then
  echo "multi mode requires at least two TEST_AGENT_NGINX_BACKENDS endpoints" >&2
  exit 1
fi

terminal_route_ids=()
terminal_route_endpoints=()
if [[ -n "${NGINX_TERMINAL_ROUTES}" ]]; then
  IFS=',' read -r -a raw_terminal_routes <<<"${NGINX_TERMINAL_ROUTES}"
  for raw_route in "${raw_terminal_routes[@]}"; do
    route="$(trim "${raw_route}")"
    [[ "${route}" =~ ^([A-Za-z0-9._-]{1,128})=([A-Za-z0-9.-]+):([0-9]{1,5})$ ]] || {
      echo "Invalid terminal route: ${route}" >&2
      exit 1
    }
    route_id="${BASH_REMATCH[1]}"
    route_endpoint="${BASH_REMATCH[2]}:${BASH_REMATCH[3]}"
    route_known=0
    for backend_endpoint in "${backend_endpoints[@]}"; do
      [[ "${route_endpoint}" == "${backend_endpoint}" ]] && route_known=1
    done
    [[ "${route_known}" -eq 1 ]] || {
      echo "Terminal route endpoint must exist in TEST_AGENT_NGINX_BACKENDS: ${route_endpoint}" >&2
      exit 1
    }
    terminal_route_ids+=("${route_id}")
    terminal_route_endpoints+=("${route_endpoint}")
  done
fi

rendered="$(mktemp)"
installed_new="${NGINX_CONF_PATH}.new.$$"
backup=""
cleanup() {
  rm -f "${rendered}" "${installed_new}"
}
trap cleanup EXIT

root_token='${TEST_AGENT_FRONTEND_ROOT}'
backends_token='${TEST_AGENT_BACKEND_SERVERS}'
listen_token='${TEST_AGENT_NGINX_LISTEN_DIRECTIVE}'
additional_listen_token='${TEST_AGENT_NGINX_ADDITIONAL_LISTEN_DIRECTIVES}'
tls_token='${TEST_AGENT_NGINX_TLS_DIRECTIVES}'
terminal_token='${TEST_AGENT_TERMINAL_LOCATIONS}'
listen_directive="listen ${NGINX_LISTEN_PORT};"
additional_listen_directives=()
tls_directives=""
if [[ "${NGINX_TLS_ENABLED}" == "true" ]]; then
  listen_directive="listen ${NGINX_LISTEN_PORT} ssl;"
  tls_directives="ssl_certificate ${NGINX_TLS_CERTIFICATE}; ssl_certificate_key ${NGINX_TLS_CERTIFICATE_KEY}; ssl_protocols TLSv1.2 TLSv1.3;"
fi
if [[ -n "${NGINX_ADDITIONAL_LISTEN_PORTS}" ]]; then
  for additional_listen_port in "${additional_listen_ports[@]}"; do
    if [[ "${NGINX_TLS_ENABLED}" == "true" ]]; then
      additional_listen_directives+=("listen ${additional_listen_port} ssl;")
    else
      additional_listen_directives+=("listen ${additional_listen_port};")
    fi
  done
fi
while IFS= read -r line || [[ -n "${line}" ]]; do
  if [[ "${line}" == *"${backends_token}"* ]]; then
    indent="${line%%"${backends_token}"*}"
    for directive in "${backend_directives[@]}"; do
      printf '%s%s\n' "${indent}" "${directive}" >>"${rendered}"
    done
    continue
  fi
  if [[ "${line}" == *"${terminal_token}"* ]]; then
    indent="${line%%"${terminal_token}"*}"
    for ((index = 0; index < ${#terminal_route_ids[@]}; index++)); do
      route_id="${terminal_route_ids[index]}"
      route_endpoint="${terminal_route_endpoints[index]}"
      printf '%slocation = /api/internal/platform/opencode-runtime/management/linux-servers/%s/terminal/ws {\n' "${indent}" "${route_id}" >>"${rendered}"
      printf '%s    proxy_pass http://%s;\n' "${indent}" "${route_endpoint}" >>"${rendered}"
      printf '%s    proxy_http_version 1.1;\n' "${indent}" >>"${rendered}"
      printf '%s    proxy_set_header Host $host;\n' "${indent}" >>"${rendered}"
      printf '%s    proxy_set_header X-Real-IP $remote_addr;\n' "${indent}" >>"${rendered}"
      printf '%s    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n' "${indent}" >>"${rendered}"
      printf '%s    proxy_set_header X-Forwarded-Proto $scheme;\n' "${indent}" >>"${rendered}"
      printf '%s    proxy_set_header Upgrade $http_upgrade;\n' "${indent}" >>"${rendered}"
      printf '%s    proxy_set_header Connection $connection_upgrade;\n' "${indent}" >>"${rendered}"
      printf '%s    proxy_read_timeout 7200s;\n' "${indent}" >>"${rendered}"
      printf '%s    proxy_send_timeout 7200s;\n' "${indent}" >>"${rendered}"
      printf '%s    proxy_buffering off;\n' "${indent}" >>"${rendered}"
      printf '%s}\n' "${indent}" >>"${rendered}"
    done
    continue
  fi
  if [[ "${line}" == *"${additional_listen_token}"* ]]; then
    indent="${line%%"${additional_listen_token}"*}"
    if [[ -n "${NGINX_ADDITIONAL_LISTEN_PORTS}" ]]; then
      for directive in "${additional_listen_directives[@]}"; do
        printf '%s%s\n' "${indent}" "${directive}" >>"${rendered}"
      done
    fi
    continue
  fi
  line="${line//${listen_token}/${listen_directive}}"
  line="${line//${tls_token}/${tls_directives}}"
  line="${line//${root_token}/${FRONTEND_ROOT}}"
  printf '%s\n' "${line}" >>"${rendered}"
done <"${TEMPLATE}"

if grep -q '\${TEST_AGENT_' "${rendered}"; then
  echo "Unresolved gateway template variable" >&2
  exit 1
fi

if [[ "${VALIDATE_ONLY}" -eq 1 ]]; then
  printf 'nginx mode: %s\n' "${NGINX_MODE}"
  printf 'nginx config: %s\n' "${NGINX_CONF_PATH}"
  printf 'listen ports: %s\n' "$(IFS=,; printf '%s' "${listen_ports[*]}")"
  printf 'backend count: %s\n' "${#backend_directives[@]}"
  printf 'terminal route count: %s\n' "${#terminal_route_ids[@]}"
  printf 'tls enabled: %s\n' "${NGINX_TLS_ENABLED}"
  exit 0
fi

require_executable "${NGINX_BIN}"
if [[ "${NGINX_RELOAD_MODE}" == "systemd" ]]; then
  require_command systemctl
fi
mkdir -p "$(dirname "${NGINX_CONF_PATH}")"
if [[ -f "${NGINX_CONF_PATH}" ]]; then
  backup="${NGINX_CONF_PATH}.bak.$(date +%Y%m%d%H%M%S)"
  cp -a "${NGINX_CONF_PATH}" "${backup}"
fi
cp "${rendered}" "${installed_new}"
chmod 0644 "${installed_new}"
mv "${installed_new}" "${NGINX_CONF_PATH}"

rollback() {
  if [[ -n "${backup}" && -f "${backup}" ]]; then
    cp -a "${backup}" "${NGINX_CONF_PATH}"
  else
    rm -f "${NGINX_CONF_PATH}"
  fi
}

if ! run_nginx -t; then
  rollback
  run_nginx -t || true
  echo "Nginx validation failed; previous gateway configuration restored" >&2
  exit 1
fi
if ! run_nginx -T 2>&1 | grep -F "# configuration file ${NGINX_CONF_PATH}:" >/dev/null; then
  rollback
  run_nginx -t || true
  echo "Nginx does not include ${NGINX_CONF_PATH}; set TEST_AGENT_NGINX_CONF_PATH to an included .conf file" >&2
  exit 1
fi
if [[ "${NGINX_RELOAD_MODE}" == "systemd" ]]; then
  reload_nginx() {
    systemctl reload "${NGINX_SYSTEMD_SERVICE}"
  }
else
  reload_nginx() {
    run_nginx -s reload
  }
fi
if ! reload_nginx; then
  rollback
  run_nginx -t || true
  reload_nginx || true
  echo "Nginx reload failed; previous gateway configuration restored" >&2
  exit 1
fi

printf 'Installed %s gateway with %s backend(s): %s\n' \
  "${NGINX_MODE}" "${#backend_directives[@]}" "${NGINX_CONF_PATH}"
