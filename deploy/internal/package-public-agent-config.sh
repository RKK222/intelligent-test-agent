#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=archive-common.sh
source "${SCRIPT_DIR}/archive-common.sh"

SOURCE_DIR="${ROOT_DIR}/public-agent-config"
OUTPUT_DIR="${SCRIPT_DIR}/dist"

usage() {
  cat <<'USAGE'
Usage: deploy/internal/package-public-agent-config.sh [--output-dir <path>]

Package the committed public Agent/Skill/Tool baseline from
public-agent-config/ into test-agent-public-agents-skills.zip.

The source directory must be clean and fully tracked by the current Git HEAD.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    printf 'Required command not found: %s\n' "$1" >&2
    exit 1
  }
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

require_command git
require_command find
require_command unzip
require_command zip

[[ -d "${SOURCE_DIR}/opencode/agents" ]] || {
  echo "Missing public Agent source directory: ${SOURCE_DIR}/opencode/agents" >&2
  exit 1
}
[[ -d "${SOURCE_DIR}/opencode/skills" ]] || {
  echo "Missing public Skill source directory: ${SOURCE_DIR}/opencode/skills" >&2
  exit 1
}
[[ -d "${SOURCE_DIR}/opencode/tools" ]] || {
  echo "Missing public Tool source directory: ${SOURCE_DIR}/opencode/tools" >&2
  exit 1
}

# 能力包只能从当前提交中的可审计文件生成，避免把未评审改动或临时文件混入交付物。
if ! git -C "${ROOT_DIR}" diff --quiet -- public-agent-config \
  || ! git -C "${ROOT_DIR}" diff --cached --quiet -- public-agent-config; then
  echo "public-agent-config contains uncommitted changes; commit them before packaging" >&2
  exit 1
fi
UNTRACKED_SOURCE="$(git -C "${ROOT_DIR}" ls-files --others --exclude-standard -- public-agent-config)"
if [[ -n "${UNTRACKED_SOURCE}" ]]; then
  echo "public-agent-config contains untracked files; commit them before packaging" >&2
  printf '%s\n' "${UNTRACKED_SOURCE}" >&2
  exit 1
fi
while IFS= read -r -d '' source_file; do
  source_file_relative="${source_file#${ROOT_DIR}/}"
  if ! git -C "${ROOT_DIR}" ls-files --error-unmatch -- "${source_file_relative}" >/dev/null 2>&1; then
    echo "public-agent-config contains a file not tracked by Git: ${source_file_relative}" >&2
    exit 1
  fi
done < <(find "${SOURCE_DIR}" -type f -print0)
SOURCE_COMMIT="$(git -C "${ROOT_DIR}" rev-parse HEAD)"
[[ -n "$(git -C "${ROOT_DIR}" ls-tree -r --name-only "${SOURCE_COMMIT}" -- public-agent-config)" ]] || {
  echo "Current Git HEAD does not contain public-agent-config" >&2
  exit 1
}

# 运行时配置、凭据和生成缓存不属于同仓公共能力基线。
FORBIDDEN_PATH="$(find "${SOURCE_DIR}" \
  \( -name .git -o -name node_modules -o -name opencode.jsonc \
     -o -name database.ini -o -name .encryption.key \
     -o -name __pycache__ -o -name .pytest_cache -o -name .DS_Store \) \
  -print -quit)"
if [[ -n "${FORBIDDEN_PATH}" ]]; then
  echo "Forbidden runtime or secret-bearing path in public Agent source: ${FORBIDDEN_PATH}" >&2
  exit 1
fi
SYMLINK_PATH="$(find "${SOURCE_DIR}" -type l -print -quit)"
if [[ -n "${SYMLINK_PATH}" ]]; then
  echo "Symlinks are not allowed in the public Agent source: ${SYMLINK_PATH}" >&2
  exit 1
fi

while IFS= read -r skill_dir; do
  [[ -f "${skill_dir}/SKILL.md" ]] || {
    echo "Skill directory lacks SKILL.md: ${skill_dir}" >&2
    exit 1
  }
done < <(find "${SOURCE_DIR}/opencode/skills" -mindepth 1 -maxdepth 1 -type d | LC_ALL=C sort)

(
  cd "${SOURCE_DIR}"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum -c artifacts.sha256
  else
    require_command shasum
    shasum -a 256 -c artifacts.sha256
  fi
)

case "${OUTPUT_DIR}" in
  ''|'/'|"${HOME}"|"${ROOT_DIR}"|"${SCRIPT_DIR}")
    echo "Unsafe output directory: ${OUTPUT_DIR}" >&2
    exit 2
    ;;
esac
mkdir -p "${OUTPUT_DIR}"
OUTPUT_DIR="$(cd "${OUTPUT_DIR}" && pwd -P)"
OUTPUT_ZIP="${OUTPUT_DIR}/test-agent-public-agents-skills.zip"
OUTPUT_SHA="${OUTPUT_ZIP}.sha256"

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-public-config.XXXXXX")"
cleanup() {
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT
STAGING_DIR="${WORK_DIR}/stage"
mkdir -p "${STAGING_DIR}"
cp -R "${SOURCE_DIR}/." "${STAGING_DIR}/"
printf '%s\n' "${SOURCE_COMMIT}" >"${STAGING_DIR}/SOURCE-COMMIT"

# 固定归档内时间，避免只因本机文件时间不同而改变能力包摘要。
find "${STAGING_DIR}" -exec touch -h -t 198001010000.00 {} +
rm -f "${OUTPUT_ZIP}" "${OUTPUT_SHA}"
archive_create_zip "${OUTPUT_ZIP}" "${STAGING_DIR}" README.md SOURCE-COMMIT artifacts.sha256 opencode
unzip -tq "${OUTPUT_ZIP}" >/dev/null

ZIP_LISTING="$(unzip -Z1 "${OUTPUT_ZIP}")"
if grep -Eq '(^|/)(\.git|node_modules|opencode\.jsonc|database\.ini|\.encryption\.key|__pycache__|\.pytest_cache)(/|$)' \
  <<<"${ZIP_LISTING}"; then
  echo "Generated ZIP contains a forbidden path" >&2
  exit 1
fi
printf '%s  %s\n' "$(sha256_file "${OUTPUT_ZIP}")" "$(basename "${OUTPUT_ZIP}")" >"${OUTPUT_SHA}"
chmod 0644 "${OUTPUT_ZIP}" "${OUTPUT_SHA}"

printf 'Public Agent package: %s\n' "${OUTPUT_ZIP}"
printf 'Source commit: %s\n' "${SOURCE_COMMIT}"
printf 'SHA-256: %s\n' "$(sha256_file "${OUTPUT_ZIP}")"
