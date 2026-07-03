package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventRepository;
import com.icbc.testagent.domain.event.RunEventScopeContext;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.persistence.mybatis.MyBatisRunEventRepository;
import com.icbc.testagent.persistence.mybatis.RunEventMapper;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
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
 * 验证 RunEvent 通过 MyBatis XML 持久化，并写入结构化 session scope 列。
 */
class MyBatisRunEventRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-03T01:00:00Z");
    private static final RunId RUN_ID = new RunId("run_event1234567890abcdef");

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbcClient;
    private RunEventRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_run_event_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcClient = JdbcClient.create(dataSource);
        seedRun();

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory();
        RunEventMapper mapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(RunEventMapper.class);
        repository = new MyBatisRunEventRepository(mapper, new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void appendWritesScopeColumnsAndLeavesMissingRawEventIdNull() {
        RunEventScopeContext scopeContext = new RunEventScopeContext(
                RUN_ID,
                "ses_root",
                "ses_child",
                "ses_root",
                true,
                "msg_task",
                "part_task",
                "call_task",
                2L,
                true);

        RunEvent event = repository.append(new RunEventDraft(
                RUN_ID,
                RunEventType.SESSION_STATUS,
                "trace_event1234567890abcdef",
                NOW,
                Map.of("status", Map.of("type", "idle")),
                scopeContext));

        assertThat(event.seq()).isEqualTo(1L);
        assertThat(event.scopeContext()).isEqualTo(scopeContext);
        assertThat(repository.findByRunIdAfter(RUN_ID, 0L, 10)).containsExactly(event);
        jdbcClient.sql("""
                select root_session_id, session_id, parent_session_id, is_child_session, scope_version,
                       task_message_id, task_part_id, task_call_id, raw_event_id
                from run_events
                where event_id = :eventId
                """)
                .param("eventId", event.eventId().value())
                .query((ResultSet rs, int rowNum) -> {
                    assertThat(rs.getString("root_session_id")).isEqualTo("ses_root");
                    assertThat(rs.getString("session_id")).isEqualTo("ses_child");
                    assertThat(rs.getString("parent_session_id")).isEqualTo("ses_root");
                    assertThat(rs.getBoolean("is_child_session")).isTrue();
                    assertThat(rs.getLong("scope_version")).isEqualTo(2L);
                    assertThat(rs.getString("task_message_id")).isEqualTo("msg_task");
                    assertThat(rs.getString("task_part_id")).isEqualTo("part_task");
                    assertThat(rs.getString("task_call_id")).isEqualTo("call_task");
                    assertThat(rs.getString("raw_event_id")).isNull();
                    return rowNum;
                })
                .single();
    }

    @Test
    void appendPersistsRawEventIdWhenPresentAndStillAllocatesMonotonicSeq() {
        RunEvent first = repository.append(new RunEventDraft(
                RUN_ID,
                RunEventType.RUN_STARTED,
                "trace_event1234567890abcdef",
                NOW,
                Map.of("status", "RUNNING", "rawEventId", "raw_1")));
        RunEvent second = repository.append(new RunEventDraft(
                RUN_ID,
                RunEventType.RUN_SUCCEEDED,
                "trace_event1234567890abcdef",
                NOW.plusSeconds(1),
                Map.of("status", "SUCCEEDED", "rawEventId", "raw_2")));

        assertThat(List.of(first.seq(), second.seq())).containsExactly(1L, 2L);
        assertThat(jdbcClient.sql("""
                select raw_event_id
                from run_events
                where event_id = :eventId
                """)
                .param("eventId", first.eventId().value())
                .query(String.class)
                .single()).isEqualTo("raw_1");
    }

    private void seedRun() {
        jdbcClient.sql("""
                insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                values('w_event1234567890abcdef', 'event workspace', '/tmp/event', 'ACTIVE',
                       'trace_event1234567890abcdef', :now, :now)
                """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at)
                values('s_event1234567890abcdef', 'w_event1234567890abcdef', 'event session', 'ACTIVE',
                       'trace_event1234567890abcdef', :now, :now)
                """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into runs(run_id, session_id, workspace_id, status, trace_id, created_at, updated_at)
                values('run_event1234567890abcdef', 's_event1234567890abcdef', 'w_event1234567890abcdef',
                       'RUNNING', 'trace_event1234567890abcdef', :now, :now)
                """)
                .param("now", NOW)
                .update();
    }

    /**
     * 直接构造 MyBatis mapper，确保 XML 映射在轻量集成测试里就能被发现。
     */
    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
