package com.enterprise.testagent.opencode.runtime.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/** 验证 XXL 扫描按固定服务器分组，并把单服务器批次限制为 50。 */
class NightExecutionDispatchCoordinatorTest {

    @Test
    void scansAtMostFiveHundredAndChunksEachTargetServer() {
        Instant now = Instant.parse("2026-07-18T13:15:00Z");
        List<NightExecutionTask> due = new ArrayList<>();
        for (int index = 0; index < 51; index++) due.add(task(index, "linux-a"));
        due.add(task(99, "linux-b"));
        NightExecutionTaskRepository repository = mock(NightExecutionTaskRepository.class);
        when(repository.findScheduledDue(now, 500)).thenReturn(due);
        List<Call> calls = new CopyOnWriteArrayList<>();
        NightExecutionDispatchGateway gateway = (linuxServerId, taskIds, traceId) -> {
            calls.add(new Call(linuxServerId, taskIds));
            return Mono.just(new NightExecutionDispatchBatchResult(
                    linuxServerId,
                    taskIds.stream().map(id -> new NightExecutionDispatchResult(
                            id, NightExecutionDispatchStatus.STARTED, "run_" + id.value(), null)).toList()));
        };
        NightExecutionDispatchCoordinator coordinator = new NightExecutionDispatchCoordinator(
                repository, gateway, Clock.fixed(now, ZoneOffset.UTC));

        NightExecutionDispatchCoordinator.Result result =
                coordinator.dispatchDue("trace_dispatch_scan", () -> false);

        assertThat(calls).hasSize(3);
        assertThat(calls).filteredOn(call -> call.linuxServerId().equals("linux-a"))
                .extracting(call -> call.taskIds().size())
                .containsExactlyInAnyOrder(50, 1);
        assertThat(result.scanned()).isEqualTo(52);
        assertThat(result.started()).isEqualTo(52);
        assertThat(result.failed()).isZero();
    }

