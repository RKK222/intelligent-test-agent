# PTY WebSocket 受控例外设计

本文档定义 Phase 11 P2 交互式 PTY 终端的架构和安全边界。后端已落地 ticket + WebSocket 受控通道、限流、审计、timeout 和输出截断，前端已接入 `packages/terminal` 面板；真实三服务联调验收前，默认 Web App 仍只能把它视为 P2 能力。

## 目标

- 为 Web IDE 提供 session/workspace 绑定的交互式终端。
- 前端只连接 `test-agent-app` 提供的平台 WebSocket，不直连 opencode server、SSH、sidecar 或任意主机。
- 后端只在应用服务层通过受控 PTY 代理执行输入、resize 和 close，不把终端通道下沉到 generated SDK 或 Repository。

## 非目标

- 不提供 settings/config/server/provider 页面。
- 不提供任意远程 SSH、WSL、sidecar 管理能力。
- 不复刻桌面 native menu、titlebar、updater 或 TUI UI 框架。
- 不允许前端保存长期终端 token。

## 协议入口

P2 后端已新增以下平台接口：

- `POST /api/sessions/{sessionId}/terminal/tickets`
  - 生成一次性 PTY ticket。
  - 请求体可包含 `workspaceId`、`cwd`、`shell`、`cols`、`rows`。
  - 响应仍使用 `ApiResponse<T>`，包含 `ticket`、`expiresAt`、`webSocketUrl`、`traceId`。
  - 当前 `shell` 暂不允许前端覆盖；后端只使用运行环境默认 shell。
- `GET /api/sessions/{sessionId}/terminal/ws?ticket=...`
  - 仅允许 WebSocket upgrade。
  - ticket 单次使用，默认 60 秒过期。

新平台 URL 并行保留同一能力：

- `POST /api/internal/platform/opencode-runtime/sessions/{sessionId}/terminal/tickets`
- `GET /api/internal/platform/opencode-runtime/sessions/{sessionId}/terminal/ws?ticket=...`

通过新平台 ticket URL 创建 ticket 时，响应 `webSocketUrl` 返回新 WebSocket path；通过旧 URL 创建时继续返回旧 path。

WebSocket message 使用 JSON envelope：

```json
{ "type": "input", "data": "npm test\n" }
{ "type": "resize", "cols": 120, "rows": 32 }
{ "type": "close", "reason": "user" }
{ "type": "output", "data": "...", "seq": 12 }
{ "type": "exit", "code": 0, "seq": 13 }
{ "type": "error", "code": "PTY_DENIED", "message": "..." }
```

## 鉴权与授权

1. ticket 创建接口必须走现有 Bearer token 占位鉴权和 traceId 过滤器。
2. WebSocket upgrade 必须校验 ticket、sessionId、workspaceId、过期时间和单次使用状态。
3. ticket 只能绑定一个 session、一个 workspace、一个 execution node 和一个 traceId。
4. session 不属于 workspace、session 已删除、workspace root 不存在或无远端 opencode session 映射时，必须拒绝创建 ticket。
5. 前端不得把 ticket 写入 localStorage、URL 历史或持久化状态；ticket 只在内存中短期使用。

## 隔离与执行目录

1. PTY cwd 必须归一化到 workspace root 内，越界返回 `FORBIDDEN`。
2. 默认 cwd 为 workspace root；用户传入 cwd 只能是 workspace root 下相对路径或等价规范化路径。
3. shell 白名单由后端配置控制，默认只允许系统默认 shell；不得从前端直接传任意可执行文件路径。
4. 每个 session 同时最多一个 active PTY；当前固定策略为拒绝新的 WebSocket 连接，返回 `CONFLICT` error envelope，并保留已有 PTY。
5. PTY 进程必须在 close、WebSocket 断开、ticket 过期、session abort 或后端关闭时被清理。

## 限流与配额

1. ticket 创建的用户维度由平台鉴权和入口 HTTP 限流承接；应用内按 session/workspace 维度限流。
2. WebSocket input 按连接限速，单条 input 默认不超过 16KB。
3. output 必须有背压和截断策略；单连接内存缓冲默认不超过 1MB。
4. idle timeout 默认 10 分钟，hard timeout 默认 2 小时。
5. resize 事件按连接节流，避免拖拽窗口时放大后端压力。

## 审计与脱敏

1. ticket 创建、upgrade 成功、upgrade 拒绝、input、resize、close、exit 和 timeout 都必须记录结构化审计日志。
2. input 审计默认只记录长度和事件类型；output 审计只记录截断 warning、exit 状态等生命周期信息；不得记录完整交互内容。
3. Authorization、Cookie、ticket、API key、token、私钥片段必须脱敏。
4. traceId 必须贯穿 ticket 创建、WebSocket upgrade 和 PTY 生命周期。
5. 输出推送给前端前应复用平台脱敏工具处理明显密钥模式；脱敏失败不能写入服务端日志。

## 前端边界

1. `packages/backend-api` 只负责创建 ticket，不直接管理 WebSocket 生命周期。
2. 交互式终端应新增独立 `packages/terminal`，负责 WebSocket 连接、输入、resize、关闭和输出渲染。
3. `packages/terminal` 不得调用 opencode server、不得保存 token、不得访问 generated SDK。
4. `apps/agent-web` 只组合 terminal panel 和 ticket mutation。
5. 未连接或被拒绝时，前端必须显示统一错误和 traceId；不得自动降级为直连 opencode。

## 错误语义

- `UNAUTHENTICATED`：未通过平台鉴权。
- `FORBIDDEN`：session/workspace 不匹配、cwd 越界或 shell 不允许。
- `CONFLICT`：session 无远端上下文、已有 active PTY 且策略为拒绝。
- `RATE_LIMITED`：ticket 或 WebSocket input 超限。
- `PTY_UNAVAILABLE`：PTY 后端不可用或 execution node 不支持。

所有 HTTP 错误继续使用 `ApiErrorResponse`。WebSocket 内错误使用 `error` envelope，并在严重错误后关闭连接。

## 验收要求

- 后端 ticket controller、application service、WebSocket handler 和 PTY adapter 均有单元或集成测试；当前已覆盖 ticket 创建、无远端映射拒绝、cwd 越界、重复使用、ticket 创建限流和消息 codec。
- 安全测试覆盖 ticket 过期、重复使用、session/workspace 不匹配、cwd 越界、active PTY 冲突、进程启动失败释放、input/resize 限速、非法 envelope、idle/hard timeout 和 CORS/origin 拒绝。
- 前端测试覆盖 ticket 获取、连接失败、输出渲染、输入发送、resize、close、error 和 warning；当前已覆盖 terminal client/panel 和 mocked ticket 创建入口。
- 文档同步 `docs/api/backend-api.md`、`docs/security/security-standards.md`、`docs/frontend/frontend-backend-contract.md` 和相关 README/PACKAGE。
