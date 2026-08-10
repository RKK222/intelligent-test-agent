package com.enterprise.testagent.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.notification.UserNotificationId;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.test.StepVerifier;

class UserNotificationRealtimeHubTest {

    @AfterEach
    void cleanTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publishesOnlyAfterCommitAndDropsRolledBackChange() {
        RecordingPublisher publisher = new RecordingPublisher("java-a");
        BackendInstanceIdentity identity = mock(BackendInstanceIdentity.class);
        when(identity.instanceId()).thenReturn("java-a");
        when(identity.linuxServerId()).thenReturn("linux-a");
        UserNotificationRealtimeHub hub = new UserNotificationRealtimeHub(publisher, identity);
        UserNotificationChange change = change("usr_commit", "ntf_commit");

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        hub.publishAfterCommit(change);

        assertThat(publisher.events).isEmpty();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().getFirst();
        synchronization.afterCommit();
        assertThat(publisher.events).hasSize(1);
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        hub.publishAfterCommit(change("usr_rollback", "ntf_rollback"));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(publisher.events).hasSize(1);
    }

    @Test
    void acceptsRemoteChangeForMatchingRecipientAndIgnoresOwnEcho() {
        RecordingPublisher publisher = new RecordingPublisher("java-b");
        BackendInstanceIdentity identity = mock(BackendInstanceIdentity.class);
        when(identity.instanceId()).thenReturn("java-b");
        when(identity.linuxServerId()).thenReturn("linux-b");
        UserNotificationRealtimeHub hub = new UserNotificationRealtimeHub(publisher, identity);
        UserId recipient = new UserId("usr_remote");
        ServerBroadcastEvent remote = new ServerBroadcastEvent(
                "sbe_remote_notification",
                UserNotificationRealtimeHub.BROADCAST_TYPE,
                "java-a",
                "linux-a",
                "trace_remote_notification",
                Instant.parse("2026-08-10T09:00:00Z"),
                Map.of(
                        "recipientUserId", recipient.value(),
                        "notificationId", "ntf_remote",
                        "changeType", "CREATED"));

        StepVerifier.create(hub.events(recipient).take(1))
                .then(() -> hub.handle(remote))
                .assertNext(change -> {
                    assertThat(change.recipientUserId()).isEqualTo(recipient);
                    assertThat(change.notificationId().value()).isEqualTo("ntf_remote");
                    assertThat(change.changeType()).isEqualTo(UserNotificationChangeType.CREATED);
                })
                .verifyComplete();

        hub.handle(new ServerBroadcastEvent(
                "sbe_own_notification",
                UserNotificationRealtimeHub.BROADCAST_TYPE,
                "java-b",
                "linux-b",
                "trace_own_notification",
                Instant.now(),
                remote.payload()));
        assertThat(publisher.events).isEmpty();
    }

    private UserNotificationChange change(String recipientId, String notificationId) {
        return new UserNotificationChange(
                new UserId(recipientId),
                new UserNotificationId(notificationId),
                UserNotificationChangeType.CREATED,
                "trace_notification",
                Instant.parse("2026-08-10T09:00:00Z"));
    }

    private static final class RecordingPublisher implements ServerBroadcastPublisher {
        private final String instanceId;
        private final ArrayList<ServerBroadcastEvent> events = new ArrayList<>();

        private RecordingPublisher(String instanceId) {
            this.instanceId = instanceId;
        }

        @Override
        public String instanceId() {
            return instanceId;
        }

        @Override
        public void publish(ServerBroadcastEvent event) {
            events.add(event);
        }
    }
}
