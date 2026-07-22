package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.session.SessionRuntimeAttention;
import com.enterprise.testagent.domain.session.SessionRuntimeStateRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.MyBatisSessionRuntimeStateRepository;
import com.enterprise.testagent.persistence.mybatis.SessionRuntimeStateMapper;
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

/**
 * 验证用户级会话运行态通过 MyBatis XML 聚合 active run 和待答 question 状态。
 */
class MyBatisSessionRuntimeStateRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-08T08:00:00Z");
    private static final UserId CURRENT_USER = new UserId("usr_runtime_current");

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbcClient;
    private SessionRuntimeStateRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_session_runtime_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcClient = JdbcClient.create(dataSource);
        seedData();

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory();
        SessionRuntimeStateMapper mapper = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(SessionRuntimeStateMapper.class);
        repository = new MyBatisSessionRuntimeStateRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void summaryCountsOnlyCurrentUserVisibleActiveRunsAndPendingQuestions() {
        var summary = repository.findUserRuntimeState(CURRENT_USER);

        assertThat(summary.runningCount()).isEqualTo(4);
        assertThat(summary.questionCount()).isEqualTo(1);
        assertThat(summary.sessions())
                .extracting(state -> state.sessionId().value())
                .containsExactly("ses_runtime_resolved", "ses_runtime_question", "ses_runtime_message", "ses_runtime_run");

        assertThat(summary.sessions().get(0).attention()).isNull();
        assertThat(summary.sessions().get(1)).satisfies(state -> {
            assertThat(state.runId().value()).isEqualTo("run_runtime_question");
            assertThat(state.runStatus().name()).isEqualTo("RUNNING");
            assertThat(state.attention()).isEqualTo(SessionRuntimeAttention.QUESTION);
            assertThat(state.attentionEventId()).isEqualTo("evt_runtime_question_asked");
            assertThat(state.attentionAt()).isEqualTo(NOW.plusSeconds(21));
        });
        assertThat(summary.sessions().get(2).attention()).isNull();
        assertThat(summary.sessions().get(3).attention()).isNull();
    }

    @Test
    void resolvedQuestionAndTerminalRunDoNotProduceAttention() {
        var summary = repository.findUserRuntimeState(CURRENT_USER);

        assertThat(summary.sessions())
                .extracting(state -> state.sessionId().value())
                .contains("ses_runtime_resolved")
                .doesNotContain("ses_runtime_terminal");
    }

    @Test
    void pendingPermissionWithNoMatchingReplyProducesPermissionAttention() {
        seedPermissionAttentionRuns();

        var summary = repository.findUserRuntimeState(CURRENT_USER);

        assertThat(summary.permissionCount()).isEqualTo(1);
        assertThat(summary.sessions())
                .filteredOn(state -> state.sessionId().value().equals("ses_runtime_permission_pending"))
                .singleElement()
                .satisfies(state -> {
                    assertThat(String.valueOf(state.attention())).isEqualTo("PERMISSION");
                    assertThat(state.attentionEventId()).isEqualTo("evt_runtime_permission_asked");
                });
        assertThat(summary.sessions())
                .filteredOn(state -> state.sessionId().value().equals("ses_runtime_permission_resolved"))
                .singleElement()
                .satisfies(state -> assertThat(state.attention()).isNull());
        assertThat(summary.sessions())
                .filteredOn(state -> state.sessionId().value().equals("ses_runtime_permission_legacy_resolved"))
                .singleElement()
                .satisfies(state -> assertThat(state.attention()).isNull());
    }

    @Test
    void runtimeStateExcludesInternalSideQuestionSessionsAndRuns() {
        var summary = repository.findUserRuntimeState(CURRENT_USER);

        assertThat(summary.sessions())
                .extracting(state -> state.sessionId().value())
                .doesNotContain("ses_runtime_side_question", "ses_runtime_side_question_active");
        assertThat(summary.runningCount()).isEqualTo(4);
    }

    private void seedData() {
        seedUsersAndWorkspaces();
        seedSessions();
        seedRuns();
        seedMessagesAndEvents();
    }

    private void seedUsersAndWorkspaces() {
        jdbcClient.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                values
                    ('usr_runtime_current', 'auth_runtime_current', 'runtime-current', 'hash', 'ACTIVE', :now, :now),
                    ('usr_runtime_other', 'auth_runtime_other', 'runtime-other', 'hash', 'ACTIVE', :now, :now)
                """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                values('wrk_runtime', 'runtime workspace', '/tmp/runtime', 'ACTIVE', 'trace_runtime', :now, :now)
                """)
                .param("now", NOW)
                .update();
    }

    private void seedSessions() {
        jdbcClient.sql("""
                insert into sessions(
                    session_id, workspace_id, title, status, trace_id, created_at, updated_at, created_by_user_id)
                values
                    ('ses_runtime_run', 'wrk_runtime', 'run attribution', 'ACTIVE', 'trace_runtime',
                     :now, :now1, null),
                    ('ses_runtime_message', 'wrk_runtime', 'message attribution', 'ACTIVE', 'trace_runtime',
                     :now, :now2, null),
                    ('ses_runtime_question', 'wrk_runtime', 'question attention', 'ACTIVE', 'trace_runtime',
                     :now, :now3, 'usr_runtime_current'),
                    ('ses_runtime_resolved', 'wrk_runtime', 'resolved question', 'ACTIVE', 'trace_runtime',
                     :now, :now4, 'usr_runtime_current'),
                    ('ses_runtime_terminal', 'wrk_runtime', 'terminal run', 'ACTIVE', 'trace_runtime',
                     :now, :now5, 'usr_runtime_current'),
                    ('ses_runtime_other', 'wrk_runtime', 'other user', 'ACTIVE', 'trace_runtime',
                     :now, :now6, 'usr_runtime_other'),
                    ('ses_runtime_side_question', 'wrk_runtime', '宠物旁路问答（内部）', 'ARCHIVED', 'trace_runtime',
                     :now, :now6, 'usr_runtime_current'),
                    ('ses_runtime_side_question_active', 'wrk_runtime', '异常未归档旁路会话', 'ACTIVE', 'trace_runtime',
                     :now, :now6, 'usr_runtime_current')
                """)
                .param("now", NOW)
                .param("now1", NOW.plusSeconds(10))
                .param("now2", NOW.plusSeconds(20))
                .param("now3", NOW.plusSeconds(30))
                .param("now4", NOW.plusSeconds(40))
                .param("now5", NOW.plusSeconds(50))
                .param("now6", NOW.plusSeconds(60))
                .update();
        jdbcClient.sql("""
                update sessions
                set source_type = 'SIDE_QUESTION', source_ref_id = 'ses_runtime_run'
                where session_id in ('ses_runtime_side_question', 'ses_runtime_side_question_active')
                """)
                .update();
    }

    private void seedRuns() {
        jdbcClient.sql("""
                insert into runs(run_id, session_id, workspace_id, status, trace_id, created_at, updated_at, triggered_by_user_id)
                values
                    ('run_runtime_run_old', 'ses_runtime_run', 'wrk_runtime', 'RUNNING', 'trace_runtime',
                     :now, :old, 'usr_runtime_current'),
                    ('run_runtime_run', 'ses_runtime_run', 'wrk_runtime', 'CANCELLING', 'trace_runtime',
                     :now, :runUpdated, 'usr_runtime_current'),
                    ('run_runtime_message', 'ses_runtime_message', 'wrk_runtime', 'PENDING', 'trace_runtime',
                     :now, :messageUpdated, null),
                    ('run_runtime_question', 'ses_runtime_question', 'wrk_runtime', 'RUNNING', 'trace_runtime',
                     :now, :questionUpdated, 'usr_runtime_current'),
                    ('run_runtime_resolved', 'ses_runtime_resolved', 'wrk_runtime', 'RUNNING', 'trace_runtime',
                     :now, :resolvedUpdated, 'usr_runtime_current'),
                    ('run_runtime_terminal', 'ses_runtime_terminal', 'wrk_runtime', 'SUCCEEDED', 'trace_runtime',
                     :now, :terminalUpdated, 'usr_runtime_current'),
                    ('run_runtime_other', 'ses_runtime_other', 'wrk_runtime', 'RUNNING', 'trace_runtime',
                     :now, :otherUpdated, 'usr_runtime_other'),
                    ('run_runtime_side_question', 'ses_runtime_side_question', 'wrk_runtime', 'RUNNING', 'trace_runtime',
                     :now, :otherUpdated, 'usr_runtime_current'),
                    ('run_runtime_side_question_active', 'ses_runtime_side_question_active', 'wrk_runtime', 'RUNNING', 'trace_runtime',
                     :now, :otherUpdated, 'usr_runtime_current')
                """)
                .param("now", NOW)
                .param("old", NOW.plusSeconds(11))
                .param("runUpdated", NOW.plusSeconds(12))
                .param("messageUpdated", NOW.plusSeconds(22))
                .param("questionUpdated", NOW.plusSeconds(32))
                .param("resolvedUpdated", NOW.plusSeconds(42))
                .param("terminalUpdated", NOW.plusSeconds(52))
                .param("otherUpdated", NOW.plusSeconds(62))
                .update();
        jdbcClient.sql("""
                update runs
                set source_type = 'SIDE_QUESTION', source_ref_id = 'ses_runtime_run'
                where run_id in ('run_runtime_side_question', 'run_runtime_side_question_active')
                """)
                .update();
    }

    private void seedMessagesAndEvents() {
        jdbcClient.sql("""
                insert into session_messages(
                    message_id, session_id, role, content, trace_id, created_at, updated_at, sender_user_id)
                values('msg_runtime_current', 'ses_runtime_message', 'USER', 'visible by message',
                       'trace_runtime', :now, :now, 'usr_runtime_current')
                """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into run_events(event_id, run_id, seq, type, trace_id, occurred_at, payload_json)
                values
                    ('evt_runtime_question_asked', 'run_runtime_question', 1, 'question.asked',
                     'trace_runtime', :questionAsked, '{"id":"question_pending","options":[{"id":"nested_question"}]}'),
                    ('evt_runtime_resolved_asked', 'run_runtime_resolved', 1, 'question.asked',
                     'trace_runtime', :resolvedAsked, '{"id":"question_resolved","options":[{"id":"nested_question_resolved"}]}'),
                    ('evt_runtime_resolved_reply', 'run_runtime_resolved', 2, 'question.replied',
                     'trace_runtime', :resolvedReply, '{"requestID":"question_resolved"}'),
                    ('evt_runtime_terminal_asked', 'run_runtime_terminal', 1, 'question.asked',
                     'trace_runtime', :terminalAsked, '{}')
                """)
                .param("questionAsked", NOW.plusSeconds(21))
                .param("resolvedAsked", NOW.plusSeconds(31))
                .param("resolvedReply", NOW.plusSeconds(33))
                .param("terminalAsked", NOW.plusSeconds(41))
                .update();
    }

    private void seedPermissionAttentionRuns() {
        jdbcClient.sql("""
                insert into sessions(
                    session_id, workspace_id, title, status, trace_id, created_at, updated_at, created_by_user_id)
                values
                    ('ses_runtime_permission_pending', 'wrk_runtime', 'permission pending', 'ACTIVE', 'trace_runtime',
                     :now, :pendingUpdated, 'usr_runtime_current'),
                    ('ses_runtime_permission_resolved', 'wrk_runtime', 'permission resolved', 'ACTIVE', 'trace_runtime',
                     :now, :resolvedUpdated, 'usr_runtime_current'),
                    ('ses_runtime_permission_legacy_resolved', 'wrk_runtime', 'legacy permission resolved', 'ACTIVE',
                     'trace_runtime', :now, :legacyResolvedUpdated, 'usr_runtime_current')
                """)
                .param("now", NOW)
                .param("pendingUpdated", NOW.plusSeconds(70))
                .param("resolvedUpdated", NOW.plusSeconds(80))
                .param("legacyResolvedUpdated", NOW.plusSeconds(90))
                .update();
        // legacy asked/replied 故意使用不同数量的空格，避免错误实现因“相同脏值”偶然收敛。
        jdbcClient.sql("""
                insert into runs(run_id, session_id, workspace_id, status, trace_id, created_at, updated_at, triggered_by_user_id)
                values
                    ('run_runtime_permission_pending', 'ses_runtime_permission_pending', 'wrk_runtime', 'RUNNING',
                     'trace_runtime', :now, :pendingUpdated, 'usr_runtime_current'),
                    ('run_runtime_permission_resolved', 'ses_runtime_permission_resolved', 'wrk_runtime', 'RUNNING',
                     'trace_runtime', :now, :resolvedUpdated, 'usr_runtime_current'),
                    ('run_runtime_permission_legacy_resolved', 'ses_runtime_permission_legacy_resolved', 'wrk_runtime',
                     'RUNNING', 'trace_runtime', :now, :legacyResolvedUpdated, 'usr_runtime_current')
                """)
                .param("now", NOW)
                .param("pendingUpdated", NOW.plusSeconds(70))
                .param("resolvedUpdated", NOW.plusSeconds(80))
                .param("legacyResolvedUpdated", NOW.plusSeconds(90))
                .update();
        jdbcClient.sql("""
                insert into run_events(event_id, run_id, seq, type, trace_id, occurred_at, payload_json)
                values
                    ('evt_runtime_permission_asked', 'run_runtime_permission_pending', 1, 'permission.asked',
                     'trace_runtime', :pendingAsked, '{"id":"permission_pending","options":[{"id":"nested_permission"}]}'),
                    ('evt_runtime_permission_unrelated_reply', 'run_runtime_permission_pending', 2, 'permission.replied',
                     'trace_runtime', :pendingUnrelatedReply, '{"requestID":"permission_other"}'),
                    ('evt_runtime_permission_resolved_asked', 'run_runtime_permission_resolved', 1, 'permission.asked',
                     'trace_runtime', :resolvedAsked, '{"id":"permission_resolved","options":[{"id":"nested_permission_resolved"}]}'),
                    ('evt_runtime_permission_replied', 'run_runtime_permission_resolved', 2, 'permission.replied',
                     'trace_runtime', :resolvedReply,
                     '{"sessionID":"ses_remote_permission","requestID":"permission_resolved","reply":"once"}'),
                    ('evt_runtime_permission_legacy_asked', 'run_runtime_permission_legacy_resolved', 1,
                     'permission.asked', 'trace_runtime', :legacyResolvedAsked, '{"id":"   "}'),
                    ('evt_runtime_permission_legacy_replied', 'run_runtime_permission_legacy_resolved', 2,
                     'permission.replied', 'trace_runtime', :legacyResolvedReply, '{"requestID":"  "}')
                """)
                .param("pendingAsked", NOW.plusSeconds(71))
                .param("pendingUnrelatedReply", NOW.plusSeconds(72))
                .param("resolvedAsked", NOW.plusSeconds(81))
                // seq 才是同一 Run 的因果主序；模拟回复映射时间回拨且 requestID 不是首字段。
                .param("resolvedReply", NOW.plusSeconds(80))
                .param("legacyResolvedAsked", NOW.plusSeconds(91))
                .param("legacyResolvedReply", NOW.plusSeconds(92))
                .update();
    }

    /**
     * 直接构造 mapper，确保运行态 SQL 只依赖 XML 映射即可运行。
     */
    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
