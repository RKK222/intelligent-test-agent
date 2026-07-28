package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.appsource.AppSourceStepStatus;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.persistence.JdbcWorkspaceRepository;
import com.enterprise.testagent.persistence.mybatis.AppSourceMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisAppSourceRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisPersistenceConfig;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 真实 PostgreSQL 事务验证三副本最后两台并发完成时的全局收敛与 generation 发布。 */
@Testcontainers(disabledWithoutDocker = true)
class AppSourceReplicaConvergencePostgresqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");
    private static final Instant OLD_EXPIRY = NOW.plusSeconds(24 * 3600L);
    private static final ApplicationId APP_ID = new ApplicationId("app_convergence_pg");
    private static final UserId USER_ID = new UserId("usr_convergence_pg");
    private static final String INDEX_SHA = "c".repeat(64);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static DataSource dataSource;
    private static MyBatisAppSourceRepository repository;
    private static JdbcWorkspaceRepository workspaces;
    private static TransactionTemplate transactions;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        postgres.setURL(POSTGRES.getJdbcUrl());
        postgres.setUser(POSTGRES.getUsername());
        postgres.setPassword(POSTGRES.getPassword());
        dataSource = postgres;
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                values (:userId, 'auth-convergence-pg', 'convergence-pg', 'hash', 'ACTIVE', :now, :now)
                """).param("userId", USER_ID.value()).param("now", Timestamp.from(NOW)).update();
        jdbc.sql("""
                insert into applications(app_id, app_name, enabled, created_at, updated_at)
                values (:appId, '并发收敛 PostgreSQL', true, :now, :now)
                """).param("appId", APP_ID.value()).param("now", Timestamp.from(NOW)).update();
        SqlSessionFactory sqlSessionFactory = new MyBatisPersistenceConfig().sqlSessionFactory(dataSource);
        repository = new MyBatisAppSourceRepository(
                new SqlSessionTemplate(sqlSessionFactory).getMapper(AppSourceMapper.class));
        workspaces = new JdbcWorkspaceRepository(JdbcClient.create(dataSource));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void lastTwoConcurrentFailuresLeaveOldActiveAndFailPendingGeneration() throws Exception {
        Scenario scenario = registerScenario("all-failed");
        BarrierContext context = recorderWithLastTwoBarrier();
        recordFailure(context, scenario, scenario.serverA(), "worker-a", false);

        runLastTwoConcurrently(
                () -> recordFailure(context, scenario, scenario.serverB(), "worker-b", true),
                () -> recordFailure(context, scenario, scenario.serverC(), "worker-c", true));

        assertThat(repository.findOperation(scenario.operation().operationId()))
                .get().extracting(AppSourceOperation::status)
                .isEqualTo(AppSourceOperationStatus.FAILED);
        assertThat(repository.findSnapshot(scenario.repositoryId(), 2L))
                .get().extracting(AppSourceSnapshot::status)
                .isEqualTo(AppSourceSnapshotStatus.FAILED);
        assertThat(repository.findSlot(scenario.repositoryId())).hasValueSatisfying(slot -> {
            assertThat(slot.activeGeneration()).isEqualTo(1L);
            assertThat(slot.pendingGeneration()).isNull();
        });
        assertThat(repository.findSnapshot(scenario.repositoryId(), 1L)).hasValueSatisfying(active -> {
            assertThat(active.status()).isEqualTo(AppSourceSnapshotStatus.ACTIVE);
            assertThat(active.expiresAt()).isEqualTo(OLD_EXPIRY);
        });
        assertThat(repository.findReplicas(scenario.repositoryId(), 2L))
                .extracting(AppSourceReplica::status)
                .containsExactly(
                        AppSourceReplicaStatus.FAILED,
                        AppSourceReplicaStatus.FAILED,
                        AppSourceReplicaStatus.FAILED);
        assertStableStepsTerminal(scenario);
    }

    @Test
    void firstReadyPromotionAndLastTwoConcurrentFailuresFinishPartialFailure() throws Exception {
        Scenario scenario = registerScenario("partial-failed");
        BarrierContext context = recorderWithLastTwoBarrier();
        Workspace readyWorkspace = new Workspace(
                new WorkspaceId("wrk_convergence_ready"), "convergence-partial-failed",
                "appsource:convergence-partial-failed", WorkspaceStatus.ACTIVE,
                NOW, NOW, scenario.serverA().value(), scenario.operation().traceId());
        transactions.executeWithoutResult(ignored -> context.recorder().recordSuccess(
                scenario.operation(), claimed(scenario, scenario.serverA()), "worker-a",
                readyWorkspace, INDEX_SHA, NOW.plusSeconds(1)));

        runLastTwoConcurrently(
                () -> recordFailure(context, scenario, scenario.serverB(), "worker-b", true),
                () -> recordFailure(context, scenario, scenario.serverC(), "worker-c", true));

        assertThat(repository.findOperation(scenario.operation().operationId()))
                .get().extracting(AppSourceOperation::status)
                .isEqualTo(AppSourceOperationStatus.PARTIAL_FAILED);
        assertThat(repository.findSnapshot(scenario.repositoryId(), 2L)).hasValueSatisfying(active -> {
            assertThat(active.status()).isEqualTo(AppSourceSnapshotStatus.ACTIVE);
            assertThat(active.indexSha256()).isEqualTo(INDEX_SHA);
        });
        assertThat(repository.findSlot(scenario.repositoryId())).hasValueSatisfying(slot -> {
            assertThat(slot.activeGeneration()).isEqualTo(2L);
            assertThat(slot.pendingGeneration()).isNull();
        });
        assertThat(repository.findSnapshot(scenario.repositoryId(), 1L))
                .get().extracting(AppSourceSnapshot::status)
                .isEqualTo(AppSourceSnapshotStatus.EXPIRED);
        assertThat(repository.findReplicas(scenario.repositoryId(), 2L))
                .extracting(AppSourceReplica::status)
                .containsExactly(
                        AppSourceReplicaStatus.READY,
                        AppSourceReplicaStatus.FAILED,
                        AppSourceReplicaStatus.FAILED);
        assertThat(workspaces.findById(readyWorkspace.workspaceId())).contains(readyWorkspace);
        assertStableStepsTerminal(scenario);
    }

    @Test
    void retryWaitsForOfflineTargetAcrossConcurrentResultAndRecoveryBeforeFinalConvergence() throws Exception {
        RetryScenario scenario = registerRetryScenario("retry-offline");
        BarrierContext context = recorderWithLastTwoBarrier();

        runLastTwoConcurrently(
                () -> {
                    context.lastTwo().set(true);
                    try {
                        transactions.executeWithoutResult(ignored -> context.recorder().recordFailure(
                                scenario.operation(), claimed(scenario.repositoryId(), scenario.serverB(), 1L),
                                "worker-b", "GIT_UNAVAILABLE", "源码副本物化失败", NOW.plusSeconds(1)));
                    } finally {
                        context.lastTwo().remove();
                    }
                },
                () -> {
                    context.lastTwo().set(true);
                    try {
                        transactions.executeWithoutResult(ignored -> context.recorder().recoverTerminalOperation(
                                scenario.operation().operationId(), NOW.plusSeconds(1)));
                    } finally {
                        context.lastTwo().remove();
                    }
                });

        assertThat(repository.findOperation(scenario.operation().operationId()))
                .get().extracting(AppSourceOperation::status)
                .isEqualTo(AppSourceOperationStatus.RUNNING);
        assertThat(repository.findSteps(scenario.operation().operationId()))
                .filteredOn(step -> step.linuxServerId().equals(scenario.serverC()))
                .allSatisfy(step -> assertThat(step.status()).isEqualTo(AppSourceStepStatus.PENDING));
        assertThat(repository.findStrandedOperations(10)).isEmpty();
        assertThat(repository.findClaimableReplicas(scenario.serverC(), NOW.plusSeconds(2), 10))
                .extracting(AppSourceReplica::linuxServerId)
                .containsExactly(scenario.serverC());

        AppSourceReplica claimedC = transactions.execute(ignored -> repository.claimReplica(
                scenario.repositoryId(), 1L, scenario.serverC(), scenario.operation().operationId(), "worker-c",
                NOW.plusSeconds(600), NOW.plusSeconds(2)).orElseThrow());
        transactions.executeWithoutResult(ignored -> new AppSourceReplicaResultRecorder(repository, workspaces)
                .recordFailure(
                        scenario.operation(), claimedC, "worker-c",
                        "GIT_UNAVAILABLE", "源码副本物化失败", NOW.plusSeconds(3)));

        assertThat(repository.findOperation(scenario.operation().operationId()))
                .get().extracting(AppSourceOperation::status)
                .isEqualTo(AppSourceOperationStatus.PARTIAL_FAILED);
        assertThat(repository.findSteps(scenario.operation().operationId()))
                .allSatisfy(step -> assertThat(step.status()).isIn(
                        AppSourceStepStatus.SUCCEEDED,
                        AppSourceStepStatus.FAILED,
                        AppSourceStepStatus.SKIPPED));
    }

    @Test
    void concurrentSameMaterializationOperationIdWritesOnceAndReturnsFrozenWinnerTargets() throws Exception {
        MaterializationScenario scenario = registerMaterializationScenario("materialize-idempotent");
        Set<LinuxServerId> firstObservedTargets = Set.of(scenario.serverA(), scenario.serverB());
        Set<LinuxServerId> secondObservedTargets = Set.of(scenario.serverA(), scenario.serverC());
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        MaterializationAttempt first;
        MaterializationAttempt second;
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<MaterializationAttempt> firstFuture = executor.submit(() -> registerAfterBarrier(
                    materializationRequest(scenario, firstObservedTargets), startBarrier));
            Future<MaterializationAttempt> secondFuture = executor.submit(() -> registerAfterBarrier(
                    materializationRequest(scenario, secondObservedTargets), startBarrier));
            first = firstFuture.get(10, TimeUnit.SECONDS);
            second = secondFuture.get(10, TimeUnit.SECONDS);
        }

        assertThat(List.of(first, second)).allSatisfy(attempt -> assertThat(attempt.failure()).isNull());
        assertThat(first.result().operation()).isEqualTo(second.result().operation());
        assertThat(first.result().targetServerIds()).isEqualTo(second.result().targetServerIds());
        Set<LinuxServerId> frozenTargets = Set.copyOf(repository.findReplicas(scenario.repositoryId(), 2L).stream()
                .map(AppSourceReplica::linuxServerId)
                .toList());
        assertThat(first.result().targetServerIds()).isEqualTo(frozenTargets);
        assertThat(frozenTargets).isIn(firstObservedTargets, secondObservedTargets);

        JdbcClient jdbc = JdbcClient.create(dataSource);
        assertThat(jdbc.sql("select count(*) from app_source_operations where operation_id = :operationId")
                .param("operationId", scenario.operationId()).query(Integer.class).single()).isOne();
        assertThat(jdbc.sql("select count(*) from app_source_snapshots "
                        + "where repository_id = :repositoryId and generation = 2")
                .param("repositoryId", scenario.repositoryId().value()).query(Integer.class).single()).isOne();
        assertThat(jdbc.sql("select count(*) from app_source_cleanup_tasks where operation_id = :operationId")
                .param("operationId", scenario.operationId()).query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("select count(*) from app_source_replicas "
                        + "where repository_id = :repositoryId and generation = 2")
                .param("repositoryId", scenario.repositoryId().value()).query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("select count(*) from app_source_operation_steps where operation_id = :operationId")
                .param("operationId", scenario.operationId()).query(Integer.class).single())
                .isEqualTo(2 * AppSourceReplicaStepCatalog.codes().size());
    }

    private Scenario registerScenario(String suffix) {
        CodeRepositoryId repositoryId = new CodeRepositoryId("repo_convergence_" + suffix);
        LinuxServerId serverA = new LinuxServerId("server_" + suffix + "_a");
        LinuxServerId serverB = new LinuxServerId("server_" + suffix + "_b");
        LinuxServerId serverC = new LinuxServerId("server_" + suffix + "_c");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                insert into code_repositories(
                    repository_id, git_url, name, english_name, repository_type,
                    deployment_mode, standard, created_at, updated_at)
                values (:repositoryId, :gitUrl, '并发收敛', :englishName,
                    'APPLICATION_CODE_REPOSITORY', 'EXTERNAL', false, :now, :now)
                """).param("repositoryId", repositoryId.value())
                .param("gitUrl", "https://git.example.test/convergence-" + suffix + ".git")
                .param("englishName", "convergence-" + suffix)
                .param("now", Timestamp.from(NOW)).update();
        for (LinuxServerId serverId : List.of(serverA, serverB, serverC)) {
            jdbc.sql("""
                    insert into linux_servers(
                        linux_server_id, name, status, capacity_summary_json, last_heartbeat_at,
                        trace_id, created_at, updated_at)
                    values (:serverId, :serverId, 'ONLINE', '{}', :now, 'trace-convergence', :now, :now)
                    """).param("serverId", serverId.value()).param("now", Timestamp.from(NOW)).update();
        }
        repository.insertSlotIfAbsent(new AppSourceRepositorySlot(
                repositoryId, 1L, null, 2L, "op-old-" + suffix, 0L, NOW.minusSeconds(3600), NOW));
        repository.saveSnapshot(new AppSourceSnapshot(
                repositoryId, 1L, "convergence-" + suffix, AppSourcePurpose.TEAM, USER_ID,
                "main", "old-commit", List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                "a".repeat(64), NOW.minusSeconds(3600), OLD_EXPIRY,
                AppSourceSnapshotStatus.ACTIVE, NOW.minusSeconds(3600), NOW));
        Set<LinuxServerId> targets = new LinkedHashSet<>(List.of(serverA, serverB, serverC));
        AppSourceMaterializationRegistrar.RegistrationResult registration = transactions.execute(ignored ->
                new AppSourceMaterializationRegistrar(repository).register(
                        new AppSourceMaterializationRegistrar.RegistrationRequest(
                                "op-convergence-" + suffix, APP_ID, repositoryId,
                                "convergence-" + suffix, USER_ID, AppSourceOperationType.UPDATE,
                                "request-convergence-" + suffix, 1L, "main", "new-commit",
                                List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                                AppSourcePurpose.TEAM, NOW.plusSeconds(48 * 3600L), targets,
                                "trace-convergence-" + suffix, NOW)));
        assertThat(registration).isNotNull();
        for (LinuxServerId serverId : targets) {
            String owner = serverId.equals(serverA) ? "worker-a"
                    : serverId.equals(serverB) ? "worker-b" : "worker-c";
            AppSourceReplica claimedReplica = transactions.execute(ignored -> repository.claimReplica(
                    repositoryId, 2L, serverId, registration.operation().operationId(),
                    owner, NOW.plusSeconds(600), NOW).orElseThrow());
            assertThat(claimedReplica).isNotNull();
        }
        return new Scenario(repositoryId, registration.operation(), serverA, serverB, serverC);
    }

    private RetryScenario registerRetryScenario(String suffix) {
        CodeRepositoryId repositoryId = new CodeRepositoryId("repo_convergence_" + suffix);
        LinuxServerId serverA = new LinuxServerId("server_" + suffix + "_a");
        LinuxServerId serverB = new LinuxServerId("server_" + suffix + "_b");
        LinuxServerId serverC = new LinuxServerId("server_" + suffix + "_c");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                insert into code_repositories(
                    repository_id, git_url, name, english_name, repository_type,
                    deployment_mode, standard, created_at, updated_at)
                values (:repositoryId, :gitUrl, '重试门禁', :englishName,
                    'APPLICATION_CODE_REPOSITORY', 'EXTERNAL', false, :now, :now)
                """).param("repositoryId", repositoryId.value())
                .param("gitUrl", "https://git.example.test/convergence-" + suffix + ".git")
                .param("englishName", "convergence-" + suffix)
                .param("now", Timestamp.from(NOW)).update();
        for (LinuxServerId serverId : List.of(serverA, serverB, serverC)) {
            jdbc.sql("""
                    insert into linux_servers(
                        linux_server_id, name, status, capacity_summary_json, last_heartbeat_at,
                        trace_id, created_at, updated_at)
                    values (:serverId, :serverId, 'ONLINE', '{}', :now, 'trace-retry-gate', :now, :now)
                    """).param("serverId", serverId.value()).param("now", Timestamp.from(NOW)).update();
        }
        repository.insertSlotIfAbsent(new AppSourceRepositorySlot(
                repositoryId, 1L, null, 2L, "op-old-" + suffix, 0L, NOW.minusSeconds(3600), NOW));
        repository.saveSnapshot(new AppSourceSnapshot(
                repositoryId, 1L, "convergence-" + suffix, AppSourcePurpose.TEAM, USER_ID,
                "main", "active-commit", List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                INDEX_SHA, NOW.minusSeconds(3600), OLD_EXPIRY,
                AppSourceSnapshotStatus.ACTIVE, NOW.minusSeconds(3600), NOW));
        WorkspaceId readyWorkspaceId = new WorkspaceId("wrk_" + suffix + "_a");
        workspaces.save(new Workspace(
                readyWorkspaceId, "convergence-" + suffix, "appsource:convergence-" + suffix,
                WorkspaceStatus.ACTIVE, NOW, NOW, serverA.value(), "trace-convergence-" + suffix));
        repository.insertReplicaIfAbsent(new AppSourceReplica(
                repositoryId, 1L, serverA, readyWorkspaceId,
                AppSourceReplicaStatus.READY, null, null, 1, null, null, null, NOW, NOW));
        for (LinuxServerId failedServer : List.of(serverB, serverC)) {
            repository.insertReplicaIfAbsent(new AppSourceReplica(
                    repositoryId, 1L, failedServer, null, AppSourceReplicaStatus.FAILED,
                    null, null, 1, NOW, "GIT_UNAVAILABLE", "源码副本物化失败", NOW, NOW));
        }
        AppSourceOperation operation = transactions.execute(ignored -> new AppSourceReplicaRetryRegistrar(repository)
                .register(new AppSourceReplicaRetryRegistrar.RetryRequest(
                        "op-convergence-" + suffix, APP_ID, repositoryId, 1L, USER_ID,
                        "request-convergence-" + suffix, Set.of(serverB, serverC),
                        "trace-convergence-" + suffix, NOW)));
        transactions.execute(ignored -> repository.claimReplica(
                repositoryId, 1L, serverB, operation.operationId(),
                "worker-b", NOW.plusSeconds(600), NOW).orElseThrow());
        return new RetryScenario(repositoryId, operation, serverA, serverB, serverC);
    }

    private MaterializationScenario registerMaterializationScenario(String suffix) {
        CodeRepositoryId repositoryId = new CodeRepositoryId("repo_convergence_" + suffix);
        LinuxServerId serverA = new LinuxServerId("server_" + suffix + "_a");
        LinuxServerId serverB = new LinuxServerId("server_" + suffix + "_b");
        LinuxServerId serverC = new LinuxServerId("server_" + suffix + "_c");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                insert into code_repositories(
                    repository_id, git_url, name, english_name, repository_type,
                    deployment_mode, standard, created_at, updated_at)
                values (:repositoryId, :gitUrl, '并发幂等', :englishName,
                    'APPLICATION_CODE_REPOSITORY', 'EXTERNAL', false, :now, :now)
                """).param("repositoryId", repositoryId.value())
                .param("gitUrl", "https://git.example.test/convergence-" + suffix + ".git")
                .param("englishName", "convergence-" + suffix)
                .param("now", Timestamp.from(NOW)).update();
        for (LinuxServerId serverId : List.of(serverA, serverB, serverC)) {
            jdbc.sql("""
                    insert into linux_servers(
                        linux_server_id, name, status, capacity_summary_json, last_heartbeat_at,
                        trace_id, created_at, updated_at)
                    values (:serverId, :serverId, 'ONLINE', '{}', :now, 'trace-materialize-idempotent', :now, :now)
                    """).param("serverId", serverId.value()).param("now", Timestamp.from(NOW)).update();
        }
        repository.insertSlotIfAbsent(new AppSourceRepositorySlot(
                repositoryId, 1L, null, 2L, "op-old-" + suffix, 0L, NOW.minusSeconds(3600), NOW));
        repository.saveSnapshot(new AppSourceSnapshot(
                repositoryId, 1L, "convergence-" + suffix, AppSourcePurpose.TEAM, USER_ID,
                "main", "active-commit", List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                INDEX_SHA, NOW.minusSeconds(3600), OLD_EXPIRY,
                AppSourceSnapshotStatus.ACTIVE, NOW.minusSeconds(3600), NOW));
        return new MaterializationScenario(
                repositoryId, "op-convergence-" + suffix, suffix, serverA, serverB, serverC);
    }

    private AppSourceMaterializationRegistrar.RegistrationRequest materializationRequest(
            MaterializationScenario scenario, Set<LinuxServerId> targets) {
        return new AppSourceMaterializationRegistrar.RegistrationRequest(
                scenario.operationId(), APP_ID, scenario.repositoryId(),
                "convergence-" + scenario.suffix(), USER_ID, AppSourceOperationType.UPDATE,
                "request-convergence-" + scenario.suffix(), 1L, "main", "new-commit",
                List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                AppSourcePurpose.TEAM, NOW.plusSeconds(48 * 3600L), targets,
                "trace-convergence-" + scenario.suffix(), NOW);
    }

    private MaterializationAttempt registerAfterBarrier(
            AppSourceMaterializationRegistrar.RegistrationRequest request,
            CyclicBarrier startBarrier) {
        try {
            startBarrier.await(5, TimeUnit.SECONDS);
            AppSourceMaterializationRegistrar.RegistrationResult result = transactions.execute(ignored ->
                    new AppSourceMaterializationRegistrar(repository).register(request));
            return new MaterializationAttempt(result, null);
        } catch (Throwable failure) {
            return new MaterializationAttempt(null, failure);
        }
    }

    private BarrierContext recorderWithLastTwoBarrier() {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ThreadLocal<Boolean> lastTwo = ThreadLocal.withInitial(() -> false);
        AppSourceRepository fencedRepository = (AppSourceRepository) Proxy.newProxyInstance(
                AppSourceRepository.class.getClassLoader(),
                new Class<?>[] {AppSourceRepository.class},
                (proxy, method, args) -> {
                    if ("findSlotForUpdate".equals(method.getName()) && Boolean.TRUE.equals(lastTwo.get())) {
                        barrier.await(5, TimeUnit.SECONDS);
                    }
                    try {
                        return method.invoke(repository, args);
                    } catch (InvocationTargetException invocationFailure) {
                        throw invocationFailure.getCause();
                    }
                });
        AppSourceReplicaResultRecorder recorder = new AppSourceReplicaResultRecorder(
                fencedRepository, workspaces, new AppSourceReplicaProgressRecorder(fencedRepository));
        return new BarrierContext(recorder, lastTwo);
    }

    private void recordFailure(
            BarrierContext context,
            Scenario scenario,
            LinuxServerId serverId,
            String leaseOwner,
            boolean participateInLastTwoBarrier) {
        context.lastTwo().set(participateInLastTwoBarrier);
        try {
            transactions.executeWithoutResult(ignored -> context.recorder().recordFailure(
                    scenario.operation(), claimed(scenario, serverId), leaseOwner,
                    "GIT_UNAVAILABLE", "源码副本物化失败", NOW.plusSeconds(2)));
        } finally {
            context.lastTwo().remove();
        }
    }

    private void runLastTwoConcurrently(Runnable second, Runnable third) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> secondFuture = executor.submit(second);
            Future<?> thirdFuture = executor.submit(third);
            secondFuture.get(10, TimeUnit.SECONDS);
            thirdFuture.get(10, TimeUnit.SECONDS);
        }
    }

    private AppSourceReplica claimed(Scenario scenario, LinuxServerId serverId) {
        return repository.findReplica(scenario.repositoryId(), 2L, serverId).orElseThrow();
    }

    private AppSourceReplica claimed(CodeRepositoryId repositoryId, LinuxServerId serverId, long generation) {
        return repository.findReplica(repositoryId, generation, serverId).orElseThrow();
    }

    private void assertStableStepsTerminal(Scenario scenario) {
        assertThat(repository.findSteps(scenario.operation().operationId()))
                .hasSize(3 * AppSourceReplicaStepCatalog.codes().size())
                .allSatisfy(step -> {
                    assertThat(step.status()).isIn(
                            com.enterprise.testagent.domain.appsource.AppSourceStepStatus.SUCCEEDED,
                            com.enterprise.testagent.domain.appsource.AppSourceStepStatus.FAILED,
                            com.enterprise.testagent.domain.appsource.AppSourceStepStatus.SKIPPED);
                    assertThat(step.safeSummary())
                            .doesNotContain("/private/", "stderr", "secret-key", "privateKey");
                });
    }

    private record BarrierContext(
            AppSourceReplicaResultRecorder recorder,
            ThreadLocal<Boolean> lastTwo) {
    }

    private record Scenario(
            CodeRepositoryId repositoryId,
            AppSourceOperation operation,
            LinuxServerId serverA,
            LinuxServerId serverB,
            LinuxServerId serverC) {
    }

    private record RetryScenario(
            CodeRepositoryId repositoryId,
            AppSourceOperation operation,
            LinuxServerId serverA,
            LinuxServerId serverB,
            LinuxServerId serverC) {
    }

    private record MaterializationScenario(
            CodeRepositoryId repositoryId,
            String operationId,
            String suffix,
            LinuxServerId serverA,
            LinuxServerId serverB,
            LinuxServerId serverC) {
    }

    private record MaterializationAttempt(
            AppSourceMaterializationRegistrar.RegistrationResult result,
            Throwable failure) {
    }
}
