# @test-agent/agent-chat

## 工程定位

Agent 对话运行态展示包。主对话视图采用 opencode 风格的消息/工具时间线，负责把平台 `AgentMessage`、message part、运行态和 Diff 摘要投影为可渲染的前端状态。

## 主要职责

- 展示用户/助手消息。`AssistantThread` 和 agent-web 的 `FigmaChatPanel` 主路径均复用 `OpencodeTimeline`，不再以旧气泡/结构化卡片作为主要正文渲染方式；助手侧只保留头像作为来源标识，不额外展示名称/时间行，以匹配用户气泡无名称的极简对话形态。
- 右侧 Agent 面板保留 Figma Web IDE 风格的 47px Chat/History 顶部 tab、紧凑消息流和底部 composer/runtime 控制区，适配约 245px 窄面板。
- `opencode-like/state` 提供 `createOpencodeLikeState` 与 `createTimelineRows`，把用户消息、孤立助手历史消息、assistant parts、运行态、Diff 文件、permission/question/todo 与模型目录归并为稳定时间线行。
- `opencode-like` 基于 RunEvent scope 区分主 Agent 与子 Agent 时间线：主视图过滤 child scoped 输出，仅展示 root 输出和 task 子 Agent 入口卡片；点击入口后切换到子 Agent 时间线，子视图隐藏输入框并只提供返回主 Agent 的提示。
- 展示 message part timeline（text、reasoning、tool、file、retry 以及未知 part fallback）。旧 `card` 消息中的 Diff payload 会被收敛为 `diff-summary` 行；存量 `AgentCard`/`TimelineCard` 仅保留兼容，不作为主对话路径。
- `reasoning`、最终 `text`、工具调用和文件引用分块展示，避免把思考、工具日志和最终答复混入同一个气泡；同一用户回合内被多个 assistant message 拆开的思考状态会合并为一个过程行，运行中仅保留一个轻量工作中动效。
- 工具调用按 opencode 常见工具拆分专用视图：bash、read、list、glob、grep、edit、write、apply_patch、webfetch、websearch、task、skill、question；同一用户回合内被拆成多条 assistant message 的同类型工具会合并成一个默认折叠的工具组，展开后仍渲染每条原始工具详情；读取/检索类上下文工具默认合并为折叠的上下文组，失败工具进入对应工具类型归并并保留失败状态。
- 工具视图统一使用 `.oc-*` primitives 和轻量折叠壳，工具详情默认折叠，过程行的标题、摘要、状态和展开箭头使用固定列对齐；最终文本直接以轻量气泡展示，不额外加“最终输出”标题，并保留复制按钮；工作区内长绝对路径在列表中展示为面向用户的短路径，完整路径只保留在悬浮提示中，避免 `.testagent`/personal worktree 前缀撑开对话区域。
- 运行中状态以 `thinking` 行和工具状态展示；失败运行追加统一错误行。
- 提供 Agent/Model/Mode selector、runtime status bar、slash command palette、`@` context picker、permission dock、question dock 和输入框上方 `TodoPanel`；Todo 收起态展示待处理/进行中/已完成/已取消/其他和总数，展开态展示任务列表、状态和优先级。模型选择器按 Provider 分组展示模型，选择模型时同步更新 Provider 与 Model。
- Skill 调用不新增独立卡片类型或 `skill.*` 事件；当 tool/message part 的 `tool` 或 `toolName` 为 `skill` 时，在前端展示为 Skill 调用块，展示 Skill 名称、用途、状态和折叠详情。
- Prompt composer 支持文本、文件附件、图片附件和附件 chips；文件读取后只向 app 层返回平台 `PromptPart`，不直接提交后端。
- History tab 支持受控搜索、选择会话、置顶/取消置顶和删除回调；实际 API 调用和历史正文加载态由 app 层完成，正文展示不等待消息反馈等附属请求。
- 提供纯 RunEvent reducer，把 `message.part.delta`、permission/question、todo、diff、Run Session scope 等事件归并为对话展示状态；opencode 重发的远端 user message/part 会合并回当前乐观 user 消息，slash command 展开后的技能正文不会覆盖用户原始命令、误建 assistant 消息或拼入回答；reducer 会在入口展开历史或调试场景中的 `payload.properties` raw 包装，确保 task 子 Agent 卡片和 scope 索引仍按同一套字段识别；child scoped delta 缺少现有 assistant message 时会创建独立子会话消息，不会 fallback 合并到主 Agent 最后一条 assistant；`message.part.delta` 的临时文本进入 `streamingTextByPartId` overlay，待 `message.part.updated/removed` 或 message 删除后清理，避免流式文本重复写入最终 part。
- 提供发送、取消、重试、打开 Diff 回调。
- Agent 面板 tabbar 最左侧提供"实时追踪"toggle 按钮（`liveTrack` prop 受控、`toggleLiveTrack` emit 回调）：开启后由 app 层在 agent 每次调用 write/edit/apply_patch 完成时自动把该文件以只读预览打开在中间编辑器并读取磁盘最新内容刷新；本包只负责按钮态展示与切换回调，不订阅事件、不操作编辑器。
- 自建最小 chat 运行时（基于纯 reducer + Vue 组合式状态），不依赖外部对话 UI 框架。
- `assistant.message.delta` 旧事件继续作为兼容输入；新 `message.part.delta` 优先按 messageId/partId 合并，避免流式输出重复。
- Agent/Model/Mode selector、slash command、`@` context、permission/question dock、Todo 和 runtime status 只暴露受控回调，HTTP 提交与 SSE 订阅仍由 app 层负责。
- Timeline、dock、附件 chips、任务分解和 Skill/Tool 视图必须使用固定区域和换行策略，Agent 对话线程必须有独立 sticky scroll 区域：用户在底部时自动跟随，用户向上阅读时保留位置并提示有新内容，避免长命令、长路径、图片名或 streaming 文本撑开工作台。
- `text`/`reasoning` part 通过 `MarkdownView` 懒加载 markdown-it + DOMPurify + highlight.js 渲染 Markdown、表格、链接与代码块（首屏不打包），并提供"查看原文"切换；流式生成的 text 会显示"生成中"光标。
- `step-start`/`step-finish` 内嵌 snapshot 默认折叠展示；`step-finish` 把 tokens 拆分为 input/output/reasoning 并按数量级动态展示 cost 精度。
- `patch` part 把 `filesMap`/`fileStats` 收敛到 `metadata`，文件列表支持展开查看每文件 diff、显示 +/- 行数，并提供 hash 完整值一键复制。

