package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStep;
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
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryDeploymentMode;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.configuration.ResolvedParameter;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.persistence.JdbcWorkspaceRepository;
import com.enterprise.testagent.persistence.mybatis.AppSourceMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisAppSourceRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisPersistenceConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 组合真实 H2 MyBatis、dispatcher 与 worker，验证失败副本重试不会因瞬时唤醒丢失而悬挂。 */
class AppSourceRetryRecoveryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");
    private static final ApplicationId APP_ID = new ApplicationId("app_retry_recovery");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_retry_recovery");
    private static final CodeRepositoryId BLOCKER_REPOSITORY_ID = new CodeRepositoryId("repo_retry_blocker");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-a");
    private static final LinuxServerId SERVER_B = new LinuxServerId("server-b");
    private static final UserId USER_ID = new UserId("usr_retry_recovery");
    private static final String INDEX_SHA = "b".repeat(64);

    @TempDir
    Path tempDir;

    private SingleConnectionDataSource schemaDataSource;
    private JdbcClient jdbc;
    private MyBatisAppSourceRepository repository;
    private AppSourceReplicaRetryRegistrar registrar;
    private ConfigurationManagementRepository configuration;
    private AppSourceGitAccessResolver gitAccess;
    private AppSourceGitMaterializer materializer;
    private ManagedWorkspacePathResolver paths;
    private JdbcWorkspaceRepository workspaces;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        String url = "jdbc:h2:mem:testagent_app_source_retry_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
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
        installAppSourceSchema(schemaDataSource);
        jdbc = JdbcClient.create(h2);
        insertBaseRows(jdbc);
        SqlSessionFactory sqlSessionFactory = new MyBatisPersistenceConfig().sqlSessionFactory(h2);
        repository = new MyBatisAppSourceRepository(
                new SqlSessionTemplate(sqlSessionFactory).getMapper(AppSourceMapper.class));
        registrar = new AppSourceReplicaRetryRegistrar(repository);
        configuration = mock(ConfigurationManagementRepository.class);
        when(configuration.findRepository(REPOSITORY_ID)).thenReturn(Optional.of(codeRepository()));
        gitAccess = mock(AppSourceGitAccessResolver.class);
        when(gitAccess.resolve(any(), eq(USER_ID)))
                .thenReturn(new AppSourceGitAccessResolver.GitAccess("https://git.example.test/source.git", null));
        materializer = mock(AppSourceGitMaterializer.class);
        paths = new ManagedWorkspacePathResolver(appSourceParameters());
        workspaces = new JdbcWorkspaceRepository(jdbc);
    }

    @AfterEach
    void tearDown() {
        schemaDataSource.destroy();
    }

    @Test
    void fullQueueRejectedFailedRetryIsRecoveredAndItsOperationTerminates() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AppSourceReplicaWorker actualWorker = workerCompletingSuccessfully(completed);
        AppSourceReplicaWorker routedWorker = spy(actualWorker);
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        doAnswer(invocation -> {
            blockerStarted.countDown();
            releaseBlocker.await(2, TimeUnit.SECONDS);
            return AppSourceReplicaWorker.Outcome.SUCCEEDED;
        }).when(routedWorker).run(eq(BLOCKER_REPOSITORY_ID), anyLong(), eq(SERVER_ID), any());
        DefaultAppSourceReplicaTaskDispatcher dispatcher = dispatcher(routedWorker);
        dispatcher.start();
        try {
            dispatcher.wake(blockerOperation(), Set.of(SERVER_ID));
            assertThat(blockerStarted.await(2, TimeUnit.SECONDS)).isTrue();
            AppSourceOperation retry = registerRetry(AppSourceReplicaStatus.FAILED, "op-retry-after-full-queue");

            dispatcher.wake(retry, Set.of(SERVER_ID));

            assertThat(repository.findOperation(retry.operationId()))
                    .get().extracting(AppSourceOperation::status)
                    .isEqualTo(AppSourceOperationStatus.PENDING);
            releaseBlocker.countDown();
            assertThat(completed.await(2, TimeUnit.SECONDS))
                    .as("replica=%s operation=%s steps=%s",
                            repository.findReplica(REPOSITORY_ID, 1L, SERVER_ID),
                            repository.findOperation(retry.operationId()),
                            repository.findSteps(retry.operationId()))
                    .isTrue();
            assertThat(repository.findOperation(retry.operationId()))
                    .get().extracting(AppSourceOperation::status)
                    .isEqualTo(AppSourceOperationStatus.SUCCEEDED);
            assertThat(repository.findSteps(retry.operationId()))
                    .hasSize(AppSourceReplicaStepCatalog.codes().size())
                    .allSatisfy(step -> {
                        assertThat(step.status().name()).isEqualTo("SUCCEEDED");
                        assertThat(step.safeSummary())
                                .doesNotContain("/data/", "git@", "stderr", "privateKey");
                    });
        } finally {
            releaseBlocker.countDown();
            dispatcher.stop();
        }
    }

    @Test
    void dispatcherRecoveryCyclesKeepRetryOpenUntilOfflineTargetCanRun() throws Exception {
        AppSourceOperation retry = registerRetryAcrossTwoServers("op-retry-offline-server");
        CountDownLatch serverACompleted = new CountDownLatch(1);
        DefaultAppSourceReplicaTaskDispatcher dispatcher = dispatcher(
                workerCompletingSuccessfully(serverACompleted, SERVER_ID));
        dispatcher.start();
        try {
            dispatcher.wake(retry, Set.of(SERVER_ID, SERVER_B));
            assertThat(serverACompleted.await(2, TimeUnit.SECONDS)).isTrue();

            for (int scan = 0; scan < 4; scan++) {
                TimeUnit.MILLISECONDS.sleep(30);
                assertThat(repository.findOperation(retry.operationId()))
                        .get().extracting(AppSourceOperation::status)
                        .isEqualTo(AppSourceOperationStatus.RUNNING);
                assertThat(repository.findSteps(retry.operationId()))
                        .filteredOn(step -> step.linuxServerId().equals(SERVER_B))
                        .allSatisfy(step -> assertThat(step.status()).isEqualTo(AppSourceStepStatus.PENDING));
            }
            assertThat(repository.findClaimableReplicas(SERVER_B, NOW, 10))
                    .extracting(AppSourceReplica::linuxServerId)
                    .containsExactly(SERVER_B);

            CountDownLatch serverBCompleted = new CountDownLatch(1);
            assertThat(workerCompletingSuccessfully(serverBCompleted, SERVER_B)
                    .run(REPOSITORY_ID, 1L, SERVER_B, retry.traceId()))
                    .isEqualTo(AppSourceReplicaWorker.Outcome.SUCCEEDED);
            assertThat(serverBCompleted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(repository.findOperation(retry.operationId()))
                    .get().extracting(AppSourceOperation::status)
                    .isEqualTo(AppSourceOperationStatus.SUCCEEDED);
        } finally {
            dispatcher.stop();
        }
    }

    @Test
    void newDispatcherRecoversStaleRetryLeftByStoppedDispatcher() throws Exception {
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        AppSourceReplicaWorker blockingWorker = mock(AppSourceReplicaWorker.class);
        doAnswer(invocation -> {
            blockerStarted.countDown();
            try {
                releaseBlocker.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return AppSourceReplicaWorker.Outcome.SUCCEEDED;
        }).when(blockingWorker).run(eq(BLOCKER_REPOSITORY_ID), anyLong(), eq(SERVER_ID), any());
        DefaultAppSourceReplicaTaskDispatcher stoppedDispatcher = dispatcher(blockingWorker);
        stoppedDispatcher.start();
        AppSourceOperation retry;
        try {
            stoppedDispatcher.wake(blockerOperation(), Set.of(SERVER_ID));
            assertThat(blockerStarted.await(2, TimeUnit.SECONDS)).isTrue();
            retry = registerRetry(AppSourceReplicaStatus.STALE, "op-retry-after-restart");
            stoppedDispatcher.wake(retry, Set.of(SERVER_ID));
            assertThat(repository.findOperation(retry.operationId()))
                    .get().extracting(AppSourceOperation::status)
                    .isEqualTo(AppSourceOperationStatus.PENDING);
        } finally {
            stoppedDispatcher.stop();
            releaseBlocker.countDown();
        }

        CountDownLatch completed = new CountDownLatch(1);
        DefaultAppSourceReplicaTaskDispatcher restartedDispatcher =
                dispatcher(workerCompletingSuccessfully(completed));
        restartedDispatcher.start();
        try {
            assertThat(completed.await(2, TimeUnit.SECONDS))
                    .as("replica=%s operation=%s steps=%s",
                            repository.findReplica(REPOSITORY_ID, 1L, SERVER_ID),
                            repository.findOperation(retry.operationId()),
                            repository.findSteps(retry.operationId()))
                    .isTrue();
            assertThat(repository.findOperation(retry.operationId()))
                    .get().extracting(AppSourceOperation::status)
                    .isEqualTo(AppSourceOperationStatus.SUCCEEDED);
            assertThat(repository.findSteps(retry.operationId()))
                    .hasSize(AppSourceReplicaStepCatalog.codes().size())
                    .allSatisfy(step -> assertThat(step.status().name()).isEqualTo("SUCCEEDED"));
        } finally {
            restartedDispatcher.stop();
        }
    }

    @Test
    void expiredLeaseAfterTerminalFailureProgressIsReclaimedAndWholeTimelineIsReset() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AppSourceOperation retry = registerRetry(
                AppSourceReplicaStatus.FAILED, "op-retry-after-result-cas-loss");
        assertThat(repository.claimReplica(
                REPOSITORY_ID, 1L, SERVER_ID, retry.operationId(), "worker-expired",
                NOW.minusSeconds(1), NOW.minusSeconds(60))).isPresent();
        // 模拟旧 worker 已写完失败步骤，但 result lease-CAS 因绝对到期未命中，副本仍为过期 RUNNING。
        jdbc.sql("update app_source_operation_steps set status = case when step_code = 'SHALLOW_CLONE' "
                        + "then 'FAILED' else 'SKIPPED' end, started_at = :startedAt, completed_at = :completedAt, "
                        + "safe_summary = '旧 attempt 终态', updated_at = :completedAt where operation_id = :operationId")
                .param("startedAt", NOW.minusSeconds(30))
                .param("completedAt", NOW.minusSeconds(1))
                .param("operationId", retry.operationId())
                .update();

        AppSourceReplicaWorker.Outcome outcome =
                workerCompletingSuccessfully(completed).run(REPOSITORY_ID, 1L, SERVER_ID, retry.traceId());

        assertThat(outcome).isEqualTo(AppSourceReplicaWorker.Outcome.SUCCEEDED);
        assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(repository.findReplica(REPOSITORY_ID, 1L, SERVER_ID)).hasValueSatisfying(replica -> {
            assertThat(replica.status()).isEqualTo(AppSourceReplicaStatus.READY);
            assertThat(replica.attemptCount()).isEqualTo(3);
        });
        assertThat(repository.findSteps(retry.operationId()))
                .hasSize(AppSourceReplicaStepCatalog.codes().size())
                .allSatisfy(step -> {
                    assertThat(step.status()).isEqualTo(AppSourceStepStatus.SUCCEEDED);
                    assertThat(step.startedAt()).isEqualTo(NOW);
                    assertThat(step.completedAt()).isEqualTo(NOW);
                    assertThat(step.safeSummary()).doesNotContain("旧 attempt 终态");
                });
        assertThat(repository.findOperation(retry.operationId()))
                .get().extracting(AppSourceOperation::status)
                .isEqualTo(AppSourceOperationStatus.SUCCEEDED);
    }

    @Test
    void legacyOnlyRetryTimelineIsBackfilledAndOldStepIsSafelySkipped() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AppSourceOperation retry = registerRetry(AppSourceReplicaStatus.FAILED, "op-legacy-only-retry");
        jdbc.sql("delete from app_source_operation_steps where operation_id = :operationId")
                .param("operationId", retry.operationId()).update();
        assertThat(repository.upsertStep(new AppSourceOperationStep(
                "legacy-retry-queued", retry.operationId(), AppSourceStepScope.SERVER, SERVER_ID,
                "RETRY_QUEUED", 0, AppSourceStepStatus.PENDING,
                "旧版等待摘要", null, null, NOW.minusSeconds(30)))).isTrue();

        assertThat(workerCompletingSuccessfully(completed)
                .run(REPOSITORY_ID, 1L, SERVER_ID, retry.traceId()))
                .isEqualTo(AppSourceReplicaWorker.Outcome.SUCCEEDED);

        assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(repository.findSteps(retry.operationId()))
                .filteredOn(step -> "RETRY_QUEUED".equals(step.stepCode()))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.status()).isEqualTo(AppSourceStepStatus.SKIPPED);
                    assertThat(step.safeSummary()).isEqualTo("已跳过：兼容旧版服务器步骤");
                });
        assertThat(repository.findSteps(retry.operationId()))
                .filteredOn(step -> AppSourceReplicaStepCatalog.codes().contains(step.stepCode()))
                .hasSize(AppSourceReplicaStepCatalog.codes().size())
                .allSatisfy(step -> assertThat(step.status()).isEqualTo(AppSourceStepStatus.SUCCEEDED));
    }

    @Test
    void realMaterializerGitFailureLeavesEveryStableStepTerminalAndSafe() {
        AppSourceOperation retry = registerRetry(AppSourceReplicaStatus.FAILED, "op-real-materializer-failure");
        AppSourceGitMaterializer failingMaterializer = new AppSourceGitMaterializer(
                (command, privateKey, timeout) -> {
                    throw new PlatformException(
                            ErrorCode.GIT_UNAVAILABLE,
                            "raw git stderr /private/source secret-key");
                },
                new com.fasterxml.jackson.databind.ObjectMapper(),
                AppSourceGitMaterializer.DirectoryMover.filesystem());

        assertThat(worker(failingMaterializer).run(REPOSITORY_ID, 1L, SERVER_ID, retry.traceId()))
                .isEqualTo(AppSourceReplicaWorker.Outcome.FAILED);

        assertThat(repository.findOperation(retry.operationId()))
                .get().extracting(AppSourceOperation::status)
                .isEqualTo(AppSourceOperationStatus.FAILED);
        assertThat(repository.findReplica(REPOSITORY_ID, 1L, SERVER_ID)).hasValueSatisfying(replica -> {
            assertThat(replica.status()).isEqualTo(AppSourceReplicaStatus.FAILED);
            assertThat(replica.safeErrorCode()).isEqualTo(ErrorCode.GIT_UNAVAILABLE.name());
            assertThat(replica.safeErrorMessage()).isEqualTo("源码副本物化失败");
        });
        assertThat(repository.findSteps(retry.operationId()))
                .hasSize(AppSourceReplicaStepCatalog.codes().size())
                .allSatisfy(step -> {
                    assertThat(step.status()).isIn(
                            AppSourceStepStatus.SUCCEEDED,
                            AppSourceStepStatus.FAILED,
                            AppSourceStepStatus.SKIPPED);
                    assertThat(step.safeSummary())
                            .doesNotContain("/private/source", "secret-key", "stderr");
                });
        assertThat(repository.findSteps(retry.operationId()))
                .filteredOn(step -> AppSourceReplicaStepCatalog.SHALLOW_CLONE.equals(step.stepCode()))
                .singleElement()
                .extracting(AppSourceOperationStep::status)
                .isEqualTo(AppSourceStepStatus.FAILED);
    }

    private AppSourceReplicaWorker workerCompletingSuccessfully(CountDownLatch completed) {
        return workerCompletingSuccessfully(completed, SERVER_ID);
    }

    private AppSourceReplicaWorker workerCompletingSuccessfully(
            CountDownLatch completed, LinuxServerId serverId) {
        doAnswer(invocation -> {
            AppSourceGitMaterializer.Result result = new AppSourceGitMaterializer.Result(INDEX_SHA, true);
            ((AppSourceGitMaterializer.Completion) invocation.getArgument(1)).complete(result);
            completed.countDown();
            return result;
        }).when(materializer).materialize(any(), any(), any());
        return worker(materializer, serverId);
    }

    private AppSourceReplicaWorker worker(AppSourceGitMaterializer selectedMaterializer) {
        return worker(selectedMaterializer, SERVER_ID);
    }

    private AppSourceReplicaWorker worker(
            AppSourceGitMaterializer selectedMaterializer, LinuxServerId serverId) {
        return new AppSourceReplicaWorker(
                repository,
                configuration,
                gitAccess,
                selectedMaterializer,
                new AppSourceReplicaResultRecorder(repository, workspaces),
                paths,
                new WorkspaceServerIdentity(serverId.value()),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(10));
    }

    private DefaultAppSourceReplicaTaskDispatcher dispatcher(AppSourceReplicaWorker worker) {
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        when(publisher.instanceId()).thenReturn("instance-a");
        return new DefaultAppSourceReplicaTaskDispatcher(
                worker, repository, new AppSourceReplicaResultRecorder(repository, workspaces), publisher,
                new WorkspaceServerIdentity(SERVER_ID.value()),
                1, 1, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMillis(20), 32);
    }

    private AppSourceOperation registerRetry(AppSourceReplicaStatus status, String operationId) {
        repository.insertSlotIfAbsent(new AppSourceRepositorySlot(
                REPOSITORY_ID, 1L, null, 2L, null, 0L, NOW.minus(Duration.ofHours(1)), NOW));
        repository.saveSnapshot(new AppSourceSnapshot(
                REPOSITORY_ID, 1L, "source-repo", AppSourcePurpose.TEAM, USER_ID,
                "main", "0123456789abcdef", List.of(
                        new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                INDEX_SHA, NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(47)),
                AppSourceSnapshotStatus.ACTIVE, NOW.minus(Duration.ofHours(1)), NOW));
        repository.insertReplicaIfAbsent(new AppSourceReplica(
                REPOSITORY_ID, 1L, SERVER_ID, null, status, null, null,
                1, NOW, "GIT_UNAVAILABLE", "源码副本物化失败", NOW.minus(Duration.ofHours(1)), NOW));
        return registrar.register(new AppSourceReplicaRetryRegistrar.RetryRequest(
                operationId, APP_ID, REPOSITORY_ID, 1L, USER_ID,
                "request-" + operationId, Set.of(SERVER_ID), "trace-" + operationId, NOW));
    }

    private AppSourceOperation registerRetryAcrossTwoServers(String operationId) {
        repository.insertSlotIfAbsent(new AppSourceRepositorySlot(
                REPOSITORY_ID, 1L, null, 2L, null, 0L, NOW.minus(Duration.ofHours(1)), NOW));
        repository.saveSnapshot(new AppSourceSnapshot(
                REPOSITORY_ID, 1L, "source-repo", AppSourcePurpose.TEAM, USER_ID,
                "main", "0123456789abcdef", List.of(
                        new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                INDEX_SHA, NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(47)),
                AppSourceSnapshotStatus.ACTIVE, NOW.minus(Duration.ofHours(1)), NOW));
        for (LinuxServerId serverId : List.of(SERVER_ID, SERVER_B)) {
            repository.insertReplicaIfAbsent(new AppSourceReplica(
                    REPOSITORY_ID, 1L, serverId, null, AppSourceReplicaStatus.FAILED,
                    null, null, 1, NOW, "GIT_UNAVAILABLE", "源码副本物化失败",
                    NOW.minus(Duration.ofHours(1)), NOW));
        }
        return registrar.register(new AppSourceReplicaRetryRegistrar.RetryRequest(
                operationId, APP_ID, REPOSITORY_ID, 1L, USER_ID,
                "request-" + operationId, Set.of(SERVER_ID, SERVER_B), "trace-" + operationId, NOW));
    }

    private AppSourceOperation blockerOperation() {
        return new AppSourceOperation(
                "op-blocker", APP_ID, BLOCKER_REPOSITORY_ID, null, 1L, USER_ID,
                AppSourceOperationType.DOWNLOAD, "blocker", AppSourceOperationStatus.PENDING,
                "trace-blocker", NOW, null);
    }

    private CodeRepository codeRepository() {
        return new CodeRepository(
                REPOSITORY_ID, "https://git.example.test/source.git", "源码库", "source-repo",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(),
                CodeRepositoryDeploymentMode.EXTERNAL.value(), false,
                NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(2)));
    }

    private CommonParameterValues appSourceParameters() {
        return new CommonParameterValues() {
            @Override
            public Optional<String> resolvedValue(String englishName) {
                return ManagedWorkspacePathResolver.PARAM_OPENCODE_APP_SOURCE_ROOT.equals(englishName)
                        ? Optional.of(tempDir.resolve("appsource").toString())
                        : Optional.empty();
            }

            @Override
            public Optional<String> resolvedValue(String englishName, ParameterPlatform platform) {
                return resolvedValue(englishName);
            }

            @Override
            public Optional<CommonParameter> raw(String englishName, ParameterPlatform platform) {
                return Optional.empty();
            }

            @Override
            public List<CommonParameter> findAll() {
                return List.of();
            }

            @Override
            public List<ResolvedParameter> resolvedAll() {
                return List.of();
            }
        };
    }

    private void insertBaseRows(JdbcClient jdbc) {
        jdbc.sql("insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at) "
                        + "values (:userId, 'auth-retry', 'retry-user', 'hash', 'ACTIVE', :now, :now)")
                .param("userId", USER_ID.value()).param("now", NOW).update();
        jdbc.sql("insert into applications(app_id, app_name, enabled, created_at, updated_at) "
                        + "values (:appId, '重试恢复', true, :now, :now)")
                .param("appId", APP_ID.value()).param("now", NOW).update();
        jdbc.sql("insert into code_repositories(repository_id, git_url, name, english_name, repository_type, "
                        + "deployment_mode, standard, created_at, updated_at) values "
                        + "(:repositoryId, 'https://git.example.test/source.git', '源码库', 'source-repo', "
                        + "'APPLICATION_CODE_REPOSITORY', 'EXTERNAL', false, :now, :now)")
                .param("repositoryId", REPOSITORY_ID.value()).param("now", NOW).update();
        jdbc.sql("insert into linux_servers(linux_server_id, name, status, capacity_summary_json, "
                        + "last_heartbeat_at, trace_id, created_at, updated_at) "
                        + "values (:serverId, 'Server A', 'ONLINE', '{}', :now, 'trace-server-a', :now, :now)")
                .param("serverId", SERVER_ID.value()).param("now", NOW).update();
        jdbc.sql("insert into linux_servers(linux_server_id, name, status, capacity_summary_json, "
                        + "last_heartbeat_at, trace_id, created_at, updated_at) "
                        + "values (:serverId, 'Server B', 'ONLINE', '{}', :now, 'trace-server-b', :now, :now)")
                .param("serverId", SERVER_B.value()).param("now", NOW).update();
    }

    private void installAppSourceSchema(SingleConnectionDataSource dataSource) throws Exception {
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
        new ResourceDatabasePopulator(new ByteArrayResource(h2Migration.getBytes(StandardCharsets.UTF_8)))
                .execute(dataSource);
    }
}
