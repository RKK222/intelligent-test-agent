# 包说明：@test-agent/backend-api/src

## 职责

封装后端 Runtime HTTP API，输出稳定 TypeScript 方法和错误对象；agent 相关能力默认使用 `opencode`，可通过 `agentId` 切换 URL 前缀。构建时显式设置空的 `VITE_TEST_AGENT_API_BASE_URL` 表示同源相对访问，不能回退到 `127.0.0.1`。

## 主要程序清单

- `index.ts`：`createBackendApiClient`、`BackendApiError` 和 API 方法集合；`agentId?: string` 默认 `opencode`，Run、Diff 和 runtime 相关方法拼接 `/api/internal/agent/{agentId}/...`；可选 `routeLinuxServerId` 在每次绑定请求发起时动态读取页面内存值，内部 `routedRequest` 只给用户 OpenCode、Session、Run、夜间任务和本地工作区/Agent 配置请求设置 `X-Test-Agent-Linux-Server-Id`，空值与普通共享控制面不发送，该头只作为 Nginx 首跳提示；可选 `rawExchangeObserver` 在 JSON 请求读取响应文本后、解析前回调安全的前后端原始交换摘要，不记录 `Authorization` 或 Cookie，并递归脱敏 `token/authToken/tokenValue`；`getRunContext(sessionId)` 签发会话 `contextToken`，`startRun` 兼容旧 prompt string 与携带 `contextToken/clientRequestId` 的对象 payload，调用方必须用认证、Session、Workspace 和交互代次 fence 丢弃迟到结果；runtime-state SSE 是运行恢复主入口，`getActiveRun(sessionId)` 只作为流不可用时“每故障窗口、每 Session 一次”的 fallback，不得恢复 1.5 秒轮询，`getSessionRuntimeState()` 兼容旧摘要缺少 `permissionCount` 并按 `sessions[].attention` 推导；permission 列表归一化保留 `patterns[]` 并回退旧 `pattern`；并封装当前用户 opencode 进程强状态/弱健康、runtime 目录、session/message 操作、`askSideQuestion(sessionId, payload)` 临时 fork/compact/清理旁路问答、Session 全局搜索/置顶/删除、运行管理 overview、配置管理（含内部模型 Token CRUD 与 Provider 关联）、工作空间创建进度轮询、应用版本工作区、个人工作区、版本工作区 Git 访问预检与 git pull、permission/question、fs/vcs/lsp/mcp status/resources/tools、用户管理查询/创建/角色调整和 terminal ticket 方法；`listAgents(workspaceId, init?)` 可透传 `signal` / `timeoutMs`，用于前端 Agent 目录取消旧请求和短超时重试；工作区文件列表、相对路径搜索、读取、写入、重命名和 Agent 配置文件读写/上传/重命名/删除都通过目标后端文件 WebSocket RPC，公共与应用上传分别使用 `uploadPublicAgentFile`、`uploadWorkspaceAgentFile`，改名分别使用 `renamePublicAgentFile`、`renameWorkspaceAgentFile`；`searchFiles(workspaceId, query)` 允许空 query 获取受限文件目录，普通工作区文件重命名方法为 `renameWorkspaceFile(workspaceId, path, name)`；公共 worktree 切换列表通过 `listPublicAgentWorktrees(linuxServerId)` 获取元数据；代码库配置 payload 必须携带 `englishName`；SessionMessage、Session tree 与 Run 的摘要/历史表示/存储模式元数据只透传为可选字段，兼容旧 payload；Command catalog 映射需保留 `source/hints` 等可选字段。
- `index.ts` 同时定义引用资产总体目标、内部操作、逐服务器在线/实际指针/匹配/同步与核验时间和单层树类型，并封装 list/initialize/synchronize/switch-branch/verify/status/tree 7 个内部 API；分支列表继续复用配置管理方法，引用配置文件继续复用 workspace 文件 WebSocket `readFile/writeFile`，工作台组合树使用同一连接的 `listWorkspaceView/readWorkspaceViewFile` 并透传稳定 locator、来源、只读能力和局部 warning，client 不直连 Git、磁盘或 opencode server。
- 引用资产状态的 `repositoryPath?: string | null` 只做兼容透传；缺字段时调用方显示不可用，client 不自行构造服务器路径。
- 文件 WebSocket client 对 workspace 与 Agent 配置路由键分别维护 single-flight 连接 Promise；socket/error/close/send 只清理自身缓存与 pending，旧连接的迟到回调不得删除新连接，同步 send 失败完成原错误清理后安全关闭底层 socket。读取 RPC 只对内部传输错误重连重试一次，`BackendApiError`、`REQUEST_TIMEOUT` 和所有写操作原样返回。
- `getMyOpencodeMessageGate()` 是公共配置发布期间的轻量只读门禁查询；它不替代后端 Run 入口校验，也不触发 manager/opencode 健康检查。
- `getNightExecutionSlots/createNightExecutionTask/listNightExecutionTasks/adjustNightExecutionTask/cancelNightExecutionTask/dismissNightExecutionTask`：夜间时段和任务 HTTP client；完整输入只用于创建请求，任务响应使用 shared-types 的安全投影。
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
