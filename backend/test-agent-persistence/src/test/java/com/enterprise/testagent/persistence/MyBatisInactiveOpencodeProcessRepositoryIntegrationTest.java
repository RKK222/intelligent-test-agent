package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.opencodeprocess.InactiveOpencodeProcessRepository;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.persistence.mybatis.InactiveOpencodeProcessMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisInactiveOpencodeProcessRepository;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 验证闲置进程候选只使用 MyBatis XML，并按全部 Run 来源和待投递夜间任务筛选。 */
class MyBatisInactiveOpencodeProcessRepositoryIntegrationTest {

    /** 北京时间 2026-08-04 02:00，对齐生产清理 Cron。 */
    private static final Instant NOW = Instant.parse("2026-08-03T18:00:00Z");
    private static final Instant CUTOFF = NOW.minusSeconds(15L * 24 * 60 * 60);
    private static final Instant TASK_SLOT_BEFORE = Instant.parse("2026-08-04T16:00:00Z");

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbc;
    private InactiveOpencodeProcessRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_inactive_process_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("20260715213000")
                .load()
                .migrate();
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260718211000__create_night_execution_tasks.sql")).execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260724143000__add_night_execution_schedule_mode.sql")).execute(dataSource);
        jdbc = JdbcClient.create(dataSource);
        seedTopology();
        repository = new MyBatisInactiveOpencodeProcessRepository(
                new SqlSessionTemplate(sqlSessionFactory()).getMapper(InactiveOpencodeProcessMapper.class));
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void findsOnlyStrictlyInactiveActiveBindingsOnRequestedServer() {
        seedUserAndProcess("usr_old", "ocp_old_process_123", "server-a", 4101,
                "RUNNING", "ACTIVE", CUTOFF.minusSeconds(1));
        seedUserAndProcess("usr_exact", "ocp_exact_process_123", "server-a", 4102,
                "RUNNING", "ACTIVE", CUTOFF);
        seedUserAndProcess("usr_other_server", "ocp_other_server_123", "server-b", 4201,
                "RUNNING", "ACTIVE", CUTOFF.minusSeconds(100));
        seedUserAndProcess("usr_inactive_binding", "ocp_inactive_bind_123", "server-a", 4103,
                "RUNNING", "INACTIVE", CUTOFF.minusSeconds(100));
        seedUserAndProcess("usr_stopped", "ocp_stopped_process_123", "server-a", 4104,
                "STOPPED", "ACTIVE", CUTOFF.minusSeconds(100));

        var candidates = repository.findCandidates(
                new LinuxServerId("server-a"), CUTOFF, NOW, TASK_SLOT_BEFORE, 50);

        assertThat(candidates)
                .extracting(candidate -> candidate.process().processId().value())
                .containsExactly("ocp_old_process_123");
        assertThat(candidates.getFirst().lastActivityAt()).isEqualTo(CUTOFF.minusSeconds(1));
    }

    @Test
    void everyRunSourceAndLegacySessionOwnershipRefreshActivity() {
        seedUserAndProcess("usr_manual", "ocp_manual_process_123", "server-a", 4111,
                "RUNNING", "ACTIVE", CUTOFF.minusSeconds(100));
        seedRun("usr_manual", "MANUAL", NOW.minusSeconds(300), false);
        seedUserAndProcess("usr_scheduled", "ocp_scheduled_proc_123", "server-a", 4112,
                "RUNNING", "ACTIVE", CUTOFF.minusSeconds(100));
        seedRun("usr_scheduled", "SCHEDULED_TASK", NOW.minusSeconds(200), false);
        seedUserAndProcess("usr_side", "ocp_side_process_123", "server-a", 4113,
                "UNHEALTHY", "ACTIVE", CUTOFF.minusSeconds(100));
        seedRun("usr_side", "SIDE_QUESTION", NOW.minusSeconds(100), false);
        seedUserAndProcess("usr_legacy", "ocp_legacy_process_123", "server-a", 4114,
                "RUNNING", "ACTIVE", CUTOFF.minusSeconds(100));
        seedRun("usr_legacy", "MANUAL", NOW.minusSeconds(50), true);

        assertThat(repository.findCandidates(
                new LinuxServerId("server-a"), CUTOFF, NOW, TASK_SLOT_BEFORE, 50)).isEmpty();
    }

    @Test
    void pendingTasksInTheActiveNightWindowOrCurrentBeijingDayProtectTheProcess() {
        seedUserAndProcess("usr_previous_night", "ocp_previous_night_123", "server-a", 4115,
                "RUNNING", "ACTIVE", CUTOFF.minusSeconds(100));
        seedNightTask(
                "usr_previous_night",
                "previous_night",
                "SCHEDULED",
                Instant.parse("2026-08-03T15:00:00Z"),
                Instant.parse("2026-08-03T23:00:00Z"));
        seedUserAndProcess("usr_today_dispatching", "ocp_today_dispatching_123", "server-a", 4116,
                "RUNNING", "ACTIVE", CUTOFF.minusSeconds(100));
        seedNightTask(
                "usr_today_dispatching",
                "today_dispatching",
                "DISPATCHING",
                Instant.parse("2026-08-04T13:00:00Z"),
                Instant.parse("2026-08-04T23:00:00Z"));

        seedUserAndProcess("usr_expired_task", "ocp_expired_task_123", "server-a", 4117,
                "RUNNING", "ACTIVE", CUTOFF.minusSeconds(100));
        seedNightTask(
                "usr_expired_task",
                "expired_task",
                "SCHEDULED",
                Instant.parse("2026-08-03T13:00:00Z"),
                Instant.parse("2026-08-03T15:00:00Z"));
        seedUserAndProcess("usr_tomorrow_task", "ocp_tomorrow_task_123", "server-a", 4118,
                "RUNNING", "ACTIVE", CUTOFF.minusSeconds(100));
        seedNightTask(
                "usr_tomorrow_task",
                "tomorrow_task",
                "SCHEDULED",
                Instant.parse("2026-08-04T17:00:00Z"),
                Instant.parse("2026-08-04T23:00:00Z"));
        seedUserAndProcess("usr_terminal_task", "ocp_terminal_task_123", "server-a", 4119,
                "RUNNING", "ACTIVE", CUTOFF.minusSeconds(100));
        seedNightTask(
                "usr_terminal_task",
                "terminal_task",
                "DISPATCHED",
                Instant.parse("2026-08-04T13:00:00Z"),
                Instant.parse("2026-08-04T23:00:00Z"));
        seedUserAndProcess("usr_without_task", "ocp_without_task_123", "server-a", 4120,
                "RUNNING", "ACTIVE", CUTOFF.minusSeconds(100));

        assertThat(repository.findCandidates(
                new LinuxServerId("server-a"), CUTOFF, NOW, TASK_SLOT_BEFORE, 50))
                .extracting(candidate -> candidate.process().userId().value())
                .containsExactlyInAnyOrder(
                        "usr_expired_task",
                        "usr_tomorrow_task",
                        "usr_terminal_task",
                        "usr_without_task");
    }

    @Test
    void revalidationStopsMatchingAfterNewRunIsPersisted() {
        seedUserAndProcess("usr_race", "ocp_race_process_123", "server-a", 4121,
                "RUNNING", "ACTIVE", CUTOFF.minusSeconds(100));
        OpencodeProcessId processId = new OpencodeProcessId("ocp_race_process_123");
        assertThat(repository.findCurrentCandidate(processId, CUTOFF, NOW, TASK_SLOT_BEFORE)).isPresent();

        seedRun("usr_race", "SIDE_QUESTION", NOW.minusSeconds(1), false);

        assertThat(repository.findCurrentCandidate(processId, CUTOFF, NOW, TASK_SLOT_BEFORE)).isEmpty();
    }

    @Test
    void revalidationStopsMatchingAfterPendingTaskIsPersisted() {
        seedUserAndProcess("usr_task_race", "ocp_task_race_123", "server-a", 4122,
                "RUNNING", "ACTIVE", CUTOFF.minusSeconds(100));
        OpencodeProcessId processId = new OpencodeProcessId("ocp_task_race_123");
        assertThat(repository.findCurrentCandidate(processId, CUTOFF, NOW, TASK_SLOT_BEFORE)).isPresent();

        seedNightTask(
                "usr_task_race",
                "task_race",
                "SCHEDULED",
                Instant.parse("2026-08-03T19:00:00Z"),
                Instant.parse("2026-08-03T23:00:00Z"));

        assertThat(repository.findCurrentCandidate(processId, CUTOFF, NOW, TASK_SLOT_BEFORE)).isEmpty();
    }

    private void seedTopology() {
        jdbc.sql("""
                insert into linux_servers(
                    linux_server_id, name, status, capacity_summary_json, last_heartbeat_at,
                    trace_id, created_at, updated_at)
                values
                    ('server-a', 'server a', 'ONLINE', '{}', :now, 'trace_seed', :now, :now),
                    ('server-b', 'server b', 'ONLINE', '{}', :now, 'trace_seed', :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into opencode_containers(
                    container_id, linux_server_id, container_name, port_start, port_end,
                    max_processes, current_processes, status, last_heartbeat_at,
                    trace_id, created_at, updated_at)
                values
                    ('container-a', 'server-a', 'container a', 4100, 4199, 100, 0, 'READY', :now,
                     'trace_seed', :now, :now),
                    ('container-b', 'server-b', 'container b', 4200, 4299, 100, 0, 'READY', :now,
                     'trace_seed', :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                values('wrk_inactive_process', 'inactive process', '/tmp/inactive', 'ACTIVE', 'trace_seed', :now, :now)
                """).param("now", NOW).update();
    }

    private void seedUserAndProcess(
            String userId,
            String processId,
            String linuxServerId,
            int port,
            String processStatus,
            String bindingStatus,
            Instant startedAt) {
        jdbc.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                values(:userId, :authId, :username, 'hash', 'ACTIVE', :startedAt, :startedAt)
                """)
                .param("userId", userId)
                .param("authId", "auth_" + userId)
                .param("username", "name_" + userId)
                .param("startedAt", startedAt)
                .update();
        String containerId = "server-a".equals(linuxServerId) ? "container-a" : "container-b";
        jdbc.sql("""
                insert into opencode_server_processes(
                    process_id, user_id, linux_server_id, container_id, port, pid, base_url, status,
                    session_path, config_path, started_at, last_health_check_at, health_message,
                    trace_id, created_at, updated_at)
                values(:processId, :userId, :serverId, :containerId, :port, :pid, :baseUrl, :status,
                    :sessionPath, :configPath, :startedAt, :now, 'healthy',
                    'trace_seed', :startedAt, :now)
                """)
                .param("processId", processId)
                .param("userId", userId)
                .param("serverId", linuxServerId)
                .param("containerId", containerId)
                .param("port", port)
                .param("pid", 10_000L + port)
                .param("baseUrl", "http://127.0.0.1:" + port)
                .param("status", processStatus)
                .param("sessionPath", "/tmp/session/" + userId)
                .param("configPath", "/tmp/config/" + userId)
                .param("startedAt", startedAt)
                .param("now", NOW)
                .update();
        jdbc.sql("""
                insert into user_opencode_process_bindings(
                    user_id, agent_id, process_id, linux_server_id, port, status,
                    trace_id, created_at, updated_at)
                values(:userId, 'opencode', :processId, :serverId, :port, :status,
                    'trace_seed', :startedAt, :now)
                """)
                .param("userId", userId)
                .param("processId", processId)
                .param("serverId", linuxServerId)
                .param("port", port)
                .param("status", bindingStatus)
                .param("startedAt", startedAt)
                .param("now", NOW)
                .update();
    }

    private void seedRun(String userId, String sourceType, Instant updatedAt, boolean legacyOwner) {
        String suffix = userId.replace("usr_", "");
        String sessionId = "ses_inactive_" + suffix;
        String runId = "run_inactive_" + suffix;
        jdbc.sql("""
                insert into sessions(
                    session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                    source_type, created_by_user_id)
                values(:sessionId, 'wrk_inactive_process', 'inactive', 'ACTIVE', 'trace_seed',
                    :updatedAt, :updatedAt, :sourceType, :userId)
                """)
                .param("sessionId", sessionId)
                .param("updatedAt", updatedAt)
                .param("sourceType", sourceType)
                .param("userId", userId)
                .update();
        jdbc.sql("""
                insert into runs(
                    run_id, session_id, workspace_id, status, trace_id, created_at, updated_at,
                    source_type, triggered_by_user_id)
                values(:runId, :sessionId, 'wrk_inactive_process', 'SUCCEEDED', 'trace_seed',
                    :updatedAt, :updatedAt, :sourceType, :triggeredBy)
                """)
                .param("runId", runId)
                .param("sessionId", sessionId)
                .param("updatedAt", updatedAt)
                .param("sourceType", sourceType)
                .param("triggeredBy", legacyOwner ? null : userId)
                .update();
    }

    private void seedNightTask(
            String userId,
            String suffix,
            String status,
            Instant slotStart,
            Instant windowEnd) {
        String sessionId = "ses_night_cleanup_" + suffix;
        jdbc.sql("""
                insert into sessions(
                    session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                    source_type, created_by_user_id)
                values(:sessionId, 'wrk_inactive_process', 'night cleanup', 'ACTIVE', 'trace_seed',
                    :now, :now, 'SCHEDULED_TASK', :userId)
                """)
                .param("sessionId", sessionId)
                .param("now", NOW)
                .param("userId", userId)
                .update();
        jdbc.sql("""
                insert into night_execution_tasks(
                    task_id, owner_user_id, session_id, workspace_id, client_request_id,
                    session_title, content_preview, status, slot_start, slot_end, window_end,
                    target_linux_server_id, trace_id, created_at, updated_at)
                values(:taskId, :userId, :sessionId, 'wrk_inactive_process', :requestId,
                    'night cleanup', 'night cleanup', :status, :slotStart, :slotEnd, :windowEnd,
                    'server-a', 'trace_seed', :now, :now)
                """)
                .param("taskId", "net_cleanup_" + suffix)
                .param("userId", userId)
                .param("sessionId", sessionId)
                .param("requestId", "request_cleanup_" + suffix)
                .param("status", status)
                .param("slotStart", slotStart)
                .param("slotEnd", slotStart.plusSeconds(60))
                .param("windowEnd", windowEnd)
                .param("now", NOW)
                .update();
    }

    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factory.getObject();
    }
}
