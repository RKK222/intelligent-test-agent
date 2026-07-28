package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.appsource.AppSourceCleanupStatus;
import com.enterprise.testagent.domain.appsource.AppSourceCleanupTask;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStep;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.appsource.AppSourceStepScope;
import com.enterprise.testagent.domain.appsource.AppSourceStepStatus;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 原样执行生产 PostgreSQL migration，验证 JSONB、部分唯一索引和延迟外键。 */
@Testcontainers(disabledWithoutDocker = true)
class MyBatisAppSourcePostgresqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-28T02:00:00Z");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_app_source_pg");
    private static final ApplicationId APP_ID = new ApplicationId("app_source_pg");
    private static final UserId USER_ID = new UserId("usr_app_source_pg");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-app-source-pg");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static DataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;
    private static MyBatisAppSourceRepository repository;
    private static TransactionTemplate transactionTemplate;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        postgres.setURL(POSTGRES.getJdbcUrl());
        postgres.setUser(POSTGRES.getUsername());
        postgres.setPassword(POSTGRES.getPassword());
        dataSource = postgres;
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        insertBaseRows();
        sqlSessionFactory = new MyBatisPersistenceConfig().sqlSessionFactory(dataSource);
        repository = new MyBatisAppSourceRepository(
                new SqlSessionTemplate(sqlSessionFactory).getMapper(AppSourceMapper.class));
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void cleanupMayBeInsertedBeforeDeferredParentsAndStepsKeepScopeUniqueness() {
        Boolean repositoryLocked = transactionTemplate.execute(
                ignored -> repository.lockRepositoryForAppSource(REPOSITORY_ID));
        assertThat(repositoryLocked).isTrue();
        assertThat(repository.insertSlotIfAbsent(slot())).isTrue();
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            // 业务事务第一写先落 cleanup；operation/snapshot 在同一事务稍后补齐，提交时才校验延迟外键。
            repository.insertCleanupTasks(List.of(cleanup()));
            repository.saveOperation(operation());
            repository.saveSnapshot(snapshot());
        });

        assertThat(repository.findCleanupTasks(REPOSITORY_ID, 1L, SERVER_ID)).containsExactly(cleanup());
        assertThat(repository.insertReplicaIfAbsent(new AppSourceReplica(
                REPOSITORY_ID, 1L, SERVER_ID, null, AppSourceReplicaStatus.PENDING,
                null, null, 0, null, null, null, NOW, NOW))).isTrue();
        assertThat(repository.markReplicaCleaned(REPOSITORY_ID, 1L, SERVER_ID, NOW.plusSeconds(1))).isTrue();
        assertThat(repository.findReplica(REPOSITORY_ID, 1L, SERVER_ID))
                .hasValueSatisfying(replica -> assertThat(replica.status()).isEqualTo(AppSourceReplicaStatus.CLEANED));
        assertThat(repository.findDueCleanupTasks(SERVER_ID, NOW, 10)).isEmpty();
        assertThat(repository.makeCleanupDueNow(REPOSITORY_ID, 1L, NOW)).isOne();
        assertThat(repository.findDueCleanupTasks(SERVER_ID, NOW, 10)).singleElement().satisfies(due -> {
            assertThat(due.cleanupTaskId()).isEqualTo("cleanup-app-source-pg");
            assertThat(due.deleteAt()).isEqualTo(NOW);
            assertThat(due.nextRetryAt()).isEqualTo(NOW);
        });
        assertThat(repository.claimCleanupTask(
                "cleanup-app-source-pg", "cleaner-pg-a", NOW.plusSeconds(30), NOW)).isPresent();
        assertThat(repository.claimCleanupTask(
                "cleanup-app-source-pg", "cleaner-pg-b", NOW.plusSeconds(30), NOW)).isEmpty();
        assertThat(repository.upsertStep(step("step-global-a", AppSourceStepStatus.PENDING))).isTrue();
        assertThat(repository.upsertStep(step("step-global-a", AppSourceStepStatus.RUNNING))).isTrue();
        assertThat(repository.upsertStep(step("step-global-a", AppSourceStepStatus.SUCCEEDED))).isTrue();
        assertThat(repository.upsertStep(step("step-global-a", AppSourceStepStatus.RUNNING))).isFalse();
        assertThat(repository.findSteps("op-app-source-pg")).singleElement()
                .extracting(AppSourceOperationStep::status)
                .isEqualTo(AppSourceStepStatus.SUCCEEDED);
        assertThatThrownBy(() -> repository.upsertStep(step("step-global-b")))
                .isInstanceOf(RuntimeException.class);

        JdbcClient jdbc = JdbcClient.create(dataSource);
        assertThat(jdbc.sql("select pg_typeof(selected_paths_json)::text from app_source_snapshots "
                        + "where repository_id = :repositoryId and generation = 1")
                .param("repositoryId", REPOSITORY_ID.value())
                .query(String.class).single()).isEqualTo("jsonb");
        assertThat(jdbc.sql("select parameter_value from common_parameters "
                        + "where parameter_english = 'OPENCODE_APP_SOURCE_ROOT' and platform = 'all'")
                .query(String.class).single())
                .isEqualTo("${SYS_DATA_ROOT_DIR}/agent-opencode/workspace/appsource/");

        assertThat(jdbc.sql("update app_source_snapshots set expires_at = :expiresAt "
                        + "where repository_id = :repositoryId and generation = 1")
                .param("expiresAt", Timestamp.from(NOW.plusSeconds(3600L)))
                .param("repositoryId", REPOSITORY_ID.value()).update()).isOne();
        assertThat(jdbc.sql("update app_source_snapshots set expires_at = :expiresAt "
                        + "where repository_id = :repositoryId and generation = 1")
                .param("expiresAt", Timestamp.from(NOW.plusSeconds(72L * 3600L)))
                .param("repositoryId", REPOSITORY_ID.value()).update()).isOne();
        assertThatThrownBy(() -> jdbc.sql("update app_source_snapshots set expires_at = :expiresAt "
                        + "where repository_id = :repositoryId and generation = 1")
                .param("expiresAt", Timestamp.from(NOW.plusSeconds(73L * 3600L)))
                .param("repositoryId", REPOSITORY_ID.value()).update())
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.sql("update app_source_snapshots set expires_at = :expiresAt "
                        + "where repository_id = :repositoryId and generation = 1")
                .param("expiresAt", Timestamp.from(NOW.plusSeconds(90L * 60L)))
                .param("repositoryId", REPOSITORY_ID.value()).update())
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.sql("update app_source_snapshots set index_sha256 = :indexSha256 "
                        + "where repository_id = :repositoryId and generation = 1")
                .param("indexSha256", "g".repeat(64))
                .param("repositoryId", REPOSITORY_ID.value()).update())
                .isInstanceOf(RuntimeException.class);
    }

    private static void insertBaseRows() {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                values (:userId, 'auth-app-source-pg', 'app-source-pg', 'hash', 'ACTIVE', :now, :now)
                """).param("userId", USER_ID.value()).param("now", Timestamp.from(NOW)).update();
        jdbc.sql("""
                insert into applications(app_id, app_name, enabled, created_at, updated_at)
                values (:appId, '应用源码 PostgreSQL', true, :now, :now)
                """).param("appId", APP_ID.value()).param("now", Timestamp.from(NOW)).update();
        jdbc.sql("""
                insert into code_repositories(
                    repository_id, git_url, name, english_name, repository_type,
                    deployment_mode, standard, created_at, updated_at)
                values (:repositoryId, 'https://git.example.test/app-source-pg.git', '源码库', 'source-pg',
                    'APPLICATION_CODE_REPOSITORY', 'EXTERNAL', false, :now, :now)
                """).param("repositoryId", REPOSITORY_ID.value()).param("now", Timestamp.from(NOW)).update();
        jdbc.sql("""
                insert into linux_servers(
                    linux_server_id, name, status, capacity_summary_json, last_heartbeat_at,
                    trace_id, created_at, updated_at)
                values (:serverId, 'Server PG', 'ONLINE', '{}', :now, 'trace-server-pg', :now, :now)
                """).param("serverId", SERVER_ID.value()).param("now", Timestamp.from(NOW)).update();
    }

    private static AppSourceRepositorySlot slot() {
        return new AppSourceRepositorySlot(REPOSITORY_ID, null, null, 1L, null, 0L, NOW, NOW);
    }

    private static AppSourceSnapshot snapshot() {
        return new AppSourceSnapshot(
                REPOSITORY_ID, 1L, "source-pg", AppSourcePurpose.PERSONAL, USER_ID,
                "main", "abcdef", List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                null, NOW, NOW.plusSeconds(48L * 3600L), AppSourceSnapshotStatus.PENDING, NOW, NOW);
    }

    private static AppSourceOperation operation() {
        return new AppSourceOperation(
                "op-app-source-pg", APP_ID, REPOSITORY_ID, null, 1L, USER_ID,
                AppSourceOperationType.DOWNLOAD, "request-pg", AppSourceOperationStatus.PENDING,
                "trace-app-source-pg", NOW, null);
    }

    private static AppSourceCleanupTask cleanup() {
        return new AppSourceCleanupTask(
                "cleanup-app-source-pg", "op-app-source-pg", REPOSITORY_ID, 1L, SERVER_ID,
                NOW.plusSeconds(5), AppSourceCleanupStatus.PENDING, null, null, 0, null,
                null, null, "trace-app-source-pg", NOW, NOW);
    }

    private static AppSourceOperationStep step(String stepId) {
        return step(stepId, AppSourceStepStatus.PENDING);
    }

    private static AppSourceOperationStep step(String stepId, AppSourceStepStatus status) {
        return new AppSourceOperationStep(
                stepId, "op-app-source-pg", AppSourceStepScope.GLOBAL, null,
                "RESOLVE_COMMIT", 10, status, null,
                status == AppSourceStepStatus.PENDING ? null : NOW,
                status == AppSourceStepStatus.SUCCEEDED ? NOW.plusSeconds(1) : null,
                status == AppSourceStepStatus.SUCCEEDED ? NOW.plusSeconds(1) : NOW);
    }
}
