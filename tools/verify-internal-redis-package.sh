#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_SCRIPT="${ROOT_DIR}/deploy/internal/package-redis-offline.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-redis-package-verify.XXXXXX")"
cleanup() { rm -rf "${TMP_ROOT}"; }
trap cleanup EXIT

sha256_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

IMAGE_ROOT="${TMP_ROOT}/image"
OUTPUT_DIR="${TMP_ROOT}/output"
EXTRACT_ROOT="${TMP_ROOT}/extract"
mkdir -p "${IMAGE_ROOT}" "${OUTPUT_DIR}" "${EXTRACT_ROOT}"
printf '%s\n' \
  '[{"Config":"fixture.json","RepoTags":["test-agent-redis:7.4.9-alpine"],"Layers":[]}]' \
  >"${IMAGE_ROOT}/manifest.json"
printf '{}\n' >"${IMAGE_ROOT}/fixture.json"
tar -C "${IMAGE_ROOT}" -cf "${TMP_ROOT}/redis.tar" manifest.json fixture.json

first_output="$(${PACKAGE_SCRIPT} --image-tar "${TMP_ROOT}/redis.tar" --output-dir "${OUTPUT_DIR}")"
archive="${OUTPUT_DIR}/test-agent-redis-offline.zip"
checksum="${archive}.sha256"
[[ -f "${archive}" && -f "${checksum}" ]]
[[ "$(awk '{print $1}' "${checksum}")" == "$(sha256_digest "${archive}")" ]]
unzip -tq "${archive}" >/dev/null
unzip -q "${archive}" -d "${EXTRACT_ROOT}"

bundle="${EXTRACT_ROOT}/test-agent-redis-offline"
for required in \
  START-HERE.md \
  deploy-redis.sh \
  redis-healthcheck.sh \
  config/redis.env \
  config/redis.conf \
  config/backend-redis.env \
  test-agent-redis_7.4.9-alpine-linux-amd64.tar \
  test-agent-redis_7.4.9-alpine-linux-amd64.tar.sha256; do
  [[ -f "${bundle}/${required}" ]] || {
    echo "Bundle entry is missing: ${required}" >&2
    exit 1
  }
done

! grep -R 'REPLACE_' "${bundle}/config" >/dev/null
redis_password="$(awk '$1 == "requirepass" {print $2; exit}' "${bundle}/config/redis.conf")"
backend_password="$(awk -F= '$1 == "TEST_AGENT_REDIS_PASSWORD" {print $2; exit}' "${bundle}/config/backend-redis.env")"
[[ ${#redis_password} -eq 64 && "${redis_password}" == "${backend_password}" ]]
[[ "${first_output}" != *"${redis_password}"* ]]
for sensitive_file in config/redis.env config/redis.conf config/backend-redis.env; do
  file_mode="$(stat -f '%Lp' "${bundle}/${sensitive_file}" 2>/dev/null || stat -c '%a' "${bundle}/${sensitive_file}")"
  [[ "${file_mode}" == "600" ]] || {
    echo "Sensitive bundle entry must be mode 600: ${sensitive_file}" >&2
    exit 1
  }
done
grep -Fxq 'appendonly yes' "${bundle}/config/redis.conf"
grep -Fxq 'appendfsync everysec' "${bundle}/config/redis.conf"
grep -Fxq 'maxmemory-policy noeviction' "${bundle}/config/redis.conf"
grep -Fxq 'protected-mode yes' "${bundle}/config/redis.conf"
if grep -Fq -- '--platform' "${bundle}/deploy-redis.sh"; then
  echo "Packaged Redis deploy script must support Docker daemons without runtime --platform" >&2
  exit 1
fi
grep -Fq 'Loaded Redis image is not linux/amd64' "${bundle}/deploy-redis.sh"
grep -Fq 'test-agent-redis.conf' "${bundle}/deploy-redis.sh"
grep -Fq '/usr/bin/setpriv --reuid redis --regid redis --clear-groups' "${bundle}/deploy-redis.sh"

image_tar="${bundle}/test-agent-redis_7.4.9-alpine-linux-amd64.tar"
[[ "$(awk '{print $1}' "${image_tar}.sha256")" == "$(sha256_digest "${image_tar}")" ]]

# 只重封手册时必须保留原密码和镜像，不能生成与平台节点包失配的新凭据。
${PACKAGE_SCRIPT} --zip-only --output-dir "${OUTPUT_DIR}" >/dev/null
rm -rf "${EXTRACT_ROOT}"
mkdir -p "${EXTRACT_ROOT}"
unzip -q "${archive}" -d "${EXTRACT_ROOT}"
bundle="${EXTRACT_ROOT}/test-agent-redis-offline"
repacked_password="$(awk '$1 == "requirepass" {print $2; exit}' "${bundle}/config/redis.conf")"
[[ "${repacked_password}" == "${redis_password}" ]]
grep -Fq 'cd ~/Desktop/mimoagent/0709' "${bundle}/START-HERE.md"
[[ "$(awk '{print $1}' "${bundle}/test-agent-redis_7.4.9-alpine-linux-amd64.tar.sha256")" \
  == "$(sha256_digest "${bundle}/test-agent-redis_7.4.9-alpine-linux-amd64.tar")" ]]

# 固定文件名允许重复封包覆盖，不堆积带时间戳的旧敏感包。
${PACKAGE_SCRIPT} --image-tar "${TMP_ROOT}/redis.tar" --output-dir "${OUTPUT_DIR}" >/dev/null
[[ "$(find "${OUTPUT_DIR}" -maxdepth 1 -type f -name 'test-agent-redis-offline.zip' | wc -l | tr -d ' ')" == "1" ]]

printf 'Redis offline package verification passed\n'
