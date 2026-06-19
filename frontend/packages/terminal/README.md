# @test-agent/terminal

## 工程定位

Phase 11 P2 交互式 PTY 终端前端包，只负责平台 WebSocket 生命周期、输入、resize、关闭和输出渲染。

## 主要职责

- 使用后端返回的一次性 terminal ticket 建立 WebSocket。
- 解析 `output`、`exit`、`error`、`warning` JSON envelope；`warning` 用于展示输出截断等非致命状态。
- 发送 `input`、`resize`、`close` JSON envelope。
- 提供 `TerminalPanel` 供 `apps/agent-web` 组合。
- 输出按 seq 追加展示；`warning`、连接失败、ticket 创建失败和 close 状态必须留在面板内展示，不能自动降级为其他连接方式。
- resize/input/close 只通过平台 envelope 发送，不解释 shell 语义，不记录或持久化终端内容。

## 禁止事项

- 不调用 `backend-api` 创建 ticket；ticket 创建由 app 层通过 `@test-agent/backend-api` 完成。
- 不直连 opencode server、SSH、sidecar 或任意非平台地址。
- 不持久化 ticket、token 或终端输入输出。
- 不订阅 RunEvent，不把 terminal 输出写入 message timeline。

## 验证

```bash
corepack pnpm --filter @test-agent/terminal typecheck
corepack pnpm test -- terminal
```

真实 PTY 浏览器到后端 WebSocket 的验收属于 `tools/dev-phase11-real-e2e.sh --start-services`，当前仍没有最新通过记录。
