#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
FIXTURE_ROOT="${ROOT_DIR}/.tmp"
TEST_DATA_TAG="${WORKSPACE_TEST_DATA_TAG:-20260719}"
LIVE_APP_WORKSPACE="${TEST_APP_PERSONAL_WORKSPACE:-}"
LIVE_PUBLIC_WORKTREE="${TEST_PUBLIC_PERSONAL_WORKTREE:-}"

if [[ ! "${TEST_DATA_TAG}" =~ ^[a-zA-Z0-9._-]+$ ]]; then
  echo "FAIL: WORKSPACE_TEST_DATA_TAG 只能包含字母、数字、点、下划线和连字符" >&2
  exit 1
fi
if ! git check-ref-format "refs/heads/feature_testagent_${TEST_DATA_TAG}"; then
  echo "FAIL: WORKSPACE_TEST_DATA_TAG 不能组成合法 Git 分支名" >&2
  exit 1
fi
if { [[ -n "${LIVE_APP_WORKSPACE}" ]] && [[ -z "${LIVE_PUBLIC_WORKTREE}" ]]; } || \
  { [[ -z "${LIVE_APP_WORKSPACE}" ]] && [[ -n "${LIVE_PUBLIC_WORKTREE}" ]]; }; then
  echo "FAIL: TEST_APP_PERSONAL_WORKSPACE 与 TEST_PUBLIC_PERSONAL_WORKTREE 必须同时设置" >&2
  exit 1
fi

mkdir -p "${FIXTURE_ROOT}"
FIXTURE_DIR="$(mktemp -d "${FIXTURE_ROOT}/workspace-branch-model.XXXXXX")"
APP_REPO="${FIXTURE_DIR}/application-repository"
APP_REMOTE="${FIXTURE_DIR}/application-remote.git"
PUBLISH_READY="${FIXTURE_DIR}/personal-publish-ready"
PUBLIC_REPO="${FIXTURE_DIR}/public-config-repository"
PUBLIC_REMOTE="${FIXTURE_DIR}/public-config-remote.git"
PUBLIC_PERSONAL="${FIXTURE_DIR}/public-personal-admin"

git_identity() {
  local repository="$1"
  git -C "${repository}" config user.name "TestAgent Fixture"
  git -C "${repository}" config user.email "testagent-fixture@example.invalid"
}

write_lines_if_missing() {
  local target="$1"
  shift
  if [[ -e "${target}" ]]; then
    printf 'SKIP: 测试数据已存在，不覆盖 %s\n' "${target}"
    return
  fi
  mkdir -p "$(dirname "${target}")"
  printf '%s\n' "$@" >"${target}"
}

