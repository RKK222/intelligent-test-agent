# @test-agent/agent-chat

## 工程定位

Agent 对话和结构化卡片展示包。

## 主要职责

- 展示用户/助手消息。
- 右侧 Agent 面板使用 Figma Web IDE 风格的 47px Chat/History 顶部 tab、紧凑消息流和底部 composer/runtime 控制区，适配约 245px 窄面板。
- 展示 message part timeline（text、reasoning、tool、file、subtask、step-start、step-finish、snapshot、patch、agent、retry、compaction），用户消息使用右侧浅灰气泡。
- `reasoning` 与最终 `text` 回答必须分块展示；最终回答使用最清晰的白底正文块，思考过程使用弱化折叠块，避免把思考过程和答复混在同一个气泡里。
- 结构化 Agent 时间线使用浅色低对比折叠卡片壳展示 plan、tool、test、diff、event：标题行包含图标、标题和展开/收起按钮；内容区按类型展示步骤、工具摘要、测试命令、变更文件表格或紧凑 JSON fallback。
- 时间线默认只展开运行中卡片、最新工具卡片和最新 Diff 卡片；历史完成项默认折叠，避免长输出撑乱工作台。
- 提供 Agent/Model/Mode selector、runtime status bar、slash command palette、`@` context picker、permission dock、question dock 和线程内任务分解展示；模型选择器按 Provider 分组展示模型，选择模型时同步更新 Provider 与 Model。
- Skill 调用不新增独立卡片类型或 `skill.*` 事件；当 tool/message part 的 `tool` 或 `toolName` 为 `skill` 时，在前端分类展示为 Skill 调用块，并展示 Skill 名称、用途、状态和折叠详情。
- Prompt composer 支持文本、文件附件、图片附件和附件 chips；文件读取后只向 app 层返回平台 `PromptPart`，不直接提交后端。
- History tab 支持受控搜索、选择会话、置顶/取消置顶和删除回调；实际 API 调用由 app 层完成。
- 提供纯 RunEvent reducer，把 `message.part.delta`、permission/question、todo、diff 等事件归并为对话展示状态；导出的 `normalizeMessagePart` 统一兼容 opencode `id/tool/state.input/metadata/status/time` 原始结构和平台规范化结构，供实时事件与历史 `partsJson` 恢复共同复用。
- 提供发送、取消、重试、打开 Diff 回调。
- Agent 面板 tabbar 最左侧提供"实时追踪"toggle 按钮（`liveTrack` prop 受控、`toggleLiveTrack` emit 回调）：开启后由 app 层在 agent 每次调用 write/edit/apply_patch 完成时自动把该文件以只读预览打开在中间编辑器并读取磁盘最新内容刷新；本包只负责按钮态展示与切换回调，不订阅事件、不操作编辑器。
- 自建最小 chat 运行时（基于纯 reducer + Vue 组合式状态），不依赖外部对话 UI 框架。
- `assistant.message.delta` 旧事件继续作为兼容输入；新 `message.part.delta` 优先按 messageId/partId 合并，避免流式输出重复。
- Agent/Model/Mode selector、slash command、`@` context、permission/question dock、Todo 和 runtime status 只暴露受控回调，HTTP 提交与 SSE 订阅仍由 app 层负责。
- Timeline、dock、附件 chips、任务分解和 Skill/Tool cards 必须使用固定区域和换行策略，Agent 对话线程必须有独立 sticky scroll 区域：用户在底部时自动跟随，用户向上阅读时保留位置并提示有新内容，避免长命令、长路径、图片名或 streaming 文本撑开工作台。
- `text`/`reasoning` part 通过 `MarkdownView` 懒加载 markdown-it + DOMPurify + highlight.js 渲染 Markdown、表格、链接与代码块（首屏不打包），并提供"查看原文"切换；流式生成的 text 会显示"生成中"光标。
- `step-start`/`step-finish` 内嵌 snapshot 默认折叠展示；`step-finish` 把 tokens 拆分为 input/output/reasoning 并按数量级动态展示 cost 精度。
- `patch` part 把 `filesMap`/`fileStats` 收敛到 `metadata`，文件列表支持展开查看每文件 diff、显示 +/- 行数，并提供 hash 完整值一键复制。

## 禁止事项

- 不直接启动 Run。
- 不订阅 SSE。
- 不调用 opencode server。
- 不创建 terminal ticket，不调用 backend-api。
