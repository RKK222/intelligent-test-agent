# 01 Contract And Domain Plan

## 目标

把 RunEvent 从单一 `remoteSessionId` 语义扩展为 root/child session scope，同时保持旧 payload 字段兼容。

## 已落地

- 新增事件类型：
  - `session.error`
  - `session.child.discovered`
  - `session.scope.updated`
  - `session.created`
  - `session.updated`
  - `session.deleted`
- 新增领域对象：
  - `RunEventScopeContext`
  - `RunSessionScope`
  - `RunSessionScopeSession`
  - `RunSessionScopeRepository`
- `RunEventDraft` / `RunEvent` 增加可空 `scopeContext`，并保留旧构造器兼容。
- `OpencodeRunEventMapper.toDrafts(...)` 支持一条 raw event 映射多条平台事件。
- `session.status busy/retry/idle` 均映射为 `session.status`；只有 root idle 额外派生 `run.succeeded`。
- `session.error` 映射为 `session.error`；只有 root error 额外派生 `run.failed`。
- `session.next.step.ended` 不再映射为 `run.succeeded`。
- scope 兼容字段复制到 payload：`rootSessionId`、`sessionId`、`parentSessionId`、`isChildSession`、`taskMessageId`、`taskPartId`、`taskCallId`、`scopeVersion`。

## 后续批次

1. child discovery contract
   - task tool part metadata 中解析 `sessionId/sessionID`。
   - `session.created/updated` 中读取 `parentID`。
   - bootstrap 读取 `session.children(root)`，只纳入本 Run 启动后发现或可绑定 task part 的 child。

2. event compatibility contract
   - 前端 reducer 必须忽略未知 payload 字段。
   - `session.diff -> session.diff` 是否替换 `diff.proposed` 需独立契约批次。

3. terminal contract
   - root idle/error 可以改变 Run 终态。
   - child idle/error 只更新 session timeline，不改变 Run 终态。
