# Phase 11 未完成内容完成计划

## 背景

- 用户问题：在已提交 Phase 11 主路径能力后，需要明确剩余未完成内容，并形成后续可执行计划。
- 当前现象：最近提交已完成 opencode Web runtime 主链路、PromptPart 后端透传、Session 全局搜索/置顶/软删除、runtime selector、permission/question、Todo、Diff 来源切换、MCP/LSP/VCS 状态和只读 transcript；本批次补齐了文件/图片附件、busy follow-up 本地 FIFO 队列、Diff hunk 导航、hunk context 和 Monaco 任意选区上下文；仍缺 PTY WebSocket 安全前置和 Playwright E2E 闭环。
- 目标：按 P0/P1/P2 风险顺序补完 Phase 11，保持前端只走 `backend-api` 和 RunEvent SSE、后端只走 `test-agent-opencode-client` facade 的边界。

## 范围

- 包含：prompt composer 附件/图片、Diff hunk/file 导航与选区上下文、PTY 架构安全设计、Playwright E2E、对应文档和测试。
- 不包含：settings/config/provider/server 配置页、MCP 安装认证配置 UI、前端直连 opencode server、opencode 公网 share API、未经过安全设计的通用 WebSocket。

## 现状分析

- 相关文件：
  - `frontend/packages/agent-chat/src/AgentChat.tsx`
  - `frontend/apps/agent-web/src/components/AgentWorkbench.tsx`
  - `frontend/packages/diff-viewer/src/DiffViewer.tsx`
  - `frontend/packages/shared-types/src/index.ts`
  - `frontend/packages/backend-api/src/index.ts`
  - `docs/api/backend-api.md`
  - `docs/frontend/frontend-backend-contract.md`
  - `docs/security/security-standards.md`
- 当前实现：
  - `PromptPart` 类型已支持 `text/file/agent/reference`，`AgentWorkbench` 会把当前打开文件内容作为 file part 追加到 `POST /api/runs`。
  - `AgentChat` 已支持文件选择、图片 data URL、附件 chips；`AgentWorkbench` 已在 run 忙碌时把 follow-up 放入本地 FIFO 队列，终态后自动提交下一条。
  - `DiffViewer` 已支持 Run/Session/VCS 来源与 split/unified 视图，但 hunk 导航、选区引用和可测试的 hunk 模型尚未补齐。
  - PTY 目前只保留 bash 工具输出卡片方向，尚未在架构和安全文档中声明受控 WebSocket 例外。
- 问题原因：前期优先完成 API、事件和主 UI 串联，剩余项涉及浏览器文件读取、运行队列语义、Diff 交互状态和双向终端安全边界，需要拆批次补齐并分别验收。

## 修改方案

### 1. Prompt 附件、图片和 busy follow-up（已完成）

- 修改文件：
  - `frontend/packages/agent-chat/src/AgentChat.tsx`
  - 新增 `frontend/packages/agent-chat/src/prompt-parts.ts`
  - 新增或扩展 `frontend/packages/agent-chat/tests/prompt-parts.test.ts`
  - 新增 `frontend/apps/agent-web/src/components/follow-up-queue.ts`
  - 新增 `frontend/apps/agent-web/tests/follow-up-queue.test.tsx`
  - `frontend/apps/agent-web/src/components/AgentWorkbench.tsx`
  - `frontend/packages/agent-chat/README.md`
  - `frontend/packages/agent-chat/src/PACKAGE.md`
  - `docs/frontend/frontend-backend-contract.md`
- 修改位置：
  - `AgentChat` prompt composer 的提交、文件 input、附件 chips、发送按钮状态。
  - `AgentWorkbench.handleSend()`、`buildPromptParts()` 和 run 终态处理。
- 具体改动：
  - 抽出 `fileToPromptAttachment(file)`：文本文件读取为 `{ type: "file", name, mimeType, content }`；图片读取为 `{ type: "file", name, mimeType, url: "data:<mime>;base64,..." }`；二进制文件默认只传 name/mimeType/url，不把不可读内容塞进 text。
  - `AgentChat` 增加附件选择、图片选择、删除附件和附件数量/名称展示；提交时把附件 parts 交给父组件，不直接调用 HTTP。
  - `AgentWorkbench` 合并文本、当前编辑器上下文、附件 parts；当 run 处于 `queued/running/cancelling` 时把 follow-up 存入本地 FIFO 队列，当前 run 到终态后自动提交下一条。
  - abort 继续复用现有 cancel/abort API；排队消息需要在 UI 中立即出现为用户消息，但出队执行时不得重复插入。
- 原因：补齐 opencode Web App 的多 part prompt 与 busy follow-up 行为，同时继续保持前端不直连 opencode。

### 2. Diff Review hunk 导航和选区上下文（hunk 已完成）

- 修改文件：
  - `frontend/packages/diff-viewer/src/DiffViewer.tsx`
  - 新增 `frontend/packages/diff-viewer/src/hunks.ts`
  - 新增 `frontend/packages/diff-viewer/tests/hunks.test.ts`
  - `frontend/apps/agent-web/src/components/AgentWorkbench.tsx`
  - `frontend/packages/diff-viewer/README.md`
  - `frontend/packages/diff-viewer/src/PACKAGE.md`
  - `docs/frontend/frontend-backend-contract.md`