## 作废代码边界

- 旧气泡消息 part 路径 `MessageParts.vue` 及其子组件（`AnswerPart.vue`、`PlainAnswer.vue`、`ReasoningPartBlock.vue`、`ToolPartBlock.vue`、`ToolDetail.vue`、`FilePartBlock.vue`、`SubtaskPartBlock.vue`、`PatchBlock.vue`、`SnapshotBlock.vue`、`StepMarker.vue`、`StepFinishMarker.vue`、`AgentChip.vue`、`RetryBlock.vue`、`CompactionMarker.vue`、`PartMarker.vue`）已作废，仅为历史兼容和短期比对保留；新 message part 展示必须在 `opencode-like/` 下扩展。
- 旧结构化卡片路径 `AgentCard.vue`、`TimelineCard.vue`、`ToolPayloadBlock.vue` 已作废；旧 `card` 消息中的 Diff payload 由 `opencode-like/state` 收敛为 `diff-summary` 行展示。
- `FigmaChatPanel.vue` 中旧 `.figma-chat-*` 气泡消息循环已从主路径禁用，不再作为新交互或新样式的修改入口。
- `FigmaChatPanel.vue` 中旧底部实时任务面板已作废并停止渲染；运行中工具/事件只由 `OpencodeTimeline` 展示，避免任务列表与时间线事件来自不同聚合逻辑。
- `ProcessDisclosure.vue` 仍被 `TaskBreakdown.vue` 等存量局部视图复用，不整体标废；但不要用它恢复旧对话主路径或新的 Todo 展示，新 Todo 展示必须使用 `opencode-like/components/TodoPanel.vue`。

## 禁止事项

- 不直接启动 Run。
- 不订阅 SSE。
- 不调用 opencode server。
- 不创建 terminal ticket，不调用 backend-api。
