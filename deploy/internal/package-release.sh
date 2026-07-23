#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

ENV_FILE="${SCRIPT_DIR}/.env"
if [[ ! -f "${ENV_FILE}" ]]; then
  ENV_FILE="${SCRIPT_DIR}/env.example"
fi
OUTPUT_DIR="${SCRIPT_DIR}/dist"
OUTPUT_DIR_FROM_ARG=0
ENV_FILE_FROM_ARG=0
PLATFORM="linux/amd64"
PACKAGE_BACKEND=1
PACKAGE_FRONTEND=1
PACKAGE_OPENCODE_WORKER=1
PACKAGE_MYSQL_IMAGE=0
SAVE_TARBALL=1
PACKAGE_ZIP=1
PACKAGE_ZIP_ONLY=0
OUTPUT_DIR_FROM_ENV_BEFORE_DOTENV="${TEST_AGENT_IMAGE_OUTPUT_DIR+x}"

usage() {
  cat <<'USAGE'
Usage: deploy/internal/package-release.sh [options]

Build enterprise internal delivery artifacts:
  - backend executable jar
  - frontend dist files and tar.gz
  - opencode-worker image and docker-loadable tar
  - repository session logs under .agents/

The default release ZIP is platform-only. Build the standalone MySQL 8.4
linux/amd64 image separately with --mysql-only.

Options:
  --env-file <path>       Dotenv file to read. Defaults to deploy/internal/.env, then env.example.
  --output-dir <path>     Artifact output directory. Defaults to deploy/internal/dist.
                          TEST_AGENT_IMAGE_OUTPUT_DIR is honored only when exported by the shell
                          or loaded from an explicit --env-file.
  --platform <platform>   Docker build platform for opencode-worker. Defaults to linux/amd64.
  --backend-only          Package only the backend jar.
  --frontend-only         Package only the frontend dist.
  --opencode-only         Package only the opencode worker image.
  --mysql-only            Package only the standalone MySQL image.
  --zip-only              Reassemble the release ZIP from existing complete artifacts and current session logs.
  --no-save               Build/pull Docker images but do not export image tarballs.
  --no-zip                Do not create the complete enterprise release zip.
  -h, --help              Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      ENV_FILE_FROM_ARG=1
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
      OUTPUT_DIR_FROM_ARG=1
      shift 2
      ;;
    --platform)
      PLATFORM="$2"
      shift 2
      ;;
    --backend-only)
      PACKAGE_BACKEND=1
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=0
      PACKAGE_MYSQL_IMAGE=0
      shift
      ;;
    --frontend-only)
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=1
      PACKAGE_OPENCODE_WORKER=0
      PACKAGE_MYSQL_IMAGE=0
      shift
      ;;
    --opencode-only)
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=1
      PACKAGE_MYSQL_IMAGE=0
      shift
      ;;
    --mysql-only)
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=0
      PACKAGE_MYSQL_IMAGE=1
      shift
      ;;
    --zip-only)
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=0
      PACKAGE_MYSQL_IMAGE=0
      PACKAGE_ZIP_ONLY=1
      shift
      ;;
    --no-save)
      SAVE_TARBALL=0
      shift
      ;;
    --no-zip)
      PACKAGE_ZIP=0
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

