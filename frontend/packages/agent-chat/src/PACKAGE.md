# 包说明：@test-agent/agent-chat/src

## 职责

渲染 Agent 对话区和 RunEvent 派生卡片。

## 主要程序清单

- `AgentChat.tsx`：Agent/历史 tab、输入框和动作按钮。
- `cards.tsx`：Plan、Tool、Test、Diff 等卡片。

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
