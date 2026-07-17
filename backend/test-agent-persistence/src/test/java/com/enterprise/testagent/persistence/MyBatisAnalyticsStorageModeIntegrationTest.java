package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.persistence.mybatis.AnalyticsActivityRow;
import com.enterprise.testagent.persistence.mybatis.AnalyticsMapper;
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
 * 验证运营事实扫描按 Run 创建时固定的 storageMode 双读，避免 Redis 摘要计数遗漏或重复。
 */
class MyBatisAnalyticsStorageModeIntegrationTest {

    private static final Instant START = Instant.parse("2026-07-10T00:00:00Z");
    private static final Instant END = START.plusSeconds(3_600);

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbcClient;
    private AnalyticsMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_analytics_storage_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcClient = JdbcClient.create(dataSource);
        mapper = new SqlSessionTemplate(sqlSessionFactory()).getMapper(AnalyticsMapper.class);
        seedControlPlane();
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void rawFactsReadLegacyEventsAndRedisSummaryRunCountersWithoutDoubleCounting() {
        seedLegacyRun();
        seedRedisSummaryRun();

        List<AnalyticsActivityRow> facts = mapper.loadRawActivityFacts(START, END);

        assertThat(facts.stream().mapToLong(AnalyticsActivityRow::userMessageCount).sum()).isEqualTo(1L);
        assertThat(facts.stream().mapToLong(AnalyticsActivityRow::assistantMessageCount).sum()).isEqualTo(1L);
        assertThat(facts.stream().mapToLong(AnalyticsActivityRow::diffProposedCount).sum()).isEqualTo(4L);
        assertThat(facts.stream().mapToLong(AnalyticsActivityRow::diffAcceptedCount).sum()).isEqualTo(3L);
        assertThat(facts.stream().mapToLong(AnalyticsActivityRow::diffRejectedCount).sum()).isEqualTo(1L);
    }

    private void seedControlPlane() {
        jdbcClient.sql("""
                        insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                        values('wrk_analytics_storage', 'analytics', '/tmp/analytics', 'ACTIVE', 'trace_analytics', :now, :now)
                        """)
                .param("now", START)
                .update();
        jdbcClient.sql("""
                        insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                        values('usr_analytics_storage', 'analytics-storage', 'analytics-storage', 'hash', 'ACTIVE', :now, :now)
                        """)
                .param("now", START)
                .update();
        jdbcClient.sql("""
                        insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                                             created_by_user_id)
                        values('ses_analytics_storage', 'wrk_analytics_storage', 'analytics', 'ACTIVE',
                               'trace_analytics', :now, :now, 'usr_analytics_storage')
                        """)
                .param("now", START)
                .update();
    }

    private void seedLegacyRun() {
        jdbcClient.sql("""
                        insert into runs(run_id, session_id, workspace_id, status, storage_mode, trace_id,
                                         created_at, updated_at, triggered_by_user_id, agent_id)
                        values('run_analytics_legacy', 'ses_analytics_storage', 'wrk_analytics_storage', 'SUCCEEDED',
                               'LEGACY_FULL', 'trace_analytics', :createdAt, :updatedAt,
                               'usr_analytics_storage', 'opencode')
                        """)
                .param("createdAt", START.plusSeconds(10))
                .param("updatedAt", START.plusSeconds(20))
                .update();
        insertDiffEvent("evt_analytics_legacy_p", "run_analytics_legacy", 1, "diff.proposed", START.plusSeconds(11));
        insertDiffEvent("evt_analytics_legacy_a", "run_analytics_legacy", 2, "diff.accepted", START.plusSeconds(12));
    }

    private void seedRedisSummaryRun() {
        jdbcClient.sql("""
                        insert into runs(run_id, session_id, workspace_id, status, storage_mode, status_version,
                                         client_request_id, producer_linux_server_id, execution_node_id_snapshot,
                                         opencode_process_id_snapshot, dispatch_message_id, assistant_summary_message_id,
                                         diff_proposed_count, diff_accepted_count, diff_rejected_count,
                                         trace_id, created_at, updated_at, triggered_by_user_id, agent_id)
                        values('run_analytics_summary', 'ses_analytics_storage', 'wrk_analytics_storage', 'SUCCEEDED',
                               'REDIS_SUMMARY', 1, 'request-analytics-summary', 'server-a', 'node-a', 'process-a',
                               'dispatch-a', 'msg_analytics_summary_assistant', 3, 2, 1,
                               'trace_analytics', :createdAt, :updatedAt, 'usr_analytics_storage', 'opencode')
                        """)
                .param("createdAt", START.plusSeconds(30))
                .param("updatedAt", START.plusSeconds(40))
                .update();
        insertSummary("msg_analytics_summary_user", "USER", START.plusSeconds(30), "run_analytics_summary:USER");
        insertSummary("msg_analytics_summary_assistant", "ASSISTANT", START.plusSeconds(40), "run_analytics_summary:ASSISTANT");
        insertRawShadow("msg_analytics_shadow_user", "USER", START.plusSeconds(31));
        insertRawShadow("msg_analytics_shadow_assistant", "ASSISTANT", START.plusSeconds(39));

        // 模拟灰度期间意外残留的 shadow 事件；storageMode 分流必须防止其与 Run 计数重复。
        insertDiffEvent("evt_analytics_summary_shadow", "run_analytics_summary", 1, "diff.proposed", START.plusSeconds(41));
    }

    private void insertSummary(String messageId, String role, Instant createdAt, String summaryKey) {
        jdbcClient.sql("""
                        insert into session_messages(message_id, session_id, role, content, trace_id, created_at,
                                                     run_id, agent_id, content_kind, summary_key,
                                                     summary_version, summary_status)
                        values(:messageId, 'ses_analytics_storage', :role, '概要', 'trace_analytics', :createdAt,
                               'run_analytics_summary', 'opencode', 'SUMMARY', :summaryKey, 1, 'COMPLETE')
                        """)
                .param("messageId", messageId)
                .param("role", role)
                .param("createdAt", createdAt)
                .param("summaryKey", summaryKey)
                .update();
    }

    private void insertRawShadow(String messageId, String role, Instant createdAt) {
        jdbcClient.sql("""
                        insert into session_messages(message_id, session_id, role, content, trace_id, created_at,
                                                     run_id, agent_id, content_kind)
                        values(:messageId, 'ses_analytics_storage', :role, '不应计数的原始 shadow',
                               'trace_analytics', :createdAt, 'run_analytics_summary', 'opencode', 'RAW_LEGACY')
                        """)
                .param("messageId", messageId)
                .param("role", role)
                .param("createdAt", createdAt)
                .update();
    }

    private void insertDiffEvent(String eventId, String runId, long seq, String type, Instant occurredAt) {
        jdbcClient.sql("""
                        insert into run_events(event_id, run_id, seq, type, trace_id, occurred_at, payload_json)
                        values(:eventId, :runId, :seq, :type, 'trace_analytics', :occurredAt, '{}')
                        """)
                .param("eventId", eventId)
                .param("runId", runId)
                .param("seq", seq)
                .param("type", type)
                .param("occurredAt", occurredAt)
                .update();
    }

    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
