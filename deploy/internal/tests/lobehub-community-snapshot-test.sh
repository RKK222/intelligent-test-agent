#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTERNAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ROOT_DIR="$(cd "${INTERNAL_DIR}/../.." && pwd)"
VERIFIER="${INTERNAL_DIR}/verify-lobehub-community-snapshot.mjs"
FORK_DIR="${TEST_AGENT_LOBEHUB_FORK_DIR:-${ROOT_DIR}/../lobehub-platform}"
FIXTURE_DIR="$(mktemp -d)"
trap 'rm -rf "${FIXTURE_DIR}"' EXIT

metadata="$(node "${VERIFIER}" "${FORK_DIR}")"
printf '%s\n' "${metadata}" | grep -E '^COMMUNITY_AGENT_COUNT=[1-9][0-9]*$' >/dev/null
printf '%s\n' "${metadata}" | grep -E '^COMMUNITY_SNAPSHOT_SHA256=[0-9a-f]{64}$' >/dev/null
printf '%s\n' "${metadata}" | grep -E '^COMMUNITY_SELECTION_SHA256=[0-9a-f]{64}$' >/dev/null
printf '%s\n' "${metadata}" | grep -E '^COMMUNITY_ASSET_SET_SHA256=[0-9a-f]{64}$' >/dev/null
printf '%s\n' "${metadata}" | grep -Fx \
  'COMMUNITY_SOURCE_REPOSITORY=https://github.com/lobehub/lobe-chat-agents' >/dev/null
printf '%s\n' "${metadata}" | grep -Fx 'COMMUNITY_SOURCE_LICENSE=MIT' >/dev/null

mkdir -p "${FIXTURE_DIR}/src/services" "${FIXTURE_DIR}/scripts" \
  "${FIXTURE_DIR}/public/community-agent-snapshot"
cp "${FORK_DIR}/src/services/communityAgentSnapshot.snapshot.json" \
  "${FIXTURE_DIR}/src/services/"
cp "${FORK_DIR}/scripts/community-agent-snapshot.selection.json" "${FIXTURE_DIR}/scripts/"
cp -R "${FORK_DIR}/public/community-agent-snapshot/avatars" \
  "${FIXTURE_DIR}/public/community-agent-snapshot/"

first_avatar="$(find "${FIXTURE_DIR}/public/community-agent-snapshot/avatars" -type f | sort | head -1)"
printf 'tampered' >>"${first_avatar}"
if node "${VERIFIER}" "${FIXTURE_DIR}" >/dev/null 2>&1; then
  echo 'Community snapshot verifier accepted a tampered avatar' >&2
  exit 1
fi

echo 'LobeHub Community snapshot test passed'
