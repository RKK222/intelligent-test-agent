# test-agent-scheduler

## 工程定位

旧 scheduler 契约与一次性用户计划模块。迁移至 XXL-JOB 后，本模块继续提供 `ScheduledTaskHandler`、`ScheduledTaskContext`、Redis 分布式锁和 PostgreSQL 运行记录，但后台 runner 只同步、扫描和执行按服务器亲和的 `USER_PLAN`。周期/手工触发由 `test-agent-xxl-job-integration` 调用同一业务 handler，不再由本模块扫描。

## 技术栈

- Java 21
- Spring Context / Spring Boot ConfigurationProperties
- Spring Data Redis
- Maven library jar

## 主要职责

- 定义 `ScheduledTaskHandler`、`ScheduledTaskContext`、`ScheduledTaskResult` 任务处理契约。
- 启动时扫描 `ScheduledTaskHandler` Bean，但只把支持 `USER_PLAN` 的 handler 同步到 PostgreSQL `scheduled_tasks`。
- 保留 `CronScheduleCalculator` 和旧管理服务供历史兼容测试，不再由生产 runner 调度 PostgreSQL Cron。
- 使用 Redis `SET NX PX` 获取锁，Lua 脚本按 token 续租和释放锁，锁 key 为 `test-agent:scheduler:lock:{taskKey}`。
- 启用 scheduler 后，后台线程只扫描 `executionAffinity` 命中当前服务器的 `USER_PLAN`；用户计划由有界线程池并发执行，运行记录以 `PENDING -> RUNNING` 条件更新认领，取消与执行不会互相覆盖。
- `ScheduledUserPlanService` 为业务模块提供一次性用户计划创建/取消能力；`ScheduledTaskExecutionAffinityProvider` 由运行模块提供当前服务器亲和标识。`USER_PLAN` 专用 handler 可以不配置 Cron，也不能被管理 API 调整 Cron、手工触发或停止。
- `SchedulerManagementService` 与旧诊断模型只为源码/历史兼容保留；HTTP `/scheduler-management/**` 已统一返回 `410 API_GONE`，不得接回生产管理页。
- `ScheduledTaskContext` 提供 `stopRequested()` 和 `throwIfStopRequested()`；未来具体业务任务在长循环或外部调用间隙必须主动检查停止请求，退出后由 runner 记录 `MANUALLY_STOPPED`。

## 框架维护任务

- `scheduler.run-retention-cleanup` handler 仍清理 `scheduled_task_runs` 中超过 7 天的已结束记录；它由 XXL 在 `Asia/Shanghai` 08:00 触发，`PENDING`、`RUNNING`、`STOPPING` 始终保留。
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
- `test-agent.scheduler.scan-interval` / `TEST_AGENT_SCHEDULER_SCAN_INTERVAL`：USER_PLAN 扫描间隔，默认 `30s`。
- `due-task-limit` 与 `manual-run-limit` 只为旧配置兼容保留，生产 runner 不再读取对应队列执行 Cron/手工任务。
- `test-agent.scheduler.user-plan-run-limit` / `TEST_AGENT_SCHEDULER_USER_PLAN_RUN_LIMIT`：当前服务器单轮认领用户计划上限，默认 `50`。
- `test-agent.scheduler.user-plan-worker-count` / `TEST_AGENT_SCHEDULER_USER_PLAN_WORKER_COUNT`：用户计划执行线程数，默认 `4`。
- `test-agent.scheduler.user-plan-queue-capacity` / `TEST_AGENT_SCHEDULER_USER_PLAN_QUEUE_CAPACITY`：用户计划执行队列容量，默认 `100`；队列满时记录保留 `PENDING`，等待后续扫描。

启用 scheduler 时必须装配 `StringRedisTemplate`。Redis 不可用时不降级为本机锁。

## 业务任务接入约定

- 业务模块可以依赖 `test-agent-scheduler` 并提供 `ScheduledTaskHandler` Bean。周期任务由 XXL integration 统一 adapter 调用；`GLOBAL_MUTEX` 复用本模块 Redis 锁，停止信号通过 `ScheduledTaskContext` 传递。
- 一次性用户计划必须通过 `ScheduledUserPlanService` 创建，并提供稳定 `executionAffinity`；业务模块不得自行轮询 `scheduled_task_runs`。handler 必须显式声明支持 `USER_PLAN`，专用 handler 不得伪造 Cron。
- 长循环业务任务必须读取 `ScheduledTaskContext.stopRequested()` 或调用 `throwIfStopRequested()`，不要自行实现分布式锁或运行记录。
- 业务模块自己的扫描阈值、Redis key、数据库查询和事件副作用必须在业务模块 README 与测试中说明；本模块不保存这些业务语义。
- 当前 PostgreSQL runner 只注册 USER_PLAN 专用 `opencode-runtime.night-execution`。周期任务清单、Cron 和初始化 SQL 见 `docs/architecture/xxl-job-integration.md`；不得把 XXL executor 路由绑定到 `executionAffinity` 或 `linuxServerId`。

## 测试覆盖

- `CronScheduleCalculatorTest` 覆盖 Cron 下次触发和非法 Cron 统一错误。
- `ScheduledTaskRegistryTest` 覆盖任务注册同步、nextFireAt 计算和管理员覆盖值保留。
- `ScheduledTaskRunnerTest` 覆盖旧 runner 不扫描 Cron/手工任务、只按亲和并发认领 `USER_PLAN`、取消/认领 CAS 竞态和停止请求；`ScheduledUserPlanServiceTest` 覆盖创建、取消与 scheduler 关闭边界。周期任务锁、重叠、续租与停止由 integration 模块测试覆盖。
- `ScheduledTaskRunRetentionTaskHandlerTest` 覆盖七天截止时间、删除结果和每日 Cron/锁 TTL 元数据。
- `RedisScheduledTaskLockTest` 覆盖 Redis `SET NX` 获取锁、token Lua 续租/释放和锁 TTL 只读检查。
- `SchedulerManagementServiceTest` 覆盖管理端 patch、非法 Cron、scheduler 关闭时拒绝手动触发、手动触发 active 冲突、只允许停止 `RUNNING` 和诊断阻塞原因。
- `SchedulerStartupValidatorTest` 覆盖关闭状态、空字符串启用值按关闭绑定、启用时 Redis 必需。

## 后续 AI 编码指引

新增周期任务时，业务实现仍放对应模块并实现 `ScheduledTaskHandler`，同时在 XXL 独立 MySQL location 新增一个不可变版本 SQL；不要恢复旧 runner Cron 扫描。新增用户级计划 API 或后台会话发送能力前，必须先扩展 domain/service/API 文档和安全设计。
