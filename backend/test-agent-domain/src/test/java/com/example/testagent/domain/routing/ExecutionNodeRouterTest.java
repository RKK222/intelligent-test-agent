package com.example.testagent.domain.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.node.ExecutionNodeStatus;
import com.example.testagent.domain.run.RunId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExecutionNodeRouterTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void routerSelectsReadyNodeWithCapacityAndLowestLoad() {
        ExecutionNode busy = node("node_busy", ExecutionNodeStatus.READY, 3, 4, 100);
        ExecutionNode selected = node("node_selected", ExecutionNodeStatus.READY, 1, 4, 50);
        ExecutionNode unhealthy = node("node_unhealthy", ExecutionNodeStatus.UNHEALTHY, 0, 4, 100);

        RoutingDecision decision = new ExecutionNodeRouter().route(
                new RunId("run_1234567890abcdef"),
                List.of(busy, selected, unhealthy),
                NOW,
                "trace_1234567890abcdef");

        assertThat(decision.executionNodeId()).isEqualTo(selected.executionNodeId());
        assertThat(decision.reason()).isEqualTo(RoutingReason.LOWEST_LOAD);
    }

    @Test
    void routerRaisesUnavailableWhenNoNodeCanRun() {
        ExecutionNode full = node("node_full", ExecutionNodeStatus.READY, 4, 4, 100);
        ExecutionNode offline = node("node_offline", ExecutionNodeStatus.OFFLINE, 0, 4, 100);

        assertThatThrownBy(() -> new ExecutionNodeRouter().route(
                        new RunId("run_1234567890abcdef"),
                        List.of(full, offline),
                        NOW,
                        "trace_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));
    }

    private static ExecutionNode node(String id, ExecutionNodeStatus status, int runningRuns, int maxRuns, int weight) {
        return new ExecutionNode(
                new ExecutionNodeId(id),
                "http://127.0.0.1:4096",
                status,
                runningRuns,
                maxRuns,
                weight,
                NOW,
                Set.of("chat"),
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }
}
