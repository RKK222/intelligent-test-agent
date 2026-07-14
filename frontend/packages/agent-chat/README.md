# @test-agent/agent-chat

## 工程定位

Agent 对话运行态展示包。主对话视图采用 opencode 风格的消息/工具时间线，负责把平台 `AgentMessage`、message part、运行态和 Diff 摘要投影为可渲染的前端状态。

## 主要职责

- 展示用户/助手消息。`AssistantThread` 和 agent-web 的 `FigmaChatPanel` 主路径均复用 `OpencodeTimeline`，不再以旧气泡/结构化卡片作为主要正文渲染方式；用户气泡展示层优先读取 user message 的原生 `file` parts 展示本轮关联的工作区文件/选区 chip，只显示用户原始提问。乐观 user message 进入 Timeline 前会把 file part 收敛为路径、文件名和选区行号等展示元数据，不携带 `content`、内联 URL 或 `source.text`；模型请求仍使用完整 parts。历史旧消息若仍是前端序列化的工作区 `<context>` 文本，会降级解析为同样的 chip 且隐藏正文上下文块；用户与助手侧均不展示头像或名称/时间行，以极简消息来源布局匹配主时间线，用户问题保持右对齐，使用语义浅灰气泡，并通过 12px 下间距与助手过程和回答分隔；助手过程与回答继续使用完整可用宽度。
- 右侧 Agent 面板保留 Figma Web IDE 风格的 47px Chat/History 顶部 tab、紧凑消息流和底部 composer/runtime 控制区，适配约 245px 窄面板。
- `opencode-like/state` 提供 `createOpencodeLikeState` 与 `createTimelineRows`，把用户消息、孤立助手历史消息、assistant parts、运行态、Diff 文件、permission/question/todo 与模型目录归并为稳定时间线行。
- `OpencodeTimeline` 在当前可见时间线用户对话轮次大于 3 时显示左侧中线对话定位器；定位器弹层列出全部轮次的用户问题、助手摘要和最多 2 个文件 chips，点击轮次会滚动定位到对应用户消息。该能力只消费现有 `AgentMessage`/message part 投影，不新增 API、事件或持久化字段。
- `opencode-like` 基于 RunEvent scope 区分主 Agent 与子 Agent 时间线：主视图过滤 child scoped 输出，仅展示 root 输出和 task 子 Agent 入口卡片；原生 pending task 先显示为不可点击“智能体 / 准备中”，收到 child discovery 或上层恢复出的 subagent 索引后转换为 `Explore + title` 可点击入口；点击入口后切换到子 Agent 时间线，子视图隐藏输入框并只提供返回主 Agent 的提示。
- 展示 message part timeline（text、reasoning、tool、file、retry 以及未知 part fallback）。旧 `card` 消息中的 Diff payload 会被收敛为 `diff-summary` 行；存量 `AgentCard`/`TimelineCard` 仅保留兼容，不作为主对话路径。
- `reasoning`、最终 `text`、工具调用和文件引用分块展示，避免把思考、工具日志和最终答复混入同一个气泡；同一用户回合内被多个 assistant message 拆开的思考状态会合并为一个过程行，默认折叠但在折叠头中保留一行实时摘要，运行中仅保留一个轻量工作中动效。
- 工具调用按 opencode 常见工具拆分专用视图：bash、read、list、glob、grep、edit、write、apply_patch、webfetch、websearch、task、skill、question；同一用户回合内被拆成多条 assistant message 的同类型工具会合并成一个默认折叠的工具组，但 task 与 question 始终按原始调用独立保留时间线位置。question 完成态显示“已回答”，展开后按问题顺序展示问题和回答；预置答案展示 label 与 description，单选、多选和自定义文本均按 OpenCode `metadata.answers` 配对，自定义答案直接以答案原文作为 label。读取/检索类上下文工具默认合并为折叠的上下文组，失败工具进入对应工具类型归并并保留失败状态。
- `diff-summary` 文件修改行默认折叠，折叠态在标题右侧展示全部文件的新增/删除行数汇总；点击标题展开文件列表，文件条目仍负责触发打开对应文件。
- `diff-summary` 文件修改行默认折叠，折叠态在标题右侧展示全部文件的新增/删除行数汇总；汇总数字随文件变化刷新并短暂跳动反馈，点击标题展开文件列表，文件条目仍负责触发打开对应文件。
- `reasoning`、最终 `text`、工具调用和文件引用分块展示，避免把思考、工具日志和最终答复混入同一个气泡；同一用户回合内被多个 assistant message 拆开的思考状态会合并为一个过程行，工具/思考已发生但文本尚未开始时仅投影一个轻量工作中动效。
- 工具调用按 opencode 常见工具拆分专用视图：bash、read、list、glob、grep、edit、write、apply_patch、webfetch、websearch、task、skill、question；同一用户回合内被拆成多条 assistant message 的普通同类型工具会合并成一个默认折叠的工具组，展开后仍渲染每条原始工具详情；task 子 Agent 卡片与 question 提问卡片始终独立展示，不进入工具组折叠；读取/检索类上下文工具默认合并为折叠的上下文组，失败工具进入对应工具类型归并并保留失败状态。
- 工具视图统一使用 `.oc-*` primitives 和轻量折叠壳，工具详情默认折叠，过程行的标题、摘要、状态和展开箭头使用固定列对齐；最终文本直接以轻量气泡展示，不额外加“最终输出”标题，并保留复制按钮；工作区内长绝对路径在列表中展示为面向用户的短路径，完整路径只保留在悬浮提示中，避免 `.testagent`/personal worktree 前缀撑开对话区域。
- 运行中状态以 `thinking` 行、工具状态和单个 `working-status` 行展示；会话级 `running` 只覆盖最新用户轮次，历史轮次的 context/reasoning/tool 分组始终保留各 part 已收敛的完成或失败状态，发送下一轮消息不会把上一轮重新标记为进行中；`runtimeStatus.type=retry` 时改为展示 retry 行和倒计时文案，不再追加“思考中”或空工作态；retry 行展示原始错误内容、可选 action 链接和“重试中 N 秒后 - 第 X 次 / 共 3 次”，失败运行追加统一错误行。
- 提供 Agent/Model/Mode selector、runtime status bar、slash command palette、`@` context picker、permission dock、question dock 和输入框上方 `TodoPanel`；question dock 只能由 RunEvent `question.asked` 归并出的 `QuestionRequest` 驱动，分页展示单选/多选/文本题、选项说明和自定义答案输入，提交时使用选项 label 或自定义文本；Todo 收起态展示待处理/进行中/已完成/已取消/其他和总数，展开态展示任务列表、状态和优先级。模型选择器按 Provider 分组展示模型，选择模型时同步更新 Provider 与 Model。
- Skill 调用不新增独立卡片类型或 `skill.*` 事件；当 tool/message part 的 `tool` 或 `toolName` 为 `skill` 时，在前端展示为 Skill 调用块，展示 Skill 名称、用途、状态和折叠详情。
- Prompt composer 支持文本、文件附件、图片附件和附件 chips；文件读取后只向 app 层返回平台 `PromptPart`，不直接提交后端。
- History tab 支持受控搜索、选择会话、置顶/取消置顶和删除回调；实际 API 调用和历史正文加载态由 app 层完成，正文展示不等待消息反馈等附属请求。
- 提供纯 RunEvent reducer，把 `message.part.delta`、permission/question、todo、diff、Run Session scope 和 `session.status.retry` 等事件归并为对话展示状态；收到 `run.snapshot.reset` 时，先保留已有平台持久消息（带 `platformMessageId` 的非 card 消息），清空当前 Run 的实时消息/part、工具卡、Todo、Diff、permission/question、scope 索引、增量 overlay 和去重状态，再按 `snapshot.events` 原顺序重放。snapshot 缺失或为空也会执行清空；缺少字段的内部事件使用外层 run/trace/time 安全兜底，无 `type` 或嵌套 reset 的坏事件会被忽略；reset 本身按 eventId 幂等但不推进 durable 游标。
- `question.asked` 兼容 opencode 原生 `question/header/options/multiple/custom` 字段，`multiple:true` 映射为多选，存在 `options` 且非多选映射为单选，无选项映射为文本题，并保留可选 `tool.messageID/callID` 作为原 question 工具关联；普通 `message.part.updated` 文本和 `tool:"question"` 过程不生成提问请求。回复 HTTP 成功的本地 action 或后到的 `question.replied` 事件都会移除待答 dock，并按工具关联把原 question part 更新为 `completed`、将答案写入 `metadata.answers`；后续真实 assistant 文本继续保留在时间线。retry 状态固定携带 `retryKey`、60 秒倒计时和 3 次上限供 app 层展示与自动重试；opencode 重发的远端 user message/part 会合并回当前乐观 user 消息，实时 `message.*` 里的 opencode message id 只写入 `remoteMessageId`，不会伪造平台 `platformMessageId`；新 Run 请求会清理旧 `run.failed` 失败卡和上一轮 Todo 快照，后到 `run.succeeded/run.cancelled` 会移除同 Run 的失败卡，确保运行态展示按最后终态收敛，Todo 面板只展示当前 Run 最近一次 `todo.updated` 快照；slash command 展开后的技能正文不会覆盖用户原始命令、误建 assistant 消息或拼入回答；reducer 会在入口展开历史或调试场景中的 `payload.properties` raw 包装，确保 task 子 Agent 卡片和 scope 索引仍按同一套字段识别；task 子 Agent 卡片优先展示 subagent 状态，缺失时会从工具 `input.subagent_type` 以及 `metadata.agentName/agent/title` 兜底展示名字和标题，并可用 task `callId` 或上层已恢复 subagent 索引匹配点击入口；进入子 Agent 视图时除 `sessionId` 外也按 child scoped `taskPartId/taskCallId` 展示对应输出；对历史 live payload 中 `sessionId=child/sessionID=root` 的矛盾 task part，reducer 按 root task part 处理；`opencode-like/state` 会在主视图用保留的 `subagentsBySessionId/subagentByTaskPartId` 索引补偿缺失的 task 入口，避免 snapshot 或 removed 事件让子 Agent 入口消失，补偿入口只会挂回仍可见的原始 `taskMessageId`，不会猜测挂到后续用户轮次；child scoped delta 缺少现有 assistant message 时会创建独立子会话消息，不会 fallback 合并到主 Agent 最后一条 assistant；`message.part.delta` 的临时文本进入 `streamingTextByPartId` overlay，待 `message.part.updated/removed` 或 message 删除后清理，避免流式文本重复写入最终 part。
- 提供发送、取消、重试、打开 Diff 回调。
- OpenCode 1.17.7 的 `todowrite` 并不保证触发 `todo.updated`；reducer 会把 `message.part.updated` 中 `tool=todowrite` 的 `input.todos` 或 `metadata.todos` 视为同一份最新快照。历史恢复会从最近的 todowrite part 兜底，空数组仍表示明确清空待办。
- Agent 面板 tabbar 最左侧提供"实时追踪"toggle 按钮（`liveTrack` prop 受控、`toggleLiveTrack` emit 回调）：开启后由 app 层在 agent 每次调用 write/edit/apply_patch 完成时自动把该文件以只读预览打开在中间编辑器并读取磁盘最新内容刷新；本包只负责按钮态展示与切换回调，不订阅事件、不操作编辑器。
- 自建最小 chat 运行时（基于纯 reducer + Vue 组合式状态），不依赖外部对话 UI 框架。
- `assistant.message.delta` 旧事件继续作为兼容输入；新 `message.part.delta` 优先按 messageId/partId 合并，避免流式输出重复。
- Agent/Model/Mode selector、slash command、`@` context、permission/question dock、Todo 和 runtime status 只暴露受控回调，HTTP 提交与 SSE 订阅仍由 app 层负责。
- Timeline、dock、附件 chips、任务分解和 Skill/Tool 视图必须使用固定区域和换行策略，Agent 对话线程必须有独立 sticky scroll 区域：用户在底部时自动跟随，用户向上阅读时保留位置并提示有新内容，避免长命令、长路径、图片名或 streaming 文本撑开工作台。
- 已完成的 `text` 与 `reasoning` part 通过 `MarkdownView` 懒加载 markdown-it + DOMPurify + highlight.js 渲染 Markdown、表格、链接与代码块（首屏不打包）；running/pending `text` part 先以轻量纯文本 live preview 展示实际增长内容和"生成中"状态，完成后再切换为 Markdown 渲染。`MarkdownView` 在非空 source 重新渲染 pending 时保留已有 HTML，空白 `text` part 不进入主时间线，避免主/子 Agent 切换时挂载无内容的 Markdown 占位。
- `step-start`/`step-finish` 内嵌 snapshot 默认折叠展示；`step-finish` 把 tokens 拆分为 input/output/reasoning 并按数量级动态展示 cost 精度。
- `patch` part 把 `filesMap`/`fileStats` 收敛到 `metadata`，文件列表支持展开查看每文件 diff、显示 +/- 行数，并提供 hash 完整值一键复制。

