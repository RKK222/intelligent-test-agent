# @test-agent/agent-chat

## 工程定位

Agent 对话和结构化卡片展示包。

## 主要职责

- 展示用户/助手消息。
- 展示 message part timeline（text、reasoning、tool、file、event），用户消息使用右侧浅灰气泡。
- `reasoning` 与最终 `text` 回答必须分块展示；最终回答使用最清晰的白底正文块，思考过程使用弱化折叠块，避免把思考过程和答复混在同一个气泡里。
- 结构化 Agent 时间线使用浅色低对比折叠卡片壳展示 plan、tool、test、diff、event：标题行包含图标、标题和展开/收起按钮；内容区按类型展示步骤、工具摘要、测试命令、变更文件表格或紧凑 JSON fallback。
- 时间线默认只展开运行中卡片、最新工具卡片和最新 Diff 卡片；历史完成项默认折叠，避免长输出撑乱工作台。
- 提供 Phase 11 Agent/Provider/Model/Mode selector、runtime status bar、slash command palette、`@` context picker、permission dock、question dock 和线程内任务分解展示。
- Skill 调用不新增独立卡片类型或 `skill.*` 事件；当 tool/message part 的 `tool` 或 `toolName` 为 `skill` 时，在前端分类展示为 Skill 调用块，并展示 Skill 名称、用途、状态和折叠详情。
- Prompt composer 支持文本、文件附件、图片附件和附件 chips；文件读取后只向 app 层返回平台 `PromptPart`，不直接提交后端。
- History tab 支持受控搜索、选择会话、置顶/取消置顶和删除回调；实际 API 调用由 app 层完成。
- 提供纯 RunEvent reducer，把 `message.part.delta`、permission/question、todo、diff 等事件归并为对话展示状态。
- 提供发送、取消、重试、打开 Diff 回调。
- 自建最小 chat 运行时（基于纯 reducer + Vue 组合式状态），不依赖外部对话 UI 框架。
- `assistant.message.delta` 旧事件继续作为兼容输入；新 `message.part.delta` 优先按 messageId/partId 合并，避免流式输出重复。
- Agent/Provider/Model/Mode selector、slash command、`@` context、permission/question dock、Todo 和 runtime status 只暴露受控回调，HTTP 提交与 SSE 订阅仍由 app 层负责。
- Timeline、dock、附件 chips、任务分解和 Skill/Tool cards 必须使用固定区域和换行策略，Agent 对话线程必须有独立 sticky scroll 区域：用户在底部时自动跟随，用户向上阅读时保留位置并提示有新内容，避免长命令、长路径、图片名或 streaming 文本撑开工作台。

## 禁止事项

- 不直接启动 Run。
- 不订阅 SSE。
- 不调用 opencode server。
- 不创建 terminal ticket，不调用 backend-api。
