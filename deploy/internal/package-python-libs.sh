#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
IMAGE="${TEST_AGENT_OPENCODE_WORKER_IMAGE:-test-agent-opencode-worker:internal}"
OUTPUT_DIR="${TEST_AGENT_IMAGE_OUTPUT_DIR:-${SCRIPT_DIR}/dist}"
PLATFORM="linux/amd64"
INDEX_URL="${PYTHON_PACKAGE_INDEX_URL:-https://mirrors.huaweicloud.com/repository/pypi/simple}"
LOCK_FILE="${SCRIPT_DIR}/python-libs/requirements-linux-amd64.lock"
ARTIFACT_NAME="test-agent-python-libs-py313-linux-amd64.tar.gz"

usage() {
  cat <<'USAGE'
Usage: deploy/internal/package-python-libs.sh [options]

Build the independent, offline Python 3.13 linux/amd64 library bundle.

Options:
  --image <image>        Worker image containing Python 3.13.14.
  --output-dir <path>    Artifact directory. Defaults to deploy/internal/dist.
  --platform <platform>  Build platform. Defaults to linux/amd64.
  --index-url <url>      Build-time Python package mirror.
  --lock-file <path>     Fully pinned requirements file with wheel hashes.
  -h, --help             Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --image)
      IMAGE="$2"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --platform)
      PLATFORM="$2"
      shift 2
      ;;
    --index-url)
      INDEX_URL="$2"
      shift 2
      ;;
    --lock-file)
      LOCK_FILE="$2"
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

for command_name in docker tar; do
  command -v "${command_name}" >/dev/null 2>&1 || {
    echo "Required command not found: ${command_name}" >&2
    exit 1
  }
done
[[ -f "${LOCK_FILE}" ]] || {
  echo "Python requirements lock not found: ${LOCK_FILE}" >&2
  exit 1
}
docker image inspect "${IMAGE}" >/dev/null 2>&1 || {
  echo "Worker image not found; build or load it first: ${IMAGE}" >&2
  exit 1
}

mkdir -p "${OUTPUT_DIR}"
tmp_root="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-python-libs.XXXXXX")"
cleanup() {
  rm -rf "${tmp_root}"
}
trap cleanup EXIT

bundle_root="${tmp_root}/python-libs"
wheel_root="${tmp_root}/wheels"
mkdir -p "${bundle_root}/site-packages" "${wheel_root}"

# 只下载锁文件中带哈希的二进制 wheel；禁止源码包和未锁定的传递依赖进入交付物。
docker run --rm --platform "${PLATFORM}" \
  --env "PIP_INDEX_URL=${INDEX_URL}" \
  --env "PIP_NO_INDEX=0" \
  --env "PIP_DISABLE_PIP_VERSION_CHECK=1" \
  --env "PIP_NO_CACHE_DIR=1" \
  --volume "${LOCK_FILE}:/input/requirements.lock:ro" \
  --volume "${tmp_root}:/build" \
  --entrypoint sh \
  "${IMAGE}" -lc '
    set -eux
    python3 -m pip download \
      --dest /build/wheels \
      --only-binary=:all: \
      --no-deps \
      --require-hashes \
      --retries 5 \
      --timeout 60 \
      -r /input/requirements.lock
    find /build/wheels -maxdepth 1 -type f ! -name "*.whl" -print -quit | grep -q . && {
      echo "Non-wheel Python artifact detected" >&2
      exit 1
    }
    python3 -m pip install \
      --no-index \
      --find-links=/build/wheels \
      --only-binary=:all: \
      --no-compile \
      --no-deps \
      --no-warn-script-location \
      --root-user-action=ignore \
      --require-hashes \
      --target=/build/python-libs/site-packages \
      -r /input/requirements.lock
    cd /build/wheels
    sha256sum *.whl | LC_ALL=C sort -k2 > /build/python-libs/WHEELS.sha256
  '

cp "${LOCK_FILE}" "${bundle_root}/requirements.lock"
python_version="$(docker run --rm --platform "${PLATFORM}" --network none \
  --entrypoint python3 "${IMAGE}" -c 'import platform; print(platform.python_version())')"
[[ "${python_version}" == "3.13.14" ]] || {
  echo "Python library bundle requires Python 3.13.14, got ${python_version}" >&2
  exit 1
}

if command -v sha256sum >/dev/null 2>&1; then
  lock_sha256="$(sha256sum "${LOCK_FILE}" | awk '{print $1}')"
else
  lock_sha256="$(shasum -a 256 "${LOCK_FILE}" | awk '{print $1}')"
fi
printf '%s\n' \
  'TEST_AGENT_PYTHON_LIBS_FORMAT=1' \
  "PYTHON_VERSION=${python_version}" \
  "PLATFORM=${PLATFORM}" \
  "REQUIREMENTS_SHA256=${lock_sha256}" \
  >"${bundle_root}/VERSION"

# FILES.sha256 覆盖真正部署的全部内容；校验文件本身不递归纳入。
docker run --rm --platform "${PLATFORM}" --network none \
  --volume "${bundle_root}:/opt/test-agent/python-libs" \
  --entrypoint sh "${IMAGE}" -lc '
    set -eu
    cd /opt/test-agent/python-libs
    find . -type f ! -name FILES.sha256 -exec sha256sum {} + \
      | LC_ALL=C sort -k2 > FILES.sha256
  '

"${ROOT_DIR}/tools/verify-python-libs.sh" \
  --image "${IMAGE}" \
  --root "${bundle_root}" \
  --platform "${PLATFORM}"

artifact="${OUTPUT_DIR}/${ARTIFACT_NAME}"
artifact_tmp="${artifact}.new.$$"
checksum_tmp="${artifact}.sha256.new.$$"
tar -C "${tmp_root}" -czf "${artifact_tmp}" python-libs
if command -v sha256sum >/dev/null 2>&1; then
  artifact_sha256="$(sha256sum "${artifact_tmp}" | awk '{print $1}')"
else
  artifact_sha256="$(shasum -a 256 "${artifact_tmp}" | awk '{print $1}')"
fi
printf '%s  %s\n' "${artifact_sha256}" "${ARTIFACT_NAME}" >"${checksum_tmp}"
mv -f "${artifact_tmp}" "${artifact}"
mv -f "${checksum_tmp}" "${artifact}.sha256"

ls -lh "${artifact}" "${artifact}.sha256"
echo "Python library artifact: ${artifact}"
