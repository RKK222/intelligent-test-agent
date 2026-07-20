# 包说明：com.enterprise.testagent.scheduler

## 职责

旧调度契约与 USER_PLAN 包，承载任务处理器/context、Redis 分布式锁、服务器亲和 USER_PLAN runner 和 PostgreSQL 运行记录维护 handler。周期任务由 XXL integration 调用，不再由本包生产 runner 扫描。

## 不负责

- 不包含具体业务定时任务；框架自身的运行记录保留维护任务除外。
- 不暴露 HTTP Controller。
- 不直接访问 JDBC Repository 实现。
- 不实现本机锁、数据库锁或 Redis 不可用降级。

## 主要程序清单

- `ScheduledTaskHandler`、`ScheduledTaskContext`、`ScheduledTaskResult`：业务任务接入框架的稳定契约。
- `CronScheduleCalculator`：统一使用 UTC 计算 Cron 下次触发时间。
- `ScheduledTaskRegistry`：扫描 handler Bean；旧 runner 只调用 `syncUserPlanTasks`，XXL adapter 只读取 handler registry。
- `ScheduledUserPlanService`、`ScheduledTaskExecutionAffinityProvider`：创建/取消一次性用户计划，并把计划约束到业务提供的服务器亲和标识。
- `RedisScheduledTaskLock`、`ScheduledTaskLockLease`：Redis token 锁、续租和释放。
- `ScheduledTaskRunner`：应用启动时只同步并扫描匹配当前服务器亲和的 USER_PLAN；用户计划使用有界线程池和运行记录状态 CAS 防止重复认领。
- `SchedulerManagementService`、`ScheduledTaskUpdateCommand`：旧管理契约兼容对象；当前 HTTP 入口返回 410。
- `SchedulerProperties`、`SchedulerStartupValidator`：配置绑定、USER_PLAN 并发/队列边界与启用 Redis 必需校验。
- `ScheduledTaskRunRetentionTaskHandler`：由 XXL 在北京时间 08:00 调用，清理超过 7 天的已结束 PostgreSQL scheduler 记录。

## 允许依赖

- `test-agent-common`、`test-agent-domain`、`test-agent-observability`。
- Spring Context、Spring Boot 配置绑定、Spring Data Redis。

## 禁止依赖

- `test-agent-api`。
- `test-agent-persistence`。
- `test-agent-app`。
- generated SDK。

## 上游调用方

- `test-agent-api` 仅保留旧路径 410 兼容 Controller，不再暴露管理服务能力。
- `test-agent-app` 运行装配并启动后台扫描。
- `test-agent-xxl-job-integration` 复用 handler registry 和 Redis lock；业务模块可通过 `ScheduledUserPlanService` 创建一次性计划。

## 下游依赖

- domain 中的 scheduler 聚合和 Repository 端口。
- Redis，用于分布式互斥锁。

## 测试位置

- `backend/test-agent-scheduler/src/test/java/com/enterprise/testagent/scheduler`。
- 新增 runner、锁、注册或管理服务能力必须覆盖成功、失败、跳过和 Redis 不可用边界。

## 修改时必须同步更新

- `backend/test-agent-scheduler/README.md`。
- `docs/api/http-api.md`，如果影响管理 API。
- `docs/deployment/backend.md` 和 `docs/standards/security.md`，如果影响运行配置或安全边界。
