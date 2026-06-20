# opencode Web API 映射

| opencode App 能力 | frontend-opencode 调用 | 平台后端映射 |
|---|---|---|
| Agent/Model/Provider catalog | `backend-api.listAgents/listModels/listProviders` | `/api/agents`、`/api/models`、`/api/providers` |
| Command/Reference catalog | `backend-api.listCommands/listReferences` | `/api/commands`、`/api/references` |
| File context picker / Files side panel | `listRuntimeFiles/findRuntimeFiles/readRuntimeFile` | `/api/fs/list`、`/api/fs/find`、`/api/fs/read` |
| VCS/LSP/MCP status | `getVcsStatus/getVcsDiff/getLspStatus/getMcpStatus/getMcpResources/getMcpTools` | `/api/vcs/*`、`/api/lsp/status`、`/api/mcp/*` |
| Prompt submit runtime selection | `startRun({ parts, agent, model, variant })` | `/api/runs`；Agent/Model/Variant 是运行态参数，不进入普通 prompt part；图片附件随 `parts` 以 `file` part 的 `mimeType/url` 发送 |
| Session children/todo/diff | `getSessionChildren/getSessionTodo/getSessionDiff` | `/api/sessions/{sessionId}/children|todo|diff` |
| Abort/fork/compact/revert/command/shell | `abortSession/forkSession/compactSession/revertSession/unrevertSession/runSessionCommand/runSessionShell` | `/api/sessions/{sessionId}/...`；fork/revert 请求体使用 opencode `messageID`，compact 使用 `providerID/modelID` |
| Permission/question | `listSessionPermissions/replySessionPermission/listSessionQuestions/...` | `/api/sessions/{sessionId}/permissions|questions` |
| RunEvent stream | `event-stream-client.subscribeRunEvents` | `/api/runs/{runId}/events` |
| Terminal | `createTerminalTicket` + PTY JSON envelope | `/api/sessions/{sessionId}/terminal/tickets` + 后端 WebSocket ticket；WebSocket 只发送 `input/resize/close` envelope |
| Config/settings | `getConfig/updateConfig/disposeGlobal` | `/api/config`、`/api/global/dispose` |
| Provider auth/OAuth | `listProviderAuth/authorizeProviderOAuth/completeProviderOAuth/setProviderAuth/removeProviderAuth` | `/api/provider/auth`、`/api/provider/{providerId}/oauth/*`、`/api/auth/{providerId}` |
| Worktree | `listWorktrees/createWorktree/removeWorktree/resetWorktree` | `/api/worktrees`、`/api/worktrees/reset` |
| Session share | `shareSession/unshareSession` | `/api/sessions/{sessionId}/share` |
| MCP auth | `startMcpAuth/completeMcpAuth/authenticateMcp/removeMcpAuth` | `/api/mcp/{name}/auth*` |

所有响应保持平台 `ApiResponse<T>`，错误包含 `traceId/code/message/retryable/details`。本工程不使用 opencode generated SDK，也不访问 opencode server URL。

> 当前 Settings MCP 面板只使用已存在的 `getMcpStatus` 与 MCP auth API。opencode App 的 MCP toggle/connect/disconnect 交互仍需平台后端补齐对应接口后再接入。
