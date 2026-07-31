#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTERNAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
FIXTURE_DIR="$(mktemp -d)"
OUTPUT_DIR="$(mktemp -d)"
trap 'rm -rf "${FIXTURE_DIR}" "${OUTPUT_DIR}"' EXIT

mkdir -p "${FIXTURE_DIR}/images" "${FIXTURE_DIR}/clients" "${FIXTURE_DIR}/bin" \
  "${FIXTURE_DIR}/sbom" "${FIXTURE_DIR}/source"
for file in \
  images/lobehub-image.tar images/paradedb-image.tar images/rustfs-image.tar \
  clients/lobehub-windows-x64.exe clients/lobehub-linux-x86_64.tar.gz \
  bin/mc-linux-amd64 sbom/lobehub.spdx.json \
  source/lobehub-v2.2.11-platform.5.tar.gz \
  approved-resources.json LICENSES.txt; do
  printf 'fixture:%s\n' "${file}" >"${FIXTURE_DIR}/${file}"
done
cat >"${FIXTURE_DIR}/release.env" <<'EOF'
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.5
LOBEHUB_UPSTREAM_VERSION=v2.2.11
LOBEHUB_UPSTREAM_COMMIT=5b4cef6
LOBEHUB_FORK_COMMIT=57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c
LOBEHUB_PLATFORM_CONTRACT_VERSION=2
LOBEHUB_PARADEDB_POSTGRES_MAJOR=17
LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=true
LOBEHUB_LINUX_CLIENT_APPROVED=true
LOBEHUB_LINUX_EXECUTION_DEFAULT=false
LOBEHUB_APP_IMAGE=test-agent/lobehub:v2.2.11-platform.5
LOBEHUB_APP_IMAGE_ID=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
LOBEHUB_PARADEDB_IMAGE=test-agent/paradedb:pg17-v2.2.11-platform.5
LOBEHUB_PARADEDB_IMAGE_ID=sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
LOBEHUB_RUSTFS_IMAGE=test-agent/rustfs:v2.2.11-platform.5
LOBEHUB_RUSTFS_IMAGE_ID=sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
EOF

if command -v sha256sum >/dev/null 2>&1; then
  WINDOWS_SHA="$(sha256sum "${FIXTURE_DIR}/clients/lobehub-windows-x64.exe" | awk '{print $1}')"
  LINUX_SHA="$(sha256sum "${FIXTURE_DIR}/clients/lobehub-linux-x86_64.tar.gz" | awk '{print $1}')"
else
  WINDOWS_SHA="$(shasum -a 256 "${FIXTURE_DIR}/clients/lobehub-windows-x64.exe" | awk '{print $1}')"
  LINUX_SHA="$(shasum -a 256 "${FIXTURE_DIR}/clients/lobehub-linux-x86_64.tar.gz" | awk '{print $1}')"
fi
cat >"${FIXTURE_DIR}/linux-client-build-evidence.txt" <<EOF
LINUX_BUILD_STATUS=Candidate
LINUX_BUILDER=linux-builder@example.internal
LINUX_CLIENT_FILE_SHA256=${LINUX_SHA}
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.5
LOBEHUB_FORK_COMMIT=57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c
CLIENT_ARCHITECTURE=x86_64
CLIENT_EXECUTION_MODE=disabled
BUILD_OS=Enterprise Linux Builder 9.6
BUILD_KERNEL=5.14.0-builder.x86_64
BUILD_NODE_VERSION=v24.11.1
BUILD_BUN_VERSION=1.3.2
SOURCE_ARCHIVE_SHA256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
EOF
cat >"${FIXTURE_DIR}/windows-authenticode-verification.txt" <<EOF
AUTHENTICODE_STATUS=Valid
AUTHENTICODE_SUBJECT=CN=Enterprise Release Signing
AUTHENTICODE_THUMBPRINT=0123456789ABCDEF0123456789ABCDEF01234567
AUTHENTICODE_FILE_SHA256=${WINDOWS_SHA}
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.5
LOBEHUB_FORK_COMMIT=57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c
CLIENT_ARCHITECTURE=x64
CLIENT_EXECUTION_MODE=disabled
EOF
cat >"${FIXTURE_DIR}/linux-client-acceptance-record.txt" <<'EOF'
VALIDATION_RESULT=Passed
CLIENT_LOGIN_RESULT=Passed
DEVICE_EXECUTION_RESULT=Disabled
PUBLIC_NETWORK_DEPENDENCY_RESULT=None
RUNTIME_DOWNLOAD_RESULT=Blocked
LOCAL_DATA_ISOLATION_RESULT=Passed
REVIEWER=security-reviewer@example.internal
APPROVAL_CHANGE_ID=CHG-20260731-LOBEHUB
EOF
if command -v sha256sum >/dev/null 2>&1; then
  ACCEPTANCE_SHA="$(sha256sum "${FIXTURE_DIR}/linux-client-acceptance-record.txt" | awk '{print $1}')"
