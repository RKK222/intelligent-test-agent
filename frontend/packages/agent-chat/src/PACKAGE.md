# 包说明：@test-agent/agent-chat/src

## 职责

渲染 Agent 对话区、RunEvent 派生状态和 opencode 风格消息/工具时间线。

## 主要程序清单

- `AgentChat.vue`：Agent/历史 tab、受控 History 搜索与置顶/删除回调、Agent/Provider/Model selector（Agent 下拉已过滤为 primary+all，排除 subagent/hidden）、runtime status bar、slash command palette、`@` context picker、permission/question dock、opencode-like message part timeline、输入框和动作按钮。
- `AssistantThread.vue`：对话线程视图，负责把消息、运行态、模型目录、Diff 文件和 `streamingTextByPartId` 传入 `createOpencodeLikeState`，并使用 `OpencodeTimeline` 作为主渲染路径；Todo 面板固定放在 composer 上方；不直接接入平台 API。
- `opencode-like/`：opencode 风格时间线模块。`state/adapter.ts` 归并会话输入，`state/projection.ts` 投影时间线行，`state/tool-registry.ts` 识别常见工具；`components/` 下按 row、part、tool、primitive 分层渲染，并包含输入框上方的 `TodoPanel.vue`；`styles/` 下集中维护 `.oc-*` token、布局、todo、markdown、diff 和动画样式。
- `runtime-reducer.ts`：归并 RunEvent 对话状态，将 opencode 重发的 user message/part 合并回当前 user 消息；slash command 展开后的技能正文不会覆盖原始命令或误建 assistant 消息；`message.part.delta` 写入 `streamingTextByPartId` 临时 overlay，最终 `updated/removed` 或 message 删除后清理；并导出 `normalizeMessagePart` 供实时事件和历史 `partsJson` 共用同一套 opencode part 字段归一化规则。
- `AgentCard.vue`、`TimelineCard.vue`、`ToolPayloadBlock.vue`：已作废的结构化卡片兼容组件。新对话正文不要再扩展这条主路径，优先在 `opencode-like/` 下新增 part/tool 视图。
- `MessageParts.vue`、`AnswerPart.vue`、`PlainAnswer.vue`、`ReasoningPartBlock.vue`、`ToolPartBlock.vue`、`ToolDetail.vue`、`FilePartBlock.vue`、`SubtaskPartBlock.vue`、`PatchBlock.vue`、`SnapshotBlock.vue`、`StepMarker.vue`、`StepFinishMarker.vue`、`AgentChip.vue`、`RetryBlock.vue`、`CompactionMarker.vue`、`PartMarker.vue`：已作废的旧气泡消息 part 渲染组件，仅为历史兼容和短期比对保留。
- `ProcessDisclosure.vue`：存量折叠壳，仍被 `TaskBreakdown.vue` 等局部视图复用；不要用它恢复旧对话主路径或新 Todo 面板。
- `ComposerArea.vue`、`RuntimeControls.vue`、`ChicPopover.vue`、`RuntimeDock.vue`、`SuggestionPanel.vue`：仍在当前 Agent 面板中使用的对话区子组件；`TaskBreakdown.vue` 仅保留存量兼容，新 Todo 展示使用 `opencode-like/components/TodoPanel.vue`。
- `chat-utils.ts`：斜杠/上下文查询、附件合并、流式指纹、卡片默认展开判定等纯函数。
- `process-status.ts`：过程状态归一化、中文文案、状态色和 Skill tool 判定工具。
- `prompt-parts.ts`：浏览器文件和图片到平台 `PromptPart` 的纯转换，供 composer 和单测复用。
- `runtime-reducer.ts`：纯 RunEvent reducer，归并旧 `assistant.message.delta` 和 `message.*`、permission/question、todo、diff/session status 事件。

## 允许依赖

- Vue 3。
- `@test-agent/shared-types`。
- `@test-agent/ui-kit`。
- lucide-vue-next。

## 禁止依赖

- backend-api、event-stream-client、opencode server。
- terminal ticket 创建、WebSocket 生命周期或文件系统直读。

## 修改时必须同步更新

- 本包 README。
- 前端测试规范中对应卡片测试。
