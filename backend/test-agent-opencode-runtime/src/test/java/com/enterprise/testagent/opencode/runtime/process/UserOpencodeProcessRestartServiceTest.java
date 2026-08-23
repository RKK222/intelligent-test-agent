package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRuntimeState;
import com.enterprise.testagent.domain.session.SessionRuntimeStateSummary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.opencode.runtime.session.SessionRuntimeStateApplicationService;
import com.enterprise.testagent.opencode.runtime.session.UserRuntimeDisposeCoordinator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UserOpencodeProcessRestartServiceTest {

    private static final UserId USER_ID = new UserId("usr_restart_12345678");
    private static final RunId RUN_ONE = new RunId("run_restart_12345678");
    private static final RunId RUN_TWO = new RunId("run_restart_87654321");
    private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");
    private static final String TRACE_ID = "trace_restart_12345678";

    private final RunRuntimeStore runtimeStore = Mockito.mock(RunRuntimeStore.class);
    private final SessionRuntimeStateApplicationService runtimeState = Mockito.mock(SessionRuntimeStateApplicationService.class);
    private final OpencodeProcessManagementRepository repository = Mockito.mock(OpencodeProcessManagementRepository.class);
    private final RunApplicationService runService = Mockito.mock(RunApplicationService.class);
    private final OpencodeProcessStopService stopService = Mockito.mock(OpencodeProcessStopService.class);
    private final UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
    private final UserOpencodeProcessBindingActivationService bindingActivationService =
            Mockito.mock(UserOpencodeProcessBindingActivationService.class);
    private final UserRuntimeDisposeCoordinator coordinator = new UserRuntimeDisposeCoordinator(runtimeStore, runtimeState);
    private final UserOpencodeProcessRestartService service = new UserOpencodeProcessRestartService(
            repository,
            runtimeState,
            runService,
            stopService,
            assignmentService,
            coordinator,
            bindingActivationService);

    @BeforeEach
    void setUp() {
        when(runtimeStore.tryAcquireUserRuntimeMaintenance(eq(USER_ID), anyString(), any(Duration.class))).thenReturn(true);
        when(repository.findUserBinding(USER_ID, "opencode")).thenReturn(Optional.of(binding()));
        when(repository.findOpencodeServerProcessById(binding().processId())).thenReturn(Optional.of(process()));
        when(assignmentService.initialize(USER_ID, "opencode", TRACE_ID)).thenReturn(ready());
    }

    @Test
    void restartsImmediatelyWhenTheUserHasNoActiveRuns() {
        when(runtimeState.snapshot(USER_ID)).thenReturn(summary());

        UserOpencodeProcessStatusResponse response = service.restart(USER_ID, "opencode", false, TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.READY);
        verify(stopService).stopAndVerify(OpencodeProcessStopRequest.tracked(process(), TRACE_ID));
        verify(assignmentService).initialize(USER_ID, "opencode", TRACE_ID);
    }

    @Test
    void avatarRestartReactivatesAnAdministrativelyClosedServerProcessWithoutStoppingItAgain() {
        UserOpencodeProcessBinding inactiveBinding = binding(UserOpencodeProcessBindingStatus.INACTIVE);
        OpencodeServerProcess stoppedProcess = process(OpencodeServerProcessStatus.STOPPED);
        when(repository.findUserBinding(USER_ID, "opencode")).thenReturn(Optional.of(inactiveBinding));
        when(repository.findOpencodeServerProcessById(inactiveBinding.processId())).thenReturn(Optional.of(stoppedProcess));
        when(runtimeState.snapshot(USER_ID)).thenReturn(summary());
        when(bindingActivationService.activateForExplicitRestart(stoppedProcess, TRACE_ID)).thenReturn(true);

        UserOpencodeProcessStatusResponse response = service.restart(USER_ID, "opencode", false, TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.READY);
        verify(bindingActivationService).activateForExplicitRestart(stoppedProcess, TRACE_ID);
        verify(bindingActivationService).requireActiveBinding(stoppedProcess);
        verify(stopService, never()).stopAndVerify(any());
        verify(assignmentService).initialize(USER_ID, "opencode", TRACE_ID);
    }

    @Test
    void failedAvatarRestartRestoresTheAdministrativelyClosedProjection() {
        UserOpencodeProcessBinding inactiveBinding = binding(UserOpencodeProcessBindingStatus.INACTIVE);
        OpencodeServerProcess stoppedProcess = process(OpencodeServerProcessStatus.STOPPED);
        when(repository.findUserBinding(USER_ID, "opencode")).thenReturn(Optional.of(inactiveBinding));
        when(repository.findOpencodeServerProcessById(inactiveBinding.processId())).thenReturn(Optional.of(stoppedProcess));
        when(runtimeState.snapshot(USER_ID)).thenReturn(summary());
        when(bindingActivationService.activateForExplicitRestart(stoppedProcess, TRACE_ID)).thenReturn(true);
        when(assignmentService.initialize(USER_ID, "opencode", TRACE_ID))
                .thenThrow(new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE));

        assertThatThrownBy(() -> service.restart(USER_ID, "opencode", false, TRACE_ID))
                .isInstanceOf(PlatformException.class);

        verify(bindingActivationService).restoreInactiveAfterFailedRestart(stoppedProcess, TRACE_ID);
    }

    @Test
    void asksForConfirmationBeforeCancellingActiveRuns() {
        when(runtimeState.snapshot(USER_ID)).thenReturn(summary(state(RUN_ONE, "ses_restart_12345678")));

        assertThatThrownBy(() -> service.restart(USER_ID, "opencode", false, TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.details()).containsEntry("confirmationRequired", true)
                            .containsEntry("runningCount", 1);
                });

        verify(runService, never()).cancelRun(any(RunId.class), anyString());
        verify(stopService, never()).stopAndVerify(any());
    }

    @Test
    void confirmationCancelsEachUniqueRunBeforeStoppingAndStarting() {
        when(runtimeState.snapshot(USER_ID)).thenReturn(
                summary(
                        state(RUN_ONE, "ses_restart_12345678"),
                        state(RUN_ONE, "ses_restart_22222222"),
                        state(RUN_TWO, "ses_restart_87654321")),
                summary());

        service.restart(USER_ID, "opencode", true, TRACE_ID);

        verify(runService).cancelRun(RUN_ONE, TRACE_ID);
        verify(runService).cancelRun(RUN_TWO, TRACE_ID);
        verify(stopService).stopAndVerify(OpencodeProcessStopRequest.tracked(process(), TRACE_ID));
        verify(assignmentService).initialize(USER_ID, "opencode", TRACE_ID);
    }

    @Test
    void cancellationFailureDoesNotStopTheProcess() {
        when(runtimeState.snapshot(USER_ID)).thenReturn(summary(state(RUN_ONE, "ses_restart_12345678")));
        when(runService.cancelRun(RUN_ONE, TRACE_ID)).thenThrow(new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE));

        assertThatThrownBy(() -> service.restart(USER_ID, "opencode", true, TRACE_ID))
                .isInstanceOf(PlatformException.class);

        verify(stopService, never()).stopAndVerify(any());
        verify(assignmentService, never()).initialize(any(), anyString(), anyString());
    }

    @Test
    void remainingActiveRunAfterCancellationDoesNotStopTheProcess() {
        SessionRuntimeState active = state(RUN_ONE, "ses_restart_12345678");
        when(runtimeState.snapshot(USER_ID)).thenReturn(summary(active), summary(active));

        assertThatThrownBy(() -> service.restart(USER_ID, "opencode", true, TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.details()).containsEntry("runningCount", 1);
                });

        verify(stopService, never()).stopAndVerify(any());
    }

    @Test
    void startFailureLeavesTheVerifiedStoppedProcessForRetry() {
        when(runtimeState.snapshot(USER_ID)).thenReturn(summary());
        when(assignmentService.initialize(USER_ID, "opencode", TRACE_ID))
                .thenThrow(new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE));

        assertThatThrownBy(() -> service.restart(USER_ID, "opencode", false, TRACE_ID))
                .isInstanceOf(PlatformException.class);

        verify(stopService).stopAndVerify(OpencodeProcessStopRequest.tracked(process(), TRACE_ID));
    }

    @Test
    void stopFailureDoesNotAttemptToStartAReplacementProcess() {
        when(runtimeState.snapshot(USER_ID)).thenReturn(summary());
        when(stopService.stopAndVerify(OpencodeProcessStopRequest.tracked(process(), TRACE_ID)))
                .thenThrow(new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE));

        assertThatThrownBy(() -> service.restart(USER_ID, "opencode", false, TRACE_ID))
                .isInstanceOf(PlatformException.class);

        verify(assignmentService, never()).initialize(any(), anyString(), anyString());
    }

    private SessionRuntimeStateSummary summary(SessionRuntimeState... states) {
        return new SessionRuntimeStateSummary(states.length, 0, 0, List.of(states), NOW);
    }

    private SessionRuntimeState state(RunId runId, String sessionId) {
        return new SessionRuntimeState(new SessionId(sessionId), runId, RunStatus.RUNNING, null, null, null, NOW);
    }

    private UserOpencodeProcessBinding binding() {
        return binding(UserOpencodeProcessBindingStatus.ACTIVE);
    }

    private UserOpencodeProcessBinding binding(UserOpencodeProcessBindingStatus status) {
        return new UserOpencodeProcessBinding(
                USER_ID,
                "opencode",
                new OpencodeProcessId("ocp_restart_12345678"),
                new LinuxServerId("server-a"),
                4096,
                status,
                NOW.minusSeconds(60),
                NOW,
                TRACE_ID);
    }

    private OpencodeServerProcess process() {
        return process(OpencodeServerProcessStatus.RUNNING);
    }

    private OpencodeServerProcess process(OpencodeServerProcessStatus status) {
        return new OpencodeServerProcess(
                binding().processId(),
                USER_ID,
                binding().linuxServerId(),
                new OpencodeContainerId("ctr_restart_12345678"),
                4096,
                status == OpencodeServerProcessStatus.STOPPED ? null : 9123L,
                "http://10.8.0.12:4096",
                status,
                "/data/sessions/usr_restart_12345678",
                "/data/config/usr_restart_12345678",
                NOW.minusSeconds(30),
                NOW,
                "healthy",
                NOW.minusSeconds(60),
                NOW,
                TRACE_ID);
    }

    private UserOpencodeProcessStatusResponse ready() {
        return new UserOpencodeProcessStatusResponse(
                UserOpencodeProcessAvailability.READY,
                false,
                "TestAgent 进程已重启",
                binding().processId().value(),
                binding().linuxServerId().value(),
                process().containerId().value(),
                process().port(),
                process().baseUrl(),
                NOW);
    }
}
