#!/usr/bin/env bash
set -euo pipefail

ROLE=""
OUTPUT_DIR="/data/0709"
INSTALL_ROOT="/data/testagent"
NGINX_HOME="/data/apps/nginx"
BACKEND_SERVICE="test-agent-backend"
NODE_LABEL=""
INCLUDE_SENSITIVE=0
MAX_ARCHIVE_BYTES=1048576

usage() {
  cat <<'USAGE'
Usage: collect-multi-backend-context.sh <backend|frontend> --include-sensitive [options]

Collect only enterprise deployment configuration files into a small archive.
The archive is limited to 1 MiB and intentionally includes raw env secrets.

Options:
  --include-sensitive       Required explicit consent. Keeps raw passwords and tokens.
  --output-dir <path>       Archive output directory. Default: /data/0709.
  --node-label <label>      Archive node label. Defaults to .serverhost or hostname.
  --install-root <path>     Test Agent install root. Default: /data/testagent.
  --nginx-home <path>       Frontend Nginx home. Default: /data/apps/nginx.
  --backend-service <name>  Backend systemd service. Default: test-agent-backend.
  -h, --help                Show this help.

Never included: JAR/lib, RSA private key, logs, Docker inspect/images, programs,
worker image, deployed frontend, database dumps or application business data.
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
  echo "Refusing to collect raw configuration secrets without --include-sensitive" >&2
  exit 2
fi
for checked_path in "${OUTPUT_DIR}" "${INSTALL_ROOT}" "${NGINX_HOME}"; do
  if [[ ! "${checked_path}" =~ ^/[A-Za-z0-9._/-]+$ ]]; then
    echo "Configuration paths must be absolute and contain only safe characters: ${checked_path}" >&2
    exit 2
  fi
done
if [[ ! "${BACKEND_SERVICE}" =~ ^[A-Za-z0-9_.@-]+$ ]]; then
  echo "Invalid backend service name: ${BACKEND_SERVICE}" >&2
  exit 2
fi

umask 077
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-config.XXXXXX")"
BUNDLE_ROOT="${TMP_ROOT}/bundle"
mkdir -p "${BUNDLE_ROOT}/files" "${OUTPUT_DIR}"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

# dotenv 只按文本读取路径参数，不能 source 现场配置，避免执行其中的 shell 内容。
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

copy_file() {
  local source="$1"
  local relative="$2"
  [[ -f "${source}" ]] || return 0
  mkdir -p "$(dirname "${BUNDLE_ROOT}/files/${relative}")"
  cp -a "${source}" "${BUNDLE_ROOT}/files/${relative}"
}

sha256_digest() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${file}" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${file}" | awk '{print $1}'
  else
    echo "Neither sha256sum nor shasum is available" >&2
    return 1
  fi
}

file_size() {
  local file="$1"
  if stat -c '%s' "${file}" >/dev/null 2>&1; then
    stat -c '%s' "${file}"
  else
    stat -f '%z' "${file}"
  fi
}

collect_backend_configs() {
  copy_file "${INSTALL_ROOT}/config/backend.env" data/testagent/config/backend.env
  copy_file "${INSTALL_ROOT}/config/docker.env" data/testagent/config/docker.env
  copy_file "${INSTALL_ROOT}/data/.serverid" data/testagent/data/.serverid
  copy_file "${INSTALL_ROOT}/data/.serverhost" data/testagent/data/.serverhost

  # systemctl cat 会合并主 unit 与 drop-in，便于后续生成脚本时核对真实 EnvironmentFile/ExecStart。
  if command -v systemctl >/dev/null 2>&1; then
    mkdir -p "${BUNDLE_ROOT}/files/etc/systemd/system"
    systemctl cat "${BACKEND_SERVICE}" \
      >"${BUNDLE_ROOT}/files/etc/systemd/system/${BACKEND_SERVICE}.effective.service" \
      2>&1 || true
  fi
}

collect_frontend_configs() {
  local nginx_env="${INSTALL_ROOT}/config/nginx.env"
  local nginx_main_conf="${NGINX_HOME}/conf/nginx.conf"
  local nginx_gateway_conf="${NGINX_HOME}/conf/test-agent.conf"

  copy_file "${nginx_env}" data/testagent/config/nginx.env
  if [[ -f "${nginx_env}" ]]; then
    nginx_main_conf="$(env_value "${nginx_env}" TEST_AGENT_NGINX_MAIN_CONF)"
    nginx_gateway_conf="$(env_value "${nginx_env}" TEST_AGENT_NGINX_CONF_PATH)"
    nginx_main_conf="${nginx_main_conf:-${NGINX_HOME}/conf/nginx.conf}"
    nginx_gateway_conf="${nginx_gateway_conf:-${NGINX_HOME}/conf/test-agent.conf}"
  fi

  copy_file "${nginx_main_conf}" data/apps/nginx/conf/nginx.conf
  copy_file "${nginx_gateway_conf}" data/apps/nginx/conf/test-agent.conf
}

if [[ -z "${NODE_LABEL}" && "${ROLE}" == "backend" && -f "${INSTALL_ROOT}/data/.serverhost" ]]; then
  NODE_LABEL="$(head -n 1 "${INSTALL_ROOT}/data/.serverhost")"
fi
if [[ -z "${NODE_LABEL}" ]]; then
  NODE_LABEL="$(hostname -s 2>/dev/null || printf unknown)"
fi
NODE_LABEL="$(safe_label "${NODE_LABEL}")"
NODE_LABEL="${NODE_LABEL:-unknown}"

if [[ "${ROLE}" == "backend" ]]; then
  collect_backend_configs
else
  collect_frontend_configs
fi

cat >"${BUNDLE_ROOT}/README-SENSITIVE.txt" <<EOF
Configuration-only enterprise deployment bundle.

Role: ${ROLE}
Node: ${NODE_LABEL}
Collected: $(date '+%Y-%m-%dT%H:%M:%S%z')
Maximum archive bytes: ${MAX_ARCHIVE_BYTES}

This archive contains raw configuration secrets. It intentionally excludes JAR/lib,
RSA private keys, logs, Docker data, programs, worker images and deployed frontend files.
EOF
find "${BUNDLE_ROOT}" -type f -print | sed "s#^${BUNDLE_ROOT}/##" | sort \
  >"${BUNDLE_ROOT}/CONTENTS.txt"

timestamp="$(date '+%Y%m%d-%H%M%S')"
archive="${OUTPUT_DIR}/test-agent-config-SENSITIVE-${ROLE}-${NODE_LABEL}-${timestamp}.tar.gz"
tar -C "${BUNDLE_ROOT}" -czf "${archive}" .
archive_bytes="$(file_size "${archive}")"
if (( archive_bytes > MAX_ARCHIVE_BYTES )); then
  rm -f "${archive}"
  echo "Configuration archive exceeds 1 MiB (${archive_bytes} bytes); archive removed" >&2
  exit 1
fi
chmod 0600 "${archive}"
archive_digest="$(sha256_digest "${archive}")"
printf '%s  %s\n' "${archive_digest}" "$(basename "${archive}")" >"${archive}.sha256"
chmod 0600 "${archive}.sha256"

printf 'Configuration archive: %s\n' "${archive}"
printf 'Archive bytes: %s (limit %s)\n' "${archive_bytes}" "${MAX_ARCHIVE_BYTES}"
printf 'Checksum: %s.sha256\n' "${archive}"
