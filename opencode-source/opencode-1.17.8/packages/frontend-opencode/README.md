# frontend-opencode

> 基于 opencode 源码改造的并行实验前端。保留 opencode 原技术栈，**只替换与后端的交互层**，按平台现有架构经 `test-agent-app` 交互。

## 工程定位

本工程以 `opencode-source/opencode-1.17.8/packages/app` 为源码基础进行改造：

- **不改 opencode 技术栈**：UI、路由、状态、构建工具、组件库、测试链路全部沿用 opencode 原栈。
- **只改后端交互层**：把 opencode 直连 opencode server 的 SDK 调用与全局事件流，替换为经平台 `test-agent-app` 的 HTTP API 与 `RunEvent SSE`。
- **按现有架构交互**：交互落点对齐平台契约（`docs/api/`、`docs/frontend/frontend-backend-contract.md`），不另起一套后端协议。

### 对"前端完全自研"红线的已知例外

`docs/frontend/frontend-architecture.md` 与 `AGENTS.md` 规则 8 要求前端完全自研、不引入外部 Web 项目、不得直连 opencode server。**本工程是该红线的已知例外**，仅限实验范围，用于验证"opencode 原栈 + 平台后端"的可行性。正式自研前端仍以 `frontend/` 为准，本工程不替代它、不纳入正式发布边界。

### 与 `frontend/` 的定位差异

| 维度 | `frontend-opencode/`（本工程） | `frontend/`（正式自研） |
| --- | --- | --- |
| 来源 | opencode `packages/app` 改造 | 完全自研 |
| 技术栈 | Solid.js + Vite | Next.js + React |
| 状态 | 并行实验，红线例外 | 正式工程 |
| 后端 | 经适配层调用 `test-agent-app` | `packages/backend-api` + `event-stream-client` |
| 实时事件 | 适配层消费平台 `RunEvent SSE` | `packages/event-stream-client` |

## 技术栈（保持 opencode 原栈，不改）

- Solid.js + @solidjs/router
- Vite（dev/build/preview）
- @tanstack/solid-query
- @kobalte/core
- Tailwind CSS、shiki、marked
- effect、solid-primitives（storage、timer、event-bus、websocket 等）
- 构建/测试脚本沿用 opencode app 的 vite / bun test / playwright

## 工程结构

```text
frontend-opencode/            # 复制 packages/app 全量源码为主体
  src/                        # opencode app 源码（UI/pages/components/context 大部分保持不动）
  适配层/                      # 新增：替换 opencode SDK 与事件流的后端交互入口
```

- 复制 `opencode-source/opencode-1.17.8/packages/app` 全量源码为本工程主体。
- `@opencode-ai/sdk`、`@opencode-ai/ui`、`@opencode-ai/core` **仍外部引用 opencode 源码**，不内联、不手改。
- 新增"自带适配层"，承接所有与后端的交互，是本工程唯一允许改动后端语义的地方。

### 阶段一落地说明（源码剥离，连独立 opencode server）

为保留 opencode 原技术栈并使 `workspace:*`/`catalog:`/`patches/` 与 app 内按 `packages/<name>/` 深度计算的相对耦合（`sound.ts` 的 `../../../ui/...` glob、`public/` 与 `src/custom-elements.d.ts` 软链、`sst-env.d.ts` 引用）原样生效，本工程源码物理存放于 opencode 工作区内：

- 真实源码：`opencode-source/opencode-1.17.8/packages/frontend-opencode/`（由 `packages/app` 复制，包名改为 `frontend-opencode` 以避免与原 `@opencode-ai/app` 工作区重名）。
- 仓库根 `frontend-opencode/` 为指向上述目录的**符号链接**，故 `cd frontend-opencode` 与 `frontend-opencode/src/...` 访问照常可用，`process.cwd()` 解析为 in-tree 真实路径，bun 可定位 opencode workspace 根。
- 本阶段**未做适配层**：前端仍直连独立 opencode server（`localhost:4096`），这是后续阶段才替换的对象。

#### 本地运行（连独立 opencode server）

前置：安装 bun@1.3.14（匹配 opencode 根 `packageManager`）。

```bash
# 1. 安装依赖（在 opencode workspace 根，自动发现新成员 frontend-opencode）
cd opencode-source/opencode-1.17.8
bun install

# 2. 终端 A：独立 opencode server（:4096）
bun run --cwd packages/opencode --conditions=browser src/index.ts serve --port 4096

# 3. 终端 B：前端 dev server（:3001，默认连 :4096）
cd frontend-opencode
bun dev
```

打开 `http://localhost:3001`，dev 下 `src/entry.tsx` 的 `getCurrentUrl()` 默认返回 `http://localhost:4096`，无需额外环境变量。可选覆盖：`VITE_OPENCODE_SERVER_HOST`、`VITE_OPENCODE_SERVER_PORT`。

### 适配层阶段一：发送+回复闭环（已完成）

`src/adapter/` 实现发送按钮经平台后端 `test-agent-app`（:8080）而非 opencode server：

- **发送**：`src/components/prompt-input/submit.ts` 的 `promptAsync` 改为 `adapter.sendPrompt` → `POST /api/runs`。会话创建保持 opencode 原生（前端本地 sessionID = opencode `ses_X`），适配层在 `sendPrompt` 内懒创建平台 workspace+session，映射 `ses_X → 平台 ses_`。
- **回复**：`src/context/server-sdk.tsx` 暴露 `pushExternalEvent` 注入钩子；`event-bridge` 为每个 runId 开 `GET /api/runs/{runId}/events` SSE，`project.ts` 把 RunEvent 投影为 opencode Event（`type=payload.rawType`、`properties=payload` 去 `rawType`/`rawEventId`/`rawPayload`，**sessionID 重写**为前端本地 `ses_X`），喂给现有 reducer（不改 reducer）。
- **Vite 代理**：`vite.config.ts` 把 `/api` 代理到 :8080 并改写 `Origin` 为 `http://localhost:3000`（后端 CORS 默认仅放行 :3000）。
- **配置**：`VITE_PLATFORM_API_URL`（默认 `/api`）、`VITE_PLATFORM_API_TOKEN`（可选）、`VITE_PLATFORM_WORKSPACE_ROOT_PATH`、`VITE_PLATFORM_WORKSPACE_NAME`。

