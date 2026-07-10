# 原生 Timeline 外层专注对话设计

## 目标

在不调整现有 OpenCode 风格 Timeline、消息投影或 part 渲染的前提下，降低对话面板中运行过程信息带来的视觉干扰。保留原生输出的头像、消息行、工具、子 Agent、Ask、推理与折叠交互；只在外层提供统一、可按需打开的本轮活动入口。

## 明确约束

- `frontend/packages/agent-chat/src/opencode-like/` 下的 `OpencodeTimeline`、`TimelineRow`、各 row/part/tool 组件及其样式不在本次修改范围。
- 不调整现有 Timeline 的 DOM 结构、`createTimelineRows` 投影规则、消息顺序、子 Agent 选择、工具展开或 OpenCode 原生输出语义。
- 因此保留原生 Timeline 已有头像；“去头像”不属于本次范围，避免通过局部 CSS 覆盖破坏原生表现。
- 不新增 API、事件、数据库字段或服务端逻辑；所有状态均来自既有前端运行时 state。

## 方案

### 1. 外层活动入口

在 `FigmaChatPanel` 这个已有 Timeline 宿主周围新增一个紧凑的“本轮活动”入口，不插入 Timeline 内部。入口只给出当前运行摘要，例如“正在处理 · 1 个子任务、3 项操作”；无运行内容时不显示。

入口通过既有状态归纳：

- `todos`：任务数量与进行中状态；
- `subagentsBySessionId`：子 Agent 数量、状态和摘要；
- `questions` / `permissions`：待用户确认数量；
- 现有 runtime 状态：运行中、完成或失败状态。

“本轮”由当前 `sessionId` 决定：运行摘要、Todo、子 Agent 和失败摘要使用当前 active Run；Ask / Permission 使用当前 session 内、现有原生运行态仍判定为待处理的项及其关联 `runId`。Run 结束后，待处理 Ask / Permission 继续可见直到其被现有原生状态标记为已处理、拒绝、失效或会话切换；新 Run 开始不自行丢弃旧待处理项，也不自行合成跨 Run 项，完全复用原生状态的存活与清除结果。会话切换、Run 重连、Run 完成或 active Run 更新时，活动摘要必须从既有 session/run 归一化状态重新计算，不能缓存或串入其他 session 的状态。

点击入口只打开本轮活动面板；不影响 Timeline 的滚动、自动跟随、`查看新内容`、反馈操作或 Composer。

### 2. 活动面板内容与优先级

活动面板采用单一列表，不嵌套多层卡片：

1. 待用户处理的 Ask / Permission，始终置顶，并提示“请在对话中处理”；不搬运、复刻或在面板内触发原生 Timeline 的处理组件或 action handler；
2. 运行中的子 Agent；
3. 运行中的任务；
4. 当前 active Run 的失败状态；只读展示既有 Run 状态文本，不增加重试入口。当前 `Run` 未提供可安全复用的错误摘要字段，因此不在活动面板伪造或另行采集错误详情。

活动面板仅做只读摘要，不反向读取、复制或重新投影 Timeline 的工具完成记录。需要查看原生 Ask、Permission、工具详情、完整子 Agent 输出、推理、Diff 或消息上下文时，仍由 Timeline 的现有渲染、展开与操作承接。

### 3. 响应式行为

| 可用聊天面板宽度 | 布局 | 行为 |
| --- | --- | --- |
| ≥ 720px | 锚定于活动入口的非模态浮层 | 覆盖在 Timeline 上方，不压缩消息正文宽度；浮层随可用空间翻转，具有最大高度并独立滚动；点击外部或 Escape 关闭。 |
| < 720px | 底部模态抽屉 | 始终单列；抽屉可关闭，不能遮挡 Composer 的输入与提交操作。 |
| 极窄宽度 | 全宽单列抽屉 | 摘要文字截断并保留可访问名称；不出现双栏或横向滚动。 |

断点使用聊天容器的 container query，而非浏览器 viewport media query，保证工作台中可拖拽/嵌入的窄聊天面板也能正确切换。

活动入口、浮层与底部抽屉都必须支持键盘焦点、Escape 关闭、按钮可访问名称和 `prefers-reduced-motion`。入口需要 `aria-expanded` 和与浮层/抽屉对应的关联关系；非模态浮层不捕获焦点，底部模态抽屉捕获焦点，关闭后恢复至入口。打开 Ask/Permission 的现有 Timeline 操作后，焦点遵循原生 Timeline 行为，活动面板不强制重新打开。

### 4. 边界与错误状态

- 没有运行态、Todo、子 Agent、Ask、Permission 或当前 active Run 失败摘要时隐藏入口，不制造空面板。
- 只要有 Ask 或 Permission，即使主 Run 已结束也继续显示入口直到用户处理。
- 运行状态缺字段时采用安全摘要，不假设进度百分比一定存在。
- 子 Agent 已被删除或状态不可用时，显示既有状态文本，不阻断主对话。
- 当前 active Run 的失败状态在会话/Run 切换前保持可见；切换后立即由新 active Run 状态替换，不长期展示历史失败。待处理 Ask / Permission 不受 Run 终态影响，直到既有原生状态将其清除。
- 用户关闭活动面板后，后续事件只更新入口摘要，不自动抢回焦点或重新打开面板。

## 复用与归属

- `FigmaChatPanel.vue` 已是主工作台中 `OpencodeTimeline` 的宿主，适合作为外层入口和抽屉的组合位置。
- 既有 `TodoPanel`、`RuntimeDock`、对话历史、Composer、Timeline 自动滚动逻辑不改变职责；活动摘要可复用其现有传入数据，但不迁移或复制 Timeline 的渲染逻辑。
- 若活动面板成为可复用组件，应归属 `packages/agent-chat` 的对话宿主层，且只能消费已归一化的前端状态；不得放入 `ui-kit`。
- 可验证边界：`frontend/packages/agent-chat/src/opencode-like/` 下文件保持不改；`OpencodeTimeline` 的 props、事件、DOM 与样式不改；新增控件只能作为 Timeline 的同级外层元素，不能以 wrapper CSS 改变 Timeline 布局。

## 测试与验收

- 在 `FigmaChatPanel` 既有测试附近覆盖：入口显隐、Ask 优先、子 Agent/任务摘要、当前 Run 失败保留与 Run 切换清除、Run 完成后 Ask / Permission 继续显示、原生状态清除后入口同步隐藏、面板开关、点击外部与 Escape 关闭、关闭后事件更新不重新打开。
- 覆盖桌面浮层定位、边界翻转、最大高度、页面滚动和打开时 Timeline 自动滚动不受影响。
- 覆盖 719px/720px 容器宽度、嵌入式窄容器、移动安全区和 Composer 上方可用高度；不得产生横向滚动或遮挡 Composer。
- 覆盖入口 `aria-expanded`、浮层非模态焦点行为、底部抽屉焦点捕获/恢复和关闭行为。
- 回归确保 `OpencodeTimeline` 的输入 props、事件、DOM/样式快照和原有测试不变。
- 运行前端定向 Vitest、`corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm build`；再启动前端并人工验证桌面与窄屏。
- 实施时检查并按需同步 `frontend/README.md`、`frontend/apps/agent-web/README.md`、`frontend/packages/agent-chat/README.md` 和相关测试说明；无需更新 API、事件或数据库文档。

## 影响

不涉及 API、RunEvent SSE 契约、数据库、权限模型或后端兼容性。唯一用户可见变化是聊天面板外层新增按需展示的本轮活动入口和响应式活动面板。
