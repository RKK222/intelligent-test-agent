package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
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
    private static final UserId USER_ID = new UserId("usr_retry_recovery");
    private static final String INDEX_SHA = "b".repeat(64);

    @TempDir
    Path tempDir;

    private SingleConnectionDataSource schemaDataSource;
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
        JdbcClient jdbc = JdbcClient.create(h2);
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
            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(repository.findOperation(retry.operationId()))
                    .get().extracting(AppSourceOperation::status)
                    .isEqualTo(AppSourceOperationStatus.SUCCEEDED);
        } finally {
            releaseBlocker.countDown();
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
            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(repository.findOperation(retry.operationId()))
                    .get().extracting(AppSourceOperation::status)
                    .isEqualTo(AppSourceOperationStatus.SUCCEEDED);
        } finally {
            restartedDispatcher.stop();
        }
    }

    private AppSourceReplicaWorker workerCompletingSuccessfully(CountDownLatch completed) {
        when(materializer.materialize(any(), any())).thenAnswer(invocation -> {
            AppSourceGitMaterializer.Result result = new AppSourceGitMaterializer.Result(INDEX_SHA, true);
            ((AppSourceGitMaterializer.Completion) invocation.getArgument(1)).complete(result);
            completed.countDown();
            return result;
        });
        return new AppSourceReplicaWorker(
                repository,
                configuration,
                gitAccess,
                materializer,
                new AppSourceReplicaResultRecorder(repository, workspaces),
                paths,
                new WorkspaceServerIdentity(SERVER_ID.value()),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(10));
    }

    private DefaultAppSourceReplicaTaskDispatcher dispatcher(AppSourceReplicaWorker worker) {
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        when(publisher.instanceId()).thenReturn("instance-a");
        return new DefaultAppSourceReplicaTaskDispatcher(
                worker, repository, publisher, new WorkspaceServerIdentity(SERVER_ID.value()),
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