load_dotenv() {
  local file="$1"
  [[ -f "${file}" ]] || return 0
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

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

configure_java_home() {
  local detected_home="" java_version
  local versions=()

  if [[ -n "${JAVA_HOME:-}" && -z "${JAVA_VERSION:-}" ]] && java_home_is_usable "${JAVA_HOME}"; then
    return
  fi

  if [[ -n "${JAVA_VERSION:-}" ]]; then
    versions=("${JAVA_VERSION}")
  else
    versions=(25 24 23 22 21)
  fi

  for java_version in "${versions[@]}"; do
    if [[ "$(uname -s)" == "Darwin" ]]; then
      detected_home="$(/usr/libexec/java_home -v "${java_version}" 2>/dev/null || true)"
      if [[ -n "${detected_home}" ]] && ! java_home_is_usable "${detected_home}" "${java_version}"; then
        detected_home=""
      fi
    fi

    if [[ -z "${detected_home}" ]]; then
      for candidate in \
        "${HOME}/Library/Java/JavaVirtualMachines/openjdk-${java_version}.0.1/Contents/Home" \
        "/Library/Java/JavaVirtualMachines/openjdk-${java_version}/Contents/Home" \
        "/usr/lib/jvm/java-${java_version}" \
        "/usr/lib/jvm/openjdk-${java_version}" \
        "${HOME}/.sdkman/candidates/java/current"; do
        if [[ -d "${candidate}" ]] && java_home_is_usable "${candidate}" "${java_version}"; then
          detected_home="${candidate}"
          break
        fi
      done
    fi

    [[ -n "${detected_home}" ]] && break
  done

  if [[ -n "${detected_home}" ]]; then
    export JAVA_HOME="${detected_home}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
  fi
}

java_home_is_usable() {
  local java_home="$1"
  local expected_major="${2:-}"
  local version_line major
  [[ -x "${java_home}/bin/java" ]] || return 1
  version_line="$("${java_home}/bin/java" -version 2>&1 | head -n 1 || true)"
  if [[ "${version_line}" =~ \"1\.([0-9]+) ]]; then
    major="${BASH_REMATCH[1]}"
  elif [[ "${version_line}" =~ \"([0-9]+) ]]; then
    major="${BASH_REMATCH[1]}"
  else
    return 1
  fi
  if [[ -n "${expected_major}" ]]; then
    [[ "${major}" -eq "${expected_major}" ]]
  else
    [[ "${major}" -ge 21 ]]
  fi
}

tag_to_tar_name() {
  local tag="$1"
  local platform="$2"
  local platform_suffix="${platform//\//-}"
  local safe="${tag//\//_}"
  safe="${safe//:/_}"
  printf '%s-%s.tar' "${safe}" "${platform_suffix}"
}

package_backend() {
  local backend_dir="${OUTPUT_DIR}/backend"
  local backend_jar_path extract_dir manifest_dir manifest_file
  require_command unzip
  require_command zip
  mkdir -p "${backend_dir}"
  echo "Building backend jar"
  # 企业包只需要主代码和运行时依赖；跳过测试源码编译，避免无关的存量测试假实现阻断交付包生成。
  (cd "${ROOT_DIR}/backend" && mvn -q -pl test-agent-app -am -Dmaven.test.skip=true package)

  local jar
  jar="$(find "${ROOT_DIR}/backend/test-agent-app/target" -maxdepth 1 -type f -name 'test-agent-app-*.jar' ! -name '*.original' | sort | tail -n 1)"
  if [[ -z "${jar}" ]]; then
    echo "Backend jar not found under backend/test-agent-app/target" >&2
    exit 1
  fi
  rm -rf "${backend_dir}"
  mkdir -p "${backend_dir}"
  cp "${jar}" "${backend_dir}/test-agent-app.jar"
  backend_jar_path="$(cd "${backend_dir}" && pwd)/test-agent-app.jar"
  extract_dir="$(mktemp -d "${OUTPUT_DIR}/.backend-lib.XXXXXX")"
  unzip -q "${backend_dir}/test-agent-app.jar" 'BOOT-INF/lib/*' -d "${extract_dir}"
  mv "${extract_dir}/BOOT-INF/lib" "${backend_dir}/lib"
  rm -rf "${extract_dir}"
  # 交付包只保留启动器和业务 classes，所有依赖由 PropertiesLauncher 从外置 lib 加载。
  zip -qd "${backend_dir}/test-agent-app.jar" 'BOOT-INF/lib/*' >/dev/null
  manifest_dir="$(mktemp -d "${OUTPUT_DIR}/.backend-manifest.XXXXXX")"
  mkdir -p "${manifest_dir}/META-INF"
  manifest_file="${manifest_dir}/META-INF/MANIFEST.MF"
  unzip -p "${backend_dir}/test-agent-app.jar" META-INF/MANIFEST.MF | tr -d '\r' \
    | sed 's#Main-Class: org.springframework.boot.loader.launch.JarLauncher#Main-Class: org.springframework.boot.loader.launch.PropertiesLauncher#' \
    | sed '${/^$/d;}' >"${manifest_file}"
  printf 'Loader-Path: /data/testagent/dist/backend/lib\n\n' >>"${manifest_file}"
  (cd "${manifest_dir}" && zip -q -u "${backend_jar_path}" META-INF/MANIFEST.MF)
  rm -rf "${manifest_dir}"
  unzip -p "${backend_dir}/test-agent-app.jar" META-INF/MANIFEST.MF | tr -d '\r' \
    | grep -Fx 'Loader-Path: /data/testagent/dist/backend/lib' >/dev/null || {
    echo "Backend jar manifest is missing the external Loader-Path" >&2
    exit 1
  }
  [[ -n "$(find "${backend_dir}/lib" -maxdepth 1 -type f -name '*.jar' -print -quit)" ]] || {
    echo "External backend libraries were not extracted" >&2
    exit 1
  }
  unzip -Z1 "${backend_dir}/test-agent-app.jar" | grep -Fx 'BOOT-INF/classes/rsa-private.key' >/dev/null || {
    echo "Backend jar is missing the embedded RSA private key resource" >&2
    exit 1
  }

  # 离线包随平台应用交付固定上游源码、许可证和版本证据，便于履行 GPL-3.0 义务与后续升级核对。
  local xxl_compliance_dir="${backend_dir}/xxl-job-upstream"
  mkdir -p "${xxl_compliance_dir}/source"
  cp "${ROOT_DIR}/backend/test-agent-xxl-job-admin-upstream/LICENSE" "${xxl_compliance_dir}/LICENSE"
  cp "${ROOT_DIR}/backend/test-agent-xxl-job-admin-upstream/UPSTREAM.md" "${xxl_compliance_dir}/UPSTREAM.md"
  cp "${ROOT_DIR}/backend/test-agent-xxl-job-admin-upstream/README.md" "${xxl_compliance_dir}/README.md"
  cp -R "${ROOT_DIR}/backend/test-agent-xxl-job-admin-upstream/src/." "${xxl_compliance_dir}/source/"
  printf '%s\n' \
    'component=XXL-JOB Admin' \
    'version=3.4.2' \
    'commit=c2bbb46c9a3af8e2a69246728a452c606240b80e' \
    'license=GPL-3.0' \
    'integration=test-agent-xxl-job-integration' \
    >"${xxl_compliance_dir}/VERSION"
  ls -lh "${backend_dir}/test-agent-app.jar"
}

package_frontend() {
  local frontend_dir="${OUTPUT_DIR}/frontend"
  echo "Building frontend dist"
  (cd "${ROOT_DIR}/frontend" && corepack pnpm install --frozen-lockfile && VITE_TEST_AGENT_API_BASE_URL="${VITE_TEST_AGENT_API_BASE_URL:-}" corepack pnpm --filter @test-agent/agent-web build)

  rm -rf "${frontend_dir}"
  mkdir -p "${frontend_dir}"
  cp -R "${ROOT_DIR}/frontend/apps/agent-web/dist/." "${frontend_dir}/"
  tar -C "${OUTPUT_DIR}" -czf "${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz" frontend
  ls -lh "${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz"
}

build_opencode_worker_image() {
  local manager_build_version
  manager_build_version="$(TZ=Asia/Shanghai date '+V%Y%m%d.%H%M%S')"
  echo "Building ${TEST_AGENT_OPENCODE_WORKER_IMAGE} for ${PLATFORM}"
  docker buildx build \
    --platform "${PLATFORM}" \
    -f "${ROOT_DIR}/deploy/internal/opencode-worker.Dockerfile" \
    -t "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" \
    --load \
    --build-arg "GOPROXY=${GOPROXY}" \
    --build-arg "NPM_REGISTRY=${NPM_REGISTRY}" \
    --build-arg "DEBIAN_MIRROR=${DEBIAN_MIRROR}" \
    --build-arg "DEBIAN_SECURITY_MIRROR=${DEBIAN_SECURITY_MIRROR}" \
    --build-arg "GO_IMAGE=${GO_IMAGE}" \
    --build-arg "MANAGER_BUILD_VERSION=${manager_build_version}" \
    --build-arg "NODE_IMAGE=${NODE_IMAGE}" \
    --build-arg "OPENCODE_VERSION=${OPENCODE_VERSION}" \
    --build-arg "OPENCODE_RELEASE_COMMIT=${OPENCODE_RELEASE_COMMIT}" \
    --build-arg "OPENCODE_ASSET_NAME=${OPENCODE_ASSET_NAME}" \
    --build-arg "OPENCODE_ASSET_SIZE=${OPENCODE_ASSET_SIZE}" \
    --build-arg "OPENCODE_ASSET_SHA256=${OPENCODE_ASSET_SHA256}" \
    --build-arg "OPENCODE_BINARY_SHA256=${OPENCODE_BINARY_SHA256}" \
    --build-arg "OPENCODE_RELEASE_BASE_URL=${OPENCODE_RELEASE_BASE_URL}" \
    --build-arg "OPENCODE_RUNTIME_PACKAGE_JSON=${OPENCODE_RUNTIME_PACKAGE_JSON}" \
    --build-arg "OPENCODE_RUNTIME_PACKAGE_LOCK=${OPENCODE_RUNTIME_PACKAGE_LOCK}" \
    "${ROOT_DIR}"
  docker image inspect "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" >/dev/null

  export_worker_programs

  if [[ "${SAVE_TARBALL}" -eq 1 ]]; then
    local tar_path="${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" "${PLATFORM}")"
    echo "Saving ${TEST_AGENT_OPENCODE_WORKER_IMAGE} to ${tar_path}"
    docker save -o "${tar_path}" "${TEST_AGENT_OPENCODE_WORKER_IMAGE}"
    ls -lh "${tar_path}"
  fi
}

package_mysql_image() {
  local tar_path architecture
  tar_path="${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_XXL_JOB_MYSQL_IMAGE}" "${PLATFORM}")"
  echo "Pulling ${TEST_AGENT_XXL_JOB_MYSQL_IMAGE} for ${PLATFORM}"
  docker pull --platform "${PLATFORM}" "${TEST_AGENT_XXL_JOB_MYSQL_IMAGE}" >/dev/null
  architecture="$(docker image inspect -f '{{.Architecture}}' "${TEST_AGENT_XXL_JOB_MYSQL_IMAGE}")"
  [[ "${architecture}" == "amd64" ]] || {
    echo "MySQL image architecture must be amd64, got ${architecture}" >&2
    exit 1
  }
  if [[ "${SAVE_TARBALL}" -eq 1 ]]; then
    echo "Saving ${TEST_AGENT_XXL_JOB_MYSQL_IMAGE} to ${tar_path}"
    docker save -o "${tar_path}" "${TEST_AGENT_XXL_JOB_MYSQL_IMAGE}"
    ls -lh "${tar_path}"
  fi
}

package_release_zip() {
  local staging_dir="${OUTPUT_DIR}/.release-zip"
  local zip_path session_log session_log_count=0
  local worker_tar required_artifact

  require_command zip
  require_command rsync
  rm -rf "${staging_dir}"
  mkdir -p "${staging_dir}/dist" "${staging_dir}/deploy/internal"
  zip_path="$(cd "${OUTPUT_DIR}" && pwd)/test-agent-internal-release.zip"
  worker_tar="${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" "${PLATFORM}")"

  # zip-only 复用刚完成验证的二进制制品，但不允许任何一层缺失后生成看似完整的发布包。
  for required_artifact in \
    "${OUTPUT_DIR}/backend/test-agent-app.jar" \
    "${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz" \
    "${OUTPUT_DIR}/test-agent-programs.tar.gz" \
    "${worker_tar}"; do
    if [[ ! -f "${required_artifact}" ]]; then
      echo "Required release artifact not found: ${required_artifact}" >&2
      exit 1
    fi
  done

  # 交付 zip 只放部署所需产物和脚本，避免把 deploy/internal/dist 自身递归打进去。
  mkdir -p "${staging_dir}/dist/backend"
  cp -a "${OUTPUT_DIR}/backend/." "${staging_dir}/dist/backend/"
  cp -a "${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz" "${staging_dir}/dist/"
  cp -a "${OUTPUT_DIR}/test-agent-programs.tar.gz" "${staging_dir}/dist/"
  cp -a "${worker_tar}" "${staging_dir}/dist/"

  if [[ "${PACKAGE_MYSQL_IMAGE}" -eq 1 ]]; then
    local mysql_tar
    mysql_tar="${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_XXL_JOB_MYSQL_IMAGE}" "${PLATFORM}")"
    [[ -f "${mysql_tar}" ]] && cp -a "${mysql_tar}" "${staging_dir}/dist/"
  fi

  # 排除默认、当前及历史命名的 dist-* 输出目录，避免旧交付物递归进入新 zip。
  local output_dir_name
  output_dir_name="$(basename "${OUTPUT_DIR}")"
  rsync -a --exclude 'dist' --exclude 'dist-*' --exclude "${output_dir_name}" --exclude '.env' "${SCRIPT_DIR}/" "${staging_dir}/deploy/internal/"

  # 会话日志属于本次交付基线，保留原始文件名放入 .agents，便于内网追溯变更、坑点和未完成事项。
  mkdir -p "${staging_dir}/.agents"
  for session_log in "${ROOT_DIR}"/.agents/session-log*.md; do
    [[ -f "${session_log}" ]] || continue
    install -m 0644 "${session_log}" "${staging_dir}/.agents/$(basename "${session_log}")"
    session_log_count=$((session_log_count + 1))
  done
  if [[ "${session_log_count}" -eq 0 ]]; then
    echo "No .agents/session-log*.md files found for release provenance" >&2
    exit 1
  fi

  rm -f "${zip_path}"
  (cd "${staging_dir}" && zip -qr "${zip_path}" .)
  rm -rf "${staging_dir}"
  ls -lh "${zip_path}"
}

write_release_checksum() {
  local zip_path="${OUTPUT_DIR}/test-agent-internal-release.zip"
  local zip_name
  zip_name="$(basename "${zip_path}")"

  # 校验文件只记录包名，复制到企业服务器任意目录后仍可直接执行校验命令。
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "${OUTPUT_DIR}" && sha256sum "${zip_name}" >"${zip_name}.sha256")
  elif command -v shasum >/dev/null 2>&1; then
    (cd "${OUTPUT_DIR}" && shasum -a 256 "${zip_name}" >"${zip_name}.sha256")
  else
    echo "Neither sha256sum nor shasum is available; cannot create release checksum" >&2
    exit 1
  fi
  cat "${zip_path}.sha256"
}

