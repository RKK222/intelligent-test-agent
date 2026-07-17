# 定时任务执行记录清理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 注册一个每天执行一次的 scheduler 维护任务，删除超过 7 天的已结束 `scheduled_task_runs` 记录。

**Architecture:** `test-agent-scheduler` 提供 `ScheduledTaskRunRetentionTaskHandler`，通过 `test-agent-domain` 的 `ScheduledTaskRunRetentionRepository` 端口请求清理。`test-agent-persistence` 使用 MyBatis mapper/XML 实现删除，并通过 Flyway 为 `ended_at` 建索引；现有 scheduler runner 继续负责 Cron、Redis 锁、运行记录和错误状态。

**Tech Stack:** Java 21、Spring Boot 4.1、Spring Cron、MyBatis XML、Flyway、H2 PostgreSQL mode、JUnit 5、AssertJ、Mockito。

## Global Constraints

- 只修改与执行记录保留相关的最小范围，不修改 `.env.local` 等环境配置。
- 新增关系型 SQL 必须位于 `src/main/resources/mybatis/**/*.xml`，不能在现有 `JdbcScheduledTaskRepository` 中新增 JDBC SQL。
- 生产代码新增类、方法和边界逻辑使用中文注释；不修改 generated SDK。
- 任务通过现有 scheduler 框架执行，使用 Redis 分布式锁，不自行实现锁和运行记录。
- 删除条件以 `ended_at < now - 7 days` 为准，并显式排除 `PENDING`、`RUNNING`、`STOPPING`。
- 任何 API、事件、DTO、生成 SDK 均不变；数据库新增索引必须有 Flyway migration 并同步数据库部署文档。

---

### Task 1: 增加领域清理端口和 scheduler handler 红灯测试

**Files:**
- Create: `backend/test-agent-domain/src/main/java/com/enterprise/testagent/domain/scheduler/ScheduledTaskRunRetentionRepository.java`
- Create: `backend/test-agent-scheduler/src/main/java/com/enterprise/testagent/scheduler/ScheduledTaskRunRetentionTaskHandler.java`
- Create: `backend/test-agent-scheduler/src/test/java/com/enterprise/testagent/scheduler/ScheduledTaskRunRetentionTaskHandlerTest.java`

**Interfaces:**
- `ScheduledTaskRunRetentionRepository#deleteEndedBefore(Instant cutoff)` returns the number of deleted rows.
- Handler implements `ScheduledTaskHandler`, exposes task key `scheduler.run-retention-cleanup`, Cron `0 0 0 * * *`, lock TTL 5 minutes, and returns `ScheduledTaskResult` with `deletedCount`, `cutoff`, and `retentionDays`.

- [ ] **Step 1: Write the failing test**

Add a Mockito-backed test with a fixed UTC clock at `2026-07-15T00:00:00Z`. Verify `run` calls `deleteEndedBefore(2026-07-08T00:00:00Z)`, returns `deletedCount=3` and `retentionDays=7`, and verify task metadata:

```java
@Test
void deletesRunsEndedBeforeSevenDayCutoff() {
    ScheduledTaskRunRetentionRepository repository = mock(ScheduledTaskRunRetentionRepository.class);
    when(repository.deleteEndedBefore(Instant.parse("2026-07-08T00:00:00Z"))).thenReturn(3);
    ScheduledTaskRunRetentionTaskHandler handler = new ScheduledTaskRunRetentionTaskHandler(
            repository,
            Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC));

    ScheduledTaskResult result = handler.run(testContext());

    verify(repository).deleteEndedBefore(Instant.parse("2026-07-08T00:00:00Z"));
    assertThat(result.result())
            .containsEntry("deletedCount", 3)
            .containsEntry("retentionDays", 7)
            .containsEntry("cutoff", "2026-07-08T00:00:00Z");
    assertThat(handler.taskKey()).isEqualTo(new ScheduledTaskKey("scheduler.run-retention-cleanup"));
    assertThat(handler.cronExpression()).isEqualTo("0 0 0 * * *");
    assertThat(handler.lockTtl()).isEqualTo(Duration.ofMinutes(5));
}
```

