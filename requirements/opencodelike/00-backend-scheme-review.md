# Run Session Tree + Timeline 后端方案评审

## 已确认决策

- 新主路径使用 `/api/internal/agent/{agentId}/...`，旧 `/api/runs/...` 只保留兼容入口。
- Run 级 session tree 只返回当前 Run scope 内的 root + child；Session 级历史树另走 root session 全量历史接口。
- `session.next.step.ended` 不再派生 Run 成功；Run 成功只由 root `session.status idle` 或 `session.idle` 派生。
- `session.diff -> diff.proposed` 的前端契约变更不混入 scope 基建批次；现阶段继续保持 `diff.proposed` 兼容。

## 原方案不合理点

1. scope router 不应放进 `opencode-client`。
   `opencode-client` 只能处理 opencode 调用和 raw/mapped DTO 边界；DB/Redis scope、pending child buffer、dedup 都属于 runtime/domain/persistence 边界。

2. `session.children(root)` 不能无条件纳入当前 Run。
   opencode 的 children API 返回 root 下历史 child；当前 Run scope 只能包含本 Run 启动后发现的 child，或可通过本 Run task part metadata 绑定的 child。

3. raw event id 缺失不能写 `"unknown"`。
   伪造值会让唯一索引把不同事件误去重；缺失 raw event id 必须保持 `NULL`。

4. 新 schema 不使用 JSONB。
   当前仓库以 H2 PostgreSQL 模式跑 Flyway 集成测试，JSON payload/capabilities 仍采用 `text`；scope metadata 也使用 `metadata_json text`。

5. 不新增 `SESSION_IDLE` wire type。
   busy/retry/idle 都规范化为 `session.status`，root idle 额外派生 `run.succeeded`。

6. `session.next.step.ended` 不能继续作为终态。
   opencode 1.17.8 已以 `session.status idle`/`session.idle` 表达 prompt 收敛；step ended 只作为未知兼容事件保留上下文。

## opencode 源码依据

- `opencode-source/opencode-1.17.8/packages/opencode/test/server/httpapi-sdk.test.ts` 覆盖 `sdk.session.children({ sessionID })`，说明 child 列表是 session API 能力。
- `opencode-source/opencode-1.17.8/packages/sdk/openapi.json` 定义 `session.children` 为 `GET /session/{sessionID}/children`。
- `opencode-source/opencode-1.17.8/packages/core/src/session/schema.ts` 和 generated `tools/opencode-sdk-generator/.../Session.java` 均包含 `parentID`。
- `opencode-source/opencode-1.17.8/packages/app/src/context/global-sync/event-reducer.test.ts` 覆盖 `session.created` 携带 `parentID` 的 child session。
- `opencode-source/opencode-1.17.8/packages/opencode/test/tool/task.test.ts` 覆盖 task tool 产生 child session，child 的 `parentID` 指向触发 task 的 root message。

## 本次落地范围

- 已落地 root scope 基建、事件 mapper 契约、scope 表与 MyBatis repository、Run 级 session-tree snapshot HTTP 接口。
- 已落地 root `session.status idle`/`session.idle` 派生 `run.succeeded`，child idle 不派生 Run 终态的 mapper 契约测试。
- 未完全落地 child discovery router、Redis pending buffer/dedup；Session 级全量历史树接口已落地为按 DB root scope 汇总，后续 discovery 批次会扩大其可见 child 范围。