else
  ACCEPTANCE_SHA="$(shasum -a 256 "${FIXTURE_DIR}/linux-client-acceptance-record.txt" | awk '{print $1}')"
fi
cat >"${FIXTURE_DIR}/linux-client-verification.txt" <<EOF
LINUX_APPROVAL_STATUS=Approved
LINUX_APPROVER=security-reviewer@example.internal
LINUX_CLIENT_FILE_SHA256=${LINUX_SHA}
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.5
LOBEHUB_FORK_COMMIT=57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c
CLIENT_ARCHITECTURE=x86_64
CLIENT_EXECUTION_MODE=disabled
LINUX_VALIDATION_OS=Enterprise Linux 9.6
LINUX_VALIDATION_KERNEL=5.14.0-570.26.1.el9_6.x86_64
LINUX_ACCEPTANCE_RECORD_SHA256=${ACCEPTANCE_SHA}
LINUX_BUILD_EVIDENCE_SHA256=$(
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${FIXTURE_DIR}/linux-client-build-evidence.txt" | awk '{print $1}'
  else
    shasum -a 256 "${FIXTURE_DIR}/linux-client-build-evidence.txt" | awk '{print $1}'
  fi
)
EOF

(
  cd "${FIXTURE_DIR}"
  if command -v sha256sum >/dev/null 2>&1; then
    find . -type f ! -name SHA256SUMS | LC_ALL=C sort | while IFS= read -r file; do
      sha256sum "${file#./}"
    done >SHA256SUMS
  else
    find . -type f ! -name SHA256SUMS | LC_ALL=C sort | while IFS= read -r file; do
      shasum -a 256 "${file#./}"
    done >SHA256SUMS
  fi
)

TEST_AGENT_LOBEHUB_ARTIFACT_DIR="${FIXTURE_DIR}" \
  "${INTERNAL_DIR}/package-release.sh" --env-file /dev/null --lobehub-only --output-dir "${OUTPUT_DIR}" >/dev/null

test -f "${OUTPUT_DIR}/test-agent-lobehub-offline.zip"
test -f "${OUTPUT_DIR}/test-agent-lobehub-offline.zip.sha256"
unzip -Z1 "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" \
  | grep -Fx 'deploy/internal/lobehub.env.example' >/dev/null
unzip -Z1 "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" \
  | grep -Fx 'deploy/internal/lobehub-backup.sh' >/dev/null
unzip -Z1 "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" \
  | grep -Fx 'deploy/internal/lobehub-platform-probe.mjs' >/dev/null
unzip -Z1 "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" \
  | grep -Fx 'deploy/internal/lobehub-redis-acl.sh' >/dev/null
unzip -Z1 "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" \
  | grep -Fx 'deploy/internal/lobehub-client-artifact-contract.sh' >/dev/null
unzip -Z1 "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" \
  | grep -Fx 'dist/lobehub/linux-client-verification.txt' >/dev/null
unzip -Z1 "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" \
  | grep -Fx 'dist/lobehub/linux-client-acceptance-record.txt' >/dev/null
unzip -Z1 "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" \
  | grep -Fx 'dist/lobehub/linux-client-build-evidence.txt' >/dev/null
unzip -Z1 "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" \
  | grep -Fx 'dist/lobehub/source/lobehub-v2.2.11-platform.5.tar.gz' >/dev/null
unzip -Z1 "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" \
  | grep -Fx 'docs/architecture/lobehub-integration.md' >/dev/null
unzip -Z1 "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" \
  | grep -Fx 'docs/deployment/lobehub-client-build.md' >/dev/null
unzip -p "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" deploy/internal/lobehub-docker.sh \
  | grep -F "show server_version_num" >/dev/null
