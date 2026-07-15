# 定时任务执行记录清理设计

## 目标

定时任务执行记录只保留最近 7 天。新增一个每天执行一次的框架维护任务，按运行结束时间删除超过 7 天的已结束 `scheduled_task_runs` 记录，并保留所有未结束记录。

## 方案选择

1. 推荐：在 `test-agent-scheduler` 注册清理 handler，在 `test-agent-domain` 增加清理持久化端口，由 `test-agent-persistence` 通过 MyBatis XML 实现删除 SQL。这样任务归属 scheduler 框架，业务模块不需要感知，同时满足新增关系型 SQL 必须使用 MyBatis XML 的约束。
2. 将 handler 放入 persistence：会让持久化模块承载调度业务，不符合模块职责。
3. 直接扩展现有 `JdbcScheduledTaskRepository`：实现简单，但会新增不符合项目规范的 JDBC SQL。

采用方案 1。

## 运行行为

- 任务 key：`scheduler.run-retention-cleanup`。
- 管理名称：`清理定时任务执行记录`。
- 默认 Cron：`0 0 0 * * *`，使用 scheduler 统一的 UTC 时区，每天执行一次。
- 默认锁租约：5 分钟，防止多节点重复清理。
- 截止时间：任务执行时钟的当前时间减去 7 天。
- 删除条件：`ended_at` 不为空、`ended_at < cutoff`，且状态属于当前已结束状态 `SUCCEEDED`、`FAILED`、`SKIPPED`、`MANUALLY_STOPPED`。
- `PENDING`、`RUNNING`、`STOPPING` 不删除，即使历史数据存在异常的结束时间也不误删活动记录。
- handler 返回删除数量、截止时间和保留天数，由 scheduler 统一保存到本次运行结果。
- scheduler 关闭时任务仍注册并展示，但不执行，遵循现有框架语义。

## 持久化与性能

新增 `ScheduledTaskRunRetentionRepository#deleteEndedBefore(Instant cutoff)` domain 端口。persistence 新增 MyBatis mapper、XML 和 Repository 适配器；SQL 只在 XML 中维护。新增 `ended_at` 索引 migration，避免每日清理对运行记录表执行全表扫描。

## 错误与兼容性

删除失败由现有 `ScheduledTaskRunner` 统一记录为失败运行，不吞异常、不写敏感数据。无新增 HTTP API、SSE 事件、DTO 或 generated SDK；现有任务管理 API 自动展示代码注册的新任务。旧运行记录只按新任务首次执行时清理，不改变已有记录字段和状态。

## 测试

- scheduler 单元测试验证 handler 使用固定时钟计算 7 天截止时间、返回删除数量，并验证任务 key、Cron、锁 TTL。
- persistence MyBatis 集成测试验证只删除截止时间之前的已结束记录，保留 7 天内记录和未结束记录。
- 运行 scheduler 模块、persistence 模块定向测试及后端构建，并检查 migration、XML 和依赖边界。
