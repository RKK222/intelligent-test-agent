package com.enterprise.testagent.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import com.enterprise.testagent.domain.localclient.LocalClientVersionRepository;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.notification.UserNotificationApplicationService;
import com.enterprise.testagent.notification.UserNotificationRealtimeHub;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientUpdateTerminalService;
import com.enterprise.testagent.persistence.mybatis.LocalClientMapper;
import com.enterprise.testagent.persistence.mybatis.LocalClientVersionMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisLocalClientInstanceRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisLocalClientVersionRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisUserNotificationRepository;
import com.enterprise.testagent.persistence.mybatis.UserNotificationMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 真实 PostgreSQL 从生产终态事务门面验证并发 attempt 与 rollout 汇总原子收敛。 */
@Testcontainers(disabledWithoutDocker = true)
class LocalClientUpdateTerminalTransactionPostgresqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
    private static final String ROLLOUT_ID = "lcrl_terminal_service_concurrent";
    private static final UserId USER_ID = new UserId("usr_terminal_service");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withStartupTimeout(Duration.ofMinutes(3));

    private static JdbcClient jdbc;
    private static LocalClientVersionRepository versions;
    private static LocalClientUpdateTerminalService terminalService;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        jdbc = JdbcClient.create(dataSource);
        createSchema(jdbc);

        SqlSessionFactory sessionFactory = sessionFactory(dataSource);
        SqlSessionTemplate sessions = new SqlSessionTemplate(sessionFactory);
        versions = new MyBatisLocalClientVersionRepository(sessions.getMapper(LocalClientVersionMapper.class));
        LocalClientInstanceRepository instances =
                new MyBatisLocalClientInstanceRepository(sessions.getMapper(LocalClientMapper.class));
        MyBatisUserNotificationRepository notificationRepository =
                new MyBatisUserNotificationRepository(sessions.getMapper(UserNotificationMapper.class));
        UserNotificationApplicationService notifications = new UserNotificationApplicationService(
                notificationRepository,
                new UserNotificationRealtimeHub(new NoopBroadcastPublisher(), new TestBackendIdentity()));
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        terminalService = new LocalClientUpdateTerminalService(
                versions,
                instances,
                notifications,
                transactionManager,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @BeforeEach
    void seed() {
        jdbc.sql("delete from user_notifications").update();
        jdbc.sql("delete from local_client_update_attempts").update();
        jdbc.sql("delete from local_client_update_rollouts").update();
        jdbc.sql("delete from local_client_instances").update();
        jdbc.sql("delete from users").update();
        insertUser();
        insertInstance("lci_terminal_service_a");
        insertInstance("lci_terminal_service_b");
        jdbc.sql("""
                        insert into local_client_update_rollouts(
                            rollout_id, rollout_scope, requested_user_id, status,
                            created_by, created_at, completed_at
                        ) values (
                            :rolloutId, 'ALL_ONLINE', null, 'PARTIAL_FAILED',
                            :userId, :createdAt, :completedAt
                        )
                """)
                .param("rolloutId", ROLLOUT_ID)
                .param("userId", USER_ID.value())
                .param("createdAt", Timestamp.from(NOW.minusSeconds(3600)))
                .param("completedAt", Timestamp.from(NOW.minusSeconds(60)))
                .update();
        insertDeadlineAttempt("lcuc_terminal_service_a", "lci_terminal_service_a");
        insertDeadlineAttempt("lcuc_terminal_service_b", "lci_terminal_service_b");
    }

    @Test
    void concurrentDeadlineCorrectionsThroughProductionServiceConvergeToCompleted() throws Exception {
        LocalClientVersionModels.Attempt firstAttempt =
                versions.findAttempt("lcuc_terminal_service_a").orElseThrow();
        LocalClientVersionModels.Attempt secondAttempt =
                versions.findAttempt("lcuc_terminal_service_b").orElseThrow();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> converge(firstAttempt, ready, start));
            Future<?> second = executor.submit(() -> converge(secondAttempt, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
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

    private static void converge(
            LocalClientVersionModels.Attempt attempt,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        await(start);
        assertThat(terminalService.convergeDeadlineFailure(
                attempt,
                LocalClientVersionModels.AttemptStatus.SUCCEEDED,
                null,
                "trace_terminal_service_pg").acknowledgement()).isNotNull();
    }

    private void insertUser() {
        jdbc.sql("""
                        insert into users(
                            user_id, unified_auth_id, username, password_hash, status, created_at, updated_at
                        ) values (
                            :userId, 'ucid-terminal-service', 'terminal-service-user', 'hash',
                            'ACTIVE', :now, :now
                        )
                """)
                .param("userId", USER_ID.value())
                .param("now", Timestamp.from(NOW.minusSeconds(7200)))
                .update();
    }

    private void insertInstance(String instanceId) {
        jdbc.sql("""
                        insert into local_client_instances(
                            client_instance_id, user_id, client_name, platform, architecture,
                            client_version, opencode_version, launcher_version,
                            self_update_capabilities, self_update_supported,
                            created_at, updated_at, last_connected_at
                        ) values (
                            :instanceId, :userId, '麒麟并发客户端', 'linux', 'arm64',
                            '20260820190000', '1.18.4', '1', 'SELF_UPDATE_V1', true,
                            :createdAt, :updatedAt, :updatedAt
                        )
                """)
                .param("instanceId", instanceId)
                .param("userId", USER_ID.value())
                .param("createdAt", Timestamp.from(NOW.minusSeconds(3600)))
                .param("updatedAt", Timestamp.from(NOW.minusSeconds(60)))
                .update();
    }

    private void insertDeadlineAttempt(String commandId, String clientInstanceId) {
        jdbc.sql("""
                        insert into local_client_update_attempts(
                            command_id, rollout_id, client_instance_id, user_id,
                            connection_generation, policy_revision, current_version, target_version,
                            direction, status, release_digest, error_code,
                            created_at, updated_at, completed_at
                        ) values (
                            :commandId, :rolloutId, :clientInstanceId, :userId,
                            7, 11, '20260820180000', '20260820190000',
                            'UPDATE', 'FAILED', :releaseDigest, 'DELIVERY_DEADLINE_EXCEEDED',
                            :createdAt, :updatedAt, :completedAt
                        )
                        """)
                .param("commandId", commandId)
                .param("rolloutId", ROLLOUT_ID)
                .param("clientInstanceId", clientInstanceId)
                .param("userId", USER_ID.value())
                .param("releaseDigest", "a".repeat(64))
                .param("createdAt", Timestamp.from(NOW.minusSeconds(3600)))
                .param("updatedAt", Timestamp.from(NOW.minusSeconds(60)))
                .param("completedAt", Timestamp.from(NOW.minusSeconds(60)))
                .update();
    }

    private static SqlSessionFactory sessionFactory(DataSource dataSource) throws Exception {
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        java.util.Properties databaseIds = new java.util.Properties();
        databaseIds.setProperty("PostgreSQL", "postgresql");
        databaseIdProvider.setProperties(databaseIds);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setDatabaseIdProvider(databaseIdProvider);
        factory.setMapperLocations(
                new org.springframework.core.io.ClassPathResource("mybatis/LocalClientMapper.xml"),
                new org.springframework.core.io.ClassPathResource("mybatis/LocalClientVersionMapper.xml"),
                new org.springframework.core.io.ClassPathResource("mybatis/UserNotificationMapper.xml"));
        return factory.getObject();
    }

    private static void createSchema(JdbcClient jdbc) {
        jdbc.sql("""
                        create table users (
                            id bigint generated by default as identity primary key,
                            user_id varchar(128) not null unique,
                            unified_auth_id varchar(255) not null unique,
                            username varchar(128) not null unique,
                            password_hash varchar(255) not null,
                            status varchar(32) not null,
                            created_at timestamp not null,
                            updated_at timestamp not null
                        )
                        """).update();
        jdbc.sql("""
                        create table local_client_instances (
                            client_instance_id varchar(128) primary key,
                            user_id varchar(128) not null references users(user_id),
                            client_name varchar(255) not null,
                            platform varchar(64) not null,
                            architecture varchar(64) not null,
                            client_version varchar(64) not null,
                            opencode_version varchar(64) not null,
                            launcher_version varchar(32),
                            self_update_capabilities text not null default '',
                            self_update_supported boolean not null default false,
                            last_update_status varchar(32),
                            last_update_target_version varchar(14),
                            last_update_at timestamp with time zone,
                            created_at timestamp with time zone not null,
                            updated_at timestamp with time zone not null,
                            last_connected_at timestamp with time zone,
                            last_disconnected_at timestamp with time zone
                        )
                        """).update();
        jdbc.sql("""
                        create table local_client_update_rollouts (
                            rollout_id varchar(128) primary key,
                            rollout_scope varchar(32) not null,
                            requested_user_id varchar(128),
                            status varchar(32) not null,
                            created_by varchar(128) not null references users(user_id),
                            created_at timestamp with time zone not null,
                            completed_at timestamp with time zone
                        )
                        """).update();
        jdbc.sql("""
                        create table local_client_update_attempts (
                            command_id varchar(128) primary key,
                            rollout_id varchar(128) not null references local_client_update_rollouts(rollout_id),
                            client_instance_id varchar(128) not null references local_client_instances(client_instance_id),
                            user_id varchar(128) not null references users(user_id),
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
        jdbc.sql("""
                        create table user_notifications (
                            id bigserial primary key,
                            notification_id varchar(96) not null unique,
                            recipient_user_id varchar(128) not null references users(user_id),
                            type varchar(64) not null,
                            actor_user_id varchar(128),
                            title varchar(200) not null,
                            body varchar(500) not null,
                            action_type varchar(64) not null,
                            action_target_id varchar(128) not null,
                            dedup_key varchar(256) not null unique,
                            status varchar(32) not null,
                            invalidation_reason varchar(128),
                            expires_at timestamp with time zone,
                            read_at timestamp with time zone,
                            invalidated_at timestamp with time zone,
                            trace_id varchar(128) not null,
                            created_at timestamp with time zone not null,
                            updated_at timestamp with time zone not null
                        )
                        """).update();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发终态测试等待超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发终态测试被中断", exception);
        }
    }

    private static final class NoopBroadcastPublisher implements ServerBroadcastPublisher {
        @Override
        public void publish(ServerBroadcastEvent event) {
            // 集成测试只验证数据库事务，不观察实时广播。
        }
    }

    private static final class TestBackendIdentity implements BackendInstanceIdentity {
        @Override
        public String instanceId() {
            return "backend-terminal-service-test";
        }

        @Override
        public String linuxServerId() {
            return "linux-terminal-service-test";
        }

        @Override
        public String backendProcessId() {
            return "bjp_terminal_service_test";
        }

        @Override
        public String listenUrl() {
            return "http://127.0.0.1:8080";
        }
    }
}
