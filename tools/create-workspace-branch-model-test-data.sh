#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
FIXTURE_ROOT="${ROOT_DIR}/.tmp"
mkdir -p "${FIXTURE_ROOT}"
FIXTURE_DIR="$(mktemp -d "${FIXTURE_ROOT}/workspace-branch-model.XXXXXX")"
APP_REPO="${FIXTURE_DIR}/application-repository"
PUBLIC_REPO="${FIXTURE_DIR}/public-config-repository"

git_identity() {
  local repository="$1"
  git -C "${repository}" config user.name "TestAgent Fixture"
  git -C "${repository}" config user.email "testagent-fixture@example.invalid"
}

# 应用 fixture：feature 是共享事实源，三个个人 worktree 分别覆盖成功、dirty 待同步和真实冲突。
git init -q -b main "${APP_REPO}"
git_identity "${APP_REPO}"
mkdir -p "${APP_REPO}/docs" "${APP_REPO}/.opencode/agents"
printf 'base shared document\n' >"${APP_REPO}/docs/shared.md"
printf 'base application agent\n' >"${APP_REPO}/.opencode/agents/reviewer.md"
git -C "${APP_REPO}" add docs/shared.md .opencode/agents/reviewer.md
git -C "${APP_REPO}" commit -q -m "初始化应用 feature 基线"
APP_BASE_COMMIT="$(git -C "${APP_REPO}" rev-parse HEAD)"
git -C "${APP_REPO}" branch feature_testagent_20260719 "${APP_BASE_COMMIT}"

git -C "${APP_REPO}" worktree add -q -b feature_testagent_20260719_usr_clean_default \
  "${FIXTURE_DIR}/personal-clean" "${APP_BASE_COMMIT}"
git_identity "${FIXTURE_DIR}/personal-clean"
printf 'clean user personal note\n' >"${FIXTURE_DIR}/personal-clean/docs/personal-note.md"
git -C "${FIXTURE_DIR}/personal-clean" add docs/personal-note.md
git -C "${FIXTURE_DIR}/personal-clean" commit -q -m "个人分支非冲突提交"

git -C "${APP_REPO}" worktree add -q -b feature_testagent_20260719_usr_dirty_default \
  "${FIXTURE_DIR}/personal-dirty" "${APP_BASE_COMMIT}"
git_identity "${FIXTURE_DIR}/personal-dirty"
printf 'uncommitted local draft\n' >"${FIXTURE_DIR}/personal-dirty/docs/local-draft.md"

git -C "${APP_REPO}" worktree add -q -b feature_testagent_20260719_usr_conflict_default \
  "${FIXTURE_DIR}/personal-conflict" "${APP_BASE_COMMIT}"
git_identity "${FIXTURE_DIR}/personal-conflict"
printf 'personal side of shared document\n' >"${FIXTURE_DIR}/personal-conflict/docs/shared.md"
git -C "${FIXTURE_DIR}/personal-conflict" add docs/shared.md
git -C "${FIXTURE_DIR}/personal-conflict" commit -q -m "个人分支冲突提交"

git -C "${APP_REPO}" checkout -q feature_testagent_20260719
printf 'feature side of shared document\n' >"${APP_REPO}/docs/shared.md"
printf 'published by user A\n' >"${APP_REPO}/docs/published-by-a.md"
printf 'updated application agent\n' >"${APP_REPO}/.opencode/agents/reviewer.md"
git -C "${APP_REPO}" add docs/shared.md docs/published-by-a.md .opencode/agents/reviewer.md
git -C "${APP_REPO}" commit -q -m "A 推送 docs 与应用 Agent"
APP_TARGET_COMMIT="$(git -C "${APP_REPO}" rev-parse HEAD)"

git -C "${FIXTURE_DIR}/personal-clean" merge -q --no-edit "${APP_TARGET_COMMIT}"
if git -C "${FIXTURE_DIR}/personal-conflict" merge --no-edit "${APP_TARGET_COMMIT}" >/dev/null 2>&1; then
  echo "FAIL: expected personal-conflict to retain a native merge conflict" >&2
  exit 1
fi

# 公共 fixture：公共 main 是远端/共享事实，管理员只在自己的长期 worktree 上编辑。
git init -q -b main "${PUBLIC_REPO}"
git_identity "${PUBLIC_REPO}"
mkdir -p "${PUBLIC_REPO}/opencode/agents" "${PUBLIC_REPO}/opencode/skills/review"
printf 'public base agent\n' >"${PUBLIC_REPO}/opencode/agents/public-reviewer.md"
printf '%s\n' '---' 'name: public-review' '---' 'public base skill' \
  >"${PUBLIC_REPO}/opencode/skills/review/SKILL.md"
git -C "${PUBLIC_REPO}" add opencode
git -C "${PUBLIC_REPO}" commit -q -m "初始化公共配置"
git -C "${PUBLIC_REPO}" worktree add -q -b public-usr_admin \
  "${FIXTURE_DIR}/public-personal-admin" main
git_identity "${FIXTURE_DIR}/public-personal-admin"
printf 'public admin local edit, not published\n' \
  >"${FIXTURE_DIR}/public-personal-admin/opencode/agents/public-reviewer.md"

# 生成可直接阅读的状态清单；目录位于 .tmp，重复运行会创建新的独立 fixture。
{
  printf '# 工作区分支模型测试数据\n\n'
  printf -- '- 应用 feature：`%s`，目标提交：`%s`。\n' "${APP_REPO}" "${APP_TARGET_COMMIT}"
  printf -- '- 正常个人 worktree：`%s`，已经通过真实 `git merge <targetCommit>` 包含目标提交。\n' "${FIXTURE_DIR}/personal-clean"
  printf -- '- dirty 个人 worktree：`%s`，保留未提交文件，平台应显示待同步且不覆盖。\n' "${FIXTURE_DIR}/personal-dirty"
  printf -- '- 冲突个人 worktree：`%s`，保留 `MERGE_HEAD` 和 `docs/shared.md` 三方冲突。\n' "${FIXTURE_DIR}/personal-conflict"
  printf -- '- 公共管理员 worktree：`%s`，保留未推送的公共 Agent 修改。\n\n' "${FIXTURE_DIR}/public-personal-admin"
  printf '建议核对命令：\n\n```bash\n'
  printf 'git -C %q status --short\n' "${FIXTURE_DIR}/personal-dirty"
  printf 'git -C %q diff --name-only --diff-filter=U\n' "${FIXTURE_DIR}/personal-conflict"
  printf 'git -C %q rev-parse MERGE_HEAD\n' "${FIXTURE_DIR}/personal-conflict"
  printf 'git -C %q merge-base --is-ancestor %q HEAD\n' "${FIXTURE_DIR}/personal-clean" "${APP_TARGET_COMMIT}"
  printf 'git -C %q status --short\n' "${FIXTURE_DIR}/public-personal-admin"
  printf '```\n'
} >"${FIXTURE_DIR}/README.md"

git -C "${FIXTURE_DIR}/personal-clean" merge-base --is-ancestor "${APP_TARGET_COMMIT}" HEAD
test -n "$(git -C "${FIXTURE_DIR}/personal-dirty" status --short)"
git -C "${FIXTURE_DIR}/personal-conflict" rev-parse -q --verify MERGE_HEAD >/dev/null
test "$(git -C "${FIXTURE_DIR}/personal-conflict" diff --name-only --diff-filter=U)" = "docs/shared.md"
test -n "$(git -C "${FIXTURE_DIR}/public-personal-admin" status --short)"

printf '%s\n' "${FIXTURE_DIR}"
