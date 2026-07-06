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
PLATFORM="linux/amd64"
BUILD_FRONTEND=1
BUILD_OPENCODE_WORKER=1
SAVE_TARBALL=1

usage() {
  cat <<'USAGE'
Usage: deploy/internal/build-images.sh [options]

Build internal deployment images and export docker-loadable tarballs.

Options:
  --env-file <path>       Dotenv file to read. Defaults to deploy/internal/.env, then env.example.
  --output-dir <path>     Tarball output directory. Defaults to deploy/internal/dist.
  --platform <platform>   Docker build platform. Defaults to linux/amd64.
  --frontend-only         Build only the frontend image.
  --opencode-only         Build only the opencode worker image.
  --no-save               Build images but do not export tarballs.
  -h, --help              Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
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
    --frontend-only)
      BUILD_FRONTEND=1
      BUILD_OPENCODE_WORKER=0
      shift
      ;;
    --opencode-only)
      BUILD_FRONTEND=0
      BUILD_OPENCODE_WORKER=1
      shift
      ;;
    --no-save)
      SAVE_TARBALL=0
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

tag_to_tar_name() {
  local tag="$1"
  local platform="$2"
  local platform_suffix="${platform//\//-}"
  local safe="${tag//\//_}"
  safe="${safe//:/_}"
  printf '%s-%s.tar' "${safe}" "${platform_suffix}"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

build_image() {
  local dockerfile="$1"
  local image="$2"
  shift 2
  echo "Building ${image} with ${dockerfile} for ${PLATFORM}"
  docker buildx build \
    --platform "${PLATFORM}" \
    -f "${dockerfile}" \
    -t "${image}" \
    --load \
    "$@" \
    "${ROOT_DIR}"
  docker image inspect "${image}" >/dev/null
}

save_image() {
  local image="$1"
  local tar_path="${OUTPUT_DIR}/$(tag_to_tar_name "${image}" "${PLATFORM}")"
  mkdir -p "${OUTPUT_DIR}"
  echo "Saving ${image} to ${tar_path}"
  docker save -o "${tar_path}" "${image}"
  ls -lh "${tar_path}"
}

require_command docker
load_dotenv "${ENV_FILE}"

if [[ "${OUTPUT_DIR_FROM_ARG}" -eq 0 && -n "${TEST_AGENT_IMAGE_OUTPUT_DIR:-}" ]]; then
  if [[ "${TEST_AGENT_IMAGE_OUTPUT_DIR}" = /* ]]; then
    OUTPUT_DIR="${TEST_AGENT_IMAGE_OUTPUT_DIR}"
  else
    OUTPUT_DIR="${ROOT_DIR}/${TEST_AGENT_IMAGE_OUTPUT_DIR}"
  fi
fi

TEST_AGENT_FRONTEND_IMAGE="${TEST_AGENT_FRONTEND_IMAGE:-test-agent-frontend:internal}"
TEST_AGENT_OPENCODE_WORKER_IMAGE="${TEST_AGENT_OPENCODE_WORKER_IMAGE:-test-agent-opencode-worker:internal}"
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"
COREPACK_NPM_REGISTRY="${COREPACK_NPM_REGISTRY:-https://registry.npmmirror.com}"
GOPROXY="${GOPROXY:-https://goproxy.cn,direct}"
DEBIAN_MIRROR="${DEBIAN_MIRROR:-https://mirrors.tuna.tsinghua.edu.cn/debian}"
DEBIAN_SECURITY_MIRROR="${DEBIAN_SECURITY_MIRROR:-https://mirrors.tuna.tsinghua.edu.cn/debian-security}"
OPENCODE_AI_PACKAGE="${OPENCODE_AI_PACKAGE:-opencode-ai@1.17.8}"
VITE_TEST_AGENT_API_BASE_URL="${VITE_TEST_AGENT_API_BASE_URL:-}"

echo "Using env file: ${ENV_FILE}"
echo "Output dir: ${OUTPUT_DIR}"
echo "Platform: ${PLATFORM}"

if [[ "${BUILD_FRONTEND}" -eq 1 ]]; then
  build_image \
    "${ROOT_DIR}/deploy/internal/frontend.Dockerfile" \
    "${TEST_AGENT_FRONTEND_IMAGE}" \
    --build-arg "NPM_REGISTRY=${NPM_REGISTRY}" \
    --build-arg "COREPACK_NPM_REGISTRY=${COREPACK_NPM_REGISTRY}" \
    --build-arg "VITE_TEST_AGENT_API_BASE_URL=${VITE_TEST_AGENT_API_BASE_URL}"
  if [[ "${SAVE_TARBALL}" -eq 1 ]]; then
    save_image "${TEST_AGENT_FRONTEND_IMAGE}"
  fi
fi

if [[ "${BUILD_OPENCODE_WORKER}" -eq 1 ]]; then
  build_image \
    "${ROOT_DIR}/deploy/internal/opencode-worker.Dockerfile" \
    "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" \
    --build-arg "GOPROXY=${GOPROXY}" \
    --build-arg "NPM_REGISTRY=${NPM_REGISTRY}" \
    --build-arg "DEBIAN_MIRROR=${DEBIAN_MIRROR}" \
    --build-arg "DEBIAN_SECURITY_MIRROR=${DEBIAN_SECURITY_MIRROR}" \
    --build-arg "OPENCODE_AI_PACKAGE=${OPENCODE_AI_PACKAGE}"
  if [[ "${SAVE_TARBALL}" -eq 1 ]]; then
    save_image "${TEST_AGENT_OPENCODE_WORKER_IMAGE}"
  fi
fi

echo
echo "Build output:"
if [[ "${BUILD_FRONTEND}" -eq 1 ]]; then
  echo "  frontend image: ${TEST_AGENT_FRONTEND_IMAGE}"
fi
if [[ "${BUILD_OPENCODE_WORKER}" -eq 1 ]]; then
  echo "  opencode worker image: ${TEST_AGENT_OPENCODE_WORKER_IMAGE}"
fi
if [[ "${SAVE_TARBALL}" -eq 1 ]]; then
  echo "  tarball dir: ${OUTPUT_DIR}"
  echo
  echo "Target import:"
  if [[ "${BUILD_FRONTEND}" -eq 1 ]]; then
    echo "  docker load -i ${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_FRONTEND_IMAGE}" "${PLATFORM}")"
  fi
  if [[ "${BUILD_OPENCODE_WORKER}" -eq 1 ]]; then
    echo "  docker load -i ${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" "${PLATFORM}")"
  fi
fi