验证：发送 → `POST /api/workspaces`/`/sessions`/`/runs` 200 → SSE 订阅 → RunEvent 投影注入 reducer → assistant 回复（diff/文本）渲染，控制台无错误。

**已知限制（后续阶段解决）**：
- 保留 opencode 全局事件流（供会话列表）；后端服务端 opencode session 事件可能产生侧栏 phantom 条目，活动会话回复不受影响。红线"不得订阅 opencode 原生事件流"暂部分违反，完整适配层阶段整体替换全局流后消除。
- shell/command 发送、worktree 创建、diff/permission/question/tool 交互未适配。
- 平台合成事件（无 `rawType`，如 run.created/started、agent/model-switched）被投影跳过；assistant 文本流（`message.part.delta`）路径已实现但待文本类回复验证。

## 前后端交互边界（必须遵守）

> 本节是本工程的核心约束。无论怎么改 UI，以下边界不可越过。

### 红线

1. **前端不得直连 opencode server**，也不得启动/依赖 `opencode serve`。
2. 所有 HTTP 请求必须经适配层调用 `test-agent-app`，不得直接拼接 opencode URL。
3. 所有实时事件必须消费平台 `RunEvent SSE`，不得订阅 opencode 原生事件流。
4. 鉴权、traceId、超时、错误格式必须对齐平台统一规范（见下"契约来源"）。

### 改造点：opencode 边界文件 → 平台落点

以下 opencode 文件是与后端交互的唯一入口，是改造/替换对象，逐一对名替换：

| opencode 边界文件 | 原职责（直连 opencode） | 本工程改造为（平台落点） |
| --- | --- | --- |
| `@opencode-ai/sdk` `createOpencodeClient`（`packages/sdk/js` `v2/client.ts`） | 生成直连 opencode HTTP server 的 client，`baseUrl=server.url`，带 `x-opencode-directory`/`x-opencode-workspace` 头与 Basic auth | 适配层平台 client：`baseUrl` 指 `test-agent-app`，统一鉴权头、traceId、超时、统一错误映射 |
| `src/utils/server.ts` `createSdkForServer` | 注入 baseUrl 与 auth 的 SDK 工厂 | 适配层等价工厂，返回指向 `test-agent-app` 的平台 client |
| `src/context/server.tsx` | 多服务器连接管理、健康检查、项目列表、`ServerConnection`/`ServerScope` | 弱化为单一 `test-agent-app` 后端，移除多 server/SSH/container 语义 |
| `src/context/server-sdk.tsx` `eventSdk.global.event` | 全局事件流：消费 opencode `Event`，含心跳/重连/coalesce | 适配层消费平台 `RunEvent SSE`：断线续传 `?lastEventId=`、`runId+seq` 幂等、心跳与自动重连 |
| `src/context/sdk.tsx` | 目录级 SDK 解析 | 适配层映射为平台 Workspace/Session 上下文 |

### 不改实现但需对齐类型的部分

事件消费者 **保持实现不动**，但其依赖的 opencode `Event` 类型由适配层提供投影：

- `src/context/sync.tsx`
- `src/context/server-sync.tsx`
- `src/context/global-sync/`

适配层须把平台 `RunEvent` 投影为 opencode `Event` 形态（或建立明确双向映射表），使上述 reducer 无需改动即可工作。映射表随改造一起维护并同步到 `docs/frontend/frontend-backend-contract.md`。

### 契约来源（以平台现有契约为准）

- HTTP API：`docs/api/backend-api.md`、`docs/frontend/frontend-backend-contract.md` 的 Runtime API 分组（Workspace / Session / Run / Diff / Event 及 Phase 11 runtime 接口）。
- 实时事件：`docs/api/event-stream-api.md` 的平台 `RunEvent SSE`（断线续传、幂等、事件类型与兼容说明）。
- 错误格式：统一错误对象，至少包含 `traceId`、`code`、`message`、`retryable`、`details`。

### 不进入边界的范围（沿用 P2）

以下能力不在本工程默认边界内，新增前必须先补架构与安全文档例外：

- 交互式 PTY WebSocket（终端）。
- 公开 share 授权与公网分享轮询。
- settings / config / provider / server / MCP 安装配置页。

## 本地联调

后端启动 `test-agent-app`，前端启动 dev server，**不再启动 `opencode serve`**：

```bash
# 后端
cd backend
mvn spring-boot:run -pl test-agent-app -Dspring-boot.run.profiles=local

# 前端（在 frontend-opencode/ 内，沿用 opencode app 脚本）
# pnpm/bun dev（具体脚本随源码复制后确定）
```

## 文档同步要求

- 涉及 HTTP / `RunEvent SSE` 契约变更时，必须同步 `docs/api/` 与 `docs/frontend/frontend-backend-contract.md`。
- `RunEvent` ↔ opencode `Event` 映射表变更必须同步到 `docs/frontend/frontend-backend-contract.md`。
- 本 README 与 `frontend/README.md` 的定位差异表述必须保持一致。
- 完成前按 `docs/development/ai-self-checklist.md` 自检。
