package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.run.TokenUsage;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.persistence.mybatis.MyBatisRunRepository;
import com.icbc.testagent.persistence.mybatis.RunMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 验证 Run 生产持久化使用 MyBatis XML，并支持终态竞态所需的条件状态写入。
 */
class MyBatisRunRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-04T12:00:00Z");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_run1234567890abcdef");
    private static final SessionId SESSION_ID = new SessionId("ses_run1234567890abcdef");
    private static final UserId USER_ID = new UserId("usr_run1234567890abcdef");

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbcClient;
    private RunRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_run_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcClient = JdbcClient.create(dataSource);
        seedWorkspaceSessionAndUser();

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory();
        RunMapper mapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(RunMapper.class);
        repository = new MyBatisRunRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void saveAndFindRoundTripsRunFields() {
        Run run = run("run_mybatis_roundtrip123456", RunStatus.RUNNING, NOW.plusSeconds(1))
                .withSource(ConversationSourceType.MANUAL, null, USER_ID)
                .withRuntimeSelection("OpenCode", "icbc-openai/deepseek")
                .withUsage(new TokenUsage(10L, 20L, 3L, 4L, 5L), new BigDecimal("0.25000000"));

        repository.save(run);

        assertThat(repository.findById(run.runId())).get().satisfies(saved -> {
            assertThat(saved.status()).isEqualTo(RunStatus.RUNNING);
            assertThat(saved.tokenUsage()).isEqualTo(new TokenUsage(10L, 20L, 3L, 4L, 5L));
            assertThat(saved.costUsd()).isEqualByComparingTo("0.25000000");
            assertThat(saved.sourceType()).isEqualTo(ConversationSourceType.MANUAL);
            assertThat(saved.triggeredByUserId()).isEqualTo(USER_ID);
            assertThat(saved.agentId()).isEqualTo("opencode");
            assertThat(saved.modelId()).isEqualTo("icbc-openai/deepseek");
        });
    }

    @Test
    void saveIfStatusDoesNotOverwriteTerminalRunWhenExpectedStatusNoLongerMatches() {
        Run running = run("run_mybatis_cas123456789", RunStatus.RUNNING, NOW.plusSeconds(1));
        repository.save(running);
        Run succeeded = running.succeed(NOW.plusSeconds(2));
        assertThat(repository.saveIfStatus(succeeded, RunStatus.RUNNING).status()).isEqualTo(RunStatus.SUCCEEDED);

        Run staleFailed = running.fail(NOW.plusSeconds(3));
        Run result = repository.saveIfStatus(staleFailed, RunStatus.RUNNING);

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(repository.findById(running.runId())).get().extracting(Run::status).isEqualTo(RunStatus.SUCCEEDED);
    }

    @Test
    void findLatestActiveBySessionIdIgnoresTerminalRuns() {
        Run oldPending = run("run_mybatis_pending123456", RunStatus.PENDING, NOW.plusSeconds(1));
        Run terminal = run("run_mybatis_done123456789", RunStatus.SUCCEEDED, NOW.plusSeconds(3));
        Run latestRunning = run("run_mybatis_running123456", RunStatus.RUNNING, NOW.plusSeconds(2));
        repository.save(oldPending);
        repository.save(terminal);
        repository.save(latestRunning);

        assertThat(repository.findLatestActiveBySessionId(SESSION_ID)).contains(latestRunning);
    }

    @Test
    void findByIdsReturnsOnlyExistingRunsInRequestedBatch() {
        Run first = run("run_mybatis_batch_first01", RunStatus.SUCCEEDED, NOW.plusSeconds(1));
        Run second = run("run_mybatis_batch_second1", RunStatus.FAILED, NOW.plusSeconds(2));
        repository.save(first);
        repository.save(second);

        assertThat(repository.findByIds(List.of(
                        first.runId(),
                        new RunId("run_mybatis_batch_missing"),
                        second.runId())))
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void findStaleActiveRunsReturnsOnlyOldLegacyNonTerminalRunsInStableOrder() {
        Run stalePending = run("run_stale_pending123456", RunStatus.PENDING, NOW.plusSeconds(1));
        Run staleRunning = run("run_stale_running123456", RunStatus.RUNNING, NOW.plusSeconds(2));
        Run staleCancelling = run("run_stale_cancel1234567", RunStatus.CANCELLING, NOW.plusSeconds(3));
        Run terminal = run("run_stale_done123456789", RunStatus.SUCCEEDED, NOW.plusSeconds(1));
        Run freshRunning = run("run_stale_fresh12345678", RunStatus.RUNNING, NOW.plusSeconds(30));
        repository.save(staleRunning);
        repository.save(freshRunning);
        repository.save(staleCancelling);
        repository.save(terminal);
        repository.save(stalePending);
        jdbcClient.sql("update runs set storage_mode = 'REDIS_SUMMARY' where run_id = :runId")
                .param("runId", staleCancelling.runId().value())
                .update();

        List<Run> firstPage = repository.findStaleActiveRuns(NOW.plusSeconds(10), 2);
        List<Run> fullPage = repository.findStaleActiveRuns(NOW.plusSeconds(10), 10);

        assertThat(firstPage).containsExactly(stalePending, staleRunning);
        assertThat(fullPage).containsExactly(stalePending, staleRunning);
    }

    @Test
    void findStaleActiveSideQuestionRunsFiltersSourceStatusAndCutoff() {
        Run stalePending = run("run_side_stale_pending01", RunStatus.PENDING, NOW.plusSeconds(1))
                .withSource(ConversationSourceType.SIDE_QUESTION, SESSION_ID.value(), USER_ID);
        Run staleRunning = run("run_side_stale_running01", RunStatus.RUNNING, NOW.plusSeconds(2))
                .withSource(ConversationSourceType.SIDE_QUESTION, SESSION_ID.value(), USER_ID);
        Run terminal = run("run_side_stale_done000001", RunStatus.SUCCEEDED, NOW.plusSeconds(1))
                .withSource(ConversationSourceType.SIDE_QUESTION, SESSION_ID.value(), USER_ID);
        Run fresh = run("run_side_fresh_running01", RunStatus.RUNNING, NOW.plusSeconds(30))
                .withSource(ConversationSourceType.SIDE_QUESTION, SESSION_ID.value(), USER_ID);
        Run manual = run("run_manual_stale_running1", RunStatus.RUNNING, NOW.plusSeconds(1))
                .withSource(ConversationSourceType.MANUAL, null, USER_ID);
        repository.save(staleRunning);
        repository.save(terminal);
        repository.save(manual);
        repository.save(fresh);
        repository.save(stalePending);

        assertThat(repository.findStaleActiveSideQuestionRuns(NOW.plusSeconds(10), 10))
                .containsExactly(stalePending, staleRunning);
    }

    private Run run(String runId, RunStatus status, Instant updatedAt) {
        return new Run(
                new RunId(runId),
                SESSION_ID,
                WORKSPACE_ID,
                status,
                NOW,
                updatedAt,
                "trace_run1234567890abcdef");
    }

    private void seedWorkspaceSessionAndUser() {
        jdbcClient.sql("""
                insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                values(:workspaceId, 'run workspace', '/tmp/run', 'ACTIVE', 'trace_run1234567890abcdef', :now, :now)
                """)
                .param("workspaceId", WORKSPACE_ID.value())
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at)
                values(:sessionId, :workspaceId, 'run session', 'ACTIVE', 'trace_run1234567890abcdef', :now, :now)
                """)
                .param("sessionId", SESSION_ID.value())
                .param("workspaceId", WORKSPACE_ID.value())
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                values(:userId, 'u_run1234567890abcdef', 'run-user', 'hash', 'ACTIVE', :now, :now)
                """)
                .param("userId", USER_ID.value())
                .param("now", NOW)
                .update();
    }

    /**
     * 直接构造 MyBatis mapper，确保 Run SQL 只依赖 XML 映射即可运行。
     */
    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
