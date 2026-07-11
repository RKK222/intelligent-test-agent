package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.event.RunEventRepository;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.icbc.testagent.domain.routing.RoutingDecisionRepository;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.run.RunSummaryPersistencePort;
import com.icbc.testagent.domain.run.TokenUsage;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessageRepository;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.event.RunEventAppender;
import com.icbc.testagent.event.RunEventLiveBus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunAccessAuthorizationTest {

    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");
    private static final UserId OWNER = new UserId("usr_run_owner");
    private static final UserId OTHER_USER = new UserId("usr_other_user");
    private static final RunId RUN_ID = new RunId("run_access_control");
    private static final SessionId SESSION_ID = new SessionId("ses_access_control");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_access_control");

    @Test
    void redisSummaryAccessUsesManifestOwnerWithoutPostgresql() {
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunRepository runs = mock(RunRepository.class);
        SessionRepository sessions = mock(SessionRepository.class);
        when(runtimeStore.findManifest(RUN_ID)).thenReturn(Optional.of(redisSummaryManifest()));
        RunApplicationService service = service(runtimeStore, runs, sessions);

        assertThatCode(() -> service.requireRunAccess(OWNER, RUN_ID)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireRunAccess(OTHER_USER, RUN_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(runs, sessions);
    }

    @Test
    void legacyAccessRequiresRunAndSessionOwnership() {
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunRepository runs = mock(RunRepository.class);
        SessionRepository sessions = mock(SessionRepository.class);
        when(runtimeStore.findManifest(RUN_ID)).thenReturn(Optional.empty());
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(ownedRun()));
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(ownedSession()));
        RunApplicationService service = service(runtimeStore, runs, sessions);

        assertThatCode(() -> service.requireRunAccess(OWNER, RUN_ID)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireRunAccess(OTHER_USER, RUN_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.FORBIDDEN));
    }

    private static RunApplicationService service(
            RunRuntimeStore runtimeStore,
            RunRepository runs,
            SessionRepository sessions) {
        return new RunApplicationService(
                mock(WorkspaceRepository.class),
                sessions,
                runs,
                mock(SessionMessageRepository.class),
                mock(ExecutionNodeRepository.class),
                mock(RoutingDecisionRepository.class),
                new RunEventAppender(mock(RunEventRepository.class)),
                mock(AgentRuntimeRegistry.class),
                mock(AgentSessionBindingRepository.class),
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                null,
                null,
                ManagedWorkspacePathResolver.legacyOnly(),
                mock(RunSessionMessageSnapshotService.class),
                null,
                null,
                null,
                null,
                runtimeStore,
                mock(RunStorageModeSelector.class),
                mock(RunSummaryPersistencePort.class),
                mock(RunTerminalProjectionService.class),
                mock(BackendInstanceIdentity.class));
    }

    private static RunRuntimeManifest redisSummaryManifest() {
        return new RunRuntimeManifest(
                RUN_ID,
                RunStorageMode.REDIS_SUMMARY,
                OWNER,
                SESSION_ID,
                WORKSPACE_ID,
                "opencode",
                "request-access-control",
                "msg_access_control",
                "server-a",
                "backend-a",
                "node-a",
                "process-a",
                "remote-session-a",
                RunStatus.RUNNING,
                1,
                2,
                1,
                0,
                false,
                2,
                1024,
                null,
                null,
                null,
                NOW.plusSeconds(86_400),
                NOW,
                NOW);
    }

    private static Run ownedRun() {
        return new Run(
                RUN_ID,
                SESSION_ID,
                WORKSPACE_ID,
                RunStatus.RUNNING,
                NOW,
                NOW,
                "trace_access_control",
                TokenUsage.empty(),
                null,
                ConversationSourceType.MANUAL,
                null,
                OWNER,
                "opencode",
                null);
    }

    private static Session ownedSession() {
        return new Session(
                SESSION_ID,
                WORKSPACE_ID,
                "Access control",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                "trace_access_control",
                null,
                null,
                false,
                ConversationSourceType.MANUAL,
                null,
                OWNER);
    }
}
