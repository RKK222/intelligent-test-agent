package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.persistence.mybatis.MyBatisNightExecutionTaskRepository;
import com.enterprise.testagent.persistence.mybatis.NightExecutionTaskMapper;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 验证夜间任务、会话锁和时段容量全部通过 MyBatis XML 持久化。 */
class MyBatisNightExecutionTaskRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");
    private static final Instant SLOT = Instant.parse("2026-07-18T13:00:00Z");
    private static final UserId USER = new UserId("usr_night_repository");
    private static final WorkspaceId WORKSPACE = new WorkspaceId("wrk_night_repository");
    private static final SessionId SESSION = new SessionId("ses_night_repository");

    private SingleConnectionDataSource dataSource;
    private NightExecutionTaskRepository repository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                ("jdbc:h2:mem:testagent_night_mybatis_%s;MODE=PostgreSQL;DATABASE_TO_LOWER=true;"
                        + "INIT=CREATE DOMAIN IF NOT EXISTS timestamptz AS TIMESTAMP WITH TIME ZONE")
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("20260715000000").load().migrate();
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260718211000__create_night_execution_tasks.sql")).execute(dataSource);

        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into scheduled_tasks(task_key,name,cron_expression,enabled,lock_ttl_seconds,"
                        + "registration_status,trace_id,created_at,updated_at) values(?,?,?,?,?,?,?,?,?)",
                "opencode-runtime.night-execution", "legacy night", "0 0/15 * * * ?", true, 300,
                "REGISTERED", "trace_legacy_night", NOW, NOW);
        for (String status : java.util.List.of("PENDING", "RUNNING", "STOPPING")) {
            jdbc.update("insert into scheduled_task_runs(task_run_id,task_key,trigger_type,status,"
                            + "scheduled_fire_at,result_json,trace_id,created_at,updated_at) "
                            + "values(?,?,?,?,?,?,?,?,?)",
                    "str_legacy_" + status.toLowerCase(), "opencode-runtime.night-execution",
                    "USER_PLAN", status, NOW, "{}", "trace_legacy_night", NOW, NOW);
        }
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260721134000__migrate_night_execution_to_xxl.sql")).execute(dataSource);

        jdbc.update("insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at) "
                + "values(?,?,?,?,?,?,?)", USER.value(), "u_night_repository", "night-user", "hash", "ACTIVE", NOW, NOW);
        jdbc.update("insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at) "
                + "values(?,?,?,?,?,?,?)", WORKSPACE.value(), "night", "/tmp/night", "ACTIVE", "trace_night_repo", NOW, NOW);
        jdbc.update("insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at) "
                + "values(?,?,?,?,?,?,?)", SESSION.value(), WORKSPACE.value(), "night", "ACTIVE", "trace_night_repo", NOW, NOW);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        NightExecutionTaskMapper mapper = new SqlSessionTemplate(factory).getMapper(NightExecutionTaskMapper.class);
        repository = new MyBatisNightExecutionTaskRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void persistsTaskAndMaintainsSessionLockAndCapacityAtomically() {
        NightExecutionTask task = task();

        assertThat(repository.reserveSlot(SLOT, 1, NOW)).isTrue();
        assertThat(repository.reserveSlot(SLOT, 1, NOW)).isFalse();
        repository.save(task);
        assertThat(repository.insertSessionLock(SESSION, task.taskId(), USER, NOW)).isTrue();

        assertThat(repository.findById(task.taskId())).contains(task);
        assertThat(repository.findByOwnerAndClientRequestId(USER, "request-night-1")).contains(task);
        assertThat(repository.findPendingBySession(SESSION)).contains(task);
        assertThat(repository.findPendingByOwner(USER, new PageRequest(1, 20)).items()).containsExactly(task);
        assertThat(repository.hasSessionLock(SESSION)).isTrue();
        assertThat(repository.reservationCounts(SLOT, SLOT.plusSeconds(900))).containsEntry(SLOT, 1);

        repository.deleteSessionLock(SESSION, task.taskId());
        repository.releaseSlot(SLOT, NOW.plusSeconds(1));
        assertThat(repository.hasSessionLock(SESSION)).isFalse();
        assertThat(repository.reservationCounts(SLOT, SLOT.plusSeconds(900))).containsEntry(SLOT, 0);
        assertThat(repository.deleteReservationsBefore(SLOT.plusSeconds(1))).isEqualTo(1);
        assertThat(repository.reservationCounts(SLOT, SLOT.plusSeconds(900))).isEmpty();
    }

    @Test
    void scheduledScanUsesSlotAndWindowInsteadOfUpdatedAtDelay() {
        NightExecutionTask scheduled = task().reschedule(
                SLOT, SLOT.plusSeconds(900), "linux-night-1", SLOT.plusSeconds(30));
        repository.save(scheduled);

        assertThat(repository.findScheduledDue(SLOT.minusSeconds(1), 10)).isEmpty();
        assertThat(repository.findScheduledDue(SLOT.plusSeconds(1), 10)).containsExactly(scheduled);
        assertThat(repository.findScheduledDue(scheduled.windowEnd(), 10)).isEmpty();
        assertThat(repository.findScheduledWindowExpired(scheduled.windowEnd(), 10)).containsExactly(scheduled);
    }

    @Test
    void claimAndCompletionAreFencedByTargetAndAttemptId() {
        NightExecutionTask scheduled = task();
        repository.save(scheduled);
        Instant claimedAt = SLOT.plusSeconds(1);
        NightExecutionTask dispatching = scheduled.startDispatch(
                "nda_attempt_current", "bjp_backend_current", claimedAt.plusSeconds(300), claimedAt);

        assertThat(repository.claimForDispatch(dispatching, "linux-night-stale")).isFalse();
        assertThat(repository.claimForDispatch(dispatching, "linux-night-1")).isTrue();
        assertThat(repository.renewDispatchLease(
                scheduled.taskId(), "nda_attempt_stale", claimedAt.plusSeconds(600), claimedAt.plusSeconds(60)))
                .isFalse();
        assertThat(repository.renewDispatchLease(
                scheduled.taskId(), "nda_attempt_current", claimedAt.plusSeconds(600), claimedAt.plusSeconds(60)))
                .isTrue();
        assertThat(repository.renewDispatchLease(
                scheduled.taskId(), "nda_attempt_current", scheduled.windowEnd().plusSeconds(300),
                scheduled.windowEnd()))
                .isFalse();

        NightExecutionTask retry = dispatching.retryDispatch(claimedAt.plusSeconds(61));
        assertThat(repository.updateDispatchIfAttempt(retry, "nda_attempt_stale")).isFalse();
        assertThat(repository.updateDispatchIfAttempt(retry, "nda_attempt_current")).isTrue();
        NightExecutionTask restored = repository.findById(scheduled.taskId()).orElseThrow();
        assertThat(restored.status()).isEqualTo(NightExecutionTaskStatus.SCHEDULED);
        assertThat(restored.dispatchAttemptId()).isNull();
        assertThat(restored.dispatchOwnerBackendProcessId()).isNull();
        assertThat(restored.dispatchLeaseUntil()).isNull();
    }

    @Test
    void scheduledUpdatesUseStateVersionToRejectConcurrentOverwrite() {
        NightExecutionTask scheduled = task();
        repository.save(scheduled);
        NightExecutionTask adjusted = scheduled.reschedule(
                SLOT.plusSeconds(900), SLOT.plusSeconds(1800), "linux-night-1", NOW.plusSeconds(1));
        NightExecutionTask cancelled = scheduled.cancel(NOW.plusSeconds(2));

        assertThat(repository.updateIfStatus(adjusted, NightExecutionTaskStatus.SCHEDULED)).isTrue();
        assertThat(repository.updateIfStatus(cancelled, NightExecutionTaskStatus.SCHEDULED)).isFalse();
        assertThat(repository.findById(scheduled.taskId())).contains(adjusted);
    }

    @Test
    void migrationTerminatesEveryLegacyUserPlanActiveState() {
        for (String previous : java.util.List.of("pending", "running", "stopping")) {
            assertThat(jdbc.queryForMap(
                    "select status, ended_at, skip_reason from scheduled_task_runs where task_run_id=?",
                    "str_legacy_" + previous))
                    .containsEntry("STATUS", "SKIPPED")
                    .containsEntry("SKIP_REASON", "夜间执行已迁移至 XXL-JOB")
                    .containsKey("ENDED_AT");
        }
    }

    @Test
    void terminalCleanupDoesNotDeleteATaskChangedAfterTheScan() {
        NightExecutionTask failed = task().fail("WINDOW_EXPIRED", "窗口结束", NOW.plusSeconds(1));
        repository.save(failed);
        NightExecutionTask scanned = repository.findTerminalBefore(NOW.plusSeconds(10), 10)
                .getFirst();
        NightExecutionTask dismissed = failed.dismiss(NOW.plusSeconds(2));
        assertThat(repository.updateIfStatus(dismissed, NightExecutionTaskStatus.FAILED)).isTrue();

        assertThat(repository.deleteTerminalIfUnchanged(
                scanned.taskId(), scanned.stateVersion(), NOW.plusSeconds(10))).isFalse();
        assertThat(repository.findById(failed.taskId())).contains(dismissed);
        assertThat(repository.deleteTerminalIfUnchanged(
                dismissed.taskId(), dismissed.stateVersion(), NOW.plusSeconds(10))).isTrue();
        assertThat(repository.findById(failed.taskId())).isEmpty();
    }

    private NightExecutionTask task() {
        return new NightExecutionTask(
                new NightExecutionTaskId("net_night_repository"), USER, SESSION, WORKSPACE,
                "request-night-1", "夜间执行", "生成回归测试", "{\"prompt\":\"生成回归测试\"}",
                NightExecutionTaskStatus.SCHEDULED, SLOT, SLOT.plusSeconds(900),
                Instant.parse("2026-07-18T23:00:00Z"), "linux-night-1", null, null, 0,
                false, null, null, null, null, null, "trace_night_repo", NOW, NOW);
    }
}
