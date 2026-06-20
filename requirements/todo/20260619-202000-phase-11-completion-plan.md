# Phase 11 剩余能力完成计划

## 背景

- 用户问题：当前 Phase 11 已完成 P0/P1 主路径，但仍有若干能力未达到 `docs/plan/11-opencode-web-feature-replica.md` 的完整验收范围。
- 当前现象：已实现 runtime facade/API、事件 reducer、运行态选择、permission/question、Todo、slash command、`@` context、Diff 来源切换、MCP/LSP/VCS 状态、只读 transcript、`POST /api/runs` 的 PromptPart 到 opencode `prompt_async` 端到端透传，以及 Session 全局搜索/置顶/软删除；附件/图片、follow-up、深度 Diff review、PTY 和 E2E 仍缺口明确。
- 目标：按不破坏现有边界的方式补完 Phase 11，最终达到 Web App 运行态能力完整可验收。

## 范围

- 包含：附件/图片、busy follow-up、Diff review 增强、PTY 架构与安全前置、Playwright E2E 和文档验收。
- 不包含：settings/config/provider/server 配置页、MCP 安装认证配置 UI、前端直连 opencode server、绕过平台鉴权的公网分享。

## 现状分析

- 相关文件：
  - `backend/test-agent-app/src/main/java/com/example/testagent/app/web/RuntimeDtos.java`
  - `backend/test-agent-app/src/main/java/com/example/testagent/app/run/RunApplicationService.java`
  - `backend/test-agent-opencode-client/src/main/java/com/example/testagent/opencode/client/OpencodeStartRunCommand.java`
  - `frontend/apps/agent-web/src/components/AgentWorkbench.tsx`
  - `frontend/packages/agent-chat/src/AgentChat.tsx`
  - `frontend/packages/diff-viewer/src/DiffViewer.tsx`
- 当前实现：`POST /api/runs` 已把平台 text/file/agent/reference parts 转换为 opencode `prompt_async` 输入并保留旧 `prompt` 兼容；Session 已支持全局搜索、标题更新、置顶和软删除，workspace 列表继续兼容；Diff 仍以 Run 级 accept/reject 为唯一落盘语义；PTY 未进入安全边界。
- 问题原因：Phase 11 先落了兼容主路径，剩余能力需要明确 opencode payload 映射、平台 API 语义、安全边界和 E2E 场景后再补。

## 修改方案

### 1. PromptPart 端到端透传（已完成）

- 修改文件：`RuntimeDtos.java`、`RunApplicationService.java`、`OpencodeStartRunCommand.java`、`DefaultOpencodeClientFacade.java`、`GeneratedOpencodeSdkGateway.java`、`frontend/packages/backend-api/src/index.ts`、`frontend/apps/agent-web/src/components/AgentWorkbench.tsx`。
- 修改位置：Run 启动请求 DTO、应用服务启动编排、opencode facade startRun 命令体、前端 prompt composer。
- 完成内容：保留旧 `prompt` 字段；新增 `StartRunInput` 和 `OpencodePromptPart`；平台 text/file/agent/reference part 已转换为 opencode `text/file/agent` part 或可读 text part；文件 part 只允许 workspace 内路径或内联内容；`agent/model/variant/messageId` 已下沉到 opencode facade。
- 后续注意：图片附件的 UI 构造和上传/URL 来源仍归入“附件、图片和 busy follow-up”批次，不影响当前后端 parts 透传能力。

### 2. Session 管理补齐（已完成）

- 修改文件：`SessionController.java`、`SessionApplicationService.java`、`SessionRepository.java`、`JdbcSessionRepository.java`、`frontend/packages/backend-api/src/index.ts`、`frontend/apps/agent-web/src/components/AgentWorkbench.tsx`。
- 修改位置：Session API、应用服务、持久化端口与工作台 history 区。
- 完成内容：新增平台 `GET /api/sessions` 搜索分页、`PATCH /api/sessions/{id}` 标题/置顶、`DELETE /api/sessions/{id}` 软删除；新增 Flyway V4 `sessions.pinned`，旧数据默认未置顶；前端 History 搜索、置顶和删除通过 `backend-api` 接入。
- 后续注意：session tab/draft 的更复杂跨设备同步仍未做数据库扩展；当前实现满足 Web History 主路径。

