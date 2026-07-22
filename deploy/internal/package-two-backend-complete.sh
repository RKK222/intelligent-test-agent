#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RELEASE_ARCHIVE="${SCRIPT_DIR}/dist/test-agent-internal-release.zip"
NODES_DIR=""
OUTPUT_DIR="${SCRIPT_DIR}/dist"
BUNDLE_NAME="test-agent-two-backend-complete"

usage() {
  cat <<'USAGE'
Usage: package-two-backend-complete.sh --nodes-dir <path> [options]

Assemble the standard enterprise release ZIP and the three prepared node
packages into one fixed-name USB delivery bundle.

Options:
  --release-archive <path>  Standard release ZIP. Default: deploy/internal/dist/test-agent-internal-release.zip.
  --nodes-dir <path>        Directory containing the prepared .4, .114 and .2 node archives plus SHA files.
  --output-dir <path>       Output directory. Default: deploy/internal/dist.
  -h, --help                Show this help.

Fixed output names:
  test-agent-two-backend-complete.zip
  test-agent-two-backend-complete.zip.sha256

Existing fixed-name outputs are replaced without interaction. Source release
and node archives are validated but never modified. The output contains
sensitive node configuration and the JAR-embedded RSA key; handle it as a
controlled artifact.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release-archive)
      RELEASE_ARCHIVE="$2"
      shift 2
      ;;
    --nodes-dir)
      NODES_DIR="$2"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
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

sha256_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    echo "Neither sha256sum nor shasum is available" >&2
    exit 1
  fi
}

# SHA 文件必须指向同目录的实际文件名，避免误校验同目录中的历史版本。
verify_checksum_pair() {
  local file="$1"
  local checksum="${file}.sha256"
  local expected actual checksum_name
  require_file "${file}"
  require_file "${checksum}"
  checksum_name="$(awk 'NF >= 2 {print $2; exit}' "${checksum}")"
  checksum_name="${checksum_name#\*}"
  if [[ "${checksum_name}" != "$(basename "${file}")" ]]; then
    echo "Checksum file does not name $(basename "${file}"): ${checksum}" >&2
    exit 1
  fi
  expected="$(awk 'NF >= 2 {print $1; exit}' "${checksum}")"
  actual="$(sha256_digest "${file}")"
  if [[ -z "${expected}" || "${expected}" != "${actual}" ]]; then
    echo "SHA256 mismatch: ${file}" >&2
    exit 1
  fi
}

require_archive_entry() {
  local listing="$1"
  local entry="$2"
  if ! grep -Fx "${entry}" <<<"${listing}" >/dev/null; then
    echo "Required archive entry not found: ${entry}" >&2
    exit 1
  fi
}

if [[ -z "${NODES_DIR}" ]]; then
  echo "--nodes-dir is required" >&2
  usage >&2
  exit 2
fi

require_command zip
require_command unzip
require_command tar
require_command awk
require_file "${RELEASE_ARCHIVE}"
require_file "${SCRIPT_DIR}/deploy-node-common.sh"
require_file "${SCRIPT_DIR}/deploy-backend-node.sh"
require_file "${SCRIPT_DIR}/deploy-frontend-node.sh"
require_file "${SCRIPT_DIR}/deploy-mysql-node.sh"
require_file "${SCRIPT_DIR}/deploy-xxl-job-mysql.sh"
require_file "${SCRIPT_DIR}/init-backend-node-config.sh"
require_file "${SCRIPT_DIR}/register-backend-on-frontend.sh"

NODE_4="${NODES_DIR}/test-agent-two-backend-122.233.30.4-SENSITIVE.tar.gz"
NODE_114="${NODES_DIR}/test-agent-two-backend-122.233.30.114-SENSITIVE.tar.gz"
NODE_2="${NODES_DIR}/test-agent-two-backend-122.233.30.2.tar.gz"
NODE_MYSQL="${NODES_DIR}/test-agent-two-backend-122.233.30.147-mysql-SENSITIVE.tar.gz"

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-two-backend-complete.XXXXXX")"
cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

verify_checksum_pair "${RELEASE_ARCHIVE}"
verify_checksum_pair "${NODE_4}"
verify_checksum_pair "${NODE_114}"
verify_checksum_pair "${NODE_2}"