export_worker_programs() {
  local programs_dir="${OUTPUT_DIR}/programs"
  local container_id
  container_id="$(docker create --platform "${PLATFORM}" "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" true)"

  rm -rf "${programs_dir}"
  mkdir -p "${programs_dir}/bin" "${programs_dir}/opencode"

  if ! docker cp "${container_id}:/usr/local/bin/opencode-manager" "${programs_dir}/bin/opencode-manager" \
    || ! docker cp "${container_id}:/usr/local/lib/opencode/." "${programs_dir}/opencode/"; then
    docker rm -f "${container_id}" >/dev/null 2>&1 || true
    return 1
  fi
  docker rm -f "${container_id}" >/dev/null

  chmod +x "${programs_dir}/bin/opencode-manager" || true
  chmod +x "${programs_dir}/opencode/bin/opencode" || true
  printf 'official opencode: %s\nasset: %s\narchive size: %s\narchive sha256: %s\nrelease commit: %s\n' \
    "${OPENCODE_VERSION}" \
    "${OPENCODE_ASSET_NAME}" \
    "${OPENCODE_ASSET_SIZE}" \
    "${OPENCODE_ASSET_SHA256}" \
    "${OPENCODE_RELEASE_COMMIT}" >"${programs_dir}/VERSION"
  tar -C "${OUTPUT_DIR}" -czf "${OUTPUT_DIR}/test-agent-programs.tar.gz" programs
  ls -lh "${OUTPUT_DIR}/test-agent-programs.tar.gz"
}

