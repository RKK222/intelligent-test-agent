# 包说明：@test-agent/backend-api/src

## 职责

封装后端 Runtime HTTP API，输出稳定 TypeScript 方法和错误对象；agent 相关能力默认使用 `opencode`，可通过 `agentId` 切换 URL 前缀。

## 主要程序清单

- `index.ts`：`createBackendApiClient`、`BackendApiError` 和 API 方法集合；`agentId?: string` 默认 `opencode`，Run、Diff 和 runtime 相关方法拼接 `/api/internal/agent/{agentId}/...`；可选 `rawExchangeObserver` 在 JSON 请求读取响应文本后、解析前回调安全的前后端原始交换摘要，不记录 `Authorization` 或 Cookie；`getRunContext(sessionId)` 签发会话 `contextToken`，`startRun` 兼容旧 prompt string 与携带 `contextToken/clientRequestId` 的对象 payload，调用方必须用认证、Session、Workspace 和交互代次 fence 丢弃迟到结果；runtime-state SSE 是运行恢复主入口，`getActiveRun(sessionId)` 只作为流不可用时“每故障窗口、每 Session 一次”的 fallback，不得恢复 1.5 秒轮询，`getSessionRuntimeState()` 仅保留兼容读取摘要；并封装当前用户 opencode 进程强状态/弱健康、runtime 目录、session/message 操作、`askSideQuestion(sessionId, payload)` 临时 fork/compact/清理旁路问答、Session 全局搜索/置顶/删除、运行管理 overview、配置管理、工作空间创建进度轮询、应用版本工作区、个人工作区、版本工作区 git pull、permission/question、fs/vcs/lsp/mcp status/resources/tools、用户管理查询/创建/角色调整和 terminal ticket 方法；`listAgents(workspaceId, init?)` 可透传 `signal` / `timeoutMs`，用于前端 Agent 目录取消旧请求和短超时重试；工作区文件列表、相对路径搜索、读取、写入、重命名和 Agent 配置文件读写都通过目标后端文件 WebSocket RPC，`searchFiles(workspaceId, query)` 允许空 query 获取受限文件目录，普通工作区文件重命名方法为 `renameWorkspaceFile(workspaceId, path, name)`；公共 worktree 切换列表通过 `listPublicAgentWorktrees(linuxServerId)` 获取元数据；代码库配置 payload 必须携带 `englishName`；SessionMessage、Session tree 与 Run 的摘要/历史表示/存储模式元数据只透传为可选字段，兼容旧 payload；Command catalog 映射需保留 `source/hints` 等可选字段。
- `index.ts` 同时定义引用资产总体目标、内部操作、逐服务器在线/实际指针/匹配/同步与核验时间和单层树类型，并封装 list/initialize/synchronize/switch-branch/verify/status/tree 7 个内部 API；分支列表继续复用配置管理方法，引用配置文件继续复用 workspace 文件 WebSocket `readFile/writeFile`，工作台组合树使用同一连接的 `listWorkspaceView/readWorkspaceViewFile` 并透传稳定 locator、来源、只读能力和局部 warning，client 不直连 Git、磁盘或 opencode server。
- 文件 WebSocket client 对 workspace 与 Agent 配置路由键分别维护 single-flight 连接 Promise；socket/error/close/send 只清理自身缓存与 pending，旧连接的迟到回调不得删除新连接，同步 send 失败完成原错误清理后安全关闭底层 socket。读取 RPC 只对内部传输错误重连重试一次，`BackendApiError`、`REQUEST_TIMEOUT` 和所有写操作原样返回。
- `getMyOpencodeMessageGate()` 是公共配置发布期间的轻量只读门禁查询；它不替代后端 Run 入口校验，也不触发 manager/opencode 健康检查。

- 工作区重命名 WebSocket RPC 通过 `renameWorkspaceFile(workspaceId, path, name)` 兼容调用文件和目录；`uploadWorkspaceFile`、`copyWorkspaceFile`、`moveWorkspaceFile` 分别承载 Base64 二进制新文件上传和普通文件复制/移动；实际路径、写权限、目标冲突和大小限制由目标后端统一校验。

## 允许依赖

- `@test-agent/shared-types`。
- 浏览器 `fetch` API。

## 禁止依赖

- React UI、工作台状态、opencode server。

## 修改时必须同步更新

- `docs/api/http-api.md`。
- `docs/architecture/module-map.md`。
- 本包 README 和测试。
