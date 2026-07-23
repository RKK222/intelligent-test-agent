#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_DIR=""
IF_PRESENT=0
RULES_FILE="${SCRIPT_DIR}/opencode-runtime.gitignore"

usage() {
  cat <<'USAGE'
Usage: ensure-opencode-runtime-gitignore.sh --config-dir <path> [--if-present]

Append the packaged OpenCode runtime ignore rules to an existing configuration
directory without replacing administrator-maintained rules.

Options:
  --config-dir <path>  Existing OpenCode configuration directory.
  --if-present         Succeed without creating the directory when it is absent.
  -h, --help           Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --config-dir)
      CONFIG_DIR="$2"
      shift 2
      ;;
    --if-present)
      IF_PRESENT=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${CONFIG_DIR}" || "${CONFIG_DIR}" != /* ]]; then
  echo "--config-dir must be an absolute path" >&2
  exit 2
fi
if [[ ! -f "${RULES_FILE}" ]]; then
  echo "OpenCode runtime Git ignore template not found: ${RULES_FILE}" >&2
  exit 1
fi
if [[ ! -d "${CONFIG_DIR}" ]]; then
  if [[ "${IF_PRESENT}" -eq 1 ]]; then
    printf 'OpenCode config directory is not initialized; Git ignore update skipped: %s\n' "${CONFIG_DIR}"
    exit 0
  fi
  echo "OpenCode config directory not found: ${CONFIG_DIR}" >&2
  exit 1
fi

IGNORE_FILE="${CONFIG_DIR}/.gitignore"
touch "${IGNORE_FILE}"
added=0

# 只补缺失规则，不重写现场已有内容；重复升级执行保持幂等。
while IFS= read -r rule || [[ -n "${rule}" ]]; do
  [[ -n "${rule}" && "${rule}" != \#* ]] || continue
  if grep -Fqx -- "${rule}" "${IGNORE_FILE}"; then
    continue
  fi
  if [[ -s "${IGNORE_FILE}" && -n "$(tail -c 1 "${IGNORE_FILE}")" ]]; then
    printf '\n' >>"${IGNORE_FILE}"
  fi
  printf '%s\n' "${rule}" >>"${IGNORE_FILE}"
  added=$((added + 1))
done <"${RULES_FILE}"

printf 'OpenCode runtime Git ignore ensured: path=%s added=%s\n' "${IGNORE_FILE}" "${added}"
