# 包说明：@test-agent/backend-api/src

## 职责

封装后端 Runtime HTTP API，输出稳定 TypeScript 方法和错误对象；agent 相关能力默认使用 `opencode`，可通过 `agentId` 切换 URL 前缀。

## 主要程序清单

- `index.ts`：`createBackendApiClient`、`BackendApiError` 和 API 方法集合；`agentId?: string` 默认 `opencode`，Run、Diff 和 runtime 相关方法拼接 `/api/internal/agent/{agentId}/...`；可选 `rawExchangeObserver` 在 JSON 请求读取响应文本后、解析前回调安全的前后端原始交换摘要，不记录 `Authorization` 或 Cookie；`startRun` 兼容旧 prompt string 与对象 payload，`getActiveRun(sessionId)` 用于刷新后恢复非终态 RunEvent SSE，并封装 runtime 目录、session/message 操作、Session 全局搜索/置顶/删除、运行管理 overview、配置管理、工作空间创建进度轮询、应用版本工作区、个人工作区、版本工作区 git pull、permission/question、fs/vcs/lsp/mcp status/resources/tools 和 terminal ticket 方法；`listAgents(workspaceId, init?)` 可透传 `signal` / `timeoutMs`，用于前端 Agent 目录取消旧请求和短超时重试；工作区文件和 Agent 配置文件读写都通过目标后端文件 WebSocket RPC，公共 worktree 切换列表通过 `listPublicAgentWorktrees(linuxServerId)` 获取元数据；代码库配置 payload 必须携带 `englishName`；SessionMessage/Run 的 `parts/tokens/costUsd` 等新增字段只透传为可选字段；Command catalog 映射需保留 `source/hints` 等可选字段，兼容旧 payload。

## 允许依赖

- `@test-agent/shared-types`。
- 浏览器 `fetch` API。

## 禁止依赖

- React UI、工作台状态、opencode server。

## 修改时必须同步更新

- `docs/api/http-api.md`。
- `docs/architecture/module-map.md`。
- 本包 README 和测试。
