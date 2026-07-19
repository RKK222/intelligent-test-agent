package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.scheduler.ScheduledTask;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRegistrationStatus;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRepository;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRun;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunStatus;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.persistence.mybatis.MyBatisScheduledTaskRepository;
import com.enterprise.testagent.persistence.mybatis.ScheduledTaskMapper;
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
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** 验证 scheduler 主仓储完全通过 MyBatis XML 保持既有读写语义。 */
class MyBatisScheduledTaskRepositoryIntegrationTest {

    private SingleConnectionDataSource dataSource;
    private ScheduledTaskRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                ("jdbc:h2:mem:testagent_scheduler_mybatis_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false;"
                        + "INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE")
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        // 后续 PostgreSQL 专用 migration 使用 timestamptz；本测试只需 scheduler 基线表。
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("20260715000000").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("alter table scheduled_tasks alter column cron_expression drop not null");
        jdbc.execute("alter table scheduled_task_runs add column execution_affinity varchar(128)");
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        ScheduledTaskMapper mapper = new SqlSessionTemplate(factory).getMapper(ScheduledTaskMapper.class);
        repository = new MyBatisScheduledTaskRepository(mapper, new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void savesAndQueriesTaskAndPendingRun() {
        Instant now = Instant.parse("2026-07-18T13:00:00Z");
        ScheduledTaskKey key = new ScheduledTaskKey("opencode-runtime.night-execution");
        ScheduledTask task = new ScheduledTask(
                key,
                "夜间任务",
                "0 0 21 * * *",
                true,
                Duration.ofMinutes(5),
                now,
                ScheduledTaskRegistrationStatus.REGISTERED,
                now,
                now,
                "trace_scheduler_mybatis");
        repository.saveTask(task);
        ScheduledTaskRun run = ScheduledTaskRun.pending(
                new ScheduledTaskRunId("str_night_mybatis_0001"),
                key,
                null,
                ScheduledTaskTriggerType.USER_PLAN,
                null,
                now,
                "trace_scheduler_mybatis");
        repository.saveRun(run);

        assertThat(repository.findTaskByKey(key)).contains(task);
        assertThat(repository.findTasks(new PageRequest(1, 20)).items()).containsExactly(task);
        assertThat(repository.findPendingRuns(ScheduledTaskTriggerType.USER_PLAN, now, 10))
                .extracting(item -> item.taskRunId().value())
                .containsExactly("str_night_mybatis_0001");
        assertThat(repository.findActiveRunByTaskKey(key)).contains(run);

        ScheduledTaskRun claimed = run.start("backend-a", now);
        assertThat(repository.updateRunIfStatus(claimed, ScheduledTaskRunStatus.PENDING)).isTrue();
        assertThat(repository.updateRunIfStatus(
                run.skip("迟到的取消", now.plusSeconds(1)), ScheduledTaskRunStatus.PENDING)).isFalse();
        assertThat(repository.findRunById(run.taskRunId()).orElseThrow().status())
                .isEqualTo(ScheduledTaskRunStatus.RUNNING);
    }
}
