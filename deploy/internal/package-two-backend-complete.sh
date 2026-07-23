#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RELEASE_ARCHIVE="${SCRIPT_DIR}/dist/test-agent-internal-release.zip"
NODES_DIR=""
OUTPUT_DIR="${SCRIPT_DIR}/dist"
BUNDLE_NAME="test-agent-two-backend-complete"

usage() {
  cat <<'USAGE'
Usage: package-two-backend-complete.sh --nodes-dir <path> [options]

Assemble the platform release ZIP and the three prepared application node
packages into one fixed-name USB delivery bundle.

Options:
  --release-archive <path>  Standard release ZIP. Default: deploy/internal/dist/test-agent-internal-release.zip.
  --nodes-dir <path>        Directory containing prepared .4, .114 and .2 node archives plus SHA files.
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
require_file "${SCRIPT_DIR}/init-backend-node-config.sh"
require_file "${SCRIPT_DIR}/register-backend-on-frontend.sh"

NODE_4="${NODES_DIR}/test-agent-two-backend-122.233.30.4-SENSITIVE.tar.gz"
NODE_114="${NODES_DIR}/test-agent-two-backend-122.233.30.114-SENSITIVE.tar.gz"
NODE_2="${NODES_DIR}/test-agent-two-backend-122.233.30.2.tar.gz"

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
require_archive_entry "${release_listing}" deploy/internal/deploy-multi-backend-node.sh
# 外层完整包只接受包含当前仓库全部会话日志的内层发布包，避免业务制品与交付追溯记录脱节。
session_log_count=0
for session_log in "${ROOT_DIR}"/.agents/session-log*.md; do
  [[ -f "${session_log}" ]] || continue
  require_archive_entry "${release_listing}" ".agents/$(basename "${session_log}")"
  session_log_count=$((session_log_count + 1))
done
if [[ "${session_log_count}" -eq 0 ]]; then
  echo "No .agents/session-log*.md files found for complete bundle validation" >&2
  exit 1
fi
if grep -Fx 'dist/mysql_8.4-linux-amd64.tar' <<<"${release_listing}" >/dev/null; then
  echo "Platform release archive must not contain the standalone MySQL image" >&2
  exit 1
fi

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
  require_archive_entry "${listing}" "${node_dir}/deploy-multi-backend-node.sh"
  require_archive_entry "${listing}" "${node_dir}/MULTI-BACKEND.md"
  require_archive_entry "${listing}" "${node_dir}/config/${required_config}"
}

validate_node_archive "${NODE_4}" test-agent-two-backend-122.233.30.4 backend.env
validate_node_archive "${NODE_4}" test-agent-two-backend-122.233.30.4 docker.env
validate_node_archive "${NODE_114}" test-agent-two-backend-122.233.30.114 backend.env
validate_node_archive "${NODE_114}" test-agent-two-backend-122.233.30.114 docker.env
validate_node_archive "${NODE_2}" test-agent-two-backend-122.233.30.2 nginx.env

# 现场敏感配置包允许复用，但封装时必须在临时副本中补齐本次发布新增的非密钥配置。
# 原始采集包及其中的密码/token 均不修改、不输出；重复键直接拒绝，避免掩盖脏配置。
replace_or_append_env_value() {
  local file="$1"
  local key="$2"
  local value="$3"
  local count tmp_file
  count="$(grep -c "^${key}=" "${file}" || true)"
  if [[ "${count}" -gt 1 ]]; then
    echo "Prepared node configuration contains duplicate ${key}" >&2
    exit 1
  fi
  tmp_file="$(mktemp "${file}.new.XXXXXX")"
  if [[ "${count}" -eq 1 ]]; then
    awk -v key="${key}" -v value="${value}" \
      'index($0, key "=") == 1 { print key "=" value; next } { print }' \
      "${file}" >"${tmp_file}"
  else
    cp "${file}" "${tmp_file}"
    printf '%s=%s\n' "${key}" "${value}" >>"${tmp_file}"
  fi
  chmod --reference="${file}" "${tmp_file}" 2>/dev/null || chmod 0600 "${tmp_file}"
  mv -f "${tmp_file}" "${file}"
}

