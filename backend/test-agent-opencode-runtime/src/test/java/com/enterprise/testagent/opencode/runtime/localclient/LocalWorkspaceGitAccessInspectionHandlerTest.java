package com.enterprise.testagent.opencode.runtime.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceGitAccessCheck;
import com.enterprise.testagent.domain.workspace.WorkspaceGitAccessCheckRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalWorkspaceGitAccessInspectionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");

    @Test
    void shouldPersistClientResultAndKeepUnsupportedClientSelectable() {
        WorkspaceGitAccessCheckRepository checks = mock(WorkspaceGitAccessCheckRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        BackendJavaRouteResolver routes = mock(BackendJavaRouteResolver.class);
        LocalClientWorkspaceFileGateway gateway = mock(LocalClientWorkspaceFileGateway.class);
        UserId userId = new UserId("usr_local");
        LocalClientInstanceId capableClient = new LocalClientInstanceId("lci_capable");
        LocalClientInstanceId oldClient = new LocalClientInstanceId("lci_old");
        var capable = candidate(userId, "wrk_capable", capableClient);
        var unsupported = candidate(userId, "wrk_old", oldClient);
        when(checks.findLocalWorkspaceCandidatesAfter(null, null, 200))
                .thenReturn(List.of(capable, unsupported));
        when(instances.findById(capableClient)).thenReturn(Optional.of(instance(capableClient, userId, true)));
        when(instances.findById(oldClient)).thenReturn(Optional.of(instance(oldClient, userId, false)));
        when(connections.find(capableClient)).thenReturn(Optional.of(route(capableClient, userId, 7)));
        when(connections.find(oldClient)).thenReturn(Optional.of(route(oldClient, userId, 8)));
        when(routes.isCurrent(new BackendProcessId("bjp_current"))).thenReturn(true);
        when(gateway.checkGitAccess(
                "lci_capable", 7, "wrk_capable", "digest-wrk_capable", "trace-local"))
                .thenReturn(new ObjectMapper().createObjectNode()
                        .put("status", "INACCESSIBLE")
                        .put("reason", "REPOSITORY_PERMISSION_REQUIRED")
                        .put("message", "Git 仓库读取权限已失效"));
        LocalWorkspaceGitAccessInspectionHandler handler = new LocalWorkspaceGitAccessInspectionHandler(
                checks,
                instances,
                connections,
                routes,
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC));

        LocalWorkspaceGitAccessInspectionHandler.InspectionResult result =
                handler.inspectConnected("trace-local");

        assertThat(result).isEqualTo(new LocalWorkspaceGitAccessInspectionHandler.InspectionResult(2, 0, 1, 1));
        ArgumentCaptor<WorkspaceGitAccessCheck> saved = ArgumentCaptor.forClass(WorkspaceGitAccessCheck.class);
        verify(checks, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(WorkspaceGitAccessCheck::targetId, WorkspaceGitAccessCheck::status)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("wrk_capable", WorkspaceGitAccessCheck.Status.INACCESSIBLE),
                        org.assertj.core.groups.Tuple.tuple("wrk_old", WorkspaceGitAccessCheck.Status.UNKNOWN));
        verify(gateway, never()).checkGitAccess(
                "lci_old", 8, "wrk_old", "digest-wrk_old", "trace-local");
    }

    private static WorkspaceGitAccessCheckRepository.LocalWorkspaceCandidate candidate(
            UserId userId,
            String workspaceId,
            LocalClientInstanceId clientId) {
        return new WorkspaceGitAccessCheckRepository.LocalWorkspaceCandidate(
                userId, new WorkspaceId(workspaceId), clientId.value(), "digest-" + workspaceId);
    }

    private static LocalClientInstance instance(
            LocalClientInstanceId clientId,
            UserId userId,
            boolean supportsInspection) {
        return new LocalClientInstance(
                clientId,
                userId,
                "本地客户端",
                "macos",
                "aarch64",
                "1.0.0",
                "1.18.4",
                "1",
                supportsInspection ? List.of("WORKSPACE_GIT_ACCESS_V1") : List.of(),
                false,
                null,
                null,
                null,
                NOW,
                NOW,
                NOW,
                null);
    }

    private static LocalClientConnectionRoute route(
            LocalClientInstanceId clientId,
            UserId userId,
            long generation) {
        return new LocalClientConnectionRoute(
                clientId,
                userId,
                new BackendProcessId("bjp_current"),
                generation,
                "127.0.0.1:4096",
                List.of("127.0.0.1"),
                null,
                LocalClientProcessStatus.STOPPED,
                null,
                null,
                false,
                NOW,
                NOW);
    }
}
