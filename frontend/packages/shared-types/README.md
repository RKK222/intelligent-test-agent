# @test-agent/shared-types

## 工程定位

跨前端包共享的轻量 TypeScript 类型集合。

## 主要职责

- 定义 API 响应、Workspace、WorkspaceDirectoryList、Session、SessionMessage、Run、RunEvent、Diff、AgentMessage 类型。
- 新增 PromptPart、MessagePart、ToolPart、PermissionRequest、QuestionRequest、AgentInfo、ModelInfo、ProviderInfo、CommandInfo、RuntimeResourceInfo、RuntimeToolInfo、SessionDiff、TodoItem、RuntimeStatus、TerminalTicketRequest、TerminalTicketResponse 等 Web App 运行态 projection 类型。
- `CommandInfo` 的 `source/hints` 为可选字段，用于保留 opencode command catalog 的来源和参数提示；旧 payload 不提供时前端必须兼容。
- `TodoItem` 保留旧 `text/status/priority` 字段，并可选承载 `title/description/summary/result/error/steps/updatedAt` 等对话框任务分解展示字段。
- `CurrentUser`、`LoginResponse` 增加可选 `roles`，旧响应缺字段时按空角色兼容。
- 定义应用配置管理 DTO：`ApplicationDefinition`、`ApplicationMember`、`CodeRepositoryConfig`、`ApplicationWorkspaceConfig`、`SshKeyMetadata` 和对应请求 payload。
- 定义应用版本工作区 DTO：`ManagedApplication`、`ApplicationWorkspaceTemplate`、`ApplicationWorkspaceVersion`、`PersonalWorkspace`、`WorkspaceDiff`、`WorkspaceSyncResult` 和对应请求 payload。
- 不引入运行时依赖。

## 禁止事项

- 不依赖 UI、API client 或事件 client。
- 不存放组件逻辑。
