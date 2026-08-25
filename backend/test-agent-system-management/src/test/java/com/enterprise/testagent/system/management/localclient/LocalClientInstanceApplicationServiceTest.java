package com.enterprise.testagent.system.management.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientCredential;
import com.enterprise.testagent.domain.localclient.LocalClientCredentialRepository;
import com.enterprise.testagent.domain.localclient.LocalClientCredentialStatus;
import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientProcessStatus;
import com.enterprise.testagent.domain.localclient.LocalClientRolloutRepository;
import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import com.enterprise.testagent.domain.localclient.LocalClientVersionRepository;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证设置页实例投影包含兼容的自更新策略状态。 */
class LocalClientInstanceApplicationServiceTest {

    @Test
    void listUsesUserPolicyBeforeGlobalPolicyAndReportsRollbackDirection() {
        UserId userId = new UserId("usr_local_instance_view");
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientCredentialRepository credentials = mock(LocalClientCredentialRepository.class);
        LocalClientRolloutRepository rollout = mock(LocalClientRolloutRepository.class);
        Instant now = Instant.parse("2026-08-20T10:00:00Z");
        when(credentials.findByUserId(userId)).thenReturn(Optional.of(credential(
                userId, LocalClientCredentialStatus.ACTIVE, now, null)));
        LocalClientInstance instance = instance("lci_local_instance_view", userId, now);
        when(instances.findByUserId(userId)).thenReturn(List.of(instance));
        when(connections.find(instance.clientInstanceId())).thenReturn(Optional.of(route(
                instance, LocalClientProcessStatus.RUNNING, true, now)));
        when(versions.findGlobalPolicy()).thenReturn(Optional.of(new LocalClientVersionModels.GlobalPolicy(
                "20260820200000", 6, userId, now)));
        when(versions.findUserPolicy(userId)).thenReturn(Optional.of(new LocalClientVersionModels.UserPolicy(
                userId, "20260820180000", 7, userId, now)));
        when(rollout.isEnabled(userId)).thenReturn(true);

        LocalClientInstanceApplicationService service =
                new LocalClientInstanceApplicationService(instances, connections, versions, credentials);
        service.setRolloutRepository(rollout);

        LocalClientInstanceResponses.InstanceView view = service.list(userId).getFirst();

        assertThat(view.selfUpdateSupported()).isTrue();
        assertThat(view.targetClientVersion()).isEqualTo("20260820180000");
        assertThat(view.updateDirection()).isEqualTo("ROLLBACK");
        assertThat(view.lastUpdateStatus()).isEqualTo("SUCCEEDED");
        assertThat(view.lastUpdateAt()).isEqualTo(now);
    }

    @Test
    void hidesOfflineHistoryButKeepsConnectedUnhealthyInstanceForRecovery() {
        UserId userId = new UserId("usr_local_instance_online_only");
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientCredentialRepository credentials = mock(LocalClientCredentialRepository.class);
        LocalClientRolloutRepository rollout = mock(LocalClientRolloutRepository.class);
        Instant now = Instant.parse("2026-08-20T10:00:00Z");
        LocalClientInstance offline = instance("lci_local_instance_history", userId, now.minusSeconds(60));
        LocalClientInstance connected = instance("lci_local_instance_current", userId, now);
        when(credentials.findByUserId(userId)).thenReturn(Optional.of(credential(
                userId, LocalClientCredentialStatus.ACTIVE, now, null)));
        when(instances.findByUserId(userId)).thenReturn(List.of(offline, connected));
        when(connections.find(offline.clientInstanceId())).thenReturn(Optional.empty());
        when(connections.find(connected.clientInstanceId())).thenReturn(Optional.of(route(
                connected, LocalClientProcessStatus.UNHEALTHY, false, now)));
        when(rollout.isEnabled(userId)).thenReturn(true);

        LocalClientInstanceApplicationService service =
                new LocalClientInstanceApplicationService(instances, connections, versions, credentials);
        service.setRolloutRepository(rollout);

        assertThat(service.list(userId))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.clientInstanceId()).isEqualTo(connected.clientInstanceId().value());
                    assertThat(view.online()).isTrue();
                    assertThat(view.opencodeHealthy()).isFalse();
                    assertThat(view.processStatus()).isEqualTo("UNHEALTHY");
                });
    }

    @Test
    void revokedCredentialHidesPersistedClientInstances() {
        UserId userId = new UserId("usr_local_instance_revoked");
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientCredentialRepository credentials = mock(LocalClientCredentialRepository.class);
        LocalClientRolloutRepository rollout = mock(LocalClientRolloutRepository.class);
        Instant now = Instant.parse("2026-08-20T10:00:00Z");
        when(credentials.findByUserId(userId)).thenReturn(Optional.of(credential(
                userId, LocalClientCredentialStatus.REVOKED, now, now)));
        when(instances.findByUserId(userId)).thenReturn(List.of(instance(
                "lci_local_instance_revoked", userId, now)));

        LocalClientInstanceApplicationService service =
                new LocalClientInstanceApplicationService(instances, connections, versions, credentials);
        service.setRolloutRepository(rollout);

        assertThat(service.list(userId)).isEmpty();
    }

    @Test
    void administrativelyDisabledRolloutHidesInstancesWithoutRevokingTheCredential() {
        UserId userId = new UserId("usr_local_instance_rollout_disabled");
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientCredentialRepository credentials = mock(LocalClientCredentialRepository.class);
        LocalClientRolloutRepository rollout = mock(LocalClientRolloutRepository.class);
        Instant now = Instant.parse("2026-08-20T10:00:00Z");
        when(credentials.findByUserId(userId)).thenReturn(Optional.of(credential(
                userId, LocalClientCredentialStatus.ACTIVE, now, null)));
        when(rollout.isEnabled(userId)).thenReturn(false);
        when(instances.findByUserId(userId)).thenReturn(List.of(instance(
                "lci_local_instance_rollout_disabled", userId, now)));

        LocalClientInstanceApplicationService service =
                new LocalClientInstanceApplicationService(instances, connections, versions, credentials);
        service.setRolloutRepository(rollout);

        assertThat(service.list(userId)).isEmpty();
    }

    private static LocalClientInstance instance(String clientInstanceId, UserId userId, Instant now) {
        return new LocalClientInstance(
                new LocalClientInstanceId(clientInstanceId),
                userId,
                "麒麟工作站",
                "linux",
                "arm64",
                "20260820190000",
                "1.18.4",
                "1",
                List.of("SELF_UPDATE_V1"),
                true,
                "SUCCEEDED",
                "20260820190000",
                now,
                now.minusSeconds(3600),
                now,
                now,
                null);
    }

    private static LocalClientCredential credential(
            UserId userId,
            LocalClientCredentialStatus status,
            Instant now,
            Instant revokedAt) {
        return new LocalClientCredential(
                userId,
                "encrypted-client-key",
                "fingerprint",
                "tack_v1_****WXYZ",
                1,
                status,
                now.minusSeconds(60),
                now,
                now.minusSeconds(30),
                revokedAt);
    }

    private static LocalClientConnectionRoute route(
            LocalClientInstance instance,
            LocalClientProcessStatus processStatus,
            boolean healthy,
            Instant now) {
        return new LocalClientConnectionRoute(
                instance.clientInstanceId(),
                instance.userId(),
                new BackendProcessId("bjp_local_instance_view"),
                8,
                "10.0.0.8",
                List.of("192.0.2.8"),
                4096,
                processStatus,
                4321L,
                now.minusSeconds(30),
                healthy,
                now.minusSeconds(60),
                now);
    }
}
