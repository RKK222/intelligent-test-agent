# 包说明：@test-agent/shared-types/src

## 职责

提供前端共享类型和稳定字段定义，包含 Web App 运行态 projection 类型。

## 主要程序清单

- `index.ts`：共享类型出口，包含 API/RunEvent/Diff、`NIGHT_WINDOW/ADMIN_CUSTOM` 定时模式、夜间时段/任务、`XxlJobSsoTicket`、`SideQuestionRequest` / `SideQuestionResponse` 以及 PromptPart、MessagePart、ToolPart、PermissionRequest、QuestionRequest、AgentInfo、ModelInfo、ProviderInfo、CommandInfo、RuntimeResourceInfo、RuntimeToolInfo、SessionDiff、TodoItem、RuntimeStatus、TerminalTicket、Workspace/Agent 配置文件 WebSocket route/ticket、用户 opencode 进程、用户管理、内部模型供应商和安全 Token 元数据等模型。任务 `scheduleMode` 保持可选以兼容旧后端响应；内部模型 Token 响应类型不包含明文，Provider 关联新增字段保持可选。`PermissionRequest.patterns` 与运行态 `permissionCount` 为可选兼容字段，attention 接受 `PERMISSION`；`XxlJobSsoTicket` 只用于瞬时表单 POST，不得进入 URL、持久化状态或日志。Session/SessionMessage/Run 等新增字段继续保持可选以兼容旧后端；RunEvent 仍只包含既有类型，本次定时模式变更不新增事件。
- `UserOpencodeMessageGate` 独立表达轻量发布门禁响应；`messageSendAllowed` 必填，其余原因和 rollout ID 保持可空以兼容开放状态。
- `WorkspaceView*` 类型表达工作区与引用目录的组合树、稳定节点身份、逻辑 locator、来源/只读/冲突和局部 warning；引用内容不复用可写 `FileTreeEntry.path` 作为唯一身份。

## 允许依赖

- TypeScript 类型系统。

## 禁止依赖

- 运行时业务包、React、后端 SDK。

## 修改时必须同步更新

- `docs/architecture/module-map.md`。
- `docs/api/*`，如果字段来自后端契约。
