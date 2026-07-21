# 包说明：com.enterprise.testagent.scheduler

## 职责

承载 XXL adapter 复用的任务 handler/context/result、Redis token 全局锁和旧 PostgreSQL 运行记录清理 handler；不再生产后台扫描线程。

## 主要程序清单

- `ScheduledTaskHandler`、`ScheduledTaskContext`、`ScheduledTaskResult`：业务任务接入 XXL 的稳定协议。
- `ScheduledTaskRegistry`：仅维护 Spring handler 映射，不同步数据库任务定义。
- `RedisScheduledTaskLock`、`ScheduledTaskLockLease`：Redis token 锁、续租和释放。
- `ScheduledTaskRunRetentionTaskHandler`：由 XXL 清理超过 7 天的旧 scheduler 已结束运行记录。

## 不负责

- 不扫描或执行 PostgreSQL Cron、手工运行或 `USER_PLAN`。
- 不提供一次性用户计划、服务器亲和 runner、本地线程池或持久队列。
- 不包含具体业务定时任务，不暴露 HTTP Controller。
- 不直接访问 persistence 实现，不实现 Redis 故障降级。

## 允许依赖

- `test-agent-common`、`test-agent-domain`、`test-agent-observability`。
- Spring Context、Spring Data Redis。

## 禁止依赖

- `test-agent-api`、`test-agent-persistence`、`test-agent-app`、generated SDK。

## 上游调用方

- `test-agent-xxl-job-integration`：查找 handler 并复用 Redis 全局锁。
- 各业务模块：实现 `ScheduledTaskHandler`，由 XXL MySQL migration 注册执行策略。

## 下游依赖

- domain 中的任务键、历史运行清理端口。
- Redis，用于 `GLOBAL_MUTEX`。

## 测试位置

- `backend/test-agent-scheduler/src/test/java/com/enterprise/testagent/scheduler`。
- handler registry、Redis 锁和历史清理必须覆盖成功、冲突、停止和 Redis 不可用边界。

## 修改时必须同步更新

- `backend/test-agent-scheduler/README.md`。
- `docs/architecture/xxl-job-integration.md`。
- `docs/deployment/backend.md` 和 `docs/standards/security.md`，如果影响运行配置或安全边界。