unzip -p "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" deploy/internal/lobehub-docker.sh \
  | grep -F 'test "$(cat /proc/1/comm)" = postgres' >/dev/null
unzip -p "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" deploy/internal/lobehub-docker.sh \
  | grep -F "anonymous set none" >/dev/null
unzip -p "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" deploy/internal/lobehub-docker.sh \
  | grep -F '"$(require_env LOBEHUB_APP_IMAGE)" /app/docker.cjs' >/dev/null
unzip -p "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" deploy/internal/lobehub-docker.sh \
  | grep -F 'LobeHub app did not become ready' >/dev/null
unzip -p "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" deploy/internal/lobehub-docker.sh \
  | grep -F 'create_app_runtime_env_file' >/dev/null
unzip -p "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" deploy/internal/lobehub-docker.sh \
  | grep -F 'LobeHub deployment verification passed' >/dev/null
unzip -p "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" deploy/internal/lobehub-backup.sh \
  | grep -F 'Restore requires --confirm-restore' >/dev/null
unzip -p "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" deploy/internal/nginx/lobehub.conf.template \
  | grep -F '__LOBEHUB_UPSTREAM__' >/dev/null
if unzip -p "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" deploy/internal/lobehub-docker.sh \
  | grep -F 'pnpm db:migrate' >/dev/null; then
  echo "LobeHub release still uses pnpm inside the scratch runtime image" >&2
  exit 1
fi

# 打包边界只允许目录和普通文件，不能把 FIFO、socket 或设备文件复制进离线包。
mkfifo "${FIXTURE_DIR}/unexpected.pipe"
if TEST_AGENT_LOBEHUB_ARTIFACT_DIR="${FIXTURE_DIR}" \
  "${INTERNAL_DIR}/package-release.sh" --env-file /dev/null --lobehub-only --no-zip \
  --output-dir "${OUTPUT_DIR}/special-file-output" >/dev/null 2>&1; then
  echo "LobeHub packager unexpectedly accepted a non-regular artifact" >&2
  exit 1
fi
rm "${FIXTURE_DIR}/unexpected.pipe"

cp "${FIXTURE_DIR}/linux-client-verification.txt" "${OUTPUT_DIR}/linux-client-verification.valid"
sed 's/^LINUX_APPROVAL_STATUS=Approved$/LINUX_APPROVAL_STATUS=Pending/' \
  "${OUTPUT_DIR}/linux-client-verification.valid" >"${FIXTURE_DIR}/linux-client-verification.txt"
(
  cd "${FIXTURE_DIR}"
  if command -v sha256sum >/dev/null 2>&1; then
    find . -type f ! -name SHA256SUMS | LC_ALL=C sort | while IFS= read -r file; do
      sha256sum "${file#./}"
    done >SHA256SUMS
  else
    find . -type f ! -name SHA256SUMS | LC_ALL=C sort | while IFS= read -r file; do
      shasum -a 256 "${file#./}"
    done >SHA256SUMS
  fi
)
if TEST_AGENT_LOBEHUB_ARTIFACT_DIR="${FIXTURE_DIR}" \
  "${INTERNAL_DIR}/package-release.sh" --env-file /dev/null --lobehub-only \
  --output-dir "${OUTPUT_DIR}/pending-linux" >/dev/null 2>&1; then
  echo "LobeHub packager unexpectedly accepted pending Linux approval evidence" >&2
  exit 1
fi
mv "${OUTPUT_DIR}/linux-client-verification.valid" \
  "${FIXTURE_DIR}/linux-client-verification.txt"

PLACEHOLDER_ENV="${OUTPUT_DIR}/lobehub-placeholder.env"
cp "${INTERNAL_DIR}/lobehub.env.example" "${PLACEHOLDER_ENV}"
chmod 0600 "${PLACEHOLDER_ENV}"
if LOBEHUB_ENV_FILE="${PLACEHOLDER_ENV}" TEST_AGENT_BASE_DIR="${OUTPUT_DIR}/runtime" \
  "${INTERNAL_DIR}/lobehub-docker.sh" validate-config >/dev/null 2>&1; then
  echo "Placeholder LobeHub secrets were unexpectedly accepted" >&2
  exit 1
fi

