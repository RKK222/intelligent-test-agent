# test-agent-scheduler

## 工程定位

分布式定时任务框架模块，负责通用任务注册、Cron 计算、Redis 分布式锁、后台扫描执行、统一运行记录和管理服务。本模块本次只提供框架，不包含任何具体业务定时任务。

## 技术栈

- Java 21
- Spring Context / Spring Boot ConfigurationProperties
- Spring Data Redis
- Maven library jar

## 主要职责

- 定义 `ScheduledTaskHandler`、`ScheduledTaskContext`、`ScheduledTaskResult` 任务处理契约。
- 启动时扫描 `ScheduledTaskHandler` Bean，并把代码注册任务同步到 `scheduled_tasks`。
- 使用 Spring `CronExpression` 计算 `nextFireAt`；漏扫只补执行一次，不回放所有错过的 cron 次数。
- 使用 Redis `SET NX PX` 获取锁，Lua 脚本按 token 续租和释放锁，锁 key 为 `test-agent:scheduler:lock:{taskKey}`。
- 后台线程扫描 due task 和管理员手动触发 pending run，统一写入 `scheduled_task_runs`。
- 提供 `SchedulerManagementService` 给 API 模块实现超级管理员管理入口。

## 不负责

- 不实现具体业务定时任务。
- 不创建或模拟定时会话、Run 或后台用户消息发送。
- 不暴露 HTTP API，HTTP 入口属于 `test-agent-api`。
- 不直接访问 JDBC 实现，持久化只通过 domain Repository 端口。
- 不提供本机锁或数据库锁 fallback。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- `test-agent-observability`。
- Spring Context、Spring Boot 配置绑定、Spring Data Redis。

## 禁止依赖

- `test-agent-api`。
- `test-agent-persistence`。
- `test-agent-app`。
- `test-agent-opencode-sdk-generated`。
- 具体业务模块中的任务实现。

## 配置

- `test-agent.scheduler.enabled` / `TEST_AGENT_SCHEDULER_ENABLED`：默认 `false`，关闭后台扫描。
- `test-agent.scheduler.scan-interval` / `TEST_AGENT_SCHEDULER_SCAN_INTERVAL`：扫描间隔，默认 `30s`。
- `test-agent.scheduler.due-task-limit` / `TEST_AGENT_SCHEDULER_DUE_TASK_LIMIT`：单轮扫描 due task 上限，默认 `50`。
- `test-agent.scheduler.manual-run-limit` / `TEST_AGENT_SCHEDULER_MANUAL_RUN_LIMIT`：单轮扫描手动 pending run 上限，默认 `50`。

启用 scheduler 时必须同时启用 `test-agent.redis.enabled=true` 并装配 `StringRedisTemplate`。Redis 不可用时不降级为本机锁。

## 测试覆盖

- `CronScheduleCalculatorTest` 覆盖 Cron 下次触发和非法 Cron 统一错误。
- `ScheduledTaskRegistryTest` 覆盖任务注册同步、nextFireAt 计算和管理员覆盖值保留。
- `ScheduledTaskRunnerTest` 覆盖 Cron 成功执行、重叠触发 `SKIPPED`、Redis 锁失败、handler 异常 `FAILED` 和手动 pending run 执行。
- `RedisScheduledTaskLockTest` 覆盖 Redis `SET NX` 获取锁和 token Lua 续租/释放。
- `SchedulerManagementServiceTest` 覆盖管理端 patch、非法 Cron 和手动触发。
- `SchedulerStartupValidatorTest` 覆盖默认关闭、启用时 Redis 必需。

## 后续 AI 编码指引

新增具体业务定时任务时，优先放在对应业务模块并实现 `ScheduledTaskHandler` Bean，不要放在本模块。新增用户级计划 API 或后台会话发送能力前，必须先扩展 domain/service/API 文档和安全设计。
