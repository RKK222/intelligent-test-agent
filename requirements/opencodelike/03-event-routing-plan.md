# 03 Event Routing Plan

## 目标

在 runtime 层建立 Run scope router，opencode-client 只提供 raw event 映射能力，不依赖 DB/Redis。

## 已落地

- `DefaultOpencodeClientFacade.streamRunEvents(...)` 使用 root `RunEventScopeContext` 调用 `toDrafts(...)`。
- root idle 会输出 `session.status` + `run.succeeded`。
- `session.next.step.ended` 只输出 `opencode.event.unknown`，不会让 Run 结束。
- `RunApplicationService` 在 Run 进入 `RUNNING` 后写入 root scope 和 root session。
- `RunEventPersistencePolicy` 保留 `scopeContext`，并在工具摘要里保留 scope payload 字段。

## 后续 router 设计

1. scope bootstrap
   - Run 启动时写 root scope。
   - 后台调用 `session.children(root)` 仅作为 bootstrap 候选。
   - 候选 child 必须满足本 Run 启动后发现，或能绑定到本 Run task part。

2. task part discovery
   - 监听 `message.part.updated` / `message.updated` 中的 tool/task part。
   - 从 `metadata.sessionId/sessionID` 提取 child session。
   - 写入 `run_session_scope_sessions`，source=`TASK_PART`。

3. session event discovery
   - 监听 `session.created` / `session.updated`。
   - event payload 中 `parentID` 命中 root 或已知 child 时纳入 scope。
   - source=`SESSION_EVENT`。

4. pending drain
   - raw event 显式属于未知 child 时，先写 Redis pending buffer。
   - child 发现后 drain，重新注入 mapper，补发/补持久化事件。

5. terminal derivation
   - root `session.status idle` / `session.idle` -> `session.status` + `run.succeeded`。
   - child `session.status idle` / `session.idle` -> 仅 `session.status`。
   - root `session.error` -> `session.error` + `run.failed`。
   - child `session.error` -> 仅 `session.error`。

## 测试要求

- root-child mapper：root idle 成功、child idle 不成功。
- error：root error 失败、child error 不失败。
- discovery：task metadata、session.created parentID、session.children bootstrap。
- buffer：child event 早于 discovery 后可 drain。