Use the existing `ScheduledTaskContext` test construction pattern with a generated test run ID, the same task key, `CRON`, the fixed scheduled fire time, and trace ID.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && mvn -pl test-agent-scheduler -am -Dtest=ScheduledTaskRunRetentionTaskHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation/test failure because the repository port and handler do not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create the domain port:

```java
public interface ScheduledTaskRunRetentionRepository {
    int deleteEndedBefore(Instant cutoff);
}
```

Implement the handler as a Spring `@Component` with constants `RETENTION_DAYS=7`, `CRON_EXPRESSION="0 0 0 * * *"`, and `LOCK_TTL=Duration.ofMinutes(5)`. In `run`, compute `clock.instant().minus(RETENTION_DAYS, ChronoUnit.DAYS)`, call the port, and return a structured result. Validate constructor dependencies and add Chinese Javadoc for the cutoff and active-record safety semantics.

- [ ] **Step 4: Run test to verify it passes**

Run the same Maven command. Expected: the new handler test passes with zero failures.

### Task 2: Add MyBatis XML persistence adapter and integration test

**Files:**
- Create: `backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/mybatis/ScheduledTaskRunRetentionMapper.java`
- Create: `backend/test-agent-persistence/src/main/resources/mybatis/ScheduledTaskRunRetentionMapper.xml`
- Create: `backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/mybatis/MyBatisScheduledTaskRunRetentionRepository.java`
- Create: `backend/test-agent-persistence/src/test/java/com/enterprise/testagent/persistence/MyBatisScheduledTaskRunRetentionRepositoryIntegrationTest.java`

**Interfaces:**
- Mapper method: `int deleteEndedBefore(@Param("cutoff") Instant cutoff)`.
- Repository implements the domain port and delegates without adding business filtering outside the XML.

- [ ] **Step 1: Write the failing integration test**

Create an H2 PostgreSQL-mode Flyway test following the existing MyBatis integration tests. Insert one registered scheduler task, then insert runs representing:

- a `SUCCEEDED` row ended before `2026-07-08T00:00:00Z`;
- a `FAILED` row ended exactly at the cutoff;
- a `SKIPPED` row ended within seven days;
- a `MANUALLY_STOPPED` row ended before the cutoff;
- a `PENDING` row with a historical scheduled time and no end time;
- a `RUNNING` row with a historical scheduled time and no end time.

Call `deleteEndedBefore(2026-07-08T00:00:00Z)` and assert the return count is 2, the old terminal rows are gone, the exact-cutoff and recent rows remain, and the active rows remain.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && mvn -pl test-agent-persistence -am -Dtest=MyBatisScheduledTaskRunRetentionRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation failure because the mapper, XML, and repository do not exist yet.

- [ ] **Step 3: Write minimal implementation**

Declare the mapper with `@Mapper` and `@Param` only. Add this XML delete statement:

```xml
<delete id="deleteEndedBefore">
    delete from scheduled_task_runs
    where ended_at is not null
      and ended_at &lt; #{cutoff}
      and status in ('SUCCEEDED', 'FAILED', 'SKIPPED', 'MANUALLY_STOPPED')
</delete>
```

Add a `@Repository` adapter that injects the mapper and returns `mapper.deleteEndedBefore(cutoff)`. Keep all SQL in XML and add Chinese comments explaining that active statuses are deliberately excluded.

- [ ] **Step 4: Run test to verify it passes**

Run the same Maven command. Expected: the integration test passes and the XML mapper is loaded by the existing MyBatis configuration.

### Task 3: Add the retention index migration and synchronize documentation

**Files:**
- Create: `backend/test-agent-persistence/src/main/resources/db/migration/V20260715000000__add_scheduler_run_retention_index.sql`
- Modify: `backend/test-agent-scheduler/README.md`
- Modify: `backend/test-agent-scheduler/src/main/java/com/enterprise/testagent/scheduler/PACKAGE.md`
- Modify: `backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/PACKAGE.md`
- Modify: `backend/test-agent-persistence/README.md` if its module-level task list differs from PACKAGE.md
- Modify: `docs/deployment/database.md`
- Modify: `docs/deployment/backend.md`

**Interfaces:**
- Migration creates `idx_scheduled_task_runs_ended_at` on `scheduled_task_runs(ended_at)` using the repository's timestamped migration naming convention.
- Documentation states the built-in task, UTC daily schedule, seven-day `ended_at` retention, active-record exclusion, and MyBatis ownership of the delete SQL.