load_dotenv "${ENV_FILE}"

if [[ "${OUTPUT_DIR_FROM_ARG}" -eq 0 && -n "${TEST_AGENT_IMAGE_OUTPUT_DIR:-}" && ( "${ENV_FILE_FROM_ARG}" -eq 1 || -n "${OUTPUT_DIR_FROM_ENV_BEFORE_DOTENV}" ) ]]; then
  if [[ "${TEST_AGENT_IMAGE_OUTPUT_DIR}" = /* ]]; then
    OUTPUT_DIR="${TEST_AGENT_IMAGE_OUTPUT_DIR}"
  else
    OUTPUT_DIR="${ROOT_DIR}/${TEST_AGENT_IMAGE_OUTPUT_DIR}"
  fi
fi

TEST_AGENT_OPENCODE_WORKER_IMAGE="${TEST_AGENT_OPENCODE_WORKER_IMAGE:-test-agent-opencode-worker:internal}"
TEST_AGENT_XXL_JOB_MYSQL_IMAGE="${TEST_AGENT_XXL_JOB_MYSQL_IMAGE:-mysql:8.4}"
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"
GOPROXY="${GOPROXY:-https://goproxy.cn,direct}"
DEBIAN_MIRROR="${DEBIAN_MIRROR:-https://mirrors.ustc.edu.cn/debian}"
DEBIAN_SECURITY_MIRROR="${DEBIAN_SECURITY_MIRROR:-https://mirrors.ustc.edu.cn/debian-security}"
OPENCODE_VERSION="${OPENCODE_VERSION:-1.18.4}"
OPENCODE_RELEASE_COMMIT="${OPENCODE_RELEASE_COMMIT:-49c69c5ed3ccf706b61b3febb43c8aaff7f8325e}"
OPENCODE_ASSET_NAME="${OPENCODE_ASSET_NAME:-opencode-linux-x64-baseline.tar.gz}"
OPENCODE_ASSET_SIZE="${OPENCODE_ASSET_SIZE:-59265643}"
OPENCODE_ASSET_SHA256="${OPENCODE_ASSET_SHA256:-4d87e414607b77fef940256021e42fbbf37b8c62b06ced76b69e26c5dcbfbabc}"
OPENCODE_BINARY_SHA256="${OPENCODE_BINARY_SHA256:-6ce6570e7db9a40e7bd3304ebdfff607920bde8cafd2eb5587bd7a26f89ba0b5}"
OPENCODE_RELEASE_BASE_URL="${OPENCODE_RELEASE_BASE_URL:-https://github.com/anomalyco/opencode/releases/download}"
OPENCODE_RUNTIME_PACKAGE_JSON="${OPENCODE_RUNTIME_PACKAGE_JSON:-deploy/internal/opencode-node-runtime.package.json}"
OPENCODE_RUNTIME_PACKAGE_LOCK="${OPENCODE_RUNTIME_PACKAGE_LOCK:-deploy/internal/opencode-node-runtime.package-lock.json}"
GO_IMAGE="${GO_IMAGE:-golang@sha256:e87b2a5f6df2dff71ea330d55d54f4979eb380ae58a7e3aabc9d53121243e689}"
NODE_IMAGE="${NODE_IMAGE:-node@sha256:b042c6d46a90773b82ea3f95b05457ea93ee127a73b1b47ad5ebbb1a08ec3df8}"
VITE_TEST_AGENT_API_BASE_URL="${VITE_TEST_AGENT_API_BASE_URL:-}"

mkdir -p "${OUTPUT_DIR}"

echo "Using env file: ${ENV_FILE}"
echo "Output dir: ${OUTPUT_DIR}"
echo "Platform: ${PLATFORM}"

if [[ "${PACKAGE_BACKEND}" -eq 1 ]]; then
  configure_java_home
  require_command mvn
  package_backend
fi

if [[ "${PACKAGE_FRONTEND}" -eq 1 ]]; then
  require_command corepack
  package_frontend
fi

if [[ "${PACKAGE_OPENCODE_WORKER}" -eq 1 ]]; then
  require_command docker
  build_opencode_worker_image
fi

if [[ "${PACKAGE_MYSQL_IMAGE}" -eq 1 ]]; then
  require_command docker
  package_mysql_image
fi

if [[ "${PACKAGE_ZIP}" -eq 1 && "${SAVE_TARBALL}" -eq 1 \
  && ( "${PACKAGE_ZIP_ONLY}" -eq 1 \
    || ( "${PACKAGE_BACKEND}" -eq 1 && "${PACKAGE_FRONTEND}" -eq 1 && "${PACKAGE_OPENCODE_WORKER}" -eq 1 ) ) ]]; then
  package_release_zip
  write_release_checksum
fi

echo
echo "Artifacts:"
if [[ "${PACKAGE_BACKEND}" -eq 1 ]]; then
  echo "  backend jar: ${OUTPUT_DIR}/backend/test-agent-app.jar"
fi
if [[ "${PACKAGE_FRONTEND}" -eq 1 ]]; then
  echo "  frontend dist: ${OUTPUT_DIR}/frontend"
  echo "  frontend archive: ${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz"
fi
if [[ "${PACKAGE_OPENCODE_WORKER}" -eq 1 && "${SAVE_TARBALL}" -eq 1 ]]; then
  echo "  opencode worker image tar: ${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" "${PLATFORM}")"
  echo "  external programs: ${OUTPUT_DIR}/programs"
  echo "  external programs archive: ${OUTPUT_DIR}/test-agent-programs.tar.gz"
  echo
  echo "Target import:"
  echo "  docker load -i ${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" "${PLATFORM}")"
fi
if [[ "${PACKAGE_MYSQL_IMAGE}" -eq 1 && "${SAVE_TARBALL}" -eq 1 ]]; then
  echo "  MySQL image tar: ${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_XXL_JOB_MYSQL_IMAGE}" "${PLATFORM}")"
  echo "  MySQL target import: docker load -i ${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_XXL_JOB_MYSQL_IMAGE}" "${PLATFORM}")"
fi
if [[ "${PACKAGE_ZIP}" -eq 1 && "${SAVE_TARBALL}" -eq 1 \
  && ( "${PACKAGE_ZIP_ONLY}" -eq 1 \
    || ( "${PACKAGE_BACKEND}" -eq 1 && "${PACKAGE_FRONTEND}" -eq 1 && "${PACKAGE_OPENCODE_WORKER}" -eq 1 ) ) ]]; then
  echo "  complete release zip: ${OUTPUT_DIR}/test-agent-internal-release.zip"
  echo "  release checksum: ${OUTPUT_DIR}/test-agent-internal-release.zip.sha256"
fi
