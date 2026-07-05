# 包说明：@test-agent/shared-types/src

## 职责

提供前端共享类型和稳定字段定义，包含 Web App 运行态 projection 类型。

## 主要程序清单

- `index.ts`：共享类型出口，包含 API/RunEvent/Diff 以及 PromptPart、MessagePart、ToolPart、PermissionRequest、QuestionRequest、AgentInfo、ModelInfo、ProviderInfo、CommandInfo、RuntimeResourceInfo、RuntimeToolInfo、SessionDiff、TodoItem、RuntimeStatus、TerminalTicketRequest、TerminalTicketResponse、Workspace/Agent 配置文件 WebSocket route/ticket、AgentConfigWorktreeOption、用户管理（测试）DTO 等模型；`SessionMessage.runId/remoteMessageId/parts/tokens/costUsd/updatedAt` 与 `Run.tokens/costUsd` 必须保持可选，兼容未升级后端或历史快照；`AgentMessage.platformMessageId/remoteMessageId` 必须保持可选，前者用于平台 API（如反馈），后者用于实时 opencode message 归并；`CodeRepositoryConfig.englishName` 对历史数据保持可选/可空，`CreateRepositoryPayload` 和 `UpdateRepositoryPayload` 必填；`WorkspaceCreateOperation` 表达设置页创建工作空间 HTTP 轮询进度；`ApplicationWorkspaceVersion.targetCommitHash/replicaCommitHash/replicaLinuxServerId/replicaStatus` 必须保持可选，兼容未升级后端和历史工作区；`CommandInfo.source/hints` 和 `TodoItem` 的 `cancelled` 状态、扩展展示字段必须保持可选/兼容，旧 payload 或未知状态不得破坏前端展示。

## 允许依赖

- TypeScript 类型系统。

## 禁止依赖

- 运行时业务包、React、后端 SDK。

## 修改时必须同步更新

- `docs/architecture/module-map.md`。
- `docs/api/*`，如果字段来自后端契约。
