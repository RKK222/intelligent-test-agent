# @test-agent/terminal

## 工程定位

Phase 11 P2 交互式 PTY 终端前端包，只负责平台 WebSocket 生命周期、输入、resize、关闭和输出渲染。

## 主要职责

- 使用后端返回的一次性 terminal ticket 建立 WebSocket。
- 解析 `output`、`exit`、`error` JSON envelope。
- 发送 `input`、`resize`、`close` JSON envelope。
- 提供 `TerminalPanel` 供 `apps/agent-web` 组合。

## 禁止事项

- 不调用 `backend-api` 创建 ticket；ticket 创建由 app 层通过 `@test-agent/backend-api` 完成。
- 不直连 opencode server、SSH、sidecar 或任意非平台地址。
- 不持久化 ticket、token 或终端输入输出。

## 验证

```bash
corepack pnpm --filter @test-agent/terminal typecheck
corepack pnpm test -- terminal
```
