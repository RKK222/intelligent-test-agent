package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.session.SessionRuntimeAttention;
import com.enterprise.testagent.domain.session.SessionRuntimeStateRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.MyBatisSessionRuntimeStateRepository;
import com.enterprise.testagent.persistence.mybatis.SessionRuntimeStateMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 以真实 PostgreSQL jsonb 分支验证 permission 请求标识与 Run seq 因果收敛。 */
@Testcontainers(disabledWithoutDocker = true)
class MyBatisSessionRuntimeStatePostgresqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_runtime_pg");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static SessionRuntimeStateRepository repository;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        seedData(dataSource);
        repository = repository(dataSource);
    }

    @Test
    void postgresqlJsonbUsesTopLevelRequestIdAndSeqInsteadOfOccurredAt() {
        var summary = repository.findUserRuntimeState(USER_ID);

        assertThat(summary.runningCount()).isEqualTo(3);
        assertThat(summary.permissionCount()).isEqualTo(1);
        assertThat(summary.sessions())
                .filteredOn(state -> state.sessionId().value().equals("ses_runtime_pg_resolved"))
                .singleElement()
                .satisfies(state -> assertThat(state.attention()).isNull());
        assertThat(summary.sessions())
                .filteredOn(state -> state.sessionId().value().equals("ses_runtime_pg_pending"))
                .singleElement()
                .satisfies(state -> {
                    assertThat(state.attention()).isEqualTo(SessionRuntimeAttention.PERMISSION);
                    assertThat(state.attentionEventId()).isEqualTo("evt_runtime_pg_pending_asked");
                });
        assertThat(summary.sessions())
                .filteredOn(state -> state.sessionId().value().equals("ses_runtime_pg_blank_resolved"))
                .singleElement()
                .satisfies(state -> assertThat(state.attention()).isNull());
    }

    private static void seedData(DataSource dataSource) {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                values ('usr_runtime_pg', 'auth_runtime_pg', 'runtime-pg', 'hash', 'ACTIVE', :now, :now)
                """).param("now", Timestamp.from(NOW)).update();
        jdbc.sql("""
                insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                values ('wrk_runtime_pg', 'runtime pg', '/tmp/runtime-pg', 'ACTIVE', 'trace_runtime_pg', :now, :now)
                """).param("now", Timestamp.from(NOW)).update();
        jdbc.sql("""
                insert into sessions(
                    session_id, workspace_id, title, status, trace_id, created_at, updated_at, created_by_user_id)
                values
                    ('ses_runtime_pg_resolved', 'wrk_runtime_pg', 'resolved', 'ACTIVE', 'trace_runtime_pg',
                     :now, :resolvedUpdated, 'usr_runtime_pg'),
                    ('ses_runtime_pg_pending', 'wrk_runtime_pg', 'pending', 'ACTIVE', 'trace_runtime_pg',
                     :now, :pendingUpdated, 'usr_runtime_pg'),
                    ('ses_runtime_pg_blank_resolved', 'wrk_runtime_pg', 'blank resolved', 'ACTIVE', 'trace_runtime_pg',
                     :now, :blankUpdated, 'usr_runtime_pg')
                """)
                .param("now", Timestamp.from(NOW))
                .param("resolvedUpdated", Timestamp.from(NOW.plusSeconds(10)))
                .param("pendingUpdated", Timestamp.from(NOW.plusSeconds(20)))
                .param("blankUpdated", Timestamp.from(NOW.plusSeconds(30)))
                .update();
        jdbc.sql("""
                insert into runs(
                    run_id, session_id, workspace_id, status, trace_id, created_at, updated_at, triggered_by_user_id)
                values
                    ('run_runtime_pg_resolved', 'ses_runtime_pg_resolved', 'wrk_runtime_pg', 'RUNNING',
                     'trace_runtime_pg', :now, :resolvedUpdated, 'usr_runtime_pg'),
                    ('run_runtime_pg_pending', 'ses_runtime_pg_pending', 'wrk_runtime_pg', 'RUNNING',
                     'trace_runtime_pg', :now, :pendingUpdated, 'usr_runtime_pg'),
                    ('run_runtime_pg_blank_resolved', 'ses_runtime_pg_blank_resolved', 'wrk_runtime_pg', 'RUNNING',
                     'trace_runtime_pg', :now, :blankUpdated, 'usr_runtime_pg')
                """)
                .param("now", Timestamp.from(NOW))
                .param("resolvedUpdated", Timestamp.from(NOW.plusSeconds(10)))
                .param("pendingUpdated", Timestamp.from(NOW.plusSeconds(20)))
                .param("blankUpdated", Timestamp.from(NOW.plusSeconds(30)))
                .update();
        jdbc.sql("""
                insert into run_events(event_id, run_id, seq, type, trace_id, occurred_at, payload_json)
                values
                    ('evt_runtime_pg_resolved_asked', 'run_runtime_pg_resolved', 1, 'permission.asked',
                     'trace_runtime_pg', :askedAt, '{"id":"permission_resolved"}'),
                    ('evt_runtime_pg_resolved_reply', 'run_runtime_pg_resolved', 2, 'permission.replied',
                     'trace_runtime_pg', :replyAt,
                     '{"sessionID":"ses_remote_pg","requestID":"permission_resolved","reply":"once"}'),
                    ('evt_runtime_pg_pending_asked', 'run_runtime_pg_pending', 1, 'permission.asked',
                     'trace_runtime_pg', :askedAt, '{"id":"permission_pending"}'),
                    ('evt_runtime_pg_pending_other_reply', 'run_runtime_pg_pending', 2, 'permission.replied',
                     'trace_runtime_pg', :replyAt,
                     '{"sessionID":"ses_remote_pg","requestID":"permission_other","reply":"once"}'),
                    ('evt_runtime_pg_blank_asked', 'run_runtime_pg_blank_resolved', 1, 'permission.asked',
                     'trace_runtime_pg', :askedAt, '{"id":"   "}'),
                    ('evt_runtime_pg_blank_reply', 'run_runtime_pg_blank_resolved', 2, 'permission.replied',
                     'trace_runtime_pg', :replyAt, '{"requestID":"\\t"}')
                """)
                .param("askedAt", Timestamp.from(NOW.plusSeconds(5)))
                // 回复 seq 更大但 occurred_at 更早，必须仍然收敛对应 asked。
                .param("replyAt", Timestamp.from(NOW.plusSeconds(4)))
                .update();
    }

    private static SessionRuntimeStateRepository repository(DataSource dataSource) throws Exception {
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties databaseIds = new Properties();
        databaseIds.setProperty("PostgreSQL", "postgresql");
        databaseIdProvider.setProperties(databaseIds);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setDatabaseIdProvider(databaseIdProvider);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        SessionRuntimeStateMapper mapper = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(SessionRuntimeStateMapper.class);
        return new MyBatisSessionRuntimeStateRepository(mapper);
    }
}
