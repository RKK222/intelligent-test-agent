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
LOBEHUB_PLATFORM_CONTRACT_VERSION=1
LOBEHUB_PARADEDB_POSTGRES_MAJOR=17
LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=true
LOBEHUB_LINUX_EXECUTION_DEFAULT=false
LOBEHUB_APP_IMAGE=registry.internal/lobehub@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
LOBEHUB_PARADEDB_IMAGE=registry.internal/paradedb@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
LOBEHUB_RUSTFS_IMAGE=registry.internal/rustfs@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
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
  | grep -F "anonymous set none" >/dev/null

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
LOBEHUB_APP_IMAGE=registry.internal/lobehub@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
LOBEHUB_PARADEDB_IMAGE=registry.internal/paradedb@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
LOBEHUB_RUSTFS_IMAGE=registry.internal/rustfs@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
POSTGRES_DB=lobehub
POSTGRES_USER=lobehub
POSTGRES_PASSWORD=database-password-32-bytes-minimum
DATABASE_URL=postgresql://lobehub:database-password@test-agent-lobehub-db:5432/lobehub
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
S3_SET_ACL=0
MC_HOST_lobehub=http://access:secret@test-agent-lobehub-rustfs:9000
APP_URL=http://chat.internal
NEXTAUTH_URL=http://chat.internal/api/auth
LOBEHUB_PLATFORM_BASE_URL=http://test-agent.internal
LOBEHUB_PLATFORM_LAUNCH_URL=http://test-agent.internal/lobehub/launch
LOBEHUB_PLATFORM_REDEEM_URL=http://test-agent.internal/api/internal/platform/lobehub-sso/tickets/redeem
LOBEHUB_PLATFORM_REVOKE_URL=http://test-agent.internal/api/internal/platform/lobehub-sso/grants/revoke
LOBEHUB_PLATFORM_HMAC_SECRET=hmac-secret-at-least-thirty-two-bytes
LOBEHUB_MODEL_GRANT_ENCRYPTION_KEY=grant-encryption-at-least-32-bytes
KEY_VAULTS_SECRET=key-vault-secret-at-least-32-bytes
NEXT_AUTH_SECRET=next-auth-secret-at-least-32-bytes
LOBEHUB_SESSION_MAX_AGE_SECONDS=86400
LOBEHUB_COOKIE_SECURE=false
LOBEHUB_COOKIE_SAME_SITE=lax
LOBEHUB_COOKIE_HOST_ONLY=true
LOBEHUB_ENTERPRISE_PROVIDER_ONLY=true
LOBEHUB_ENTERPRISE_MODEL_BASE_URL=http://test-agent.internal/api/internal/platform/model-gateway/v1
LOBEHUB_ENTERPRISE_MODEL_GRANT_STORAGE=server-encrypted
LOBEHUB_OFFLINE_MODE=true
LOBEHUB_DISABLE_PUBLIC_SEARCH=true
LOBEHUB_DISABLE_SAAS_CONNECTORS=true
LOBEHUB_DISABLE_BYOK=true
LOBEHUB_DISABLE_TELEMETRY=true
LOBEHUB_DISABLE_UPDATE_CHECK=true
LOBEHUB_DISABLE_RUNTIME_DOWNLOADS=true
LOBEHUB_DISABLE_LOCAL_REGISTRATION=true
LOBEHUB_DISABLE_PASSWORD_LOGIN=true
LOBEHUB_DISABLE_CUSTOM_IDENTITY_PROVIDERS=true
LOBEHUB_WINDOWS_EXECUTION_ENABLED=false
LOBEHUB_LINUX_EXECUTION_ENABLED=false
LOBEHUB_LINUX_SANDBOX_APPROVAL_FILE=unused-while-disabled
EOF
chmod 0600 "${VALID_ENV}"
LOBEHUB_ENV_FILE="${VALID_ENV}" TEST_AGENT_BASE_DIR="${OUTPUT_DIR}/runtime" \
  "${INTERNAL_DIR}/lobehub-docker.sh" validate-config \
  | grep -Fx 'LobeHub configuration contract passed' >/dev/null

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
