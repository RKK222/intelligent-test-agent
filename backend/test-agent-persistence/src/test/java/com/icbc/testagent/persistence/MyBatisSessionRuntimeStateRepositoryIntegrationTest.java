package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.session.SessionRuntimeAttention;
import com.icbc.testagent.domain.session.SessionRuntimeStateRepository;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.persistence.mybatis.MyBatisSessionRuntimeStateRepository;
import com.icbc.testagent.persistence.mybatis.SessionRuntimeStateMapper;
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
                     :now, :now6, 'usr_runtime_other')
                """)
                .param("now", NOW)
                .param("now1", NOW.plusSeconds(10))
                .param("now2", NOW.plusSeconds(20))
                .param("now3", NOW.plusSeconds(30))
                .param("now4", NOW.plusSeconds(40))
                .param("now5", NOW.plusSeconds(50))
                .param("now6", NOW.plusSeconds(60))
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
                     :now, :otherUpdated, 'usr_runtime_other')
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
                     'trace_runtime', :questionAsked, '{}'),
                    ('evt_runtime_resolved_asked', 'run_runtime_resolved', 1, 'question.asked',
                     'trace_runtime', :resolvedAsked, '{}'),
                    ('evt_runtime_resolved_reply', 'run_runtime_resolved', 2, 'question.replied',
                     'trace_runtime', :resolvedReply, '{}'),
                    ('evt_runtime_terminal_asked', 'run_runtime_terminal', 1, 'question.asked',
                     'trace_runtime', :terminalAsked, '{}')
                """)
                .param("questionAsked", NOW.plusSeconds(21))
                .param("resolvedAsked", NOW.plusSeconds(31))
                .param("resolvedReply", NOW.plusSeconds(33))
                .param("terminalAsked", NOW.plusSeconds(41))
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
