package com.enterprise.testagent.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.event.RunEvent;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventRepository;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.event.RunEventAppender;
import com.enterprise.testagent.opencode.runtime.runtime.SideQuestionTerminalService;
import com.enterprise.testagent.persistence.mybatis.MyBatisRunEventRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisRunRepository;
import com.enterprise.testagent.persistence.mybatis.RunEventMapper;
import com.enterprise.testagent.persistence.mybatis.RunMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 使用真实 H2、Flyway、MyBatis 与 Spring 事务代理验证旁路终态 CAS 和事件追加具备同一事务边界。
 */
class SideQuestionTerminalTransactionIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-11T01:00:00Z");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_terminaltx1234567890");
    private static final SessionId SESSION_ID = new SessionId("ses_terminaltx1234567890");
    private static final AtomicBoolean FAIL_AFTER_APPEND = new AtomicBoolean();
    private static final String JDBC_URL = "jdbc:h2:mem:side_question_terminal_"
            + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1";

    private static AnnotationConfigApplicationContext context;
    private JdbcClient jdbc;
    private RunRepository runs;
    private RunEventRepository events;
    private SideQuestionTerminalService terminalService;

    @BeforeAll
    static void setUpContext() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
    }

    @AfterAll
    static void closeContext() {
        context.close();
    }

    @BeforeEach
    void resetDatabase() {
        FAIL_AFTER_APPEND.set(false);
        jdbc = JdbcClient.create(context.getBean(DataSource.class));
        runs = context.getBean(RunRepository.class);
        events = context.getBean("durableRunEvents", RunEventRepository.class);
        terminalService = context.getBean(SideQuestionTerminalService.class);
        jdbc.sql("delete from run_events").update();
        jdbc.sql("delete from runs").update();
        jdbc.sql("delete from sessions").update();
        jdbc.sql("delete from workspaces").update();
        seedWorkspaceAndSession();
    }

    @Test
    void successAndCleanupRaceProduceOnePersistedTerminalFact() throws Exception {
        Run running = saveRunning("run_terminaltxsuccess01");

        List<Boolean> results = race(
                () -> terminalService.succeed(
                        running.runId(),
                        java.util.Map.of("sideQuestion", true, "answer", "完成", "compacted", false),
                        running.traceId()),
                () -> terminalService.fail(running.runId(), "旁路任务超时", running.traceId()));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        Run persisted = runs.findById(running.runId()).orElseThrow();
        assertThat(persisted.status()).isIn(RunStatus.SUCCEEDED, RunStatus.FAILED);
        List<RunEvent> terminalEvents = terminalEvents(running.runId());
        assertThat(terminalEvents).hasSize(1);
        assertThat(terminalEvents.getFirst().type()).isIn(RunEventType.RUN_SUCCEEDED, RunEventType.RUN_FAILED);
    }

    @Test
    void failureAndCleanupRaceStillProduceOneFailedTerminalFact() throws Exception {
        Run running = saveRunning("run_terminaltxfailure01");

        List<Boolean> results = race(
                () -> terminalService.fail(running.runId(), "远端流失败", running.traceId()),
                () -> terminalService.fail(running.runId(), "旁路任务超时", running.traceId()));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(runs.findById(running.runId())).get().extracting(Run::status).isEqualTo(RunStatus.FAILED);
        assertThat(terminalEvents(running.runId()))
                .singleElement()
                .extracting(RunEvent::type)
                .isEqualTo(RunEventType.RUN_FAILED);
    }

    @Test
    void appendFailureRollsBackBothRunCasAndInsertedEvent() {
        Run running = saveRunning("run_terminaltxrollback01");
        FAIL_AFTER_APPEND.set(true);

        assertThatThrownBy(() -> terminalService.fail(running.runId(), "旁路问答暂时失败", running.traceId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced append failure");

        assertThat(runs.findById(running.runId())).get().extracting(Run::status).isEqualTo(RunStatus.RUNNING);
        assertThat(events.findByRunIdAfter(running.runId(), 0, 20)).isEmpty();
    }

    private List<Boolean> race(java.util.concurrent.Callable<Boolean> first, java.util.concurrent.Callable<Boolean> second)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstResult = executor.submit(() -> {
                ready.countDown();
                start.await();
                return first.call();
            });
            Future<Boolean> secondResult = executor.submit(() -> {
                ready.countDown();
                start.await();
                return second.call();
            });
            ready.await();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        }
    }

    private Run saveRunning(String runId) {
        Run running = new Run(
                new RunId(runId),
                SESSION_ID,
                WORKSPACE_ID,
                RunStatus.RUNNING,
                NOW,
                NOW,
                "trace_terminaltx1234567890");
        return runs.save(running);
    }

    private List<RunEvent> terminalEvents(RunId runId) {
        return events.findByRunIdAfter(runId, 0, 20).stream()
                .filter(event -> event.type() == RunEventType.RUN_SUCCEEDED || event.type() == RunEventType.RUN_FAILED)
                .toList();
    }

    private void seedWorkspaceAndSession() {
        jdbc.sql("""
                insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                values(:workspaceId, 'terminal tx workspace', '/tmp/terminal-tx', 'ACTIVE', :traceId, :now, :now)
                """)
                .param("workspaceId", WORKSPACE_ID.value())
                .param("traceId", "trace_terminaltx1234567890")
                .param("now", NOW)
                .update();
        jdbc.sql("""
                insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at)
                values(:sessionId, :workspaceId, 'terminal tx session', 'ARCHIVED', :traceId, :now, :now)
                """)
                .param("sessionId", SESSION_ID.value())
                .param("workspaceId", WORKSPACE_ID.value())
                .param("traceId", "trace_terminaltx1234567890")
                .param("now", NOW)
                .update();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(JDBC_URL, "sa", "");
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:mybatis/**/*.xml"));
            return factory.getObject();
        }

        @Bean
        RunMapper runMapper(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory).getMapper(RunMapper.class);
        }

        @Bean
        RunEventMapper runEventMapper(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory).getMapper(RunEventMapper.class);
        }

        @Bean
        RunRepository runRepository(RunMapper mapper) {
            return new MyBatisRunRepository(mapper);
        }

        @Bean("durableRunEvents")
        RunEventRepository durableRunEvents(RunEventMapper mapper) {
            return new MyBatisRunEventRepository(mapper, new ObjectMapper().findAndRegisterModules());
        }

        @Bean
        RunEventAppender runEventAppender(RunEventRepository durableRunEvents) {
            RunEventRepository failingAfterAppend = new RunEventRepository() {
                @Override
                public RunEvent append(RunEventDraft draft) {
                    RunEvent appended = durableRunEvents.append(draft);
                    if (FAIL_AFTER_APPEND.get()) {
                        throw new IllegalStateException("forced append failure");
                    }
                    return appended;
                }

                @Override
                public List<RunEvent> findByRunIdAfter(RunId runId, long lastSeq, int limit) {
                    return durableRunEvents.findByRunIdAfter(runId, lastSeq, limit);
                }
            };
            return new RunEventAppender(failingAfterAppend);
        }

        @Bean
        SideQuestionTerminalService sideQuestionTerminalService(
                RunRepository runRepository,
                RunEventAppender runEventAppender) {
            return new SideQuestionTerminalService(runRepository, runEventAppender);
        }
    }
}
