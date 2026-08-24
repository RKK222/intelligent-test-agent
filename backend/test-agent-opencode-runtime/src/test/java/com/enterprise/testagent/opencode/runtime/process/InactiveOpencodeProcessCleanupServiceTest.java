package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.InactiveOpencodeProcessCandidate;
import com.enterprise.testagent.domain.opencodeprocess.InactiveOpencodeProcessRepository;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.session.UserRuntimeDisposeCoordinator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证闲置关闭在用户闸门内二次核对，并始终复用公共 tracked stop。 */
class InactiveOpencodeProcessCleanupServiceTest {

    /** 北京时间 2026-08-04 02:00，对齐生产清理 Cron。 */
    private static final Instant NOW = Instant.parse("2026-08-03T18:00:00Z");
    private static final Instant CUTOFF = NOW.minus(InactiveOpencodeProcessCleanupService.INACTIVITY_THRESHOLD);
    private static final Instant TASK_SLOT_BEFORE = Instant.parse("2026-08-04T16:00:00Z");
    private static final String TRACE_ID = "trace_inactive_cleanup_01";

    private InactiveOpencodeProcessRepository repository;
    private BackendJavaRouteResolver routeResolver;
    private LiveOpencodeContainerCandidateResolver containerResolver;
    private UserRuntimeDisposeCoordinator idleCoordinator;
    private OpencodeProcessStopService stopService;
    private InactiveOpencodeProcessCleanupService service;

