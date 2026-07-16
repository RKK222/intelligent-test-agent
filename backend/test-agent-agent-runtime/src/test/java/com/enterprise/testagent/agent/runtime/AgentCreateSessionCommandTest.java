package com.enterprise.testagent.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentCreateSessionCommandTest {

    @Test
    void normalizesOptionalTitleWithoutChangingExplicitTitle() {
        assertThat(command(null).title()).isNull();
        assertThat(command("  ").title()).isNull();
        assertThat(command("  AI generated title  ").title()).isEqualTo("  AI generated title  ");
    }

    private static AgentCreateSessionCommand command(String title) {
        return new AgentCreateSessionCommand(node(), "/tmp/demo", null, title, "trace_1234567890abcdef");
    }

    private static ExecutionNode node() {
        Instant now = Instant.parse("2026-07-10T00:00:00Z");
        return new ExecutionNode(
                new ExecutionNodeId("node_1234567890abcdef"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                4,
                100,
                now,
                Set.of("chat"),
                now,
                now,
                "trace_1234567890abcdef");
    }
}
