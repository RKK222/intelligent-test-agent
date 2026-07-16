package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.scheduler.ScheduledTaskRunRetentionRepository;
import com.icbc.testagent.persistence.mybatis.MyBatisScheduledTaskRunRetentionRepository;
import com.icbc.testagent.persistence.mybatis.ScheduledTaskRunRetentionMapper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 验证定时任务运行记录保留策略通过 MyBatis XML 清理，并保护活动记录不被误删。
 */
class MyBatisScheduledTaskRunRetentionRepositoryIntegrationTest {

    private static final String TASK_KEY = "scheduler.run-retention-cleanup";
    private static final Instant CUTOFF = Instant.parse("2026-07-08T00:00:00Z");

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private ScheduledTaskRunRetentionRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_scheduler_retention_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        insertTask();

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory();
        ScheduledTaskRunRetentionMapper mapper = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(ScheduledTaskRunRetentionMapper.class);
        repository = new MyBatisScheduledTaskRunRetentionRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void deletesOnlyTerminalRunsOlderThanSevenDays() {
        insertRun("str_retention_old_success", "SUCCEEDED", CUTOFF.minusSeconds(1));
        insertRun("str_retention_at_cutoff", "FAILED", CUTOFF);
        insertRun("str_retention_recent_skip", "SKIPPED", CUTOFF.plus(Duration.ofHours(1)));
        insertRun("str_retention_old_manual", "MANUALLY_STOPPED", CUTOFF.minus(Duration.ofDays(1)));
        insertRun("str_retention_pending", "PENDING", null);
        insertRun("str_retention_running", "RUNNING", null);

        int deletedCount = repository.deleteEndedBefore(CUTOFF);

        assertThat(deletedCount).isEqualTo(2);
        assertThat(countRun("str_retention_old_success")).isZero();
        assertThat(countRun("str_retention_old_manual")).isZero();
        assertThat(countRun("str_retention_at_cutoff")).isEqualTo(1);
        assertThat(countRun("str_retention_recent_skip")).isEqualTo(1);
        assertThat(countRun("str_retention_pending")).isEqualTo(1);
        assertThat(countRun("str_retention_running")).isEqualTo(1);
    }

    @Test
    void retentionCutoffHasAnIndex() throws Exception {
        boolean found = false;
        try (Connection connection = dataSource.getConnection();
                ResultSet indexes = connection.getMetaData().getIndexInfo(
                        null,
                        null,
                        "scheduled_task_runs",
                        false,
                        false)) {
            while (indexes.next()) {
                if ("idx_scheduled_task_runs_ended_at".equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    found = true;
                    break;
                }
            }
        }

        assertThat(found).isTrue();
    }

    private void insertTask() {
        jdbc.update("""
                insert into scheduled_tasks(
                    task_key, name, cron_expression, enabled, lock_ttl_seconds,
                    next_fire_at, registration_status, trace_id, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                TASK_KEY,
                "清理定时任务执行记录",
                "0 0 0 * * *",
                true,
                300L,
                Timestamp.from(CUTOFF),
                "REGISTERED",
                "trace_retention_integration",
                Timestamp.from(CUTOFF),
                Timestamp.from(CUTOFF));
    }

    private void insertRun(String taskRunId, String status, Instant endedAt) {
        Instant scheduledFireAt = CUTOFF.minus(Duration.ofDays(1));
        jdbc.update("""
                insert into scheduled_task_runs(
                    task_run_id, task_key, plan_id, trigger_type, status,
                    requested_by_user_id, scheduled_fire_at, started_at, ended_at,
                    owner_instance_id, skip_reason, error_code, error_message,
                    result_json, trace_id, created_at, updated_at,
                    stop_requested_at, stop_requested_by_user_id, stop_reason
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskRunId,
                TASK_KEY,
                null,
                "CRON",
                status,
                null,
                Timestamp.from(scheduledFireAt),
                endedAt == null ? null : Timestamp.from(scheduledFireAt),
                endedAt == null ? null : Timestamp.from(endedAt),
                null,
                null,
                null,
                null,
                "{}",
                "trace_retention_integration",
                Timestamp.from(scheduledFireAt),
                Timestamp.from(endedAt == null ? scheduledFireAt : endedAt),
                null,
                null,
                null);
    }

    private int countRun(String taskRunId) {
        return jdbc.queryForObject(
                "select count(*) from scheduled_task_runs where task_run_id = ?",
                Integer.class,
                taskRunId);
    }

    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
