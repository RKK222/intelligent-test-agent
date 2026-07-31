#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
FORK_DIR="${TEST_AGENT_LOBEHUB_FORK_DIR:-${ROOT_DIR}/../lobehub-platform}"
VERSION_FILE="${SCRIPT_DIR}/lobehub/version.env"
OUTPUT_DIR="${ROOT_DIR}/lobehub-fork-transfer"
FORCE=0
STAGING_DIR=""
LOCK_DIR=""
LOCK_ACQUIRED=0
OUTPUT_EXISTED=0
OUTPUT_IDENTITY=""

usage() {
  cat <<'USAGE'
Usage: deploy/internal/build-lobehub-fork-transfer.sh [options]

Build a self-contained, checksum-protected Git bundle for importing the independent LobeHub fork
into an enterprise Git service without contacting GitHub. Only main and the locked internal release
tag are advertised by the bundle.

Options:
  --fork-dir <path>      Clean independent LobeHub fork checkout.
  --version-file <path>  Version lock (default: deploy/internal/lobehub/version.env).
  --output-dir <path>    New directory for the transfer ZIP and outer SHA-256.
  --force                Replace only the exact output directory after validation.
  -h, --help             Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --fork-dir) FORK_DIR="$2"; shift 2 ;;
    --version-file) VERSION_FILE="$2"; shift 2 ;;
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    --force) FORCE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

fail() {
  echo "LobeHub fork transfer build failed: $1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

require_regular_file() {
  [[ -f "$1" && ! -L "$1" && -s "$1" ]] || fail "required non-empty file not found: $1"
}

validate_state_file() {
  local path="$1"
  require_regular_file "${path}"
  awk '
    {
      sub(/\r$/, "", $0)
      if ($0 !~ /^[A-Z][A-Z0-9_]*=/) exit 1
      key = substr($0, 1, index($0, "=") - 1)
      if (seen[key]++) exit 1
    }
    END { if (NR == 0) exit 1 }
  ' "${path}" || fail "version lock is malformed: ${path}"
}

state_value() {
  local key="$1"
  awk -F= -v wanted="${key}" '
    $1 == wanted { print substr($0, index($0, "=") + 1); found++ }
    END { if (found != 1) exit 1 }
  ' "${VERSION_FILE}" || fail "version lock must contain ${key} exactly once"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    fail 'neither sha256sum nor shasum is available'
  fi
}

path_identity() {
  if stat -c '%d:%i:%F' "$1" >/dev/null 2>&1; then
    stat -c '%d:%i:%F' "$1"
  else
    stat -f '%d:%i:%HT' "$1"
  fi
}

scan_fork_delta_for_credentials() {
  local oid object_type
  # 只使用高置信格式，避免把示例中的普通 password/token 字样误判为真实凭据；命中时不输出原文或路径。
  local credential_pattern='-----BEGIN ((RSA|EC|DSA|OPENSSH|ENCRYPTED) )?PRIVATE KEY-----|-----BEGIN PGP PRIVATE KEY BLOCK-----|PuTTY-User-Key-File-[23]:|A(KIA|SIA)[0-9A-Z]{16}|github_pat_[A-Za-z0-9_]{40,}|gh[pousr]_[A-Za-z0-9]{36,}|glpat-[A-Za-z0-9_-]{20,}|npm_[A-Za-z0-9]{36,}|xox[baprs]-[A-Za-z0-9-]{20,}|AIza[0-9A-Za-z_-]{35}|sk-ant-[A-Za-z0-9_-]{20,}|sk-proj-[A-Za-z0-9_-]{20,}|sk_live_[A-Za-z0-9]{24,}'
  while IFS=' ' read -r oid _; do
    [[ "${oid}" =~ ^[0-9a-f]{40,64}$ ]] || fail 'fork history returned a malformed object ID'
    object_type="$(git -C "${FORK_DIR}" cat-file -t "${oid}")"
    case "${object_type}" in
      blob|commit|tag)
        if LC_ALL=C grep -aE -q -- "${credential_pattern}" \
          < <(git -C "${FORK_DIR}" cat-file "${object_type}" "${oid}"); then
          fail "high-confidence credential marker found in fork-only Git history object ${oid}; rotate the credential and rewrite the fork history before export"
        fi
        ;;
      tree) ;;
      *) fail "unexpected Git object type in fork history: ${object_type}" ;;
    esac
  done < <(git -C "${FORK_DIR}" rev-list --objects "${UPSTREAM_COMMIT}..${HEAD_COMMIT}")

  # annotated tag 本身不在 commit range 中，tag message 也必须通过相同门禁。
  if LC_ALL=C grep -aE -q -- "${credential_pattern}" \
    < <(git -C "${FORK_DIR}" cat-file tag "${TAG_OBJECT}"); then
    fail 'high-confidence credential marker found in the annotated release tag; rotate it and recreate the tag before export'
  fi
}

