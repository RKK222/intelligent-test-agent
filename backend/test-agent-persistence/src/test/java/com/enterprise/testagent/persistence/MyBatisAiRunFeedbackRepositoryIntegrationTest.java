package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.analytics.AiMessageFeedbackId;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackRating;
import com.enterprise.testagent.domain.analytics.AiRunFeedback;
import com.enterprise.testagent.domain.analytics.AiRunFeedbackRepository;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.AiRunFeedbackMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisAiRunFeedbackRepository;
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

/** 验证 Run 级反馈只以 userId/runId 持久化，message_id 保持为空。 */
class MyBatisAiRunFeedbackRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-15T13:30:00Z");
    private static final UserId USER_ID = new UserId("usr_feedback_run_test01");
    private static final SessionId SESSION_ID = new SessionId("ses_feedback_run_test01");
    private static final RunId RUN_ID = new RunId("run_feedback_run_test01");

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbcClient;
    private AiRunFeedbackRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_run_feedback_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcClient = JdbcClient.create(dataSource);
        seedReferences();
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        AiRunFeedbackMapper mapper = new SqlSessionTemplate(sessionFactory).getMapper(AiRunFeedbackMapper.class);
        repository = new MyBatisAiRunFeedbackRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void saveFindAndBatchQueryUseRunScopeWithoutMessageId() {
        AiRunFeedback feedback = feedback(AiMessageFeedbackRating.POSITIVE, NOW);
        repository.save(feedback);

        assertThat(repository.findByUserIdAndRunId(USER_ID, RUN_ID)).contains(feedback);
        assertThat(repository.findByUserIdAndRunIds(
                        USER_ID, List.of(RUN_ID, new RunId("run_feedback_missing01"))))
                .containsExactly(feedback);
        assertThat(jdbcClient.sql("select message_id from ai_message_feedbacks where run_id = :runId")
                        .param("runId", RUN_ID.value()).query(String.class).optional())
                .isEmpty();

        AiRunFeedback updated = feedback.update(
                AiMessageFeedbackRating.NEGATIVE, null, "需要改进", "trace_feedback_updated", NOW.plusSeconds(1));
        repository.save(updated);
        assertThat(repository.findByUserIdAndRunId(USER_ID, RUN_ID)).contains(updated);
        assertThat(jdbcClient.sql("select count(*) from ai_message_feedbacks where run_id = :runId")
                        .param("runId", RUN_ID.value()).query(Integer.class).single())
                .isEqualTo(1);
    }

    private AiRunFeedback feedback(AiMessageFeedbackRating rating, Instant updatedAt) {
        return new AiRunFeedback(
                new AiMessageFeedbackId("fb_feedback_run_test01"), USER_ID, SESSION_ID, RUN_ID,
                rating, null, null, "机构", "研发部", "部门", "trace_feedback_run_test", NOW, updatedAt);
    }

    private void seedReferences() {
        jdbcClient.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                values(:userId, 'u_feedback_run_test', 'feedback-run-user', 'hash', 'ACTIVE', :now, :now)
                """).param("userId", USER_ID.value()).param("now", NOW).update();
        jdbcClient.sql("""
                insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                values('wrk_feedback_run_test01', 'feedback workspace', '/tmp/feedback', 'ACTIVE',
                       'trace_feedback_run_test', :now, :now)
                """).param("now", NOW).update();
        jdbcClient.sql("""
                insert into sessions(session_id, workspace_id, title, status, created_by_user_id, trace_id, created_at, updated_at)
                values(:sessionId, 'wrk_feedback_run_test01', 'feedback session', 'ACTIVE', :userId,
                       'trace_feedback_run_test', :now, :now)
                """).param("sessionId", SESSION_ID.value()).param("userId", USER_ID.value()).param("now", NOW).update();
        jdbcClient.sql("""
                insert into runs(run_id, session_id, workspace_id, status, trace_id, created_at, updated_at)
                values(:runId, :sessionId, 'wrk_feedback_run_test01', 'SUCCEEDED',
                       'trace_feedback_run_test', :now, :now)
                """).param("runId", RUN_ID.value()).param("sessionId", SESSION_ID.value()).param("now", NOW).update();
    }
}
