#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../deploy/internal/archive-common.sh
source "${ROOT_DIR}/deploy/internal/archive-common.sh"

IMAGE="${TEST_AGENT_OPENCODE_WORKER_IMAGE:-test-agent-opencode-worker:internal}"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-archive-hygiene.XXXXXX")"
cleanup() { rm -rf "${TMP_ROOT}"; }
trap cleanup EXIT

mkdir -p "${TMP_ROOT}/source/root/__MACOSX" "${TMP_ROOT}/source/root/nested"
printf 'keep\n' >"${TMP_ROOT}/source/root/.required-config"
printf 'drop\n' >"${TMP_ROOT}/source/root/._resource"
printf 'drop\n' >"${TMP_ROOT}/source/root/.DS_Store"
printf 'drop\n' >"${TMP_ROOT}/source/root/__MACOSX/metadata"
printf 'drop\n' >"${TMP_ROOT}/source/root/nested/._resource"
printf 'payload\n' >"${TMP_ROOT}/source/root/nested/file.txt"
if command -v xattr >/dev/null 2>&1; then
  xattr -w com.apple.provenance archive-hygiene "${TMP_ROOT}/source/root" 2>/dev/null || true
  xattr -w com.test.agent archive-hygiene "${TMP_ROOT}/source/root/nested/file.txt" 2>/dev/null || true
fi

archive_create_tar_gz "${TMP_ROOT}/clean.tar.gz" "${TMP_ROOT}/source" root
archive_create_zip "${TMP_ROOT}/clean.zip" "${TMP_ROOT}/source" root

docker image inspect "${IMAGE}" >/dev/null
tar_listing="${TMP_ROOT}/tar-listing.txt"
tar_stderr="${TMP_ROOT}/tar-stderr.txt"
docker run --rm --platform linux/amd64 --network none -i \
  --entrypoint tar "${IMAGE}" -tzf - \
  <"${TMP_ROOT}/clean.tar.gz" >"${tar_listing}" 2>"${tar_stderr}"

grep -Fxq 'root/.required-config' "${tar_listing}"
grep -Fxq 'root/nested/file.txt' "${tar_listing}"
if grep -Eq '(^|/)(\._[^/]*|\.DS_Store|__MACOSX|\.Spotlight-V100|\.Trashes|\.fseventsd)(/|$)' \
  "${tar_listing}"; then
  echo 'TAR retained forbidden macOS metadata' >&2
  exit 1
fi
if grep -Eqi 'LIBARCHIVE|extended header|xattr|AppleDouble' "${tar_stderr}"; then
  cat "${tar_stderr}" >&2
  echo 'TAR retained macOS extended metadata' >&2
  exit 1
fi

zip_listing="$(unzip -Z1 "${TMP_ROOT}/clean.zip")"
grep -Fxq 'root/.required-config' <<<"${zip_listing}"
grep -Fxq 'root/nested/file.txt' <<<"${zip_listing}"
if grep -Eq '(^|/)(\._[^/]*|\.DS_Store|__MACOSX|\.Spotlight-V100|\.Trashes|\.fseventsd)(/|$)' \
  <<<"${zip_listing}"; then
  echo 'ZIP retained forbidden macOS metadata' >&2
  exit 1
fi
if command -v xattr >/dev/null 2>&1; then
  [[ -z "$(xattr "${TMP_ROOT}/clean.tar.gz")" ]]
  [[ -z "$(xattr "${TMP_ROOT}/clean.zip")" ]]
fi

echo 'Enterprise TAR/ZIP macOS metadata exclusion verified with Linux GNU tar'
