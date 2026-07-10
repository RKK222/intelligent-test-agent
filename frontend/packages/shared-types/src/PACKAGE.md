# 包说明：@test-agent/shared-types/src

## 职责

提供前端共享类型和稳定字段定义，包含 Web App 运行态 projection 类型。

## 主要程序清单

- `index.ts`：共享类型出口，包含 API/RunEvent/Diff 以及 PromptPart、MessagePart、ToolPart、PermissionRequest、QuestionRequest、AgentInfo、ModelInfo、ProviderInfo、CommandInfo、RuntimeResourceInfo、RuntimeToolInfo、SessionDiff、TodoItem、RuntimeStatus、TerminalTicketRequest、TerminalTicketResponse、Workspace/Agent 配置文件 WebSocket route/ticket、AgentConfigWorktreeOption、用户 opencode 进程强状态/弱健康 DTO、用户级历史会话运行态 DTO、用户管理 DTO（含创建用户和单角色调整 payload）、内部模型供应商管理 DTO 等模型；RunEvent 已包含 transient `run.snapshot.reset`，`RunRuntimeSnapshot` / `RunSnapshotResetPayload` 字段保持可选，其中 `runtimeVersion` 只表达后端 Redis durable/transient 尾流版本，snapshot 内部事件不推进 durable 游标；`SessionMessage` 的投影与 `contentKind/summaryStatus/summaryVersion`、`Run` 的消耗与 `storageMode/clientRequestId/detailsAvailableUntil`、`SessionTreeMessagesResponse` 的 `historyRepresentation/replayAvailable/detailsAvailableUntil` 都必须保持可选/可空，兼容未升级后端或历史数据；`AgentMessage.platformMessageId/remoteMessageId` 必须保持可选，前者用于平台 API（如反馈），后者用于实时 opencode message 归并；`CodeRepositoryConfig.englishName` 对历史数据保持可选/可空，`CreateRepositoryPayload` 和 `UpdateRepositoryPayload` 必填；`WorkspaceCreateOperation` 表达设置页创建工作空间 HTTP 轮询进度；`ApplicationWorkspaceVersion.targetCommitHash/replicaCommitHash/replicaLinuxServerId/replicaStatus` 必须保持可选，兼容未升级后端和历史工作区；`CommandInfo.source/hints` 和 `TodoItem` 的 `cancelled` 状态、扩展展示字段必须保持可选/兼容，旧 payload 或未知状态不得破坏前端展示。

## 允许依赖

- TypeScript 类型系统。

## 禁止依赖

- 运行时业务包、React、后端 SDK。

## 修改时必须同步更新

- `docs/architecture/module-map.md`。
- `docs/api/*`，如果字段来自后端契约。
