# 运营分析汇总迁移定时任务设计

## 目标与范围

把当前由每个 Java 实例通过 Spring `@Scheduled` 每五分钟触发的运营分析汇总，迁移为 `test-agent-scheduler` 统一管理的业务定时任务。迁移后由 scheduler 负责代码注册、Cron 调度、Redis 集群互斥、锁续租、运行记录、管理员启停、手工触发和协作式停止，运营分析模块只保留汇总业务语义。

本次不改变运营分析指标口径、最近两小时 hourly 窗口、最近七天 daily 窗口、Run 耗时直方图、freshness 水位、查询 API、事件、数据库结构或前端页面。现有汇总 delete/insert 的事务改造、任意历史时间窗口补数和原始事实分页聚合不在本次范围。

## 方案比较与选择

可选方案如下：

1. 保留现有 `@Scheduled`，只增加日志或监控。改动最小，但所有 Java 继续周期竞争数据库锁，管理员仍无法通过定时任务管理页统一启停、手工补跑和查看运行记录。
2. 直接删除 `@Scheduled` 和数据库业务锁，只使用 scheduler Redis 锁。最终结构最干净，但滚动发布期间旧实例仍走数据库锁、新实例只走 Redis 锁，两套入口没有共同互斥条件，存在同窗并发重建风险。
3. 使用 scheduler Handler 替换 `@Scheduled`，暂时保留应用服务中的数据库锁作为滚动发布兼容保护。新实例先取得 scheduler Redis 锁，再复用数据库锁；旧实例仍只竞争同一个数据库锁，因此新旧版本共存期间不会绕过彼此。所有实例升级完成后，数据库锁成为第二层防御，后续如要移除应另立任务并制定发布顺序。

采用方案 3。它在不引入环境开关和数据库变更的前提下完成统一任务管理，并保证多服务器滚动升级安全。永久保留两个触发入口不作为方案；新 Handler 生效后删除旧 `AnalyticsRollupScheduler`。

## 任务定义与模块边界

在 `test-agent-opencode-runtime` 的 analytics 包新增 `AnalyticsRollupTaskHandler`，因为运营分析汇总属于 opencode runtime 业务；通用 scheduler 模块不承载具体业务任务。

任务定义如下：

- taskKey：`opencode-runtime.analytics-rollup`
- 管理名称：`TestAgent 运营分析汇总`
- 默认 Cron：`0 */5 * * * *`
- 默认 scheduler 锁 TTL：5 分钟，由框架每 TTL 三分之一周期续租
- 手工触发：与 Cron 使用同一 Handler，始终重算调用时刻对应的最近窗口
- 结果：只记录 jobName、是否实际取得数据库兼容锁、hourly/daily 窗口和低敏状态，不记录用户、组织、Run、消息或原始事实明细

Handler 只负责读取 `ScheduledTaskContext`、传递 traceId/停止信号并把结构化结果返回 scheduler。任务注册、Redis 锁、运行状态和管理操作继续由 `test-agent-scheduler` 负责。

## 执行与停止流程

Cron 或手工触发后的数据流如下：

```text
ScheduledTaskRunner
  -> 取得 opencode-runtime.analytics-rollup Redis 锁并启动续租
  -> AnalyticsRollupTaskHandler
  -> AnalyticsRollupApplicationService
  -> 取得 analytics-rollup 数据库兼容锁
  -> 重建最近窗口 hourly 与 duration histogram
  -> 从 hourly 重建最近七天 daily
  -> 更新 analytics_rollup_watermarks
  -> 释放数据库兼容锁
  -> 返回低敏结果并写 scheduled_task_runs
```

应用服务增加 `BooleanSupplier` 停止信号，并在取得数据库锁前、hourly 完成后、daily 完成后等外部调用边界检查。收到停止请求后不再进入下一阶段，始终在 `finally` 中释放数据库锁；Handler 随后调用 `context.throwIfStopRequested()`，由 scheduler 统一把运行记录写为 `MANUALLY_STOPPED`。已经完成的一个汇总阶段不回滚，watermark 只在整轮成功完成后更新。

数据库锁未取得表示旧实例或其它兼容调用正在执行。该次任务返回 `executed=false` 的低敏结果，不把正常互斥记录为业务失败。其它运行异常沿用现有行为：应用服务把 freshness 标记为 `FAILED` 后继续抛出，由 scheduler 把本次运行记录为 `FAILED`。

## 配置与发布兼容

删除 `AnalyticsRollupScheduler` 后，`test-agent.analytics.rollup.enabled`、`test-agent.analytics.rollup.fixed-delay-ms` 以及对应 `TEST_AGENT_ANALYTICS_ROLLUP_*` 配置不再生效，应从应用默认配置和稳定文档中移除。任务是否执行、Cron 和手工触发统一由 scheduler 任务定义控制。

迁移不修改 `.env.local`、`.env.test` 或生产环境文件。部署前必须确认目标环境启用了 `test-agent.scheduler.enabled`；未启用时任务定义仍会同步到管理页，但不会自动执行或接受手工触发。

推荐发布顺序：先发布包含新 Handler 且删除旧 `@Scheduled` 的版本，再通过任务管理页确认 `opencode-runtime.analytics-rollup` 已注册和启用。滚动过程中，新旧版本通过现有数据库锁互斥；不得在旧实例仍在线时移除数据库锁。

## 测试与验收

测试驱动覆盖以下行为：

- Handler 暴露稳定 taskKey、管理名称、五分钟 Cron 和五分钟锁 TTL。
- Handler 向应用服务传递 scheduler traceId 与动态停止信号，并返回低敏结构化结果。
- Handler 在执行前或执行后收到停止请求时抛出 scheduler 协作式停止信号。
- 应用服务在未取得数据库兼容锁时不执行汇总并返回 `executed=false`。
- 应用服务成功时依次完成 hourly、histogram、daily 和 freshness 更新，并释放数据库锁。
- 应用服务在阶段间收到停止请求时不进入后续阶段、不更新成功水位，并释放数据库锁。
- 应用服务异常时更新失败水位、释放数据库锁并向 scheduler 传播异常。
- 生产代码和配置中不再存在 `AnalyticsRollupScheduler` 或旧 analytics rollup 调度配置。

完成后运行 opencode-runtime analytics/Handler 定向测试、`test-agent-opencode-runtime -am` 模块测试、后端全量测试或按既有阻塞如实记录、生产打包、`git diff --check`。同步更新 opencode-runtime README、scheduler 业务任务示例、后端部署配置说明和数据库文档；本次不修改 HTTP API、RunEvent、DTO 或 Flyway migration。
