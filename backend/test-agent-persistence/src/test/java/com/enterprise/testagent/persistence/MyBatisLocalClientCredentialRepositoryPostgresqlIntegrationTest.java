package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.localclient.LocalClientCredential;
import com.enterprise.testagent.domain.localclient.LocalClientCredentialStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.LocalClientMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisLocalClientCredentialRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 使用真实 PostgreSQL 验证两个 Java 事务并发消费时只有一个能观察到未展示状态。 */
@Testcontainers(disabledWithoutDocker = true)
class MyBatisLocalClientCredentialRepositoryPostgresqlIntegrationTest {

    private static final UserId USER_ID = new UserId("usr_local_credential_pg");
    private static final Instant CREATED_AT = Instant.parse("2026-08-20T10:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static MyBatisLocalClientCredentialRepository repository;
    private static TransactionTemplate transaction;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcClient.create(dataSource).sql("""
                        insert into users(
                            user_id, unified_auth_id, username, password_hash, status, created_at, updated_at
                        ) values (
                            :userId, 'AUTH_LOCAL_CREDENTIAL_PG', 'local-credential-pg',
                            'hash', 'ACTIVE', :now, :now
                        )
                """)
                .param("userId", USER_ID.value())
                .param("now", Timestamp.from(CREATED_AT))
                .update();

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mybatis/LocalClientMapper.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        LocalClientMapper mapper = new SqlSessionTemplate(sessionFactory).getMapper(LocalClientMapper.class);
        repository = new MyBatisLocalClientCredentialRepository(mapper);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.executeWithoutResult(status -> repository.save(credential(null, CREATED_AT)));
    }

    @Test
    void concurrentConsumersAreSerializedByCredentialRowLock() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> consumeOnce(ready, start, winners));
            Future<?> second = executor.submit(() -> consumeOnce(ready, start, winners));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : List.of(first, second)) future.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(winners).hasValue(1);
        assertThat(repository.findByUserId(USER_ID)).get()
                .extracting(LocalClientCredential::revealedAt)
                .isNotNull();
    }

    private static void consumeOnce(
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger winners) {
        ready.countDown();
        await(start);
        transaction.executeWithoutResult(status -> {
            LocalClientCredential current = repository.findByUserIdForUpdate(USER_ID).orElseThrow();
            if (current.revealedAt() != null) return;
            winners.incrementAndGet();
            Instant revealedAt = CREATED_AT.plusSeconds(300);
            repository.save(credential(revealedAt, revealedAt));
        });
    }

    private static LocalClientCredential credential(Instant revealedAt, Instant updatedAt) {
        return new LocalClientCredential(
                USER_ID,
                "rsa-ciphertext",
                "d".repeat(64),
                "tack_v1_ABC...WXYZ",
                1,
                LocalClientCredentialStatus.ACTIVE,
                CREATED_AT,
                updatedAt,
                revealedAt,
                null);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("latch timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", exception);
        }
    }
}