seed_live_personal_worktrees() {
  local app_workspace="$1"
  local public_worktree="$2"
  local app_git_root public_git_root

  app_git_root="$(git -C "${app_workspace}" rev-parse --show-toplevel)"
  public_git_root="$(git -C "${public_worktree}" rev-parse --show-toplevel)"
  if [[ ! -d "${app_workspace}/.opencode" ]]; then
    echo "FAIL: 应用个人工作区不存在 .opencode：${app_workspace}" >&2
    exit 1
  fi
  if [[ ! -d "${public_worktree}/opencode" ]]; then
    echo "FAIL: 公共个人 worktree 不存在 opencode：${public_worktree}" >&2
    exit 1
  fi

  # 真实个人 worktree 只造未提交数据，供 UI 保存、本地提交和 Diff 验证；脚本绝不 push 真实远程。
  write_lines_if_missing "${app_workspace}/docs/test-data/publish-normal-${TEST_DATA_TAG}.md" \
    "# 普通文档提交推送测试 ${TEST_DATA_TAG}" "" \
    "marker: APP-DOC-${TEST_DATA_TAG}-R1" \
    "用途：验证 docs 文件可提交，并由发布流程投影到应用 feature。"
  write_lines_if_missing "${app_workspace}/archive/test-data/publish-archive-${TEST_DATA_TAG}.md" \
    "# 归档文档提交推送测试 ${TEST_DATA_TAG}" "" \
    "marker: APP-ARCHIVE-${TEST_DATA_TAG}-R1" \
    "用途：验证 archive 文件属于可发布的应用普通文件。"
  write_lines_if_missing "${app_workspace}/spec/test-data/local-only-${TEST_DATA_TAG}.md" \
    "# Spec 本地提交测试 ${TEST_DATA_TAG}" "" \
    "marker: APP-SPEC-${TEST_DATA_TAG}-R1" \
    "用途：验证 spec 可在个人分支提交，但发布必须被拒绝。"
  write_lines_if_missing "${app_workspace}/.opencode/agents/personal-hot-reload-${TEST_DATA_TAG}.md" \
    "---" "name: personal-hot-reload-${TEST_DATA_TAG}" \
    "description: 应用个人 Agent 热加载测试 ${TEST_DATA_TAG} R1" \
    "mode: subagent" "---" "" \
    "# 应用个人 Agent 热加载测试" "" \
    "保存前把 frontmatter 中的 R1 改成 R2；只应刷新当前用户。"
  write_lines_if_missing "${app_workspace}/.opencode/skills/personal-hot-reload-${TEST_DATA_TAG}/SKILL.md" \
    "---" "name: personal-hot-reload-${TEST_DATA_TAG}" \
    "description: 应用个人 Skill 热加载测试 ${TEST_DATA_TAG} R1" \
    "compatibility: opencode" "metadata:" "  scope: workspace" \
    "  marker: R1" "---" "" "# 应用个人 Skill 热加载测试" "" \
    "保存前把 description 与 marker 中的 R1 改成 R2。"
  write_lines_if_missing "${app_workspace}/.opencode/skills/personal-hot-reload-${TEST_DATA_TAG}/rules/no-dispose.md" \
    "# Skill 资源文件不触发 dispose" "" \
    "marker: APP-SKILL-RULE-${TEST_DATA_TAG}-R1" \
    "保存后只应进入 Diff，不应请求 /global/dispose。"

  write_lines_if_missing "${public_worktree}/opencode/agents/public-personal-hot-reload-${TEST_DATA_TAG}.md" \
    "---" "name: public-personal-hot-reload-${TEST_DATA_TAG}" \
    "description: 公共个人 Agent 热加载测试 ${TEST_DATA_TAG} R1" \
    "mode: subagent" "---" "" \
    "# 公共个人 Agent 热加载测试" "" \
    "保存前把 frontmatter 中的 R1 改成 R2；只应刷新当前超管。"
  write_lines_if_missing "${public_worktree}/opencode/skills/public-personal-hot-reload-${TEST_DATA_TAG}/SKILL.md" \
    "---" "name: public-personal-hot-reload-${TEST_DATA_TAG}" \
    "description: 公共个人 Skill 热加载测试 ${TEST_DATA_TAG} R1" \
    "compatibility: opencode" "metadata:" "  scope: public-personal" \
    "  marker: R1" "---" "" "# 公共个人 Skill 热加载测试" "" \
    "保存前把 description 与 marker 中的 R1 改成 R2。"
  write_lines_if_missing "${public_worktree}/opencode/skills/public-personal-hot-reload-${TEST_DATA_TAG}/rules/no-dispose.md" \
    "# 公共 Skill 资源文件不触发 dispose" "" \
    "marker: PUBLIC-SKILL-RULE-${TEST_DATA_TAG}-R1" \
    "保存后只应进入 Diff，不应请求 /global/dispose。"

  printf '真实应用个人 worktree 已造数（未提交）：%s\n' "${app_git_root}"
  printf '真实公共个人 worktree 已造数（未提交）：%s\n' "${public_git_root}"
}

# 应用 fixture：本地 bare remote 模拟共享远程；feature 是共享事实源。
git init -q --bare "${APP_REMOTE}"
git init -q -b main "${APP_REPO}"
git_identity "${APP_REPO}"
git -C "${APP_REPO}" remote add origin "${APP_REMOTE}"
mkdir -p "${APP_REPO}/docs" "${APP_REPO}/.opencode/agents"
printf 'base shared document\n' >"${APP_REPO}/docs/shared.md"
printf '%s\n' '---' 'name: reviewer' 'description: 应用 Agent 基线 R1' 'mode: subagent' '---' \
  'application reviewer baseline' >"${APP_REPO}/.opencode/agents/reviewer.md"
git -C "${APP_REPO}" add docs/shared.md .opencode/agents/reviewer.md
git -C "${APP_REPO}" commit -q -m "初始化应用 feature 基线"
APP_BASE_COMMIT="$(git -C "${APP_REPO}" rev-parse HEAD)"
git -C "${APP_REPO}" branch "feature_testagent_${TEST_DATA_TAG}" "${APP_BASE_COMMIT}"
git -C "${APP_REPO}" push -q origin main "feature_testagent_${TEST_DATA_TAG}"

git -C "${APP_REPO}" worktree add -q -b "feature_testagent_${TEST_DATA_TAG}_usr_clean_default" \
  "${FIXTURE_DIR}/personal-clean" "${APP_BASE_COMMIT}"
