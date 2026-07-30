#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ARCHIVE=""
CHECKSUM_FILE=""
ENV_FILE="/data/testagent/config/docker.env"
INSTALL_ROOT="${TEST_AGENT_PYTHON_LIBS_ROOT:-}"
IMAGE="${TEST_AGENT_OPENCODE_WORKER_IMAGE:-}"
CONTAINER_NAME="test-agent-opencode-worker"
RESTART_WORKER=1

usage() {
  cat <<'USAGE'
Usage: deploy/internal/deploy-python-libs.sh --archive <tar.gz> [options]

Install the independently versioned Python library bundle and restart the worker.

Options:
  --archive <path>       test-agent-python-libs-py313-linux-amd64.tar.gz.
  --checksum <path>      Checksum file. Defaults to <archive>.sha256.
  --env-file <path>      Worker dotenv file. Defaults to /data/testagent/config/docker.env.
  --install-root <path>  Host install path. Defaults to /data/testagent/python-libs.
  --image <image>        Worker image; otherwise read TEST_AGENT_OPENCODE_WORKER_IMAGE.
  --name <name>          Worker container name. Defaults to test-agent-opencode-worker.
  --no-restart           Install and verify without restarting the worker.
  -h, --help             Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --archive)
      ARCHIVE="$2"
      shift 2
      ;;
    --checksum)
      CHECKSUM_FILE="$2"
      shift 2
      ;;
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --install-root)
      INSTALL_ROOT="$2"
      shift 2
      ;;
    --image)
      IMAGE="$2"
      shift 2
      ;;
    --name)
      CONTAINER_NAME="$2"
      shift 2
      ;;
    --no-restart)
      RESTART_WORKER=0
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
  local file="$1" line key value
  [[ -f "${file}" ]] || return 0
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

for command_name in docker sha256sum tar; do
  command -v "${command_name}" >/dev/null 2>&1 || {
    echo "Required command not found: ${command_name}" >&2
    exit 1
  }
done
[[ -n "${ARCHIVE}" && -f "${ARCHIVE}" ]] || {
  echo "Python library archive not found: ${ARCHIVE:-<empty>}" >&2
  exit 1
}
if [[ -z "${CHECKSUM_FILE}" && -f "${ARCHIVE}.sha256" ]]; then
  CHECKSUM_FILE="${ARCHIVE}.sha256"
fi
[[ -n "${CHECKSUM_FILE}" && -f "${CHECKSUM_FILE}" ]] || {
  echo "Python library checksum file not found: ${CHECKSUM_FILE:-${ARCHIVE}.sha256}" >&2
  exit 1
}
expected_sha256="$(awk 'NF >= 2 { print $1; exit }' "${CHECKSUM_FILE}")"
[[ "${expected_sha256}" =~ ^[0-9a-f]{64}$ ]] || {
  echo "Python library checksum is invalid: ${CHECKSUM_FILE}" >&2
  exit 1
}
actual_sha256="$(sha256sum "${ARCHIVE}" | awk '{print $1}')"
[[ "${actual_sha256}" == "${expected_sha256}" ]] || {
  echo "Python library archive checksum mismatch: expected=${expected_sha256} actual=${actual_sha256}" >&2
  exit 1
}

load_dotenv "${ENV_FILE}"
IMAGE="${IMAGE:-${TEST_AGENT_OPENCODE_WORKER_IMAGE:-test-agent-opencode-worker:internal}}"
INSTALL_ROOT="${INSTALL_ROOT:-${TEST_AGENT_PYTHON_LIBS_ROOT:-/data/testagent/python-libs}}"
docker image inspect "${IMAGE}" >/dev/null 2>&1 || {
  echo "Worker image not found: ${IMAGE}" >&2
  exit 1
}

# 拒绝绝对路径、父目录穿越以及非 python-libs 根目录成员，再进入临时目录验证。
while IFS= read -r entry; do
  case "${entry}" in
    python-libs|python-libs/*) ;;
    *)
      echo "Unsafe or unexpected archive entry: ${entry}" >&2
      exit 1
      ;;
  esac
  case "/${entry}/" in
    */../*)
      echo "Parent traversal archive entry rejected: ${entry}" >&2
      exit 1
      ;;
  esac
done < <(tar -tzf "${ARCHIVE}")

install_parent="$(dirname "${INSTALL_ROOT}")"
mkdir -p "${install_parent}"
staging_root="$(mktemp -d "${install_parent}/.python-libs.deploy.XXXXXX")"
cleanup() {
  rm -rf "${staging_root}"
}
trap cleanup EXIT
tar -C "${staging_root}" -xzf "${ARCHIVE}"
candidate="${staging_root}/python-libs"

verifier="${SCRIPT_DIR}/verify-python-libs.sh"
if [[ ! -x "${verifier}" ]]; then
  verifier="${SCRIPT_DIR}/../../tools/verify-python-libs.sh"
fi
[[ -x "${verifier}" ]] || {
  echo "Python library verifier is not executable: ${verifier}" >&2
  exit 1
}
"${verifier}" \
  --image "${IMAGE}" \
  --root "${candidate}"

worker_script="${SCRIPT_DIR}/opencode-worker-docker.sh"
if [[ "${RESTART_WORKER}" -eq 1 && ! -x "${worker_script}" ]]; then
  echo "Worker deployment script is not executable: ${worker_script}" >&2
  exit 1
fi

restart_and_verify_worker() {
  "${worker_script}" --env-file "${ENV_FILE}" --name "${CONTAINER_NAME}" restart \
    && docker exec "${CONTAINER_NAME}" python3 -c \
      'import jsonschema, openpyxl, orjson, pandas, xlsxwriter; from docx import Document; print("live Python libraries ok")'
}

timestamp="$(date +%Y%m%d%H%M%S)"
backup_root=""
if [[ -e "${INSTALL_ROOT}" ]]; then
  backup_root="${INSTALL_ROOT}.bak.${timestamp}.$$"
  mv "${INSTALL_ROOT}" "${backup_root}"
fi
mv "${candidate}" "${INSTALL_ROOT}"

if [[ "${RESTART_WORKER}" -eq 1 ]]; then
  if ! restart_and_verify_worker; then
    echo "Worker restart or live Python library verification failed; rolling back." >&2
    failed_root="${INSTALL_ROOT}.failed.${timestamp}.$$"
    mv "${INSTALL_ROOT}" "${failed_root}"
    if [[ -n "${backup_root}" && -d "${backup_root}" ]]; then
      mv "${backup_root}" "${INSTALL_ROOT}"
      echo "Previous Python library bundle restored; failed candidate retained at ${failed_root}" >&2
    else
      echo "No previous Python library bundle; failed candidate retained at ${failed_root}" >&2
    fi
    if ! "${worker_script}" --env-file "${ENV_FILE}" --name "${CONTAINER_NAME}" restart; then
      echo "Worker restart also failed after rollback; manual recovery is required." >&2
    fi
    exit 1
  fi
fi

echo "Python library bundle installed: ${INSTALL_ROOT}"
[[ -z "${backup_root}" ]] || echo "Previous bundle backup: ${backup_root}"
