#!/usr/bin/env bash
set -euo pipefail

ARCHIVE="/data/0709/test-agent-internal-release.zip"
EXTRACT_DIR="/data/0709/test-agent-internal-frontend"
EXTRACT_DIR_EXPLICIT=0
FRONTEND_ROOT="/data/testagent"
FRONTEND_HEALTH_URL="http://122.233.30.2/health"
FRONTEND_URL="http://122.233.30.2/"
NGINX_ENV="/data/testagent/config/nginx.env"
KEEP_EXTRACT=0
VALIDATE_ONLY=0

usage() {
  cat <<'USAGE'
Usage: deploy/internal/deploy-internal-frontend.sh [options]

Update the enterprise frontend on the frontend Nginx server itself. Use this
when the backend server cannot ssh/scp to the frontend server because unified
login or bastion policy blocks direct key-based access.

Options:
  --archive <path>             Release zip path. Default: /data/0709/test-agent-internal-release.zip.
  --extract-dir <path>         Temporary unzip directory. Default: /data/0709/test-agent-internal-frontend.
  --frontend-root <path>       Frontend install root. Default: /data/testagent.
  --frontend-health-url <url>  Frontend health URL. Default: http://122.233.30.2/health.
  --frontend-url <url>         Frontend page URL. Default: http://122.233.30.2/.
  --nginx-env <path>           Nginx env path. Default: /data/testagent/config/nginx.env.
  --keep-extract              Keep extracted temporary files after success.
  --validate-only             Only unzip and validate frontend artifacts, without deploying.
  -h, --help                  Show this help.
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
      EXTRACT_DIR_EXPLICIT=1
      shift 2
      ;;
    --frontend-root)
      FRONTEND_ROOT="$2"
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
    --nginx-env)
      NGINX_ENV="$2"
      shift 2
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

# 仅校验包时使用系统临时目录，不要求校验机存在生产路径 /data/testagent。
if [[ "${VALIDATE_ONLY}" -eq 1 && "${EXTRACT_DIR_EXPLICIT}" -eq 0 ]]; then
  EXTRACT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-internal-frontend-validate.XXXXXX")"
fi

cleanup_validate_extract() {
  if [[ "${VALIDATE_ONLY}" -eq 1 && "${KEEP_EXTRACT}" -eq 0 ]]; then
    rm -rf "${EXTRACT_DIR}"
  fi
}
trap cleanup_validate_extract EXIT

