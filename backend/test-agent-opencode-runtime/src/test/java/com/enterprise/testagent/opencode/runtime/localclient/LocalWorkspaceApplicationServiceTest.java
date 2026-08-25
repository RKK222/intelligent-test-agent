package com.enterprise.testagent.opencode.runtime.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.UserWorkspacePreference;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.session.SessionRuntimeTargetRepository;
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
        ManagedWorkspaceRepository recentWorkspaces = mock(ManagedWorkspaceRepository.class);
        LocalClientWorkspaceRepository bindings = mock(LocalClientWorkspaceRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientWorkspaceFileGateway files = mock(LocalClientWorkspaceFileGateway.class);
        SessionRuntimeTargetRepository sessionTargets = mock(SessionRuntimeTargetRepository.class);
        NightExecutionTaskRepository nightTasks = mock(NightExecutionTaskRepository.class);
        BackendJavaRouteResolver routes = mock(BackendJavaRouteResolver.class);
        ObjectMapper objectMapper = new ObjectMapper();
        LocalWorkspaceApplicationService service = new LocalWorkspaceApplicationService(
                workspaces, recentWorkspaces, bindings, instances, connections, files,
                sessionTargets, nightTasks, routes, objectMapper);

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
        when(bindings.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(binding));
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

        LocalWorkspaceApplicationService.LocalWorkspaceView recent = service.markRecent(userId, workspaceId);
        assertThat(recent.online()).isTrue();
        verify(recentWorkspaces).savePreference(argThat((UserWorkspacePreference preference) ->
                preference.userId().equals(userId)
                        && preference.appId() == null
                        && preference.workspaceId().equals(workspaceId)));
    }

    @Test
    void reinstalledClientReclaimsVerifiedOfflineWorkspaceAndHidesFullyReplacedInstance() {
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        ManagedWorkspaceRepository recentWorkspaces = mock(ManagedWorkspaceRepository.class);
        LocalClientWorkspaceRepository bindings = mock(LocalClientWorkspaceRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientWorkspaceFileGateway files = mock(LocalClientWorkspaceFileGateway.class);
        SessionRuntimeTargetRepository sessionTargets = mock(SessionRuntimeTargetRepository.class);
        NightExecutionTaskRepository nightTasks = mock(NightExecutionTaskRepository.class);
        BackendJavaRouteResolver routes = mock(BackendJavaRouteResolver.class);
        ObjectMapper objectMapper = new ObjectMapper();
        LocalWorkspaceApplicationService service = new LocalWorkspaceApplicationService(
                workspaces, recentWorkspaces, bindings, instances, connections, files,
                sessionTargets, nightTasks, routes, objectMapper);

        Instant now = Instant.parse("2026-08-25T01:00:00Z");
        UserId userId = new UserId("usr_reinstalled_client");
        LocalClientInstanceId oldClientId = new LocalClientInstanceId("lci_reinstalled_old");
        LocalClientInstanceId newClientId = new LocalClientInstanceId("lci_reinstalled_new");
        WorkspaceId workspaceId = new WorkspaceId("wrk_reinstalled_client");
        BackendProcessId backendProcessId = new BackendProcessId("bjp_reinstalled_client");
        LocalClientInstance newInstance = new LocalClientInstance(
                newClientId, userId, "麒麟工作站", "linux", "arm64",
                "20260825010000", "1.18.4", "1", List.of("SELF_UPDATE_V1"), true,
                null, null, null, now, now, now, null);
        LocalClientConnectionRoute newRoute = new LocalClientConnectionRoute(
                newClientId, userId, backendProcessId, 11, "127.0.0.1:4096", List.of("127.0.0.1"),
                null, LocalClientProcessStatus.STOPPED, null, null, false, now, now);
        LocalClientWorkspaceBinding oldBinding = new LocalClientWorkspaceBinding(
                workspaceId, userId, oldClientId, "/home/test/project", "same-root-digest",
                "same-file-system", now.minusSeconds(3600), now.minusSeconds(3600));
        Workspace workspace = new Workspace(
                workspaceId, "project", "/home/test/project", WorkspaceStatus.ACTIVE,
                now.minusSeconds(3600), now.minusSeconds(3600), null, "trace_old");
        ObjectNode registration = objectMapper.createObjectNode()
                .put("normalizedRootPath", "/home/test/project")
                .put("rootDigest", "same-root-digest")
                .put("fileSystemIdentity", "same-file-system");

        when(instances.findById(newClientId)).thenReturn(Optional.of(newInstance));
        when(connections.find(newClientId)).thenReturn(Optional.of(newRoute));
        when(routes.isCurrent(backendProcessId)).thenReturn(true);
        when(files.invoke(
                eq(newClientId.value()), eq(11L), eq(null), eq(null),
                eq("workspace.validateRoot"), any(), eq("trace_reclaim")))
                .thenReturn(registration);
        when(bindings.findByOwnerClientAndRootDigest(userId, newClientId, "same-root-digest"))
                .thenReturn(Optional.empty());
        when(bindings.findByOwnerRootIdentity(userId, "same-root-digest", "same-file-system"))
                .thenReturn(List.of(oldBinding));
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(files.invoke(
                eq(newClientId.value()), eq(11L), eq(workspaceId.value()), eq(null),
                eq("workspace.registerRoot"), any(), eq("trace_reclaim")))
                .thenReturn(registration);
        when(bindings.rebind(any(LocalClientWorkspaceBinding.class), eq(oldClientId))).thenReturn(true);
        when(bindings.findByClientInstanceId(oldClientId)).thenReturn(List.of());

        LocalWorkspaceApplicationService.LocalWorkspaceView result = service.create(
                userId, newClientId, "project", "/home/test/project", "trace_reclaim");

        assertThat(result.workspaceId()).isEqualTo(workspaceId.value());
        assertThat(result.localClientInstanceId()).isEqualTo(newClientId.value());
        verify(bindings).rebind(argThat(binding ->
                binding.workspaceId().equals(workspaceId)
                        && binding.clientInstanceId().equals(newClientId)
                        && binding.createdAt().equals(oldBinding.createdAt())), eq(oldClientId));
        verify(sessionTargets).rebindLocalClientTargets(workspaceId, oldClientId, newClientId);
        verify(nightTasks).rebindScheduledLocalClientTargets(
                eq(workspaceId), eq(oldClientId), eq(newClientId), any(Instant.class));
        verify(instances).markReplaced(eq(userId), eq(oldClientId), eq(newClientId), any(Instant.class));
        verify(workspaces, never()).save(any());
    }
}