for command_name in git node zip awk find grep mktemp stat; do
  require_command "${command_name}"
done
validate_state_file "${VERSION_FILE}"
VERSION_FILE="$(cd "$(dirname "${VERSION_FILE}")" && pwd -P)/$(basename "${VERSION_FILE}")"
[[ -d "${FORK_DIR}/.git" && ! -L "${FORK_DIR}" ]] ||
  fail "independent fork checkout not found: ${FORK_DIR}"
FORK_DIR="$(cd "${FORK_DIR}" && pwd -P)"
require_regular_file "${FORK_DIR}/package.json"

INTERNAL_VERSION="$(state_value LOBEHUB_INTERNAL_VERSION)"
UPSTREAM_VERSION="$(state_value LOBEHUB_UPSTREAM_VERSION)"
UPSTREAM_COMMIT="$(state_value LOBEHUB_UPSTREAM_COMMIT)"
LOCKED_FORK_COMMIT="$(state_value LOBEHUB_FORK_COMMIT)"
[[ "${INTERNAL_VERSION}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+-platform\.[0-9]+$ \
  && "${UPSTREAM_VERSION}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ \
  && "${UPSTREAM_COMMIT}" =~ ^[0-9a-f]{7}([0-9a-f]{33})?$ \
  && "${LOCKED_FORK_COMMIT}" =~ ^[0-9a-f]{40}$ ]] || fail 'version lock contains malformed release identities'

CURRENT_BRANCH="$(git -C "${FORK_DIR}" branch --show-current)"
[[ "${CURRENT_BRANCH}" == main ]] || fail "fork release branch must be main, got ${CURRENT_BRANCH:-detached}"
HEAD_COMMIT="$(git -C "${FORK_DIR}" rev-parse HEAD)"
[[ "${HEAD_COMMIT}" == "${LOCKED_FORK_COMMIT}" ]] ||
  fail "fork HEAD must equal locked commit ${LOCKED_FORK_COMMIT}, got ${HEAD_COMMIT}"
[[ -z "$(git -C "${FORK_DIR}" status --porcelain)" ]] || fail 'fork worktree must be clean'
PACKAGE_VERSION="$(node -p "require(process.argv[1]).version" "${FORK_DIR}/package.json")"
[[ "v${PACKAGE_VERSION}" == "${INTERNAL_VERSION}" ]] ||
  fail "fork package version must be ${INTERNAL_VERSION}, got v${PACKAGE_VERSION}"
git -C "${FORK_DIR}" cat-file -e "${UPSTREAM_COMMIT}^{commit}" 2>/dev/null ||
  fail "upstream commit is not present in the fork: ${UPSTREAM_COMMIT}"
git -C "${FORK_DIR}" merge-base --is-ancestor "${UPSTREAM_COMMIT}" "${HEAD_COMMIT}" ||
  fail "fork commit does not descend from ${UPSTREAM_COMMIT}"

TAG_REF="refs/tags/${INTERNAL_VERSION}"
BRANCH_REF="refs/heads/main"
git -C "${FORK_DIR}" show-ref --verify --quiet "${TAG_REF}" || fail "release tag is missing: ${TAG_REF}"
TAG_TYPE="$(git -C "${FORK_DIR}" cat-file -t "${TAG_REF}")"
[[ "${TAG_TYPE}" == tag ]] || fail "release tag must be annotated: ${TAG_REF}"
TAG_OBJECT="$(git -C "${FORK_DIR}" rev-parse "${TAG_REF}")"
TAG_COMMIT="$(git -C "${FORK_DIR}" rev-parse "${TAG_REF}^{}")"
[[ "${TAG_COMMIT}" == "${LOCKED_FORK_COMMIT}" ]] ||
  fail "release tag does not resolve to locked commit ${LOCKED_FORK_COMMIT}"
[[ "$(git -C "${FORK_DIR}" rev-parse "${BRANCH_REF}")" == "${LOCKED_FORK_COMMIT}" ]] ||
  fail 'main branch does not resolve to the locked fork commit'
scan_fork_delta_for_credentials

OUTPUT_BASENAME="$(basename "${OUTPUT_DIR}")"
[[ -n "${OUTPUT_BASENAME}" && "${OUTPUT_BASENAME}" != . && "${OUTPUT_BASENAME}" != .. ]] ||
  fail "unsafe output directory: ${OUTPUT_DIR}"