- 修改位置：
  - diff 文件列表、toolbar、Monaco diff editor mount 回调、当前文件反馈入口。
- 具体改动：
  - 从 unified patch 解析 hunk 列表，生成 `{ filePath, oldStart, oldLines, newStart, newLines, heading }` 的轻量模型。
  - Diff toolbar 增加上一处/下一处 hunk 导航，保持 split/unified 两种视图都能定位。
  - 将当前 hunk 或当前选区转换为 `PromptPart` file source context，交给 `AgentWorkbench` 追加到下一次 prompt。
  - 继续只暴露 Run 级 accept/reject 为落盘动作；当前文件 accept/reject 按现有 UI 只给意图反馈，不调用未定义后端 API。
- 原因：满足 P1 Diff/review 可用性，避免提前制造 per-file/per-message 回滚语义。

### 3. PTY WebSocket 安全前置和 P2 实施

- 修改文件：
  - 新增 `docs/architecture/pty-websocket-design.md`
  - 更新 `docs/security/security-standards.md`
  - 更新 `docs/api/backend-api.md`
  - 后续新增后端 PTY controller/service 和前端 terminal package。
- 修改位置：
  - 架构文档先定义 PTY ticket、鉴权、限流、审计、脱敏、输入输出协议、resize/input/close 生命周期。
  - 安全文档加入 workspace/session 隔离、CORS、traceId、日志和敏感输出处理。
- 具体改动：
  - 第一批只提交架构和安全文档，不写 PTY 代码。
  - 文档合并后再实现受控 WebSocket：后端只暴露 session/workspace 绑定的 PTY 通道，前端 terminal package 只消费平台 WebSocket，不连接 opencode server。
  - 未完成文档前继续只展示 bash 工具输出卡片。
- 原因：PTY 是双向命令通道，必须先满足 Phase 11 计划要求的安全例外。

### 4. Playwright E2E 和阶段验收闭环

- 修改文件：
  - `frontend/apps/agent-web/tests/workbench.e2e.spec.ts`
  - `frontend/apps/agent-web/playwright.config.ts` 或现有 E2E 配置文件
  - 后端 runtime/controller 集成测试
  - `docs/frontend/frontend-testing-standards.md`
  - `docs/development/ai-self-checklist.md` 按需补充 Phase 11 验收项
- 修改位置：
  - 本地联调启动脚本、workbench 主流程、mock opencode server 事件流。
- 具体改动：
  - 覆盖新建/继续会话、发送 prompt parts、流式 message part、工具卡片、permission once/always/reject、question reply/reject、abort、Diff 来源切换、slash command、`@` 引用、只读 transcript。
  - P2 完成后追加 MCP/LSP 状态、浏览器通知和 PTY 场景。
  - 每个 E2E 场景只验证平台 API 和 RunEvent SSE，不允许前端测试依赖 opencode 公网 API。
- 原因：把 Phase 11 从“功能已接入”推进到“可重复验收”。

## 影响范围

- UI/交互：prompt composer、AgentChat timeline、AgentWorkbench 运行状态、DiffViewer toolbar、后续 terminal panel。
- 数据/协议：不新增数据库字段；附件和选区继续复用现有 `PromptPart`；PTY 文档通过后才新增 WebSocket 协议。
- 兼容性：旧 `prompt: string`、旧 `assistant.message.delta`、Run 级 Diff accept/reject、只读 transcript API 均继续保留。
- 风险：图片 data URL 体积、二进制附件大小限制、follow-up 出队时机、Monaco hunk 定位稳定性、PTY 输入输出审计和 E2E 环境稳定性。

## 验收标准

- [x] prompt composer 支持文本文件、图片附件、删除附件和当前编辑器上下文，提交 payload 中包含正确 `PromptPart`。
- [x] run 忙碌时 follow-up 按 FIFO 排队，当前 run 终态后自动提交下一条，UI 不重复插入用户消息。
- [x] Diff viewer 支持 Run/Session/VCS 来源、split/unified、文件导航、hunk 导航和 hunk context；只有 Run 级 accept/reject 触发后端落盘 API。
- [x] Monaco 任意文本选区可转换为下一条 Prompt 的 file context。
- [ ] PTY 代码实现前，`docs/architecture/pty-websocket-design.md` 与 `docs/security/security-standards.md` 已定义安全例外。
- [ ] Playwright 覆盖 Phase 11 主流程，本地前端、后端、opencode server 联调环境可执行。
- [ ] 每批改动同步 README/PACKAGE、API、前后端契约和测试说明文档。

## 验证方式

- 命令/操作：
  - `cd backend && mvn test`
  - `cd frontend && corepack pnpm typecheck`
  - `cd frontend && corepack pnpm test`
  - `cd frontend && corepack pnpm build`
  - `cd frontend && corepack pnpm e2e`
  - `git diff --check`
- 预期结果：
  - 后端单元和集成测试通过。
  - 前端类型检查、Vitest、构建和 E2E 通过。
  - 新增 API、事件、架构、安全和包级文档与实现一致。
  - `test-agent-app`、`backend-api`、RunEvent SSE 和 `test-agent-opencode-client` facade 边界没有被绕过。
