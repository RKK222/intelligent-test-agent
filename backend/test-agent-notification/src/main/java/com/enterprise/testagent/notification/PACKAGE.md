# 包说明：com.enterprise.testagent.notification

## 职责

通用用户站内通知应用层，统一处理通知分页/未读、受控动作生命周期、事务提交后实时变化、用户级 SSE 状态源和 90 天历史清理。首期通知生产者为会话协作分享。

## 主要程序清单

- `UserNotificationApplicationService`：分页与未读统计、通用幂等已读、分享通知创建/更新/失效、分享访问成功已读、30 秒数据库校准和每日清理。
- `UserNotificationRealtimeHub`：事务提交后本机 fan-out 与 `ServerBroadcastPublisher` 跨 Java 唤醒；远端广播不回环。
- `UserNotificationChange` / `UserNotificationChangeType`：只表达接收人、可选通知 ID、变化类型、traceId 和时间的低敏变化。
- `UserNotificationPage` / `UserNotificationStreamUpdate`：业务分页结果和 SSE 状态投影，不包含任意 URL。

## 上下游边界

上游是 `test-agent-api` 和作为通知生产者的 `test-agent-opencode-runtime`；下游只允许 `test-agent-common`、`test-agent-domain` 与通用 Spring/Reactor/日志库。MyBatis、Flyway、Controller、DTO 和 Vue 组件不属于本包。

## 安全与恢复

- 通知动作只能使用领域枚举和内部目标 ID；禁止消息正文、文件路径、Token、外部 URL 或原始异常。
- 分享访问成功后的已读失败不得阻断访问；失效事实由查询联表继续 fail-closed。
- 广播是低延迟增强，失败不回滚数据库事务；初始快照和 30 秒校准恢复漏消息。

## 修改时必须同步更新

- `backend/test-agent-notification/README.md`
- `docs/api/http-api.md`
- `docs/api/event-stream.md`
- `docs/standards/security.md`
- `docs/architecture/dependency-rules.md`（依赖边界变化时）
