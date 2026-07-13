# 标题监听 CLOSED 状态继续透传 RunEvent 设计

## 背景

首轮 Run 在执行期间收到有效 root `session.updated` 时，会先同步平台标题，再把对应
`TitleWatchToken` 从 `ACTIVE` 关闭为 `CLOSED`。关闭 `ACTIVE` token 的设计语义是不再处理标题，
但不取消仍在执行的主 Run 事件流；当前 `acceptsTitleWatchEvent` 却只对 `ACTIVE` 全量放行，导致
`CLOSED` 被错误套用 `TITLE_WAIT` 的过滤规则，后续 message、part、question 和终态事件全部丢失。

## 目标与边界

- `ACTIVE` 与 `CLOSED` 状态继续全量透传主 Run 事件。
- 只有 `TITLE_WAIT` 与 `TITLE_READING` 状态限制为目标 root 的 `session.updated` 或 title agent 完成消息。
- 保留 `TITLE_WAIT/TITLE_READING` 关闭时通过取消信号释放旧 Run 订阅的既有行为。
- 不改变 HTTP API、RunEvent wire name、DTO、数据库、Flyway、generated SDK 或前端逻辑。
- 不调整 `LEGACY_FULL` 与 `REDIS_SUMMARY` 的事件持久化策略。

## 实现

在 `RunApplicationService.acceptsTitleWatchEvent` 中显式按 token 状态判断：

- `ACTIVE`、`CLOSED`：返回 `true`。
- `TITLE_WAIT`、`TITLE_READING`：沿用现有标题事件白名单。

不修改 token registry，也不在 `CLOSED` 时重新注册 token。这里的 `CLOSED` 只表示标题监听职责结束；
主 Run 的生命周期仍由 root `run.succeeded/run.failed` 和原有订阅控制。

## 验证

- 新增 Run 级回归：先发送有效 root `session.updated` 并完成标题同步，延迟发送
  `message.updated` 和 `run.succeeded`；验证 message 进入实时通道、Run 收敛为成功。
- 修复前该测试应因 message 和终态被过滤而失败，修复后通过。
- 运行 `RunApplicationServiceTest` 与标题监听相关测试，确认 `TITLE_WAIT/TITLE_READING` 行为不回退。