OUTPUT_PARENT="$(dirname "${OUTPUT_DIR}")"
[[ -d "${OUTPUT_PARENT}" && ! -L "${OUTPUT_PARENT}" ]] ||
  fail "output parent must already be a real directory: ${OUTPUT_PARENT}"
OUTPUT_PARENT="$(cd "${OUTPUT_PARENT}" && pwd -P)"
OUTPUT_DIR="${OUTPUT_PARENT}/${OUTPUT_BASENAME}"
case "${OUTPUT_DIR}" in
  /|"${HOME}"|"${ROOT_DIR}"|"${SCRIPT_DIR}"|"${FORK_DIR}")
    fail "unsafe output directory: ${OUTPUT_DIR}" ;;
esac
case "${OUTPUT_DIR}" in
  "${FORK_DIR}"/*) fail 'output directory must not be inside the fork checkout' ;;
esac
case "${FORK_DIR}" in
  "${OUTPUT_DIR}"/*) fail 'output directory must not contain the fork checkout' ;;
esac
case "${VERSION_FILE}" in
  "${OUTPUT_DIR}"|"${OUTPUT_DIR}"/*)
    fail 'output directory must not equal or contain the version lock' ;;
esac
case "${ROOT_DIR}" in
  "${OUTPUT_DIR}"/*) fail 'output directory must not contain the platform repository' ;;
esac
case "${SCRIPT_DIR}" in
  "${OUTPUT_DIR}"/*) fail 'output directory must not contain the deployment scripts' ;;
esac
[[ ! -L "${OUTPUT_DIR}" ]] || fail 'output directory must not be a symbolic link'
if [[ -e "${OUTPUT_DIR}" && "${FORCE}" -ne 1 ]]; then
  fail "output already exists; use --force for this exact directory: ${OUTPUT_DIR}"
fi
if [[ -e "${OUTPUT_DIR}" ]]; then
  [[ -d "${OUTPUT_DIR}" && ! -L "${OUTPUT_DIR}" ]] ||
    fail 'existing output must be a real directory'
  OUTPUT_EXISTED=1
  OUTPUT_IDENTITY="$(path_identity "${OUTPUT_DIR}")"
fi

TRANSFER_NAME="lobehub-fork-transfer-${INTERNAL_VERSION}"
BUNDLE_NAME="lobehub-platform-${INTERNAL_VERSION}.bundle"
ZIP_NAME="${TRANSFER_NAME}.zip"
LOCK_DIR="${OUTPUT_PARENT}/.${OUTPUT_BASENAME}.lobehub-fork-transfer.lock"
mkdir "${LOCK_DIR}" 2>/dev/null ||
  fail "another transfer builder is already publishing this output: ${OUTPUT_DIR}"
LOCK_ACQUIRED=1
STAGING_DIR="$(mktemp -d "${OUTPUT_PARENT}/.lobehub-fork-transfer.XXXXXX")"
cleanup() {
  [[ -n "${STAGING_DIR}" && -d "${STAGING_DIR}" ]] && rm -rf "${STAGING_DIR}"
  if [[ "${LOCK_ACQUIRED}" -eq 1 && -d "${LOCK_DIR}" && ! -L "${LOCK_DIR}" ]]; then
    rmdir "${LOCK_DIR}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
CONTENT_DIR="${STAGING_DIR}/${TRANSFER_NAME}"
PUBLISH_DIR="${STAGING_DIR}/publish"
CLONE_DIR="${STAGING_DIR}/verification-clone"
mkdir -p "${CONTENT_DIR}" "${PUBLISH_DIR}"
BUNDLE_PATH="${CONTENT_DIR}/${BUNDLE_NAME}"

# 显式传入两个发布 ref，避免把上游数百个 tag 作为可见引用带入企业 Git。
git -C "${FORK_DIR}" bundle create "${BUNDLE_PATH}" "${BRANCH_REF}" "${TAG_REF}"
git -C "${FORK_DIR}" bundle verify "${BUNDLE_PATH}" >/dev/null
BUNDLE_HEADS="$(git bundle list-heads "${BUNDLE_PATH}")"
[[ "$(printf '%s\n' "${BUNDLE_HEADS}" | wc -l | tr -d ' ')" -eq 2 ]] ||
  fail 'bundle must advertise exactly main and the internal release tag'
printf '%s\n' "${BUNDLE_HEADS}" | grep -Fx "${LOCKED_FORK_COMMIT} ${BRANCH_REF}" >/dev/null ||
  fail 'bundle main ref does not match the locked fork commit'
printf '%s\n' "${BUNDLE_HEADS}" | grep -Fx "${TAG_OBJECT} ${TAG_REF}" >/dev/null ||
  fail 'bundle release tag ref does not match the annotated tag object'

# 真正从 bundle 创建一次新仓库，证明转运介质不依赖原 checkout 或公网 remote。
git clone -q "${BUNDLE_PATH}" "${CLONE_DIR}"
[[ "$(git -C "${CLONE_DIR}" branch --show-current)" == main \
  && "$(git -C "${CLONE_DIR}" rev-parse HEAD)" == "${LOCKED_FORK_COMMIT}" \
  && "$(git -C "${CLONE_DIR}" rev-parse "${TAG_REF}^{}")" == "${LOCKED_FORK_COMMIT}" ]] ||
  fail 'standalone bundle clone verification failed'
rm -rf "${CLONE_DIR}"

cat >"${CONTENT_DIR}/refs.txt" <<EOF
LOBEHUB_INTERNAL_VERSION=${INTERNAL_VERSION}
LOBEHUB_UPSTREAM_VERSION=${UPSTREAM_VERSION}
LOBEHUB_UPSTREAM_COMMIT=${UPSTREAM_COMMIT}
LOBEHUB_FORK_COMMIT=${LOCKED_FORK_COMMIT}
BRANCH_REF=${BRANCH_REF}
BRANCH_COMMIT=${LOCKED_FORK_COMMIT}
TAG_REF=${TAG_REF}
TAG_OBJECT=${TAG_OBJECT}
TAG_COMMIT=${TAG_COMMIT}
FORK_DELTA_CREDENTIAL_SCAN=Passed
FORK_DELTA_CREDENTIAL_SCAN_BASE=${UPSTREAM_COMMIT}
EOF

cat >"${CONTENT_DIR}/IMPORT.md" <<EOF
# LobeHub 独立 fork 企业 Git 导入

本包由锁定的 \`${INTERNAL_VERSION}\` 独立 fork 生成，Git Bundle 自包含完整可达历史，导入过程不访问
GitHub。Bundle 只发布 \`${BRANCH_REF}\` 与 \`${TAG_REF}\` 两个 ref；导入前须先校验外层和包内
\`SHA256SUMS\`，再核对 \`refs.txt\`。生成器已对 fork 相对 \`${UPSTREAM_COMMIT}\` 新增的全部可达
blob/commit/tag 及当前 annotated tag 执行高置信私钥/token 格式扫描；该门禁不能替代企业源码平台的持续
secret scanning，也不能证明上游公开历史不存在测试凭据。

\`\`\`bash
git clone ${BUNDLE_NAME} lobehub-platform
cd lobehub-platform
git switch main
git remote rename origin transfer
git remote add origin <enterprise-git-url>
git push --set-upstream origin main
git push origin ${TAG_REF}
git ls-remote origin ${BRANCH_REF} ${TAG_REF} ${TAG_REF}^{}
\`\`\`

企业 Git 管理员必须确认远端 main 与 tag 解引用后的 commit 都是
\`${LOCKED_FORK_COMMIT}\`。\`<enterprise-git-url>\` 由现场管理员提供，禁止在转运包中预置凭据。
EOF

(
  cd "${CONTENT_DIR}"
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

(
  cd "${STAGING_DIR}"
  COPYFILE_DISABLE=1 zip -X -q -r "${PUBLISH_DIR}/${ZIP_NAME}" "${TRANSFER_NAME}"
)
ZIP_SHA="$(sha256_file "${PUBLISH_DIR}/${ZIP_NAME}")"
printf '%s  %s\n' "${ZIP_SHA}" "${ZIP_NAME}" >"${PUBLISH_DIR}/${ZIP_NAME}.sha256"
rm -rf "${CONTENT_DIR}"

if [[ "${OUTPUT_EXISTED}" -eq 1 ]]; then
  [[ -d "${OUTPUT_DIR}" && ! -L "${OUTPUT_DIR}" \
    && "$(path_identity "${OUTPUT_DIR}")" == "${OUTPUT_IDENTITY}" ]] ||
    fail 'forced output object changed while transfer media was being built'
  rm -rf "${OUTPUT_DIR}"
else
  [[ ! -e "${OUTPUT_DIR}" && ! -L "${OUTPUT_DIR}" ]] ||
    fail 'output appeared while transfer media was being built'
fi
mv "${PUBLISH_DIR}" "${OUTPUT_DIR}"
rm -rf "${STAGING_DIR}"
STAGING_DIR=""
if [[ "${LOCK_ACQUIRED}" -eq 1 ]]; then
  rmdir "${LOCK_DIR}"
  LOCK_ACQUIRED=0
fi
trap - EXIT

echo "LobeHub fork transfer media built at ${OUTPUT_DIR}/${ZIP_NAME}."
echo "SHA-256: ${ZIP_SHA}"
