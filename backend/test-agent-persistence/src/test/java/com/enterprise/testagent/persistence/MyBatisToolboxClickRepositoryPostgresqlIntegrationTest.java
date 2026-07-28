package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.toolbox.ToolboxClickEvent;
import com.enterprise.testagent.domain.toolbox.ToolboxClickRepository;
import com.enterprise.testagent.domain.toolbox.ToolboxClickWriteResult;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.MyBatisToolboxClickRepository;
import com.enterprise.testagent.persistence.mybatis.ToolboxClickMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 使用真实 PostgreSQL 验证完整迁移链和 30 秒窗口的并发原子竞争。 */
@Testcontainers(disabledWithoutDocker = true)
class MyBatisToolboxClickRepositoryPostgresqlIntegrationTest {

    private static final String CONCURRENT_USER_ID = "usr_toolbox_pg_concurrent";
    private static final String DELETE_USER_ID = "usr_toolbox_pg_delete";
    private static final String TOOL_ID = "it-tools.hash-text";
    private static final String DELETE_TOOL_ID = "omni-tools.text.word-counter";
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static DataSource dataSource;
    private static ToolboxClickRepository repository;
    private static TransactionTemplate transaction;
    private static JdbcClient jdbc;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource postgresDataSource = new PGSimpleDataSource();
        postgresDataSource.setURL(POSTGRES.getJdbcUrl());
        postgresDataSource.setUser(POSTGRES.getUsername());
        postgresDataSource.setPassword(POSTGRES.getPassword());
        dataSource = postgresDataSource;
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(dataSource);
        insertUser(CONCURRENT_USER_ID);
        insertUser(DELETE_USER_ID);
        repository = repository(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private static void insertUser(String userId) {
        jdbc.sql("""
                        insert into users(
                            user_id, unified_auth_id, username, password_hash, status, created_at, updated_at
                        ) values (
                            :userId, :authId, :username, 'hash', 'ACTIVE', :now, :now
                        )
                        """)
                .param("userId", userId)
                .param("authId", "auth_" + userId)
                .param("username", "name_" + userId)
                .param("now", Timestamp.from(NOW))
                .update();
    }

    @Test
    void simultaneousClicksKeepBothEventsButOnlyOneWinsTheWindow() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ToolboxClickWriteResult> first = submitClick(executor, ready, start, "evt_toolbox_pg_first");
            Future<ToolboxClickWriteResult> second = submitClick(executor, ready, start, "evt_toolbox_pg_second");

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<ToolboxClickWriteResult> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(results).allMatch(ToolboxClickWriteResult::recorded);
            assertThat(results).filteredOn(ToolboxClickWriteResult::incremented).hasSize(1);
            assertThat(jdbc.sql("""
                                    select count(*) from toolbox_tool_click_events
                                    where event_id in ('evt_toolbox_pg_first', 'evt_toolbox_pg_second')
                                    """)
                            .query(Long.class)
                            .single())
                    .isEqualTo(2L);
            assertThat(jdbc.sql("select click_count from toolbox_tool_click_totals where tool_id = :toolId")
                            .param("toolId", TOOL_ID)
                            .query(Long.class)
                            .single())
                    .isEqualTo(1L);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void deletingUserAnonymizesPermanentEventsAndCascadesWindowState() {
        String eventId = "evt_toolbox_pg_delete";
        transaction.execute(status -> repository.record(
                event(eventId, DELETE_USER_ID, DELETE_TOOL_ID), Duration.ofSeconds(30)));

        jdbc.sql("delete from users where user_id = :userId")
                .param("userId", DELETE_USER_ID)
                .update();

        assertThat(jdbc.sql("select count(*) from toolbox_tool_click_events where event_id = :eventId")
                        .param("eventId", eventId)
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
        assertThat(jdbc.sql("""
                                select count(*) from toolbox_tool_click_events
                                where event_id = :eventId and user_id is null
                                """)
                        .param("eventId", eventId)
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
        assertThat(jdbc.sql("""
                                select count(*) from toolbox_tool_user_click_states
                                where user_id = :userId
                                """)
                        .param("userId", DELETE_USER_ID)
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(jdbc.sql("select click_count from toolbox_tool_click_totals where tool_id = :toolId")
                        .param("toolId", DELETE_TOOL_ID)
                        .query(Long.class)
                        .single())
                .isPositive();
    }

    private static Future<ToolboxClickWriteResult> submitClick(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            String eventId) {
        return executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return transaction.execute(status -> repository.record(
                    event(eventId, CONCURRENT_USER_ID, TOOL_ID), Duration.ofSeconds(30)));
        });
    }

    private static ToolboxClickEvent event(String eventId, String userId, String toolId) {
        return new ToolboxClickEvent(
                eventId,
                toolId,
                toolId.startsWith("it-tools.") ? "IT_TOOLS" : "OMNI_TOOLS",
                new UserId(userId),
                "trace_toolbox_pg",
                NOW,
                false);
    }

    private static ToolboxClickRepository repository(DataSource dataSource) throws Exception {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties databaseIds = new Properties();
        databaseIds.setProperty("PostgreSQL", "postgresql");
        provider.setProperties(databaseIds);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setDatabaseIdProvider(provider);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        ToolboxClickMapper mapper = new SqlSessionTemplate(factory).getMapper(ToolboxClickMapper.class);
        return new MyBatisToolboxClickRepository(mapper);
    }
}
