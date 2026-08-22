package com.enterprise.testagent.opencode.runtime.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceBinding;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceRepository;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocalWorkspaceApplicationServiceTest {

    @Test
    void repeatedRootRegistrationRestoresAndReturnsExistingWorkspace() {
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        LocalClientWorkspaceRepository bindings = mock(LocalClientWorkspaceRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientWorkspaceFileGateway files = mock(LocalClientWorkspaceFileGateway.class);
        BackendJavaRouteResolver routes = mock(BackendJavaRouteResolver.class);
        ObjectMapper objectMapper = new ObjectMapper();
        LocalWorkspaceApplicationService service = new LocalWorkspaceApplicationService(
                workspaces, bindings, instances, connections, files, routes, objectMapper);

        Instant now = Instant.parse("2026-08-22T07:00:00Z");
        UserId userId = new UserId("usr_test_dev");
        LocalClientInstanceId clientId = new LocalClientInstanceId("lci_1234567890abcdef");
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        BackendProcessId backendProcessId = new BackendProcessId("bjp_1234567890abcdef");
        LocalClientInstance instance = new LocalClientInstance(
                clientId,
                userId,
                "Mac 客户端",
                "macos",
                "aarch64",
                "1.0.0",
                "1.18.4",
                "1.0.0",
                List.of(),
                false,
                null,
                null,
                null,
                now,
                now,
                now,
                null);
        LocalClientConnectionRoute route = new LocalClientConnectionRoute(
                clientId,
                userId,
                backendProcessId,
                7,
                "127.0.0.1:4096",
                List.of("127.0.0.1"),
                null,
                LocalClientProcessStatus.STOPPED,
                null,
                null,
                false,
                now,
                now);
        LocalClientWorkspaceBinding binding = new LocalClientWorkspaceBinding(
                workspaceId,
                userId,
                clientId,
                "/Users/test/project",
                "root-digest",
                "filesystem-identity",
                now,
                now);
        Workspace workspace = new Workspace(
                workspaceId,
                "project",
                "/Users/test/project",
                WorkspaceStatus.ACTIVE,
                now,
                now,
                null,
                "trace_old");
        ObjectNode registration = objectMapper.createObjectNode()
                .put("normalizedRootPath", "/Users/test/project")
                .put("rootDigest", "root-digest")
                .put("fileSystemIdentity", "filesystem-identity");

        when(instances.findById(clientId)).thenReturn(Optional.of(instance));
        when(connections.find(clientId)).thenReturn(Optional.of(route));
        when(routes.isCurrent(backendProcessId)).thenReturn(true);
        when(files.invoke(
                eq(clientId.value()), eq(7L), eq(null), eq(null),
                eq("workspace.validateRoot"), any(), eq("trace_new")))
                .thenReturn(registration);
        when(bindings.findByOwnerClientAndRootDigest(userId, clientId, "root-digest"))
                .thenReturn(Optional.of(binding));
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(files.invoke(
                eq(clientId.value()), eq(7L), eq(workspaceId.value()), eq(null),
                eq("workspace.registerRoot"), any(), eq("trace_new")))
                .thenReturn(registration);

        LocalWorkspaceApplicationService.LocalWorkspaceView result = service.create(
                userId, clientId, "project", "/Users/test/project", "trace_new");

        assertThat(result.workspaceId()).isEqualTo(workspaceId.value());
        assertThat(result.name()).isEqualTo("project");
        verify(bindings).lockRegistration(userId, clientId);
        verify(workspaces, never()).save(any());
        verify(bindings, never()).save(any());
    }
}