VALID_ENV="${OUTPUT_DIR}/lobehub-valid.env"
cat >"${VALID_ENV}" <<'EOF'
LOBEHUB_APP_IMAGE=test-agent/lobehub:v2.2.11-platform.5
LOBEHUB_PARADEDB_IMAGE=test-agent/paradedb:pg17-v2.2.11-platform.5
LOBEHUB_RUSTFS_IMAGE=test-agent/rustfs:v2.2.11-platform.5
POSTGRES_DB=lobehub
POSTGRES_USER=lobehub
POSTGRES_PASSWORD=database-password-32-bytes-minimum
DATABASE_URL=postgresql://lobehub:database-password-32-bytes-minimum@test-agent-lobehub-db:5432/lobehub
DATABASE_DRIVER=node
LOBEHUB_REDIS_HOST=redis.internal
LOBEHUB_REDIS_PORT=6379
LOBEHUB_REDIS_USERNAME=lobehub
LOBEHUB_REDIS_PASSWORD=redis-password-32-bytes-minimum
REDIS_URL=redis://lobehub:redis-password-32-bytes-minimum@redis.internal:6379/0
REDIS_PREFIX=lobehub:app
RUSTFS_ACCESS_KEY=rustfs-access-key-32-bytes
RUSTFS_SECRET_KEY=rustfs-secret-key-at-least-32-bytes
LOBEHUB_S3_BUCKET=lobehub-private
S3_ENDPOINT=http://test-agent-lobehub-rustfs:9000
S3_BUCKET=lobehub-private
S3_ACCESS_KEY_ID=rustfs-access-key-32-bytes
S3_SECRET_ACCESS_KEY=rustfs-secret-key-at-least-32-bytes
S3_ENABLE_PATH_STYLE=1
S3_SET_ACL=0
MC_HOST_lobehub=http://rustfs-access-key-32-bytes:rustfs-secret-key-at-least-32-bytes@test-agent-lobehub-rustfs:9000
APP_URL=http://chat.internal
INTERNAL_APP_URL=http://test-agent-lobehub-app:3210
LOBEHUB_APP_BIND_ADDRESS=127.0.0.1
AUTH_SECRET=auth-secret-at-least-thirty-two-bytes
KEY_VAULTS_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=
ENTERPRISE_INTERNAL_SCHEDULER_SECRET=internal-scheduler-secret-at-least-thirty-two-bytes
PLATFORM_SSO_ENABLED=1
LOBEHUB_ENTERPRISE_OFFLINE=1
AGENT_RUNTIME_MODE=local
PLATFORM_LAUNCH_URL=http://test-agent.internal/lobehub/launch
PLATFORM_SSO_REDEEM_URL=http://test-agent.internal/api/internal/platform/lobehub-sso/tickets/redeem
PLATFORM_SSO_REVOKE_URL=http://test-agent.internal/api/internal/platform/lobehub-sso/grants/revoke
PLATFORM_MODEL_GATEWAY_BASE_URL=http://test-agent.internal/api/internal/platform/model-gateway/v1
PLATFORM_SSO_HMAC_SECRET=hmac-secret-at-least-thirty-two-bytes
PLATFORM_MODEL_GRANT_ENCRYPTION_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=
TELEMETRY_DISABLED=1
LOBEHUB_DEVICE_EXECUTION_MODE=disabled
EOF
chmod 0600 "${VALID_ENV}"
if ! VALIDATE_OUTPUT="$(
  LOBEHUB_ENV_FILE="${VALID_ENV}" TEST_AGENT_BASE_DIR="${OUTPUT_DIR}/runtime" \
    "${INTERNAL_DIR}/lobehub-docker.sh" validate-config 2>&1
)"; then
  echo "${VALIDATE_OUTPUT}" >&2
  exit 1
fi
printf '%s\n' "${VALIDATE_OUTPUT}" | grep -Fx 'LobeHub configuration contract passed' >/dev/null

ENCODED_CREDENTIAL_ENV="${OUTPUT_DIR}/lobehub-encoded-credential.env"
sed \
  -e 's#^POSTGRES_PASSWORD=.*$#POSTGRES_PASSWORD=database password@32-bytes!#' \
  -e 's#^DATABASE_URL=.*$#DATABASE_URL=postgresql://lobehub:database%20password%4032-bytes%21@test-agent-lobehub-db:5432/lobehub#' \
  "${VALID_ENV}" >"${ENCODED_CREDENTIAL_ENV}"
