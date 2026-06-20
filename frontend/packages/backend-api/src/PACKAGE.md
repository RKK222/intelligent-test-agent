# 包说明：@test-agent/backend-api/src

## 职责

封装后端 Runtime HTTP API，输出稳定 TypeScript 方法和错误对象。

## 主要程序清单

- `index.ts`：`createBackendApiClient`、`BackendApiError` 和 API 方法集合；`startRun` 兼容旧 prompt string 与 Phase 11 对象 payload，并封装 Phase 11 runtime 目录、session/message 操作、Session 全局搜索/置顶/删除、permission/question、fs/vcs/lsp/mcp status/resources/tools 和 terminal ticket 方法。

## 允许依赖

- `@test-agent/shared-types`。
- 浏览器 `fetch` API。

## 禁止依赖

- React UI、工作台状态、opencode server。

## 修改时必须同步更新

- `docs/api/http-api.md`。
- `docs/architecture/module-map.md`。
- 本包 README 和测试。
