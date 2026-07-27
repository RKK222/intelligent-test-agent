package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.opencodeprocess.ContainerManagerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;

class OpencodeProcessAutoRecoveryServiceTest {

    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-01");
    private static final OpencodeContainerId CONTAINER_ID = new OpencodeContainerId("ctr_01");
    private static final ContainerManagerId MANAGER_ID = new ContainerManagerId("mgr_1234567890abcdef");
    private static final Instant NOW = Instant.parse("2026-07-27T08:00:00Z");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void recoversOnlyRunningIntentWithActiveBindingOncePerManagerConnection() {
        OpencodeProcessManagementRepository repository = mock(OpencodeProcessManagementRepository.class);
        UserOpencodeProcessAssignmentService assignmentService = mock(UserOpencodeProcessAssignmentService.class);
        OpencodeServerProcess running = process("ocp_running_1234567890", "usr_running", 4096,
                OpencodeServerProcessStatus.RUNNING);
        OpencodeServerProcess starting = process("ocp_starting_123456789", "usr_starting", 4097,
                OpencodeServerProcessStatus.STARTING);
        OpencodeServerProcess inactive = process("ocp_inactive_123456789", "usr_inactive", 4098,
                OpencodeServerProcessStatus.RUNNING);
        stubProcessPages(repository, List.of(running, inactive), List.of(starting));
        when(repository.findUserBindingsByProcessIds(any())).thenReturn(Map.of(
                running.processId(), binding(running, UserOpencodeProcessBindingStatus.ACTIVE),
                starting.processId(), binding(starting, UserOpencodeProcessBindingStatus.ACTIVE),
                inactive.processId(), binding(inactive, UserOpencodeProcessBindingStatus.INACTIVE)));
        OpencodeProcessAutoRecoveryService service = new OpencodeProcessAutoRecoveryService(
                repository,
                assignmentService,
                Schedulers.immediate(),
                ignored -> { });

        service.prepareRecovery(MANAGER_ID, SERVER_ID, CONTAINER_ID, TRACE_ID);
        // manager 刚重启时的前端状态探测可能先把数据库运行态改掉，恢复仍应使用注册时冻结的候选。
        stubProcessPages(repository, List.of(), List.of());
        service.requestPreparedRecovery(MANAGER_ID, TRACE_ID);
        service.requestPreparedRecovery(MANAGER_ID, TRACE_ID);

        verify(assignmentService).initialize(running.userId(), "opencode", TRACE_ID);
        verify(assignmentService).initialize(starting.userId(), "opencode", TRACE_ID);
        verify(assignmentService, never()).initialize(inactive.userId(), "opencode", TRACE_ID);

        service.managerDisconnected(MANAGER_ID);
        stubProcessPages(repository, List.of(running, inactive), List.of(starting));
        service.prepareRecovery(MANAGER_ID, SERVER_ID, CONTAINER_ID, TRACE_ID);
        service.requestPreparedRecovery(MANAGER_ID, TRACE_ID);

        verify(assignmentService, org.mockito.Mockito.times(2))
                .initialize(running.userId(), "opencode", TRACE_ID);
        verify(assignmentService, org.mockito.Mockito.times(2))
                .initialize(starting.userId(), "opencode", TRACE_ID);
    }

    @Test
    void retriesFailedRecoveryOnceWithoutRepeatingTheDatabaseScan() {
        OpencodeProcessManagementRepository repository = mock(OpencodeProcessManagementRepository.class);
        UserOpencodeProcessAssignmentService assignmentService = mock(UserOpencodeProcessAssignmentService.class);
        OpencodeServerProcess running = process("ocp_retry_1234567890123", "usr_retry", 4096,
                OpencodeServerProcessStatus.RUNNING);
        stubProcessPages(repository, List.of(running), List.of());
        when(repository.findUserBindingsByProcessIds(any())).thenReturn(Map.of(
                running.processId(), binding(running, UserOpencodeProcessBindingStatus.ACTIVE)));
        doThrow(new IllegalStateException("manager warming up"))
                .doReturn(null)
                .when(assignmentService)
                .initialize(running.userId(), "opencode", TRACE_ID);
        List<Duration> waits = new ArrayList<>();
        OpencodeProcessAutoRecoveryService service = new OpencodeProcessAutoRecoveryService(
                repository,
                assignmentService,
                Schedulers.immediate(),
                waits::add);

        service.prepareRecovery(MANAGER_ID, SERVER_ID, CONTAINER_ID, TRACE_ID);
        service.requestPreparedRecovery(MANAGER_ID, TRACE_ID);

        verify(assignmentService, org.mockito.Mockito.times(2))
                .initialize(running.userId(), "opencode", TRACE_ID);
        assertThat(waits).containsExactly(Duration.ofSeconds(1));
        verify(repository, org.mockito.Mockito.times(2))
                .findOpencodeServerProcesses(any(OpencodeServerProcessFilter.class), any(PageRequest.class));
    }

    @Test
    void databaseScanFailureDoesNotBlockManagerConnection() {
        OpencodeProcessManagementRepository repository = mock(OpencodeProcessManagementRepository.class);
        UserOpencodeProcessAssignmentService assignmentService = mock(UserOpencodeProcessAssignmentService.class);
        when(repository.findOpencodeServerProcesses(any(OpencodeServerProcessFilter.class), any(PageRequest.class)))
                .thenThrow(new IllegalStateException("database unavailable"));
        OpencodeProcessAutoRecoveryService service = new OpencodeProcessAutoRecoveryService(
                repository,
                assignmentService,
                Schedulers.immediate(),
                ignored -> { });

        assertThatCode(() -> {
            service.prepareRecovery(MANAGER_ID, SERVER_ID, CONTAINER_ID, TRACE_ID);
            service.requestPreparedRecovery(MANAGER_ID, TRACE_ID);
        }).doesNotThrowAnyException();

        verify(assignmentService, never()).initialize(any(), any(), any());
    }

    private static void stubProcessPages(
            OpencodeProcessManagementRepository repository,
            List<OpencodeServerProcess> running,
            List<OpencodeServerProcess> starting) {
        when(repository.findOpencodeServerProcesses(any(OpencodeServerProcessFilter.class), any(PageRequest.class)))
                .thenAnswer(invocation -> {
                    OpencodeServerProcessFilter filter = invocation.getArgument(0);
                    List<OpencodeServerProcess> items = filter.status() == OpencodeServerProcessStatus.RUNNING
                            ? running
                            : starting;
                    return new PageResponse<>(items, 1, PageRequest.MAX_SIZE, items.size());
                });
    }

    private static OpencodeServerProcess process(
            String processId,
            String userId,
            int port,
            OpencodeServerProcessStatus status) {
        return new OpencodeServerProcess(
                new OpencodeProcessId(processId),
                new UserId(userId),
                SERVER_ID,
                CONTAINER_ID,
                port,
                12345L,
                "http://127.0.0.1:" + port,
                status,
                "/data/session/" + userId,
                "/data/config/" + userId,
                NOW,
                NOW,
                "healthy",
                NOW,
                NOW,
                TRACE_ID);
    }

    private static UserOpencodeProcessBinding binding(
            OpencodeServerProcess process,
            UserOpencodeProcessBindingStatus status) {
        return new UserOpencodeProcessBinding(
                process.userId(),
                "opencode",
                process.processId(),
                process.linuxServerId(),
                process.port(),
                status,
                NOW,
                NOW,
                TRACE_ID);
    }
}