git_identity "${FIXTURE_DIR}/personal-clean"
printf 'clean user personal note\n' >"${FIXTURE_DIR}/personal-clean/docs/personal-note.md"
git -C "${FIXTURE_DIR}/personal-clean" add docs/personal-note.md
git -C "${FIXTURE_DIR}/personal-clean" commit -q -m "个人分支非冲突提交"

git -C "${APP_REPO}" worktree add -q -b "feature_testagent_${TEST_DATA_TAG}_usr_dirty_default" \
  "${FIXTURE_DIR}/personal-dirty" "${APP_BASE_COMMIT}"
git_identity "${FIXTURE_DIR}/personal-dirty"
printf 'uncommitted local draft\n' >"${FIXTURE_DIR}/personal-dirty/docs/local-draft.md"

git -C "${APP_REPO}" worktree add -q -b "feature_testagent_${TEST_DATA_TAG}_usr_conflict_default" \
  "${FIXTURE_DIR}/personal-conflict" "${APP_BASE_COMMIT}"
git_identity "${FIXTURE_DIR}/personal-conflict"
printf 'personal side of shared document\n' >"${FIXTURE_DIR}/personal-conflict/docs/shared.md"
git -C "${FIXTURE_DIR}/personal-conflict" add docs/shared.md
git -C "${FIXTURE_DIR}/personal-conflict" commit -q -m "个人分支冲突提交"

git -C "${APP_REPO}" checkout -q "feature_testagent_${TEST_DATA_TAG}"
printf 'feature side of shared document\n' >"${APP_REPO}/docs/shared.md"
printf 'published by user A\n' >"${APP_REPO}/docs/published-by-a.md"
printf '%s\n' '---' 'name: reviewer' 'description: 应用 Agent 已发布 R2' 'mode: subagent' '---' \
  'updated application reviewer' >"${APP_REPO}/.opencode/agents/reviewer.md"
git -C "${APP_REPO}" add docs/shared.md docs/published-by-a.md .opencode/agents/reviewer.md
git -C "${APP_REPO}" commit -q -m "A 推送 docs 与应用 Agent"
APP_TARGET_COMMIT="$(git -C "${APP_REPO}" rev-parse HEAD)"
git -C "${APP_REPO}" push -q origin "feature_testagent_${TEST_DATA_TAG}"

git -C "${FIXTURE_DIR}/personal-clean" merge -q --no-edit "${APP_TARGET_COMMIT}"
if git -C "${FIXTURE_DIR}/personal-conflict" merge --no-edit "${APP_TARGET_COMMIT}" >/dev/null 2>&1; then
  echo "FAIL: expected personal-conflict to retain a native merge conflict" >&2
  exit 1
fi

# 发布就绪 worktree 同时包含可发布普通文件、应用配置和只能本地提交的 spec。
git -C "${APP_REPO}" worktree add -q -b "feature_testagent_${TEST_DATA_TAG}_usr_publish_default" \
  "${PUBLISH_READY}" "${APP_TARGET_COMMIT}"
git_identity "${PUBLISH_READY}"
write_lines_if_missing "${PUBLISH_READY}/docs/test-data/publish-normal-${TEST_DATA_TAG}.md" \
  "# 待发布普通文档" "" "marker: FIXTURE-DOC-${TEST_DATA_TAG}-R1"
write_lines_if_missing "${PUBLISH_READY}/archive/test-data/publish-archive-${TEST_DATA_TAG}.md" \
  "# 待发布归档文档" "" "marker: FIXTURE-ARCHIVE-${TEST_DATA_TAG}-R1"
write_lines_if_missing "${PUBLISH_READY}/spec/test-data/local-only-${TEST_DATA_TAG}.md" \
  "# 只能本地提交的 Spec" "" "marker: FIXTURE-SPEC-${TEST_DATA_TAG}-R1"
write_lines_if_missing "${PUBLISH_READY}/.opencode/agents/publish-agent-${TEST_DATA_TAG}.md" \
  "---" "name: publish-agent-${TEST_DATA_TAG}" \
  "description: 待发布应用 Agent ${TEST_DATA_TAG} R1" "mode: subagent" "---" \
  "publish fixture agent"
write_lines_if_missing "${PUBLISH_READY}/.opencode/skills/publish-skill-${TEST_DATA_TAG}/SKILL.md" \
  "---" "name: publish-skill-${TEST_DATA_TAG}" \
  "description: 待发布应用 Skill ${TEST_DATA_TAG} R1" \
  "compatibility: opencode" "---" "publish fixture skill"
