# 包说明：@test-agent/shared-types/src

## 职责

提供前端共享类型和稳定字段定义，包含 Web App 运行态 projection 类型。

## 主要程序清单

- `index.ts`：共享类型出口，包含 API/RunEvent/Diff、`BatchContext`、`NIGHT_WINDOW/ADMIN_CUSTOM` 定时模式、夜间时段/任务、`XxlJobSsoTicket`、`SideQuestionRequest` / `SideQuestionResponse`、`SupportAccessGrantRequest` / `SupportAccessGrant` / `SupportAccessIncidentSuggestion` / `SupportAccessTarget` / `SupportAccessAuditEvent` 以及 PromptPart、MessagePart、ToolPart、PermissionRequest、QuestionRequest、AgentInfo、ModelInfo、ProviderInfo、CommandInfo、RuntimeResourceInfo、RuntimeToolInfo、SessionDiff、TodoItem、RuntimeStatus、TerminalTicket、Workspace/Agent 配置文件 WebSocket route/ticket、用户 opencode 进程、用户管理、内部模型供应商和安全 Token 元数据等模型。`BatchContext` 只表达稳定批次和条目请求 ID，不承载正文；内部模型可观测类型包含精确 `InternalModelCallOutcome`、五类 `InternalModelCallOutcomeGroup`、小时聚合和带可空 `ucid` 的调用明细。`Workspace.physicalRootPath` 是可选增量字段，表示后端解析后的物理绝对路径，`rootPath` 仅保留旧响应兼容。排查 grant 只允许页面内存使用，排查单号建议可选携带 `source=WORK_ORDER/GENERATED`，旧响应缺少来源时保持兼容；排查 Workspace 的 `backendAvailability/backendLastHeartbeatAt`、Session tree 的 `LEGACY` 表示和事件 `traceId` 均为可选兼容字段。任务 `scheduleMode` 保持可选以兼容旧后端响应，内部模型 Token 响应类型不包含明文，Provider 关联新增字段保持可选。`PermissionRequest.patterns` 与运行态 `permissionCount` 为可选兼容字段，attention 接受 `PERMISSION`；`XxlJobSsoTicket` 只用于瞬时表单 POST，不得进入 URL、持久化状态或日志。Session/SessionMessage/Run/AgentMessage/SessionRuntimeState 的 `resend` 元数据继续保持可选以兼容旧后端；RunEvent additive 增加 `run.resend.scheduled/started/failed`，事件只携带重发身份、次数、状态和执行时间，不携带 prompt、回答或供应商正文；批量编排和内部模型可观测不新增其他事件。
- `MemoryScope/MemoryStatus/MemorySource` 与 `MemoryView/MemoryEvidenceView/MemoryUsageView/MemorySkillProposalView/EmbeddingProfile/MemoryAdminHealth/MemorySettingsView/MemoryWhitelistView` 对齐通用记忆 DTO；公开来源只有 `NATIVE/MANUAL/TEAM_PROPOSAL` 等通用语义，不包含 QA taskTypes 或自定义 confidence。证据 additive 包含 `sessionId/sessionTitle/transcriptAvailable/runId`。这些类型不加入 RunEvent union，`contentAvailable=false` 时只能展示治理摘要。
- `UserOpencodeMessageGate` 独立表达轻量发布门禁响应；`messageSendAllowed` 必填，其余原因和 rollout ID 保持可空以兼容开放状态。
- `ExternalApiScope`、`ExternalApiCredential*` 类型固定系统管理的 scope、分页、CRUD 与一次性明文响应；列表不包含密文，`apiKey` 只允许由组件瞬时消费。
- `WorkspaceView*` 类型表达工作区与引用目录的组合树、稳定节点身份、逻辑 locator、来源/只读/冲突和局部 warning；引用内容不复用可写 `FileTreeEntry.path` 作为唯一身份。`FileBinaryChunk` / `FileBinaryChunkRequest` 表达任意文件下载的原始字节分段与快照栅栏。
- `AppSource*` 类型表达应用源码仓库、固定选择、远端树节点及 `{targetCommit,nodes}` 树快照、操作/服务器/步骤安全快照、打开结果、一次性进度 ticket 和 `snapshot/step/completed/failed` WebSocket envelope；下载状态严格为 `NOT_DOWNLOADED/DOWNLOADED_ACTIVE/DOWNLOADED_EXPIRED/PERSONAL_OCCUPIED`。旧树节点数组类型继续保留，调用方可增量改用 `AppSourceTreeSnapshot`；响应新增信息保持可空/可选兼容，选中路径稳定使用 `{ path, type }`；该进度协议独立于 RunEvent，不向 RunEvent union 增加 wire name。

## 允许依赖

- TypeScript 类型系统。

## 禁止依赖

- 运行时业务包、React、后端 SDK。

## 修改时必须同步更新

- `docs/architecture/module-map.md`。
- `docs/api/*`，如果字段来自后端契约。
