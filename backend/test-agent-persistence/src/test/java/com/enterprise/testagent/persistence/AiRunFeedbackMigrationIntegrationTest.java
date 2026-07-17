package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 验证消息反馈回填到 Run 后会去重，并允许新 Run 反馈不带 message_id。 */
class AiRunFeedbackMigrationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-15T13:00:00Z");
    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_feedback_migration_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("20260715000000").load().migrate();
        jdbc = JdbcClient.create(dataSource);
        seedReferencesAndDuplicateFeedbacks();
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void migrationBackfillsRunDeduplicatesAndAllowsNullMessageId() {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.sql("select feedback_id from ai_message_feedbacks where run_id = 'run_feedback_migration'")
                        .query(String.class).list())
                .containsExactly("fb_feedback_newer");
        jdbc.sql("""
                insert into ai_message_feedbacks(
                    feedback_id, user_id, session_id, run_id, message_id, rating, trace_id, created_at, updated_at)
                values('fb_feedback_run_only', 'usr_feedback_migration', 'ses_feedback_migration',
                       'run_feedback_run_only', null, 'POSITIVE', 'trace_feedback_migration', :now, :now)
                """)
                .param("now", NOW.plusSeconds(20))
                .update();
        assertThat(jdbc.sql("select message_id from ai_message_feedbacks where feedback_id = 'fb_feedback_run_only'")
                        .query(String.class).optional())
                .isEmpty();
    }

    private void seedReferencesAndDuplicateFeedbacks() {
        jdbc.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                values('usr_feedback_migration', 'auth_feedback_migration', 'feedback-migration', 'hash', 'ACTIVE', :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                values('wrk_feedback_migration', 'feedback migration', '/tmp/feedback', 'ACTIVE',
                       'trace_feedback_migration', :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into sessions(session_id, workspace_id, title, status, created_by_user_id, trace_id, created_at, updated_at)
                values('ses_feedback_migration', 'wrk_feedback_migration', 'feedback migration', 'ACTIVE',
                       'usr_feedback_migration', 'trace_feedback_migration', :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into runs(run_id, session_id, workspace_id, status, trace_id, created_at, updated_at)
                values('run_feedback_migration', 'ses_feedback_migration', 'wrk_feedback_migration', 'SUCCEEDED',
                       'trace_feedback_migration', :now, :now),
                      ('run_feedback_run_only', 'ses_feedback_migration', 'wrk_feedback_migration', 'SUCCEEDED',
                       'trace_feedback_migration', :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into session_messages(message_id, session_id, role, content, trace_id, created_at, run_id)
                values('msg_feedback_older', 'ses_feedback_migration', 'ASSISTANT', '旧回复',
                       'trace_feedback_migration', :now, 'run_feedback_migration'),
                      ('msg_feedback_newer', 'ses_feedback_migration', 'ASSISTANT', '新回复',
                       'trace_feedback_migration', :later, 'run_feedback_migration')
                """).param("now", NOW).param("later", NOW.plusSeconds(1)).update();
        jdbc.sql("""
                insert into ai_message_feedbacks(
                    feedback_id, user_id, session_id, message_id, rating, trace_id, created_at, updated_at)
                values('fb_feedback_older', 'usr_feedback_migration', 'ses_feedback_migration',
                       'msg_feedback_older', 'NEGATIVE', 'trace_feedback_migration', :now, :now),
                      ('fb_feedback_newer', 'usr_feedback_migration', 'ses_feedback_migration',
                       'msg_feedback_newer', 'POSITIVE', 'trace_feedback_migration', :later, :later)
                """).param("now", NOW).param("later", NOW.plusSeconds(2)).update();
    }
}
