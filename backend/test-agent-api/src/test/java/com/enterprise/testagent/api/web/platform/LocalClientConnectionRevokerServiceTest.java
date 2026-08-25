package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientModelGrantStore;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientConnectionRegistry;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.xxljob.XxlJobProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocalClientConnectionRevokerServiceTest {

    @Test
    void revokeAllExceptKeepsCurrentGenerationAndClosesOtherInstance() {
        UserId userId = new UserId("usr_single_connection");
        LocalClientInstanceId currentId = new LocalClientInstanceId("lci_current_client");
        LocalClientInstanceId previousId = new LocalClientInstanceId("lci_previous_client");
        BackendProcessId currentBackend = new BackendProcessId("bjp_current_client");
        BackendProcessId previousBackend = new BackendProcessId("bjp_previous_client");
        LocalClientInstance currentInstance = mock(LocalClientInstance.class);
        LocalClientInstance previousInstance = mock(LocalClientInstance.class);
        LocalClientConnectionRoute currentRoute = mock(LocalClientConnectionRoute.class);
        LocalClientConnectionRoute previousRoute = mock(LocalClientConnectionRoute.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientModelGrantStore grants = mock(LocalClientModelGrantStore.class);
        BackendJavaRouteResolver routes = mock(BackendJavaRouteResolver.class);
        LocalClientConnectionRegistry localConnections = mock(LocalClientConnectionRegistry.class);

        when(currentInstance.userId()).thenReturn(userId);
        when(currentInstance.clientInstanceId()).thenReturn(currentId);
        when(previousInstance.userId()).thenReturn(userId);
        when(previousInstance.clientInstanceId()).thenReturn(previousId);
        when(instances.findByUserIdIncludingReplaced(userId))
                .thenReturn(List.of(currentInstance, previousInstance));
        when(connections.find(currentId)).thenReturn(Optional.of(currentRoute));
        when(connections.find(previousId)).thenReturn(Optional.of(previousRoute));
        when(currentRoute.clientInstanceId()).thenReturn(currentId);
        when(currentRoute.connectionGeneration()).thenReturn(20L);
        when(currentRoute.backendProcessId()).thenReturn(currentBackend);
        when(previousRoute.clientInstanceId()).thenReturn(previousId);
        when(previousRoute.connectionGeneration()).thenReturn(19L);
        when(previousRoute.backendProcessId()).thenReturn(previousBackend);
        when(connections.delete(previousId, 19L)).thenReturn(true);
        when(routes.isCurrent(previousBackend)).thenReturn(true);

        LocalClientConnectionRevokerService service = new LocalClientConnectionRevokerService(
                instances,
                connections,
                grants,
                routes,
                mock(BackendHttpForwarder.class),
                localConnections,
                mock(XxlJobProperties.class));
        service.revokeAllExcept(
                userId, currentId, 20L, "USER_CONNECTION_SUPERSEDED", "trace_single_connection");

        verify(connections, never()).delete(currentId, 20L);
        verify(connections).delete(previousId, 19L);
        verify(grants).deleteForConnection(previousId, 19L);
        verify(instances).markDisconnected(org.mockito.ArgumentMatchers.eq(previousId),
                org.mockito.ArgumentMatchers.any());
        verify(localConnections).close(previousId, 19L, "USER_CONNECTION_SUPERSEDED");
    }
}
