# Phase 11：opencode Web App 功能复刻

## 阶段目标

在现有 React/Next.js 自研 Web IDE 中复刻 opencode Web App 的运行态能力，使 `agent-web` 具备接近 opencode Web App 的会话、消息、prompt parts、权限、提问、Agent/Model 选择、命令、上下文、Diff/review、bash 输出、Todo、MCP/LSP/VCS 状态等能力。

本阶段不迁移 opencode 的 Solid/TUI/Desktop 技术栈，不实现 settings/config 页面，不让前端绕过 `test-agent-app`、`backend-api` 和平台 RunEvent SSE 边界。

## 源码核查结论

opencode 1.17.8 的交互式 Web 功能主来源是 `opencode-source/opencode-1.17.8/packages/app`：

- `packages/app/src/app.tsx`：交互式 App 入口，组合 ServerSDK、ServerSync、Permission、Prompt、Terminal、File、Comments 等 provider。
- `packages/app/src/context/server-sdk.tsx`：直接消费 opencode `/api/event`，合并 `message.part.delta` 等高频事件。
- `packages/app/src/context/global-sync/event-reducer.ts`：消费 `session.created`、`session.updated`、`session.deleted`、`session.diff`、`todo.updated`、`session.status`、`message.updated`、`message.removed`、`message.part.updated`、`message.part.removed`、`message.part.delta`、`vcs.branch.updated`、`permission.asked/replied`、`question.asked/replied/rejected`、`lsp.updated` 等运行态事件。
- `packages/app/src/components/prompt-input/submit.ts` 与 `build-request-parts.ts`：构造 text/file/agent 等 prompt parts，处理 follow-up、command、shell、abort、worktree 等提交路径。
- `packages/app/src/pages/session/composer/session-permission-dock.tsx`、`session-question-dock.tsx`：权限审批和提问交互。
- `packages/app/src/pages/session/review-tab.tsx`：基于 SessionReview 的 split/unified diff review。
- `packages/app/src/pages/session/terminal-panel.tsx` 与 `context/terminal.tsx`：交互式 PTY 终端，使用 WebSocket。

`opencode-source/opencode-1.17.8/packages/web` 主要是官网、文档和 `/s/:id` 只读分享查看页，不是交互式 Web App。分享页只可作为 P2 只读 transcript 能力参考，不能复制其直连 `share_data` / `share_poll` 的公网 API 方式。

TUI、Desktop 代码只作为行为参考：可参考快捷键、消息操作、命令和 diff 行为，但不复刻终端 UI 框架、桌面 titlebar/updater/native 菜单、WSL/sidecar/SSH 等桌面能力。

## 范围

### 纳入范围

1. 会话：列表、搜索、详情、切换、tab、draft、置顶、重命名、删除、children、fork、abort、compact/summarize、revert/unrevert。
2. 消息：分页 timeline、message parts、流式 delta 合并、工具卡片、bash 输出、Todo、用量和 context 占比。
3. Prompt：text/file/agent/reference parts、附件/图片/文件上下文、排队 follow-up、slash command、shell 命令入口。
4. 权限与提问：permission dock、question dock、once/always/reject、reply/reject。
5. 运行态选择：Agent、Model、Provider 只读目录、Variant、模式切换。
6. 上下文与命令：命令面板、斜杠命令、`@` context picker、references、fs find/read/list、MCP resources/tools。
7. Diff/Review：Run diff、Session/message diff、VCS diff/status、split/unified、hunk/file navigation、评论或选区上下文。
8. 状态：MCP status、LSP status、VCS branch/status、浏览器通知。
9. 分享：P2 只读 `/s/:id` transcript 页面，由平台只读 API 提供数据。
10. 终端：P1 先实现 bash 工具输出；P2 才考虑交互式 PTY 面板。

### 排除范围

1. settings/config 页面。
2. Provider、server、MCP 安装配置与认证配置页面。
3. TUI、Desktop、native menu、titlebar、updater。
4. WSL、sidecar、SSH 等桌面/远端运行环境能力。
5. 前端直连 opencode server、opencode share 公网 API 或 generated SDK。
6. 未经架构和安全文档确认的通用 WebSocket 实时通道。

## 架构原则

