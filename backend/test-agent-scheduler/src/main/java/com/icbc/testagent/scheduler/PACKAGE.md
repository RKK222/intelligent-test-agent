# 包说明：com.icbc.testagent.scheduler

## 职责

定时任务框架包，承载任务处理器契约、Cron 计算、代码注册同步、Redis 分布式锁、后台 runner 和管理服务。

## 不负责

- 不包含具体业务定时任务。
- 不暴露 HTTP Controller。
- 不直接访问 JDBC Repository 实现。
- 不实现本机锁、数据库锁或 Redis 不可用降级。

## 主要程序清单

- `ScheduledTaskHandler`、`ScheduledTaskContext`、`ScheduledTaskResult`：业务任务接入框架的稳定契约。
- `CronScheduleCalculator`：统一使用 UTC 计算 Cron 下次触发时间。
- `ScheduledTaskRegistry`：扫描 handler Bean 并同步代码注册任务定义。
- `RedisScheduledTaskLock`、`ScheduledTaskLockLease`：Redis token 锁、续租和释放。
- `ScheduledTaskRunner`：应用启动时先同步代码注册任务；启用 scheduler 后后台线程扫描 due task 和 pending manual run，统一记录运行状态。
- `SchedulerManagementService`、`ScheduledTaskUpdateCommand`：管理 API 使用的应用服务与命令对象。
- `SchedulerProperties`、`SchedulerStartupValidator`：配置绑定与启用 Redis 必需校验。

## 允许依赖

- `test-agent-common`、`test-agent-domain`、`test-agent-observability`。
- Spring Context、Spring Boot 配置绑定、Spring Data Redis。

## 禁止依赖

- `test-agent-api`。
- `test-agent-persistence`。
- `test-agent-app`。
- generated SDK。

## 上游调用方

- `test-agent-api` 通过 `SchedulerManagementService` 暴露内部管理 API。
- `test-agent-app` 运行装配并启动后台扫描。
- 未来业务模块通过提供 `ScheduledTaskHandler` Bean 注册具体任务。

## 下游依赖

- domain 中的 scheduler 聚合和 Repository 端口。
- Redis，用于分布式互斥锁。

## 测试位置

- `backend/test-agent-scheduler/src/test/java/com/icbc/testagent/scheduler`。
- 新增 runner、锁、注册或管理服务能力必须覆盖成功、失败、跳过和 Redis 不可用边界。

## 修改时必须同步更新

- `backend/test-agent-scheduler/README.md`。
- `docs/api/http-api.md`，如果影响管理 API。
- `docs/deployment/backend.md` 和 `docs/standards/security.md`，如果影响运行配置或安全边界。
