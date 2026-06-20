# 包说明：@test-agent/agent-chat/src

## 职责

渲染 Agent 对话区和 RunEvent 派生卡片。

## 主要程序清单

- `AgentChat.tsx`：Agent/历史 tab、受控 History 搜索与置顶/删除回调、Agent/Provider/Model/Mode selector、runtime status bar、slash command palette、`@` context picker、permission/question dock、message part timeline、输入框和动作按钮。
- `cards.tsx`：统一 `TimelineCard` 折叠壳，以及 Plan、Tool、Test、Diff、Event 卡片的 payload 派生展示；Diff 卡片展开区展示文件、状态和行变更表格。
- `AssistantThread.tsx`、`assistant-thread.tsx`：assistant-ui thread 适配和展示辅助，负责区分 reasoning 思考过程、任务分解、Skill/Tool 调用与 text 最终回答，并计算运行中、最新工具和最新 Diff 的默认展开状态，不直接接入平台 API。
- `process-blocks.tsx`：浅色过程信息展示块，包括最终回答、思考状态、任务分解、Tool 调用和 `tool=skill` 的 Skill 调用分类展示。
- `process-status.ts`：过程状态归一化、中文文案、状态色和 Skill tool 判定工具。
- `prompt-parts.ts`：浏览器文件和图片到平台 `PromptPart` 的纯转换，供 composer 和单测复用。
- `runtime-reducer.ts`：纯 RunEvent reducer，归并旧 `assistant.message.delta` 和 Phase 11 `message.*`、permission/question、todo、diff/session status 事件。

## 允许依赖

- React。
- assistant-ui 类型。
- `@test-agent/shared-types`。
- `@test-agent/ui-kit`。

## 禁止依赖

- backend-api、event-stream-client、opencode server。
- terminal ticket 创建、WebSocket 生命周期或文件系统直读。

## 修改时必须同步更新

- 本包 README。
- 前端测试规范中对应卡片测试。