release_listing="$(unzip -Z1 "${RELEASE_ARCHIVE}")"
require_archive_entry "${release_listing}" dist/backend/test-agent-app.jar
require_archive_entry "${release_listing}" dist/test-agent-frontend-dist.tar.gz
require_archive_entry "${release_listing}" dist/test-agent-programs.tar.gz
require_archive_entry "${release_listing}" dist/test-agent-opencode-worker_internal-linux-amd64.tar
require_archive_entry "${release_listing}" dist/mysql_8.4-linux-amd64.tar
require_archive_entry "${release_listing}" deploy/internal/deploy-multi-backend-node.sh
require_archive_entry "${release_listing}" deploy/internal/deploy-xxl-job-mysql.sh

# 完整外层包仍按 RSA 密钥交付物管理，封装前必须确认内层 JAR 的固定资源存在。
unzip -p "${RELEASE_ARCHIVE}" dist/backend/test-agent-app.jar >"${TMP_ROOT}/test-agent-app.jar"
jar_listing="$(unzip -Z1 "${TMP_ROOT}/test-agent-app.jar")"
require_archive_entry "${jar_listing}" BOOT-INF/classes/rsa-private.key

validate_node_archive() {
  local archive="$1"
  local node_dir="$2"
  local required_config="$3"
  local listing archive_size
  archive_size="$(wc -c <"${archive}" | tr -d '[:space:]')"
  if [[ "${archive_size}" -gt 1048576 ]]; then
    echo "Prepared node archive exceeds 1 MiB: ${archive}" >&2
    exit 1
  fi
  listing="$(tar -tzf "${archive}")"
  if [[ "${node_dir}" == "test-agent-two-backend-122.233.30.147-mysql" ]]; then
    require_archive_entry "${listing}" "${node_dir}/deploy-xxl-job-mysql.sh"
  else
    require_archive_entry "${listing}" "${node_dir}/deploy-multi-backend-node.sh"
  fi
  require_archive_entry "${listing}" "${node_dir}/MULTI-BACKEND.md"
  require_archive_entry "${listing}" "${node_dir}/config/${required_config}"
}

validate_node_archive "${NODE_4}" test-agent-two-backend-122.233.30.4 backend.env
validate_node_archive "${NODE_4}" test-agent-two-backend-122.233.30.4 docker.env
validate_node_archive "${NODE_114}" test-agent-two-backend-122.233.30.114 backend.env
validate_node_archive "${NODE_114}" test-agent-two-backend-122.233.30.114 docker.env
validate_node_archive "${NODE_2}" test-agent-two-backend-122.233.30.2 nginx.env
validate_node_archive "${NODE_MYSQL}" test-agent-two-backend-122.233.30.147-mysql mysql.env

