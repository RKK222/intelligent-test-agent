package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.run.RunDetailsLocator;
import com.icbc.testagent.domain.run.RunDiffAction;
import com.icbc.testagent.domain.run.RunDiffCounts;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.run.RunTerminalProjection;
import com.icbc.testagent.domain.run.RunTerminalProjectionResult;
import com.icbc.testagent.domain.run.TokenUsage;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.persistence.mybatis.MyBatisRunSummaryPersistenceRepository;
import com.icbc.testagent.persistence.mybatis.RunSummaryMapper;
import java.time.Instant;
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

/** 验证 Diff 低频动作只读取 runs 中的非原文定位字段。 */
class MyBatisRunDetailsLocatorIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbcClient;
    private MyBatisRunSummaryPersistenceRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_run_locator_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcClient = JdbcClient.create(dataSource);
        jdbcClient.sql("""
                        insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                        values('wrk_run_locator', 'locator', '/tmp/locator', 'ACTIVE', 'trace_locator', :now, :now)
                        """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                        insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at)
                        values('ses_run_locator', 'wrk_run_locator', 'locator', 'ACTIVE', 'trace_locator', :now, :now)
                        """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                        insert into runs(run_id, session_id, workspace_id, status, storage_mode, status_version,
                                         client_request_id, producer_linux_server_id, execution_node_id_snapshot,
                                         opencode_process_id_snapshot, root_remote_session_id, dispatch_message_id,
                                         assistant_summary_message_id, last_remote_message_id, last_remote_part_id,
                                         details_expires_at, trace_id, created_at, updated_at)
                        values('run_details_locator', 'ses_run_locator', 'wrk_run_locator', 'SUCCEEDED',
                               'REDIS_SUMMARY', 1, 'request-locator', 'server-a', 'node-locator', 'process-locator',
                               'remote-session-locator', 'dispatch-locator', 'msg_assistant_locator',
                               'remote-message-locator', 'remote-part-locator', :expiresAt,
                               'trace_locator', :now, :now)
                        """)
                .param("now", NOW)
                .param("expiresAt", NOW.plusSeconds(86_400))
                .update();
        repository = new MyBatisRunSummaryPersistenceRepository(new SqlSessionTemplate(sqlSessionFactory())
                .getMapper(RunSummaryMapper.class));
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void readsOnlyStableRemoteLocatorFromRunAnchor() {
        assertThat(repository.findDetailsLocator(new RunId("run_details_locator")))
                .contains(new RunDetailsLocator(
                        new RunId("run_details_locator"),
                        RunStorageMode.REDIS_SUMMARY,
                        "remote-session-locator",
                        "node-locator",
                        "remote-message-locator",
                        "remote-part-locator",
                        NOW.plusSeconds(86_400)));
    }

    @Test
    void legacyRunDoesNotProduceSummaryDetailsLocator() {
        jdbcClient.sql("""
                        insert into runs(run_id, session_id, workspace_id, status, storage_mode,
                                         trace_id, created_at, updated_at)
                        values('run_details_legacy', 'ses_run_locator', 'wrk_run_locator', 'SUCCEEDED',
                               'LEGACY_FULL', 'trace_locator', :now, :now)
                        """)
                .param("now", NOW)
                .update();

        assertThat(repository.findDetailsLocator(new RunId("run_details_legacy"))).isEmpty();
    }

    @Test
    void recordsRedisSummaryDiffActionsInRunAnchorWithoutRunEvents() {
        assertThat(repository.recordDiffAction(new RunId("run_details_locator"), RunDiffAction.ACCEPTED)).isTrue();
        assertThat(repository.recordDiffAction(new RunId("run_details_locator"), RunDiffAction.REJECTED)).isTrue();

        assertThat(jdbcClient.sql("""
                        select diff_accepted_count, diff_rejected_count
                        from runs where run_id = 'run_details_locator'
                        """)
                .query((rs, rowNum) -> java.util.Map.of(
                        "accepted", rs.getInt("diff_accepted_count"),
                        "rejected", rs.getInt("diff_rejected_count")))
                .single())
                .containsEntry("accepted", 1)
                .containsEntry("rejected", 1);
        assertThat(jdbcClient.sql("select count(*) from run_events where run_id = 'run_details_locator'")
                .query(Long.class)
                .single()).isZero();
    }

    @Test
    void terminalProjectionDoesNotOverwriteConcurrentDiffActionCounter() {
        jdbcClient.sql("""
                        update runs set status = 'RUNNING', status_version = 0,
                                        diff_accepted_count = 0, diff_rejected_count = 0
                        where run_id = 'run_details_locator'
                        """)
                .update();
        assertThat(repository.recordDiffAction(new RunId("run_details_locator"), RunDiffAction.ACCEPTED)).isTrue();

        RunTerminalProjectionResult result = repository.persistTerminal(new RunTerminalProjection(
                new RunId("run_details_locator"),
                new SessionId("ses_run_locator"),
                RunStatus.SUCCEEDED,
                0L,
                "REMOTE_ROOT",
                "COMPLETED",
                null,
                false,
                4L,
                NOW.plusSeconds(86_400),
                "remote-session-locator",
                RunDiffCounts.empty(),
                "remote-message-locator",
                "remote-part-locator",
                TokenUsage.empty(),
                null,
                "trace_locator",
                NOW.plusSeconds(1),
                "opencode",
                ConversationSourceType.MANUAL,
                null,
                null,
                java.util.List.of()));

        assertThat(result).isEqualTo(RunTerminalProjectionResult.APPLIED);
        assertThat(jdbcClient.sql("select diff_accepted_count from runs where run_id = 'run_details_locator'")
                .query(Integer.class)
                .single()).isEqualTo(1);
    }

    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
