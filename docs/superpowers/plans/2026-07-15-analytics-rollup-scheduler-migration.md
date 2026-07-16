# 运营分析汇总定时任务迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将运营分析汇总从应用内 `@Scheduled` 迁移为统一定时任务 `opencode-runtime.analytics-rollup`，支持计划管理、运行审计、分布式互斥与协作式停止。

**Architecture:** 在 `test-agent-opencode-runtime` 增加 `ScheduledTaskHandler`，由统一 scheduler 负责 cron、Redis owner lock、续租和运行记录；现有 `AnalyticsRollupApplicationService` 继续负责业务汇总，并暂时保留数据库锁作为滚动部署兼容保护。旧 `AnalyticsRollupScheduler` 及其专用配置删除。

**Tech Stack:** Java 21、Spring Boot、JUnit 5、Mockito、Maven、MyBatis 持久化接口、TestAgent scheduler framework

## Global Constraints

- 不修改现有数据库结构、HTTP API、事件协议和前端。
- 不修改 `.env.local` 或其他环境配置文件。
- 保留 `analytics_job_locks` 业务锁，兼容新旧版本滚动部署。
- 只提交本任务文件，保留工作区已有的 Mermaid 前端改动。
- 新增或复杂逻辑使用中文注释，文档同步描述统一 scheduler 的真实行为。

---

### Task 1: 为汇总服务增加结果与协作式停止

**Files:**

- Create: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/analytics/AnalyticsRollupApplicationServiceTest.java`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/analytics/AnalyticsRollupApplicationService.java`

- [x] **Step 1: 写失败测试，覆盖锁冲突、阶段间停止、成功结果和异常水位**

测试应断言：数据库锁被占用时返回 `executed=false`；小时汇总完成后收到停止信号时不继续日汇总或更新成功水位；完整成功返回低敏窗口结果；异常仍写 `FAILED` 水位并释放锁。

- [x] **Step 2: 运行服务测试并确认失败**

Run: `(cd backend && mvn -pl test-agent-opencode-runtime -am -Dtest=AnalyticsRollupApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test)`

Expected: FAIL，因为服务尚无 `BooleanSupplier` 参数和结构化 `Result`。

- [x] **Step 3: 最小实现服务结果和停止检查**

将入口调整为：

```java
public Result rollupRecent(String traceId, BooleanSupplier stopRequested)
```

在获取锁前、获取锁后、小时汇总后、日汇总后检查停止信号。`Result` 只包含 `executed`、`stopped` 与汇总窗口，提供 `toMap()` 给 scheduler 记录；只有完整成功才写 `FRESH` 水位。

- [x] **Step 4: 运行服务测试并确认通过**

Run: `(cd backend && mvn -pl test-agent-opencode-runtime -am -Dtest=AnalyticsRollupApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test)`

Expected: PASS

### Task 2: 注册统一定时任务并移除旧调度入口

**Files:**

- Create: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/analytics/AnalyticsRollupTaskHandlerTest.java`
- Create: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/analytics/AnalyticsRollupTaskHandler.java`
- Delete: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/analytics/AnalyticsRollupScheduler.java`
- Modify: `backend/test-agent-app/src/main/resources/application.yml`

- [x] **Step 1: 写失败测试，覆盖任务元数据、上下文透传和人工停止**

测试应断言任务 key 为 `opencode-runtime.analytics-rollup`、cron 为 `0 */5 * * * *`、锁 TTL 为 5 分钟；handler 透传 traceId 和动态停止信号；运行前或服务返回后收到停止请求时抛出框架停止异常。

- [x] **Step 2: 运行 handler 测试并确认失败**

Run: `(cd backend && mvn -pl test-agent-opencode-runtime -am -Dtest=AnalyticsRollupTaskHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test)`

Expected: FAIL，因为 handler 尚不存在。

- [x] **Step 3: 最小实现 handler**

实现 `ScheduledTaskHandler`，固定元数据：

```java
taskKey = opencode-runtime.analytics-rollup
name = TestAgent 运营分析汇总
cron = 0 */5 * * * *
lockTtl = PT5M
```

`run` 在调用服务前后执行 `context.throwIfStopRequested()`，服务使用 `context::stopRequested`，返回 `ScheduledTaskResult.of(result.toMap())`。

- [x] **Step 4: 删除旧 scheduler 和专用配置**

删除 `AnalyticsRollupScheduler`，并从 `application.yml` 删除 `test-agent.analytics.rollup.enabled` 与 `fixed-delay-ms`。

- [x] **Step 5: 运行 handler 与服务测试并确认通过**

Run: `(cd backend && mvn -pl test-agent-opencode-runtime -am -Dtest=AnalyticsRollupApplicationServiceTest,AnalyticsRollupTaskHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test)`

Expected: PASS

### Task 3: 同步稳定文档并完成全量校验

**Files:**

- Modify: `backend/test-agent-app/README.md`
- Modify: `backend/test-agent-opencode-runtime/README.md`
- Modify: `backend/test-agent-scheduler/README.md`
- Modify: `docs/deployment/backend.md`
- Modify: `docs/deployment/database.md`
- Modify: `.agents/session-log.md`

- [x] **Step 1: 更新模块与部署文档**

说明运营分析汇总已注册为统一定时任务、默认 cron、Redis 框架锁与暂保数据库锁的职责边界；注明旧专用配置已删除，任务启停和 cron 由统一计划配置管理；澄清当前审计写入 `scheduled_task_runs`，`analytics_rollup_job_runs` 仅为历史预留表。

- [x] **Step 2: 扫描旧入口和配置残留**

Run: `rg -n "AnalyticsRollupScheduler|test-agent\.analytics\.rollup|TEST_AGENT_ANALYTICS_ROLLUP|rollupRecent\(traceId\)" backend --glob '*.java' --glob '*.yml' --glob '*.yaml'`

Expected: 无旧入口或旧配置引用。

- [x] **Step 3: 运行模块测试**

Run: `(cd backend && mvn -pl test-agent-opencode-runtime -am test)`

Expected: PASS

Actual: Reactor 在 `test-agent-opencode-runtime` 的既有测试源码编译阶段失败；11 个无关 process/runtime 测试尚未适配当前 `OpencodeProcessManagementRepository` / DTO 接口。本任务新增的 8 个测试已通过隔离编译执行，主代码 Reactor 跳过测试打包通过。

- [x] **Step 4: 运行格式与变更检查**

Run: `git diff --check`

Run: `git status --short`

Expected: 无空白错误；仅本任务文件和用户既有 Mermaid 文件发生变化。

- [x] **Step 5: 更新会话日志并复核近期记录**

将本次实现结果合并到现有运营分析迁移记录，按 `Why / What / How / Result` 描述；提交前重新阅读 `.agents/session-log.md` 尾部，确认没有覆盖其他开发者成果。

- [x] **Step 6: 仅暂存本任务文件并中文提交**

Run: `git diff --cached --name-only`

Expected: 不含任何 Mermaid 前端文件。

Commit: `后端：迁移运营分析汇总到统一定时任务`
