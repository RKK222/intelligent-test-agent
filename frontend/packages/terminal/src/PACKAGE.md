# 包说明：@test-agent/terminal/src

## 职责

承载交互式 PTY 的前端 WebSocket client 和终端面板。

## 主要程序清单

- `terminal-client.ts`：把后端 ticket 响应转换为受控 WebSocket 会话，兼容企业同源构建的空 API base，并负责 input/resize/close 和 output/exit/error/warning 状态归并。
- `TerminalPanel.vue`：xterm 终端 UI 面板，调用上层传入的 `createTicket` 回调，再交给 `terminal-client` 管理 WebSocket 生命周期；以有界绝对布局和合帧 resize 防止尺寸反馈循环。
- `terminal-client.test.ts`、`terminal-panel.test.ts`：覆盖 ticket 连接、空 API base 下的绝对/相对 WebSocket 地址、output/exit/error/warning、input/resize/close、键盘聚焦和重复尺寸通知去重。
- `index.ts`：包导出入口。

## 允许依赖

- Vue 3。
- xterm.js 与 FitAddon。
- `@test-agent/shared-types`。
- `@test-agent/ui-kit`。

## 禁止依赖

- `@test-agent/backend-api`。
- `@test-agent/event-stream-client`。
- opencode server URL、SSH、sidecar 或持久化 token。
- RunEvent timeline 写入或 shell 命令语义解释。

## 修改时必须同步更新

- 本包 README。
- `docs/architecture/module-map.md`。
- `docs/api/http-api.md`，如果 terminal 协议变化。