## 作废代码边界

- 旧气泡消息 part 路径 `MessageParts.vue` 及其子组件（`AnswerPart.vue`、`PlainAnswer.vue`、`ReasoningPartBlock.vue`、`ToolPartBlock.vue`、`ToolDetail.vue`、`FilePartBlock.vue`、`SubtaskPartBlock.vue`、`PatchBlock.vue`、`SnapshotBlock.vue`、`StepMarker.vue`、`StepFinishMarker.vue`、`AgentChip.vue`、`RetryBlock.vue`、`CompactionMarker.vue`、`PartMarker.vue`）已作废，仅为历史兼容和短期比对保留；新 message part 展示必须在 `opencode-like/` 下扩展。
- 旧结构化卡片路径 `AgentCard.vue`、`TimelineCard.vue`、`ToolPayloadBlock.vue` 已作废；旧 `card` 消息中的 Diff payload 由 `opencode-like/state` 收敛为 `diff-summary` 行展示。
- `FigmaChatPanel.vue` 中旧 `.figma-chat-*` 气泡消息循环已从主路径禁用，不再作为新交互或新样式的修改入口。
- `FigmaChatPanel.vue` 中旧底部实时任务面板已作废并停止渲染；运行中工具/事件只由 `OpencodeTimeline` 展示，避免任务列表与时间线事件来自不同聚合逻辑。
- `FigmaChatPanel.vue` 中旧本地文本启发式选择题面板已作废；提问 UI 只能由 RunEvent `question.asked` 归并出的 `QuestionRequest` 驱动，普通 assistant 编号列表和 `message.part.updated` 的 question 工具过程必须按正文/过程展示，不得弹出提问面板。
- `ProcessDisclosure.vue` 仍被 `TaskBreakdown.vue` 等存量局部视图复用，不整体标废；但不要用它恢复旧对话主路径或新的 Todo 展示，新 Todo 展示必须使用 `opencode-like/components/TodoPanel.vue`。

## 禁止事项

- 不直接启动 Run。
- 不订阅 SSE。
- 不调用 opencode server。
- 不创建 terminal ticket，不调用 backend-api。
