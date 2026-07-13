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
SAVE_TARBALL=1
PACKAGE_ZIP=1
OUTPUT_DIR_FROM_ENV_BEFORE_DOTENV="${TEST_AGENT_IMAGE_OUTPUT_DIR+x}"

usage() {
  cat <<'USAGE'
Usage: deploy/internal/package-release.sh [options]

Build enterprise internal delivery artifacts:
  - backend executable jar
  - frontend dist files and tar.gz
  - opencode-worker image and docker-loadable tar

Options:
  --env-file <path>       Dotenv file to read. Defaults to deploy/internal/.env, then env.example.
  --output-dir <path>     Artifact output directory. Defaults to deploy/internal/dist.
                          TEST_AGENT_IMAGE_OUTPUT_DIR is honored only when exported by the shell
                          or loaded from an explicit --env-file.
  --platform <platform>   Docker build platform for opencode-worker. Defaults to linux/amd64.
  --db-driver-jar <path>   Replace the bundled PostgreSQL JDBC jar with an external GaussDB/JDBC jar.
  --backend-only          Package only the backend jar.
  --frontend-only         Package only the frontend dist.
  --opencode-only         Package only the opencode worker image.
  --no-save               Build opencode image but do not export tarball.
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
    --db-driver-jar)
      [[ $# -ge 2 ]] || {
        echo "--db-driver-jar requires a path." >&2
        exit 2
      }
      DB_DRIVER_JAR="$2"
      shift 2
      ;;
    --backend-only)
      PACKAGE_BACKEND=1
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=0
      shift
      ;;
    --frontend-only)
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=1
      PACKAGE_OPENCODE_WORKER=0
      shift
      ;;
    --opencode-only)
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=1
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
  local extract_dir manifest_file driver_jar
  require_command unzip
  require_command zip
  require_command jar
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
  extract_dir="$(mktemp -d "${OUTPUT_DIR}/.backend-lib.XXXXXX")"
  unzip -q "${backend_dir}/test-agent-app.jar" 'BOOT-INF/lib/*' -d "${extract_dir}"
  mv "${extract_dir}/BOOT-INF/lib" "${backend_dir}/lib"
  rm -rf "${extract_dir}"
  if [[ -n "${DB_DRIVER_JAR:-}" ]]; then
    driver_jar="${DB_DRIVER_JAR}"
    if [[ "${driver_jar}" != /* ]]; then
      driver_jar="${ROOT_DIR}/${driver_jar}"
    fi
    [[ -f "${driver_jar}" ]] || {
      echo "Database driver jar not found: ${driver_jar}" >&2
      exit 1
    }
    # GaussDB 的 PostgreSQL 兼容驱动与官方 PostgreSQL 驱动不能同时进入同一 classpath，
    # 否则 org.postgresql.* 类会按文件顺序随机加载，产生难以判断的 API 冲突。
    rm -f "${backend_dir}/lib"/postgresql-*.jar
    cp "${driver_jar}" "${backend_dir}/lib/$(basename "${driver_jar}")"
    echo "Using external database driver: ${driver_jar}"
  fi
  # 交付包只保留启动器和业务 classes，所有依赖由 PropertiesLauncher 从外置 lib 加载。
  zip -qd "${backend_dir}/test-agent-app.jar" 'BOOT-INF/lib/*' >/dev/null
  manifest_file="$(mktemp "${OUTPUT_DIR}/.backend-manifest.XXXXXX")"
  unzip -p "${backend_dir}/test-agent-app.jar" META-INF/MANIFEST.MF | tr -d '\r' \
    | sed 's#Main-Class: org.springframework.boot.loader.launch.JarLauncher#Main-Class: org.springframework.boot.loader.launch.PropertiesLauncher#' \
    | sed '${/^$/d;}' >"${manifest_file}"
  printf 'Loader-Path: /data/testagent/dist/backend/lib\n\n' >>"${manifest_file}"
  jar umf "${manifest_file}" "${backend_dir}/test-agent-app.jar" >/dev/null 2>&1
  rm -f "${manifest_file}"
  [[ -n "$(find "${backend_dir}/lib" -maxdepth 1 -type f -name '*.jar' -print -quit)" ]] || {
    echo "External backend libraries were not extracted" >&2
    exit 1
  }
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
    --build-arg "OPENCODE_AI_PACKAGE=${OPENCODE_AI_PACKAGE}" \
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

package_release_zip() {
  local staging_dir="${OUTPUT_DIR}/.release-zip"
  local zip_path

  require_command zip
  require_command rsync
  rm -rf "${staging_dir}"
  mkdir -p "${staging_dir}/dist" "${staging_dir}/deploy/internal"
  zip_path="$(cd "${OUTPUT_DIR}" && pwd)/test-agent-internal-release.zip"

  # 交付 zip 只放部署所需产物和脚本，避免把 deploy/internal/dist 自身递归打进去。
  if [[ -d "${OUTPUT_DIR}/backend" ]]; then
    mkdir -p "${staging_dir}/dist/backend"
    cp -a "${OUTPUT_DIR}/backend/." "${staging_dir}/dist/backend/"
  fi
  [[ -f "${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz" ]] && cp -a "${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz" "${staging_dir}/dist/"
  [[ -f "${OUTPUT_DIR}/test-agent-programs.tar.gz" ]] && cp -a "${OUTPUT_DIR}/test-agent-programs.tar.gz" "${staging_dir}/dist/"

  local worker_tar
  worker_tar="${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" "${PLATFORM}")"
  [[ -f "${worker_tar}" ]] && cp -a "${worker_tar}" "${staging_dir}/dist/"

  # 输出目录可能按版本命名为 dist-gauss 等；统一排除当前输出目录，避免把交付物自身递归打进 zip。
  local output_dir_name
  output_dir_name="$(basename "${OUTPUT_DIR}")"
  rsync -a --exclude 'dist' --exclude "${output_dir_name}" --exclude '.env' "${SCRIPT_DIR}/" "${staging_dir}/deploy/internal/"

  rm -f "${zip_path}"
  (cd "${staging_dir}" && zip -qr "${zip_path}" .)
  rm -rf "${staging_dir}"
  ls -lh "${zip_path}"
}

export_worker_programs() {
  local programs_dir="${OUTPUT_DIR}/programs"
  local container_id
  container_id="$(docker create --platform "${PLATFORM}" "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" true)"

  rm -rf "${programs_dir}"
  mkdir -p "${programs_dir}/bin" "${programs_dir}/opencode/bin" "${programs_dir}/opencode/lib/node_modules"

  if ! docker cp "${container_id}:/usr/local/bin/opencode-manager" "${programs_dir}/bin/opencode-manager" \
    || ! docker cp "${container_id}:/usr/local/lib/node_modules/opencode-ai" "${programs_dir}/opencode/lib/node_modules/opencode-ai"; then
    docker rm -f "${container_id}" >/dev/null 2>&1 || true
    return 1
  fi
  docker rm -f "${container_id}" >/dev/null

  # npm 全局 bin 是跨目录符号链接，Docker Desktop 直接 docker cp 该链接到宿主机时可能失败；
  # 先复制包目录，再在交付目录内创建相同相对链接，保证目标 Linux 机器解压后可执行。
  ln -sfn "../lib/node_modules/opencode-ai/bin/opencode.exe" "${programs_dir}/opencode/bin/opencode"
  chmod +x "${programs_dir}/bin/opencode-manager" || true
  chmod +x "${programs_dir}/opencode/bin/opencode" || true
  printf 'opencode package: %s\n' "${OPENCODE_AI_PACKAGE}" >"${programs_dir}/VERSION"
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
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"
GOPROXY="${GOPROXY:-https://goproxy.cn,direct}"
DEBIAN_MIRROR="${DEBIAN_MIRROR:-https://mirrors.tuna.tsinghua.edu.cn/debian}"
DEBIAN_SECURITY_MIRROR="${DEBIAN_SECURITY_MIRROR:-https://mirrors.tuna.tsinghua.edu.cn/debian-security}"
OPENCODE_AI_PACKAGE="${OPENCODE_AI_PACKAGE:-opencode-ai@1.17.8}"
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

if [[ "${PACKAGE_ZIP}" -eq 1 && "${PACKAGE_BACKEND}" -eq 1 && "${PACKAGE_FRONTEND}" -eq 1 && "${PACKAGE_OPENCODE_WORKER}" -eq 1 && "${SAVE_TARBALL}" -eq 1 ]]; then
  package_release_zip
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
if [[ "${PACKAGE_ZIP}" -eq 1 && "${PACKAGE_BACKEND}" -eq 1 && "${PACKAGE_FRONTEND}" -eq 1 && "${PACKAGE_OPENCODE_WORKER}" -eq 1 && "${SAVE_TARBALL}" -eq 1 ]]; then
  echo "  complete release zip: ${OUTPUT_DIR}/test-agent-internal-release.zip"
fi
