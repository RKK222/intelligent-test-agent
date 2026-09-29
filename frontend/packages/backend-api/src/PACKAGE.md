# 包说明：@test-agent/backend-api/src

团队最新文件审阅复用短 scope/ticket 与平台文件 WebSocket，支持 list/search/版本校验分片 read；同 scope 单飞连接，切换/刷新关闭并作废迟到连接。HTTP 不返回文件目录或正文，聚合 ID 不作为物理 Workspace。

## 职责

封装后端 Runtime HTTP API，输出稳定 TypeScript 方法和错误对象；agent 相关能力默认使用 `opencode`，可通过 `agentId` 切换 URL 前缀。构建时显式设置空的 `VITE_TEST_AGENT_API_BASE_URL` 表示同源相对访问，不能回退到 `127.0.0.1`。

## 主要程序清单

- Agent/Skill Hub client 支持 `source=PLATFORM|SKILLHUB|ALL` 和外部正文 `materialize`；文件正文仍走只读 Hub WebSocket，引用写入仍走目标工作区文件 RPC。
- `index.ts` 的本地客户端 API 使用固定 `localClientBase` 与 `localClientVersionManagementBase`：版本管理方法仅供
  超级管理员页面调用，普通用户更新只传通知 ID 与原通知解析的 14 位目标版本；不接受浏览器提供下载 URL、策略
  revision 或实例 owner。`instances/me` 新增字段均为可选，以兼容旧节点。

- `createRunResend(agentId, sessionId, payload)`：调用统一撤销重发 API，传递最后远端消息边界、可选源 Run、上下文令牌、客户端幂等键和可选修改文本，返回预留替代 Run 及 additive `resend` 元数据。
- `listExternalApiScopes/listExternalApiCredentials/create/update/reveal/rotate/deleteExternalApiCredential`：超级管理员 API Key 管理 client；`rawExchangeObserver` 对 `apiKey/ciphertext/encryptedApiKey` 强制脱敏，调用方不得缓存一次性明文响应。
- `getTcdsTaskTypes()` / `maintainTcdsTestCases(payload)`：普通登录用户案例维护 client；分别固定访问 `/api/internal/platform/integration/tcds/task-types` 与 `/api/internal/platform/integration/tcds/test-cases`，浏览器不直连 TCDS，只提交 `itemNo/caseList`，也不暴露 TCDS 地址、`toolId` 或可伪造的 `userId`。

