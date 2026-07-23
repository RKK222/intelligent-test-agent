package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessReservationLockPort;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class OpencodeProcessReservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UserId USER = new UserId("usr_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void initialReservationLocksUserThenServerAndAvoidsDatabaseAndManagerPorts() {
        RepositoryFixture fixture = new RepositoryFixture();
        fixture.databaseOccupiedPorts.add(4096);
        RecordingLockPort locks = new RecordingLockPort();
        OpencodeProcessReservationService service = new OpencodeProcessReservationService(
                fixture.repository, locks, CLOCK);

        OpencodeProcessReservation reservation = service.reserveInitial(
                        USER,
                        container("ctr_01", 4096, 4099),
                        port -> "http://10.8.0.12:" + port,
                        "/data/opencode/session/users/ucid_001",
                        "/data/opencode/session/users/ucid_001/.testagent-runtime/current-public-config",
                        Set.of(4097),
                        TRACE_ID)
                .orElseThrow();

        assertThat(locks.calls).containsExactly("user:" + USER.value(), "server:10.8.0.12");
        assertThat(reservation.process().port()).isEqualTo(4098);
        assertThat(reservation.process().pid()).isNull();
        assertThat(reservation.process().status()).isEqualTo(OpencodeServerProcessStatus.STARTING);
        assertThat(reservation.binding().processId()).isEqualTo(reservation.process().processId());
        assertThat(fixture.bindings.get(USER.value())).isEqualTo(reservation.binding());
        assertThat(reservation.concurrentWinner()).isFalse();
    }

    @Test
    void initialReservationReturnsConcurrentWinnerInsteadOfAllocatingAgain() {
        RepositoryFixture fixture = new RepositoryFixture();
        OpencodeServerProcess winner = process(USER, "ocp_existing", "ctr_01", 4096, NOW.minusSeconds(60));
        fixture.processes.put(winner.processId().value(), winner);
        fixture.bindings.put(USER.value(), binding(USER, winner, NOW.minusSeconds(60)));
        OpencodeProcessReservationService service = new OpencodeProcessReservationService(
                fixture.repository, new RecordingLockPort(), CLOCK);

        OpencodeProcessReservation reservation = service.reserveInitial(
                        USER,
                        container("ctr_01", 4096, 4099),
                        port -> "http://10.8.0.12:" + port,
                        winner.sessionPath(),
                        winner.configPath(),
                        Set.of(),
                        TRACE_ID)
                .orElseThrow();

        assertThat(reservation.process()).isEqualTo(winner);
        assertThat(reservation.concurrentWinner()).isTrue();
        assertThat(fixture.processes).hasSize(1);
    }

    @Test
    void migrationAvoidsManagerOnlyPortAndPreservesIdentityAndCreatedTimes() {
        RepositoryFixture fixture = new RepositoryFixture();
        Instant processCreatedAt = NOW.minusSeconds(300);
        Instant bindingCreatedAt = NOW.minusSeconds(240);
        OpencodeServerProcess oldProcess = process(USER, "ocp_existing", "ctr_old", 4096, processCreatedAt);
        UserOpencodeProcessBinding oldBinding = binding(USER, oldProcess, bindingCreatedAt);
        fixture.processes.put(oldProcess.processId().value(), oldProcess);
        fixture.bindings.put(USER.value(), oldBinding);
        fixture.databaseOccupiedPorts.add(4200);
        OpencodeProcessReservationService service = new OpencodeProcessReservationService(
                fixture.repository, new RecordingLockPort(), CLOCK);

        OpencodeProcessReservation reservation = service.reserveMigration(
                        oldBinding,
                        oldProcess,
                        container("ctr_new", 4200, 4203),
                        port -> "http://10.8.0.12:" + port,
                        Set.of(4201),
                        TRACE_ID)
                .orElseThrow();

        assertThat(reservation.concurrentWinner()).isFalse();
        assertThat(reservation.process().processId()).isEqualTo(oldProcess.processId());
        assertThat(reservation.process().port()).isEqualTo(4202);
        assertThat(reservation.process().createdAt()).isEqualTo(processCreatedAt);
        assertThat(reservation.binding().createdAt()).isEqualTo(bindingCreatedAt);
        assertThat(fixture.bindings.get(USER.value()).port()).isEqualTo(4202);
        assertThat(fixture.processes.get("ocp_existing").containerId().value()).isEqualTo("ctr_new");
    }

    @Test
    void concurrentSameUserGetsOneWinnerAndConcurrentUsersOnServerGetDistinctPorts() throws Exception {
        RepositoryFixture sameUserFixture = new RepositoryFixture();
        OpencodeProcessReservationService sameUserService = new OpencodeProcessReservationService(
                sameUserFixture.repository, new RecordingLockPort(), CLOCK);
        List<OpencodeProcessReservation> sameUser = runConcurrently(
                () -> sameUserService.reserveInitial(
                                USER,
                                container("ctr_01", 4096, 4099),
                                port -> "http://10.8.0.12:" + port,
                                "/session/ucid_001",
                                "/config",
                                Set.of(),
                                TRACE_ID)
                        .orElseThrow(),
                () -> sameUserService.reserveInitial(
                                USER,
                                container("ctr_01", 4096, 4099),
                                port -> "http://10.8.0.12:" + port,
                                "/session/ucid_001",
                                "/config",
                                Set.of(),
                                TRACE_ID)
                        .orElseThrow());

        assertThat(sameUser).extracting(item -> item.process().processId()).containsOnly(sameUser.getFirst().process().processId());
        assertThat(sameUserFixture.processes).hasSize(1);

        RepositoryFixture sameServerFixture = new RepositoryFixture();
        OpencodeProcessReservationService sameServerService = new OpencodeProcessReservationService(
                sameServerFixture.repository, new RecordingLockPort(), CLOCK);
        UserId secondUser = new UserId("usr_2222222222222222");
        List<OpencodeProcessReservation> sameServer = runConcurrently(
                () -> sameServerService.reserveInitial(
                                USER, container("ctr_01", 4096, 4099), port -> "http://10.8.0.12:" + port,
                                "/session/ucid_001", "/config", Set.of(), TRACE_ID).orElseThrow(),
                () -> sameServerService.reserveInitial(
                                secondUser, container("ctr_01", 4096, 4099), port -> "http://10.8.0.12:" + port,
                                "/session/ucid_002", "/config", Set.of(), TRACE_ID).orElseThrow());

        assertThat(sameServer).extracting(item -> item.process().port()).doesNotHaveDuplicates();
        assertThat(sameServerFixture.processes).hasSize(2);
    }

    private static List<OpencodeProcessReservation> runConcurrently(
            java.util.concurrent.Callable<OpencodeProcessReservation> first,
            java.util.concurrent.Callable<OpencodeProcessReservation> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<OpencodeProcessReservation> firstFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return first.call();
            });
            Future<OpencodeProcessReservation> secondFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return second.call();
            });
            ready.await();
            start.countDown();
            return List.of(firstFuture.get(), secondFuture.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static OpencodeContainer container(String id, int start, int end) {
        return new OpencodeContainer(
                new OpencodeContainerId(id),
                new LinuxServerId("10.8.0.12"),
                id,
                start,
                end,
                end - start + 1,
                0,
                com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerStatus.READY,
                NOW,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static OpencodeServerProcess process(
            UserId userId,
            String processId,
            String containerId,
            int port,
            Instant createdAt) {
        return new OpencodeServerProcess(
                new OpencodeProcessId(processId), userId, new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId(containerId), port, 12345L, "http://10.8.0.12:" + port,
                OpencodeServerProcessStatus.UNHEALTHY, "/session/ucid_001", "/config", NOW.minusSeconds(60),
                NOW.minusSeconds(30), "unhealthy", createdAt, NOW.minusSeconds(30), TRACE_ID);
    }

    private static UserOpencodeProcessBinding binding(
            UserId userId,
            OpencodeServerProcess process,
            Instant createdAt) {
        return new UserOpencodeProcessBinding(
                userId, "opencode", process.processId(), process.linuxServerId(), process.port(),
                UserOpencodeProcessBindingStatus.ACTIVE, createdAt, NOW.minusSeconds(30), TRACE_ID);
    }

    private static final class RecordingLockPort implements OpencodeProcessReservationLockPort {
        private final List<String> calls = java.util.Collections.synchronizedList(new ArrayList<>());

        @Override
        public boolean lockUser(UserId userId) {
            calls.add("user:" + userId.value());
            return true;
        }

        @Override
        public boolean lockLinuxServer(LinuxServerId linuxServerId) {
            calls.add("server:" + linuxServerId.value());
            return true;
        }
    }

    private static final class RepositoryFixture {
        private final OpencodeProcessManagementRepository repository = mock(OpencodeProcessManagementRepository.class);
        private final Map<String, OpencodeServerProcess> processes = new ConcurrentHashMap<>();
        private final Set<Integer> databaseOccupiedPorts = ConcurrentHashMap.newKeySet();
        private final Map<String, UserOpencodeProcessBinding> bindings = new ConcurrentHashMap<>();

        private RepositoryFixture() {
            when(repository.findUserBinding(any(UserId.class), anyString()))
                    .thenAnswer(invocation -> Optional.ofNullable(bindings.get(
                            invocation.<UserId>getArgument(0).value())));
            when(repository.findOpencodeServerProcessById(any(OpencodeProcessId.class)))
                    .thenAnswer(invocation -> Optional.ofNullable(processes.get(
                            invocation.<OpencodeProcessId>getArgument(0).value())));
            when(repository.findOccupiedPorts(any(LinuxServerId.class), any(OpencodeContainerId.class)))
                    .thenAnswer(invocation -> List.copyOf(databaseOccupiedPorts));
            when(repository.saveOpencodeServerProcess(any(OpencodeServerProcess.class)))
                    .thenAnswer(invocation -> {
                        OpencodeServerProcess process = invocation.getArgument(0);
                        processes.put(process.processId().value(), process);
                        databaseOccupiedPorts.add(process.port());
                        return process;
                    });
            when(repository.saveUserBinding(any(UserOpencodeProcessBinding.class)))
                    .thenAnswer(invocation -> {
                        UserOpencodeProcessBinding saved = invocation.getArgument(0);
                        bindings.put(saved.userId().value(), saved);
                        return saved;
                    });
        }
    }
}