1. 前端 HTTP 只能通过 `frontend/packages/backend-api` 调用平台后端服务，当前由 `test-agent-app` 装配运行。
2. 运行实时事件只能通过 `frontend/packages/event-stream-client` 消费平台 RunEvent SSE。
3. `test-agent-api` Controller 只做协议转换、参数校验和统一响应封装，不直接调用 Repository、generated SDK 或 opencode server。
4. `test-agent-opencode-runtime -> test-agent-opencode-client` 是 opencode 业务能力的调用链；generated DTO 不进入 app/domain/API 响应。
5. 新增 API、DTO、RunEvent、前端 shared types 均保持向后兼容：字段只追加、可选，未知事件可降级。
6. 先拆 `AgentWorkbench` 聚合逻辑，再下沉可复用能力到 feature packages，避免把 Phase 11 继续堆进单个页面组件。

建议前端包边界：

- `session-manager`：session list、tab、draft、children、fork、title/pin/delete。
- `agent-chat`：message timeline、part renderer、tool cards、prompt composer。
- `permission-prompt`：权限 dock 和 saved rule UI。
- `question-prompt`：提问 dock。
- `agent-model-selector`：Agent/Model/Provider/Variant 运行态选择。
- `command-palette`：命令面板和 slash command。
- `context-picker`：`@` 文件、引用、Agent、MCP resource 选择。
- `terminal`：bash 输出组件；P2 PTY 面板单独隔离。
- `diff-viewer`：diff source、split/unified、hunk/file navigation。

## 公共 API 与接口

### `POST /api/runs` 兼容扩展

保留旧请求体：

```json
{
  "sessionId": "ses_...",
  "prompt": "run prompt"
}
```

新增字段均可选：

```json
{
  "sessionId": "ses_...",
  "prompt": "fallback text for old clients",
  "parts": [
    { "type": "text", "text": "run prompt" },
    { "type": "file", "path": "src/App.tsx", "source": { "start": 10, "end": 20 } },
    { "type": "agent", "agentId": "build" }
  ],
  "messageId": "msg_client_or_existing",
  "agent": "build",
  "model": "anthropic/claude-sonnet-4-5",
  "variant": "default",
  "mode": "build"
}
```

后端兼容规则：

- `parts` 缺失时继续使用 `prompt` 构造 text part。
- `prompt` 保留到至少一个完整版本周期，不能立即废弃。
- `agent`、`model`、`variant`、`mode` 只影响本次运行或会话运行态选择，不进入 settings/provider 配置。

### Session 能力

P0/P1 需要逐步新增：

- `GET /api/sessions`：列表/搜索/分页/置顶过滤。
- `GET /api/sessions/{sessionId}`：详情。
- `PATCH /api/sessions/{sessionId}`：更新标题或置顶。
- `DELETE /api/sessions/{sessionId}`：删除。
- `GET /api/sessions/{sessionId}/messages`：消息分页。
- `GET /api/sessions/{sessionId}/children`：子会话。
- `GET /api/sessions/{sessionId}/diff`：session/message diff。
- `GET /api/sessions/{sessionId}/todo`：Todo。
- `POST /api/sessions/{sessionId}/fork`：fork，可选 messageId。
- `POST /api/sessions/{sessionId}/abort`：中断。
- `POST /api/sessions/{sessionId}/compact` 或 `/summarize`：压缩/总结。
- `POST /api/sessions/{sessionId}/revert`、`/unrevert`：撤销/重做。
- `POST /api/sessions/{sessionId}/command`：斜杠命令。
- `POST /api/sessions/{sessionId}/shell`：shell 命令。

### 运行态目录

新增只读或受控操作：

- `GET /api/agents`
- `GET /api/models`
- `GET /api/providers`
- `GET /api/commands`
- `GET /api/references`
- `GET /api/fs/find`
- `GET /api/fs/read`
- `GET /api/fs/list`
- `GET /api/vcs/diff`
- `GET /api/vcs/status`
- `GET /api/lsp/status`
- `GET /api/mcp/status`
- `GET /api/mcp/resources`
- `GET /api/mcp/tools`

Provider/MCP/server 的安装、认证、配置写操作暂不实现。

### 权限与提问

