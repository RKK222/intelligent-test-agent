# 工作区 Git 面板交互修复 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让工作区 Git 面板遵循真实 Git stage/unstage 状态，快速打开已加载 Diff，并可靠保存三方冲突结果。

**Architecture:** `GitWorkspaceService` 继续提供 Git 原子命令，`ManagedWorkspaceApplicationService` 负责用户、路径和冲突校验，Controller 暴露定点 stage/unstage API。前端 `GitChangesPanel` 通过 backend-api 操作真实 index，`AgentWorkbench` 复用已加载 Diff，`MergeConflictEditor` 用响应式结果桥接 Monaco。

**Tech Stack:** Java 21、Spring WebFlux、JUnit 5、Vue 3、TypeScript、Monaco Editor、Vitest、Testing Library。

---

## Chunk 1: 真实 Git index 操作

### Task 1: 后端 stage/unstage API

**Files:**
- Modify: `backend/test-agent-workspace-management/src/main/java/com/enterprise/testagent/workspace/ManagedWorkspaceApplicationService.java`
- Modify: `backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/platform/ManagedWorkspaceController.java`
- Test: `backend/test-agent-workspace-management/src/test/java/com/enterprise/testagent/workspace/ManagedWorkspaceApplicationServiceTest.java`
- Test: `backend/test-agent-api/src/test/java/com/enterprise/testagent/api/web/platform/ManagedWorkspaceControllerTest.java`

- [ ] 写失败测试：非冲突文件定点 stage/unstage，冲突路径拒绝。
- [ ] 运行目标测试并确认因服务和路由缺失而失败。
- [ ] 复用 `personalGitContext`、`repoRelativeFiles`、`stageFiles`、`unstageFiles` 实现服务方法。
- [ ] 暴露 `POST git-stage` 与 `POST git-unstage`，请求体复用 `WorkspaceGitFilesRequest`。
- [ ] 运行 workspace-management 与 API 目标测试。

### Task 2: 前端真实暂存编排

**Files:**
- Modify: `frontend/packages/backend-api/src/index.ts`
- Modify: `frontend/apps/agent-web/src/components/GitChangesPanel.vue`
- Test: `frontend/apps/agent-web/tests/git-changes-panel.test.ts`

- [ ] 写失败测试：冲突存在时非冲突文件仍可 stage/unstage，并调用后端 API。
- [ ] 运行测试确认当前本地 Set 与隐藏按钮不符合要求。
- [ ] 增加 backend-api 方法，暂存动作成功后刷新 `git-diff`。
- [ ] 保留冲突时 commit/push 禁用，并增加原生 Git 约束提示。
- [ ] 运行 GitChangesPanel 测试。

## Chunk 2: 列表与编辑器体验

### Task 3: 完整路径提示与快速打开 Diff

**Files:**
- Modify: `frontend/apps/agent-web/src/components/GitChangesPanel.vue`
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`
- Test: `frontend/apps/agent-web/tests/git-changes-panel.test.ts`
- Test: `frontend/apps/agent-web/tests/AgentWorkbench.test.ts`

- [ ] 写失败测试：整行具有完整路径提示；缓存命中时点击文件不重复调用 `getWorkspaceGitDiff`。
- [ ] 运行测试确认提示范围和重复加载问题。
- [ ] openDiff payload 携带当前 Diff 文件；工作台直接使用缓存并切换选中项。
- [ ] 仅缓存缺失时回退 `loadDiffSource("vcs")`。
- [ ] 运行目标前端测试。

### Task 4: 响应式冲突保存

**Files:**
- Modify: `frontend/packages/diff-viewer/src/MergeConflictEditor.vue`
- Test: `frontend/packages/diff-viewer/tests/merge-conflict-editor.test.ts`

- [ ] 写失败测试：保留当前、采用应用、保留两者后保存按钮启用并提交正确内容。
- [ ] 运行测试确认非响应式 Monaco model 导致按钮不可用。
- [ ] 用响应式结果同步预设按钮与 Monaco model，并把按钮文案改为“保存并标记已解决”。
- [ ] 运行 diff-viewer 与 GitChangesPanel 测试。

## Chunk 3: 文档与运行态验证

### Task 5: 文档、回归和本地验证

**Files:**
- Modify: `docs/api/http-api.md`
- Modify: `backend/test-agent-common/README.md`
- Modify: `backend/test-agent-workspace-management/README.md`
- Modify: `backend/test-agent-api/README.md`
- Modify: `frontend/README.md`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `frontend/apps/agent-web/src/PACKAGE.md`
- Modify: `frontend/packages/backend-api/README.md`
- Modify: `frontend/packages/diff-viewer/README.md`
- Modify: `.agents/session-log.md`

- [ ] 同步 stage/unstage API、原生冲突提交约束和交互说明。
- [ ] 运行后端目标测试、前端全量测试、typecheck 和 build。
- [ ] 使用 `.env.test` / `test` profile 重启服务。
- [ ] 用现有 `usr_test_dev / coss / default` 冲突数据验证三方保存、真实 stage/unstage 和快速 Diff。
- [ ] 回顾 session log、运行 `git diff --check`，只暂存本任务文件并中文提交。
