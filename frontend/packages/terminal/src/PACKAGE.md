# 包说明：@test-agent/terminal/src

## 职责

承载 Phase 11 P2 交互式 PTY 的前端 WebSocket client 和终端面板。

## 主要程序清单

- `terminal-client.ts`：把后端 ticket 响应转换为受控 WebSocket 会话，负责 input/resize/close 和 output/exit/error 状态归并。
- `TerminalPanel.tsx`：终端 UI 面板，调用上层传入的 `createTicket` 回调，再交给 `terminal-client` 管理 WebSocket 生命周期。
- `index.ts`：包导出入口。

## 允许依赖

- React。
- `@test-agent/shared-types`。
- `@test-agent/ui-kit`。

## 禁止依赖

- `@test-agent/backend-api`。
- `@test-agent/event-stream-client`。
- opencode server URL、SSH、sidecar 或持久化 token。

## 修改时必须同步更新

- 本包 README。
- `docs/frontend/frontend-backend-contract.md`。
- `docs/api/backend-api.md`，如果 terminal 协议变化。
