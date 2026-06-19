# 前后端契约规范

本文档定义完全自研前端与 `test-agent-app` 的调用边界。前端不得直连 opencode server，所有 HTTP 请求必须通过 `packages/backend-api`，所有实时事件必须通过 `packages/event-stream-client` 消费 `RunEvent SSE`。

## 契约来源

前后端契约以以下文档为准：

- `docs/api/backend-api.md`：HTTP API 路径、方法、请求、响应、错误码和兼容说明。
- `docs/api/event-stream-api.md`：RunEvent SSE 事件类型、字段、顺序、断线续传和兼容说明。
- `docs/architecture/dependency-rules.md`：前后端访问关系和分层依赖规则。

## HTTP Client

`packages/backend-api` 是前端访问后端的唯一入口。

必须负责：

1. 统一 base URL、鉴权头、traceId 和请求超时。
2. 统一解析成功响应和错误响应。
3. 将后端统一错误格式转换为前端可展示的错误对象。
4. 暴露稳定 TypeScript 方法，禁止页面组件直接拼接 URL。
5. 为 TanStack Query 提供稳定 query key 和 mutation 方法。

不得负责：

- 不得直连 opencode server。
- 不得保存 UI 面板状态。
- 不得引入 Monaco、Dockview 或具体业务组件。
- 不得吞掉后端错误导致用户无法看到失败原因。

当前 Runtime API 分组：

- Workspace：`POST/GET /api/workspaces`、文件单层列表、UTF-8 内容读写和文件状态。
- Session：创建、按 workspace 分页、详情、消息追加和消息分页。
- Run：启动、详情、取消。
- Diff：`GET /api/runs/{runId}/diff`、`POST /api/runs/{runId}/diff/accept`、`POST /api/runs/{runId}/diff/reject`。
- Event：`GET /api/runs/{runId}/events`，只消费平台 RunEvent SSE。

详细路径、请求和响应以 `docs/api/backend-api.md` 为准。

## Diff 契约

前端 Diff 闭环只能调用平台 Diff API，不能直连 opencode server。

1. `GET /api/runs/{runId}/diff` 用于刷新 Run 级变更列表和 Monaco Diff 输入。
2. `POST /api/runs/{runId}/diff/accept` 表示保留当前工作区变更并追加 `diff.accepted` 平台事件。
3. `POST /api/runs/{runId}/diff/reject` 表示通过后端封装的 opencode `sessionRevert` 回滚本次 Run 对应消息的变更并追加 `diff.rejected` 平台事件。
4. 后端缺少可回滚的 opencode `messageID` 时返回 `CONFLICT`，前端必须展示错误和 `traceId`，不得承诺已回滚。
5. Phase 08 不支持 per-file 后端回滚；当前文件接受/拒绝按钮只作为选择和反馈交互，真正落盘语义仍是 Run 级。

## RunEvent SSE Client

`packages/event-stream-client` 是前端消费实时事件的唯一入口。

必须负责：

1. 建立和关闭 RunEvent SSE 连接。
2. 处理 `Last-Event-ID` 断线续传。
3. 对重复事件做幂等保护。
4. 对缺失和未知事件给出可观测错误；重复事件必须按 `runId + seq` 幂等忽略。
5. 向上层输出类型化事件，不暴露原始解析细节。

不得负责：

- 不得直接修改 React 组件状态。
- 不得直接触发测试执行或 Diff 应用。
- 不得访问 opencode server。

## 类型映射

前端类型来源：

1. 后端 HTTP DTO 映射到 `packages/shared-types` 或 `packages/backend-api` 内部类型。
2. RunEvent 事件类型映射到 `packages/shared-types`。
3. 页面展示模型可以在 feature package 内单独定义，但必须由 API DTO 或 RunEvent 明确转换而来。

兼容要求：

- 新增字段必须默认可选，前端必须能处理旧响应缺字段。
- 废弃字段必须保留过渡期，前后端文档必须说明替代字段。
- 事件类型新增时，前端必须对未知事件有安全展示或忽略策略。Phase 08 已知 Diff 事件包括 `diff.proposed`、`diff.accepted` 和 `diff.rejected`。

## 错误映射

后端统一错误响应必须转换为前端错误对象，至少包含：

- `traceId`
- `code`
- `message`
- `retryable`
- `details`

页面展示要求：

1. 可重试错误必须提供重试入口。
2. 权限错误必须引导重新登录或申请权限。
3. 限流错误必须展示等待或稍后重试语义。
4. 系统错误必须展示 traceId，便于排查。
5. Diff 拒绝返回 `CONFLICT` 时必须说明缺少可回滚消息，不能把它当作普通网络重试。

## 文档同步

涉及以下变更时必须同步本文档：

- 新增或变更 HTTP API。
- 新增或变更 RunEvent SSE 事件。
- 修改错误格式。
- 修改鉴权、traceId、限流或重试规则。
- 修改 `backend-api` 或 `event-stream-client` 的边界。
