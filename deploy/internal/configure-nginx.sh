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
FRONTEND_ROOT="${TEST_AGENT_FRONTEND_ROOT:-/data/testagent/frontend}"
NGINX_BACKENDS="${TEST_AGENT_NGINX_BACKENDS:-}"
NGINX_CONF_PATH="${TEST_AGENT_NGINX_CONF_PATH:-/etc/nginx/conf.d/test-agent-gateway.conf}"

[[ "${NGINX_MODE}" == "single" || "${NGINX_MODE}" == "multi" ]] || {
  echo "TEST_AGENT_NGINX_MODE must be single or multi" >&2
  exit 1
}
[[ "${NGINX_LISTEN_PORT}" =~ ^[0-9]{1,5}$ ]] && (( NGINX_LISTEN_PORT >= 1 && NGINX_LISTEN_PORT <= 65535 )) || {
  echo "Invalid TEST_AGENT_NGINX_LISTEN_PORT: ${NGINX_LISTEN_PORT}" >&2
  exit 1
}
[[ "${FRONTEND_ROOT}" =~ ^/[A-Za-z0-9._/-]+$ ]] || {
  echo "Invalid TEST_AGENT_FRONTEND_ROOT: ${FRONTEND_ROOT}" >&2
  exit 1
}
[[ "${NGINX_CONF_PATH}" =~ ^/[A-Za-z0-9._/-]+\.conf$ ]] || {
  echo "Invalid TEST_AGENT_NGINX_CONF_PATH: ${NGINX_CONF_PATH}" >&2
  exit 1
}

IFS=',' read -r -a raw_backends <<<"${NGINX_BACKENDS}"
backend_directives=()
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
done

if [[ "${NGINX_MODE}" == "single" && "${#backend_directives[@]}" -ne 1 ]]; then
  echo "single mode requires exactly one TEST_AGENT_NGINX_BACKENDS endpoint" >&2
  exit 1
fi
if [[ "${NGINX_MODE}" == "multi" && "${#backend_directives[@]}" -lt 2 ]]; then
  echo "multi mode requires at least two TEST_AGENT_NGINX_BACKENDS endpoints" >&2
  exit 1
fi

rendered="$(mktemp)"
installed_new="${NGINX_CONF_PATH}.new.$$"
backup=""
cleanup() {
  rm -f "${rendered}" "${installed_new}"
}
trap cleanup EXIT

listen_token='${TEST_AGENT_NGINX_LISTEN_PORT}'
root_token='${TEST_AGENT_FRONTEND_ROOT}'
backends_token='${TEST_AGENT_BACKEND_SERVERS}'
while IFS= read -r line || [[ -n "${line}" ]]; do
  if [[ "${line}" == *"${backends_token}"* ]]; then
    indent="${line%%"${backends_token}"*}"
    for directive in "${backend_directives[@]}"; do
      printf '%s%s\n' "${indent}" "${directive}" >>"${rendered}"
    done
    continue
  fi
  line="${line//${listen_token}/${NGINX_LISTEN_PORT}}"
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
  printf 'backend count: %s\n' "${#backend_directives[@]}"
  exit 0
fi

require_command nginx
require_command systemctl
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

if ! nginx -t; then
  rollback
  nginx -t || true
  echo "Nginx validation failed; previous gateway configuration restored" >&2
  exit 1
fi
if ! nginx -T 2>&1 | grep -F "# configuration file ${NGINX_CONF_PATH}:" >/dev/null; then
  rollback
  nginx -t || true
  echo "Nginx does not include ${NGINX_CONF_PATH}; set TEST_AGENT_NGINX_CONF_PATH to an included .conf file" >&2
  exit 1
fi
if ! systemctl reload nginx; then
  rollback
  nginx -t || true
  systemctl reload nginx || true
  echo "Nginx reload failed; previous gateway configuration restored" >&2
  exit 1
fi

printf 'Installed %s gateway with %s backend(s): %s\n' \
  "${NGINX_MODE}" "${#backend_directives[@]}" "${NGINX_CONF_PATH}"