- [ ] **Step 1: Write the failing migration/index assertion if an existing migration test hook is available**

Extend the persistence integration test or migration verification assertion to check that the retention index is present after Flyway migration. If the project has no portable index metadata helper in the existing test support, use the delete integration test as the behavior-level proof and add a source-level migration assertion only if existing migration tests already use that pattern.

- [ ] **Step 2: Run the migration test to verify the new index is absent**

Run the persistence retention integration test before adding the migration and confirm the migration/index assertion fails for the expected missing index, or record that the existing behavior test does not expose index metadata and proceed with the migration plus full Flyway parse verification.

- [ ] **Step 3: Add the migration and docs**

Create:

```sql
create index if not exists idx_scheduled_task_runs_ended_at
    on scheduled_task_runs(ended_at);
```

Update scheduler docs to describe the built-in framework maintenance task rather than claiming the module has no concrete task. Update persistence docs with the MyBatis retention mapper and migration. Update database deployment docs under the scheduler table section and backend deployment docs under scheduler startup behavior. Do not change API or event docs because no wire contract changes.

- [ ] **Step 4: Run the persistence tests and documentation checks**

Run:

```bash
cd backend && mvn -pl test-agent-persistence -am test
git diff --check
```

Expected: persistence tests pass and `git diff --check` emits no errors.

### Task 4: Cross-module verification and commit

**Files:**
- Modify: `.agents/session-log.md` only if this task produces a reusable migration/testing pitfall or deployment decision.

- [ ] **Step 1: Run focused scheduler and persistence tests**

```bash
cd backend && mvn -pl test-agent-scheduler,test-agent-persistence -am test
```

- [ ] **Step 2: Run the application build without tests**

```bash
cd backend && mvn -pl test-agent-app -am package -DskipTests
```

- [ ] **Step 3: Review repository boundaries and session log**

Run:

```bash
rg -n "deleteEndedBefore|ScheduledTaskRunRetention|scheduler.run-retention-cleanup|idx_scheduled_task_runs_ended_at" backend docs
git diff --check
git status --short
sed -n '1,220p' .agents/session-log.md
```

Confirm no `.env.local`, generated SDK, unrelated worktree changes, API/event files, or new JDBC SQL were included. Add one `Why / What / How / Result` session-log entry only if the task has a durable pitfall or decision worth handing off.

- [ ] **Step 4: Commit only task files**

Stage the exact changed files and commit with a Chinese message:

```bash
git add backend/test-agent-domain/src/main/java/com/enterprise/testagent/domain/scheduler/ScheduledTaskRunRetentionRepository.java \
  backend/test-agent-scheduler/src/main/java/com/enterprise/testagent/scheduler/ScheduledTaskRunRetentionTaskHandler.java \
  backend/test-agent-scheduler/src/test/java/com/enterprise/testagent/scheduler/ScheduledTaskRunRetentionTaskHandlerTest.java \
  backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/mybatis/ScheduledTaskRunRetentionMapper.java \
  backend/test-agent-persistence/src/main/resources/mybatis/ScheduledTaskRunRetentionMapper.xml \
  backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/mybatis/MyBatisScheduledTaskRunRetentionRepository.java \
  backend/test-agent-persistence/src/test/java/com/enterprise/testagent/persistence/MyBatisScheduledTaskRunRetentionRepositoryIntegrationTest.java \
  backend/test-agent-persistence/src/main/resources/db/migration/V20260715000000__add_scheduler_run_retention_index.sql \
  backend/test-agent-scheduler/README.md \
  backend/test-agent-scheduler/src/main/java/com/enterprise/testagent/scheduler/PACKAGE.md \
  backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/PACKAGE.md \
  backend/test-agent-persistence/README.md \
  docs/deployment/database.md \
  docs/deployment/backend.md \
  docs/superpowers/specs/2026-07-15-scheduler-run-retention-cleanup-design.md \
  docs/superpowers/plans/2026-07-15-scheduler-run-retention-cleanup.md
git commit -m "新增定时任务执行记录清理任务"
```

Expected: commit succeeds and contains only the design, implementation, test, migration, and synchronized documentation files for this request.
