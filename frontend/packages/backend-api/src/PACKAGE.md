# 包说明：@test-agent/backend-api/src

## 职责

封装后端 Runtime HTTP API，输出稳定 TypeScript 方法和错误对象；agent 相关能力默认使用 `opencode`，可通过 `agentId` 切换 URL 前缀。

## 主要程序清单

- `index.ts`：`createBackendApiClient`、`BackendApiError` 和 API 方法集合；`agentId?: string` 默认 `opencode`，Run、Diff 和 runtime 相关方法拼接 `/api/internal/agent/{agentId}/...`；`startRun` 兼容旧 prompt string 与对象 payload，并封装 runtime 目录、session/message 操作、Session 全局搜索/置顶/删除、配置管理、应用版本工作区、个人工作区、permission/question、fs/vcs/lsp/mcp status/resources/tools 和 terminal ticket 方法；Command catalog 映射需保留 `source/hints` 等可选字段，兼容旧 payload。

## 允许依赖

- `@test-agent/shared-types`。
- 浏览器 `fetch` API。

## 禁止依赖

- React UI、工作台状态、opencode server。

## 修改时必须同步更新

- `docs/api/http-api.md`。
- `docs/architecture/module-map.md`。
- 本包 README 和测试。
