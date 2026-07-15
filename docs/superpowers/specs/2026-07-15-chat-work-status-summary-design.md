# 合并对话工作状态展示设计

## 目标

主智能体当前轮只展示一个工作状态块，并固定在原 Todo 面板位置、输入框上方。状态块聚合真实 reasoning、普通工具事件和当前 Todo，文件修改块紧随其后；历史轮在最后一个 assistant 输出下方收为单图标。问答、子智能体卡片和子智能体内部时间线保持现状。

## 数据与排序

- 根时间线新增 `work-status` 行，聚合 reasoning 以及除 `task`、`question` 外的全部 tool part 引用。
- 正文、retry、失败提示继续留在消息滚动区；最新 `work-status` 与 `diff-summary` 由 `OpencodeTimeline` 投送到输入框上方的 Dock，顺序固定为状态在前、文件修改在后。
- 用户消息已进入时间线但尚无 assistant part 时，也生成空事件状态块并显示真实轮次运行状态。
- reducer 按 user message ID 保存 Todo 快照；新用户消息进入前归档上一轮 Todo 并立即清空当前 Todo，`todo.updated` 与 `todowrite` part 更新当前轮快照，历史持久消息按轮恢复最后一次 todowrite。
- 状态数据按轮保留；新用户轮次出现时，旧轮摘要在最后一个 assistant 输出下方收为单图标。
- 子智能体视图继续使用既有 reasoning/context/tool 行，避免改变子智能体展示方式。

## 展示与交互

- 细边框包裹状态内容，左侧竖向 `ShimmerDivider` 贯穿块高。
- 第一行保持现有“思考状态”标题、摘要、状态和 reasoning 展开样式；没有 reasoning 时不生成虚构正文。
- 第二行按首次出现顺序展示探索、技能、命令行、编辑、写入、补丁、网页、待办和未知工具类别。单次只显示图标，多次显示图标与数字，未出现类别不占位。
- 当前轮有 Todo 时增加第三行，复用现有任务统计和展开列表；无 Todo 时保持两行。历史展开优先显示该轮快照，无法恢复时不展示错误任务。
- 点击事件图标打开与对话内容区等宽的悬浮气泡；气泡按可用空间选择上方或下方，最大高度为 `min(360px, 50vh)`，内部滚动并复用现有工具详情组件。
- 全时间线同时只允许一个事件气泡；再次点击、点击外部或按 Esc 关闭。历史状态同时只允许展开一个，点击图标原位展开，再次点击收起；新一轮开始关闭所有旧详情。
- 最新运行轮播放竖向流光，当前完成轮显示静态渐变线；历史收起图标不保留完整状态块高度。

## 兼容边界

- `TimelineRow.work-status` 携带该轮 Todo 快照；运行态和对话投影输入增加可选的 `todoSnapshotsByUserMessageId`。
- `OpencodeTimeline` 接受可选 Dock 目标；未提供时仍按“状态、文件修改”顺序在原时间线渲染。
- `ShimmerDivider` 增加向后兼容的 `orientation` 与 `animated` 可选属性，默认横向动画行为不变。
- 不改变 `AgentMessage`、`MessagePart`、HTTP API、RunEvent、数据库、权限或安全契约。

## 验收

- reducer/投影测试覆盖分轮 Todo 归档与恢复、空 assistant、事件聚合、计数、未知工具、状态与文件修改排序、多轮保留、retry/error、问答/子智能体不变。
- 组件测试覆盖两行/三行状态块、Dock 投送、Todo 展开、历史单图标及互斥展开、全宽气泡互斥与关闭和流光生命周期。
- `ShimmerDivider` 测试覆盖纵向、静态模式和默认横向兼容。
- 执行前端 typecheck、Vitest、生产 build、`git diff --check`，并检查桌面与窄屏真实页面。
