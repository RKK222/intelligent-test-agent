package com.icbc.testagent.domain.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExecutionNodeTest {

    private static final Instant NOW = Instant.parse("2026-06-20T00:00:00Z");

    @Test
    void readyNodeWithCapacityCanAcceptRun() {
        ExecutionNode node = node(ExecutionNodeStatus.READY, 1, 3);

        assertThat(node.availableCapacity()).isEqualTo(2);
        assertThat(node.canAcceptRun()).isTrue();
    }

    @Test
    void fullOrUnavailableNodeCannotAcceptRun() {
        assertThat(node(ExecutionNodeStatus.READY, 3, 3).canAcceptRun()).isFalse();
        assertThat(node(ExecutionNodeStatus.UNHEALTHY, 0, 3).canAcceptRun()).isFalse();
        assertThat(node(ExecutionNodeStatus.OFFLINE, 0, 3).canAcceptRun()).isFalse();
    }

    @Test
    void constructorCopiesCapabilitiesAndRejectsInvalidCapacity() {
        Set<String> capabilities = new HashSet<>();
        capabilities.add("chat");

        ExecutionNode node = new ExecutionNode(
                new ExecutionNodeId("node_123"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                3,
                100,
                NOW,
                capabilities,
                NOW,
                NOW,
                "trace_123");
        capabilities.add("late");

        assertThat(node.capabilities()).containsExactly("chat");
        assertThatThrownBy(() -> new ExecutionNode(
                        new ExecutionNodeId("node_123"),
                        "http://127.0.0.1:4096",
                        ExecutionNodeStatus.READY,
                        4,
                        3,
                        NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runningRuns");
    }

    private static ExecutionNode node(ExecutionNodeStatus status, int runningRuns, int maxRuns) {
        return new ExecutionNode(
                new ExecutionNodeId("node_123"),
                "http://127.0.0.1:4096",
                status,
                runningRuns,
                maxRuns,
                NOW);
    }
}
