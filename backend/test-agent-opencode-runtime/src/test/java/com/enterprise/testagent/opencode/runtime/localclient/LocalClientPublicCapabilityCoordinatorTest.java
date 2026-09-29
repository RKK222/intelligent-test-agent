package com.enterprise.testagent.opencode.runtime.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityModels;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.notification.UserNotificationApplicationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalClientPublicCapabilityCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-08-23T08:00:00Z");
    private static final UserId USER_ID = new UserId("usr_public_capability_coordinator");
    private static final LocalClientInstanceId INSTANCE_ID =
            new LocalClientInstanceId("lci_public_capability_coordinator");
    private static final String COMMIT = "c".repeat(40);
    private static final String DIGEST = "d".repeat(64);
    private static final String PREVIOUS_DIGEST = "a".repeat(64);

    private LocalClientPublicCapabilityRepository repository;
    private LocalClientInstanceRepository instanceRepository;
    private LocalClientConnectionStore connectionStore;
    private LocalClientConnectionRegistry connectionRegistry;
    private LocalClientPublicCapabilityCoordinator coordinator;

    @BeforeEach
    void setUp() {
        repository = mock(LocalClientPublicCapabilityRepository.class);
        instanceRepository = mock(LocalClientInstanceRepository.class);
        connectionStore = mock(LocalClientConnectionStore.class);
        connectionRegistry = new LocalClientConnectionRegistry();
        when(instanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance()));
        coordinator = new LocalClientPublicCapabilityCoordinator(
                repository,
                instanceRepository,
                connectionStore,
                connectionRegistry,
                mock(UserNotificationApplicationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void offlineConfirmationBindsNewGenerationAndDispatchesAfterReconnect() {
        when(repository.findInstanceState(INSTANCE_ID)).thenReturn(Optional.of(updateAvailableState()));
        when(repository.findReleaseByDigest(DIGEST)).thenReturn(Optional.of(release()));
        when(repository.findAttempt(INSTANCE_ID, DIGEST)).thenReturn(Optional.empty());
        when(connectionStore.find(INSTANCE_ID)).thenReturn(Optional.empty());
        when(repository.findDispatchableAttempts(anyInt())).thenReturn(List.of());

        var requested = coordinator.requestUpdate(USER_ID, INSTANCE_ID, DIGEST, "trace_request");
        ArgumentCaptor<LocalClientPublicCapabilityModels.Attempt> attemptCaptor =
                ArgumentCaptor.forClass(LocalClientPublicCapabilityModels.Attempt.class);
        verify(repository).insertAttempt(attemptCaptor.capture());
        LocalClientPublicCapabilityModels.Attempt offline = attemptCaptor.getValue();
        assertThat(offline.connectionGeneration()).isZero();
        assertThat(requested.commandId()).isEqualTo(offline.commandId());

        RecordingSender sender = new RecordingSender();
        connectionRegistry.register(INSTANCE_ID, USER_ID, 9L, "model-grant", sender);
        LocalClientPublicCapabilityModels.Attempt rebound = new LocalClientPublicCapabilityModels.Attempt(
                offline.commandId(), INSTANCE_ID, USER_ID, 9L, COMMIT, DIGEST,
                LocalClientPublicCapabilityModels.AttemptStatus.PENDING, null, NOW, NOW, null);
        when(repository.findUpdateAvailableStates(anyInt())).thenReturn(List.of());
        when(repository.findDispatchableAttempts(anyInt())).thenReturn(List.of(offline));
        when(repository.bindAttemptGeneration(offline.commandId(), 0L, 9L, NOW)).thenReturn(true);
        when(repository.findAttempt(offline.commandId())).thenReturn(Optional.of(rebound));
        when(repository.transitionAttempt(
                offline.commandId(), "PENDING", "SENT", null, NOW)).thenReturn(true);

        coordinator.reconcile();

        assertThat(sender.frames).singleElement().satisfies(frame -> {
            assertThat(frame.type()).isEqualTo(LocalClientFrameType.PUBLIC_CAPABILITY_UPDATE_COMMAND);
            LocalClientPayloads.PublicCapabilityUpdateCommand command =
                    new LocalClientFrameCodec().payload(
                            frame, LocalClientPayloads.PublicCapabilityUpdateCommand.class);
            assertThat(command.connectionGeneration()).isEqualTo(9L);
            assertThat(command.bundleDigest()).isEqualTo(DIGEST);
            assertThat(command.commandId()).isEqualTo(offline.commandId());
        });
    }

    @Test
    void reconcilePushesAvailableReleaseToAlreadyConnectedClient() {
        RecordingSender sender = new RecordingSender();
        connectionRegistry.register(INSTANCE_ID, USER_ID, 8L, "model-grant", sender);
        when(repository.findUpdateAvailableStates(anyInt())).thenReturn(List.of(updateAvailableState()));
        when(repository.findReleaseByDigest(DIGEST)).thenReturn(Optional.of(release()));
        when(repository.findDispatchableAttempts(anyInt())).thenReturn(List.of());

        coordinator.reconcile();

        assertThat(sender.frames).singleElement().satisfies(frame -> {
            assertThat(frame.type()).isEqualTo(LocalClientFrameType.PUBLIC_CAPABILITY_AVAILABLE);
            LocalClientPayloads.PublicCapabilityAvailable available =
                    new LocalClientFrameCodec().payload(frame, LocalClientPayloads.PublicCapabilityAvailable.class);
            assertThat(available.clientInstanceId()).isEqualTo(INSTANCE_ID.value());
            assertThat(available.connectionGeneration()).isEqualTo(8L);
            assertThat(available.sourceCommit()).isEqualTo(COMMIT);
            assertThat(available.bundleDigest()).isEqualTo(DIGEST);
        });
    }

    @Test
    void repeatedConfirmationReusesActiveAttempt() {
        LocalClientPublicCapabilityModels.Attempt existing = new LocalClientPublicCapabilityModels.Attempt(
                "lcpc_" + "a".repeat(32), INSTANCE_ID, USER_ID, 0L, COMMIT, DIGEST,
                LocalClientPublicCapabilityModels.AttemptStatus.PENDING, null, NOW, NOW, null);
        when(repository.findInstanceState(INSTANCE_ID)).thenReturn(Optional.of(updateAvailableState()));
        when(repository.findReleaseByDigest(DIGEST)).thenReturn(Optional.of(release()));
        when(repository.findAttempt(INSTANCE_ID, DIGEST)).thenReturn(Optional.of(existing));
        when(repository.findDispatchableAttempts(anyInt())).thenReturn(List.of());

        var result = coordinator.requestUpdate(USER_ID, INSTANCE_ID, DIGEST, "trace_repeat");

        assertThat(result.commandId()).isEqualTo(existing.commandId());
        verify(repository, never()).insertAttempt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void confirmedDiscardUsesPersistentCommandMarkerAndWireFlag() {
        when(repository.findInstanceState(INSTANCE_ID)).thenReturn(Optional.of(updateAvailableState()));
        when(repository.findReleaseByDigest(DIGEST)).thenReturn(Optional.of(release()));
        when(repository.findAttempt(INSTANCE_ID, DIGEST)).thenReturn(Optional.empty());
        when(connectionStore.find(INSTANCE_ID)).thenReturn(Optional.empty());
        when(repository.findDispatchableAttempts(anyInt())).thenReturn(List.of());

        var requested = coordinator.requestUpdate(USER_ID, INSTANCE_ID, DIGEST, true, "trace_confirmed");
        assertThat(requested.commandId()).startsWith("lcpcd_");

        LocalClientPublicCapabilityModels.Attempt attempt = new LocalClientPublicCapabilityModels.Attempt(
                requested.commandId(), INSTANCE_ID, USER_ID, 0L, COMMIT, DIGEST,
                LocalClientPublicCapabilityModels.AttemptStatus.PENDING, null, NOW, NOW, null);
        RecordingSender sender = new RecordingSender();
        connectionRegistry.register(INSTANCE_ID, USER_ID, 8L, "model-grant", sender);
        when(repository.findDispatchableAttempts(anyInt())).thenReturn(List.of(attempt));
        when(repository.bindAttemptGeneration(attempt.commandId(), 0L, 8L, NOW)).thenReturn(true);
        when(repository.findAttempt(attempt.commandId())).thenReturn(Optional.of(
                new LocalClientPublicCapabilityModels.Attempt(
                        attempt.commandId(), INSTANCE_ID, USER_ID, 8L, COMMIT, DIGEST,
                        LocalClientPublicCapabilityModels.AttemptStatus.PENDING, null, NOW, NOW, null)));
        when(repository.transitionAttempt(attempt.commandId(), "PENDING", "SENT", null, NOW)).thenReturn(true);

        coordinator.reconcile();

        LocalClientPayloads.PublicCapabilityUpdateCommand command = new LocalClientFrameCodec().payload(
                sender.frames.stream().filter(frame -> frame.type() == LocalClientFrameType.PUBLIC_CAPABILITY_UPDATE_COMMAND)
                        .findFirst().orElseThrow(), LocalClientPayloads.PublicCapabilityUpdateCommand.class);
        assertThat(command.confirmedDiscardPersonalChanges()).isTrue();
    }

    @Test
    void repeatedTerminalStatusReturnsIdempotentAck() {
        LocalClientPublicCapabilityModels.Attempt succeeded = new LocalClientPublicCapabilityModels.Attempt(
                "lcpc_" + "b".repeat(32), INSTANCE_ID, USER_ID, 4L, COMMIT, DIGEST,
                LocalClientPublicCapabilityModels.AttemptStatus.SUCCEEDED, null,
                NOW.minusSeconds(3), NOW, NOW);
        when(repository.findAttempt(succeeded.commandId())).thenReturn(Optional.of(succeeded));
        LocalClientPayloads.PublicCapabilityUpdateStatus status =
                new LocalClientPayloads.PublicCapabilityUpdateStatus(
                        succeeded.commandId(), INSTANCE_ID.value(), 4L, COMMIT, DIGEST,
                        "SUCCEEDED", null, NOW);

        LocalClientPayloads.PublicCapabilityUpdateStatusAck ack = coordinator.handleStatus(
                USER_ID, INSTANCE_ID, 4L, status, "trace_idempotent");

        assertThat(ack.status()).isEqualTo("SUCCEEDED");
        verify(repository, never()).transitionAttempt(
                anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skippedPublicCapabilityVersionForcesRestartWhenDispatchingUpdate() {
        RecordingSender sender = new RecordingSender();
        connectionRegistry.register(INSTANCE_ID, USER_ID, 9L, "model-grant", sender);
        LocalClientPublicCapabilityModels.Attempt attempt = new LocalClientPublicCapabilityModels.Attempt(
                "lcpc_" + "c".repeat(32), INSTANCE_ID, USER_ID, 9L, COMMIT, DIGEST,
                LocalClientPublicCapabilityModels.AttemptStatus.PENDING, null, NOW, NOW, null);
        when(repository.findUpdateAvailableStates(anyInt())).thenReturn(List.of());
        when(repository.findDispatchableAttempts(anyInt())).thenReturn(List.of(attempt));
        when(repository.findReleaseByDigest(DIGEST)).thenReturn(Optional.of(agentOnlyRelease()));
        when(repository.findInstanceState(INSTANCE_ID)).thenReturn(Optional.of(updateAvailableState("b".repeat(64))));
        when(repository.transitionAttempt(attempt.commandId(), "PENDING", "SENT", null, NOW)).thenReturn(true);

        coordinator.reconcile();

        assertThat(sender.frames).singleElement().satisfies(frame -> {
            LocalClientPayloads.PublicCapabilityUpdateCommand command = new LocalClientFrameCodec().payload(
                    frame, LocalClientPayloads.PublicCapabilityUpdateCommand.class);
            assertThat(command.requiresRestart()).isTrue();
        });
    }

    @Test
    void contiguousAgentOnlyUpdateKeepsHotReload() {
        assertThat(LocalClientPublicCapabilityCoordinator.requiresRestart(
                PREVIOUS_DIGEST, agentOnlyRelease())).isFalse();
    }

    @Test
    void invalidOrMissingPreviousDigestFailsClosedToRestart() {
        LocalClientPublicCapabilityModels.Release malformed = release(
                false, "{\"initial\":false,\"previousDigest\":\"invalid\"}");

        assertThat(LocalClientPublicCapabilityCoordinator.requiresRestart(PREVIOUS_DIGEST, malformed)).isTrue();
        assertThat(LocalClientPublicCapabilityCoordinator.requiresRestart(null, agentOnlyRelease())).isTrue();
    }

    private static LocalClientInstance instance() {
        return new LocalClientInstance(
                INSTANCE_ID, USER_ID, "Mac", "darwin", "arm64", "0.1.0", "2.0.18", "1",
                List.of(LocalClientPublicCapabilityCoordinator.PROTOCOL_CAPABILITY,
                        LocalClientPublicCapabilityCoordinator.PERSONAL_EDIT_CAPABILITY), false,
                null, null, null, NOW.minusSeconds(60), NOW, NOW, null);
    }

    private static LocalClientPublicCapabilityModels.InstanceState updateAvailableState() {
        return updateAvailableState(null);
    }

    private static LocalClientPublicCapabilityModels.InstanceState updateAvailableState(String activeDigest) {
        return new LocalClientPublicCapabilityModels.InstanceState(
                INSTANCE_ID, null, activeDigest, COMMIT, DIGEST,
                LocalClientPublicCapabilityModels.InstanceStatus.UPDATE_AVAILABLE,
                null, NOW, NOW);
    }

    private static LocalClientPublicCapabilityModels.Release release() {
        return release(true, "{\"initial\":true}");
    }

    private static LocalClientPublicCapabilityModels.Release agentOnlyRelease() {
        return release(false, "{\"initial\":false,\"previousDigest\":\"" + PREVIOUS_DIGEST
                + "\",\"toolsChanged\":false,\"dependenciesChanged\":false}");
    }

    private static LocalClientPublicCapabilityModels.Release release(
            boolean requiresRestart,
            String changeSummaryJson) {
        byte[] artifact = new byte[] {1, 2, 3};
        return new LocalClientPublicCapabilityModels.Release(
                COMMIT, DIGEST, "e".repeat(64),
                LocalClientPublicCapabilityModels.Compatibility.AVAILABLE, null,
                "{\"schemaVersion\":1}", changeSummaryJson,
                new LocalClientPublicCapabilityModels.Counts(1, 1, 1), requiresRestart,
                artifact, artifact.length, artifact.length, 4, NOW);
    }

    private static final class RecordingSender implements LocalClientConnectionSender {
        private final List<LocalClientFrame> frames = new ArrayList<>();

        @Override
        public void send(LocalClientFrame frame) {
            frames.add(frame);
        }

        @Override
        public void close(String reason) {
        }
    }
}
