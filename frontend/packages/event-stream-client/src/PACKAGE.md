# 包说明：@test-agent/event-stream-client/src

## 职责

封装 RunEvent SSE 订阅、解析、去重和关闭。

## 主要程序清单

- `index.ts`：`subscribeRunEvents`、`parseRunEvent`、已知事件类型列表。

## 允许依赖

- `@test-agent/shared-types`。
- 浏览器 `EventSource` API。

## 禁止依赖

- React 组件、业务状态、opencode server。

## 修改时必须同步更新

- `docs/api/event-stream-api.md`。
- `docs/frontend/frontend-backend-contract.md`。
- 本包 README 和测试。
