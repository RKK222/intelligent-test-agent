#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENSURE_SCRIPT="${ROOT_DIR}/deploy/internal/ensure-opencode-runtime-gitignore.sh"
RULES_FILE="${ROOT_DIR}/deploy/internal/opencode-runtime.gitignore"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-opencode-gitignore.XXXXXX")"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

CONFIG_DIR="${TMP_ROOT}/repository/opencode"
mkdir -p "${CONFIG_DIR}/agents" "${CONFIG_DIR}/skills"
printf 'custom-cache/' >"${CONFIG_DIR}/.gitignore"
printf 'agent\n' >"${CONFIG_DIR}/agents/review.md"
printf 'skill\n' >"${CONFIG_DIR}/skills/SKILL.md"

bash "${ENSURE_SCRIPT}" --config-dir "${CONFIG_DIR}" >/dev/null
bash "${ENSURE_SCRIPT}" --config-dir "${CONFIG_DIR}" >/dev/null

grep -Fxq 'custom-cache/' "${CONFIG_DIR}/.gitignore"
while IFS= read -r rule || [[ -n "${rule}" ]]; do
  [[ -n "${rule}" ]] || continue
  [[ "$(grep -Fxc -- "${rule}" "${CONFIG_DIR}/.gitignore")" -eq 1 ]]
done <"${RULES_FILE}"

git -C "${TMP_ROOT}/repository" init -q
touch "${CONFIG_DIR}/package.json" "${CONFIG_DIR}/package-lock.json"
status="$(git -C "${TMP_ROOT}/repository" status --short --untracked-files=all)"
grep -Fq '?? opencode/agents/' <<<"${status}"
grep -Fq '?? opencode/skills/' <<<"${status}"
if grep -Eq 'package(-lock)?\.json|\.gitignore' <<<"${status}"; then
  echo "OpenCode runtime files unexpectedly appear in Git status" >&2
  exit 1
fi

missing_dir="${TMP_ROOT}/not-initialized/opencode"
bash "${ENSURE_SCRIPT}" --config-dir "${missing_dir}" --if-present >/dev/null
[[ ! -e "${missing_dir}" ]]

echo "OpenCode runtime Git ignore rules and user-managed directory detection verified"
