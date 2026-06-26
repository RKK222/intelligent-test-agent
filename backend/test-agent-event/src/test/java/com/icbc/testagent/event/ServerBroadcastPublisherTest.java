package com.icbc.testagent.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.broadcast.ServerBroadcastEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServerBroadcastPublisherTest {

    @Test
    void noopPublisherAcceptsServerBroadcastEventWithoutSideEffects() {
        ServerBroadcastEvent event = new ServerBroadcastEvent(
                "sbe_1234567890abcdef",
                "workspace.version.sync-requested",
                "instance-a",
                "10.8.0.11",
                "trace_1234567890abcdef",
                Instant.parse("2026-06-26T00:00:00Z"),
                Map.of("versionId", "awv_123", "targetCommitHash", "abc123"));

        NoopServerBroadcastPublisher.INSTANCE.publish(event);

        assertThat(NoopServerBroadcastPublisher.INSTANCE.instanceId()).isEqualTo("noop");
        assertThat(NoopServerBroadcastPublisher.INSTANCE).isSameAs(NoopServerBroadcastPublisher.INSTANCE);
    }
}