write_lines_if_missing "${PUBLISH_READY}/.opencode/skills/publish-skill-${TEST_DATA_TAG}/rules/check.md" \
  "# 随 Skill 发布的规则" "" "marker: FIXTURE-RULE-${TEST_DATA_TAG}-R1"

# 公共 fixture：公共 main 是本地 bare remote 的共享事实源，管理员在长期 worktree 编辑。
git init -q --bare "${PUBLIC_REMOTE}"
git init -q -b main "${PUBLIC_REPO}"
git_identity "${PUBLIC_REPO}"
git -C "${PUBLIC_REPO}" remote add origin "${PUBLIC_REMOTE}"
mkdir -p "${PUBLIC_REPO}/opencode/agents" "${PUBLIC_REPO}/opencode/skills/review"
printf '%s\n' '---' 'name: public-reviewer' 'description: 公共 Agent 基线 R1' 'mode: subagent' '---' \
  'public reviewer baseline' >"${PUBLIC_REPO}/opencode/agents/public-reviewer.md"
printf '%s\n' '---' 'name: public-review' 'description: 公共 Skill 基线 R1' \
  'compatibility: opencode' '---' 'public base skill' \
  >"${PUBLIC_REPO}/opencode/skills/review/SKILL.md"
git -C "${PUBLIC_REPO}" add opencode
git -C "${PUBLIC_REPO}" commit -q -m "初始化公共配置"
git -C "${PUBLIC_REPO}" push -q -u origin main
git -C "${PUBLIC_REPO}" worktree add -q -b public-usr_admin "${PUBLIC_PERSONAL}" main
git_identity "${PUBLIC_PERSONAL}"
write_lines_if_missing "${PUBLIC_PERSONAL}/opencode/agents/public-personal-hot-reload-${TEST_DATA_TAG}.md" \
  "---" "name: public-personal-hot-reload-${TEST_DATA_TAG}" \
  "description: 公共个人 Agent 待保存/发布 ${TEST_DATA_TAG} R1" "mode: subagent" "---" \
  "public personal fixture agent"
write_lines_if_missing "${PUBLIC_PERSONAL}/opencode/skills/public-personal-hot-reload-${TEST_DATA_TAG}/SKILL.md" \
  "---" "name: public-personal-hot-reload-${TEST_DATA_TAG}" \
  "description: 公共个人 Skill 待保存/发布 ${TEST_DATA_TAG} R1" \
  "compatibility: opencode" "---" "public personal fixture skill"
write_lines_if_missing "${PUBLIC_PERSONAL}/opencode/skills/public-personal-hot-reload-${TEST_DATA_TAG}/rules/no-dispose.md" \
  "# 公共 Skill 资源文件" "" "marker: PUBLIC-RULE-${TEST_DATA_TAG}-R1"

if [[ -n "${LIVE_APP_WORKSPACE}" ]]; then
  seed_live_personal_worktrees "${LIVE_APP_WORKSPACE}" "${LIVE_PUBLIC_WORKTREE}"
fi

