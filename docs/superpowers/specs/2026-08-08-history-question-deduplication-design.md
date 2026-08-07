# 历史会话待答问题去重设计

## 背景与根因

平台会话同时存在两类身份：平台 `sessionId` 用于鉴权、Workspace/Run 关联和前端 API 路由，OpenCode 远端 `sessionId` 用于原生消息、工具调用、SSE 与子 Agent 会话树。两者通过后端 binding 关联，不能合并成单一标识。

打开仍有待答问题的历史会话时，前端会并行恢复 Session Tree 与 OpenCode 当前 pending 快照。Session Tree 中根问题保留远端 `sessionId`，`/sessions/{platformSessionId}/questions` 适配结果使用平台 `sessionId`。现有 `replaceRootSessionInteractions` 只按平台 `sessionId` 替换根请求，因此同一 `requestId` 会暂时保留两份。随后 SSE 回放把远端根会话映射为平台会话，reducer 只替换第一份同 ID 请求，最终两份都进入当前会话可见列表，形成重复问题卡片。

## 修复设计

在历史交互快照的合并边界统一按 `requestId` 识别同一个 permission/question 请求：

- OpenCode 当前 pending 快照为根会话权威状态，覆盖 Session Tree 中相同 `requestId` 的历史副本。
- 继续移除平台根 `sessionId` 下已经失效的历史请求。
- 保留 `requestId` 不同的子 Agent permission/question，使子会话作用域与既有交互不受影响。
- 不在渲染层隐藏重复项，也不改变 Session Tree、SSE 或共享 DTO 的 session 身份语义。

实现保持在 `replaceRootSessionInteractions` 这一既有边界内，同时适用于 question 与 permission；实时列表为 `null` 时仍保留历史降级结果，空数组仍表示权威地清空根 pending 请求。

## 数据流与失败处理

历史切换顺序保持不变：平台消息先展示，pending 快照校准交互，Session Tree 补齐完整时间线与子会话，活跃 Run 再订阅 SSE。合并结果保证同一 `requestId` 只保留实时根请求，后续重复 SSE ask 继续由 reducer 幂等更新。

若 permission 或 question 实时接口单独失败，对应 `null` 分支仍沿用 Session Tree 降级数据；另一个成功接口仍可独立校准，不新增错误提示、重试或网络请求。

## 测试与验收

- 先增加失败单测：Session Tree 含 `requestId=que_1` 的远端根问题，实时快照含同 ID 的平台根问题，合并后只能保留平台根问题。
- 验证不同 `requestId` 的子 Agent 请求仍被保留。
- 验证实时空数组清空根请求，实时 `null` 保留历史降级请求。
- 在合并结果上回放同一 `question.asked`，确认 reducer 不会重新产生第二张卡片。
- 运行 agent-web 聚焦 Vitest、前端 typecheck，并按项目自检要求执行差异检查。

验收标准是：从会话列表打开存在一个未回答问题的历史会话时只显示一张可交互问题卡；提交或忽略仍使用原 `requestId`；子 Agent 待处理交互不丢失。

## 影响边界

本次仅调整前端历史交互状态合并及其测试、稳定文档。不修改 HTTP API、RunEvent wire name、共享 DTO、后端、数据库/Flyway、性能、安全、环境配置、generated SDK 或 OpenCode 源码。
