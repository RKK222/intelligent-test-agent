# @test-agent/backend-api

## 工程定位

前端访问 `test-agent-app` HTTP API 的唯一 client。

## 主要职责

- 统一 baseUrl、traceId、鉴权头和 JSON 解析。
- 支持 `agentId?: string` 配置，默认 `opencode`；Run、Diff 和 runtime 相关请求统一走 `/api/internal/agent/{agentId}/...`。
- 默认 30 秒请求超时，可通过 `requestTimeoutMs` 覆盖；超时统一映射为 `BackendApiError` 的 `REQUEST_TIMEOUT`。
- 映射统一错误响应为 `BackendApiError`。
- 暴露 Workspace、受控目录选择、文件、Session、Session message、Run、Diff API 方法；Session 支持 workspace 列表、全局搜索、标题/置顶更新和软删除。Workspace/Session CRUD 仍走平台 URL，不放入 agent URL。
- 工作区文件列表、读取、写入、状态和删除统一走“route 查询 + 目标后端 ticket + 文件 WebSocket RPC”，不再调用旧 HTTP 文件接口；client 负责 requestId 匹配、超时、断线错误和切换工作区关闭旧连接。
- 暴露 `listWorkspaceBackendServers()`、`listServerWorkspaceDirectories()`、`createServerWorkspace()` 等超级管理员服务器工作空间选择方法，目录浏览和创建也通过目标后端文件 WebSocket ticket 执行。
- 暴露 `getActiveRun(sessionId)`，用于刷新或重进会话后恢复仍在执行的 RunEvent SSE 订阅；返回 `null` 表示当前会话没有非终态 Run。
- Session message 和 Run 响应透传可选 `parts`、`tokens`、`costUsd` 等新增字段，旧后端缺字段时保持兼容。
- 暴露配置管理和个人 SSH key API 方法，统一走 `/api/internal/platform/configuration-management`，不直连 Git 服务或 opencode server；代码库新增/编辑 payload 包含 `englishName`，应用工作空间创建支持 `operationId`/`version` 并通过 `getWorkspaceCreateOperation(operationId)` 轮询后端进度。
- 暴露应用版本工作区和个人工作区 API 方法，统一走 `/api/internal/platform/workspace-management`，包括成员应用、模板、版本、个人空间、最近使用、diff、同步和版本工作区 `gitPullWorkspaceVersion(versionId)`；版本响应透传目标 commit 与服务器副本状态字段。
- 暴露 Agent 配置管理 API 方法，统一走 `/api/internal/platform/workspace-management/agent-config`；公共/工作空间 Agent 配置文件列表、读取、写入使用“agent-config file route + 目标后端 ticket + 文件 WebSocket RPC”，公共 worktree 切换列表通过 `listPublicAgentWorktrees(linuxServerId)` 读取元数据，Git 更新、worktree、diff、stage/unstage、commit、publish 和 operation progress 仍走对应 HTTP/进度 WebSocket；不直连 Git 仓库或 opencode server。
- 暴露当前用户 opencode 进程状态与初始化方法：`getMyOpencodeProcess()`、`initializeMyOpencodeProcess()`，统一走默认 `opencode` 的 agent-scoped URL；状态响应透传头像菜单使用的 `serviceStatus` 与 `serviceAddress` 字段，旧后端缺字段时由上层前端兼容推断。
- 暴露超级管理员运行管理方法：`getOpencodeRuntimeManagementOverview(params)`、`getOpencodeRuntimeManagementUserProcesses(params)`、`getOpencodeRuntimeContainerMetrics(containerId, params)`、`getOpencodeRuntimeBackendServerMetrics(linuxServerId, params)`、`getOpencodeRuntimeBackendProcessMetrics(backendProcessId, params)`、`restartOpencodeRuntimeManagedProcess(containerId, port)`、`stopOpencodeRuntimeManagedProcess(containerId, port)`，统一走 `/api/internal/platform/opencode-runtime/management/...`，自动携带用户 Bearer Token；overview 透传 `managers[].managedProcesses[]` 供前端合并容器/manager 行并按 `ownership=BOUND/UNBOUND` 展开有主/无主 opencode server 明细；底部用户进程列表通过 `getOpencodeRuntimeManagementUserProcesses({ keyword, page, size })` 按用户名、`userId` 或统一认证号查询，并透传 `managerStatus`、`healthStatus`、`restartable` 区分未运行、健康失败和检查失败；进程重启/停止只按容器和端口调用后端管理命令 API，具体 Java 后端路由由后端根据容器所属服务器完成，组件层在命令成功或失败后都应刷新当前用户进程查询；后端 Java 指标历史以 `linuxServerId`/IP 为主查询键，旧 `backendProcessId` 方法只作兼容入口；指标历史主查询参数使用 `windowMinutes` / `maxPoints`，`hours` 仅保留旧客户端兼容，窗口上限由后端校验。
- 暴露超级管理员定时任务管理方法：任务分页/详情/更新/手动触发、运行记录分页/详情/停止，统一走 `/api/internal/platform/scheduler-management`，自动携带用户 Bearer Token。
- 暴露 AI 回复反馈方法：`putMessageFeedback(messageId, payload)`、`getMyMessageFeedback(messageId)`，统一走 `/api/internal/platform/opencode-runtime/messages/{messageId}/feedback...`，只提交评分、原因和备注，不提交 prompt/assistant 原文。
- 暴露超级管理员运营分析方法：overview、timeseries、peaks、users、organizations、satisfaction、exceptions 和 `exportAnalyticsCsv(type, params)`，统一走 `/api/internal/platform/analytics`；CSV 以 `Blob` 返回，不提供 cost/costUsd 字段。
- 暴露超级管理员用户管理（测试）方法：用户分页查询、创建用户（默认密码 123456）、角色列表，统一走 `/api/internal/platform/system-management`，自动携带用户 Bearer Token；仅用于研发测试便捷造号。
- `startRun` 同时支持旧 `(sessionId, prompt)` 参数和对象 payload（`parts`、`messageId`、`agent`、`model`、`variant`、`mode`）。
- 暴露 opencode Web App 标准运行态方法：Agent/Model/Provider/Command/Reference 目录、config、provider auth/OAuth、worktree、Session active-run/children/todo/diff/abort/fork/compact/revert/command/shell/share、permission/question、fs/vcs/lsp/mcp status/resources/tools/auth 和 terminal ticket；除 terminal ticket 外均按 agent URL 调用。
- Command catalog 映射会保留 opencode runtime 的 `source/hints` 可选字段，供 frontend-opencode 生成 slash command 参数表单。
- 把后端文件 DTO 转换为前端稳定展示模型。
- SSH key 新增方法只发送私钥给平台后端，响应类型只包含 key 元信息，不包含明文或密文。

## 禁止事项

- 不依赖 Vue 组件、dockview-vue、Monaco 或页面状态。
- 不访问 opencode server。
- 不绕过 agent-scoped 后端 URL 调用旧 `/api/...` runtime 入口。

## 验证

```bash
corepack pnpm --filter @test-agent/backend-api typecheck
corepack pnpm test -- backend-api
```
