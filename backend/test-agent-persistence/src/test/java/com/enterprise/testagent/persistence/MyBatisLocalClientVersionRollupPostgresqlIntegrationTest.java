package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import com.enterprise.testagent.persistence.mybatis.LocalClientVersionMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisLocalClientVersionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

/** 真实 PostgreSQL 验证同一 rollout 的并发迟到终态最终按行锁串行汇总。 */
@Testcontainers(disabledWithoutDocker = true)
class MyBatisLocalClientVersionRollupPostgresqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
    private static final String ROLLOUT_ID = "lcrl_concurrent_deadline_correction";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withStartupTimeout(Duration.ofMinutes(3));

    private static JdbcClient jdbc;
    private static MyBatisLocalClientVersionRepository repository;
    private static TransactionTemplate transaction;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        jdbc = JdbcClient.create(dataSource);
        createSchema(jdbc);
        repository = repository(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void seed() {
        jdbc.sql("delete from local_client_update_attempts").update();
        jdbc.sql("delete from local_client_update_rollouts").update();
        jdbc.sql("""
                        insert into local_client_update_rollouts(
                            rollout_id, rollout_scope, requested_user_id, status,
                            created_by, created_at, completed_at
                        ) values (
                            :rolloutId, 'ALL_ONLINE', null, 'PARTIAL_FAILED',
                            'usr_rollout_admin', :createdAt, :completedAt
                        )
                        """)
                .param("rolloutId", ROLLOUT_ID)
                .param("createdAt", NOW.minusSeconds(3600))
                .param("completedAt", NOW.minusSeconds(60))
                .update();
        insertDeadlineAttempt("lcuc_concurrent_deadline_a", "lci_concurrent_deadline_a");
        insertDeadlineAttempt("lcuc_concurrent_deadline_b", "lci_concurrent_deadline_b");
    }

    @Test
    void concurrentDeadlineCorrectionsConvergeAttemptsAndRolloutToCompleted() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch transitioned = new CountDownLatch(2);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondAttemptingLock = new CountDownLatch(1);
        CountDownLatch secondRead = new CountDownLatch(1);
        CountDownLatch firstRead = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> correctFirstAndFinalize(
                    ready,
                    start,
                    transitioned,
                    firstLocked,
                    secondAttemptingLock,
                    secondRead,
                    firstRead));
            Future<?> second = executor.submit(() -> correctSecondAndFinalize(
                    ready,
                    start,
                    transitioned,
                    firstLocked,
                    secondAttemptingLock,
                    secondRead,
                    firstRead));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(jdbc.sql("""
                        select status from local_client_update_attempts
                        where rollout_id = :rolloutId order by command_id
                        """)
                .param("rolloutId", ROLLOUT_ID)
                .query(String.class)
                .list()).containsExactly("SUCCEEDED", "SUCCEEDED");
        assertThat(jdbc.sql("select status from local_client_update_rollouts where rollout_id = :rolloutId")
                .param("rolloutId", ROLLOUT_ID)
                .query(String.class)
                .single()).isEqualTo("COMPLETED");
    }

    private static void correctFirstAndFinalize(
            CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch transitioned,
            CountDownLatch firstLocked,
            CountDownLatch secondAttemptingLock,
            CountDownLatch secondRead,
            CountDownLatch firstRead) {
        ready.countDown();
        await(start);
        transaction.executeWithoutResult(status -> {
            correctAttempt("lcuc_concurrent_deadline_a", transitioned);
            repository.findRolloutForUpdate(ROLLOUT_ID).orElseThrow();
            firstLocked.countDown();
            await(secondAttemptingLock);
            // 没有 FOR UPDATE 时让第二事务先完成读取，强制双方都只看见另一条旧 FAILED；有行锁时这里会按时返回 false。
            awaitAtMost(secondRead, 500, TimeUnit.MILLISECONDS);
            finalizeFromPersistedAttempts(firstRead);
        });
    }

    private static void correctSecondAndFinalize(
            CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch transitioned,
            CountDownLatch firstLocked,
            CountDownLatch secondAttemptingLock,
            CountDownLatch secondRead,
            CountDownLatch firstRead) {
        ready.countDown();
        await(start);
        transaction.executeWithoutResult(status -> {
            correctAttempt("lcuc_concurrent_deadline_b", transitioned);
            await(firstLocked);
            secondAttemptingLock.countDown();
            repository.findRolloutForUpdate(ROLLOUT_ID).orElseThrow();
            List<LocalClientVersionModels.Attempt> attempts = repository.findAttemptsByRollout(ROLLOUT_ID);
            secondRead.countDown();
            await(firstRead);
            completeRollout(attempts);
        });
    }

    private static void correctAttempt(String commandId, CountDownLatch transitioned) {
        assertThat(repository.transitionAttempt(
                commandId,
                "FAILED",
                "SUCCEEDED",
                "a".repeat(64),
                null,
                NOW)).isTrue();
        transitioned.countDown();
        await(transitioned);
    }

    private static void finalizeFromPersistedAttempts(CountDownLatch firstRead) {
        List<LocalClientVersionModels.Attempt> attempts = repository.findAttemptsByRollout(ROLLOUT_ID);
        firstRead.countDown();
        completeRollout(attempts);
    }

    private static void completeRollout(List<LocalClientVersionModels.Attempt> attempts) {
        boolean failed = attempts.stream().anyMatch(attempt ->
                attempt.status() != LocalClientVersionModels.AttemptStatus.SUCCEEDED);
        repository.completeRollout(
                ROLLOUT_ID,
                failed
                        ? LocalClientVersionModels.RolloutStatus.PARTIAL_FAILED.name()
                        : LocalClientVersionModels.RolloutStatus.COMPLETED.name(),
                NOW);
    }

    private void insertDeadlineAttempt(String commandId, String clientInstanceId) {
        jdbc.sql("""
                        insert into local_client_update_attempts(
                            command_id, rollout_id, client_instance_id, user_id,
                            connection_generation, policy_revision, current_version, target_version,
                            direction, status, release_digest, error_code,
                            created_at, updated_at, completed_at
                        ) values (
                            :commandId, :rolloutId, :clientInstanceId, 'usr_rollout_client',
                            7, 11, '20260820180000', '20260820190000',
                            'UPDATE', 'FAILED', :releaseDigest, 'DELIVERY_DEADLINE_EXCEEDED',
                            :createdAt, :updatedAt, :completedAt
                        )
                        """)
                .param("commandId", commandId)
                .param("rolloutId", ROLLOUT_ID)
                .param("clientInstanceId", clientInstanceId)
                .param("releaseDigest", "a".repeat(64))
                .param("createdAt", NOW.minusSeconds(3600))
                .param("updatedAt", NOW.minusSeconds(60))
                .param("completedAt", NOW.minusSeconds(60))
                .update();
    }

    private static MyBatisLocalClientVersionRepository repository(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mybatis/LocalClientVersionMapper.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        LocalClientVersionMapper mapper = new SqlSessionTemplate(sessionFactory).getMapper(LocalClientVersionMapper.class);
        return new MyBatisLocalClientVersionRepository(mapper);
    }

    private static void createSchema(JdbcClient jdbc) {
        jdbc.sql("""
                        create table local_client_update_rollouts (
                            rollout_id varchar(128) primary key,
                            rollout_scope varchar(32) not null,
                            requested_user_id varchar(128),
                            status varchar(32) not null,
                            created_by varchar(128) not null,
                            created_at timestamp with time zone not null,
                            completed_at timestamp with time zone
                        )
                        """).update();
        jdbc.sql("""
                        create table local_client_update_attempts (
                            command_id varchar(128) primary key,
                            rollout_id varchar(128) not null,
                            client_instance_id varchar(128) not null,
                            user_id varchar(128) not null,
                            connection_generation bigint not null,
                            policy_revision bigint not null,
                            current_version varchar(64) not null,
                            target_version varchar(64) not null,
                            direction varchar(16) not null,
                            status varchar(32) not null,
                            release_digest varchar(64),
                            error_code varchar(128),
                            created_at timestamp with time zone not null,
                            updated_at timestamp with time zone not null,
                            completed_at timestamp with time zone
                        )
                        """).update();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发测试等待超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试被中断", exception);
        }
    }

    private static boolean awaitAtMost(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            return latch.await(timeout, unit);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试被中断", exception);
        }
    }
}
