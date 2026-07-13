# 包说明：@test-agent/agent-chat/src

## 职责

渲染 Agent 对话区、RunEvent 派生状态和 opencode 风格消息/工具时间线。

## 主要程序清单

- `AgentChat.vue`：Agent/历史 tab、受控 History 搜索与置顶/删除回调、Agent/Provider/Model selector（Agent 下拉已过滤为 primary+all，排除 subagent/hidden）、runtime status bar、slash command palette、`@` context picker、permission/question dock、opencode-like message part timeline、输入框和动作按钮。
- `AssistantThread.vue`：对话线程视图，负责把消息、运行态、模型目录、Diff 文件、`streamingTextByPartId` 和 Run Session scope 索引传入 `createOpencodeLikeState`，并使用 `OpencodeTimeline` 作为主渲染路径；Todo 面板固定放在 composer 上方；子 Agent 视图隐藏 composer 和 runtime 控制，只展示返回主 Agent 的提示；切入子 Agent 视图要求上层传入对应 `subagentsBySessionId` 索引；不直接接入平台 API。
- `opencode-like/`：opencode 风格时间线模块。`state/adapter.ts` 归并会话输入并按 `activeSubagentSessionId` 过滤主/子时间线，主视图会用保留的 subagent 索引补偿缺失的 task 入口，但只补回仍可见的原始 `taskMessageId`，避免上一轮子 Agent 被合成到后续轮次；`state/projection.ts` 投影时间线行，task 子 Agent 卡片始终独立成行，不进入普通工具折叠组，并在最新 running turn 已有过程项但尚无文本输出时追加单个 `working-status` 行；当 `runtimeStatus.type=retry` 时改为渲染 `retry` 行并显示上游 message/action，避免一直停留在“思考中”；`state/tool-registry.ts` 识别常见工具；`components/` 下按 row、part、tool、primitive 分层渲染，task 工具未绑定 child 时显示为“智能体 / 准备中”，绑定后渲染为可点击子 Agent 卡片；task metadata/output 中的 child sessionId 仅用于卡片展示和上层索引恢复，不能单独绕过子视图守卫；task 卡片保持单行高度，Agent 名使用较小字号完整展示并与任务标题留出间隔；running 文本先走纯文本 live preview，完成后再走 Markdown，reasoning 展开详情使用紧凑纯文本避免 Markdown 首次挂载卡顿，并包含输入框上方的 `TodoPanel.vue`；`styles/` 下集中维护 `.oc-*` token、布局、todo、markdown、diff 和动画样式。
- `runtime-reducer.ts`：归并 RunEvent 对话状态；`run.snapshot.reset` 会保留已有平台持久消息，清空当前 Run 的实时投影并按物化 `snapshot.events` 原顺序重放，空/缺失 snapshot 仍清空，无 type/嵌套 reset 事件安全忽略；将 opencode 重发的 user message/part 合并回当前 user 消息；实时 `message.*` 里的 opencode message id 仅作为 `remoteMessageId` 保留，不生成平台 `platformMessageId`；`session.status.retry` 兼容对象型 `payload.status`，归一化为 `runtimeStatus.type=retry`，固定携带 `retryKey`、60 秒倒计时和 3 次上限供 app 层展示与自动重试；新 Run 请求清理旧 `run.failed` 失败卡和上一轮 Todo 快照，后到成功/取消终态清理同 Run 失败卡，避免旧 `Streaming response failed` 或上一轮任务列表继续污染后续轮次；入口兼容展开 `payload.properties` raw 包装，避免 task 子 Agent 卡片在主视图短暂出现后因 message/part id 未识别而消失；维护 `messageScopesById`、`subagentsBySessionId` 和 `subagentByTaskPartId` 运行期索引，支持原生 pending task 后续由 `session.child.discovered/session.scope.updated` 补齐绑定，并对 `sessionId=child/sessionID=root` 的矛盾 task part 按 root scope 防御性兼容；slash command 展开后的技能正文不会覆盖原始命令或误建 assistant 消息；child scoped `message.part.delta` 缺少现有 assistant message 时会创建独立子会话消息，避免合并进主 Agent 输出；`message.part.delta` 写入 `streamingTextByPartId` 临时 overlay，最终 `updated/removed` 或 message 删除后清理；并导出 `normalizeMessagePart` 供实时事件和历史 `partsJson` 共用同一套 opencode part 字段归一化规则。
- `AgentCard.vue`、`TimelineCard.vue`、`ToolPayloadBlock.vue`：已作废的结构化卡片兼容组件。新对话正文不要再扩展这条主路径，优先在 `opencode-like/` 下新增 part/tool 视图。
- `MessageParts.vue`、`AnswerPart.vue`、`PlainAnswer.vue`、`ReasoningPartBlock.vue`、`ToolPartBlock.vue`、`ToolDetail.vue`、`FilePartBlock.vue`、`SubtaskPartBlock.vue`、`PatchBlock.vue`、`SnapshotBlock.vue`、`StepMarker.vue`、`StepFinishMarker.vue`、`AgentChip.vue`、`RetryBlock.vue`、`CompactionMarker.vue`、`PartMarker.vue`：已作废的旧气泡消息 part 渲染组件，仅为历史兼容和短期比对保留。
- `ProcessDisclosure.vue`：存量折叠壳，仍被 `TaskBreakdown.vue` 等局部视图复用；不要用它恢复旧对话主路径或新 Todo 面板。
- `ComposerArea.vue`、`RuntimeControls.vue`、`ChicPopover.vue`、`RuntimeDock.vue`、`SuggestionPanel.vue`：仍在当前 Agent 面板中使用的对话区子组件；`TaskBreakdown.vue` 仅保留存量兼容，新 Todo 展示使用 `opencode-like/components/TodoPanel.vue`。
- `chat-utils.ts`：斜杠/上下文查询、附件合并、流式指纹、卡片默认展开判定等纯函数。
- `process-status.ts`：过程状态归一化、中文文案、状态色和 Skill tool 判定工具。
- `prompt-parts.ts`：浏览器文件和图片到平台 `PromptPart` 的纯转换，供 composer 和单测复用。
- `user-message-display.ts`：用户消息展示文案与工作区上下文 chip 派生工具；历史 `<context>` 文本只用于兼容解析，原生 file prompt parts 优先展示，并按 `type/path/lines` 去重。乐观 user message 会剥离 `content`、内联 URL 和 `source.text`，只保留用户原始问题与附件展示元数据；模型提交 parts 不经过该展示转换。
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