# 完整包必须在封装前确认四份配置引用同一组 MySQL 凭据，且后端已经切到 PostgreSQL 同机的 MySQL。
validate_mysql_cluster_config() {
  local config_root="${TMP_ROOT}/cluster-config-check"
  local backend_4 backend_114 frontend mysql_env expected_url
  local backend_password backend_token mysql_password
  mkdir -m 0700 -p "${config_root}"
  backend_4="${config_root}/backend-4.env"
  backend_114="${config_root}/backend-114.env"
  frontend="${config_root}/nginx.env"
  mysql_env="${config_root}/mysql.env"
  tar -xOzf "${NODE_4}" 'test-agent-two-backend-122.233.30.4/config/backend.env' >"${backend_4}"
  tar -xOzf "${NODE_114}" 'test-agent-two-backend-122.233.30.114/config/backend.env' >"${backend_114}"
  tar -xOzf "${NODE_2}" 'test-agent-two-backend-122.233.30.2/config/nginx.env' >"${frontend}"
  tar -xOzf "${NODE_MYSQL}" 'test-agent-two-backend-122.233.30.147-mysql/config/mysql.env' >"${mysql_env}"
  chmod 0600 "${backend_4}" "${backend_114}" "${frontend}" "${mysql_env}"
  if grep -q 'REPLACE_' "${backend_4}" "${backend_114}" "${frontend}" "${mysql_env}"; then
    echo "Prepared cluster configuration still contains a REPLACE_ placeholder" >&2
    exit 1
  fi
  expected_url='jdbc:mysql://122.233.30.147:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
  for key in TEST_AGENT_XXL_JOB_MYSQL_URL TEST_AGENT_XXL_JOB_MYSQL_USERNAME \
    TEST_AGENT_XXL_JOB_MYSQL_PASSWORD TEST_AGENT_XXL_JOB_ACCESS_TOKEN; do
    [[ "$(grep -c "^${key}=" "${backend_4}" || true)" -eq 1 \
      && "$(grep -c "^${key}=" "${backend_114}" || true)" -eq 1 ]] || {
      echo "Both backend configs must contain exactly one ${key}" >&2
      exit 1
    }
  done
  grep -Fxq "TEST_AGENT_XXL_JOB_MYSQL_URL=${expected_url}" "${backend_4}"
  grep -Fxq "TEST_AGENT_XXL_JOB_MYSQL_URL=${expected_url}" "${backend_114}"
  grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_USERNAME=xxl_job' "${backend_4}"
  grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_USERNAME=xxl_job' "${backend_114}"
  grep -Fxq 'TEST_AGENT_NGINX_XXL_JOB_ADMINS=122.233.30.4:18080,122.233.30.114:18080' "${frontend}"
  grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_IMAGE=mysql:8.4' "${mysql_env}"
  grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_HOST_PORT=3306' "${mysql_env}"
  grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_DATABASE=xxl_job' "${mysql_env}"
  grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_USERNAME=xxl_job' "${mysql_env}"

  backend_password="$(sed -n 's/^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=//p' "${backend_4}")"
  backend_token="$(sed -n 's/^TEST_AGENT_XXL_JOB_ACCESS_TOKEN=//p' "${backend_4}")"
  mysql_password="$(sed -n 's/^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=//p' "${mysql_env}")"
  [[ ${#backend_password} -ge 16 && ${#backend_token} -ge 32 ]] || {
    echo "Prepared XXL-JOB password or access token is too short" >&2
    exit 1
  }
  [[ "${backend_password}" == "$(sed -n 's/^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=//p' "${backend_114}")" \
    && "${backend_password}" == "${mysql_password}" ]] || {
    echo "MySQL application passwords differ across prepared node configs" >&2
    exit 1
  }
  [[ "${backend_token}" == "$(sed -n 's/^TEST_AGENT_XXL_JOB_ACCESS_TOKEN=//p' "${backend_114}")" ]] || {
    echo "XXL-JOB access tokens differ between backend configs" >&2
    exit 1
  }
}

validate_mysql_cluster_config

# 旧前端节点包可能仍只包含终端路由键。封装新交付包时在临时副本中完成一次性迁移，
# 不修改源敏感包，也不把任何路由值或其它 env 内容打印到日志。
normalize_frontend_server_routes() {
  local nginx_env="$1"
  local server_route_count legacy_route_count migrated
  server_route_count="$(grep -Ec '^TEST_AGENT_NGINX_SERVER_ROUTES=' "${nginx_env}" || true)"
  legacy_route_count="$(grep -Ec '^TEST_AGENT_NGINX_TERMINAL_ROUTES=' "${nginx_env}" || true)"
  if [[ "${server_route_count}" -eq 1 && "${legacy_route_count}" -eq 0 ]]; then
    return 0
  fi
  if [[ "${server_route_count}" -ne 0 || "${legacy_route_count}" -ne 1 ]]; then
    echo "Frontend nginx.env must contain exactly one server route key (legacy or current)" >&2
    exit 1
  fi
  migrated="$(mktemp "${nginx_env}.new.XXXXXX")"
  sed 's/^TEST_AGENT_NGINX_TERMINAL_ROUTES=/TEST_AGENT_NGINX_SERVER_ROUTES=/' \
    "${nginx_env}" >"${migrated}"
  chmod --reference="${nginx_env}" "${migrated}" 2>/dev/null || chmod 0600 "${migrated}"
  mv -f "${migrated}" "${nginx_env}"
}

BUNDLE_ROOT="${TMP_ROOT}/${BUNDLE_NAME}"
mkdir -p "${BUNDLE_ROOT}/nodes" "${OUTPUT_DIR}"
install -m 0644 "${SCRIPT_DIR}/MULTI-BACKEND.md" "${BUNDLE_ROOT}/START-HERE.md"
install -m 0644 "${SCRIPT_DIR}/deploy-node-common.sh" "${BUNDLE_ROOT}/deploy-node-common.sh"
install -m 0755 "${SCRIPT_DIR}/deploy-backend-node.sh" "${BUNDLE_ROOT}/deploy-backend-node.sh"
install -m 0755 "${SCRIPT_DIR}/deploy-frontend-node.sh" "${BUNDLE_ROOT}/deploy-frontend-node.sh"
install -m 0755 "${SCRIPT_DIR}/deploy-mysql-node.sh" "${BUNDLE_ROOT}/deploy-mysql-node.sh"
install -m 0755 "${SCRIPT_DIR}/init-backend-node-config.sh" "${BUNDLE_ROOT}/init-backend-node-config.sh"
install -m 0755 "${SCRIPT_DIR}/register-backend-on-frontend.sh" \
  "${BUNDLE_ROOT}/register-backend-on-frontend.sh"
install -m 0600 "${RELEASE_ARCHIVE}" "${BUNDLE_ROOT}/test-agent-internal-release.zip"
printf '%s  %s\n' \
  "$(sha256_digest "${BUNDLE_ROOT}/test-agent-internal-release.zip")" \
  test-agent-internal-release.zip \
  >"${BUNDLE_ROOT}/test-agent-internal-release.zip.sha256"

# 节点包保留现场配置，但总是换入当前逐机脚本和手册，避免携带历史部署逻辑。
repack_node() {
  local source="$1"
  local node_dir="$2"
  local node_root="${TMP_ROOT}/repack-${node_dir}"
  local target="${BUNDLE_ROOT}/nodes/$(basename "${source}")"
  mkdir -p "${node_root}"
  tar -C "${node_root}" -xzf "${source}"
  if [[ "${node_dir}" == "test-agent-two-backend-122.233.30.2" ]]; then
    normalize_frontend_server_routes "${node_root}/${node_dir}/config/nginx.env"
  fi
  if [[ "${node_dir}" == "test-agent-two-backend-122.233.30.147-mysql" ]]; then
    install -m 0755 "${SCRIPT_DIR}/deploy-xxl-job-mysql.sh" \
      "${node_root}/${node_dir}/deploy-xxl-job-mysql.sh"
  else
    install -m 0755 "${SCRIPT_DIR}/deploy-multi-backend-node.sh" \
      "${node_root}/${node_dir}/deploy-multi-backend-node.sh"
  fi
  install -m 0644 "${SCRIPT_DIR}/MULTI-BACKEND.md" \
    "${node_root}/${node_dir}/MULTI-BACKEND.md"
  tar -C "${node_root}" -czf "${target}" "${node_dir}"
  chmod 0600 "${target}"
  printf '%s  %s\n' "$(sha256_digest "${target}")" "$(basename "${target}")" \
    >"${target}.sha256"
}

repack_node "${NODE_4}" test-agent-two-backend-122.233.30.4
repack_node "${NODE_114}" test-agent-two-backend-122.233.30.114
repack_node "${NODE_2}" test-agent-two-backend-122.233.30.2
repack_node "${NODE_MYSQL}" test-agent-two-backend-122.233.30.147-mysql

TMP_ARCHIVE="${TMP_ROOT}/${BUNDLE_NAME}.zip"
(cd "${TMP_ROOT}" && zip -qr "${TMP_ARCHIVE}" "${BUNDLE_NAME}")
unzip -tq "${TMP_ARCHIVE}" >/dev/null

OUTPUT_ARCHIVE="${OUTPUT_DIR}/${BUNDLE_NAME}.zip"
OUTPUT_CHECKSUM="${OUTPUT_ARCHIVE}.sha256"
OUTPUT_ARCHIVE_TMP="$(mktemp "${OUTPUT_DIR}/.${BUNDLE_NAME}.zip.XXXXXX")"
OUTPUT_CHECKSUM_TMP="$(mktemp "${OUTPUT_DIR}/.${BUNDLE_NAME}.zip.sha256.XXXXXX")"
install -m 0600 "${TMP_ARCHIVE}" "${OUTPUT_ARCHIVE_TMP}"
printf '%s  %s\n' "$(sha256_digest "${OUTPUT_ARCHIVE_TMP}")" "$(basename "${OUTPUT_ARCHIVE}")" \
  >"${OUTPUT_CHECKSUM_TMP}"
chmod 0600 "${OUTPUT_CHECKSUM_TMP}"

# 固定文件逐个原子替换，不生成日期或版本后缀，也不触发交互式覆盖。
mv -f "${OUTPUT_ARCHIVE_TMP}" "${OUTPUT_ARCHIVE}"
mv -f "${OUTPUT_CHECKSUM_TMP}" "${OUTPUT_CHECKSUM}"

printf 'Complete two-backend bundle: %s\n' "${OUTPUT_ARCHIVE}"
printf 'Bundle checksum: %s\n' "${OUTPUT_CHECKSUM}"
printf 'Bundle SHA256: %s\n' "$(sha256_digest "${OUTPUT_ARCHIVE}")"
