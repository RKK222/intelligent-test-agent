# @test-agent/shared-types

## 工程定位

跨前端包共享的轻量 TypeScript 类型集合。

## 主要职责

- 定义 API 响应、Workspace、Session、SessionMessage、Run、RunEvent、Diff、AgentMessage 类型。
- Phase 11 新增 PromptPart、MessagePart、ToolPart、PermissionRequest、QuestionRequest、AgentInfo、ModelInfo、ProviderInfo、CommandInfo、RuntimeResourceInfo、RuntimeToolInfo、SessionDiff、TodoItem、RuntimeStatus 等 Web App 运行态 projection 类型。
- 不引入运行时依赖。

## 禁止事项

- 不依赖 UI、API client 或事件 client。
- 不存放组件逻辑。
