package com.enterprise.testagent.opencode.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.opencode.runtime.session.SessionMessageRealtimeHub.SessionMessageChange;
import com.enterprise.testagent.opencode.runtime.session.SessionMessageRealtimeHub.SessionMessageChangeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.test.StepVerifier;

class SessionMessageRealtimeHubTest {

    private static final SessionId SESSION = new SessionId("ses_message_realtime");
    private static final RunId SOURCE_RUN = new RunId("run_message_realtime_source");
    private static final RunId RUN = new RunId("run_message_realtime");
    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");

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
        SessionMessageRealtimeHub hub = hub(publisher, "java-a", "linux-a");

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        hub.publishAfterCommit(change(SESSION, RUN));

        assertThat(publisher.events).isEmpty();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().getFirst();
        synchronization.afterCommit();
        assertThat(publisher.events).hasSize(1);
        assertThat(publisher.events.getFirst().payload()).containsOnly(
                Map.entry("sessionId", SESSION.value()),
                Map.entry("sourceRunId", SOURCE_RUN.value()),
                Map.entry("replacementRunId", RUN.value()),
                Map.entry("runId", RUN.value()),
                Map.entry("changeType", SessionMessageChangeType.RESEND_RESERVED.name()),
                Map.entry("revision", NOW.toString()));
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        hub.publishAfterCommit(change(new SessionId("ses_rollback"), new RunId("run_rollback")));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(publisher.events).hasSize(1);
    }

    @Test
    void acceptsRemoteChangeForMatchingSessionAndIgnoresOwnEcho() {
        RecordingPublisher publisher = new RecordingPublisher("java-b");
        SessionMessageRealtimeHub hub = hub(publisher, "java-b", "linux-b");
        ServerBroadcastEvent remote = new ServerBroadcastEvent(
                "sbe_remote_message",
                SessionMessageRealtimeHub.BROADCAST_TYPE,
                "java-a",
                "linux-a",
                "trace_remote_message",
                NOW,
                Map.of(
                        "sessionId", SESSION.value(),
                        "sourceRunId", SOURCE_RUN.value(),
                        "replacementRunId", RUN.value(),
                        "changeType", SessionMessageChangeType.RESEND_RESTORED.name(),
                        "revision", NOW.toString()));

        StepVerifier.create(hub.events(SESSION).take(1))
                .then(() -> hub.handle(remote))
                .assertNext(change -> {
                    assertThat(change.sessionId()).isEqualTo(SESSION);
                    assertThat(change.sourceRunId()).isEqualTo(SOURCE_RUN);
                    assertThat(change.replacementRunId()).isEqualTo(RUN);
                    assertThat(change.changeType()).isEqualTo(SessionMessageChangeType.RESEND_RESTORED);
                    assertThat(change.revision()).isEqualTo(NOW);
                    assertThat(change.traceId()).isEqualTo("trace_remote_message");
                })
                .verifyComplete();

        hub.handle(new ServerBroadcastEvent(
                "sbe_own_message",
                SessionMessageRealtimeHub.BROADCAST_TYPE,
                "java-b",
                "linux-b",
                "trace_own_message",
                NOW,
                remote.payload()));
        assertThat(publisher.events).isEmpty();
    }

    private SessionMessageRealtimeHub hub(
            RecordingPublisher publisher,
            String instanceId,
            String linuxServerId) {
        BackendInstanceIdentity identity = mock(BackendInstanceIdentity.class);
        when(identity.instanceId()).thenReturn(instanceId);
        when(identity.linuxServerId()).thenReturn(linuxServerId);
        return new SessionMessageRealtimeHub(publisher, identity);
    }

    private SessionMessageChange change(SessionId sessionId, RunId runId) {
        return new SessionMessageChange(
                sessionId, SOURCE_RUN, runId, SessionMessageChangeType.RESEND_RESERVED,
                NOW, "trace_message_realtime", NOW);
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
