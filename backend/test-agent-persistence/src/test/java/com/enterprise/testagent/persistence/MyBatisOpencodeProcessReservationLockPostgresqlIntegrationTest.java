package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServer;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessAssignmentConflictException;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessAtomicMutationPort;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessReservationLockPort;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.persistence.mybatis.MyBatisOpencodeProcessAtomicMutationPort;
import com.enterprise.testagent.persistence.mybatis.MyBatisOpencodeProcessReservationLockPort;
import com.enterprise.testagent.persistence.mybatis.OpencodeProcessAtomicMutationMapper;
import com.enterprise.testagent.persistence.mybatis.OpencodeProcessReservationLockMapper;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
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

/** 使用真实 PostgreSQL 验证 SELECT FOR UPDATE 会把同一权威行串行化。 */
@Testcontainers(disabledWithoutDocker = true)
class MyBatisOpencodeProcessReservationLockPostgresqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-22T08:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static DataSource dataSource;
    private static OpencodeProcessReservationLockPort lockPort;
    private static OpencodeProcessAtomicMutationPort atomicMutationPort;
    private static JdbcOpencodeProcessManagementRepository processRepository;
    private static JdbcUserRepository userRepository;
    private static TransactionTemplate transaction;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource postgresDataSource = new PGSimpleDataSource();
        postgresDataSource.setURL(POSTGRES.getJdbcUrl());
        postgresDataSource.setUser(POSTGRES.getUsername());
        postgresDataSource.setPassword(POSTGRES.getPassword());
        dataSource = postgresDataSource;
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        // 测试数据复用现有仓储写入，锁实现新增的关系型 SQL 仍只存在于 MyBatis XML。
        userRepository = new JdbcUserRepository(jdbc);
        processRepository = new JdbcOpencodeProcessManagementRepository(jdbc, new ObjectMapper());
        userRepository.save(new User(
                new UserId("usr_lock_test"),
                "auth_lock_test",
                "lock-test",
                "hash",
                null,
                null,
                null,
                UserStatus.ACTIVE,
                NOW,
                NOW));
        processRepository.saveLinuxServer(new LinuxServer(
                new LinuxServerId("linux-lock-test"),
                "lock test",
                LinuxServerStatus.READY,
                Map.of(),
                NOW,
                NOW,
                NOW,
                "trace_lock_test"));

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        lockPort = new MyBatisOpencodeProcessReservationLockPort(
                sqlSessionTemplate.getMapper(OpencodeProcessReservationLockMapper.class));
        atomicMutationPort = new MyBatisOpencodeProcessAtomicMutationPort(
                sqlSessionTemplate.getMapper(OpencodeProcessAtomicMutationMapper.class));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void sameUserRowLockBlocksSecondTransactionAndMissingRowsFailSafely() throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondReturned = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                assertThat(lockPort.lockUser(new UserId("usr_lock_test"))).isTrue();
                assertThat(lockPort.lockLinuxServer(new LinuxServerId("linux-lock-test"))).isTrue();
                firstLocked.countDown();
                await(releaseFirst);
            }));
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() -> transaction.executeWithoutResult(status -> {
                assertThat(lockPort.lockUser(new UserId("usr_lock_test"))).isTrue();
                secondReturned.countDown();
            }));

            assertThat(secondReturned.await(250, TimeUnit.MILLISECONDS)).isFalse();
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(secondReturned.getCount()).isZero();
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }

        transaction.executeWithoutResult(status -> {
            assertThat(lockPort.lockUser(new UserId("usr_missing_lock"))).isFalse();
            assertThat(lockPort.lockLinuxServer(new LinuxServerId("linux-missing-lock"))).isFalse();
        });
    }

    @Test
    void staleRuntimeStateWriteCannotRestoreCoordinatesAfterMigrationCommit() {
        AssignmentFixture fixture = assignmentFixture("stale", 4096, 4200, false);
        OpencodeServerProcess staleSnapshot = fixture.oldProcess();

        transaction.executeWithoutResult(status -> atomicMutationPort.compareAndSetAssignment(
                fixture.oldProcess(),
                fixture.oldBinding(),
                fixture.replacementProcess(),
                fixture.replacementBinding()));

        boolean updated = transaction.execute(status -> atomicMutationPort.compareAndSetRuntimeState(
                staleSnapshot,
                runtimeState(staleSnapshot, OpencodeServerProcessStatus.RUNNING, 9988L, "late old health")));

        assertThat(updated).isFalse();
        assertThat(processRepository.findOpencodeServerProcessById(staleSnapshot.processId())).get().satisfies(actual -> {
            assertThat(actual.containerId()).isEqualTo(fixture.replacementProcess().containerId());
            assertThat(actual.port()).isEqualTo(4200);
            assertThat(actual.status()).isEqualTo(OpencodeServerProcessStatus.STARTING);
        });
        assertThat(processRepository.findUserBinding(staleSnapshot.userId(), "opencode")).get().satisfies(actual -> {
            assertThat(actual.processId()).isEqualTo(staleSnapshot.processId());
            assertThat(actual.linuxServerId()).isEqualTo(staleSnapshot.linuxServerId());
            assertThat(actual.port()).isEqualTo(4200);
        });
    }

    @Test
    void runtimeStateCasAllowsNormalStartingToRunningTransition() {
        AssignmentFixture fixture = assignmentFixture("runtime_normal", 4098, 4202, false);
        OpencodeServerProcess starting = runtimeState(
                fixture.oldProcess(),
                OpencodeServerProcessStatus.STARTING,
                100L,
                "starting",
                "trace_runtime_normal");

        boolean candidateStored = transaction.execute(status ->
                atomicMutationPort.compareAndSetRuntimeState(fixture.oldProcess(), starting));
        OpencodeServerProcess persistedStarting = processRepository
                .findOpencodeServerProcessById(starting.processId())
                .orElseThrow();
        OpencodeServerProcess running = runtimeState(
                persistedStarting,
                OpencodeServerProcessStatus.RUNNING,
                100L,
                "running",
                "trace_runtime_normal");
        boolean runningStored = transaction.execute(status ->
                atomicMutationPort.compareAndSetRuntimeState(persistedStarting, running));

        assertThat(candidateStored).isTrue();
        assertThat(runningStored).isTrue();
        assertThat(processRepository.findOpencodeServerProcessById(running.processId())).get()
                .satisfies(actual -> {
                    assertThat(actual.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
                    assertThat(actual.pid()).isEqualTo(100L);
                    assertThat(actual.traceId()).isEqualTo("trace_runtime_normal");
                });
    }

    @Test
    void staleRuntimeLifecycleCannotOverwriteNewPidAtSameCoordinates() {
        AssignmentFixture fixture = assignmentFixture("runtime_generation", 4099, 4203, false);
        OpencodeServerProcess oldRunning = runtimeState(
                fixture.oldProcess(),
                OpencodeServerProcessStatus.RUNNING,
                100L,
                "old running",
                "trace_runtime_old");
        boolean oldRunningStored = Boolean.TRUE.equals(transaction.execute(status ->
                atomicMutationPort.compareAndSetRuntimeState(fixture.oldProcess(), oldRunning)));
        assertThat(oldRunningStored).isTrue();
        OpencodeServerProcess staleOldSnapshot = processRepository
                .findOpencodeServerProcessById(oldRunning.processId())
                .orElseThrow();

        OpencodeServerProcess newRunning = runtimeState(
                staleOldSnapshot,
                OpencodeServerProcessStatus.RUNNING,
                200L,
                "new running",
                "trace_runtime_new");
        boolean newRunningStored = Boolean.TRUE.equals(transaction.execute(status ->
                atomicMutationPort.compareAndSetRuntimeState(staleOldSnapshot, newRunning)));
        assertThat(newRunningStored).isTrue();

        OpencodeServerProcess lateOldHealth = runtimeState(
                staleOldSnapshot,
                OpencodeServerProcessStatus.RUNNING,
                100L,
                "late old health",
                "trace_runtime_late_old");
        boolean staleUpdated = transaction.execute(status ->
                atomicMutationPort.compareAndSetRuntimeState(staleOldSnapshot, lateOldHealth));

        assertThat(staleUpdated).isFalse();
        assertThat(processRepository.findOpencodeServerProcessById(newRunning.processId())).get()
                .satisfies(actual -> {
                    assertThat(actual.containerId()).isEqualTo(newRunning.containerId());
                    assertThat(actual.port()).isEqualTo(newRunning.port());
                    assertThat(actual.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
                    assertThat(actual.pid()).isEqualTo(200L);
                    assertThat(actual.traceId()).isEqualTo("trace_runtime_new");
                });
    }

    @Test
    void staleAssignmentMigrationCannotMoveNewLifecycleAtSameCoordinates() {
        AssignmentFixture fixture = assignmentFixture("assignment_generation", 4100, 4204, false);
        OpencodeServerProcess newRunning = runtimeState(
                fixture.oldProcess(),
                OpencodeServerProcessStatus.RUNNING,
                200L,
                "new lifecycle running",
                "trace_assignment_new");
        boolean newRunningStored = Boolean.TRUE.equals(transaction.execute(status ->
                atomicMutationPort.compareAndSetRuntimeState(fixture.oldProcess(), newRunning)));
        assertThat(newRunningStored).isTrue();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                        atomicMutationPort.compareAndSetAssignment(
                                fixture.oldProcess(),
                                fixture.oldBinding(),
                                fixture.replacementProcess(),
                                fixture.replacementBinding())))
                .isInstanceOf(OpencodeProcessAssignmentConflictException.class);

        assertThat(processRepository.findOpencodeServerProcessById(newRunning.processId())).get()
                .satisfies(actual -> {
                    assertThat(actual.containerId()).isEqualTo(newRunning.containerId());
                    assertThat(actual.port()).isEqualTo(newRunning.port());
                    assertThat(actual.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
                    assertThat(actual.pid()).isEqualTo(200L);
                    assertThat(actual.traceId()).isEqualTo("trace_assignment_new");
                });
        assertThat(processRepository.findUserBinding(newRunning.userId(), "opencode")).get()
                .satisfies(actual -> {
                    assertThat(actual.linuxServerId()).isEqualTo(fixture.oldBinding().linuxServerId());
                    assertThat(actual.port()).isEqualTo(fixture.oldBinding().port());
                });
    }

    @Test
    void bindingCasFailureRollsBackEarlierProcessCasInSameTransaction() {
        AssignmentFixture fixture = assignmentFixture("rollback", 4097, 4201, true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                        atomicMutationPort.compareAndSetAssignment(
                                fixture.oldProcess(),
                                fixture.oldBinding(),
                                fixture.replacementProcess(),
                                fixture.replacementBinding())))
                .isInstanceOf(OpencodeProcessAssignmentConflictException.class);

        assertThat(processRepository.findOpencodeServerProcessById(fixture.oldProcess().processId())).get()
                .satisfies(actual -> {
                    assertThat(actual.containerId()).isEqualTo(fixture.oldProcess().containerId());
                    assertThat(actual.port()).isEqualTo(fixture.oldProcess().port());
                    assertThat(actual.status()).isEqualTo(fixture.oldProcess().status());
                });
        assertThat(processRepository.findUserBinding(fixture.oldBinding().userId(), "opencode")).get()
                .satisfies(actual -> assertThat(actual.port()).isEqualTo(fixture.oldBinding().port() + 1));
    }

    private static AssignmentFixture assignmentFixture(
            String suffix,
            int oldPort,
            int replacementPort,
            boolean mismatchStoredBinding) {
        UserId userId = new UserId("usr_cas_" + suffix);
        LinuxServerId linuxServerId = new LinuxServerId("linux-cas-" + suffix);
        OpencodeContainerId oldContainerId = new OpencodeContainerId("ctr_cas_old_" + suffix);
        OpencodeContainerId replacementContainerId = new OpencodeContainerId("ctr_cas_new_" + suffix);
        userRepository.save(new User(
                userId,
                "auth_cas_" + suffix,
                "cas-" + suffix,
                "hash",
                null,
                null,
                null,
                UserStatus.ACTIVE,
                NOW,
                NOW));
        processRepository.saveLinuxServer(new LinuxServer(
                linuxServerId,
                "cas " + suffix,
                LinuxServerStatus.READY,
                Map.of(),
                NOW,
                NOW,
                NOW,
                "trace_cas_" + suffix));
        processRepository.saveContainer(container(oldContainerId, linuxServerId, oldPort));
        processRepository.saveContainer(container(replacementContainerId, linuxServerId, replacementPort));

        OpencodeProcessId processId = new OpencodeProcessId("ocp_cas_" + suffix);
        OpencodeServerProcess oldProcess = new OpencodeServerProcess(
                processId,
                userId,
                linuxServerId,
                oldContainerId,
                oldPort,
                null,
                "http://127.0.0.1:" + oldPort,
                OpencodeServerProcessStatus.STOPPED,
                "/tmp/session/" + suffix,
                "/tmp/config/" + suffix,
                NOW,
                NOW,
                "old",
                NOW,
                NOW,
                "trace_cas_" + suffix);
        UserOpencodeProcessBinding oldBinding = new UserOpencodeProcessBinding(
                userId,
                "opencode",
                processId,
                linuxServerId,
                oldPort,
                UserOpencodeProcessBindingStatus.ACTIVE,
                NOW,
                NOW,
                "trace_cas_" + suffix);
        OpencodeServerProcess replacementProcess = new OpencodeServerProcess(
                processId,
                userId,
                linuxServerId,
                replacementContainerId,
                replacementPort,
                null,
                "http://127.0.0.1:" + replacementPort,
                OpencodeServerProcessStatus.STARTING,
                oldProcess.sessionPath(),
                oldProcess.configPath(),
                NOW.plusSeconds(1),
                NOW.plusSeconds(1),
                "reserved",
                NOW,
                NOW.plusSeconds(1),
                "trace_cas_" + suffix);
        UserOpencodeProcessBinding replacementBinding = new UserOpencodeProcessBinding(
                userId,
                "opencode",
                processId,
                linuxServerId,
                replacementPort,
                UserOpencodeProcessBindingStatus.ACTIVE,
                NOW,
                NOW.plusSeconds(1),
                "trace_cas_" + suffix);
        processRepository.saveOpencodeServerProcess(oldProcess);
        processRepository.saveUserBinding(mismatchStoredBinding
                ? new UserOpencodeProcessBinding(
                        oldBinding.userId(),
                        oldBinding.agentId(),
                        oldBinding.processId(),
                        oldBinding.linuxServerId(),
                        oldBinding.port() + 1,
                        oldBinding.status(),
                        oldBinding.createdAt(),
                        oldBinding.updatedAt(),
                        oldBinding.traceId())
                : oldBinding);
        return new AssignmentFixture(oldProcess, oldBinding, replacementProcess, replacementBinding);
    }

    private static OpencodeContainer container(
            OpencodeContainerId containerId,
            LinuxServerId linuxServerId,
            int port) {
        return new OpencodeContainer(
                containerId,
                linuxServerId,
                containerId.value(),
                port,
                port,
                1,
                0,
                OpencodeContainerStatus.READY,
                NOW,
                NOW,
                NOW,
                "trace_cas");
    }

    private static OpencodeServerProcess runtimeState(
            OpencodeServerProcess assignment,
            OpencodeServerProcessStatus status,
            Long pid,
            String message) {
        return runtimeState(assignment, status, pid, message, assignment.traceId());
    }

    private static OpencodeServerProcess runtimeState(
            OpencodeServerProcess assignment,
            OpencodeServerProcessStatus status,
            Long pid,
            String message,
            String traceId) {
        return new OpencodeServerProcess(
                assignment.processId(),
                assignment.userId(),
                assignment.linuxServerId(),
                assignment.containerId(),
                assignment.port(),
                pid,
                assignment.baseUrl(),
                status,
                assignment.sessionPath(),
                assignment.configPath(),
                assignment.startedAt(),
                NOW.plusSeconds(2),
                message,
                assignment.createdAt(),
                NOW.plusSeconds(2),
                traceId);
    }

    private record AssignmentFixture(
            OpencodeServerProcess oldProcess,
            UserOpencodeProcessBinding oldBinding,
            OpencodeServerProcess replacementProcess,
            UserOpencodeProcessBinding replacementBinding) { }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
