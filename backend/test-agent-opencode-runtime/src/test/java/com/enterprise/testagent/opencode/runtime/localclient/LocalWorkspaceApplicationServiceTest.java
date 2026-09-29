package com.enterprise.testagent.opencode.runtime.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

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
                sessionTargets, nightTasks, routes, objectMapper, mock(PlatformTransactionManager.class));

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
                "2.0.18",
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
        when(files.invokeRootRegistration(
                eq(clientId.value()), eq(7L), eq(null),
                eq("workspace.validateRoot"), any(), eq("trace_new")))
                .thenReturn(registration);
        when(bindings.findByOwnerClientAndRootDigest(userId, clientId, "root-digest"))
                .thenReturn(Optional.of(binding));
        when(bindings.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(binding));
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(files.invokeRootRegistration(
                eq(clientId.value()), eq(7L), eq(workspaceId.value()),
                eq("workspace.registerRoot"), any(), eq("trace_new")))
                .thenReturn(registration);

        LocalWorkspaceApplicationService.LocalWorkspaceView result = service.create(
                userId, clientId, "project", "/Users/test/project", "trace_new");

        assertThat(result.workspaceId()).isEqualTo(workspaceId.value());
        assertThat(result.name()).isEqualTo("project");
        verify(bindings).lockRegistration(userId, clientId);
        verify(workspaces, never()).save(any());
        verify(bindings, never()).save(any());

        LocalWorkspaceApplicationService.LocalWorkspaceView recent = service.markRecent(
                userId, workspaceId, "trace_new");
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
                sessionTargets, nightTasks, routes, objectMapper, mock(PlatformTransactionManager.class));

        Instant now = Instant.parse("2026-08-25T01:00:00Z");
        UserId userId = new UserId("usr_reinstalled_client");
        LocalClientInstanceId oldClientId = new LocalClientInstanceId("lci_reinstalled_old");
        LocalClientInstanceId newClientId = new LocalClientInstanceId("lci_reinstalled_new");
        WorkspaceId workspaceId = new WorkspaceId("wrk_reinstalled_client");
        BackendProcessId backendProcessId = new BackendProcessId("bjp_reinstalled_client");
        LocalClientInstance newInstance = new LocalClientInstance(
                newClientId, userId, "麒麟工作站", "linux", "arm64",
                "20260825010000", "2.0.18", "1", List.of("SELF_UPDATE_V1"), true,
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
        when(files.invokeRootRegistration(
                eq(newClientId.value()), eq(11L), eq(null),
                eq("workspace.validateRoot"), any(), eq("trace_reclaim")))
                .thenReturn(registration);
        when(bindings.findByOwnerClientAndRootDigest(userId, newClientId, "same-root-digest"))
                .thenReturn(Optional.empty());
        when(bindings.findByOwnerRootIdentity(userId, "same-root-digest", "same-file-system"))
                .thenReturn(List.of(oldBinding));
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(files.invokeRootRegistration(
                eq(newClientId.value()), eq(11L), eq(workspaceId.value()),
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

    @Test
    void selectingHistoricalWorkspaceReclaimsItThroughSoleOnlineClient() {
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
                sessionTargets, nightTasks, routes, objectMapper, mock(PlatformTransactionManager.class));

        Instant now = Instant.parse("2026-08-25T08:30:00Z");
        UserId userId = new UserId("001177621");
        LocalClientInstanceId oldClientId = new LocalClientInstanceId("lci_ca417_history");
        LocalClientInstanceId newClientId = new LocalClientInstanceId("lci_be6f_current");
        WorkspaceId workspaceId = new WorkspaceId("wrk_f557_history");
        BackendProcessId backendProcessId = new BackendProcessId("bjp_enterprise_current");
        LocalClientInstance newInstance = mock(LocalClientInstance.class);
        LocalClientConnectionRoute newRoute = mock(LocalClientConnectionRoute.class);
        LocalClientWorkspaceBinding oldBinding = new LocalClientWorkspaceBinding(
                workspaceId,
                userId,
                oldClientId,
                "/home/001177621/Desktop/mimoagent",
                "history-root-digest",
                "history-file-system",
                now.minusSeconds(86400),
                now.minusSeconds(86400));
        Workspace workspace = new Workspace(
                workspaceId,
                "mimoagent",
                "/home/001177621/Desktop/mimoagent",
                WorkspaceStatus.ACTIVE,
                now.minusSeconds(86400),
                now.minusSeconds(86400),
                null,
                "trace_history");
        ObjectNode registration = objectMapper.createObjectNode()
                .put("normalizedRootPath", "/home/001177621/Desktop/mimoagent")
                .put("rootDigest", "history-root-digest")
                .put("fileSystemIdentity", "history-file-system");

        when(bindings.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(oldBinding));
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(newInstance.clientInstanceId()).thenReturn(newClientId);
        when(newInstance.userId()).thenReturn(userId);
        when(instances.findById(newClientId)).thenReturn(Optional.of(newInstance));
        when(instances.findByUserId(userId)).thenReturn(List.of(newInstance));
        when(connections.find(oldClientId)).thenReturn(Optional.empty());
        when(connections.find(newClientId)).thenReturn(Optional.of(newRoute));
        when(newRoute.userId()).thenReturn(userId);
        when(newRoute.clientInstanceId()).thenReturn(newClientId);
        when(newRoute.connectionGeneration()).thenReturn(21L);
        when(newRoute.backendProcessId()).thenReturn(backendProcessId);
        when(routes.isCurrent(backendProcessId)).thenReturn(true);
        when(files.invokeRootRegistration(
                eq(newClientId.value()), eq(21L), eq(null),
                eq("workspace.validateRoot"), any(), eq("trace_activate_history")))
                .thenReturn(registration);
        when(files.invokeRootRegistration(
                eq(newClientId.value()), eq(21L), eq(workspaceId.value()),
                eq("workspace.registerRoot"), any(), eq("trace_activate_history")))
                .thenReturn(registration);
        when(bindings.rebind(any(LocalClientWorkspaceBinding.class), eq(oldClientId))).thenReturn(true);
        when(bindings.findByClientInstanceId(oldClientId)).thenReturn(List.of());

        LocalWorkspaceApplicationService.LocalWorkspaceView result = service.markRecent(
                userId, workspaceId, "trace_activate_history");

        assertThat(result.workspaceId()).isEqualTo(workspaceId.value());
        assertThat(result.localClientInstanceId()).isEqualTo(newClientId.value());
        assertThat(result.online()).isTrue();
        verify(bindings).lockRegistration(userId, newClientId);
        verify(bindings).rebind(any(LocalClientWorkspaceBinding.class), eq(oldClientId));
        verify(recentWorkspaces).savePreference(any(UserWorkspacePreference.class));
    }

    @Test
    void reconnectRestoresRecentLocalWorkspaceWithoutSelectingItAgain() {
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
                sessionTargets, nightTasks, routes, objectMapper, mock(PlatformTransactionManager.class));

        Instant now = Instant.parse("2026-08-25T12:00:00Z");
        UserId userId = new UserId("usr_reconnect_recent");
        LocalClientInstanceId clientId = new LocalClientInstanceId("lci_reconnect_recent");
        WorkspaceId workspaceId = new WorkspaceId("wrk_reconnect_recent");
        BackendProcessId backendProcessId = new BackendProcessId("bjp_reconnect_recent");
        LocalClientInstance instance = mock(LocalClientInstance.class);
        LocalClientConnectionRoute route = mock(LocalClientConnectionRoute.class);
        LocalClientWorkspaceBinding binding = new LocalClientWorkspaceBinding(
                workspaceId, userId, clientId, "/home/test/recent", "recent-root-digest",
                "recent-file-system", now.minusSeconds(60), now.minusSeconds(60));
        Workspace workspace = new Workspace(
                workspaceId, "recent", "/home/test/recent", WorkspaceStatus.ACTIVE,
                now.minusSeconds(60), now.minusSeconds(60), null, "trace_old");
        ObjectNode registration = objectMapper.createObjectNode()
                .put("normalizedRootPath", "/home/test/recent")
                .put("rootDigest", "recent-root-digest")
                .put("fileSystemIdentity", "recent-file-system");

        when(recentWorkspaces.findGlobalPreference(userId)).thenReturn(Optional.of(
                new UserWorkspacePreference(userId, null, workspaceId, now.minusSeconds(30))));
        when(bindings.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(binding));
        when(instances.findById(clientId)).thenReturn(Optional.of(instance));
        when(instance.userId()).thenReturn(userId);
        when(connections.find(clientId)).thenReturn(Optional.of(route));
        when(route.userId()).thenReturn(userId);
        when(route.clientInstanceId()).thenReturn(clientId);
        when(route.connectionGeneration()).thenReturn(31L);
        when(route.backendProcessId()).thenReturn(backendProcessId);
        when(routes.isCurrent(backendProcessId)).thenReturn(true);
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(files.invokeRootRegistration(
                eq(clientId.value()), eq(31L), eq(null),
                eq("workspace.validateRoot"), any(), eq("trace_reconnect")))
                .thenReturn(registration);
        when(files.invokeRootRegistration(
                eq(clientId.value()), eq(31L), eq(workspaceId.value()),
                eq("workspace.registerRoot"), any(), eq("trace_reconnect")))
                .thenReturn(registration);

        Optional<LocalWorkspaceApplicationService.LocalWorkspaceView> restored =
                service.restoreRecentOnReconnect(userId, clientId, 31L, "trace_reconnect");

        assertThat(restored).isPresent();
        assertThat(restored.orElseThrow().workspaceId()).isEqualTo(workspaceId.value());
        assertThat(restored.orElseThrow().online()).isTrue();
        verify(bindings).lockRegistration(userId, clientId);
        verify(files).invokeRootRegistration(
                eq(clientId.value()), eq(31L), eq(workspaceId.value()),
                eq("workspace.registerRoot"), any(), eq("trace_reconnect"));
        verify(recentWorkspaces, never()).savePreference(any(UserWorkspacePreference.class));
    }

    @Test
    void reconnectRestoresEveryAvailableHistoricalWorkspaceAndSkipsMissingDirectory() {
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
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        AtomicBoolean transactionActive = new AtomicBoolean();
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> {
            assertThat(transactionActive.compareAndSet(false, true)).isTrue();
            return transactionStatus;
        });
        doAnswer(invocation -> {
                    assertThat(transactionActive.compareAndSet(true, false)).isTrue();
                    return null;
                })
                .when(transactionManager).commit(transactionStatus);
        doAnswer(invocation -> {
                    transactionActive.set(false);
                    return null;
                })
                .when(transactionManager).rollback(transactionStatus);
        LocalWorkspaceApplicationService service = new LocalWorkspaceApplicationService(
                workspaces, recentWorkspaces, bindings, instances, connections, files,
                sessionTargets, nightTasks, routes, objectMapper, transactionManager);

        Instant now = Instant.parse("2026-08-26T02:00:00Z");
        UserId userId = new UserId("001177621");
        LocalClientInstanceId currentClientId = new LocalClientInstanceId("lci_current_client");
        LocalClientInstanceId historicalClientId = new LocalClientInstanceId("lci_previous_version");
        WorkspaceId availableWorkspaceId = new WorkspaceId("wrk_available_history");
        WorkspaceId missingWorkspaceId = new WorkspaceId("wrk_missing_history");
        BackendProcessId backendProcessId = new BackendProcessId("bjp_current_client");
        LocalClientInstance currentInstance = mock(LocalClientInstance.class);
        LocalClientInstance historicalInstance = mock(LocalClientInstance.class);
        LocalClientConnectionRoute currentRoute = mock(LocalClientConnectionRoute.class);
        LocalClientWorkspaceBinding availableBinding = new LocalClientWorkspaceBinding(
                availableWorkspaceId, userId, historicalClientId, "/home/user/available",
                "available-digest", "available-file-system", now.minusSeconds(3600), now.minusSeconds(3600));
        LocalClientWorkspaceBinding missingBinding = new LocalClientWorkspaceBinding(
                missingWorkspaceId, userId, historicalClientId, "/home/user/missing",
                "missing-digest", "missing-file-system", now.minusSeconds(3500), now.minusSeconds(3500));
        Workspace availableWorkspace = new Workspace(
                availableWorkspaceId, "available", "/home/user/available", WorkspaceStatus.ACTIVE,
                now.minusSeconds(3600), now.minusSeconds(3600), null, "trace_old");
        ObjectNode registration = objectMapper.createObjectNode()
                .put("normalizedRootPath", "/home/user/available")
                .put("rootDigest", "available-digest")
                .put("fileSystemIdentity", "available-file-system");

        when(instances.findById(currentClientId)).thenReturn(Optional.of(currentInstance));
        when(currentInstance.userId()).thenReturn(userId);
        when(currentInstance.clientInstanceId()).thenReturn(currentClientId);
        when(historicalInstance.clientInstanceId()).thenReturn(historicalClientId);
        when(instances.findByUserIdIncludingReplaced(userId))
                .thenReturn(List.of(currentInstance, historicalInstance));
        when(bindings.findByClientInstanceId(currentClientId)).thenReturn(List.of());
        when(bindings.findByClientInstanceId(historicalClientId))
                .thenReturn(List.of(availableBinding, missingBinding), List.of(missingBinding));
        when(bindings.findByWorkspaceId(availableWorkspaceId)).thenReturn(Optional.of(availableBinding));
        when(bindings.findByWorkspaceId(missingWorkspaceId)).thenReturn(Optional.of(missingBinding));
        when(connections.find(currentClientId)).thenReturn(Optional.of(currentRoute));
        when(connections.find(historicalClientId)).thenReturn(Optional.empty());
        when(currentRoute.userId()).thenReturn(userId);
        when(currentRoute.clientInstanceId()).thenReturn(currentClientId);
        when(currentRoute.connectionGeneration()).thenReturn(41L);
        when(currentRoute.backendProcessId()).thenReturn(backendProcessId);
        when(routes.isCurrent(backendProcessId)).thenReturn(true);
        when(workspaces.findById(availableWorkspaceId)).thenReturn(Optional.of(availableWorkspace));
        when(files.invokeRootRegistration(
                eq(currentClientId.value()), eq(41L), eq(null),
                eq("workspace.validateRoot"), argThat(node ->
                        "/home/user/available".equals(node.path("absolutePath").asText())), eq("trace_restore_all")))
                .thenAnswer(invocation -> {
                    assertThat(transactionActive).isFalse();
                    return registration;
                });
        when(files.invokeRootRegistration(
                eq(currentClientId.value()), eq(41L), eq(availableWorkspaceId.value()),
                eq("workspace.registerRoot"), any(), eq("trace_restore_all")))
                .thenAnswer(invocation -> {
                    assertThat(transactionActive).isFalse();
                    return registration;
                });
        when(files.invokeRootRegistration(
                eq(currentClientId.value()), eq(41L), eq(null),
                eq("workspace.validateRoot"), argThat(node ->
                        "/home/user/missing".equals(node.path("absolutePath").asText())), eq("trace_restore_all")))
                .thenAnswer(invocation -> {
                    assertThat(transactionActive).isFalse();
                    throw new IllegalStateException("directory missing");
                });
        doAnswer(invocation -> {
                    assertThat(transactionActive).isTrue();
                    return null;
                })
                .when(bindings).lockRegistration(userId, currentClientId);
        when(bindings.rebind(any(LocalClientWorkspaceBinding.class), eq(historicalClientId))).thenReturn(true);
        LocalWorkspaceApplicationService.ReconnectRestoreResult result = service.restoreAvailableOnReconnect(
                userId, currentClientId, 41L, "trace_restore_all");

        assertThat(result).isEqualTo(new LocalWorkspaceApplicationService.ReconnectRestoreResult(2, 1, 1));
        verify(bindings).rebind(argThat(binding ->
                binding.workspaceId().equals(availableWorkspaceId)
                        && binding.clientInstanceId().equals(currentClientId)), eq(historicalClientId));
        verify(bindings, never()).rebind(argThat(binding ->
                binding.workspaceId().equals(missingWorkspaceId)), eq(historicalClientId));
        verify(transactionManager).commit(transactionStatus);
        verify(transactionManager, never()).rollback(any());
        assertThat(transactionActive).isFalse();
    }
}
