# 项目记忆

## 架构约定
- 后端 API 入口: `test-agent-api` Controller → `test-agent-workspace-management` Service
- 前端数据访问: `@test-agent/backend-api` client → 后端 HTTP API
- 文件操作通过 WebSocket RPC (WorkspaceFileSocketClient)
- Git 操作通过 `GitWorkspaceService` (ProcessGitCommandExecutor)

## 个人工作区 (Personal Workspace / Private Worktree)
- 创建入口: `POST /workspace-versions/{versionId}/ensure-default-personal-workspace`（无需请求体）
- 分支命名（新规则）: `{应用版本分支}_{userId}_默认空间名`，如 `feature_testagent_20260715_usr_xxx_default`
- 路径: `OPENCODE_PERSONAL_WORKTREE_ROOT/{sanitizedVersion}/{sanitizedUserId}/{repoEnglishName}/{sanitizedName}`
- 前端进入版本时自动调用 ensureDefaultPersonalWorkspace，同一用户同一版本 default 复用
- 旧 personal workspace 记录不做迁移，新规则仅影响新建

## Git Diff 与推送
- 工作区 Git diff: `GET /workspaces/{workspaceId}/git-diff` — 使用本地 git status/diff，不依赖 opencode
- 提交并推送: `POST /personal-workspaces/{personalWorkspaceId}/publish` — 个人 worktree merge 回应用版本分支
- 冲突仅发生在个人 worktree，应用版本副本保持干净

## Diff 架构变更 (2026-06-30)
- 旧: `api.getVcsDiffFiles(workspaceId)` → opencode `/vcs/diff`
- 新: `api.getWorkspaceGitDiff(workspaceId)` → 平台本地 Git diff
- 前端 `GitChangesPanel` 使用 `getWorkspaceGitDiff`，响应字段: path, status, staged, patch, additions, deletions

## 前端类型（新增）
- `DefaultPersonalWorkspaceResponse`: { personalWorkspaceId, personalWorkspaceName, personalWorkspaceBranch, runtimeWorkspace }
- `WorkspaceGitDiff`: { files: WorkspaceGitDiffFile[] }
- `PublishPersonalWorkspaceResult`: { status: "MERGED"|"CONFLICT", conflictFiles, message }
