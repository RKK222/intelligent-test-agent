package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.persistence.mybatis.AnalyticsBackfillEventRow;
import com.enterprise.testagent.persistence.mybatis.AnalyticsEventOutboxMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisAnalyticsEventOutboxRepository;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import javax.sql.DataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 使用真实 PostgreSQL 锁定业务写入与脱敏运营 outbox 的同事务语义。 */
@Testcontainers(disabledWithoutDocker = true)
class AnalyticsEventOutboxPostgresqlIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static JdbcClient jdbc;
    private static TransactionTemplate transaction;
    private static MyBatisAnalyticsEventOutboxRepository outbox;
    private static AnalyticsEventOutboxMapper outboxMapper;

    @BeforeAll
    static void setUp() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        migrate(dataSource);
        jdbc = JdbcClient.create(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        outbox = outbox(dataSource);
        seedMasterData();
    }

    @Test
    void messageWriteCreatesSanitizedAttributedEventInSameTransaction() {
        transaction.executeWithoutResult(status -> jdbc.sql("""
                        insert into session_messages(
                            message_id, session_id, role, content, trace_id, created_at,
                            sender_user_id, sender_unified_auth_id
                        ) values (
                            'msg_outbox_committed', 'ses_outbox', 'USER', '绝不能进入运营库的消息正文 secret-123',
                            'trace_outbox', :now, 'usr_outbox', 'auth_outbox'
                        )
                        """).param("now", Timestamp.from(Instant.parse("2026-08-13T04:00:00Z"))).update());

        String payload = jdbc.sql("""
                        select payload_json from analytics_event_outbox
                        where event_id = 'message:msg_outbox_committed'
                        """).query(String.class).single();

        assertThat(payload)
                .contains("\"userId\": \"usr_outbox\"")
                .contains("\"organization\": \"总行\"")
                .contains("\"userMessageCount\": 1")
                .doesNotContain("消息正文", "secret-123", "content", "cost");
    }

    @Test
    void rolledBackBusinessWriteAlsoRollsBackOutboxEvent() {
        transaction.executeWithoutResult(status -> {
            jdbc.sql("""
                            insert into user_login_logs(log_id, user_id, login_at, login_result)
                            values('login_outbox_rollback', 'usr_outbox', :now, 'SUCCESS')
                            """).param("now", Timestamp.from(Instant.parse("2026-08-13T04:10:00Z"))).update();
            status.setRollbackOnly();
        });

        long count = jdbc.sql("""
                        select count(*) from analytics_event_outbox
                        where event_id = 'login:login_outbox_rollback'
                        """).query(Long.class).single();
        assertThat(count).isZero();
    }

    @Test
    void brokenOutboxFailsTheBusinessWriteInsteadOfLosingAnalyticsFact() {
        jdbc.sql("alter table analytics_event_outbox add constraint ck_test_event_source "
                        + "check (source_type != 'USER_LOGIN_LOGS')")
                .update();
        try {
            assertThatThrownBy(() -> jdbc.sql("""
                            insert into user_login_logs(log_id, user_id, login_at, login_result)
                            values('login_outbox_fail_closed', 'usr_outbox', :now, 'SUCCESS')
                            """).param("now", Timestamp.from(Instant.parse("2026-08-13T04:15:00Z"))).update())
                    .isInstanceOf(RuntimeException.class);

            long businessCount = jdbc.sql("""
                            select count(*) from user_login_logs
                            where log_id = 'login_outbox_fail_closed'
                            """).query(Long.class).single();
            assertThat(businessCount).isZero();
        } finally {
            jdbc.sql("alter table analytics_event_outbox drop constraint ck_test_event_source").update();
        }
    }

    @Test
    void publishedEventIsRemovedFromPostgresqlAfterClickHouseAcknowledgement() {
        jdbc.sql("""
                        insert into user_login_logs(log_id, user_id, login_at, login_result)
                        values('login_outbox_published', 'usr_outbox', :now, 'SUCCESS')
                        """).param("now", Timestamp.from(Instant.parse("2026-08-13T04:20:00Z"))).update();
        Long id = jdbc.sql("""
                        select id from analytics_event_outbox
                        where event_id = 'login:login_outbox_published'
                        """).query(Long.class).single();

        outbox.markPublished(List.of(id), Instant.parse("2026-08-13T04:21:00Z"));

        long count = jdbc.sql("""
                        select count(*) from analytics_event_outbox
                        where event_id = 'login:login_outbox_published'
                        """).query(Long.class).single();
        assertThat(count).isZero();
    }

    @Test
    void userDimensionEventsAreSanitizedAndAvailableForHistoricalBackfill() {
        Instant updatedAt = Instant.parse("2026-08-13T04:25:00Z");
        jdbc.sql("""
                        update users
                        set organization = '分行', department = '质量部', status = 'INACTIVE', updated_at = :updatedAt
                        where user_id = 'usr_outbox'
                        """).param("updatedAt", Timestamp.from(updatedAt)).update();

        String realtimePayload = jdbc.sql("""
                        select payload_json from analytics_event_outbox
                        where event_id = 'user-dimension:usr_outbox'
                        order by event_version desc limit 1
                        """).query(String.class).single();
        String historicalPayload = payload(
                outboxMapper.backfillUserDimensionEvents(),
                "backfill-v1:user-dimension:usr_outbox");

        assertThat(realtimePayload)
                .contains("\"eventType\": \"USER_DIMENSION\"")
                .contains("\"status\": \"INACTIVE\"")
                .contains("\"organization\": \"分行\"")
                .doesNotContain("password", "unifiedAuth", "hash");
        assertThat(historicalPayload)
                .contains("\"status\": \"INACTIVE\"")
                .contains("\"department\": \"质量部\"")
                .doesNotContain("password", "unifiedAuth", "hash");

        jdbc.sql("""
                        update users
                        set organization = '总行', department = '平台部', status = 'ACTIVE', updated_at = :updatedAt
                        where user_id = 'usr_outbox'
                        """).param("updatedAt", Timestamp.from(updatedAt.plusSeconds(1))).update();
    }

    @Test
    void genericToolTitleIsExcludedFromRealtimeAndBackfillOutbox() {
        Instant occurredAt = Instant.parse("2026-08-13T04:27:00Z");
        jdbc.sql("""
                        insert into runs(
                            run_id, session_id, workspace_id, status, trace_id, created_at, updated_at,
                            triggered_by_user_id, agent_id, model_id
                        ) values (
                            'run_tool_redaction', 'ses_outbox', 'wrk_outbox', 'RUNNING', 'trace_outbox',
                            :occurredAt, :occurredAt, 'usr_outbox', 'opencode', 'model-a'
                        )
                        """).param("occurredAt", Timestamp.from(occurredAt)).update();
        jdbc.sql("""
                        insert into run_events(
                            event_id, run_id, seq, type, trace_id, occurred_at, payload_json,
                            root_session_id, session_id, is_child_session, scope_version
                        ) values (
                            'evt_tool_redaction', 'run_tool_redaction', 1, 'tool.started', 'trace_outbox',
                            :occurredAt, '{"tool":"bash","callID":"call-bash","title":"cat /sensitive/path"}',
                            'ses_outbox', 'ses_outbox', false, 1
                        )
                        """).param("occurredAt", Timestamp.from(occurredAt.plusMillis(1))).update();

        String realtimePayload = jdbc.sql("""
                        select payload_json from analytics_event_outbox
                        where event_id = 'run-event:evt_tool_redaction'
                        """).query(String.class).single();
        String historicalPayload = payload(
                outboxMapper.backfillDetailEvents(occurredAt.minusSeconds(1), occurredAt.plusSeconds(1)),
                "backfill-v1:run-event:evt_tool_redaction");

        assertThat(realtimePayload).contains("\"toolName\": \"bash\"").doesNotContain("sensitive", "title");
        assertThat(historicalPayload).contains("\"toolName\": \"bash\"").doesNotContain("sensitive", "title");
    }

    @Test
    void historicalRunBackfillCarriesCountersTokensAndAnIndividualDurationSample() {
        Instant createdAt = Instant.parse("2026-08-13T04:30:00Z");
        Instant updatedAt = createdAt.plusSeconds(65);
        jdbc.sql("""
                        insert into runs(
                            run_id, session_id, workspace_id, status, trace_id, created_at, updated_at,
                            triggered_by_user_id, agent_id, model_id
                        ) values (
                            'run_backfill_duration', 'ses_outbox', 'wrk_outbox', 'RUNNING', 'trace_outbox',
                            :createdAt, :createdAt, 'usr_outbox', 'opencode', 'model-a'
                        )
                        """)
                .param("createdAt", Timestamp.from(createdAt))
                .param("updatedAt", Timestamp.from(updatedAt))
                .update();
        jdbc.sql("""
                        update runs
                        set status = 'SUCCEEDED', updated_at = :updatedAt,
                            tokens_input = 100, tokens_output = 40, tokens_reasoning = 10,
                            tokens_cache_read = 20, tokens_cache_write = 5
                        where run_id = 'run_backfill_duration'
                        """).param("updatedAt", Timestamp.from(updatedAt)).update();

        AnalyticsBackfillEventRow event = outboxMapper.backfillDetailEvents(
                        createdAt.minusSeconds(1), updatedAt.plusSeconds(1)).stream()
                .filter(row -> row.eventId().equals("backfill-v1:run:run_backfill_duration"))
                .findFirst()
                .orElseThrow();

        assertThat(event.payloadJson())
                .contains("\"durationMs\": 65000")
                .contains("\"runCount\": 1")
                .contains("\"succeededRunCount\": 1")
                .contains("\"validInteractionCount\": 1")
                .contains("\"tokensInput\": 100")
                .contains("\"tokensOutput\": 40")
                .contains("\"tokensReasoning\": 10")
                .contains("\"tokensCacheRead\": 20")
                .contains("\"tokensCacheWrite\": 5");
        String terminalPayload = jdbc.sql("""
                        select payload_json from analytics_event_outbox
                        where event_id = 'run-terminal:run_backfill_duration'
                        """).query(String.class).single();
        assertThat(terminalPayload)
                .contains("\"succeededRunCount\": 1")
                .contains("\"validInteractionCount\": 1")
                .contains("\"activeTerminationCount\": 0")
                .contains("\"tokensInput\": 100");
    }

    @Test
    void historicalBackfillPreservesLoginSessionAndMessageFacts() {
        Instant occurredAt = Instant.parse("2026-08-13T05:30:00Z");
        jdbc.sql("""
                        insert into user_login_logs(log_id, user_id, login_at, login_result)
                        values('login_backfill_facts', 'usr_outbox', :occurredAt, 'SUCCESS')
                        """).param("occurredAt", Timestamp.from(occurredAt)).update();
        jdbc.sql("""
                        insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                                             created_by_user_id)
                        values('ses_backfill_facts', 'wrk_outbox', 'facts', 'ACTIVE', 'trace_outbox',
                               :occurredAt, :occurredAt, 'usr_outbox')
                        """).param("occurredAt", Timestamp.from(occurredAt.plusSeconds(1))).update();
        jdbc.sql("""
                        insert into session_messages(message_id, session_id, role, content, trace_id, created_at,
                                                     sender_user_id)
                        values
                          ('msg_backfill_user_1', 'ses_backfill_facts', 'USER', 'first', 'trace_outbox', :firstAt, 'usr_outbox'),
                          ('msg_backfill_user_2', 'ses_backfill_facts', 'USER', 'second', 'trace_outbox', :secondAt, 'usr_outbox'),
                          ('msg_backfill_assistant', 'ses_backfill_facts', 'ASSISTANT', 'answer', 'trace_outbox', :thirdAt, null)
                        """)
                .param("firstAt", Timestamp.from(occurredAt.plusSeconds(2)))
                .param("secondAt", Timestamp.from(occurredAt.plusSeconds(3)))
                .param("thirdAt", Timestamp.from(occurredAt.plusSeconds(4)))
                .update();

        List<AnalyticsBackfillEventRow> events = outboxMapper.backfillDetailEvents(
                occurredAt.minusSeconds(1), occurredAt.plusSeconds(10));

        assertThat(payload(events, "backfill-v1:login:login_backfill_facts"))
                .contains("\"loginCount\": 1");
        assertThat(payload(events, "backfill-v1:session:ses_backfill_facts"))
                .contains("\"sessionCount\": 1")
                .contains("\"activeSessionCount\": 1")
                .contains("\"emptySessionCount\": 0")
                .contains("\"continuousSessionCount\": 1");
        assertThat(payload(events, "backfill-v1:message:msg_backfill_user_1"))
                .contains("\"userMessageCount\": 1")
                .contains("\"assistantMessageCount\": 0");
        assertThat(payload(events, "backfill-v1:message:msg_backfill_assistant"))
                .contains("\"userMessageCount\": 0")
                .contains("\"assistantMessageCount\": 1");
    }

    @Test
    void childSessionEventPreservesAgentIdentityWithoutTaskDescription() {
        Instant occurredAt = Instant.parse("2026-08-13T05:00:00Z");
        jdbc.sql("""
                        insert into runs(
                            run_id, session_id, workspace_id, status, trace_id, created_at, updated_at,
                            triggered_by_user_id, agent_id, model_id
                        ) values (
                            'run_child_agent', 'ses_outbox', 'wrk_outbox', 'RUNNING', 'trace_outbox',
                            :occurredAt, :occurredAt, 'usr_outbox', 'opencode', 'model-a'
                        )
                        """).param("occurredAt", Timestamp.from(occurredAt)).update();
        jdbc.sql("""
                        insert into run_events(
                            event_id, run_id, seq, type, trace_id, occurred_at, payload_json,
                            root_session_id, session_id, parent_session_id, is_child_session,
                            scope_version, task_call_id
                        ) values (
                            'evt_child_agent', 'run_child_agent', 1, 'session.child.discovered', 'trace_outbox',
                            :occurredAt, '{"agent":"review","agentName":"review","title":"检查接口实现"}',
                            'ses_outbox', 'child-session', 'ses_outbox', true, 2, 'call-task'
                        )
                        """).param("occurredAt", Timestamp.from(occurredAt.plusMillis(1))).update();

        String payload = jdbc.sql("""
                        select payload_json from analytics_event_outbox
                        where event_id = 'run-event:evt_child_agent'
                        """).query(String.class).single();

        assertThat(payload)
                .contains("\"eventType\": \"SESSION_CHILD_DISCOVERED\"")
                .contains("\"parentSessionId\": \"ses_outbox\"")
                .contains("\"taskCallId\": \"call-task\"")
                .contains("\"agentName\": \"review\"")
                .doesNotContain("检查接口实现");
    }

    private static void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/migration-postgresql")
                .load()
                .migrate();
    }

    private static String payload(List<AnalyticsBackfillEventRow> events, String eventId) {
        return events.stream()
                .filter(row -> row.eventId().equals(eventId))
                .map(AnalyticsBackfillEventRow::payloadJson)
                .findFirst()
                .orElseThrow();
    }

    private static void seedMasterData() {
        Instant now = Instant.parse("2026-08-13T03:00:00Z");
        jdbc.sql("""
                        insert into users(user_id, unified_auth_id, username, password_hash,
                                          organization, rd_department, department, status, created_at, updated_at)
                        values('usr_outbox', 'auth_outbox', '张三', 'hash', '总行', '研发一部', '平台部',
                               'ACTIVE', :now, :now)
                        """).param("now", Timestamp.from(now)).update();
        jdbc.sql("""
                        insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                        values('wrk_outbox', 'outbox', '/tmp/outbox', 'ACTIVE', 'trace_outbox', :now, :now)
                        """).param("now", Timestamp.from(now)).update();
        jdbc.sql("""
                        insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                                             created_by_user_id)
                        values('ses_outbox', 'wrk_outbox', 'outbox', 'ACTIVE', 'trace_outbox', :now, :now, 'usr_outbox')
                        """).param("now", Timestamp.from(now)).update();
    }

    private static MyBatisAnalyticsEventOutboxRepository outbox(DataSource dataSource) {
        try {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:mybatis/AnalyticsEventOutboxMapper.xml"));
            SqlSessionTemplate template = new SqlSessionTemplate(factory.getObject());
            outboxMapper = template.getMapper(AnalyticsEventOutboxMapper.class);
            return new MyBatisAnalyticsEventOutboxRepository(
                    outboxMapper);
        } catch (Exception exception) {
            throw new IllegalStateException("初始化运营 outbox mapper 失败", exception);
        }
    }
}
