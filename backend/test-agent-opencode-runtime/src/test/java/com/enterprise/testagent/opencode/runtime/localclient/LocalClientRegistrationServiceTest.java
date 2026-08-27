package com.enterprise.testagent.opencode.runtime.localclient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.localclient.LocalClientConnectionRevoker;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientModelGrantStore;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocalClientRegistrationServiceTest {

    @Test
    void registrationLocksUserAndRevokesEveryOtherRealtimeConnection() {
        UserId userId = new UserId("001177621");
        LocalClientInstanceId clientInstanceId = new LocalClientInstanceId("lci_be6f_current");
        BackendProcessId backendProcessId = new BackendProcessId("bjp_enterprise_current");
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientModelGrantStore grants = mock(LocalClientModelGrantStore.class);
        LocalClientConnectionRevoker revoker = mock(LocalClientConnectionRevoker.class);
        when(instances.findById(clientInstanceId)).thenReturn(Optional.empty());
        when(connections.find(clientInstanceId)).thenReturn(Optional.empty());
        when(connections.nextGeneration()).thenReturn(42L);
        LocalClientRegistrationService service = new LocalClientRegistrationService(
                instances,
                connections,
                grants,
                Clock.fixed(Instant.parse("2026-08-25T08:40:00Z"), ZoneOffset.UTC));
        service.setConnectionRevoker(revoker);
        LocalClientPayloads.Register payload = new LocalClientPayloads.Register(
                "unused-after-authentication",
                clientInstanceId.value(),
                "Win10 工作站",
                "windows",
                "amd64",
                "20260825135217",
                "1.18.4",
                List.of("127.0.0.1"),
                "001177621",
                "1",
                List.of("SELF_UPDATE_V1"));

        service.register(userId, payload, backendProcessId, "10.0.0.8", "trace_single_connection");

        verify(instances).lockUser(userId);
        verify(connections).save(any(), any());
        verify(revoker).revokeAllExcept(
                eq(userId),
                eq(clientInstanceId),
                eq(42L),
                eq("USER_CONNECTION_SUPERSEDED"),
                eq("trace_single_connection"));
    }
}
