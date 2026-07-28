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
import com.enterprise.testagent.domain.appsource.AppSourceRecentSelection;
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
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 真实 Flyway 与 MyBatis XML 验证应用源码的 generation、乐观锁和租约 fencing。 */
class MyBatisAppSourceRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-28T02:00:00Z");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_app_source");
    private static final ApplicationId APP_ID = new ApplicationId("app_source");
    private static final UserId USER_ID = new UserId("usr_app_source");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-a");

    private SingleConnectionDataSource schemaDataSource;
    private JdbcClient jdbcClient;
    private SqlSessionFactory sqlSessionFactory;
    private MyBatisAppSourceRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        String url = "jdbc:h2:mem:testagent_app_source_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                .formatted(UUID.randomUUID().toString().replace("-", ""));
        h2.setURL(url);
        h2.setUser("sa");
        h2.setPassword("");
        schemaDataSource = new SingleConnectionDataSource(url, "sa", "", true);
        Flyway.configure()
                .dataSource(schemaDataSource)
                .locations("classpath:db/migration")
                .target("20260715213000")
                .load()
                .migrate();
        String h2Migration = new ClassPathResource(
                "db/migration/V20260728103000__create_app_source_snapshot_tables.sql")
                .getContentAsString(StandardCharsets.UTF_8)
                .replace("jsonb", "json")
                .replace("deferrable initially deferred", "")
                .replace("accepted_at + interval '1 hour'", "dateadd('hour', 1, accepted_at)")
                .replace("accepted_at + interval '72 hours'", "dateadd('hour', 72, accepted_at)")
                .replace("mod(extract(epoch from (expires_at - accepted_at)), 3600)",
                        "mod(datediff('second', accepted_at, expires_at), 3600)")
                .replaceAll("(?s)create unique index uk_app_source_snapshots_active.*?;", "")
                .replaceAll("(?s)create unique index uk_app_source_steps_global.*?;", "")
                .replaceAll("(?s)create unique index uk_app_source_steps_server.*?;", "");
        // H2 不支持 PostgreSQL 部分索引和延迟外键；完整生产 migration 由 PostgreSQL 集成测试原样执行。
        new ResourceDatabasePopulator(new ByteArrayResource(h2Migration.getBytes(StandardCharsets.UTF_8)))
                .execute(schemaDataSource);
        jdbcClient = JdbcClient.create(h2);
        insertBaseRows();

        sqlSessionFactory = new MyBatisPersistenceConfig().sqlSessionFactory(h2);
        AppSourceMapper mapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(AppSourceMapper.class);
        repository = new MyBatisAppSourceRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        schemaDataSource.destroy();
    }

    @Test
    void migrationCreatesTablesAndExactReadOnlyRootParameter() {
        assertThat(jdbcClient.sql("select count(*) from app_source_repository_slots")
                .query(Integer.class).single()).isZero();
        assertThat(jdbcClient.sql("select parameter_value from common_parameters "
                        + "where parameter_english = 'OPENCODE_APP_SOURCE_ROOT' and platform = 'all'")
                .query(String.class).single())
                .isEqualTo("${SYS_DATA_ROOT_DIR}/agent-opencode/workspace/appsource/");
        assertThat(jdbcClient.sql("select editable from common_parameters "
                        + "where parameter_english = 'OPENCODE_APP_SOURCE_ROOT' and platform = 'all'")
                .query(Boolean.class).single()).isFalse();
    }

    @Test
    void slotCompareAndSetRejectsStaleOptimisticVersion() {
        AppSourceRepositorySlot initial = slot(null, 0L, NOW);
        assertThat(repository.insertSlotIfAbsent(initial)).isTrue();
        assertThat(repository.insertSlotIfAbsent(initial)).isFalse();

        AppSourceRepositorySlot reserved = new AppSourceRepositorySlot(
                REPOSITORY_ID, null, 1L, 2L, "op-download", 1L, NOW, NOW.plusSeconds(1));
        assertThat(repository.updateSlotIfVersion(reserved, 9L)).isFalse();
        assertThat(repository.updateSlotIfVersion(reserved, 0L)).isTrue();
        assertThat(repository.findSlot(REPOSITORY_ID)).contains(reserved);
    }

    @Test
    void repositoryLockUsesTheRealMyBatisSelectForUpdate() {
        assertThat(repository.lockRepositoryForAppSource(REPOSITORY_ID)).isTrue();
        assertThat(repository.lockRepositoryForAppSource(new CodeRepositoryId("repo_missing"))).isFalse();
    }

    @Test
    void snapshotSelectionRoundTripsAndStatusUpdateUsesExpectedState() {
        repository.insertSlotIfAbsent(slot(null, 0L, NOW));
        AppSourceSnapshot snapshot = snapshot("op-download", AppSourceSnapshotStatus.PENDING);
        repository.saveSnapshot(snapshot);

        assertThat(repository.findSnapshot(REPOSITORY_ID, 1L)).contains(snapshot);
        assertThat(repository.updateSnapshotStatusAndIndex(
                REPOSITORY_ID, 1L, AppSourceSnapshotStatus.ACTIVE, AppSourceSnapshotStatus.EXPIRED,
                "a".repeat(64), NOW.plusSeconds(1))).isFalse();
        assertThatThrownBy(() -> repository.updateSnapshotStatusAndIndex(
                REPOSITORY_ID, 1L, AppSourceSnapshotStatus.PENDING, AppSourceSnapshotStatus.ACTIVE,
                "g".repeat(64), NOW.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.updateSnapshotStatusAndIndex(
                REPOSITORY_ID, 1L, AppSourceSnapshotStatus.PENDING, AppSourceSnapshotStatus.ACTIVE,
                "a".repeat(64), NOW.plusSeconds(1))).isTrue();
        assertThat(repository.findActiveSnapshot(REPOSITORY_ID)).hasValueSatisfying(active -> {
            assertThat(active.selectedPaths()).containsExactly(
                    new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY),
                    new AppSourceSelectedPath("pom.xml", AppSourcePathType.FILE));
            assertThat(active.indexSha256()).isEqualTo("a".repeat(64));
        });
    }

    @Test
    void replicaClaimAndTerminalWriteAreFencedByGenerationOwnerAndLease() {
        repository.insertSlotIfAbsent(slot(null, 0L, NOW));
        repository.saveSnapshot(snapshot("op-download", AppSourceSnapshotStatus.PENDING));
        assertThat(repository.insertReplicaIfAbsent(
                replica(AppSourceReplicaStatus.PENDING, null, null, NOW))).isTrue();

        assertThat(repository.claimReplica(
                REPOSITORY_ID, 1L, SERVER_ID, "worker-a", NOW.plusSeconds(30), NOW)).isPresent();
        assertThat(repository.claimReplica(
                REPOSITORY_ID, 1L, SERVER_ID, "worker-b", NOW.plusSeconds(30), NOW)).isEmpty();
        assertThat(repository.insertReplicaIfAbsent(
                replica(AppSourceReplicaStatus.PENDING, null, null, NOW.plusSeconds(1)))).isFalse();
        assertThat(repository.findReplica(REPOSITORY_ID, 1L, SERVER_ID)).hasValueSatisfying(running -> {
            assertThat(running.status()).isEqualTo(AppSourceReplicaStatus.RUNNING);
            assertThat(running.leaseOwner()).isEqualTo("worker-a");
            assertThat(running.attemptCount()).isOne();
        });
        AppSourceReplica ready = replica(AppSourceReplicaStatus.READY, null, null, NOW.plusSeconds(1));
        assertThat(repository.updateReplicaIfLease(ready, "worker-b", NOW.plusSeconds(1))).isFalse();
        assertThat(repository.updateReplicaIfLease(ready, "worker-a", NOW.plusSeconds(1))).isTrue();
        assertThat(repository.findReplica(REPOSITORY_ID, 1L, SERVER_ID)).contains(ready);
        assertThat(repository.insertReplicaIfAbsent(
                replica(AppSourceReplicaStatus.PENDING, null, null, NOW.plusSeconds(2)))).isFalse();
        assertThat(repository.findReplica(REPOSITORY_ID, 1L, SERVER_ID)).contains(ready);
    }

    @Test
    void claimableReplicaScanFindsPendingAndExpiredRunningOnlyOnTheRequestedServer() {
        repository.insertSlotIfAbsent(slot(null, 0L, NOW));
        repository.saveSnapshot(snapshot("op-download", AppSourceSnapshotStatus.PENDING));
        AppSourceReplica pending = replica(AppSourceReplicaStatus.PENDING, null, null, NOW);
        assertThat(repository.insertReplicaIfAbsent(pending)).isTrue();

        assertThat(repository.findClaimableReplicas(SERVER_ID, NOW, 10)).containsExactly(pending);
        assertThat(repository.findClaimableReplicas(new LinuxServerId("server-b"), NOW, 10)).isEmpty();

        assertThat(repository.claimReplica(
                REPOSITORY_ID, 1L, SERVER_ID, "worker-a", NOW.plusSeconds(30), NOW)).isPresent();
        assertThat(repository.findClaimableReplicas(SERVER_ID, NOW.plusSeconds(29), 10)).isEmpty();
        assertThat(repository.findClaimableReplicas(SERVER_ID, NOW.plusSeconds(30), 10))
                .singleElement()
                .satisfies(replica -> assertThat(replica.status()).isEqualTo(AppSourceReplicaStatus.RUNNING));
    }

    @Test
    void claimableRetryScanRequiresMatchingNonTerminalOperationAndServerStep() {
        repository.insertSlotIfAbsent(slot(1L, 0L, NOW));
        repository.saveSnapshot(snapshot("op-download", AppSourceSnapshotStatus.ACTIVE));
        AppSourceReplica failed = replica(AppSourceReplicaStatus.FAILED, null, null, NOW);
        assertThat(repository.insertReplicaIfAbsent(failed)).isTrue();

        assertThat(repository.findClaimableReplicas(SERVER_ID, NOW, 10)).isEmpty();

        AppSourceOperation failedRetry = retryOperation("op-failed-retry", AppSourceOperationStatus.PENDING, null);
        repository.saveOperation(failedRetry);
        AppSourceOperationStep failedRetryStep = retryStep(
                "step-failed-retry", failedRetry.operationId(), AppSourceStepStatus.PENDING);
        assertThat(repository.upsertStep(failedRetryStep)).isTrue();
        assertThat(repository.findClaimableReplicas(SERVER_ID, NOW, 10)).containsExactly(failed);

        assertThat(repository.upsertStep(retryStep(
                failedRetryStep.stepId(), failedRetry.operationId(), AppSourceStepStatus.RUNNING))).isTrue();
        assertThat(repository.upsertStep(retryStep(
                failedRetryStep.stepId(), failedRetry.operationId(), AppSourceStepStatus.SUCCEEDED))).isTrue();
        assertThat(repository.findClaimableReplicas(SERVER_ID, NOW, 10)).isEmpty();

        AppSourceOperation terminalOperation = retryOperation(
                "op-terminal-retry", AppSourceOperationStatus.SUCCEEDED, NOW.plusSeconds(1));
        repository.saveOperation(terminalOperation);
        assertThat(repository.upsertStep(retryStep(
                "step-terminal-retry", terminalOperation.operationId(), AppSourceStepStatus.PENDING))).isTrue();
        assertThat(repository.findClaimableReplicas(SERVER_ID, NOW, 10)).isEmpty();

        jdbcClient.sql("update app_source_replicas set status = 'STALE' "
                        + "where repository_id = :repositoryId and generation = 1 and linux_server_id = :serverId")
                .param("repositoryId", REPOSITORY_ID.value()).param("serverId", SERVER_ID.value()).update();
        AppSourceOperation staleRetry = retryOperation("op-stale-retry", AppSourceOperationStatus.PENDING, null);
        repository.saveOperation(staleRetry);
        assertThat(repository.upsertStep(retryStep(
                "step-stale-retry", staleRetry.operationId(), AppSourceStepStatus.RUNNING))).isTrue();
        assertThat(repository.findClaimableReplicas(SERVER_ID, NOW, 10)).singleElement()
                .extracting(AppSourceReplica::status)
                .isEqualTo(AppSourceReplicaStatus.STALE);

        assertThat(repository.updateOperationStatus(
                staleRetry.operationId(), AppSourceOperationStatus.PENDING,
                AppSourceOperationStatus.RUNNING, null)).isTrue();
        assertThat(repository.updateOperationStatus(
                staleRetry.operationId(), AppSourceOperationStatus.RUNNING,
                AppSourceOperationStatus.FAILED, NOW.plusSeconds(2))).isTrue();
        assertThat(repository.findClaimableReplicas(SERVER_ID, NOW, 10)).isEmpty();
    }

    @Test
    void replicaLeaseWriteRejectsIllegalStatusTransition() {
        repository.insertSlotIfAbsent(slot(null, 0L, NOW));
        repository.saveSnapshot(snapshot("op-download", AppSourceSnapshotStatus.PENDING));
        assertThat(repository.insertReplicaIfAbsent(
                replica(AppSourceReplicaStatus.PENDING, null, null, NOW))).isTrue();
        assertThat(repository.claimReplica(
                REPOSITORY_ID, 1L, SERVER_ID, "worker-a", NOW.plusSeconds(30), NOW)).isPresent();

        AppSourceReplica regressed = replica(AppSourceReplicaStatus.PENDING, null, null, NOW.plusSeconds(1));
        assertThatThrownBy(() -> repository.updateReplicaIfLease(regressed, "worker-a", NOW.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findReplica(REPOSITORY_ID, 1L, SERVER_ID)).hasValueSatisfying(running -> {
            assertThat(running.status()).isEqualTo(AppSourceReplicaStatus.RUNNING);
            assertThat(running.leaseOwner()).isEqualTo("worker-a");
        });
    }

    @Test
    void runtimeWorkspaceLookupAndCleanupStateUseTheRealMyBatisXml() {
        repository.insertSlotIfAbsent(slot(null, 0L, NOW));
        repository.saveSnapshot(snapshot("op-download", AppSourceSnapshotStatus.PENDING));
        AppSourceReplica ready = new AppSourceReplica(
                REPOSITORY_ID, 1L, SERVER_ID, new WorkspaceId("wrk_app_source"), AppSourceReplicaStatus.READY,
                null, null, 1, null, null, null, NOW, NOW);
        assertThat(repository.insertReplicaIfAbsent(ready)).isTrue();

        assertThat(repository.findReplicaByRuntimeWorkspaceId("wrk_app_source")).contains(ready);
        assertThat(repository.findReplicaByRuntimeWorkspaceId("wrk_missing")).isEmpty();
        jdbcClient.sql("update app_source_replicas set status = 'PENDING', runtime_workspace_id = null "
                        + "where repository_id = :repositoryId and generation = 1 and linux_server_id = :serverId")
                .param("repositoryId", REPOSITORY_ID.value()).param("serverId", SERVER_ID.value()).update();
        assertThat(repository.markReplicaCleaned(REPOSITORY_ID, 1L, SERVER_ID, NOW.plusSeconds(1))).isTrue();
        assertThat(repository.findReplica(REPOSITORY_ID, 1L, SERVER_ID)).hasValueSatisfying(cleaned -> {
            assertThat(cleaned.status()).isEqualTo(AppSourceReplicaStatus.CLEANED);
            assertThat(cleaned.runtimeWorkspaceId()).isNull();
            assertThat(cleaned.updatedAt()).isEqualTo(NOW.plusSeconds(1));
        });
        assertThat(repository.markReplicaCleaned(REPOSITORY_ID, 1L, SERVER_ID, NOW.plusSeconds(2))).isFalse();
    }

    @Test
    void operationStepsRecentSelectionAndHistoryUseStableKeys() {
        repository.insertSlotIfAbsent(slot(null, 0L, NOW));
        repository.saveSnapshot(snapshot("op-download", AppSourceSnapshotStatus.PENDING));
        AppSourceOperation operation = operation("op-download", AppSourceOperationStatus.PENDING, null);
        repository.saveOperation(operation);

        assertThat(repository.updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.RUNNING,
                AppSourceOperationStatus.SUCCEEDED, NOW.plusSeconds(1))).isFalse();
        assertThat(repository.updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.PENDING,
                AppSourceOperationStatus.RUNNING, null)).isTrue();
        AppSourceOperationStep step = new AppSourceOperationStep(
                "step-global", operation.operationId(), AppSourceStepScope.GLOBAL, null,
                "RESOLVE_COMMIT", 10, AppSourceStepStatus.RUNNING, "正在解析", NOW, null, NOW);
        assertThat(repository.upsertStep(step)).isTrue();
        assertThat(repository.upsertStep(new AppSourceOperationStep(
                "step-global", operation.operationId(), AppSourceStepScope.GLOBAL, null,
                "RESOLVE_COMMIT", 10, AppSourceStepStatus.SUCCEEDED, "解析完成", NOW,
                NOW.plusSeconds(1), NOW.plusSeconds(1)))).isTrue();
        assertThat(repository.findSteps(operation.operationId())).singleElement()
                .extracting(AppSourceOperationStep::status)
                .isEqualTo(AppSourceStepStatus.SUCCEEDED);
        assertThat(repository.upsertStep(new AppSourceOperationStep(
                "step-global", operation.operationId(), AppSourceStepScope.GLOBAL, null,
                "RESOLVE_COMMIT", 10, AppSourceStepStatus.RUNNING, "迟到执行者", NOW,
                null, NOW.plusSeconds(2)))).isFalse();
        assertThat(repository.findSteps(operation.operationId())).singleElement()
                .extracting(AppSourceOperationStep::status)
                .isEqualTo(AppSourceStepStatus.SUCCEEDED);

        AppSourceOperationStep serverStep = new AppSourceOperationStep(
                "step-server", operation.operationId(), AppSourceStepScope.SERVER, SERVER_ID,
                "MATERIALIZE", 20, AppSourceStepStatus.PENDING, "等待物化", null, null, NOW);
        assertThat(repository.upsertStep(serverStep)).isTrue();
        assertThat(repository.findInFlightOperationForReplica(REPOSITORY_ID, 1L, SERVER_ID))
                .hasValueSatisfying(bound -> {
                    assertThat(bound.operationId()).isEqualTo(operation.operationId());
                    assertThat(bound.status()).isEqualTo(AppSourceOperationStatus.RUNNING);
                });
        assertThat(repository.updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.RUNNING,
                AppSourceOperationStatus.SUCCEEDED, NOW.plusSeconds(3))).isTrue();
        assertThat(repository.findInFlightOperationForReplica(REPOSITORY_ID, 1L, SERVER_ID)).isEmpty();

        AppSourceRecentSelection recent = new AppSourceRecentSelection(
                USER_ID, APP_ID, REPOSITORY_ID, 1L, NOW);
        repository.upsertRecentSelection(recent);
        assertThat(repository.findRecentSelection(USER_ID)).contains(recent);
        assertThat(repository.hasRepositoryHistory(REPOSITORY_ID)).isTrue();
        repository.deleteRecentSelection(USER_ID);
        assertThat(repository.findRecentSelection(USER_ID)).isEmpty();
    }

    @Test
    void historyGuardIncludesStandaloneOperationRows() {
        repository.saveOperation(operation("op-history-only", AppSourceOperationStatus.PENDING, null));

        assertThat(repository.hasRepositoryHistory(REPOSITORY_ID)).isTrue();
    }

    @Test
    void cleanupTasksSupportDueClaimAndLeaseFencing() {
        repository.insertSlotIfAbsent(slot(null, 0L, NOW));
        repository.saveOperation(operation("op-download", AppSourceOperationStatus.PENDING, null));
        repository.saveSnapshot(snapshot("op-download", AppSourceSnapshotStatus.PENDING));
        repository.insertCleanupTasks(List.of(cleanupTask()));

        assertThat(repository.findCleanupTasks(REPOSITORY_ID, 1L, SERVER_ID))
                .containsExactly(cleanupTask());
        assertThat(repository.findDueCleanupTasks(SERVER_ID, NOW, 10)).isEmpty();
        assertThat(repository.makeCleanupDueNow(REPOSITORY_ID, 1L, NOW)).isOne();
        assertThat(repository.findDueCleanupTasks(SERVER_ID, NOW, 10)).singleElement().satisfies(due -> {
            assertThat(due.cleanupTaskId()).isEqualTo("cleanup-1");
            assertThat(due.deleteAt()).isEqualTo(NOW);
            assertThat(due.nextRetryAt()).isEqualTo(NOW);
        });
        assertThat(repository.claimCleanupTask(
                "cleanup-1", "cleaner-a", NOW.plusSeconds(40), NOW.plusSeconds(10))).isPresent();
        assertThat(repository.completeCleanupTask(
                "cleanup-1", "cleaner-b", NOW.plusSeconds(11))).isFalse();
        assertThat(repository.completeCleanupTask(
                "cleanup-1", "cleaner-a", NOW.plusSeconds(11))).isTrue();
    }

    private void insertBaseRows() {
        jdbcClient.sql("insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at) "
                        + "values (:userId, 'auth-app-source', 'app-source-user', 'hash', 'ACTIVE', :now, :now)")
                .param("userId", USER_ID.value()).param("now", NOW).update();
        jdbcClient.sql("insert into applications(app_id, app_name, enabled, created_at, updated_at) "
                        + "values (:appId, '应用源码', true, :now, :now)")
                .param("appId", APP_ID.value()).param("now", NOW).update();
        jdbcClient.sql("insert into code_repositories(repository_id, git_url, name, english_name, repository_type, "
                        + "deployment_mode, standard, created_at, updated_at) values "
                        + "(:repositoryId, 'https://gitee.com/demo/source.git', '源码库', 'source-repo', "
                        + "'APPLICATION_CODE_REPOSITORY', 'EXTERNAL', false, :now, :now)")
                .param("repositoryId", REPOSITORY_ID.value()).param("now", NOW).update();
        jdbcClient.sql("insert into linux_servers(linux_server_id, name, status, capacity_summary_json, "
                        + "last_heartbeat_at, trace_id, created_at, updated_at) "
                        + "values (:serverId, 'Server A', 'ONLINE', '{}', :now, 'trace-server-a', :now, :now)")
                .param("serverId", SERVER_ID.value()).param("now", NOW).update();
        jdbcClient.sql("insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at) "
                        + "values ('wrk_app_source', 'appsource:source-repo', '/tmp/appsource/source-repo', "
                        + "'ACTIVE', 'trace-app-source-workspace', :now, :now)")
                .param("now", NOW).update();
    }

    private AppSourceRepositorySlot slot(Long activeGeneration, long lockVersion, Instant updatedAt) {
        return new AppSourceRepositorySlot(
                REPOSITORY_ID, activeGeneration, null, activeGeneration == null ? 1L : activeGeneration + 1L,
                null, lockVersion, NOW, updatedAt);
    }

    private AppSourceSnapshot snapshot(String operationId, AppSourceSnapshotStatus status) {
        return new AppSourceSnapshot(
                REPOSITORY_ID, 1L, "source-repo", AppSourcePurpose.PERSONAL, USER_ID,
                "main", "0123456789abcdef", List.of(
                        new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY),
                        new AppSourceSelectedPath("pom.xml", AppSourcePathType.FILE)),
                null, NOW, NOW.plusSeconds(48L * 3600L), status, NOW, NOW);
    }

    private AppSourceReplica replica(
            AppSourceReplicaStatus status, String leaseOwner, Instant leaseUntil, Instant updatedAt) {
        return new AppSourceReplica(
                REPOSITORY_ID, 1L, SERVER_ID, null, status, leaseOwner, leaseUntil,
                status == AppSourceReplicaStatus.READY ? 1 : 0, null, null, null, NOW, updatedAt);
    }

    private AppSourceOperation operation(
            String operationId, AppSourceOperationStatus status, Instant completedAt) {
        return new AppSourceOperation(
                operationId, APP_ID, REPOSITORY_ID, null, 1L, USER_ID,
                AppSourceOperationType.DOWNLOAD, "request-hash", status, "trace-app-source", NOW, completedAt);
    }

    private AppSourceOperation retryOperation(
            String operationId, AppSourceOperationStatus status, Instant completedAt) {
        return new AppSourceOperation(
                operationId, APP_ID, REPOSITORY_ID, 1L, 1L, USER_ID,
                AppSourceOperationType.RETRY_REPLICAS, "request-" + operationId,
                status, "trace-" + operationId, NOW, completedAt);
    }

    private AppSourceOperationStep retryStep(
            String stepId, String operationId, AppSourceStepStatus status) {
        return new AppSourceOperationStep(
                stepId, operationId, AppSourceStepScope.SERVER, SERVER_ID,
                "RETRY_QUEUED", 0, status, "等待失败副本重试",
                status == AppSourceStepStatus.PENDING ? null : NOW,
                status == AppSourceStepStatus.SUCCEEDED ? NOW.plusSeconds(1) : null,
                status == AppSourceStepStatus.SUCCEEDED ? NOW.plusSeconds(1) : NOW);
    }

    private AppSourceCleanupTask cleanupTask() {
        return new AppSourceCleanupTask(
                "cleanup-1", "op-download", REPOSITORY_ID, 1L, SERVER_ID,
                NOW.plusSeconds(5), AppSourceCleanupStatus.PENDING, null, null,
                0, null, null, null, "trace-cleanup", NOW, NOW);
    }
}
