package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendId;
import com.enterprise.testagent.domain.run.RunResendPolicy;
import com.enterprise.testagent.domain.run.RunResendRepository;
import com.enterprise.testagent.domain.run.RunResendStatus;
import com.enterprise.testagent.domain.run.RunResendTrigger;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.MyBatisRunResendRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisRunResendDetailCleanup;
import com.enterprise.testagent.persistence.mybatis.RunResendMapper;
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

/** 验证重发幂等、会话锁和状态 CAS 全部由 MyBatis XML 与数据库约束保证。 */
class MyBatisRunResendRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-07T10:00:00Z");
    private static final UserId USER = new UserId("usr_resend_repository");
    private static final SessionId SESSION = new SessionId("ses_resend_repository");

    private SingleConnectionDataSource dataSource;
    private RunResendRepository repository;
    private RunResendMapper mapper;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                ("jdbc:h2:mem:testagent_resend_mybatis_%s;MODE=PostgreSQL;DATABASE_TO_LOWER=true;"
                        + "INIT=CREATE DOMAIN IF NOT EXISTS timestamptz AS TIMESTAMP WITH TIME ZONE")
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("20260715000000").load().migrate();
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260715213000__migrate_ai_feedback_to_run_scope.sql")).execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260807190000__create_run_resends.sql")).execute(dataSource);

        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into users(user_id,unified_auth_id,username,password_hash,status,created_at,updated_at) "
                        + "values(?,?,?,?,?,?,?)",
                USER.value(), "u_resend", "resend-user", "hash", "ACTIVE", NOW, NOW);
        jdbc.update("insert into workspaces(workspace_id,name,root_path,status,trace_id,created_at,updated_at) "
                        + "values(?,?,?,?,?,?,?)",
                "wrk_resend_repository", "resend", "/tmp/resend", "ACTIVE", "trace_resend", NOW, NOW);
        jdbc.update("insert into sessions(session_id,workspace_id,title,status,trace_id,created_at,updated_at) "
                        + "values(?,?,?,?,?,?,?)",
                SESSION.value(), "wrk_resend_repository", "resend", "ACTIVE", "trace_resend", NOW, NOW);
        for (String runId : java.util.List.of("run_resend_source", "run_resend_replacement")) {
            jdbc.update("insert into runs(run_id,session_id,workspace_id,status,trace_id,created_at,updated_at) "
                            + "values(?,?,?,?,?,?,?)",
                    runId, SESSION.value(), "wrk_resend_repository", "FAILED", "trace_resend", NOW, NOW);
        }

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        mapper = new SqlSessionTemplate(factory).getMapper(RunResendMapper.class);
        repository = new MyBatisRunResendRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void persistsIdempotencyAndSessionLockAndUsesStatusCas() {
        RunResend waiting = waiting();

        repository.save(waiting);
        assertThat(repository.insertSessionLock(SESSION, waiting.resendId(), USER, NOW)).isTrue();
        assertThat(repository.insertSessionLock(
                SESSION, new RunResendId("rsd_resend_conflict"), USER, NOW)).isFalse();
        assertThat(repository.findByOwnerAndClientRequestId(USER, "request-resend-1")).contains(waiting);
        assertThat(repository.findBySourceRunId(waiting.sourceRunId())).contains(waiting);
        assertThat(repository.findActiveBySession(SESSION)).contains(waiting);
        assertThat(repository.findDue(NOW, 10)).containsExactly(waiting);

        RunResend reverting = waiting.startReverting("lease_1", NOW.plusSeconds(30), NOW.plusSeconds(1));
        assertThat(repository.saveIfStatus(reverting, RunResendStatus.WAITING)).isTrue();
        assertThat(repository.saveIfStatus(waiting.cancel(NOW.plusSeconds(2)), RunResendStatus.WAITING)).isFalse();

        repository.deleteSessionLock(SESSION, waiting.resendId());
        assertThat(repository.hasSessionLock(SESSION)).isFalse();
    }

    @Test
    void committedCleanupRemovesSourceDetailsButKeepsRunAndFeedbackAudit() {
        jdbc.update("insert into session_messages(message_id,session_id,role,content,trace_id,created_at,run_id,remote_message_id) "
                        + "values(?,?,?,?,?,?,?,?)",
                "msg_source_answer", SESSION.value(), "ASSISTANT", "old answer", "trace_resend", NOW,
                "run_resend_source", "msg_remote_answer");
        jdbc.update("insert into ai_message_feedbacks(feedback_id,user_id,session_id,run_id,message_id,rating,trace_id,created_at,updated_at) "
                        + "values(?,?,?,?,?,?,?,?,?)",
                "fb_resend_source", USER.value(), SESSION.value(), "run_resend_source", "msg_source_answer",
                "NEGATIVE", "trace_resend", NOW, NOW);
        jdbc.update("insert into run_events(event_id,run_id,seq,type,trace_id,occurred_at,payload_json) values(?,?,?,?,?,?,?)",
                "evt_resend_source", "run_resend_source", 1L, "tool.finished", "trace_resend", NOW, "{}");
        jdbc.update("insert into run_session_scopes(run_id,root_session_id,scope_version,trace_id,metadata_json,created_at,updated_at) "
                        + "values(?,?,?,?,?,?,?)",
                "run_resend_source", "ses_remote_root", 1L, "trace_resend", "{}", NOW, NOW);
        jdbc.update("insert into run_session_scope_sessions(run_id,session_id,root_session_id,child_session,discovery_source,trace_id,metadata_json,discovered_at,updated_at) "
                        + "values(?,?,?,?,?,?,?,?,?)",
                "run_resend_source", "ses_remote_root", "ses_remote_root", false, "ROOT", "trace_resend", "{}", NOW, NOW);

        new MyBatisRunResendDetailCleanup(mapper).purgeSourceRun(new RunId("run_resend_source"));

        assertThat(jdbc.queryForObject("select count(*) from runs where run_id='run_resend_source'", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from session_messages where run_id='run_resend_source'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from run_events where run_id='run_resend_source'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from run_session_scopes where run_id='run_resend_source'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ai_message_feedbacks where run_id='run_resend_source'", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select count(*) from ai_message_feedbacks where run_id='run_resend_source' and message_id is null",
                Long.class)).isEqualTo(1L);
    }

    private RunResend waiting() {
        return new RunResend(
                new RunResendId("rsd_resend_repository"), SESSION, USER,
                new RunId("run_resend_source"), new RunId("run_resend_replacement"),
                "msg_resend_source", "msg_resend_replacement", RunResendTrigger.AUTOMATIC,
                1, 1, RunResendPolicy.MAX_AUTOMATIC_ATTEMPTS, RunResendStatus.WAITING,
                NOW, "linux-resend-1", null, null, "request-resend-1", "trace_resend",
                null, NOW, NOW);
    }
}
