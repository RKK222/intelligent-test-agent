#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_SCRIPT="${ROOT_DIR}/deploy/internal/package-mysql-offline.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-mysql-package-verify.XXXXXX")"
cleanup() { rm -rf "${TMP_ROOT}"; }
trap cleanup EXIT

sha256_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

write_checksum() {
  local file="$1"
  printf '%s  %s\n' "$(sha256_digest "${file}")" "$(basename "${file}")" >"${file}.sha256"
}

NODES_DIR="${TMP_ROOT}/nodes"
OUTPUT_DIR="${TMP_ROOT}/output"
IMAGE_ROOT="${TMP_ROOT}/image"
NODE_DIR="test-agent-two-backend-122.233.30.147-mysql"
NODE_ROOT="${TMP_ROOT}/node"
mkdir -p "${NODES_DIR}" "${OUTPUT_DIR}" "${IMAGE_ROOT}" "${NODE_ROOT}/${NODE_DIR}/config"

printf '[]\n' >"${IMAGE_ROOT}/manifest.json"
tar -C "${IMAGE_ROOT}" -cf "${TMP_ROOT}/mysql_8.4-linux-amd64.tar" manifest.json
printf '#!/usr/bin/env bash\n' >"${NODE_ROOT}/${NODE_DIR}/deploy-xxl-job-mysql.sh"
printf '# fixture deployment guide\n' >"${NODE_ROOT}/${NODE_DIR}/MULTI-BACKEND.md"
printf '%s\n' \
  'TEST_AGENT_XXL_JOB_MYSQL_IMAGE=mysql:8.4' \
  'TEST_AGENT_XXL_JOB_MYSQL_CONTAINER=test-agent-xxl-job-mysql' \
  'TEST_AGENT_XXL_JOB_MYSQL_HOST_PORT=3306' \
  'TEST_AGENT_XXL_JOB_MYSQL_DATA_ROOT=/data/testagent/mysql' \
  'TEST_AGENT_XXL_JOB_MYSQL_ROOT_PASSWORD=root-secret-must-not-print' \
  'TEST_AGENT_XXL_JOB_MYSQL_DATABASE=xxl_job' \
  'TEST_AGENT_XXL_JOB_MYSQL_USERNAME=xxl_job' \
  'TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=app-secret-must-not-print' \
  >"${NODE_ROOT}/${NODE_DIR}/config/mysql.env"
NODE_ARCHIVE="${NODES_DIR}/${NODE_DIR}-SENSITIVE.tar.gz"
tar -C "${NODE_ROOT}" -czf "${NODE_ARCHIVE}" "${NODE_DIR}"
write_checksum "${NODE_ARCHIVE}"

run_package() {
  bash "${PACKAGE_SCRIPT}" \
    --image-tar "${TMP_ROOT}/mysql_8.4-linux-amd64.tar" \
    --nodes-dir "${NODES_DIR}" \
    --output-dir "${OUTPUT_DIR}"
}

output="$(run_package 2>&1)"
if grep -Eq 'root-secret|app-secret' <<<"${output}"; then
  echo "MySQL package output leaked prepared credentials" >&2
  exit 1
fi

BUNDLE="${OUTPUT_DIR}/test-agent-mysql-offline.zip"
CHECKSUM="${BUNDLE}.sha256"
test -s "${BUNDLE}"
test -s "${CHECKSUM}"
(cd "${OUTPUT_DIR}" && shasum -a 256 -c "$(basename "${CHECKSUM}")") >/dev/null
unzip -tq "${BUNDLE}" >/dev/null
listing="$(unzip -Z1 "${BUNDLE}")"
grep -Fxq 'test-agent-mysql-offline/START-HERE.md' <<<"${listing}"
grep -Fxq 'test-agent-mysql-offline/deploy-node-common.sh' <<<"${listing}"
grep -Fxq 'test-agent-mysql-offline/deploy-mysql-node.sh' <<<"${listing}"
grep -Fxq 'test-agent-mysql-offline/mysql_8.4-linux-amd64.tar' <<<"${listing}"
grep -Fxq 'test-agent-mysql-offline/mysql_8.4-linux-amd64.tar.sha256' <<<"${listing}"
grep -Fxq "test-agent-mysql-offline/nodes/${NODE_DIR}-SENSITIVE.tar.gz" <<<"${listing}"
if grep -Eq 'test-agent-internal-release|opencode-worker|122\.233\.30\.(4|114|2)' <<<"${listing}"; then
  echo "MySQL bundle unexpectedly contains platform artifacts" >&2
  exit 1
fi

# 第二次执行必须无交互覆盖固定文件名。
run_package >/dev/null
test "$(find "${OUTPUT_DIR}" -maxdepth 1 -type f -name 'test-agent-mysql-offline*.zip' | wc -l | tr -d '[:space:]')" = 1

echo 'Fixed-name standalone MySQL bundle, checksum, structure, redaction and overwrite verified'
