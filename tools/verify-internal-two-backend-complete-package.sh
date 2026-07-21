#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_SCRIPT="${ROOT_DIR}/deploy/internal/package-two-backend-complete.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-complete-package-verify.XXXXXX")"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
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
  printf '%s  %s\n' "$(sha256_digest "${file}")" "$(basename "${file}")" \
    >"${file}.sha256"
}

RELEASE_ROOT="${TMP_ROOT}/release-root"
RELEASE_ARCHIVE="${TMP_ROOT}/test-agent-internal-release.zip"
NODES_DIR="${TMP_ROOT}/nodes"
OUTPUT_DIR="${TMP_ROOT}/output"
mkdir -p "${RELEASE_ROOT}/dist/backend" "${RELEASE_ROOT}/deploy/internal" \
  "${NODES_DIR}" "${OUTPUT_DIR}"

JAR_ROOT="${TMP_ROOT}/jar-root"
mkdir -p "${JAR_ROOT}/BOOT-INF/classes"
printf 'fixture-rsa-private-key\n' >"${JAR_ROOT}/BOOT-INF/classes/rsa-private.key"
(cd "${JAR_ROOT}" && zip -qr "${RELEASE_ROOT}/dist/backend/test-agent-app.jar" .)
printf 'frontend\n' >"${RELEASE_ROOT}/dist/test-agent-frontend-dist.tar.gz"
printf 'programs\n' >"${RELEASE_ROOT}/dist/test-agent-programs.tar.gz"
printf 'worker\n' >"${RELEASE_ROOT}/dist/test-agent-opencode-worker_internal-linux-amd64.tar"
printf '#!/usr/bin/env bash\n' >"${RELEASE_ROOT}/deploy/internal/deploy-multi-backend-node.sh"
(cd "${RELEASE_ROOT}" && zip -qr "${RELEASE_ARCHIVE}" .)
write_checksum "${RELEASE_ARCHIVE}"

create_node_archive() {
  local node_dir="$1"
  local archive_name="$2"
  local config_name="$3"
  local root="${TMP_ROOT}/node-${node_dir}"
  mkdir -p "${root}/${node_dir}/config"
  printf '#!/usr/bin/env bash\n' >"${root}/${node_dir}/deploy-multi-backend-node.sh"
  printf '# fixture deployment guide\n' >"${root}/${node_dir}/MULTI-BACKEND.md"
  printf 'SENSITIVE_VALUE=must-not-print\n' >"${root}/${node_dir}/config/${config_name}"
  if [[ "${config_name}" == backend.env ]]; then
    printf 'TEST_AGENT_OPENCODE_MANAGER_TOKEN=must-not-print\n' \
      >"${root}/${node_dir}/config/docker.env"
  fi
  tar -C "${root}" -czf "${NODES_DIR}/${archive_name}" "${node_dir}"
  write_checksum "${NODES_DIR}/${archive_name}"
}

create_node_archive \
  test-agent-two-backend-122.233.30.4 \
  test-agent-two-backend-122.233.30.4-SENSITIVE.tar.gz \
  backend.env
create_node_archive \
  test-agent-two-backend-122.233.30.114 \
  test-agent-two-backend-122.233.30.114-SENSITIVE.tar.gz \
  backend.env
create_node_archive \
  test-agent-two-backend-122.233.30.2 \
  test-agent-two-backend-122.233.30.2.tar.gz \
  nginx.env

run_package() {
  bash "${PACKAGE_SCRIPT}" \
    --release-archive "${RELEASE_ARCHIVE}" \
    --nodes-dir "${NODES_DIR}" \
    --output-dir "${OUTPUT_DIR}"
}

output="$(run_package 2>&1)"
if grep -Fq 'must-not-print' <<<"${output}"; then
  echo "Complete package output leaked node configuration" >&2
  exit 1
fi
grep -Fq 'test-agent-two-backend-complete.zip' <<<"${output}"

BUNDLE="${OUTPUT_DIR}/test-agent-two-backend-complete.zip"
CHECKSUM="${BUNDLE}.sha256"
test -s "${BUNDLE}"
test -s "${CHECKSUM}"
(cd "${OUTPUT_DIR}" && shasum -a 256 -c "$(basename "${CHECKSUM}")") >/dev/null
unzip -tq "${BUNDLE}" >/dev/null

listing="$(unzip -Z1 "${BUNDLE}")"
grep -Fxq 'test-agent-two-backend-complete/START-HERE.md' <<<"${listing}"
grep -Fxq 'test-agent-two-backend-complete/test-agent-internal-release.zip' <<<"${listing}"
grep -Fxq 'test-agent-two-backend-complete/nodes/test-agent-two-backend-122.233.30.4-SENSITIVE.tar.gz' <<<"${listing}"
grep -Fxq 'test-agent-two-backend-complete/nodes/test-agent-two-backend-122.233.30.114-SENSITIVE.tar.gz' <<<"${listing}"
grep -Fxq 'test-agent-two-backend-complete/nodes/test-agent-two-backend-122.233.30.2.tar.gz' <<<"${listing}"
if grep -Eq '202[0-9]|-v[0-9]+/' <<<"${listing}"; then
  echo "Fixed-name bundle unexpectedly contains a dated/versioned root" >&2
  exit 1
fi

# 第二次执行必须无交互覆盖固定文件名，不能生成日期或版本后缀的新包。
run_package >/dev/null
test "$(find "${OUTPUT_DIR}" -maxdepth 1 -type f -name 'test-agent-two-backend-complete*.zip' | wc -l | tr -d '[:space:]')" = 1

echo 'Fixed-name complete two-backend bundle, checksum, structure, redaction and overwrite verified'