    @BeforeEach
    void setUp() {
        repository = mock(InactiveOpencodeProcessRepository.class);
        routeResolver = mock(BackendJavaRouteResolver.class);
        containerResolver = mock(LiveOpencodeContainerCandidateResolver.class);
        idleCoordinator = mock(UserRuntimeDisposeCoordinator.class);
        stopService = mock(OpencodeProcessStopService.class);
        when(routeResolver.currentLinuxServerId()).thenReturn(new LinuxServerId("server-a"));
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get())
                .when(idleCoordinator).withUserIdle(any(), any(), any());
        service = new InactiveOpencodeProcessCleanupService(
                repository,
                routeResolver,
                containerResolver,
                idleCoordinator,
                stopService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void inactivityThresholdIsTenDays() {
        assertThat(InactiveOpencodeProcessCleanupService.INACTIVITY_THRESHOLD)
                .isEqualTo(Duration.ofDays(10));
    }

    @Test
    void revalidatesInsideIdleGateAndUsesTrackedStop() {
        InactiveOpencodeProcessCandidate candidate = candidate("usr_old", "ocp_old_process_123", 4101);
        when(repository.findCandidates(new LinuxServerId("server-a"), CUTOFF, NOW, TASK_SLOT_BEFORE, 500))
                .thenReturn(List.of(candidate));
        when(repository.findCurrentCandidate(candidate.process().processId(), CUTOFF, NOW, TASK_SLOT_BEFORE))
                .thenReturn(Optional.of(candidate));
        when(containerResolver.findExactBoundContainer(
                        candidate.process().linuxServerId(), candidate.process().containerId()))
                .thenReturn(Optional.of(mock(OpencodeContainer.class)));

        var result = service.cleanupCurrentServer(TRACE_ID, () -> false);

        assertThat(result.toMap())
                .containsEntry("scannedCount", 1)
                .containsEntry("stoppedCount", 1)
                .containsEntry("failedCount", 0);
        verify(idleCoordinator).withUserIdle(eq(candidate.process().userId()), eq(TRACE_ID), any());
        ArgumentCaptor<OpencodeProcessStopRequest> request =
                ArgumentCaptor.forClass(OpencodeProcessStopRequest.class);
        verify(stopService).stopAndVerify(request.capture());
        assertThat(request.getValue().tracked()).isTrue();
        assertThat(request.getValue().processSnapshot()).isEqualTo(candidate.process());
        verify(repository).findCandidates(
                new LinuxServerId("server-a"), CUTOFF, NOW, TASK_SLOT_BEFORE, 500);
        verify(repository).findCurrentCandidate(
                candidate.process().processId(), CUTOFF, NOW, TASK_SLOT_BEFORE);
    }

    @Test
    void skipsBusyUserDefensively() {
        InactiveOpencodeProcessCandidate candidate = candidate("usr_busy", "ocp_busy_process_123", 4102);
        when(repository.findCandidates(any(), eq(CUTOFF), eq(NOW), eq(TASK_SLOT_BEFORE), eq(500)))
                .thenReturn(List.of(candidate));
        doThrow(new PlatformException(ErrorCode.CONFLICT, "busy"))
                .when(idleCoordinator).withUserIdle(eq(candidate.process().userId()), eq(TRACE_ID), any());

        var result = service.cleanupCurrentServer(TRACE_ID, () -> false);

        assertThat(result.busySkippedCount()).isEqualTo(1);
        verify(stopService, never()).stopAndVerify(any());
    }

    @Test
    void skipsWhenNewRunOrBindingChangeRemovesCandidate() {
        InactiveOpencodeProcessCandidate candidate = candidate("usr_changed", "ocp_changed_proc_123", 4103);
        when(repository.findCandidates(any(), eq(CUTOFF), eq(NOW), eq(TASK_SLOT_BEFORE), eq(500)))
                .thenReturn(List.of(candidate));
        when(repository.findCurrentCandidate(
                        candidate.process().processId(), CUTOFF, NOW, TASK_SLOT_BEFORE))
                .thenReturn(Optional.empty());

        var result = service.cleanupCurrentServer(TRACE_ID, () -> false);

        assertThat(result.changedSkippedCount()).isEqualTo(1);
        verify(containerResolver, never()).findExactBoundContainer(any(), any());
        verify(stopService, never()).stopAndVerify(any());
    }

    @Test
    void onlyManagerHolderStopsAndCandidateFailureDoesNotAbortFollowingCandidate() {
        InactiveOpencodeProcessCandidate remote = candidate("usr_remote", "ocp_remote_process_123", 4104);
        InactiveOpencodeProcessCandidate failed = candidate("usr_failed", "ocp_failed_process_123", 4105);
        InactiveOpencodeProcessCandidate stopped = candidate("usr_stopped", "ocp_stopped_again_123", 4106);
        when(repository.findCandidates(any(), eq(CUTOFF), eq(NOW), eq(TASK_SLOT_BEFORE), eq(500)))
                .thenReturn(List.of(remote, failed, stopped));
        when(repository.findCurrentCandidate(any(), eq(CUTOFF), eq(NOW), eq(TASK_SLOT_BEFORE)))
                .thenAnswer(invocation -> {
                    OpencodeProcessId id = invocation.getArgument(0);
                    return List.of(remote, failed, stopped).stream()
                            .filter(item -> item.process().processId().equals(id))
                            .findFirst();
                });
        when(containerResolver.findExactBoundContainer(any(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(mock(OpencodeContainer.class)))
                .thenReturn(Optional.of(mock(OpencodeContainer.class)));
        when(stopService.stopAndVerify(any()))
                .thenThrow(new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "offline"))
                .thenReturn(mock(OpencodeProcessControlResult.class));

        var result = service.cleanupCurrentServer(TRACE_ID, () -> false);

        assertThat(result.scannedCount()).isEqualTo(3);
        assertThat(result.notLocalSkippedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.stoppedCount()).isEqualTo(1);
    }

    @Test
    void cooperativeStopEndsBeforeNextCandidate() {
        InactiveOpencodeProcessCandidate candidate = candidate("usr_cancel", "ocp_cancel_process_123", 4107);
        when(repository.findCandidates(any(), eq(CUTOFF), eq(NOW), eq(TASK_SLOT_BEFORE), eq(500)))
                .thenReturn(List.of(candidate));
        AtomicBoolean stop = new AtomicBoolean(true);

        var result = service.cleanupCurrentServer(TRACE_ID, stop::get);

        assertThat(result.stopRequested()).isTrue();
        assertThat(result.scannedCount()).isZero();
        verify(idleCoordinator, never()).withUserIdle(any(), any(), any());
    }

    private InactiveOpencodeProcessCandidate candidate(String userId, String processId, int port) {
        Instant startedAt = CUTOFF.minusSeconds(60);
        return new InactiveOpencodeProcessCandidate(
                new OpencodeServerProcess(
                        new OpencodeProcessId(processId),
                        new UserId(userId),
                        new LinuxServerId("server-a"),
                        new OpencodeContainerId("container-a"),
                        port,
                        20_000L + port,
                        "http://127.0.0.1:" + port,
                        OpencodeServerProcessStatus.RUNNING,
                        "/tmp/session/" + userId,
                        "/tmp/config/" + userId,
                        startedAt,
                        NOW.minusSeconds(10),
                        "healthy",
                        startedAt,
                        NOW.minusSeconds(10),
                        "trace_process_generation"),
                startedAt);
    }
}
