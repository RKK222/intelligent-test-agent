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
  source/lobehub-v2.2.11-platform.1.tar.gz \
  approved-resources.json LICENSES.txt windows-authenticode-verification.txt; do
  printf 'fixture:%s\n' "${file}" >"${FIXTURE_DIR}/${file}"
done
cat >"${FIXTURE_DIR}/release.env" <<'EOF'
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.1
LOBEHUB_UPSTREAM_VERSION=v2.2.11
LOBEHUB_UPSTREAM_COMMIT=5b4cef6
LOBEHUB_FORK_COMMIT=7d16863c88b8acbacda6d9ee15df0840749e0aaa
LOBEHUB_PLATFORM_CONTRACT_VERSION=1
LOBEHUB_PARADEDB_POSTGRES_MAJOR=17
LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=true
LOBEHUB_LINUX_EXECUTION_DEFAULT=false
LOBEHUB_APP_IMAGE=test-agent/lobehub:v2.2.11-platform.1
LOBEHUB_APP_IMAGE_ID=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
LOBEHUB_PARADEDB_IMAGE=test-agent/paradedb:pg17-v2.2.11-platform.1
LOBEHUB_PARADEDB_IMAGE_ID=sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
LOBEHUB_RUSTFS_IMAGE=test-agent/rustfs:v2.2.11-platform.1
LOBEHUB_RUSTFS_IMAGE_ID=sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
EOF

if command -v sha256sum >/dev/null 2>&1; then
  WINDOWS_SHA="$(sha256sum "${FIXTURE_DIR}/clients/lobehub-windows-x64.exe" | awk '{print $1}')"
else
  WINDOWS_SHA="$(shasum -a 256 "${FIXTURE_DIR}/clients/lobehub-windows-x64.exe" | awk '{print $1}')"
fi
cat >"${FIXTURE_DIR}/windows-authenticode-verification.txt" <<EOF
AUTHENTICODE_STATUS=Valid
AUTHENTICODE_SUBJECT=CN=TestAgent Fixture
AUTHENTICODE_THUMBPRINT=0123456789ABCDEF
AUTHENTICODE_FILE_SHA256=${WINDOWS_SHA}
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
  | grep -Fx 'dist/lobehub/source/lobehub-v2.2.11-platform.1.tar.gz' >/dev/null
unzip -Z1 "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" \
  | grep -Fx 'docs/architecture/lobehub-integration.md' >/dev/null
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
unzip -p "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" deploy/internal/nginx/lobehub.conf.template \
  | grep -F '__LOBEHUB_UPSTREAM__' >/dev/null
if unzip -p "${OUTPUT_DIR}/test-agent-lobehub-offline.zip" deploy/internal/lobehub-docker.sh \
  | grep -F 'pnpm db:migrate' >/dev/null; then
  echo "LobeHub release still uses pnpm inside the scratch runtime image" >&2
  exit 1
fi

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
LOBEHUB_APP_IMAGE=test-agent/lobehub:v2.2.11-platform.1
LOBEHUB_PARADEDB_IMAGE=test-agent/paradedb:pg17-v2.2.11-platform.1
LOBEHUB_RUSTFS_IMAGE=test-agent/rustfs:v2.2.11-platform.1
POSTGRES_DB=lobehub
POSTGRES_USER=lobehub
POSTGRES_PASSWORD=database-password-32-bytes-minimum
DATABASE_URL=postgresql://lobehub:database-password@test-agent-lobehub-db:5432/lobehub
DATABASE_DRIVER=node
LOBEHUB_REDIS_HOST=redis.internal
LOBEHUB_REDIS_PORT=6379
LOBEHUB_REDIS_USERNAME=lobehub
LOBEHUB_REDIS_PASSWORD=redis-password-32-bytes-minimum
REDIS_URL=redis://lobehub:redis-password@redis.internal:6379/0
REDIS_PREFIX=lobehub:app:
RUSTFS_ACCESS_KEY=rustfs-access-key-32-bytes
RUSTFS_SECRET_KEY=rustfs-secret-key-at-least-32-bytes
LOBEHUB_S3_BUCKET=lobehub-private
S3_ENDPOINT=http://test-agent-lobehub-rustfs:9000
S3_BUCKET=lobehub-private
S3_ACCESS_KEY_ID=rustfs-access-key-32-bytes
S3_SECRET_ACCESS_KEY=rustfs-secret-key-at-least-32-bytes
S3_ENABLE_PATH_STYLE=1
S3_SET_ACL=0
MC_HOST_lobehub=http://access:secret@test-agent-lobehub-rustfs:9000
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
