# 工作区 Git 原生冲突流程 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为个人工作区发布增加远程变更预览、Git 原生批量冲突处理、正确的非三方冲突展示和可靠的 Diff 数量自动同步。

**Architecture:** `GitWorkspaceService` 扩展中文路径和批量 ours/theirs 原子操作；`ManagedWorkspaceApplicationService` 复用既有 personal workspace 归属、应用副本和发布编排，增加预览、HEAD 校验与元数据同步；Controller/backend-api 暴露兼容 API；`GitChangesPanel` 和 `AgentWorkbench` 负责确认交互与单一刷新数据流。

**Tech Stack:** Java 21、Spring WebFlux、JUnit 5、真实临时 Git 仓库、Vue 3、TypeScript、Vitest、Monaco Editor。

---

## Chunk 1: Git 原子能力与批量冲突

### Task 1: 中文冲突路径与原生批量解决

**Files:**
- Modify: `backend/test-agent-common/src/main/java/com/enterprise/testagent/common/git/GitWorkspaceService.java`
- Modify: `backend/test-agent-common/src/test/java/com/enterprise/testagent/common/git/GitWorkspaceServiceTest.java`
- Modify: `backend/test-agent-common/README.md`

- [ ] **Step 1: 写中文路径红测**

构造真实临时仓库和中文 `AU`/`UD` 冲突，断言 `conflictPaths` 返回 UTF-8 路径而不是带引号的八进制转义。

- [ ] **Step 2: 写批量 current/incoming 红测**

断言批量 `CURRENT` 保留 stage 2、stage 2 缺失时删除；批量 `INCOMING` 保留 stage 3、stage 3 缺失时删除；操作后 `git diff --diff-filter=U` 为空。

- [ ] **Step 3: 运行红测**

Run:

```bash
cd backend
mvn -pl test-agent-common -Dtest=GitWorkspaceServiceTest test
```

Expected: 中文路径仍转义，批量方法不存在。

- [ ] **Step 4: 最小实现**

`conflictPaths` 改用现有 `gitNoQuotedPath`；新增批量冲突方法，按 stage 将 path 分组，复用 `checkout --ours/--theirs`、`stageFiles` 和 `git rm`，不使用无条件 `git add -A`。

- [ ] **Step 5: 运行绿测并更新 README**

Run:

```bash
cd backend
mvn -pl test-agent-common -Dtest=GitWorkspaceServiceTest test
```

## Chunk 2: 发布预览、HEAD 校验和副本元数据

### Task 2: 工作区服务发布预览与批量 API

**Files:**
- Modify: `backend/test-agent-workspace-management/src/main/java/com/enterprise/testagent/workspace/ManagedWorkspaceApplicationService.java`
- Modify: `backend/test-agent-workspace-management/src/main/java/com/enterprise/testagent/workspace/ManagedWorkspaceResponses.java`
- Modify: `backend/test-agent-workspace-management/src/test/java/com/enterprise/testagent/workspace/ManagedWorkspaceApplicationServiceTest.java`
- Modify: `backend/test-agent-workspace-management/README.md`

- [ ] **Step 1: 写服务红测**

覆盖：

- preview 返回应用 HEAD、待合入提交数、A/M/D/R 数量和样例路径；
- expected HEAD 一致时发布继续，不一致时在提交个人文件前返回 `CONFLICT`；
- 应用 pull 成功、个人 merge 冲突时，version target 和本机 replica commit 已同步到实际应用 HEAD；
- resolve-all 只允许 `CURRENT/INCOMING`，并委托 common 批量方法；
- 没有 merge 或没有 unmerged path 时拒绝。

- [ ] **Step 2: 运行红测**

Run:

```bash
cd backend
mvn -pl test-agent-workspace-management -am \
  -Dtest=ManagedWorkspaceApplicationServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: 实现预览和元数据共享程序**

抽取现有应用副本 fetch/pull 后的 HEAD 记录逻辑，让 preview 和 publish 共用；preview 不修改个人 worktree。publish 接收可选 expected HEAD，校验通过后才 reset/stage/commit。

- [ ] **Step 4: 实现 resolve-all 服务**

复用 `personalGitContext`、`conflictPaths` 和 common 批量方法；完成后仍由调用方刷新真实 Git Diff。

- [ ] **Step 5: 运行绿测并更新 README**

### Task 3: Controller、DTO 和 HTTP 文档

**Files:**
- Modify: `backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/platform/ManagedWorkspaceController.java`
- Modify: `backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/platform/ManagedWorkspaceDtos.java`
- Modify: `backend/test-agent-api/src/test/java/com/enterprise/testagent/api/web/platform/ManagedWorkspaceControllerTest.java`
- Modify: `backend/test-agent-api/README.md`
- Modify: `docs/api/http-api.md`
- Modify: `docs/architecture/module-map.md`

- [ ] **Step 1: 写 Controller 红测**

覆盖 `publish-preview`、`git-conflict/resolve-all` 和 publish `expectedApplicationHead` 映射。

- [ ] **Step 2: 运行红测**

Run:

```bash
cd backend
mvn -pl test-agent-api -am \
  -Dtest=ManagedWorkspaceControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: 增加路由和 DTO**

新增：

