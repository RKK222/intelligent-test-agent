# Phase 04 后端 API 运行时详细设计

## 目标

Phase 04 在 `test-agent-app` 中暴露前端后续可消费的 Runtime API。入口层只负责 HTTP/SSE 协议转换、参数校验、traceId 和统一响应；应用服务负责编排 domain、persistence、event 和 opencode facade。Controller 不直接访问 Repository，不直接调用 generated SDK。

## API 边界

- 所有业务 API 使用 `/api` 前缀，成功响应统一包装为 `ApiResponse<T>`，错误由 `GlobalExceptionHandler` 输出 `ApiErrorResponse`。
- `X-Trace-Id` 由 `TraceIdWebFilter` 生成或透传，并写入响应头和响应体。
- `ApiTokenWebFilter` 是鉴权占位：未配置 `test-agent.security.api-token` 时放行；配置后要求 `Authorization: Bearer <token>`。
- `InMemoryRateLimitWebFilter` 是限流占位：默认关闭或宽松，本阶段用于验证统一 `RATE_LIMITED` 响应和后续替换边界。
- CORS、基础安全响应头统一在 app 配置中处理，生产环境允许来源只能来自配置。

## Workspace API

- `POST /api/workspaces` 注册本地工作区，请求体包含 `name`、`rootPath`。`rootPath` 必须是存在目录，返回 Workspace DTO。
- `GET /api/workspaces?page=&size=` 分页列出工作区；`size` 使用 `PageRequest.MAX_SIZE` 限制。
- `GET /api/workspaces/{workspaceId}` 查询工作区详情，不存在返回 `NOT_FOUND`。
- `GET /api/workspaces/{workspaceId}/files?path=` 单层列目录，默认 root，相对路径归一化后必须仍位于 workspace root 下；单次最多返回 1000 项。
- `GET /api/workspaces/{workspaceId}/files/content?path=` 读取 UTF-8 文本文件，默认最大 1MB，可配置。
- `PUT /api/workspaces/{workspaceId}/files/content` 保存 UTF-8 文本文件，请求体包含 `path`、`content`。
- `GET /api/workspaces/{workspaceId}/files/status?path=` 返回文件存在性、类型、大小和最后修改时间。初版不计算 Git 状态。

## Session 与 Run API

- 新增 `SessionMessage` 领域模型、`SessionMessageRepository` 端口和 `session_messages` 表，用于平台保存用户消息和后续助手消息索引。
- `POST /api/sessions` 创建会话，请求体包含 `workspaceId`、`title`。
- `GET /api/workspaces/{workspaceId}/sessions?page=&size=` 分页查询工作区会话；`GET /api/sessions/{sessionId}` 查询详情。
- `POST /api/sessions/{sessionId}/messages` 追加消息，初版允许 `USER`、`ASSISTANT`、`SYSTEM` 三类角色；`GET /api/sessions/{sessionId}/messages` 分页读取。
- `POST /api/runs` 接收平台 `sessionId` 和 `prompt`，创建 Run、追加用户消息、追加 `run.created`；首次 Run 先路由执行节点并通过 `OpencodeClientFacade.createSession` 创建远端 opencode session，保存内部映射后再调用 `OpencodeClientFacade.startRun`，后续 Run 复用远端 session 和原 execution node。成功后 Run 转为 `RUNNING` 并追加 `run.started`。
- `GET /api/runs/{runId}` 查询 Run 状态。
- `POST /api/runs/{runId}/cancel` 对 `PENDING/RUNNING/CANCELLING` 以外状态返回 `CONFLICT`；可取消状态调用 `cancelSession`，追加取消事件并保存终态。

## 事件流

- `GET /api/runs/{runId}/events` 输出 `text/event-stream`。
- `Last-Event-ID` 解析由 `RunEventReplayService` 负责，非法值抛出 `VALIDATION_ERROR`。
- SSE `id` 固定为 RunEvent `seq`，`event` 固定为 `RunEventType.wireName()`。
- 初版 SSE 使用 repository polling 增量读取，避免只依赖单机内存广播；每次轮询按 `seq > lastSeq` 读取有限数量事件。
- opencode stream 订阅由 Run 启动服务异步消费，转换后的 `RunEventDraft` 通过 `RunEventAppender` 落库。未知 opencode 事件仍按 Phase 03 策略降级为 `opencode.event.unknown`。

## 验收

- Controller 测试覆盖成功响应、参数错误、鉴权失败、限流、NOT_FOUND、CONFLICT、traceId 和统一错误格式。
- 应用服务测试覆盖 workspace/session/run/cancel 编排和 opencode facade 错误映射。
- 文件服务测试覆盖路径穿越拒绝、目录列表、UTF-8 读写和大文件限制。
- SSE 测试覆盖连接、`Last-Event-ID` 续传、非法续传 ID 和断开释放。
- 依赖边界检查确认 app/domain/event/persistence 不 import generated SDK。
