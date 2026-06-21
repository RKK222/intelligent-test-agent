package com.icbc.testagent.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.session.SessionId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AgentSessionBindingTest {

    @Test
    void normalizesAgentIdAndKeepsRemoteSessionMapping() {
        Instant now = Instant.parse("2026-06-21T00:00:00Z");

        AgentSessionBinding binding = new AgentSessionBinding(
                new SessionId("ses_1234567890abcdef"),
                " OPENCODE ",
                "ses_remote1234567890abcdef",
                new ExecutionNodeId("node_1234567890abcdef"),
                now,
                now,
                "trace_1234567890abcdef");

        assertThat(binding.agentId()).isEqualTo("opencode");
        assertThat(binding.hasRemoteSession()).isTrue();
    }
}