chmod 0600 "${ENCODED_CREDENTIAL_ENV}"
LOBEHUB_ENV_FILE="${ENCODED_CREDENTIAL_ENV}" TEST_AGENT_BASE_DIR="${OUTPUT_DIR}/runtime" \
  "${INTERNAL_DIR}/lobehub-docker.sh" validate-config >/dev/null

INVALID_DRIVER_ENV="${OUTPUT_DIR}/lobehub-invalid-driver.env"
sed 's/^DATABASE_DRIVER=node$/DATABASE_DRIVER=neon/' "${VALID_ENV}" >"${INVALID_DRIVER_ENV}"
chmod 0600 "${INVALID_DRIVER_ENV}"
if LOBEHUB_ENV_FILE="${INVALID_DRIVER_ENV}" TEST_AGENT_BASE_DIR="${OUTPUT_DIR}/runtime" \
  "${INTERNAL_DIR}/lobehub-docker.sh" validate-config >/dev/null 2>&1; then
  echo "Neon database driver was unexpectedly accepted for offline ParadeDB" >&2
  exit 1
fi

INVALID_BIND_ENV="${OUTPUT_DIR}/lobehub-invalid-bind.env"
sed 's/^LOBEHUB_APP_BIND_ADDRESS=127.0.0.1$/LOBEHUB_APP_BIND_ADDRESS=0.0.0.0/' \
  "${VALID_ENV}" >"${INVALID_BIND_ENV}"
chmod 0600 "${INVALID_BIND_ENV}"
if LOBEHUB_ENV_FILE="${INVALID_BIND_ENV}" TEST_AGENT_BASE_DIR="${OUTPUT_DIR}/runtime" \
  "${INTERNAL_DIR}/lobehub-docker.sh" validate-config >/dev/null 2>&1; then
  echo "Wildcard LobeHub app binding was unexpectedly accepted" >&2
  exit 1
fi

DUPLICATE_ENV="${OUTPUT_DIR}/lobehub-duplicate.env"
cp "${VALID_ENV}" "${DUPLICATE_ENV}"
printf 'APP_URL=http://duplicate.invalid\n' >>"${DUPLICATE_ENV}"
chmod 0600 "${DUPLICATE_ENV}"
if LOBEHUB_ENV_FILE="${DUPLICATE_ENV}" TEST_AGENT_BASE_DIR="${OUTPUT_DIR}/runtime" \
  "${INTERNAL_DIR}/lobehub-docker.sh" validate-config >/dev/null 2>&1; then
  echo "Duplicate LobeHub dotenv key was unexpectedly accepted" >&2
  exit 1
fi

INVALID_QUEUE_ENV="${OUTPUT_DIR}/lobehub-invalid-queue.env"
sed 's/^AGENT_RUNTIME_MODE=local$/AGENT_RUNTIME_MODE=queue/' "${VALID_ENV}" >"${INVALID_QUEUE_ENV}"
chmod 0600 "${INVALID_QUEUE_ENV}"
if LOBEHUB_ENV_FILE="${INVALID_QUEUE_ENV}" TEST_AGENT_BASE_DIR="${OUTPUT_DIR}/runtime" \
  "${INTERNAL_DIR}/lobehub-docker.sh" validate-config >/dev/null 2>&1; then
  echo "QStash queue runtime was unexpectedly accepted for enterprise offline deployment" >&2
  exit 1
fi

INVALID_DIGEST_REF_ENV="${OUTPUT_DIR}/lobehub-invalid-digest-ref.env"
sed 's#^LOBEHUB_APP_IMAGE=.*#LOBEHUB_APP_IMAGE=test-agent/lobehub@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa#' \
  "${VALID_ENV}" >"${INVALID_DIGEST_REF_ENV}"
chmod 0600 "${INVALID_DIGEST_REF_ENV}"
if LOBEHUB_ENV_FILE="${INVALID_DIGEST_REF_ENV}" TEST_AGENT_BASE_DIR="${OUTPUT_DIR}/runtime" \
  "${INTERNAL_DIR}/lobehub-docker.sh" validate-config >/dev/null 2>&1; then
  echo "Registry digest reference was unexpectedly accepted for docker-load media" >&2
  exit 1
fi

