package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.routing.RoutingDecision;
import com.icbc.testagent.domain.routing.RoutingDecisionRepository;
import com.icbc.testagent.domain.routing.RoutingReason;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunEventSseRouteServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-09T00:00:00Z");
    private static final RunId RUN_ID = new RunId("run_1234567890abcdef");
    private static final OpencodeProcessId PROCESS_ID = new OpencodeProcessId("ocp_1234567890abcdef");
    private static final LinuxServerId SERVER_B = new LinuxServerId("server-b");

    @Test
    void resolvesRemoteBackendFromRunProductionProcess() {
        RoutingDecisionRepository routingDecisions = mock(RoutingDecisionRepository.class);
        OpencodeProcessManagementRepository processes = mock(OpencodeProcessManagementRepository.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        BackendJavaProcess target = backend("bjp_target_backend", SERVER_B, "http://10.8.0.22:8080");
        when(routingDecisions.findByRunId(RUN_ID))
                .thenReturn(Optional.of(decision("node_" + PROCESS_ID.value())));
        when(processes.findOpencodeServerProcessById(PROCESS_ID))
                .thenReturn(Optional.of(process(PROCESS_ID, SERVER_B)));
        when(routeResolver.remoteTarget(SERVER_B)).thenReturn(Optional.of(SERVER_B));
        when(routeResolver.requireBackend(SERVER_B)).thenReturn(target);

        RunEventSseRouteService service = new RunEventSseRouteService(routingDecisions, processes, routeResolver);

        assertThat(service.forwardTarget(RUN_ID)).contains(target);
    }

    @Test
    void returnsEmptyWhenRunOwnerIsCurrentBackend() {
        RoutingDecisionRepository routingDecisions = mock(RoutingDecisionRepository.class);
        OpencodeProcessManagementRepository processes = mock(OpencodeProcessManagementRepository.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        when(routingDecisions.findByRunId(RUN_ID))
                .thenReturn(Optional.of(decision("node_" + PROCESS_ID.value())));
        when(processes.findOpencodeServerProcessById(PROCESS_ID))
                .thenReturn(Optional.of(process(PROCESS_ID, SERVER_B)));
        when(routeResolver.remoteTarget(SERVER_B)).thenReturn(Optional.empty());

        RunEventSseRouteService service = new RunEventSseRouteService(routingDecisions, processes, routeResolver);

        assertThat(service.forwardTarget(RUN_ID)).isEmpty();
    }

    @Test
    void fallsBackToLocalReplayWhenTargetBackendIsUnavailable() {
        RoutingDecisionRepository routingDecisions = mock(RoutingDecisionRepository.class);
        OpencodeProcessManagementRepository processes = mock(OpencodeProcessManagementRepository.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        when(routingDecisions.findByRunId(RUN_ID))
                .thenReturn(Optional.of(decision("node_" + PROCESS_ID.value())));
        when(processes.findOpencodeServerProcessById(PROCESS_ID))
                .thenReturn(Optional.of(process(PROCESS_ID, SERVER_B)));
        when(routeResolver.remoteTarget(SERVER_B)).thenReturn(Optional.of(SERVER_B));
        when(routeResolver.requireBackend(SERVER_B)).thenThrow(new PlatformException(
                ErrorCode.OPENCODE_UNAVAILABLE,
                "目标服务器后端不可用",
                Map.of("linuxServerId", SERVER_B.value())));

        RunEventSseRouteService service = new RunEventSseRouteService(routingDecisions, processes, routeResolver);

        assertThat(service.forwardTarget(RUN_ID)).isEmpty();
    }

    @Test
    void strictRoutingPropagatesUnavailableTargetForWriteOperations() {
        RoutingDecisionRepository routingDecisions = mock(RoutingDecisionRepository.class);
        OpencodeProcessManagementRepository processes = mock(OpencodeProcessManagementRepository.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        when(routingDecisions.findByRunId(RUN_ID))
                .thenReturn(Optional.of(decision("node_" + PROCESS_ID.value())));
        when(processes.findOpencodeServerProcessById(PROCESS_ID))
                .thenReturn(Optional.of(process(PROCESS_ID, SERVER_B)));
        when(routeResolver.remoteTarget(SERVER_B)).thenReturn(Optional.of(SERVER_B));
        when(routeResolver.requireBackend(SERVER_B)).thenThrow(new PlatformException(
                ErrorCode.OPENCODE_UNAVAILABLE,
                "目标服务器后端不可用",
                Map.of("linuxServerId", SERVER_B.value())));

        RunEventSseRouteService service = new RunEventSseRouteService(routingDecisions, processes, routeResolver);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.forwardTargetStrict(RUN_ID))
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).errorCode())
                .isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
    }

    @Test
    void strictRoutingRejectsMissingRunOwnerInsteadOfExecutingWriteLocally() {
        RoutingDecisionRepository routingDecisions = mock(RoutingDecisionRepository.class);
        OpencodeProcessManagementRepository processes = mock(OpencodeProcessManagementRepository.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        when(routingDecisions.findByRunId(RUN_ID)).thenReturn(Optional.empty());

        RunEventSseRouteService service = new RunEventSseRouteService(routingDecisions, processes, routeResolver);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.forwardTargetStrict(RUN_ID))
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).errorCode())
                .isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
        verifyNoInteractions(processes, routeResolver);
    }

    @Test
    void ignoresLegacyExecutionNodeThatCannotBeMappedToOpencodeProcess() {
        RoutingDecisionRepository routingDecisions = mock(RoutingDecisionRepository.class);
        OpencodeProcessManagementRepository processes = mock(OpencodeProcessManagementRepository.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        when(routingDecisions.findByRunId(RUN_ID)).thenReturn(Optional.of(decision("node_legacy")));

        RunEventSseRouteService service = new RunEventSseRouteService(routingDecisions, processes, routeResolver);

        assertThat(service.forwardTarget(RUN_ID)).isEmpty();
        verifyNoInteractions(processes, routeResolver);
    }

    @Test
    void resolvesRedisManifestProducerWithoutDatabaseRoutingQueries() {
        RoutingDecisionRepository routingDecisions = mock(RoutingDecisionRepository.class);
        OpencodeProcessManagementRepository processes = mock(OpencodeProcessManagementRepository.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        BackendJavaProcess target = backend("bjp_target_backend", SERVER_B, "http://10.8.0.22:8080");
        when(runtimeStore.findManifest(RUN_ID)).thenReturn(Optional.of(runtimeManifest()));
        when(routeResolver.remoteTarget(SERVER_B)).thenReturn(Optional.of(SERVER_B));
        when(routeResolver.requireBackend(SERVER_B)).thenReturn(target);
        RunEventSseRouteService service = new RunEventSseRouteService(
                routingDecisions, processes, routeResolver, runtimeStore);

        assertThat(service.forwardTarget(RUN_ID)).contains(target);

        verifyNoInteractions(routingDecisions, processes);
    }

    private static RoutingDecision decision(String executionNodeId) {
        return new RoutingDecision(
                RUN_ID,
                new ExecutionNodeId(executionNodeId),
                RoutingReason.STICKY_SESSION,
                NOW,
                "trace_route");
    }

    private static OpencodeServerProcess process(OpencodeProcessId processId, LinuxServerId linuxServerId) {
        return new OpencodeServerProcess(
                processId,
                new UserId("usr_1234567890abcdef"),
                linuxServerId,
                new OpencodeContainerId("ctr_1234567890abcdef"),
                4097,
                12345L,
                "http://" + linuxServerId.value() + ":4097",
                OpencodeServerProcessStatus.RUNNING,
                "/data/opencode/session/4097",
                "/data/opencode/.config/opencode/",
                NOW,
                NOW,
                "ok",
                NOW,
                NOW,
                "trace_process");
    }

    private static BackendJavaProcess backend(String backendProcessId, LinuxServerId linuxServerId, String listenUrl) {
        return new BackendJavaProcess(
                new BackendProcessId(backendProcessId),
                linuxServerId,
                listenUrl,
                BackendJavaProcessStatus.READY,
                NOW,
                NOW,
                NOW,
                NOW,
                "trace_backend");
    }

    private static RunRuntimeManifest runtimeManifest() {
        return new RunRuntimeManifest(
                RUN_ID,
                RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                "opencode",
                "req_route",
                "msg_dispatch_route",
                SERVER_B.value(),
                "bjp_target_backend",
                "node_" + PROCESS_ID.value(),
                PROCESS_ID.value(),
                "remote-session-route",
                RunStatus.RUNNING,
                0, 0, 1, 0, false, 0, 0, null, null, null,
                NOW.plusSeconds(10_800),
                NOW,
                NOW);
    }
}
