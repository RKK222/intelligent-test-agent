# 工作区 Git 发布与冲突处理 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让工作区 Git 发布严格遵循文件白名单、可在工作台完成三方冲突解决，并只在远端 push 已确认时展示成功。

**Architecture:** `GitWorkspaceService` 提供 Git index/merge 原子能力，`ManagedWorkspaceApplicationService` 负责用户与工作区校验及发布编排，`ManagedWorkspaceController` 暴露冲突 API。前端由 `backend-api` 访问平台 API，`diff-viewer` 复用 Monaco 提供纯展示/编辑组件，`agent-web` 负责加载、解决、刷新和反馈。

**Tech Stack:** Java 21、Spring WebFlux、JUnit 5、Vue 3、TypeScript、Monaco Editor、Vitest、Testing Library、Playwright。

---

## Chunk 1: 后端 Git 语义与 API

### Task 1: 文件白名单提交与推送确认

**Files:**
- Modify: `backend/test-agent-common/src/main/java/com/icbc/testagent/common/git/GitWorkspaceService.java`
- Modify: `backend/test-agent-common/src/test/java/com/icbc/testagent/common/git/GitWorkspaceServiceTest.java`
- Modify: `backend/test-agent-workspace-management/src/main/java/com/icbc/testagent/workspace/ManagedWorkspaceApplicationService.java`
- Modify: `backend/test-agent-workspace-management/src/main/java/com/icbc/testagent/workspace/ManagedWorkspaceResponses.java`
- Test: `backend/test-agent-workspace-management/src/test/java/com/icbc/testagent/workspace/ManagedWorkspaceApplicationServiceTest.java`

- [ ] 写失败测试：普通发布存在其他 staged 文件时，必须先 unstage 全部并只提交请求文件。
- [ ] 运行目标测试并确认因缺少隔离逻辑失败。
- [ ] 增加 merge 状态检测和索引重置原子方法；普通发布使用文件白名单，merge 重试保留完整 merge index。
- [ ] 增加 `remotePushed/headCommit` 响应字段，只在 push、HEAD 查询和版本回写成功后返回确认。
- [ ] 运行 common 与 workspace-management 目标测试。

### Task 2: 冲突读取、解决和取消 API

**Files:**
- Modify: `backend/test-agent-common/src/main/java/com/icbc/testagent/common/git/GitWorkspaceService.java`
- Modify: `backend/test-agent-workspace-management/src/main/java/com/icbc/testagent/workspace/ManagedWorkspaceApplicationService.java`
- Modify: `backend/test-agent-workspace-management/src/main/java/com/icbc/testagent/workspace/ManagedWorkspaceResponses.java`
- Modify: `backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/platform/ManagedWorkspaceDtos.java`
- Modify: `backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/platform/ManagedWorkspaceController.java`
- Test: `backend/test-agent-common/src/test/java/com/icbc/testagent/common/git/GitWorkspaceServiceTest.java`
- Test: `backend/test-agent-workspace-management/src/test/java/com/icbc/testagent/workspace/ManagedWorkspaceApplicationServiceTest.java`
- Test: `backend/test-agent-api/src/test/java/com/icbc/testagent/api/web/platform/ManagedWorkspaceControllerTest.java`

- [ ] 写失败测试：读取 stage 1/2/3、拒绝非冲突路径、CURRENT/INCOMING/BOTH/MANUAL/DELETE 解决和 abort。
- [ ] 运行目标测试并确认失败原因是 API/服务缺失。
- [ ] 实现最小 Git stage 读取、工作树写入/删除、定点 stage 与 merge abort。
- [ ] 暴露 `GET git-conflict`、`POST git-conflict/resolve`、`POST git-conflict/abort`。
- [ ] 运行 workspace-management 与 API 目标测试。

## Chunk 2: 前端合并编辑器与状态反馈

### Task 3: backend-api 与共享类型

**Files:**
- Modify: `frontend/packages/shared-types/src/index.ts`
- Modify: `frontend/packages/backend-api/src/index.ts`
- Test: `frontend/packages/backend-api/tests/backend-api.test.ts`

- [ ] 写失败测试：三个冲突 API 的 URL、method、请求体和响应映射。
- [ ] 运行 backend-api 测试并确认失败。
- [ ] 增加冲突 DTO、解决枚举与 client 方法。
- [ ] 运行 backend-api 测试。

### Task 4: Monaco 三方合并组件

**Files:**
- Create: `frontend/packages/diff-viewer/src/MergeConflictEditor.vue`
- Modify: `frontend/packages/diff-viewer/src/index.ts`
- Modify: `frontend/packages/diff-viewer/README.md`
- Test: `frontend/apps/agent-web/tests/git-changes-panel.test.ts`

- [ ] 写失败组件测试：冲突行打开三方编辑器，选择当前/应用/两者、手工编辑、删除语义和 abort。
- [ ] 运行测试并确认组件/交互缺失。
- [ ] 复用现有 Monaco loader 实现两侧只读、结果可编辑的工作台合并视图。
- [ ] 运行组件测试与 diff-viewer typecheck。

### Task 5: GitChangesPanel 编排与成功状态

**Files:**
- Modify: `frontend/apps/agent-web/src/components/GitChangesPanel.vue`
- Modify: `frontend/apps/agent-web/tests/git-changes-panel.test.ts`

- [ ] 写失败测试：`remotePushed` 缺失或 false、API reject、后续 Agent push reject 都不能展示成功。
- [ ] 运行测试并确认旧实现错误展示进度或成功。
- [ ] 接入冲突加载/解决/abort，并在每次动作后刷新 diff。
- [ ] 将成功提示收口到全部操作确认之后；catch 必须清空 progress。
- [ ] 运行 GitChangesPanel 测试。

## Chunk 3: 文档、真实 Git 和运行态验证

### Task 6: 稳定文档同步

**Files:**
- Modify: `docs/api/http-api.md`
- Modify: `frontend/README.md`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `backend/test-agent-common/README.md`
- Modify: `backend/test-agent-workspace-management/README.md`

- [ ] 更新 API 请求/响应、冲突处理和远端推送确认语义。
- [ ] 检查 API、事件、数据库、安全、性能和兼容性影响。

### Task 7: 验证、启动与提交

- [ ] 运行后端目标测试和前端目标测试。
- [ ] 用临时 bare remote + 两分支构造真实冲突，验证白名单提交、三方内容、解决、push 失败和成功。
- [ ] 运行前端 typecheck/build。
- [ ] 使用 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 启动。
- [ ] 验证 health/readiness、前端 HTTP、CORS 和 manager 日志。
- [ ] 用浏览器直连 `http://127.0.0.1:3000` 构造/注入测试数据并验证交互。
- [ ] 按 `docs/guides/self-checklist.md` 自检，回顾并按需更新 `.agents/session-log.md`。
- [ ] 只暂存本任务文件并使用中文 commit message 自动提交。
