# 04 History API Plan

## 目标

提供 Run 当前子树和 Session 全量历史树两类读取能力，并让 SSE snapshot 可按 Run scope 恢复 root + child messages。

## 已落地

### Run 当前子树 HTTP snapshot

主路径：

```text
GET /api/internal/agent/{agentId}/runs/{runId}/session-tree/messages
```

兼容路径：

```text
GET /api/internal/platform/opencode-runtime/runs/{runId}/session-tree/messages
GET /api/runs/{runId}/session-tree/messages
```

响应 DTO：

- `runId`
- `sessions`
- `messagesBySessionId`
- `events`

实现：

- Controller 复用 `RunMessageRecoveryService`。
- scope 表存在时按 root + child session 拉取 projected messages。
- scope 表为空时 fallback 到旧 root-only 远端 session。

### SSE snapshot 恢复

- `RunMessageRecoveryService` 已按 Run scope 恢复 root + child projected messages。
- snapshot payload 带 `rootSessionId/sessionId/parentSessionId/isChildSession`。
- user message 不重复回放，避免前端误拼 assistant 内容。

### Session 全量历史树 HTTP snapshot

主路径：

```text
GET /api/internal/agent/{agentId}/sessions/{sessionId}/session-tree/messages
```

兼容路径：

```text
GET /api/internal/platform/opencode-runtime/sessions/{sessionId}/session-tree/messages
GET /api/sessions/{sessionId}/session-tree/messages
```

语义：

- 输入平台 `sessionId`，通过 `AgentSessionBinding` 找 root remote session。
- 通过 `run_session_scope_sessions.root_session_id` 获取 root 下跨 Run 已发现过的 child session。
- 不按 Run scope 过滤，适合历史浏览器视图。
- 返回 DTO 与 Run 当前子树接口兼容，顶层标识为 `sessionId`。

后续增强：

- child discovery 批次补齐 `session.children` bootstrap 和 task tool metadata 后，该接口会自然覆盖更多历史 child。
- 如需展示未被任何 Run scope 记录过的历史 child，再评估直接调用 agent children API 补全。

## 兼容策略

- 新主路径走 `/api/internal/agent/{agentId}/...`。
- 旧 `/api/runs/...` 只用于兼容，不作为新前端默认入口。
- HTTP snapshot 是辅助恢复接口；实时仍以 RunEvent SSE 为准。
