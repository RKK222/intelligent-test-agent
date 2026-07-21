# test-agent-scheduler

## 工程定位

XXL-JOB 业务适配所复用的公共任务协议与 Redis 全局锁模块。本模块不再启动 PostgreSQL 定时扫描线程，也不再创建或执行 `USER_PLAN`；旧 scheduler 表仅保留历史审计和运行记录清理用途。

## 技术栈

- Java 21
- Spring Context
- Spring Data Redis
- Maven library jar

## 主要职责

- 定义 `ScheduledTaskHandler`、`ScheduledTaskContext`、`ScheduledTaskResult` 任务处理契约。
- `ScheduledTaskRegistry` 只维护 Spring 容器中的 handler 映射，供 XXL adapter 按 `taskKey` 查找，不把定义同步到 PostgreSQL。
- 使用 Redis `SET NX PX` 获取锁，Lua 脚本按 token 续租和释放锁，锁 key 为 `test-agent:scheduler:lock:{taskKey}`。
- `ScheduledTaskRunRetentionTaskHandler` 由 XXL 在北京时间 08:00 调用，清理 `scheduled_task_runs` 中超过 7 天的已结束历史；`PENDING`、`RUNNING`、`STOPPING` 始终保留。

## 不负责

- 不扫描或执行 PostgreSQL `scheduled_task_runs`，不提供本地 runner、worker 或持久队列。
- 不创建、取消或模拟一次性用户计划，不保存夜间任务业务语义。
- 不实现具体业务定时任务；历史运行清理 handler 除外。
- 不暴露 HTTP API，不直接访问 JDBC/MyBatis 实现。
- 不提供本机锁或数据库锁 fallback。

## 允许依赖

- `test-agent-common`、`test-agent-domain`、`test-agent-observability`。
- Spring Context、Spring Data Redis。

## 禁止依赖

- `test-agent-api`、`test-agent-persistence`、`test-agent-app`。
- `test-agent-opencode-sdk-generated`。
- 具体业务模块中的任务实现。

## 业务任务接入约定

- 业务模块实现 `ScheduledTaskHandler`；XXL integration 统一调用 handler，`GLOBAL_MUTEX` 复用本模块 Redis 锁，停止信号通过 `ScheduledTaskContext` 传递。
- 任务键、Cron、路由、阻塞、过期和重试策略必须通过 XXL MySQL Flyway migration 注册，不得恢复 PostgreSQL Cron/手工/`USER_PLAN` 扫描。
- 长循环或批量任务必须定期检查 `ScheduledTaskContext.stopRequested()`；业务扫描上限、路由和幂等语义由所属业务模块维护。
- 夜间执行的 15 分钟分发与 5 分钟补偿均由 XXL 调度，设计见 `docs/architecture/xxl-job-integration.md`。

## 配置

本模块没有 `test-agent.scheduler.*` 运行配置。Redis 连接统一使用 Spring `spring.data.redis.*`，Redis 不可用时 `GLOBAL_MUTEX` 不降级。

## 测试覆盖

- `ScheduledTaskRegistryTest` 覆盖 handler 映射、重复任务键和未知任务键。
- `ScheduledTaskRunRetentionTaskHandlerTest` 覆盖七天截止时间、删除结果和每日 Cron/锁 TTL 元数据。
- `RedisScheduledTaskLockTest` 覆盖 Redis token 锁、Lua 续租/释放和锁 TTL 只读检查。
- XXL adapter 的锁、重叠、续租、停止和任务注册由 `test-agent-xxl-job-integration` 测试覆盖。

## 后续 AI 编码指引

新增周期任务时，业务实现放在所属模块并实现 `ScheduledTaskHandler`，同时在 XXL 独立 MySQL location 新增不可变 migration；不要恢复旧 runner、`USER_PLAN` 服务或 scheduler 配置。