# 预检必须校验同一个实际目标，不能只分别验证每个 URL 的格式。
assert_cross_wired_env_rejected() {
  local name="$1" expression="$2" replacement="$3" description="$4" invalid_env
  invalid_env="${OUTPUT_DIR}/lobehub-cross-wired-${name}.env"
  sed "s#${expression}#${replacement}#" "${VALID_ENV}" >"${invalid_env}"
  chmod 0600 "${invalid_env}"
  if LOBEHUB_ENV_FILE="${invalid_env}" TEST_AGENT_BASE_DIR="${OUTPUT_DIR}/runtime" \
    "${INTERNAL_DIR}/lobehub-docker.sh" validate-config >/dev/null 2>&1; then
    echo "${description} was unexpectedly accepted" >&2
    exit 1
  fi
}

assert_cross_wired_env_rejected database \
  '^DATABASE_URL=.*$' \
  'DATABASE_URL=postgresql://lobehub:database-password-32-bytes-minimum@other-db:5432/lobehub' \
  'Cross-wired LobeHub database URL'
assert_cross_wired_env_rejected database-credential \
  '^DATABASE_URL=.*$' \
  'DATABASE_URL=postgresql://lobehub:wrong-password@test-agent-lobehub-db:5432/lobehub' \
  'Cross-wired LobeHub database credential'
assert_cross_wired_env_rejected redis \
  '^REDIS_URL=.*$' \
  'REDIS_URL=redis://lobehub:redis-password-32-bytes-minimum@other-redis.internal:6379/0' \
  'Cross-wired LobeHub Redis URL'
assert_cross_wired_env_rejected redis-credential \
  '^REDIS_URL=.*$' \
  'REDIS_URL=redis://lobehub:wrong-password@redis.internal:6379/0' \
  'Cross-wired LobeHub Redis credential'
assert_cross_wired_env_rejected s3-bucket \
  '^S3_BUCKET=.*$' \
  'S3_BUCKET=other-bucket' \
  'Cross-wired LobeHub S3 bucket'
assert_cross_wired_env_rejected internal-app \
  '^INTERNAL_APP_URL=.*$' \
  'INTERNAL_APP_URL=http://other-app:3210' \
  'Cross-wired LobeHub internal app URL'
assert_cross_wired_env_rejected mc-credential \
  '^MC_HOST_lobehub=.*$' \
  'MC_HOST_lobehub=http://wrong-access:wrong-secret@test-agent-lobehub-rustfs:9000' \
  'Cross-wired LobeHub MC credential'
assert_cross_wired_env_rejected platform-origin \
  '^PLATFORM_MODEL_GATEWAY_BASE_URL=.*$' \
  'PLATFORM_MODEL_GATEWAY_BASE_URL=http://other-platform.internal/api/internal/platform/model-gateway/v1' \
  'Cross-wired platform model gateway origin'

grep -v 'LICENSES.txt$' "${FIXTURE_DIR}/SHA256SUMS" >"${FIXTURE_DIR}/SHA256SUMS.incomplete"
mv "${FIXTURE_DIR}/SHA256SUMS.incomplete" "${FIXTURE_DIR}/SHA256SUMS"
if TEST_AGENT_LOBEHUB_ARTIFACT_DIR="${FIXTURE_DIR}" \
  "${INTERNAL_DIR}/package-release.sh" --env-file /dev/null --lobehub-only --no-zip \
  --output-dir "${OUTPUT_DIR}" >/dev/null 2>&1; then
  echo "Incomplete LobeHub checksum coverage was unexpectedly accepted" >&2
  exit 1
fi

(
  cd "${FIXTURE_DIR}"
  if command -v sha256sum >/dev/null 2>&1; then
    find . -type f ! -name SHA256SUMS | LC_ALL=C sort | while IFS= read -r file; do
      sha256sum "${file#./}"
    done >SHA256SUMS
  else
    find . -type f ! -name SHA256SUMS | LC_ALL=C sort | while IFS= read -r file; do
      shasum -a 256 "${file#./}"
    done >SHA256SUMS
  fi
)

printf 'tampered\n' >"${FIXTURE_DIR}/approved-resources.json"
if TEST_AGENT_LOBEHUB_ARTIFACT_DIR="${FIXTURE_DIR}" \
  "${INTERNAL_DIR}/package-release.sh" --env-file /dev/null --lobehub-only --no-zip \
  --output-dir "${OUTPUT_DIR}" >/dev/null 2>&1; then
  echo "Tampered LobeHub artifact set was unexpectedly accepted" >&2
  exit 1
fi

echo "LobeHub package contract test passed"
