package com.icbc.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.broadcast.ServerBroadcastEvent;
import com.icbc.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.icbc.testagent.domain.configuration.AgentConfigOperationStatus;
import com.icbc.testagent.workspace.AgentConfigProgressEvent;
import com.icbc.testagent.workspace.WorkspaceServerIdentity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentConfigProgressHubTest {

    @Test
    void publishesProgressToServerBroadcastAndConsumesRemoteProgress() {
        RecordingBroadcastPublisher publisher = new RecordingBroadcastPublisher("instance-origin");
        AgentConfigProgressHub origin = new AgentConfigProgressHub(publisher, new WorkspaceServerIdentity("linux-1"));
        AgentConfigProgressEvent event = new AgentConfigProgressEvent(
                "aco_progress_12345678",
                "step",
                AgentConfigOperationStatus.RUNNING,
                "PUSHING",
                "git push origin main",
                null,
                null,
                null,
                "trace_progress",
                Instant.parse("2026-06-28T10:00:00Z"));

        origin.publish(event);

        assertThat(publisher.events).hasSize(1);
        assertThat(publisher.events.get(0).type()).isEqualTo(AgentConfigProgressHub.BROADCAST_TYPE);

        AgentConfigProgressHub receiver = new AgentConfigProgressHub(
                new RecordingBroadcastPublisher("instance-receiver"),
                new WorkspaceServerIdentity("linux-2"));
        List<AgentConfigProgressEvent> received = new ArrayList<>();
        receiver.events("aco_progress_12345678").subscribe(received::add);

        receiver.handle(publisher.events.get(0));

        assertThat(received)
                .extracting(AgentConfigProgressEvent::operationId)
                .containsExactly("aco_progress_12345678");
        assertThat(received.get(0).currentStep()).isEqualTo("PUSHING");
        assertThat(received.get(0).command()).isEqualTo("git push origin main");
    }

    private static final class RecordingBroadcastPublisher implements ServerBroadcastPublisher {
        private final List<ServerBroadcastEvent> events = new ArrayList<>();
        private final String instanceId;

        private RecordingBroadcastPublisher(String instanceId) {
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