log() {
  printf '\n[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
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

find_first_file() {
  local root="$1"
  local name="$2"
  # 兼容完整升级包和只包含 dist 的临时包。
  find "${root}" -maxdepth 6 -type f -name "${name}" | sort | head -n 1
}

find_local_client_dist() {
  find "$1" -maxdepth 6 -type d -path '*/dist/local-opencode-client' | sort | head -n 1
}

manifest_value() {
  local file="$1" key="$2"
  [[ -f "${file}" ]] || return 0
  awk -F= -v wanted="${key}" '$1 == wanted { value=substr($0, index($0, "=") + 1) } END { print value }' \
    "${file}"
}

verify_local_client_root() {
  local root="$1"
  bash "${DEPLOY_INTERNAL_SRC}/verify-local-opencode-client-distribution.sh" \
    --root "${root}" \
    --expected-version "${LOCAL_CLIENT_VERSION}" \
    --expected-manifest-sha256 "${LOCAL_CLIENT_MANIFEST_SHA256}" \
    --expected-signature-sha256 "${LOCAL_CLIENT_SIGNATURE_SHA256}" \
    --expected-install-sha256 "${LOCAL_CLIENT_INSTALL_SHA256}" \
    --expected-user-package-sha256 "${LOCAL_CLIENT_USER_PACKAGE_SHA256}"
}

require_command unzip
require_command find
require_command tar
require_file "${ARCHIVE}"

if [[ "${VALIDATE_ONLY}" -eq 0 ]]; then
  require_command curl
fi

log "Extract release archive"
rm -rf "${EXTRACT_DIR}"
mkdir -p "${EXTRACT_DIR}"
unzip -q "${ARCHIVE}" -d "${EXTRACT_DIR}"

FRONTEND_ARCHIVE="$(find_first_file "${EXTRACT_DIR}" 'test-agent-frontend-dist.tar.gz')"
DEPLOY_FRONTEND_SCRIPT="$(find_first_file "${EXTRACT_DIR}" 'deploy-internal-frontend.sh')"
CONFIGURE_NGINX_SCRIPT="$(find_first_file "${EXTRACT_DIR}" 'configure-nginx.sh')"
LOCAL_CLIENT_DIST="$(find_local_client_dist "${EXTRACT_DIR}")"
DEPLOY_INTERNAL_SRC=""
if [[ -n "${DEPLOY_FRONTEND_SCRIPT}" ]]; then
  DEPLOY_INTERNAL_SRC="$(cd "$(dirname "${DEPLOY_FRONTEND_SCRIPT}")" && pwd)"
fi
COMPONENT_MANIFEST="${DEPLOY_INTERNAL_SRC}/release-components.env"
LOCAL_CLIENT_COMPONENT_MODE="$(manifest_value "${COMPONENT_MANIFEST}" \
  TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT)"
LOCAL_CLIENT_COMPONENT_MODE="${LOCAL_CLIENT_COMPONENT_MODE:-included}"
LOCAL_CLIENT_FINGERPRINT="$(manifest_value "${COMPONENT_MANIFEST}" \
  TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_FINGERPRINT)"
LOCAL_CLIENT_VERSION="$(manifest_value "${COMPONENT_MANIFEST}" \
  TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_VERSION)"
LOCAL_CLIENT_MANIFEST_SHA256="$(manifest_value "${COMPONENT_MANIFEST}" \
  TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_MANIFEST_SHA256)"
LOCAL_CLIENT_SIGNATURE_SHA256="$(manifest_value "${COMPONENT_MANIFEST}" \
  TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_SIGNATURE_SHA256)"
LOCAL_CLIENT_INSTALL_SHA256="$(manifest_value "${COMPONENT_MANIFEST}" \
  TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_INSTALL_SHA256)"
LOCAL_CLIENT_USER_PACKAGE_SHA256="$(manifest_value "${COMPONENT_MANIFEST}" \
  TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT_USER_PACKAGE_SHA256)"

require_file "${FRONTEND_ARCHIVE}"
require_file "${CONFIGURE_NGINX_SCRIPT}"
if [[ -z "${DEPLOY_INTERNAL_SRC}" || ! -d "${DEPLOY_INTERNAL_SRC}" ]]; then
  echo "deploy/internal directory not found in archive" >&2
  exit 1
fi
require_file "${DEPLOY_INTERNAL_SRC}/verify-local-opencode-client-distribution.sh"
[[ "${LOCAL_CLIENT_COMPONENT_MODE}" == included || "${LOCAL_CLIENT_COMPONENT_MODE}" == reuse ]] || {
  echo "Invalid TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT: ${LOCAL_CLIENT_COMPONENT_MODE}" >&2
  exit 1
}
if [[ "${LOCAL_CLIENT_COMPONENT_MODE}" == included ]]; then
  require_file "${LOCAL_CLIENT_DIST}/install.sh"
  require_file "${LOCAL_CLIENT_DIST}/TestAgent-Local-Client-Kylin-arm64.tar.gz"
  require_file "${LOCAL_CLIENT_DIST}/stable/manifest.json"
  require_file "${LOCAL_CLIENT_DIST}/stable/manifest.json.sig"
  # 旧全量包没有组件哈希字段时继续兼容；新包必须执行完整逐文件校验。
  if [[ -n "${LOCAL_CLIENT_FINGERPRINT}${LOCAL_CLIENT_MANIFEST_SHA256}${LOCAL_CLIENT_SIGNATURE_SHA256}${LOCAL_CLIENT_INSTALL_SHA256}${LOCAL_CLIENT_USER_PACKAGE_SHA256}" ]]; then
    [[ "${LOCAL_CLIENT_FINGERPRINT}" =~ ^[0-9a-f]{64}$ ]] || {
      echo "Invalid local client component fingerprint" >&2
      exit 1
    }
    verify_local_client_root "${LOCAL_CLIENT_DIST}"
  fi
else
  [[ -z "${LOCAL_CLIENT_DIST}" ]] || {
    echo "Local client reuse release must not embed dist/local-opencode-client" >&2
    exit 1
  }
  [[ "${LOCAL_CLIENT_FINGERPRINT}" =~ ^[0-9a-f]{64}$ \
    && -n "${LOCAL_CLIENT_VERSION}" \
    && "${LOCAL_CLIENT_MANIFEST_SHA256}" =~ ^[0-9a-f]{64}$ \
    && "${LOCAL_CLIENT_SIGNATURE_SHA256}" =~ ^[0-9a-f]{64}$ \
    && "${LOCAL_CLIENT_INSTALL_SHA256}" =~ ^[0-9a-f]{64}$ \
    && "${LOCAL_CLIENT_USER_PACKAGE_SHA256}" =~ ^[0-9a-f]{64}$ ]] || {
    echo "Local client reuse metadata is missing or invalid" >&2
    exit 1
  }
fi

if [[ "${VALIDATE_ONLY}" -eq 1 ]]; then
  log "Frontend archive validation passed"
  printf 'frontend archive: %s\n' "${FRONTEND_ARCHIVE}"
  printf 'deploy internal: %s\n' "${DEPLOY_INTERNAL_SRC}"
  printf 'local client component: %s\n' "${LOCAL_CLIENT_COMPONENT_MODE}"
  if [[ "${KEEP_EXTRACT}" -eq 0 ]]; then
    rm -rf "${EXTRACT_DIR}"
  fi
  exit 0
fi

require_file "${NGINX_ENV}"

log "Update frontend under ${FRONTEND_ROOT}"
timestamp="$(date +%Y%m%d%H%M%S)"
mkdir -p "${FRONTEND_ROOT}/frontend" "${FRONTEND_ROOT}/dist" "${FRONTEND_ROOT}/deploy"
cp "${FRONTEND_ARCHIVE}" "${FRONTEND_ROOT}/dist/test-agent-frontend-dist.tar.gz"
if [[ "${LOCAL_CLIENT_COMPONENT_MODE}" == included ]]; then
  rm -rf "${FRONTEND_ROOT}/dist/local-opencode-client.new"
  cp -a "${LOCAL_CLIENT_DIST}" "${FRONTEND_ROOT}/dist/local-opencode-client.new"
else
  # reuse 必须在替换静态前端或 reload Nginx 前证明现网分发目录与发布清单逐字一致。
  verify_local_client_root "${FRONTEND_ROOT}/dist/local-opencode-client"
fi

rm -rf "${FRONTEND_ROOT}/deploy/internal.new"
cp -a "${DEPLOY_INTERNAL_SRC}" "${FRONTEND_ROOT}/deploy/internal.new"
if [[ -d "${FRONTEND_ROOT}/deploy/internal" ]]; then
  rm -rf "${FRONTEND_ROOT}/deploy/internal.bak.${timestamp}"
  mv "${FRONTEND_ROOT}/deploy/internal" "${FRONTEND_ROOT}/deploy/internal.bak.${timestamp}"
fi
mv "${FRONTEND_ROOT}/deploy/internal.new" "${FRONTEND_ROOT}/deploy/internal"

if [[ -d "${FRONTEND_ROOT}/frontend" ]]; then
  rm -rf "${FRONTEND_ROOT}/frontend.bak.${timestamp}"
  cp -a "${FRONTEND_ROOT}/frontend" "${FRONTEND_ROOT}/frontend.bak.${timestamp}"
fi

tar -C "${FRONTEND_ROOT}" -xzf "${FRONTEND_ROOT}/dist/test-agent-frontend-dist.tar.gz"
if [[ "${LOCAL_CLIENT_COMPONENT_MODE}" == included ]]; then
  if [[ -d "${FRONTEND_ROOT}/dist/local-opencode-client" ]]; then
    rm -rf "${FRONTEND_ROOT}/dist/local-opencode-client.bak.${timestamp}"
    mv "${FRONTEND_ROOT}/dist/local-opencode-client" "${FRONTEND_ROOT}/dist/local-opencode-client.bak.${timestamp}"
  fi
  mv "${FRONTEND_ROOT}/dist/local-opencode-client.new" "${FRONTEND_ROOT}/dist/local-opencode-client"
fi
bash "${FRONTEND_ROOT}/deploy/internal/configure-nginx.sh" --env-file "${NGINX_ENV}"
curl -fsS "${FRONTEND_HEALTH_URL}" >/dev/null
curl -fsS "${FRONTEND_URL}" >/dev/null

if [[ "${KEEP_EXTRACT}" -eq 0 ]]; then
  rm -rf "${EXTRACT_DIR}"
fi

log "Frontend deployment finished"