- `POST /personal-workspaces/{id}/publish-preview`
- `POST /workspaces/{workspaceId}/git-conflict/resolve-all`

旧 publish 请求保持兼容。

- [ ] **Step 4: 运行绿测并同步稳定文档**

## Chunk 3: 前端确认、批量操作和刷新数据流

### Task 4: shared-types 与 backend-api

**Files:**
- Modify: `frontend/packages/shared-types/src/index.ts`
- Modify: `frontend/packages/backend-api/src/index.ts`
- Modify: `frontend/packages/backend-api/tests/backend-api.test.ts`
- Modify: `frontend/packages/backend-api/README.md`

- [ ] **Step 1: 写 backend-api 红测**

断言预览、批量解决路由、请求体和 expected HEAD 正确。

- [ ] **Step 2: 运行红测**

Run:

```bash
cd frontend
corepack pnpm test -- backend-api.test.ts
```

- [ ] **Step 3: 增加类型与 client 方法**

- [ ] **Step 4: 运行绿测和包 typecheck**

### Task 5: GitChangesPanel 发布确认与批量操作

**Files:**
- Modify: `frontend/apps/agent-web/src/components/GitChangesPanel.vue`
- Modify: `frontend/apps/agent-web/tests/git-changes-panel.test.ts`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `frontend/apps/agent-web/src/PACKAGE.md`

- [ ] **Step 1: 写交互红测**

覆盖：

- incoming 为 0 时直接 publish；
- incoming 大于 0 时只展示确认，不立即 publish；
- 确认后携带 expected HEAD 发布；
- preview 后 HEAD 变化显示重新确认提示；
- 全部保留个人/全部采用应用调用 resolve-all；
- conflict banner 只显示数量摘要；
- 每次 stage/unstage/resolve/abort/publish 后刷新并把真实文件摘要上送父组件。

- [ ] **Step 2: 运行红测**

Run:

```bash
cd frontend
corepack pnpm test -- git-changes-panel.test.ts
```

- [ ] **Step 3: 实现最小交互**

复用现有 Button、错误区、refreshChanges 和提交状态；增加轻量确认层，不引入新状态库或重复 Git 数据源。

- [ ] **Step 4: 运行绿测**

### Task 6: 非三方冲突文案

**Files:**
- Modify: `frontend/packages/diff-viewer/src/MergeConflictEditor.vue`
- Modify: `frontend/packages/diff-viewer/tests/merge-conflict-editor.test.ts`
- Modify: `frontend/packages/diff-viewer/README.md`
- Modify: `frontend/packages/diff-viewer/src/PACKAGE.md`

- [ ] **Step 1: 写 `AU/UD` 红测**

stage 3 缺失时展示“应用版本已删除此文件”，stage 2 缺失时展示“个人版本已删除此文件”，并禁用保留两者。

- [ ] **Step 2: 运行红测**

Run:

```bash
cd frontend
corepack pnpm test -- merge-conflict-editor.test.ts
```

- [ ] **Step 3: 调整条件文案并运行绿测**

### Task 7: Diff 数量首次加载自动同步

**Files:**
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`
- Modify: `frontend/apps/agent-web/src/components/FigmaFileExplorer.vue`
- Modify: `frontend/apps/agent-web/src/components/GitChangesPanel.vue`
- Modify: `frontend/apps/agent-web/tests/AgentWorkbench.test.ts`
- Modify: `frontend/apps/agent-web/tests/git-changes-panel.test.ts`

- [ ] **Step 1: 写首次加载和竞态红测**

覆盖 watcher 建立前已有 selected workspace 时立即加载角标；快速切换工作区时旧请求不能覆盖新工作区；panel 刷新后父组件直接使用 payload 文件摘要，不重复请求。

- [ ] **Step 2: 运行红测**

- [ ] **Step 3: 实现 immediate watcher、请求 token 和 refresh payload**

- [ ] **Step 4: 运行绿测**

## Chunk 4: 总体验证和交付

### Task 8: 回归、启动与页面验收

**Files:**
- Modify: `frontend/README.md`
- Modify: `.agents/session-log.md`

- [ ] **Step 1: 后端目标回归**

Run:

```bash
cd backend
mvn -pl test-agent-common,test-agent-workspace-management,test-agent-api -am \
  -Dtest=GitWorkspaceServiceTest,ManagedWorkspaceApplicationServiceTest,ManagedWorkspaceControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 2: 前端全量回归**

Run:

```bash
cd frontend
corepack pnpm test
corepack pnpm typecheck
corepack pnpm build
```

- [ ] **Step 3: 重启本地服务**

Run:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:/Users/kaka/Desktop/intelligent-test-agent/.tmp/dev-bin:/opt/homebrew/opt/libpq/bin:$PATH"
./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build
```

- [ ] **Step 4: 健康和页面验收**

验证：

- backend readiness、frontend、manager 和当前 opencode health；
- 登录 888 账户；
- 变更角标首次进入自动显示；
- 当前 54+8 现场展示中文路径、批量按钮和简洁 banner；
- 发布预览展示应用分支变更摘要；
- 不实际点击当前现场的批量解决和取消 merge。

- [ ] **Step 5: 收尾**

更新 session log，运行 `git diff --check`，回顾所有改动和未完成项，中文提交；不推送远程。
