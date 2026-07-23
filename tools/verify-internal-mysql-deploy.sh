#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_SCRIPT="${ROOT_DIR}/deploy/internal/deploy-xxl-job-mysql.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-mysql-deploy-verify.XXXXXX")"
cleanup() { rm -rf "${TMP_ROOT}"; }
trap cleanup EXIT

mkdir -p "${TMP_ROOT}/release/dist" "${TMP_ROOT}/data"
printf 'fixture image\n' >"${TMP_ROOT}/release/dist/mysql_8.4-linux-amd64.tar"
# 让镜像条目后仍有超过管道缓冲区的 ZIP 列表，回归 grep -q + pipefail 的 SIGPIPE 误报。
mkdir -p "${TMP_ROOT}/release/padding"
for index in $(seq 1 2000); do
  printf 'fixture\n' >"${TMP_ROOT}/release/padding/entry-${index}-after-mysql-image.txt"
done
(cd "${TMP_ROOT}/release" && zip -qr "${TMP_ROOT}/release.zip" .)

write_valid_env() {
  local target="$1"
  printf '%s\n' \
    'TEST_AGENT_XXL_JOB_MYSQL_IMAGE=mysql:8.4' \
    'TEST_AGENT_XXL_JOB_MYSQL_CONTAINER=test-agent-xxl-job-mysql' \
    'TEST_AGENT_XXL_JOB_MYSQL_HOST_PORT=13306' \
    "TEST_AGENT_XXL_JOB_MYSQL_DATA_ROOT=${TMP_ROOT}/data" \
    'TEST_AGENT_XXL_JOB_MYSQL_ROOT_PASSWORD=root-secret-must-not-print' \
    'TEST_AGENT_XXL_JOB_MYSQL_DATABASE=xxl_job' \
    'TEST_AGENT_XXL_JOB_MYSQL_USERNAME=xxl_job' \
    'TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=app-secret-must-not-print' \
    >"${target}"
  chmod 0600 "${target}"
}

VALID_ENV="${TMP_ROOT}/mysql.env"
write_valid_env "${VALID_ENV}"
output="$(bash "${DEPLOY_SCRIPT}" \
  --env-file "${VALID_ENV}" \
  --release-archive "${TMP_ROOT}/release.zip" \
  validate 2>&1)"
grep -Fq 'MySQL configuration and offline image validation passed' <<<"${output}"
if grep -Eq 'root-secret|app-secret' <<<"${output}"; then
  echo "MySQL validation leaked a prepared password" >&2
  exit 1
fi

BAD_ENV="${TMP_ROOT}/mysql-placeholder.env"
write_valid_env "${BAD_ENV}"
sed -i.bak \
  's/^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=.*/TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=REPLACE_XXL_JOB_MYSQL_PASSWORD/' \
  "${BAD_ENV}"
rm -f "${BAD_ENV}.bak"
if bash "${DEPLOY_SCRIPT}" --env-file "${BAD_ENV}" validate >/dev/null 2>&1; then
  echo "MySQL validation unexpectedly accepted a placeholder password" >&2
  exit 1
fi

grep -Fq -- '--platform linux/amd64' "${DEPLOY_SCRIPT}"
grep -Fq -- '--restart unless-stopped' "${DEPLOY_SCRIPT}"
grep -Fq 'Existing database data is never deleted' "${DEPLOY_SCRIPT}"
grep -Fq 'MySQL container created: id=' "${DEPLOY_SCRIPT}"
grep -Fq 'MySQL container state (docker ps -a):' "${DEPLOY_SCRIPT}"
grep -Fq 'docker run failed for MySQL container' "${DEPLOY_SCRIPT}"
grep -Fq 'getenforce' "${DEPLOY_SCRIPT}"

echo 'Standalone MySQL config, diagnostics, SELinux mount, secret redaction and offline deploy contract verified'