### 3. 附件、图片和 busy follow-up

- 修改文件：`frontend/packages/agent-chat/src/AgentChat.tsx`、新增 `frontend/packages/agent-chat/src/prompt-parts.ts`、`AgentWorkbench.tsx`、`docs/api/backend-api.md`。
- 修改位置：prompt composer、prompt parts 构造、Run busy 状态处理。
- 具体改动：支持文件选择、图片 data URL 或后端可读路径、当前编辑器选区上下文；Run busy 时把 follow-up 存入本地队列，当前 run 终态后自动提交下一条；abort 使用现有 cancel/abort API。
- 原因：补齐 opencode Web App 的多 part prompt 和 busy follow-up 行为。

### 4. Diff Review 增强

- 修改文件：`frontend/packages/diff-viewer/src/DiffViewer.tsx`、`frontend/apps/agent-web/src/components/AgentWorkbench.tsx`、`docs/frontend/frontend-backend-contract.md`。
- 修改位置：Diff viewer 文件导航、hunk 定位、评论/选区上下文。
- 具体改动：在不开放后端 per-file 回滚的前提下增加 hunk 列表、上/下 hunk 导航、当前选区引用到 prompt parts；Run 级 accept/reject 继续保持唯一落盘动作。
- 原因：满足 P1 Diff review 可用性，同时不制造未定义的回滚语义。

### 5. PTY 前置与 P2 实施

- 修改文件：`docs/architecture/` 新增 PTY WebSocket 设计、`docs/security/security-standards.md`、后端 runtime controller/service、前端新增 terminal 包。
- 修改位置：架构与安全文档先行，随后实现受控 WebSocket。
- 具体改动：先定义 ticket、鉴权、限流、审计、脱敏、resize/input/close 协议；文档验收后再实现后端 PTY 代理和前端 terminal 面板。
- 原因：PTY 是高风险双向通道，必须满足计划中的安全例外前置条件。

### 6. E2E 与验收闭环

- 修改文件：`frontend/apps/agent-web/tests/workbench.spec.ts`、后端 runtime/controller 测试、`docs/frontend/frontend-testing-standards.md`。
- 修改位置：Playwright 主流程、后端 API 集成测试、文档测试说明。
- 具体改动：覆盖新建/继续会话、prompt parts、流式 message part、权限审批、提问回复、abort、Diff 来源切换、slash command、`@` 引用、只读 transcript；P2 完成后再补 PTY 场景。
- 原因：把 Phase 11 从“功能已接入”推进到“可回归验收”。

## 影响范围

- UI/交互：Agent 面板、prompt composer、history、Diff viewer、terminal。
- 数据/协议：已新增 Session `pinned` 字段；软删除复用 `status=ARCHIVED`；PromptPart 已升级为真实运行输入。
- 兼容性：旧 `prompt`、旧 `assistant.message.delta`、Run 级 Diff accept/reject 必须保留。
- 风险：PTY 安全边界、附件/图片来源约束和 E2E 稳定性是主要风险点。

## 验收标准

- [x] `POST /api/runs` 的 text/file/agent/reference parts 能端到端进入 opencode 运行，不破坏旧 prompt。
- [x] Session 支持全局列表/搜索/重命名/置顶/删除，旧 workspace session API 继续可用。
- [ ] prompt composer 支持文件、图片、当前选区上下文和 busy follow-up 队列。
- [ ] Diff viewer 支持 Run/Session/VCS 来源、split/unified、文件/hunk 导航和选区上下文，只有 Run 级 accept/reject 可落盘。
- [ ] PTY WebSocket 只有在架构与安全文档合并后才进入实现。
- [ ] Playwright 覆盖 Phase 11 主流程并可在本地后端、前端、opencode server 联调环境通过。

## 验证方式

- 命令/操作：
  - `cd backend && mvn test`
  - `cd frontend && corepack pnpm typecheck`
  - `cd frontend && corepack pnpm test`
  - `cd frontend && corepack pnpm build`
  - `cd frontend && corepack pnpm e2e`
- 预期结果：所有单元、类型、构建和 E2E 验证通过；新增 API 文档、事件文档、README/PACKAGE 和安全/架构文档与实现一致。
