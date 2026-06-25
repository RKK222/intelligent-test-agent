# opencode Web API 映射

| opencode App 能力 | frontend-opencode 调用 | 平台后端映射 |
|---|---|---|
| Agent/Model/Provider catalog | `backend-api.listAgents/listModels/listProviders` | `/api/agents`、`/api/models`、`/api/providers` |
| Command/Reference catalog | `backend-api.listCommands/listReferences` | `/api/commands`、`/api/references` |
| File context picker / Files side panel | `listRuntimeFiles/findRuntimeFiles/readRuntimeFile` | `/api/fs/list`、`/api/fs/find`、`/api/fs/read` |
| VCS/LSP/MCP status | `getVcsStatus/getVcsDiff/getLspStatus/getMcpStatus/getMcpResources/getMcpTools` | `/api/vcs/*`、`/api/lsp/status`、`/api/mcp/*` |
| Prompt submit runtime selection | `startRun({ parts, agent, model, variant })` | `/api/runs`；Agent/Model/Variant 是运行态参数，不进入普通 prompt part；图片附件随 `parts` 以 `file` part 的 `mimeType/url` 发送 |
| Refresh running session recovery | `getSessionMessages` + `getActiveRun(sessionId)` + `subscribeRunEvents(activeRun.runId)` | `/api/sessions/{sessionId}/messages` 优先刷新 opencode 投影并落库，失败时返回数据库快照；`/api/sessions/{sessionId}/active-run` 返回最新非终态 Run；前端先展示存量消息，再订阅 RunEvent SSE 增量 |
| Session children/todo/diff | `getSessionChildren/getSessionTodo/getSessionDiff` | `/api/sessions/{sessionId}/children|todo|diff` |
| Abort/fork/compact/revert/command/shell | `abortSession/forkSession/compactSession/revertSession/unrevertSession/runSessionCommand/runSessionShell` | `/api/sessions/{sessionId}/...`；fork/revert 请求体使用 opencode `messageID`，compact 使用 `providerID/modelID`；composer shell mode 提交 `command`，slash command 提交 `command/arguments/parts` |
| Permission/question | `listSessionPermissions/replySessionPermission/listSessionQuestions/...` | `/api/sessions/{sessionId}/permissions|questions` |
| RunEvent stream | `event-stream-client.subscribeRunEvents` | `/api/runs/{runId}/events`；后端合并 DB replay、本机 live bus 和可选 Redis fan-out，前端按 messageId 合并持久化快照与 SSE projection，避免刷新恢复后重复显示 |
| Stop running output | `cancelRun(activeRun.runId)` + close local EventSource | `/api/runs/{runId}/cancel`；点击停止后前端立即关闭本地订阅并标记 cancelling，后端取消 Run 后持久化最终消息快照 |
| Terminal | `createTerminalTicket` + PTY JSON envelope | `/api/sessions/{sessionId}/terminal/tickets` + 后端 WebSocket ticket；WebSocket 只发送 `input/resize/close` envelope |
| Config/settings | `getConfig/updateConfig/disposeGlobal` | `/api/config`、`/api/global/dispose` |
| Provider auth/OAuth | `listProviderAuth/authorizeProviderOAuth/completeProviderOAuth/setProviderAuth/removeProviderAuth` | `/api/provider/auth`、`/api/provider/{providerId}/oauth/*`、`/api/auth/{providerId}`；`listProviderAuth` 兼容 opencode `ProviderAuthMethod[]` map，authorize 保留 method index 与 prompt inputs |
| Worktree | `listWorktrees/createWorktree/removeWorktree/resetWorktree` | `/api/worktrees`、`/api/worktrees/reset` |
| Session share | `shareSession/unshareSession` | `/api/sessions/{sessionId}/share` |
| MCP connect/auth | `connectMcp/disconnectMcp/startMcpAuth/completeMcpAuth/authenticateMcp/removeMcpAuth` | `/api/mcp/{name}/connect|disconnect|auth*` |

所有响应保持平台 `ApiResponse<T>`，错误包含 `traceId/code/message/retryable/details`。本工程不使用 opencode generated SDK，也不访问 opencode server URL。
