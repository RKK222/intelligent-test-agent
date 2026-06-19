# 包说明：@test-agent/agent-chat/src

## 职责

渲染 Agent 对话区和 RunEvent 派生卡片。

## 主要程序清单

- `AgentChat.tsx`：Agent/历史 tab、受控 History 搜索与置顶/删除回调、Agent/Provider/Model/Mode selector、runtime status bar、slash command palette、`@` context picker、permission/question/Todo dock、message part timeline、输入框和动作按钮。
- `cards.tsx`：Plan、Tool、Test、Diff 等卡片。
- `runtime-reducer.ts`：纯 RunEvent reducer，归并旧 `assistant.message.delta` 和 Phase 11 `message.*`、permission/question、todo、diff/session status 事件。

## 允许依赖

- React。
- assistant-ui 类型。
- `@test-agent/shared-types`。
- `@test-agent/ui-kit`。

## 禁止依赖

- backend-api、event-stream-client、opencode server。

## 修改时必须同步更新

- 本包 README。
- 前端测试规范中对应卡片测试。