- `GET /api/sessions/{sessionId}/permissions`
- `POST /api/sessions/{sessionId}/permissions/{requestId}/reply`
- `GET /api/sessions/{sessionId}/questions`
- `POST /api/sessions/{sessionId}/questions/{requestId}/reply`
- `POST /api/sessions/{sessionId}/questions/{requestId}/reject`

所有响应必须使用统一 `ApiResponse<T>` / `ApiErrorResponse`，带 traceId，不暴露 opencode raw DTO。

### 分享页

`/s/:id` 是 P2，只读分享页通过平台 API 提供 transcript：

- `GET /api/share/{shareId}` 或等价只读 transcript API。
- 不直连 opencode `share_data`。
- 不使用 opencode `share_poll` WebSocket；若需要实时刷新，仍优先使用平台 SSE 或轮询。

### PTY 终端

P1 只实现 bash 工具输出卡片。

P2 交互式 PTY 需要先在 `docs/architecture/` 和 `docs/security/security-standards.md` 明确受控 WebSocket 例外，包括：

- token 获取和过期策略。
- 输入输出审计和脱敏。
- resize/input/close 操作权限。
- 每 workspace/session 的隔离和限流。
- CORS、鉴权、traceId 与日志规则。

未完成上述文档和安全确认前，不新增 PTY WebSocket。

## RunEvent 类型

现有事件保留：`run.*`、`assistant.message.delta`、`tool.*`、`diff.*`、`test.finished`、`opencode.event.unknown`。

Phase 11 新增事件优先映射 opencode App 实际消费的运行态事件：

| wire name | 来源 | 用途 | 优先级 |
|---|---|---|---|
| `message.updated` | opencode App global sync | 更新 message projection | P0 |
| `message.removed` | opencode App global sync | 移除 message projection | P0 |
| `message.part.updated` | opencode App global sync | 更新 part 状态和内容 | P0 |
| `message.part.removed` | opencode App global sync | 移除 part | P0 |
| `message.part.delta` | opencode App global sync | 流式 delta 合并 | P0 |
| `session.diff` | opencode App global sync | session diff 状态 | P1 |
| `session.status` | opencode App global sync | session busy/idle/status | P0 |
| `todo.updated` | opencode App global sync | Todo 列表更新 | P1 |
| `permission.asked` | opencode App permission provider | 权限请求 | P0 |
| `permission.replied` | opencode App permission provider | 权限回复 | P0 |
| `question.asked` | opencode App question provider | 提问请求 | P0 |
| `question.replied` | opencode App question provider | 提问回复 | P0 |
| `question.rejected` | opencode App question provider | 提问拒绝 | P0 |
| `vcs.branch.updated` | opencode App global sync | 当前分支更新 | P1 |
| `lsp.updated` | opencode App global sync | LSP 状态更新 | P2 |
| `mcp.tools.changed` | opencode App global sync | MCP 工具目录更新 | P2 |

兼容要求：

- 旧前端仍可只消费 `assistant.message.delta`、`tool.*` 和 `diff.*`。
- 前端必须监听新增事件，但未知事件仍可安全忽略或展示为 `opencode.event.unknown`。
- 后端 mapper 必须保留 rawType/rawEventId 等安全排查信息，但不得把密钥、token、绝对敏感路径直接暴露给前端。
- 浏览器 EventSource 首次续传可使用 `?lastEventId=`，后端仍保留 `Last-Event-ID` header 兼容。

## shared-types 基础模型

前端 `shared-types` 需要新增并持续收敛以下分类型模型：

- `PromptPart`：text/file/agent/reference。
- `AgentMessage`：保留旧 `text` 字段，新增 `parts`。
- `MessagePart`：text/reasoning/tool/file/event。
- `ToolPart`：toolName、callId、status、input、output、metadata。
- `PermissionRequest`
- `QuestionRequest`
- `AgentInfo`
- `ModelInfo`
- `CommandInfo`
- `SessionDiff`
- `TodoItem`
- `RuntimeStatus`

这些类型是平台 DTO projection，不是 generated SDK DTO。

## 实施批次

### P0：Contract / Facade Prework

