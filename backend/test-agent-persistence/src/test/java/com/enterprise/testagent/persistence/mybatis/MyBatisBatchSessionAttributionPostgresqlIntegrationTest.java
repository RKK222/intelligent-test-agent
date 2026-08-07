package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.session.BatchSessionAttributionRepository;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 使用真实 PostgreSQL 验证批量会话归因迁移、唯一约束和事务级串行锁。 */
@Testcontainers(disabledWithoutDocker = true)
class MyBatisBatchSessionAttributionPostgresqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-07T15:00:00Z");
    private static final UserId USER_A = new UserId("usr_batch_pg_a");
    private static final UserId USER_B = new UserId("usr_batch_pg_b");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static BatchSessionAttributionRepository repository;
    private static TransactionTemplate transaction;
    private static JdbcClient jdbc;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = JdbcClient.create(dataSource);
        seedData();
        repository = repository(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void migrationDefaultsConstraintsAndIndexesPreserveUserScopedIdempotency() {
        Map<String, Object> ordinary = jdbc.sql("""
                        select batch_mode, batch_id, batch_item_request_id
                        from sessions where session_id='ses_batch_pg_ordinary'
                        """)
                .query()
                .singleRow();
        assertThat(ordinary.get("batch_mode")).isEqualTo(false);
        assertThat(ordinary.get("batch_id")).isNull();
        assertThat(ordinary.get("batch_item_request_id")).isNull();

        boolean marked = transaction.execute(status -> repository.markBatch(
                new SessionId("ses_batch_pg_a1"), USER_A, "batch_pg", "item_shared"));
        assertThat(marked).isTrue();
        assertThat(repository.findSessionId(USER_A, "item_shared"))
                .contains(new SessionId("ses_batch_pg_a1"));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> repository.markBatch(
                        new SessionId("ses_batch_pg_a2"), USER_A, "batch_pg", "item_shared")))
                .isInstanceOf(DataIntegrityViolationException.class);
        boolean crossUserMarked = Boolean.TRUE.equals(transaction.execute(status -> repository.markBatch(
                new SessionId("ses_batch_pg_b1"), USER_B, "batch_pg", "item_shared")));
        assertThat(crossUserMarked).isTrue();

        assertThatThrownBy(() -> jdbc.sql("""
                                update sessions set batch_mode=true, batch_id=null,
                                    batch_item_request_id='item_invalid'
                                where session_id='ses_batch_pg_ordinary'
                                """)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.sql("""
                                select indexname from pg_indexes
                                where tablename='sessions'
                                  and indexname in ('uk_sessions_batch_item_request', 'idx_sessions_batch_created')
                                """)
                        .query(String.class)
                        .list())
                .containsExactlyInAnyOrder("uk_sessions_batch_item_request", "idx_sessions_batch_created");
    }

    @Test
    void advisoryLockSerializesTheSameUserAndItemRequest() throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondLocked = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                repository.lockCreateRequest(USER_A, "item_lock");
                firstLocked.countDown();
                await(releaseFirst);
            }));
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> transaction.executeWithoutResult(status -> {
                repository.lockCreateRequest(USER_A, "item_lock");
                secondLocked.countDown();
            }));
            assertThat(secondLocked.await(300, TimeUnit.MILLISECONDS)).isFalse();
            releaseFirst.countDown();

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(secondLocked.getCount()).isZero();
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待测试并发锁时被中断", exception);
        }
    }

    private static void seedData() {
        for (UserId userId : new UserId[] {USER_A, USER_B}) {
            jdbc.sql("""
                            insert into users(
                                user_id, unified_auth_id, username, password_hash, status, created_at, updated_at
                            ) values (:userId, :authId, :username, 'hash', 'ACTIVE', :now, :now)
                            """)
                    .param("userId", userId.value())
                    .param("authId", "auth_" + userId.value())
                    .param("username", "name_" + userId.value())
                    .param("now", Timestamp.from(NOW))
                    .update();
        }
        jdbc.sql("""
                        insert into workspaces(
                            workspace_id, name, root_path, status, trace_id, created_at, updated_at
                        ) values ('wrk_batch_pg', '批量测试工作区', '/repo/batch', 'ACTIVE',
                            'trace_batch_pg', :now, :now)
                        """)
                .param("now", Timestamp.from(NOW))
                .update();
        jdbc.sql("""
                        insert into sessions(
                            session_id, workspace_id, title, status, created_by_user_id,
                            trace_id, created_at, updated_at
                        ) values
                            ('ses_batch_pg_ordinary', 'wrk_batch_pg', '普通会话', 'ACTIVE', :userA,
                             'trace_batch_pg', :now, :now),
                            ('ses_batch_pg_a1', 'wrk_batch_pg', '批量 A1', 'ACTIVE', :userA,
                             'trace_batch_pg', :now, :now),
                            ('ses_batch_pg_a2', 'wrk_batch_pg', '批量 A2', 'ACTIVE', :userA,
                             'trace_batch_pg', :now, :now),
                            ('ses_batch_pg_b1', 'wrk_batch_pg', '批量 B1', 'ACTIVE', :userB,
                             'trace_batch_pg', :now, :now)
                        """)
                .param("userA", USER_A.value())
                .param("userB", USER_B.value())
                .param("now", Timestamp.from(NOW))
                .update();
    }

    private static BatchSessionAttributionRepository repository(DataSource dataSource) throws Exception {
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties databaseIds = new Properties();
        databaseIds.setProperty("PostgreSQL", "postgresql");
        databaseIdProvider.setProperties(databaseIds);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setDatabaseIdProvider(databaseIdProvider);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        BatchSessionAttributionMapper mapper = new SqlSessionTemplate(factory)
                .getMapper(BatchSessionAttributionMapper.class);
        return new MyBatisBatchSessionAttributionRepository(mapper);
    }
}
