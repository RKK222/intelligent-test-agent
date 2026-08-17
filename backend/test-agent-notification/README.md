# test-agent-notification

## 工程定位

通用用户站内通知业务模块。生产者包括会话协作分享和 Agent 配置 dispose rollout；模块负责通知生命周期、未读统计、用户级实时变化和历史清理，不承载 HTTP DTO、MyBatis 实现或具体页面。

## 上游调用方

- `test-agent-api`：分页、幂等已读和用户级通知 SSE。
- `test-agent-opencode-runtime`：分享生命周期与已读同步，以及配置 dispose 的等待、成功、失败和已结束状态推进。
- `test-agent-app`：通过依赖图装配 Spring Bean 和定时任务。

## 下游依赖

- `test-agent-common`：通知 ID 和统一错误。
- `test-agent-domain`：`UserNotificationRepository`、通知模型、分享与用户值对象，以及 `ServerBroadcastPublisher`。
- Spring transaction/context、Reactor 和 SLF4J：事务提交后发布、用户级流与脱敏告警。

## 主要职责

- 用受控 `actionType + actionTargetId` 表达通知动作，禁止保存任意 URL。
- 首次分享、重新加入或分享重新激活时创建新通知；普通设置更新只更新当前通知快照；成员移除、撤销和会话归档使当前通知失效。
- dispose 按 `AGENT_CONFIG_DISPOSE:{rolloutId}:{userId}` 单行去重，采用“条件更新 → 幂等插入 → 并发重试更新”；相同状态不修改已读/更新时间或广播，真实变化清空已读并发布 `UPDATED`。失败状态只开放 `RESTART_OWN_PROCESS`，其它状态使用 `NONE`，目标只保存 rolloutId。
- 配置状态通知使用“正在更新、更新成功、更新失败、本次更新已结束”等用户文案，不向页面暴露 dispose、rollout 或进程缓存等内部概念。
- 未读口径固定为 `readAt` 为空、通知有效且未过期；分享的当前状态还要实时合并分享、成员、会话和所属人事实。
- 分享访问鉴权成功后按 `recipientUserId + shareId` 幂等已读。该同步失败只记录脱敏告警，不阻断分享访问。
- 数据库事务提交成功后才向本机连接和 `ServerBroadcastPublisher` 发布变化；跨节点 payload 只包含接收人 ID、通知 ID、变化类型和既有广播元数据。
- SSE 建连先返回数据库未读快照，广播变化时即时刷新，并每 30 秒重新校准；90 天前历史由每日任务清理。

## 允许依赖

只允许依赖 `test-agent-common`、`test-agent-domain` 和通用 Spring/Reactor/日志库。后续通知生产者应调用本模块的业务服务，不应复制去重、有效性、已读或广播规则。

## 禁止依赖

- 不得依赖 `test-agent-api`、`test-agent-persistence`、`test-agent-app`、页面组件或 generated SDK。
- 不得直接访问 MyBatis mapper、拼关系型 SQL、保存外部 URL、消息正文、文件路径、Token 或第三方原始错误。
- 不得把 Redis 广播当作通知事实源；广播丢失必须由数据库快照与校准恢复。

违反边界时：HTTP/SSE DTO 放到 `test-agent-api`，关系型实现和迁移放到 `test-agent-persistence`，分享业务准入继续放到 `test-agent-opencode-runtime`，浏览器交互放到 `frontend/apps/agent-web`。

## 验证

```bash
cd backend
mvn -pl test-agent-notification -am -Dtest='UserNotification*' -Dsurefire.failIfNoSpecifiedTests=false test
```
