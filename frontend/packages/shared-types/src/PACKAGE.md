# 包说明：@test-agent/shared-types/src

## 职责

提供前端共享类型和稳定字段定义，包含 Phase 11 Web App 运行态 projection 类型。

## 主要程序清单

- `index.ts`：共享类型出口，包含 API/RunEvent/Diff 以及 PromptPart、MessagePart、ToolPart、PermissionRequest、QuestionRequest、AgentInfo、ModelInfo、ProviderInfo、CommandInfo、RuntimeResourceInfo、RuntimeToolInfo、SessionDiff、TodoItem、RuntimeStatus、TerminalTicketRequest、TerminalTicketResponse 等模型；`CommandInfo.source/hints` 和 `TodoItem` 的扩展展示字段必须保持可选，兼容旧 payload。

## 允许依赖

- TypeScript 类型系统。

## 禁止依赖

- 运行时业务包、React、后端 SDK。

## 修改时必须同步更新

- `docs/architecture/module-map.md`。
- `docs/api/*`，如果字段来自后端契约。
