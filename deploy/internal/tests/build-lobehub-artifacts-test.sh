#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTERNAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ROOT_DIR="$(cd "${INTERNAL_DIR}/../.." && pwd)"
BUILDER="${INTERNAL_DIR}/build-lobehub-artifacts.sh"
FIXTURE_DIR="$(mktemp -d)"
trap 'rm -rf "${FIXTURE_DIR}"' EXIT

"${BUILDER}" --help | grep -F -- '--validate-only' >/dev/null

if "${BUILDER}" --validate-only >/dev/null 2>&1; then
  echo "LobeHub artifact builder accepted missing release inputs" >&2
  exit 1
fi

grep -F 'pnpm install --frozen-lockfile' "${ROOT_DIR}/../lobehub-platform/Dockerfile" >/dev/null
grep -F 'ARG NODE_BASE_IMAGE=' "${ROOT_DIR}/../lobehub-platform/Dockerfile" >/dev/null
grep -F 'ARG BUSYBOX_BASE_IMAGE=' "${ROOT_DIR}/../lobehub-platform/Dockerfile" >/dev/null
grep -F 'ARG LOBEHUB_FORK_COMMIT' "${ROOT_DIR}/../lobehub-platform/Dockerfile" >/dev/null
grep -F '# syntax=docker/dockerfile:1.7@sha256:' \
  "${ROOT_DIR}/../lobehub-platform/Dockerfile" >/dev/null
grep -F 'id=lobehub-pnpm-store' "${ROOT_DIR}/../lobehub-platform/Dockerfile" >/dev/null
grep -F 'npm_config_fetch_retries=5' "${ROOT_DIR}/../lobehub-platform/Dockerfile" >/dev/null
grep -F 'org.opencontainers.image.revision="${LOBEHUB_FORK_COMMIT}"' \
  "${ROOT_DIR}/../lobehub-platform/Dockerfile" >/dev/null
grep -F 'NEXT_TELEMETRY_DISABLED="1"' "${ROOT_DIR}/../lobehub-platform/Dockerfile" >/dev/null
grep -F 'LOBEHUB_ENTERPRISE_OFFLINE="1"' "${ROOT_DIR}/../lobehub-platform/Dockerfile" >/dev/null
grep -F 'ENTERPRISE_INTERNAL_SCHEDULER_SECRET' \
  "${ROOT_DIR}/../lobehub-platform/scripts/serverLauncher/startServer.js" >/dev/null
grep -F 'http://127.0.0.1:${port}/api/agent/enterprise/schedule-dispatch' \
  "${ROOT_DIR}/../lobehub-platform/scripts/serverLauncher/startServer.js" >/dev/null
grep -F "['/api/workflows', 'offline-workflow-endpoint']" \
  "${ROOT_DIR}/../lobehub-platform/apps/server/src/services/enterpriseOffline/policy.ts" >/dev/null
grep -F 'corepack pnpm@10.33.0 add --save-exact' \
  "${ROOT_DIR}/../lobehub-platform/Dockerfile" >/dev/null
grep -F 'corepack pnpm@10.33.0' "${BUILDER}" >/dev/null
grep -F 'LOBEHUB_FORK_COMMIT' "${ROOT_DIR}/deploy/internal/lobehub/version.env" >/dev/null
grep -F 'git -C "${FORK_DIR}" archive --format=tar "${FORK_COMMIT}"' "${BUILDER}" >/dev/null
grep -F 'corepack pnpm@10.33.0 install --frozen-lockfile' \
  "${ROOT_DIR}/tools/lobehub-dev-services.sh" >/dev/null
grep -F 'LOBEHUB_PARADEDB_IMAGE=test-agent/paradedb:pg17-v2.2.11-platform.3' \
  "${INTERNAL_DIR}/lobehub.env.example" >/dev/null
grep -F 'LOBEHUB_RUSTFS_IMAGE=test-agent/rustfs:v2.2.11-platform.3' \
  "${INTERNAL_DIR}/lobehub.env.example" >/dev/null
grep -F 'AGENT_RUNTIME_MODE=local' "${INTERNAL_DIR}/lobehub.env.example" >/dev/null

FORK_DIR="${TEST_AGENT_LOBEHUB_FORK_DIR:-${ROOT_DIR}/../lobehub-platform}"
if [[ -d "${FORK_DIR}/.git" ]]; then
  printf 'signed-windows-client\n' >"${FIXTURE_DIR}/lobehub-windows-x64.exe"
  printf 'linux-client\n' >"${FIXTURE_DIR}/lobehub-linux-x86_64.tar.gz"
  if command -v sha256sum >/dev/null 2>&1; then
    WINDOWS_SHA="$(sha256sum "${FIXTURE_DIR}/lobehub-windows-x64.exe" | awk '{print $1}')"
  else
    WINDOWS_SHA="$(shasum -a 256 "${FIXTURE_DIR}/lobehub-windows-x64.exe" | awk '{print $1}')"
  fi
  cat >"${FIXTURE_DIR}/windows-authenticode-verification.txt" <<EOF
AUTHENTICODE_STATUS=Valid
AUTHENTICODE_SUBJECT=CN=Enterprise Test
AUTHENTICODE_THUMBPRINT=0123456789ABCDEF
AUTHENTICODE_FILE_SHA256=${WINDOWS_SHA}
EOF

  "${BUILDER}" --validate-only --allow-dirty \
    --fork-dir "${FORK_DIR}" \
    --windows-client "${FIXTURE_DIR}/lobehub-windows-x64.exe" \
    --windows-signature-evidence "${FIXTURE_DIR}/windows-authenticode-verification.txt" \
    --linux-client "${FIXTURE_DIR}/lobehub-linux-x86_64.tar.gz" \
    --node-base-image "node@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" \
    --busybox-base-image "busybox@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" \
    --paradedb-source-image "paradedb/paradedb@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc" \
    --rustfs-source-image "rustfs/rustfs@sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd" \
    --mc-source-image "minio/mc@sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee" \
    | grep -Fx 'LobeHub artifact inputs passed' >/dev/null

  sed 's/^AUTHENTICODE_STATUS=Valid$/AUTHENTICODE_STATUS=Unknown/' \
    "${FIXTURE_DIR}/windows-authenticode-verification.txt" \
    >"${FIXTURE_DIR}/windows-authenticode-invalid.txt"
  if "${BUILDER}" --validate-only --allow-dirty \
    --fork-dir "${FORK_DIR}" \
    --windows-client "${FIXTURE_DIR}/lobehub-windows-x64.exe" \
    --windows-signature-evidence "${FIXTURE_DIR}/windows-authenticode-invalid.txt" \
    --linux-client "${FIXTURE_DIR}/lobehub-linux-x86_64.tar.gz" \
    --node-base-image "node@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" \
    --busybox-base-image "busybox@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" \
    --paradedb-source-image "paradedb/paradedb@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc" \
    --rustfs-source-image "rustfs/rustfs@sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd" \
    --mc-source-image "minio/mc@sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee" \
    >/dev/null 2>&1; then
    echo "Invalid Authenticode evidence was unexpectedly accepted" >&2
    exit 1
  fi
fi

echo "LobeHub artifact builder contract test passed"
