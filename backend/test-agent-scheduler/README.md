# test-agent-scheduler

## 工程定位

分布式定时任务框架模块，负责通用任务注册、Cron 计算、Redis 分布式锁、后台扫描执行、统一运行记录和管理服务。本模块包含框架自身的运行记录保留维护任务；其它具体业务任务在所属业务模块实现 `ScheduledTaskHandler` Bean，例如 opencode runtime 的 stale active Run 收敛和运营分析汇总任务。

## 技术栈

- Java 21
- Spring Context / Spring Boot ConfigurationProperties
- Spring Data Redis
- Maven library jar

## 主要职责

- 定义 `ScheduledTaskHandler`、`ScheduledTaskContext`、`ScheduledTaskResult` 任务处理契约。
- 启动时扫描 `ScheduledTaskHandler` Bean，并把代码注册任务同步到 `scheduled_tasks`；`test-agent.scheduler.enabled=false` 时仍同步任务定义，便于管理页展示。
- 使用 Spring `CronExpression` 计算 `nextFireAt`；漏扫只补执行一次，不回放所有错过的 cron 次数。
- 使用 Redis `SET NX PX` 获取锁，Lua 脚本按 token 续租和释放锁，锁 key 为 `test-agent:scheduler:lock:{taskKey}`。
- 启用 scheduler 后，后台线程扫描 due task 和管理员手动触发 pending run，统一写入 `scheduled_task_runs`。
- 提供 `SchedulerManagementService` 给 API 模块实现超级管理员管理入口，支持查看任务/运行记录、调整 Cron、手动触发和协作式停止 `RUNNING` 运行记录。
- 提供只读诊断能力，展示当前进程 scheduler 生效配置、扫描线程状态、任务 active/pending 运行和 Redis 锁 TTL，用于判断任务为何无法执行。
- `ScheduledTaskContext` 提供 `stopRequested()` 和 `throwIfStopRequested()`；未来具体业务任务在长循环或外部调用间隙必须主动检查停止请求，退出后由 runner 记录 `MANUALLY_STOPPED`。

## 框架维护任务

- `scheduler.run-retention-cleanup` 默认每天 UTC 00:00 执行一次，清理 `scheduled_task_runs` 中 `ended_at` 超过 7 天的 `SUCCEEDED`、`FAILED`、`SKIPPED`、`MANUALLY_STOPPED` 记录；`PENDING`、`RUNNING`、`STOPPING` 始终保留。
- 清理通过 domain 端口调用 persistence 的 MyBatis XML，不在 scheduler 模块直接访问数据库；任务结果保存本次删除数量和截止时间。

## 不负责

- 不实现具体业务定时任务；框架自身的运行记录保留维护任务除外。
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

- `test-agent.scheduler.enabled` / `TEST_AGENT_SCHEDULER_ENABLED`：应用默认 `true`，启动后台扫描并执行 pending run；显式设为 `false` 可关闭，环境变量存在但值为空时也按 `false` 处理，避免启动绑定失败。代码注册任务无论是否启用都会在应用启动时同步到 `scheduled_tasks`，管理页可正常展示。关闭时管理端手动触发会直接返回冲突错误，避免写入无法执行的 `PENDING` 运行记录。
- `test-agent.scheduler.scan-interval` / `TEST_AGENT_SCHEDULER_SCAN_INTERVAL`：扫描间隔，默认 `30s`。
- `test-agent.scheduler.due-task-limit` / `TEST_AGENT_SCHEDULER_DUE_TASK_LIMIT`：单轮扫描 due task 上限，默认 `50`。
- `test-agent.scheduler.manual-run-limit` / `TEST_AGENT_SCHEDULER_MANUAL_RUN_LIMIT`：单轮扫描手动 pending run 上限，默认 `50`。

启用 scheduler 时必须装配 `StringRedisTemplate`。Redis 不可用时不降级为本机锁。

## 业务任务接入约定

- 业务模块可以依赖 `test-agent-scheduler` 并提供 `ScheduledTaskHandler` Bean，由本模块统一完成任务注册、Redis 锁、运行记录、cron 调整、手动触发和停止信号传递。
- 长循环业务任务必须读取 `ScheduledTaskContext.stopRequested()` 或调用 `throwIfStopRequested()`，不要自行实现分布式锁或运行记录。
- 业务模块自己的扫描阈值、Redis key、数据库查询和事件副作用必须在业务模块 README 与测试中说明；本模块不保存这些业务语义。
- 当前代码注册示例包括 `scheduler.run-retention-cleanup`、`opencode-runtime.stale-active-run-reconcile` 和 `opencode-runtime.analytics-rollup`；清理任务默认每天 UTC 00:00 执行，运营分析汇总默认每 5 分钟执行，并在主要数据库阶段间检查停止信号。运营分析的业务数据库锁仅用于滚动部署期间兼容仍在运行的旧 `@Scheduled` 实例，不是 scheduler 框架的锁降级实现。

## 测试覆盖

- `CronScheduleCalculatorTest` 覆盖 Cron 下次触发和非法 Cron 统一错误。
- `ScheduledTaskRegistryTest` 覆盖任务注册同步、nextFireAt 计算和管理员覆盖值保留。
- `ScheduledTaskRunnerTest` 覆盖 Cron 成功执行、重叠触发 `SKIPPED`、Redis 锁失败、handler 异常 `FAILED`、手动 pending run 执行和停止请求最终记录 `MANUALLY_STOPPED`。
- `ScheduledTaskRunRetentionTaskHandlerTest` 覆盖七天截止时间、删除结果和每日 Cron/锁 TTL 元数据。
- `RedisScheduledTaskLockTest` 覆盖 Redis `SET NX` 获取锁、token Lua 续租/释放和锁 TTL 只读检查。
- `SchedulerManagementServiceTest` 覆盖管理端 patch、非法 Cron、scheduler 关闭时拒绝手动触发、手动触发 active 冲突、只允许停止 `RUNNING` 和诊断阻塞原因。
- `SchedulerStartupValidatorTest` 覆盖关闭状态、空字符串启用值按关闭绑定、启用时 Redis 必需。

## 后续 AI 编码指引

新增具体业务定时任务时，优先放在对应业务模块并实现 `ScheduledTaskHandler` Bean，不要放在本模块。新增用户级计划 API 或后台会话发送能力前，必须先扩展 domain/service/API 文档和安全设计。
