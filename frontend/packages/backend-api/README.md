# @test-agent/backend-api

## 工程定位

前端访问 `test-agent-app` HTTP API 的唯一 client。

## 主要职责

- 统一 baseUrl、traceId、鉴权头和 JSON 解析；`VITE_TEST_AGENT_API_BASE_URL` 显式为空时保留同源相对 `/api`，不会回退到本机开发地址。
- 可选 `rawExchangeObserver` 供前端调试面板记录浏览器与平台后端之间的最终 method/url/path/traceId、请求体、响应状态/响应头和响应原文；observer 不记录 `Authorization`、Cookie 等敏感请求头，也不改变后端 API 契约。
- 支持 `agentId?: string` 配置，默认 `opencode`；Run、Diff 和 runtime 相关请求统一走 `/api/internal/agent/{agentId}/...`。
- 默认 30 秒请求超时，可通过 `requestTimeoutMs` 覆盖，或通过单个请求 init 参数中的 `timeoutMs` 进行局部覆盖；超时统一映射为 `BackendApiError` 的 `REQUEST_TIMEOUT`。
- 映射统一错误响应为 `BackendApiError`。
- 暴露 Workspace、Session message、Run 与 Diff API；历史恢复优先使用 `getSessionTreeMessages`，`listSessionMessages(..., refresh=false)` 用于只读 transcript、Run ID 恢复和旧消息反馈兼容。新反馈不再依赖平台 assistant messageId。
- 工作区原始文件列表、读取、写入、二进制上传、普通文件复制/移动、状态和删除，以及工作台使用的引用组合视图 `listWorkspaceView/readWorkspaceViewFile`，统一走“route 查询 + 目标后端 ticket + 文件 WebSocket RPC”，不再调用旧 HTTP 文件接口；client 负责 requestId 匹配、超时、断线错误和切换工作区关闭旧连接。组合视图只映射后端签发的稳定 `id/locator/source/readonly/workspacePath/warnings`，不在浏览器自行解析引用根目录。
- 工作区与 Agent 配置文件连接分别按路由键复用 single-flight 建连过程，并对缓存连接做实例身份校验；连接在 open 前关闭、报错或同步发送失败时会立即结算 pending，同步发送失败还会安全关闭已失效的底层 socket。只有 `workspace.read` 与 `agent-config.read` 遇到明确 WebSocket 传输错误时自动重连并重试一次，业务错误、请求超时和写操作不重试。
- 暴露 `listWorkspaceBackendServers()`、`listServerWorkspaceDirectories()`、`createServerWorkspace()` 等超级管理员服务器工作空间选择方法，目录浏览和创建也通过目标后端文件 WebSocket ticket 执行。
- 暴露 `getActiveRun(sessionId)` 作为用户级 runtime-state SSE 不可用时的单次 fallback；返回 `null` 表示当前会话没有非终态 Run。调用方不得用它恢复固定间隔 active-run 轮询。
- 暴露 `getSessionRuntimeState()` 兼容读取 `/api/internal/platform/opencode-runtime/sessions/runtime-state`；新工作台以 `event-stream-client` 的用户级 runtime-state SSE 为主入口，不在流连接期间并行调用该 HTTP 接口。
- Session message、Session tree 和 Run 响应透传可选 `parts`、`tokens`、`costUsd`、`events`、`contentKind/summaryStatus/summaryVersion`、`historyRepresentation/replayAvailable/detailsAvailableUntil` 与 `storageMode/clientRequestId`；client 不推断或改写这些元数据，旧后端缺字段时保持兼容。
- 暴露配置管理和个人 SSH key API 方法，统一走 `/api/internal/platform/configuration-management`，不直连 Git 服务或 opencode server；代码库新增 payload 包含 `englishName`、`repositoryType`、`deploymentMode` 和兼容 `standard`，编辑 payload 只发送名称/英文名，`listRepositoryTypes()` 读取版本库类型下拉字典，`getRepositoryDeploymentOptions()` 读取部署模式默认值与内部 SSH 前缀；`getRepositoryTree(appId, repositoryId, branch)` 读取应用关联版本库的远端目录/文件树，不落本地磁盘；应用工作空间创建支持 `operationId`/`version`/`directoryNew` 并通过 `getWorkspaceCreateOperation(operationId)` 轮询后端进度。
- 暴露通用参数显式 JVM 内存值方法：`listCommonParameterMemoryValues()`、`getCommonParameterMemoryValues(backendProcessId)`、`refreshCommonParameterMemoryValues()`、`refreshCommonParameterMemoryValuesForProcess(backendProcessId)`，统一走 `/configuration-management/common-parameters/memory-values` 的 additive 接口；client 只负责进程 ID 编码和 GET/POST 方法，不合并同服务器 Java、不推断未注册参数。
- 暴露应用版本工作区和个人工作区 API 方法，统一走 `/api/internal/platform/workspace-management`，包括成员应用、模板、版本、个人空间、最近使用、diff、同步和版本工作区 `gitPullWorkspaceVersion(versionId)`；版本响应透传目标 commit 与服务器副本状态字段。
- 暴露应用引用资产库方法 `listReferenceRepositories`、`initializeReferenceRepository`、`synchronizeReferenceRepository`、`switchReferenceRepositoryBranch`、`verifyReferenceRepositoryPointers`、`getReferenceRepositoryStatus`、`listReferenceRepositoryTree`，统一走 `/api/internal/platform/workspace-management/applications/{appId}/reference-repositories`；初始化与切换的分支候选继续复用既有 `listRepositoryBranches(repositoryId)`。client 透传目标指针、内部操作、逐服务器在线/实际指针/匹配/同步与核验时间和树节点 `highlighted/selectable`，不自行访问 Git 或磁盘；工作区 `.opencode/opencode.jsonc` 仍通过既有 `readFile/writeFile` 的 route + ticket + WebSocket RPC 修改。
- 引用资产库状态类型额外透传可空 `repositoryPath`；旧后端未返回该字段时保持 `undefined`，client 不拼接、推断或记录物理路径。
- 暴露 Agent 配置管理 API 方法，统一走 `/api/internal/platform/workspace-management/agent-config`；公共/工作空间 Agent 配置文件列表、读取、写入使用“agent-config file route + 目标后端 ticket + 文件 WebSocket RPC”，公共 worktree 切换列表通过 `listPublicAgentWorktrees(linuxServerId)` 读取元数据，公共个人目录定义保存后的本人热加载使用 `reloadPublicPersonalAgentRuntime(worktreeId, linuxServerId)`，由平台后端切换受管配置链接并 dispose，client 不接触服务器路径；系统管理公共仓库行通过 `pullPublicAgentRepository(linuxServerId, branch, operationId?, discardLocalChanges?)` 显式拉取目标服务器仓库；Git 提交推送、worktree、diff、stage/unstage、commit、publish 和 operation progress 仍走对应 HTTP/进度 WebSocket，`updatePublicAgentConfigAndPush` 会先 fetch 远端，再提交本地变更、merge `origin/{branch}` 并 push；公共冲突接口 `getPublicAgentGitConflictFiles`、`getPublicAgentGitConflict`、`resolvePublicAgentGitConflict`、`resolveAllPublicAgentGitConflicts`、`abortPublicAgentGitConflict` 复用工作区冲突协议；`getPublicAgentGitConflictFiles` 只返回未解决冲突路径，用于左侧 Agents 树快速标识冲突，避免刷新公共级时拉取完整 diff patch；进度 WebSocket 返回前会等待连接 open，避免 Git 发布首条 `command` 事件丢失；publish 和公共 update-and-push 冲突文件通过统一错误 `details.conflictFiles` 透传给 UI；不直连 Git 仓库或 opencode server。
- 暴露工作区 Git diff/discard、真实 stage/unstage、冲突读取/逐个或批量解决/取消 API；个人发布支持远程变化预览、expected HEAD 和可选 `operationId`，响应保留 `remotePushed/headCommit/currentStep/executedCommands`，进度 WebSocket 的 `command` 字段用于展示当前正在执行的真实 Git 命令，发布失败时后端错误 `details.failedStep/executedCommands` 可用于展示失败阶段命令。
- 暴露当前用户 opencode 进程强状态、公共配置轻量消息闸门、弱健康、初始化和初始化进度查询方法：`getMyOpencodeProcess()`、`getMyOpencodeMessageGate()`、`getMyOpencodeProcessHealth({ linuxServerId, containerId, port })`、`initializeMyOpencodeProcess(operationId?)`、`getOpencodeProcessStartOperation(operationId)`，统一走默认 `opencode` 的 agent-scoped URL；消息闸门只读共享 rollout 状态，不触发 manager health，供已打开页面固定轮询并在当前用户旧实例 dispose 后开闸。状态响应透传头像菜单使用的 `serviceStatus`、稳定服务器身份 `linuxServerId` 与当前解析地址 `serviceAddress` 字段，弱健康参数必须来自最近一次强状态响应，旧后端缺字段时由上层前端兼容推断。未传 `operationId` 时初始化请求不发送 body，保持旧调用兼容。
- 暴露超级管理员运行管理方法：`getOpencodeRuntimeManagementOverview(params)`、`getOpencodeRuntimeManagementUserProcesses(params)`、`getOpencodeRuntimeContainerMetrics(containerId, params)`、`getOpencodeRuntimeBackendServerMetrics(linuxServerId, params)`、`restartOpencodeRuntimeManagedProcess(containerId, port)`、`stopOpencodeRuntimeManagedProcess(containerId, port)` 和 `createServerTerminalTicket(linuxServerId, payload)`，统一走 `/api/internal/platform/opencode-runtime/management/...`，自动携带用户 Bearer Token；服务器终端 payload 使用 `SERVER@linuxServerId` 绑定当前确认的目标服务器。overview 透传 `managers[].managedProcesses[]` 供前端合并容器/manager 行并按 `ownership=BOUND/UNBOUND` 展开有主/无主 opencode server 明细；底部用户进程列表通过 `getOpencodeRuntimeManagementUserProcesses({ keyword, page, size })` 按用户名、`userId` 或统一认证号查询，并透传 `managerStatus`、`healthStatus`、`restartable` 区分未运行、健康失败和检查失败；进程重启/停止只按容器和端口调用后端管理命令 API，具体 Java 后端路由由后端根据容器所属服务器完成，组件层在命令成功或失败后都应刷新当前用户进程查询；后端 Java 指标历史以稳定 `linuxServerId` 为主查询键，旧 `backendProcessId` 方法已停止导出；后端指标响应透传服务器 CPU/load/内存/swap/磁盘、Java 进程 CPU/RSS/FD、JVM heap/non-heap/direct/mapped/GC/线程字段，字段均可空并保留旧字段回退；指标历史主查询参数使用 `windowMinutes` / `maxPoints`，`hours` 仅保留旧客户端兼容，窗口上限由后端校验。
- 暴露超级管理员定时任务管理方法：任务分页/详情/更新/手动触发、运行诊断、运行记录分页/详情/停止，统一走 `/api/internal/platform/scheduler-management`，自动携带用户 Bearer Token。
- 暴露当前用户夜间执行方法：`getNightExecutionSlots`、`createNightExecutionTask`、`listNightExecutionTasks`、`adjustNightExecutionTask`、`cancelNightExecutionTask`、`dismissNightExecutionTask`，统一走 `/api/internal/platform/opencode-runtime/night-execution`；创建 payload 可复用 Run 的 prompt/parts/agent/model/command 输入，查询响应只返回安全预览。
- 暴露 Run 整体回复反馈方法：`putRunFeedback`、`getMyRunFeedback`、`queryMyRunFeedbacks`，历史批量查询每次最多 100 个 Run；旧消息反馈方法保留过渡兼容，均不提交 prompt/assistant 原文。
- 暴露超级管理员运营分析方法：overview、timeseries、peaks、users、organizations、satisfaction、exceptions 和 `exportAnalyticsCsv(type, params)`，统一走 `/api/internal/platform/analytics`；CSV 以 `Blob` 返回，不提供 cost/costUsd 字段。
- 暴露超级管理员用户管理方法：用户分页查询、创建用户（默认密码 123456）、调整用户全局角色和角色列表，统一走 `/api/internal/platform/system-management`，自动携带用户 Bearer Token；创建用户能力仅用于研发测试便捷造号，角色调整由超管直接操作，不包含普通用户审批通知流。
- `startRun` 同时支持旧 `(sessionId, prompt)` 参数和对象 payload（`parts`、`messageId`、`agent`、`model`、`variant`、`mode`、`command`、`arguments`、`contextToken`、`clientRequestId`）；`getRunContext(sessionId)` 签发仅存页面内存的会话上下文。上下文过期重试必须复用同一 `clientRequestId`，调用方还需自行校验认证、Session、Workspace 与交互代次，不能让迟到 HTTP 结果覆盖当前页面状态。
- 暴露 opencode Web App 标准运行态方法：Agent/Model/Provider/Command/Reference 目录、config、provider auth/OAuth、worktree、Session active-run/children/todo/diff/abort/fork/side-question/compact/revert/command/shell/share、permission/question、fs/vcs/lsp/mcp status/resources/tools/auth 和 terminal ticket；`startSideQuestionRun` 基于主 Session，`startManualQuestionRun` 只提交 `{workspaceId,question,model?}` 并用于无主对话手册问答，两者立即取得 `runId` 并通过既有 RunEvent SSE 消费，不写普通主时间线；旧 `askSideQuestion` 同步调用保留兼容。配置管理额外暴露 `createApplication`，只由超级管理员 UI 调用。其他只读目录、Session 操作、fs/vcs/lsp/mcp 和 terminal ticket 统一走 `/api/internal/platform/opencode-runtime/...`，Run/Diff/SSE、当前用户 opencode 进程和历史 session-tree 主读取继续按 `/api/internal/agent/{agentId}/...` 调用。`listAgents(workspaceId, init?)` 支持透传单请求 `signal` / `timeoutMs`，用于工作区切换取消旧请求和 Agent 下拉短超时重试；内部模型供应商管理方法只覆盖系统管理页 DTO，不给内部代理暴露前端便捷方法。
- Command catalog 映射会保留 opencode runtime 的 `source/hints` 可选字段，供 frontend-opencode 生成 slash command 参数表单。
- 把后端文件 DTO 转换为前端稳定展示模型。
- SSH key 新增方法只发送私钥给平台后端，响应类型只包含 key 元信息，不包含明文或密文。

## 禁止事项

- 不依赖 Vue 组件、dockview-vue、Monaco 或页面状态。
- 不访问 opencode server。
- 不绕过 `backend-api` 调用旧 `/api/...` runtime/workspace 入口；旧入口后端会返回 `410 API_GONE`。

## 验证

```bash
corepack pnpm --filter @test-agent/backend-api typecheck
corepack pnpm test -- backend-api
```

`backend-api.test.ts` 覆盖 7 个引用资产端点的 app/repository/path 编码、初始化/切换 body、无 body 同步/核验和响应透传、组合视图 list/read 的 locator/稳定身份/只读来源/局部 warning 映射，以及通用参数内存值四个接口的 URL 编码与 HTTP 方法；`night-execution.test.ts` 覆盖夜间时段和任务创建/查询/改期/取消/关闭的 URL、查询参数与请求体。