- `index.ts` 的 `createBatchItemSession` 为每个批量条目幂等创建独立 Session；`CreateNightExecutionTaskPayload.batchContext` 为可选兼容字段，携带时调用方必须省略 `sessionId`。两条链路都复用现有用户 OpenCode 路由提示，不新增工作区文件 HTTP 代理。
- `index.ts` 的通用记忆 client 固定访问 `/api/internal/platform/memory/v1`（管理端为 `/admin`），承载个人/团队治理、含 Session 标题/ID 的证据、Run usage、Skill 提案、双 profile 健康/设置和白名单；团队提案可选传本人 `sourceMemoryId`，修改请求透传 `expectedVersion`。浏览器不调用 memory-service、不读取他人原始聊天，也不新增 RunEvent。
- Model/Provider 原生目录在 V2 中按 `enabled/activation` 排除禁用项；旧版运行配置声明非空 `enabled_providers` 时继续共用同一轮 config 请求，按 Provider ID 过滤且不改变原生顺序，请求结束后不长期缓存白名单。
- 应用工作空间配置通过 `updateApplicationWorkspace` 部分更新 `workspaceName/enabled`，旧 `renameApplicationWorkspace` 保持兼容；`listApplicationWorkspaces/listWorkspaceVersions` 仅服务测试工作空间查询，不发送个人 TestAgent 路由头。`listRepositories(page, size, keyword?)` 复用配置管理分页 API，并仅在有值时编码发送关键字。自动化当前配置统一使用应用级 `automation-reference-repositories` API，`alias` 与分支、目录、描述一起保存，不再暴露模板版本激活方法。
- `reconcileWorkspaceAutomationReferences(workspaceId)` 复用 Agent 配置文件 WebSocket 的 `agent-config.automation-reference.reconcile`，只传当前工作区身份并接收 `changed/warnings`。前端 API 不读取、解析或上传自动化 JSONC，也不接收应用、版本库、generation 或物理路径作为对账输入。
- `openExperienceWorkspace()` 对任意已登录用户打开其 READY TestAgent 所在服务器的体验 Workspace；`commitExperienceWorkspace(workspaceId, commitMessage, files)` 只调用运行态 Workspace 本地提交接口，不复用个人 workspace publish，也不暴露 push 参数。
- `index.ts`：`createBackendApiClient`、`BackendApiError` 和 API 方法集合；`agentId?: string` 默认 `opencode`，Run、Diff 和 runtime 相关方法拼接 `/api/internal/agent/{agentId}/...`；可选 `routeLinuxServerId` 在每次绑定请求发起时动态读取页面内存值，内部 `routedRequest` 只给用户 OpenCode、Session、Run、夜间任务和本地工作区/Agent 配置请求设置 `X-Test-Agent-Linux-Server-Id`，空值与普通共享控制面不发送，该头只作为 Nginx 首跳提示；`APP_ADMIN` 控制面 `listApplicationGitRefreshScopes()`、`refreshApplicationGit(appId)` 与 `refreshApplicationGitGroup(appId, selector)` 固定使用普通 `request`，避免把应用级控制面误路由到个人进程服务器；问题排查 client 通过禁用浏览器缓存的 `getSupportAccessIncidentSuggestion()` 请求带来源的排查单号建议，当前没有权威工单数据源时每次取得新的 `sai_` 单号，grant 仍只在页面内存保存；可选 `rawExchangeObserver` 在 JSON 请求读取响应文本后、解析前回调安全的前后端原始交换摘要，不记录 `Authorization` 或 Cookie，并递归脱敏 `token/authToken/tokenValue`；`getRunContext(sessionId)` 签发会话 `contextToken`，`startRun` 兼容旧 prompt string 与携带 `contextToken/clientRequestId` 的对象 payload，调用方必须用认证、Session、Workspace 和交互代次 fence 丢弃迟到结果；runtime-state SSE 是运行恢复主入口，`getActiveRun(sessionId)` 只作为流不可用时“每故障窗口、每 Session 一次”的 fallback，不得恢复 1.5 秒轮询，`getSessionRuntimeState()` 兼容旧摘要缺少 `permissionCount` 并按 `sessions[].attention` 推导；permission 列表归一化保留 `patterns[]` 并回退旧 `pattern`；并封装当前用户 opencode 进程强状态/弱健康、`restartMyOpencodeProcess(confirmRunning?)` 个人重启、runtime 目录、session/message 操作、使用一小时局部超时的 `compactSession(sessionId, payload)`、`askSideQuestion(sessionId, payload)` 临时 fork/compact/清理旁路问答、Session 全局搜索/置顶/删除、运行管理 overview、配置管理（含内部模型 Token CRUD 与 Provider 关联）、内部模型可观测明细/小时统计/探活（明细支持精确 `outcome` 和五类 `outcomeGroup`，并透传 `ucid`）、工作空间创建进度轮询、应用版本工作区、个人工作区、版本工作区 Git 访问预检与 git pull、permission/question、fs/vcs/lsp/mcp status/resources/tools、用户管理查询/创建/角色调整和 terminal ticket 方法；`listAgents(workspaceId, init?)` 可透传单请求 `signal` / `timeoutMs`，用于工作区切换取消旧请求和短超时重试；工作区文件列表、相对路径搜索、读取、写入、重命名和 Agent 配置文件读写/上传/重命名/删除都通过目标后端文件 WebSocket RPC，公共与应用上传分别使用 `uploadPublicAgentFile`、`uploadWorkspaceAgentFile`，改名分别使用 `renamePublicAgentFile`、`renameWorkspaceAgentFile`；`searchFiles(workspaceId, query)` 允许空 query 获取受限文件目录，普通工作区文件重命名方法为 `renameWorkspaceFile(workspaceId, path, name)`；公共 worktree 切换列表通过 `listPublicAgentWorktrees(linuxServerId)` 获取元数据；代码库配置 payload 必须携带 `englishName`；SessionMessage、Session tree 与 Run 的摘要/历史表示/存储模式元数据只透传为可选字段，兼容旧 payload；Command catalog 映射需保留 `source/hints` 等可选字段。
- 网页本地目录浏览复用 `createLocalDirectoryPickerClient`：`listLocalClientDirectories()` 通过 `directory.list` 提供显式兜底，调用结束必须关闭一次性文件 WebSocket。原生选择与注册由客户端托盘直接完成，不由网页触发。
- `restartMyOpencodeProcess(confirmRunning?)` 访问当前 agent-scoped `/processes/me/restart`，缺省发送 `confirmRunning=false`；后端活动 Run 冲突的 `confirmationRequired/runningCount` 保留在 `BackendApiError.details`，供头像菜单和 dispose 失败通知复用同一确认流程。
- 应用 Git 三个 client 属于 `APP_ADMIN` 控制面：应用管理员由后端限制为自己有效成员关系所属的启用应用，超级管理员保持全量；client 不根据页面状态扩大范围，也不发送个人 OpenCode 路由头。
- 通知 client 透传 dispose 四态和 `NONE/RESTART_OWN_PROCESS` 受控动作；`NONE` 不等于通知无效，未知类型或动作由消费端失败关闭。
- `index.ts` 的 support client 以独立连接缓存实现授权签发/撤销、目标切换、目标用户会话与工作区读取、审计查询和只读文件 RPC；每个请求携带内存中的 `X-Support-Access-Grant`，撤销或清空授权时同步关闭 support socket。原始交换 observer 对 grant 字段和请求头脱敏，support 401 只收敛排查界面，不污染普通登录态。
- `index.ts` 同时定义引用资产总体目标、内部操作、逐服务器在线/实际指针/匹配/同步与核验时间和单层树类型，并封装 list/initialize/synchronize/switch-branch/verify/terminate/status/tree 8 个内部 API；终止只传当前页面实际观察到的 generation，分支列表继续复用配置管理方法，引用配置文件统一走 Agent 配置 WebSocket `readWorkspaceAgentFile/writeWorkspaceAgentFile`，工作台组合树使用同一连接的 `listWorkspaceView/readWorkspaceViewFile` 并透传稳定 locator、来源、只读能力和局部 warning；下载通过 `readFileBinaryChunk/readWorkspaceViewFileBinaryChunk` 获取原始字节分段，client 不直连 Git、磁盘或 opencode server。
- `index.ts` 封装应用源码仓库列表、分支、远端树、物化、同 generation 重试、打开、最近选择、操作快照和进度 ticket；分支请求使用 70 秒局部超时，`listAppSourceTree/getAppSourceTreeSnapshot` 使用 130 秒局部超时，覆盖后端权威 Git 命令窗口而不改变全局 30 秒默认值。`listAppSourceTree` 保留原节点数组与 URL，`getAppSourceTreeSnapshot` 在同一 URL 增加 `includeCommit=true` 并返回 `targetCommit/nodes`，供物化请求提交同一棵树的 `expectedTreeCommit`。应用/最近入口沿用用户进程服务器路由提示，`appId/repositoryId/branch/path` 分别按 URL 组件或查询参数编码，物化选择固定发送 `{ path, type }`。operationId 在所有 REST/ticket 请求前按与后端相同的 ECMAScript WhiteSpace + LineTerminator 集合规范化，并拒绝规范化后的精确 `.`/`..`。`connectAppSourceProgress(..., { signal })` 每次连接都先取得新的一次性 ticket，并直接使用后端返回的绝对 WebSocket URL；AbortSignal 可在 ticket 请求或 socket CONNECTING 阶段以 `AbortError` 释放连接且不报告传输失败。client 不自动重连，严格校验 `snapshot/step/completed/failed` 判别、内外 operationId/traceId 和终态组合；每个非法入站帧都被拒绝并各自转换为一次 `WEBSOCKET_MESSAGE_INVALID` 安全失败回调，不暴露原始 payload 或解析/校验错误细节；底层 error/close 使用连接故障 fence 去重，调用方主动关闭只释放观察且不发送取消操作。
- 引用资产状态的 `repositoryPath?: string | null` 只做兼容透传；缺字段时调用方显示不可用，client 不自行构造服务器路径。
- 文件 WebSocket client 对 workspace 与 Agent 配置路由键分别维护 single-flight 连接 Promise；企业同源构建的空 API base 遇到相对 ticket URL 时，先按浏览器当前页面补全绝对 `ws://` / `wss://` 地址再建连。socket/error/close/send 只清理自身缓存与 pending，旧连接的迟到回调不得删除新连接，同步 send 失败完成原错误清理后安全关闭底层 socket。文本读取、UTF-8 预览分段和原始字节下载分段只对内部传输错误重连重试一次，`BackendApiError`、`REQUEST_TIMEOUT` 和所有写操作原样返回。
- `getMyOpencodeMessageGate()` 是公共配置发布期间的轻量只读门禁查询；它不替代后端 Run 入口校验，也不触发 manager/opencode 健康检查。
- `updatePublicAgentConfig()` 只发起远端分支到固定 commit 的公共全局 rollout；`getPublicAgentConfigRollout()` 读取逐服务器 Git 同步、进程排空、公共个人 worktree 补偿和 `lastError`，`getApplicationAgentConfigRollouts()` 读取最近应用版本发布及未 dispose/重启用户明细。这些方法使用普通共享控制面请求，不附加个人 OpenCode 服务器路由头；`pullPublicAgentRepository()` 仅为旧客户端兼容保留。
- `getNightExecutionSlots/createNightExecutionTask/listNightExecutionTasks/adjustNightExecutionTask/cancelNightExecutionTask/dismissNightExecutionTask`：双模式定时时段和任务 HTTP client；创建 payload 的 `scheduleMode` 可选以兼容旧请求，调整请求不允许切换模式，完整输入只用于创建请求，任务响应使用 shared-types 的安全投影。
- `createXxlJobSsoTicket()`：调用平台票据 API并返回同源表单动作；只允许组件把 ticket 写入瞬时隐藏表单，禁止拼接 URL。原始 HTTP observer 会对 ticket/token/authToken/tokenValue/cookie/password/secret/sessionDigest 递归脱敏。

- 工作区重命名 WebSocket RPC 通过 `renameWorkspaceFile(workspaceId, path, name)` 兼容调用文件和目录；`uploadWorkspaceFile`、`copyWorkspaceFile`、`moveWorkspaceFile` 分别承载 Base64 二进制新文件上传和普通文件复制/移动。Agent 配置对应提供公共级与应用级 copy/move 方法，通过同一目标后端文件 WebSocket 执行；实际路径、写权限、白名单和目标冲突由目标后端统一校验。

## 允许依赖

- `@test-agent/shared-types`。
- 浏览器 `fetch` API。

## 禁止依赖

- React UI、工作台状态、opencode server。

## 修改时必须同步更新

- `docs/api/http-api.md`。
- `docs/architecture/module-map.md`。
- 本包 README 和测试。