    @Test
    void oneTargetServerFailureDoesNotBlockOtherServers() {
        Instant now = Instant.parse("2026-07-18T13:15:00Z");
        List<NightExecutionTask> due = List.of(task(1, "linux-a"), task(2, "linux-b"));
        NightExecutionTaskRepository repository = mock(NightExecutionTaskRepository.class);
        when(repository.findScheduledDue(now, 500)).thenReturn(due);
        List<String> calledServers = new CopyOnWriteArrayList<>();
        NightExecutionDispatchGateway gateway = (linuxServerId, taskIds, traceId) -> {
            calledServers.add(linuxServerId);
            if (linuxServerId.equals("linux-a")) {
                return Mono.error(new IllegalStateException("server offline"));
            }
            NightExecutionTaskId taskId = taskIds.get(0);
            return Mono.just(new NightExecutionDispatchBatchResult(
                    linuxServerId,
                    List.of(new NightExecutionDispatchResult(
                            taskId, NightExecutionDispatchStatus.STARTED, "run_" + taskId.value(), null))));
        };
        NightExecutionDispatchCoordinator coordinator = new NightExecutionDispatchCoordinator(
                repository, gateway, Clock.fixed(now, ZoneOffset.UTC));

        NightExecutionDispatchCoordinator.Result result =
                coordinator.dispatchDue("trace_dispatch_isolation", () -> false);

        assertThat(calledServers).containsExactlyInAnyOrder("linux-a", "linux-b");
        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.started()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void offlineLocalClientWaitsWithoutFallingBackToServerDispatch() {
        Instant now = Instant.parse("2026-07-18T13:15:00Z");
        LocalClientInstanceId clientInstanceId = new LocalClientInstanceId("lci_night_offline");
        NightExecutionTask localTask = localTask(201, clientInstanceId);
        NightExecutionTaskRepository repository = mock(NightExecutionTaskRepository.class);
        when(repository.findScheduledDue(now, 500)).thenReturn(List.of(localTask));
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        when(connections.find(clientInstanceId)).thenReturn(java.util.Optional.empty());
        AtomicBoolean dispatched = new AtomicBoolean();
        NightExecutionDispatchGateway gateway = (target, taskIds, traceId) -> {
            dispatched.set(true);
            return Mono.error(new AssertionError("offline local task must not use server fallback"));
        };
        NightExecutionDispatchCoordinator coordinator = new NightExecutionDispatchCoordinator(
                repository, gateway, Clock.fixed(now, ZoneOffset.UTC));
        coordinator.configureLocalClientConnections(connections);

        NightExecutionDispatchCoordinator.Result result =
                coordinator.dispatchDue("trace_local_offline", () -> false);

        assertThat(dispatched).isFalse();
        assertThat(result.scanned()).isOne();
        assertThat(result.targetServers()).isZero();
        assertThat(result.started()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    void reconnectedLocalClientDispatchesToItsOwningBackendOnly() {
        Instant now = Instant.parse("2026-07-18T13:15:00Z");
        LocalClientInstanceId clientInstanceId = new LocalClientInstanceId("lci_night_reconnected");
        NightExecutionTask localTask = localTask(202, clientInstanceId);
        NightExecutionTaskRepository repository = mock(NightExecutionTaskRepository.class);
        when(repository.findScheduledDue(now, 500)).thenReturn(List.of(localTask));
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        when(connections.find(clientInstanceId)).thenReturn(java.util.Optional.of(new LocalClientConnectionRoute(
                clientInstanceId,
                localTask.ownerUserId(),
                new BackendProcessId("bjp_local_owner"),
                19,
                "10.0.0.5:43110",
                List.of("192.168.1.20"),
                4096,
                LocalClientProcessStatus.RUNNING,
                1234L,
                now.minusSeconds(30),
                true,
                now.minusSeconds(60),
                now)));
        List<String> targets = new CopyOnWriteArrayList<>();
        NightExecutionDispatchGateway gateway = (target, taskIds, traceId) -> {
            targets.add(target);
            return Mono.just(new NightExecutionDispatchBatchResult(
                    target,
                    List.of(new NightExecutionDispatchResult(
                            taskIds.getFirst(), NightExecutionDispatchStatus.STARTED, "run_local", null))));
        };
        NightExecutionDispatchCoordinator coordinator = new NightExecutionDispatchCoordinator(
                repository, gateway, Clock.fixed(now, ZoneOffset.UTC));
        coordinator.configureLocalClientConnections(connections);

        NightExecutionDispatchCoordinator.Result result =
                coordinator.dispatchDue("trace_local_reconnected", () -> false);

        assertThat(targets).containsExactly("local:" + clientInstanceId.value());
        assertThat(result.started()).isOne();
        assertThat(result.failed()).isZero();
    }

    private NightExecutionTask task(int index, String server) {
        Instant created = Instant.parse("2026-07-18T12:00:00Z").plusSeconds(index);
        return new NightExecutionTask(
                new NightExecutionTaskId("net_scan_task_" + index), new UserId("usr_scan_" + index),
                new SessionId("ses_scan_" + index), new WorkspaceId("wrk_scan_" + index),
                "request-scan-" + index, "夜间任务", "执行任务", "{}",
                NightExecutionTaskStatus.SCHEDULED, Instant.parse("2026-07-18T13:00:00Z"),
                Instant.parse("2026-07-18T13:15:00Z"), Instant.parse("2026-07-18T23:00:00Z"),
                server, null, null, 0, false, null, null, null, null, null,
                "trace_scan_" + index, created, created);
    }

    private NightExecutionTask localTask(int index, LocalClientInstanceId clientInstanceId) {
        Instant created = Instant.parse("2026-07-18T12:00:00Z").plusSeconds(index);
        return new NightExecutionTask(
                new NightExecutionTaskId("net_local_task_" + index), new UserId("usr_local_" + index),
                new SessionId("ses_local_" + index), new WorkspaceId("wrk_local_" + index),
                "request-local-" + index, "本地夜间任务", "执行本地任务", "{}",
                com.enterprise.testagent.domain.nightexecution.NightExecutionScheduleMode.NIGHT_WINDOW,
                NightExecutionTaskStatus.SCHEDULED,
                Instant.parse("2026-07-18T13:00:00Z"), Instant.parse("2026-07-18T13:15:00Z"),
                Instant.parse("2026-07-18T23:00:00Z"), null, null, null, 0, false,
                null, null, null, null, 0L, null, null, null, null,
                "trace_local_" + index, created, created,
                RuntimeKind.LOCAL_CLIENT, clientInstanceId);
    }

    private record Call(String linuxServerId, List<NightExecutionTaskId> taskIds) {
        private Call {
            taskIds = List.copyOf(taskIds);
        }
    }
}