# 生成可直接阅读的状态和安全发布命令；目录位于 .tmp，重复运行会创建新的独立 fixture。
{
  printf '# 工作区分支模型测试数据\n\n'
  printf -- '- 应用本地远程：`%s`。\n' "${APP_REMOTE}"
  printf -- '- 应用 feature：`%s`，已推送目标提交：`%s`。\n' "${APP_REPO}" "${APP_TARGET_COMMIT}"
  printf -- '- 发布就绪个人 worktree：`%s`，包含 docs、archive、spec、Agent、Skill 和 rules 未提交数据。\n' "${PUBLISH_READY}"
  printf -- '- 正常个人 worktree：`%s`，已经通过真实 `git merge <targetCommit>` 包含目标提交。\n' "${FIXTURE_DIR}/personal-clean"
  printf -- '- dirty 个人 worktree：`%s`，保留未提交文件，平台应显示待同步且不覆盖。\n' "${FIXTURE_DIR}/personal-dirty"
  printf -- '- 冲突个人 worktree：`%s`，保留 `MERGE_HEAD` 和 `docs/shared.md` 三方冲突。\n' "${FIXTURE_DIR}/personal-conflict"
  printf -- '- 公共本地远程：`%s`。\n' "${PUBLIC_REMOTE}"
  printf -- '- 公共管理员 worktree：`%s`，保留未提交的公共 Agent/Skill 修改。\n\n' "${PUBLIC_PERSONAL}"
  printf '## 初始状态核对\n\n```bash\n'
  printf 'git -C %q status --short\n' "${PUBLISH_READY}"
  printf 'git -C %q status --short\n' "${FIXTURE_DIR}/personal-dirty"
  printf 'git -C %q diff --name-only --diff-filter=U\n' "${FIXTURE_DIR}/personal-conflict"
  printf 'git -C %q rev-parse MERGE_HEAD\n' "${FIXTURE_DIR}/personal-conflict"
  printf 'git -C %q merge-base --is-ancestor %q HEAD\n' "${FIXTURE_DIR}/personal-clean" "${APP_TARGET_COMMIT}"
  printf 'git --git-dir=%q rev-parse %q\n' "${APP_REMOTE}" "refs/heads/feature_testagent_${TEST_DATA_TAG}"
  printf 'git -C %q status --short\n' "${PUBLIC_PERSONAL}"
  printf '```\n\n'
  printf '## 安全执行个人提交与应用 feature 推送\n\n'
  printf '下面只访问 fixture 内本地 bare remote，不会访问真实 Gitee。`spec/**` 只提交到个人分支，不投影到 feature。\n\n```bash\n'
  printf 'git -C %q add docs archive spec .opencode\n' "${PUBLISH_READY}"
  printf "git -C %q commit -m '测试：个人提交发布数据'\n" "${PUBLISH_READY}"
  printf 'PERSONAL_HEAD=$(git -C %q rev-parse HEAD)\n' "${PUBLISH_READY}"
  printf 'git -C %q checkout "$PERSONAL_HEAD" -- docs archive .opencode\n' "${APP_REPO}"
  printf 'git -C %q add docs archive .opencode\n' "${APP_REPO}"
  printf "git -C %q commit -m '测试：投影并发布个人选择文件'\n" "${APP_REPO}"
  printf 'git -C %q push origin %q\n' "${APP_REPO}" "feature_testagent_${TEST_DATA_TAG}"
  printf 'git --git-dir=%q show %q:%q\n' "${APP_REMOTE}" \
    "feature_testagent_${TEST_DATA_TAG}" "docs/test-data/publish-normal-${TEST_DATA_TAG}.md"
  printf 'if git --git-dir=%q cat-file -e %q 2>/dev/null; then exit 1; fi\n' "${APP_REMOTE}" \
    "feature_testagent_${TEST_DATA_TAG}:spec/test-data/local-only-${TEST_DATA_TAG}.md"
  printf '```\n\n'
  printf '## 安全执行公共提交与推送\n\n```bash\n'
  printf 'git -C %q add opencode\n' "${PUBLIC_PERSONAL}"
  printf "git -C %q commit -m '测试：提交公共个人配置'\n" "${PUBLIC_PERSONAL}"
  printf 'git -C %q push origin HEAD:main\n' "${PUBLIC_PERSONAL}"
  printf 'git --git-dir=%q show %q:%q\n' "${PUBLIC_REMOTE}" main \
    "opencode/agents/public-personal-hot-reload-${TEST_DATA_TAG}.md"
  printf '```\n'
  if [[ -n "${LIVE_APP_WORKSPACE}" ]]; then
    printf '\n## 真实个人 worktree 造数\n\n'
    printf -- '- 应用目录：`%s`。\n' "${LIVE_APP_WORKSPACE}"
    printf -- '- 公共目录：`%s`。\n' "${LIVE_PUBLIC_WORKTREE}"
    printf -- '- 这些文件均保持未提交；只用于 UI 保存、Diff、本地热加载与选择性提交测试，不会自动 push。\n'
  fi
} >"${FIXTURE_DIR}/README.md"

# 自动断言 fixture 的 Git 状态和已推送远程提交，失败即不交付目录。
git -C "${FIXTURE_DIR}/personal-clean" merge-base --is-ancestor "${APP_TARGET_COMMIT}" HEAD
test -n "$(git -C "${FIXTURE_DIR}/personal-dirty" status --short)"
git -C "${FIXTURE_DIR}/personal-conflict" rev-parse -q --verify MERGE_HEAD >/dev/null
test "$(git -C "${FIXTURE_DIR}/personal-conflict" diff --name-only --diff-filter=U)" = "docs/shared.md"
test -n "$(git -C "${PUBLISH_READY}" status --short)"
test -n "$(git -C "${PUBLIC_PERSONAL}" status --short)"
test "$(git --git-dir="${APP_REMOTE}" rev-parse "refs/heads/feature_testagent_${TEST_DATA_TAG}")" = "${APP_TARGET_COMMIT}"
test "$(git --git-dir="${PUBLIC_REMOTE}" rev-parse refs/heads/main)" = \
  "$(git -C "${PUBLIC_REPO}" rev-parse main)"

printf '%s\n' "${FIXTURE_DIR}"
