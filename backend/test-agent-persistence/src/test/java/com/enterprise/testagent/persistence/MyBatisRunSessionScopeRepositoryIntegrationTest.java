package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.event.RunSessionScope;
import com.enterprise.testagent.domain.event.RunSessionScopeRepository;
import com.enterprise.testagent.domain.event.RunSessionScopeSession;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.persistence.mybatis.MyBatisRunSessionScopeRepository;
import com.enterprise.testagent.persistence.mybatis.RunSessionScopeMapper;
import java.time.Instant;
import java.util.Map;
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
 * 验证 Run session scope 通过 MyBatis XML 持久化，且 Flyway migration 兼容 H2 PostgreSQL 模式。
 */
class MyBatisRunSessionScopeRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-03T00:00:00Z");

    private SingleConnectionDataSource dataSource;
    private RunSessionScopeRepository repository;
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_run_session_scope_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcClient = JdbcClient.create(dataSource);
        seedRun();

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory();
        RunSessionScopeMapper mapper = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(RunSessionScopeMapper.class);
        repository = new MyBatisRunSessionScopeRepository(mapper, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void scopeAndSessionsAreUpsertedAndQueriedThroughMyBatisXmlMapper() {
        RunId runId = new RunId("run_scope1234567890abcdef");

        repository.upsertScope(new RunSessionScope(
                runId,
                "ses_root",
                1L,
                "trace_scope1234567890abcdef",
                NOW,
                NOW,
                Map.of("source", "runtime")));
        repository.upsertSession(new RunSessionScopeSession(
                runId,
                "ses_root",
                "ses_root",
                null,
                false,
                "ROOT",
                null,
                null,
                null,
                "trace_scope1234567890abcdef",
                NOW,
                NOW,
                Map.of()));
        repository.upsertSession(new RunSessionScopeSession(
                runId,
                "ses_child",
                "ses_root",
                "ses_root",
                true,
                "TASK_PART",
                "msg_task",
                "part_task",
                "call_task",
                "trace_scope1234567890abcdef",
                NOW.plusSeconds(1),
                NOW.plusSeconds(1),
                Map.of("agent", "build")));

        assertThat(repository.findSessionsByRunId(runId))
                .extracting(RunSessionScopeSession::sessionId)
                .containsExactly("ses_root", "ses_child");
        assertThat(repository.findSessionsByRootSessionId("ses_root"))
                .extracting(RunSessionScopeSession::sessionId)
                .containsExactly("ses_root", "ses_child");
        assertThat(repository.findSession(runId, "ses_child"))
                .get()
                .satisfies(session -> {
                    assertThat(session.childSession()).isTrue();
                    assertThat(session.parentSessionId()).isEqualTo("ses_root");
                    assertThat(session.taskPartId()).isEqualTo("part_task");
                    assertThat(session.metadata()).containsEntry("agent", "build");
                });
    }

    private void seedRun() {
        jdbcClient.sql("""
                insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                values('w_scope1234567890abcdef', 'scope workspace', '/tmp/scope', 'ACTIVE',
                       'trace_scope1234567890abcdef', :now, :now)
                """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at)
                values('s_scope1234567890abcdef', 'w_scope1234567890abcdef', 'scope session', 'ACTIVE',
                       'trace_scope1234567890abcdef', :now, :now)
                """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into runs(run_id, session_id, workspace_id, status, trace_id, created_at, updated_at)
                values('run_scope1234567890abcdef', 's_scope1234567890abcdef', 'w_scope1234567890abcdef',
                       'RUNNING', 'trace_scope1234567890abcdef', :now, :now)
                """)
                .param("now", NOW)
                .update();
    }

    /**
     * 直接用 MyBatis-Spring 构造 mapper，避免完整 Spring Boot 上下文掩盖 XML 配置问题。
     */
    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