normalize_backend_node_archive() {
  local source="$1"
  local node_dir="$2"
  local node_root="${TMP_ROOT}/normalize-${node_dir}"
  local backend_env docker_env target
  mkdir -p "${node_root}"
  tar -C "${node_root}" -xzf "${source}"
  backend_env="${node_root}/${node_dir}/config/backend.env"
  docker_env="${node_root}/${node_dir}/config/docker.env"

  replace_or_append_env_value "${backend_env}" TEST_AGENT_XXL_JOB_COOKIE_SECURE false
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MAX_PREVIEW_BYTES 5242880
  replace_or_append_env_value "${backend_env}" TEST_AGENT_UPLOAD_CHUNK_BYTES 262144
  replace_or_append_env_value "${docker_env}" OPENCODE_WORKER_BACKEND_PORT 8080
  replace_or_append_env_value "${docker_env}" OPENCODE_WORKER_PORT_START 4096
  replace_or_append_env_value "${docker_env}" OPENCODE_WORKER_PORT_END 5095

  target="${TMP_ROOT}/$(basename "${source}")"
  tar -C "${node_root}" -czf "${target}" "${node_dir}"
  chmod 0600 "${target}"
  printf '%s\n' "${target}"
}

NODE_4="$(normalize_backend_node_archive "${NODE_4}" test-agent-two-backend-122.233.30.4)"
NODE_114="$(normalize_backend_node_archive "${NODE_114}" test-agent-two-backend-122.233.30.114)"
validate_node_archive "${NODE_4}" test-agent-two-backend-122.233.30.4 backend.env
validate_node_archive "${NODE_4}" test-agent-two-backend-122.233.30.4 docker.env
validate_node_archive "${NODE_114}" test-agent-two-backend-122.233.30.114 backend.env
validate_node_archive "${NODE_114}" test-agent-two-backend-122.233.30.114 docker.env

# 完整包必须在封装前确认两台后台引用同一个外部 MySQL，且账号密码和 access token 一致。
validate_mysql_cluster_config() {
  local config_root="${TMP_ROOT}/cluster-config-check"
  local backend_4 backend_114 frontend expected_url
  local backend_password backend_token
  mkdir -m 0700 -p "${config_root}"
  backend_4="${config_root}/backend-4.env"
  backend_114="${config_root}/backend-114.env"
  frontend="${config_root}/nginx.env"
  tar -xOzf "${NODE_4}" 'test-agent-two-backend-122.233.30.4/config/backend.env' >"${backend_4}"
  tar -xOzf "${NODE_114}" 'test-agent-two-backend-122.233.30.114/config/backend.env' >"${backend_114}"
  tar -xOzf "${NODE_2}" 'test-agent-two-backend-122.233.30.2/config/nginx.env' >"${frontend}"
  chmod 0600 "${backend_4}" "${backend_114}" "${frontend}"
  if grep -q 'REPLACE_' "${backend_4}" "${backend_114}" "${frontend}"; then
    echo "Prepared cluster configuration still contains a REPLACE_ placeholder" >&2
    exit 1
  fi
  expected_url='jdbc:mysql://122.210.106.43:3306/xxl_job?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
  for key in TEST_AGENT_XXL_JOB_MYSQL_URL TEST_AGENT_XXL_JOB_MYSQL_USERNAME \
    TEST_AGENT_XXL_JOB_MYSQL_PASSWORD TEST_AGENT_XXL_JOB_ACCESS_TOKEN \
    TEST_AGENT_XXL_JOB_COOKIE_SECURE; do
    [[ "$(grep -c "^${key}=" "${backend_4}" || true)" -eq 1 \
      && "$(grep -c "^${key}=" "${backend_114}" || true)" -eq 1 ]] || {
      echo "Both backend configs must contain exactly one ${key}" >&2
      exit 1
    }
  done
  grep -Fxq "TEST_AGENT_XXL_JOB_MYSQL_URL=${expected_url}" "${backend_4}"
  grep -Fxq "TEST_AGENT_XXL_JOB_MYSQL_URL=${expected_url}" "${backend_114}"
  grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_USERNAME=root' "${backend_4}"
  grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_USERNAME=root' "${backend_114}"
  grep -Fxq 'TEST_AGENT_XXL_JOB_COOKIE_SECURE=false' "${backend_4}"
  grep -Fxq 'TEST_AGENT_XXL_JOB_COOKIE_SECURE=false' "${backend_114}"
  grep -Fxq 'TEST_AGENT_NGINX_XXL_JOB_ADMINS=122.233.30.4:18080,122.233.30.114:18080' "${frontend}"

  backend_password="$(sed -n 's/^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=//p' "${backend_4}")"
  backend_token="$(sed -n 's/^TEST_AGENT_XXL_JOB_ACCESS_TOKEN=//p' "${backend_4}")"
  [[ ${#backend_password} -ge 8 && ${#backend_token} -ge 32 ]] || {
    echo "Prepared XXL-JOB password or access token is too short" >&2
    exit 1
  }
  [[ "${backend_password}" == "$(sed -n 's/^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=//p' "${backend_114}")" ]] || {
    echo "MySQL passwords differ between prepared backend configs" >&2
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
  install -m 0755 "${SCRIPT_DIR}/deploy-multi-backend-node.sh" \
    "${node_root}/${node_dir}/deploy-multi-backend-node.sh"
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