1. 扩展 `test-agent-opencode-client` facade，按 pinned OpenAPI 增加 session、message、permission、question、agent/model、command、fs、vcs、mcp、lsp 等操作的受控封装。
2. 扩展 `RunEventType` 和 `OpencodeRunEventMapper`，覆盖 opencode App 实际消费事件；保留旧事件兼容。
3. 扩展 `shared-types`、`event-stream-client`、`backend-api` 的基础类型和方法。
4. 更新 `docs/api/backend-api.md`、`docs/api/event-stream-api.md`、`docs/frontend/frontend-backend-contract.md`。
5. 增加 generated SDK 使用边界测试：app/domain/API 不依赖 generated DTO。

### P0：Session + Message Core

1. 拆分 `AgentWorkbench` 中 session、run、chat、diff 的聚合逻辑。
2. 实现 session 首页/列表/切换/tab/draft。
3. 实现消息分页和 message part timeline。
4. 实现 `message.part.delta` 合并、自动滚动、旧 `assistant.message.delta` 兼容。
5. 增加 reducer 单测和核心 Playwright 流程。

### P0：Prompt + Approval

1. 实现 prompt parts 构造和提交。
2. 支持附件、图片、文件上下文、当前编辑器选区上下文。
3. 支持忙碌状态 follow-up 排队和 abort。
4. 实现 permission dock 和 question dock。
5. 权限与提问 API、事件和 UI 测试必须成套提交。

### P1：Runtime Controls

1. Agent/Model/Provider 只读列表。
2. Agent/Model/Variant 运行态选择。
3. 命令面板、slash command、`@` context picker。
4. Todo 展示。
5. usage/context 占比展示。

### P1：Diff / Review

1. Diff 来源切换：Run、Session/message、VCS 工作树。
2. split/unified、hunk/file navigation。
3. message diff、评论/选区上下文。
4. Run 级 accept/reject 保持兼容；按文件/按消息操作必须等后端语义明确后再开放。

### P2：Advanced Web

1. MCP 状态、资源和工具。
2. LSP 状态和诊断。
3. 浏览器通知。
4. 只读分享页。
5. 交互式 PTY 终端，仅在 WebSocket 例外获文档确认后实施。

## 测试计划

后端：

- facade/gateway 测试覆盖新增 opencode 操作、超时、错误映射、traceId。
- API 集成测试覆盖统一响应、鉴权、限流、兼容字段。
- 事件 mapper 测试覆盖已知事件和 unknown 降级。
- generated SDK 边界测试覆盖 app/domain/API 不返回 generated DTO。

前端：

- vitest 覆盖 prompt parts 构造、event reducer、message part 渲染、权限/提问交互、Diff 来源切换、command 和 `@` picker。
- `event-stream-client` 覆盖新增事件订阅、重复事件去重、query/header 续传兼容和关闭订阅。
- `backend-api` 覆盖新增 API 方法路径、请求体和错误映射。

E2E：

- Playwright 覆盖新建/继续会话、发送 prompt parts、流式消息、工具卡片、权限审批、提问回复、Diff 查看与拒绝、命令执行、上下文引用。

文档：

- 同步 API、SSE、前端架构、README/PACKAGE.md。
- 涉及 PTY WebSocket 或分享页时同步安全与架构说明。

## 验收标准

1. 前端不直连 opencode server，不直连 generated SDK，不绕过平台 API 和 RunEvent SSE。
2. settings/config/provider/server/MCP 安装配置不进入 Phase 11 运行态复刻范围。
3. P0 完成后，旧 prompt string 和旧事件仍可工作，新 message part timeline 可消费新增事件。
4. P1 完成后，Agent/Model、命令、上下文、Todo、Diff review 可以支撑日常 Web IDE 工作流。
5. P2 完成前，分享页和 PTY 终端不得影响 P0/P1 主路径稳定性。
6. 每批完成时必须更新稳定文档并执行对应后端、前端和 E2E 验证。

## 假设

1. 默认采用“保留运行态、排除配置页”：Agent/Model 选择属于运行态，Provider/MCP/server 安装配置属于 settings，暂不复刻。
2. 默认把只读分享页放入 P2，而不是核心 P0。
3. 不手改 generated SDK；如 pinned spec 变化，只通过 `tools/generate-opencode-java-sdk.sh` 更新。
4. 非 Run 级全局同步先用 HTTP/TanStack Query 补齐；只有 agent/run 过程实时输出进入 RunEvent SSE。
