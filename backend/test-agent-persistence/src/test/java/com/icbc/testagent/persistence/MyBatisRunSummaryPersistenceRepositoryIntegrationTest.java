package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.run.RunConversationSummary;
import com.icbc.testagent.domain.run.RunDiffCounts;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunPersistenceAnchor;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.run.RunSummaryPersistencePort;
import com.icbc.testagent.domain.run.RunSummaryStatus;
import com.icbc.testagent.domain.run.RunTerminalProjection;
import com.icbc.testagent.domain.run.RunTerminalProjectionResult;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.run.TokenUsage;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.persistence.mybatis.MyBatisRunSummaryPersistenceRepository;
import com.icbc.testagent.persistence.mybatis.RunSummaryMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 验证无原文 Run 锚点和终态双摘要只通过 MyBatis XML 完成，并受 SQL 次数预算约束。
 */
class MyBatisRunSummaryPersistenceRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_run_summary");
    private static final SessionId SESSION_ID = new SessionId("ses_run_summary");
    private static final UserId USER_ID = new UserId("usr_run_summary");

    private SingleConnectionDataSource physicalDataSource;
    private CountingDataSource countingDataSource;
    private JdbcClient jdbcClient;
    private RunSummaryPersistencePort repository;

    @BeforeEach
    void setUp() throws Exception {
        physicalDataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_run_summary_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(physicalDataSource).locations("classpath:db/migration").load().migrate();
        jdbcClient = JdbcClient.create(physicalDataSource);
        seedWorkspaceSessionAndUser();

        countingDataSource = new CountingDataSource(physicalDataSource);
        SqlSessionFactory factory = sqlSessionFactory(countingDataSource);
        RunSummaryMapper mapper = new SqlSessionTemplate(factory).getMapper(RunSummaryMapper.class);
        repository = new MyBatisRunSummaryPersistenceRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        physicalDataSource.destroy();
    }

    @Test
    void insertAnchorUsesOneStatementAndIdempotentRetryReturnsExistingRun() {
        RunPersistenceAnchor anchor = anchor("run_summary_anchor_one", "request-anchor-one");

        countingDataSource.reset();
        boolean inserted = repository.insertAnchor(anchor);

        assertThat(inserted).isTrue();
        assertThat(countingDataSource.statementCount()).isEqualTo(1);

        RunPersistenceAnchor conflictingRetry = anchor("run_summary_anchor_other", "request-anchor-one");
        assertThat(repository.insertAnchor(conflictingRetry)).isFalse();
        assertThat(repository.findBySessionAndClientRequestId(SESSION_ID, "request-anchor-one"))
                .get()
                .extracting(RunPersistenceAnchor::runId)
                .isEqualTo(anchor.runId());
        assertThat(jdbcClient.sql("select count(*) from runs where client_request_id = 'request-anchor-one'")
                .query(Long.class)
                .single()).isEqualTo(1L);
    }

    @Test
    void findByClientRequestIdReturnsRoutingAndStableAssistantMessageSnapshot() {
        RunPersistenceAnchor anchor = anchor("run_summary_anchor_find", "request-anchor-find");
        repository.insertAnchor(anchor);

        assertThat(repository.findBySessionAndClientRequestId(SESSION_ID, "request-anchor-find"))
                .contains(anchor);
    }

    @Test
    void initialAgentBindingUsesWritesOnlyAndKeepsLegacySessionColumnsCompatible() {
        jdbcClient.sql("""
                        insert into execution_nodes(
                            execution_node_id, base_url, status, running_runs, max_runs, weight,
                            last_heartbeat_at, capabilities_json, trace_id, created_at, updated_at
                        ) values(
                            'node_summary_a', 'http://127.0.0.1:4096', 'ONLINE', 0, 8, 1,
                            :now, '[]', 'trace_run_summary', :now, :now
                        )
                        """)
                .param("now", NOW)
                .update();
        AgentSessionBinding binding = new AgentSessionBinding(
                SESSION_ID,
                "opencode",
                "remote-session-first",
                new ExecutionNodeId("node_summary_a"),
                NOW,
                NOW.plusSeconds(1),
                "trace_run_summary");

        countingDataSource.reset();
        repository.persistInitialAgentBinding(binding);

        assertThat(countingDataSource.statementCount()).isEqualTo(2);
        assertThat(jdbcClient.sql("""
                        select remote_session_id from agent_session_bindings
                        where session_id = :sessionId and agent_id = 'opencode'
                        """)
                .param("sessionId", SESSION_ID.value())
                .query(String.class)
                .single()).isEqualTo("remote-session-first");
        assertThat(jdbcClient.sql("""
                        select opencode_session_id from sessions where session_id = :sessionId
                        """)
                .param("sessionId", SESSION_ID.value())
                .query(String.class)
                .single()).isEqualTo("remote-session-first");
    }

    @Test
    void terminalProjectionUsesThreeStatementsAndWritesAtMostTwoSummaryRowsWithoutParts() {
        RunPersistenceAnchor anchor = anchor("run_summary_terminal_ok", "request-terminal-ok");
        repository.insertAnchor(anchor);
        RunTerminalProjection projection = projection(anchor, 1L, "用户概要", "助手概要");

        countingDataSource.reset();
        RunTerminalProjectionResult result = repository.persistTerminal(projection);
        int terminalStatementCount = countingDataSource.statementCount();

        assertThat(result).isEqualTo(RunTerminalProjectionResult.APPLIED);
        assertThat(terminalStatementCount).isEqualTo(3);
        assertThat(jdbcClient.sql("""
                        select status, status_version, terminal_source, terminal_reason_code,
                               safe_error_message, remote_stop_confirmed, last_event_seq,
                               diff_proposed_count, diff_accepted_count, diff_rejected_count,
                               last_remote_message_id, last_remote_part_id
                        from runs where run_id = :runId
                        """)
                .param("runId", anchor.runId().value())
                .query((rs, rowNum) -> Map.ofEntries(
                        Map.entry("status", rs.getString("status")),
                        Map.entry("statusVersion", rs.getLong("status_version")),
                        Map.entry("source", rs.getString("terminal_source")),
                        Map.entry("reason", rs.getString("terminal_reason_code")),
                        Map.entry("error", rs.getString("safe_error_message")),
                        Map.entry("stop", rs.getBoolean("remote_stop_confirmed")),
                        Map.entry("seq", rs.getLong("last_event_seq")),
                        Map.entry("proposed", rs.getInt("diff_proposed_count")),
                        Map.entry("accepted", rs.getInt("diff_accepted_count")),
                        Map.entry("rejected", rs.getInt("diff_rejected_count")),
                        Map.entry("message", rs.getString("last_remote_message_id")),
                        Map.entry("part", rs.getString("last_remote_part_id"))))
                .single())
                .containsEntry("status", "SUCCEEDED")
                .containsEntry("statusVersion", 2L)
                .containsEntry("seq", 42L)
                .containsEntry("proposed", 3)
                .containsEntry("accepted", 2)
                .containsEntry("rejected", 1);

        assertThat(jdbcClient.sql("""
                        select message_id, role, content, parts_json, content_kind,
                               summary_key, summary_version, summary_status
                        from session_messages where run_id = :runId order by role desc
                        """)
                .param("runId", anchor.runId().value())
                .query((rs, rowNum) -> Map.of(
                        "messageId", rs.getString("message_id"),
                        "role", rs.getString("role"),
                        "content", rs.getString("content"),
                        "contentKind", rs.getString("content_kind"),
                        "summaryKey", rs.getString("summary_key"),
                        "summaryVersion", rs.getInt("summary_version"),
                        "summaryStatus", rs.getString("summary_status"),
                        "partsNull", rs.getString("parts_json") == null))
                .list()).hasSize(2).allSatisfy(row -> {
                    assertThat(row).containsEntry("contentKind", "SUMMARY");
                    assertThat(row).containsEntry("partsNull", true);
                });
        assertThat(jdbcClient.sql("select updated_at from sessions where session_id = :sessionId")
                .param("sessionId", SESSION_ID.value())
                .query(Instant.class)
                .single()).isEqualTo(NOW.plusSeconds(20));
    }

    @Test
    void staleStatusVersionDoesNotInsertSummariesOrTouchSession() {
        RunPersistenceAnchor anchor = anchor("run_summary_terminal_cas", "request-terminal-cas");
        repository.insertAnchor(anchor);
        assertThat(repository.persistTerminal(projection(anchor, 1L, "第一版用户概要", "第一版助手概要")))
                .isEqualTo(RunTerminalProjectionResult.APPLIED);
        Instant sessionUpdatedAt = jdbcClient.sql("select updated_at from sessions where session_id = :sessionId")
                .param("sessionId", SESSION_ID.value())
                .query(Instant.class)
                .single();

        RunTerminalProjection stale = projection(anchor, 1L, "不应写入", "不应写入");
        countingDataSource.reset();
        assertThat(repository.persistTerminal(stale)).isEqualTo(RunTerminalProjectionResult.VERSION_CONFLICT);
        assertThat(countingDataSource.statementCount()).isEqualTo(1);
        assertThat(jdbcClient.sql("select updated_at from sessions where session_id = :sessionId")
                .param("sessionId", SESSION_ID.value())
                .query(Instant.class)
                .single()).isEqualTo(sessionUpdatedAt);
        assertThat(jdbcClient.sql("select content from session_messages where run_id = :runId order by role")
                .param("runId", anchor.runId().value())
                .query(String.class)
                .list()).doesNotContain("不应写入");
    }

    @Test
    void allowsExactlyOneLaterRootTerminalCorrectionWithHigherEventSequence() {
        RunPersistenceAnchor anchor = anchor("run_summary_late_correction", "request-late-correction");
        repository.insertAnchor(anchor);
        assertThat(repository.persistTerminal(projection(
                        anchor, 1L, 42L, "第一版用户概要", "第一版助手概要")))
                .isEqualTo(RunTerminalProjectionResult.APPLIED);

        countingDataSource.reset();
        assertThat(repository.persistTerminal(projection(
                        anchor, 1L, 43L, "纠正后用户概要", "纠正后助手概要")))
                .isEqualTo(RunTerminalProjectionResult.APPLIED);
        assertThat(countingDataSource.statementCount()).isEqualTo(3);
        assertThat(jdbcClient.sql("select status_version from runs where run_id = :runId")
                .param("runId", anchor.runId().value())
                .query(Long.class)
                .single()).isEqualTo(3L);
        assertThat(jdbcClient.sql("select content from session_messages where run_id = :runId order by role")
                .param("runId", anchor.runId().value())
                .query(String.class)
                .list()).containsExactlyInAnyOrder("纠正后用户概要", "纠正后助手概要");

        countingDataSource.reset();
        assertThat(repository.persistTerminal(projection(
                        anchor, 1L, 44L, "第三版不应写入", "第三版不应写入")))
                .isEqualTo(RunTerminalProjectionResult.VERSION_CONFLICT);
        assertThat(countingDataSource.statementCount()).isEqualTo(1);
    }

    @Test
    void terminalProjectionRejectsMoreThanTwoSummariesBeforeSql() {
        RunPersistenceAnchor anchor = anchor("run_summary_too_many", "request-too-many");
        RunTerminalProjection valid = projection(anchor, 1L, "用户概要", "助手概要");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RunTerminalProjection(
                        valid.runId(),
                        valid.sessionId(),
                        valid.status(),
                        valid.expectedStatusVersion(),
                        valid.terminalSource(),
                        valid.terminalReasonCode(),
                        valid.safeErrorMessage(),
                        valid.remoteStopConfirmed(),
                        valid.lastEventSeq(),
                        valid.detailsExpiresAt(),
                        valid.rootRemoteSessionId(),
                        valid.diffCounts(),
                        valid.lastRemoteMessageId(),
                        valid.lastRemotePartId(),
                        valid.tokenUsage(),
                        valid.costUsd(),
                        valid.traceId(),
                        valid.updatedAt(),
                        valid.agentId(),
                        valid.sourceType(),
                        valid.sourceRefId(),
                        valid.senderUserId(),
                        List.of(valid.summaries().get(0), valid.summaries().get(1), valid.summaries().get(0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most two summaries");
    }

    private RunPersistenceAnchor anchor(String runId, String clientRequestId) {
        return new RunPersistenceAnchor(
                new RunId(runId),
                SESSION_ID,
                WORKSPACE_ID,
                RunStatus.RUNNING,
                RunStorageMode.REDIS_SUMMARY,
                1L,
                clientRequestId,
                "server-summary-a",
                "node-summary-a",
                "opc_process-summary-a",
                "remote-session-summary-a",
                "dispatch-summary-a",
                new SessionMessageId("msg_assistant_" + runId.substring(4)),
                "trace_run_summary",
                NOW,
                NOW,
                NOW.plusSeconds(86_400),
                ConversationSourceType.MANUAL,
                null,
                USER_ID,
                "opencode",
                "provider/model");
    }

    private RunTerminalProjection projection(
            RunPersistenceAnchor anchor,
            long expectedVersion,
            String userContent,
            String assistantContent) {
        return projection(anchor, expectedVersion, 42L, userContent, assistantContent);
    }

    private RunTerminalProjection projection(
            RunPersistenceAnchor anchor,
            long expectedVersion,
            long lastEventSeq,
            String userContent,
            String assistantContent) {
        return new RunTerminalProjection(
                anchor.runId(),
                anchor.sessionId(),
                RunStatus.SUCCEEDED,
                expectedVersion,
                "REMOTE_ROOT",
                "COMPLETED",
                "可安全展示的终态信息",
                true,
                lastEventSeq,
                NOW.plusSeconds(86_400),
                "remote-session-summary-a",
                new RunDiffCounts(3, 2, 1),
                "remote-message-final",
                "remote-part-final",
                new TokenUsage(10L, 20L, 3L, 4L, 5L),
                new BigDecimal("0.25000000"),
                anchor.traceId(),
                NOW.plusSeconds(20),
                anchor.agentId(),
                anchor.sourceType(),
                anchor.sourceRefId(),
                anchor.triggeredByUserId(),
                List.of(
                        new RunConversationSummary(
                                new SessionMessageId("msg_user_" + anchor.runId().value().substring(4)),
                                SessionMessageRole.USER,
                                userContent,
                                anchor.runId().value() + ":USER",
                                1,
                                RunSummaryStatus.COMPLETE,
                                NOW,
                                null),
                        new RunConversationSummary(
                                anchor.assistantSummaryMessageId(),
                                SessionMessageRole.ASSISTANT,
                                assistantContent,
                                anchor.runId().value() + ":ASSISTANT",
                                1,
                                RunSummaryStatus.COMPLETE,
                                NOW.plusSeconds(1),
                                "remote-message-final")));
    }

    private void seedWorkspaceSessionAndUser() {
        jdbcClient.sql("""
                        insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                        values(:workspaceId, 'summary workspace', '/tmp/summary', 'ACTIVE', 'trace_run_summary', :now, :now)
                        """)
                .param("workspaceId", WORKSPACE_ID.value())
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                        insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                        values(:userId, 'u_run_summary', 'run-summary-user', 'hash', 'ACTIVE', :now, :now)
                        """)
                .param("userId", USER_ID.value())
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                        insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                                             created_by_user_id)
                        values(:sessionId, :workspaceId, 'summary session', 'ACTIVE', 'trace_run_summary', :now, :now,
                               :userId)
                        """)
                .param("sessionId", SESSION_ID.value())
                .param("workspaceId", WORKSPACE_ID.value())
                .param("userId", USER_ID.value())
                .param("now", NOW)
                .update();
    }

    private SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }

    /** 只统计业务调用创建的 PreparedStatement，Flyway 和断言查询不经过该包装。 */
    private static final class CountingDataSource extends AbstractDataSource {
        private final DataSource delegate;
        private final AtomicInteger statements = new AtomicInteger();

        private CountingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public java.sql.Connection getConnection() throws java.sql.SQLException {
            return counted(delegate.getConnection());
        }

        @Override
        public java.sql.Connection getConnection(String username, String password) throws java.sql.SQLException {
            return counted(delegate.getConnection(username, password));
        }

        private java.sql.Connection counted(java.sql.Connection connection) {
            return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                    java.sql.Connection.class.getClassLoader(),
                    new Class<?>[] {java.sql.Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().startsWith("prepareStatement")) {
                            statements.incrementAndGet();
                        }
                        try {
                            return method.invoke(connection, args);
                        } catch (java.lang.reflect.InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        }

        private void reset() {
            statements.set(0);
        }

        private int statementCount() {
            return statements.get();
        }
    }
}
