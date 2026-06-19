package com.example.testagent.app.config;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.node.ExecutionNodeStatus;
import com.example.testagent.opencode.client.OpencodeClientFacade;
import com.example.testagent.opencode.client.OpencodeHealthCommand;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * opencode 节点健康检查，只暴露 nodeId/baseUrl/status/errorCode 等安全字段。
 */
@Component("opencodeNodes")
public class OpencodeNodesHealthIndicator implements HealthIndicator {

    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);

    private final TestAgentRuntimeProperties properties;
    private final OpencodeClientFacade opencodeClientFacade;

    public OpencodeNodesHealthIndicator(
            TestAgentRuntimeProperties properties,
            OpencodeClientFacade opencodeClientFacade) {
        this.properties = properties;
        this.opencodeClientFacade = opencodeClientFacade;
    }

    @Override
    public Health health() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        boolean allAvailable = true;
        for (TestAgentRuntimeProperties.Node configured : properties.getOpencode().getNodes()) {
            try {
                var result = opencodeClientFacade.health(new OpencodeHealthCommand(node(configured), "trace_healthcheck"))
                        .block(HEALTH_TIMEOUT);
                boolean available = result != null && result.available();
                allAvailable = allAvailable && available;
                nodes.add(Map.of(
                        "nodeId", configured.getId(),
                        "baseUrl", configured.getBaseUrl(),
                        "available", available));
            } catch (Exception exception) {
                allAvailable = false;
                nodes.add(Map.of(
                        "nodeId", configured.getId(),
                        "baseUrl", configured.getBaseUrl(),
                        "available", false,
                        "error", exception.getClass().getSimpleName()));
            }
        }
        return (allAvailable ? Health.up() : Health.down())
                .withDetail("nodes", nodes)
                .build();
    }

    private ExecutionNode node(TestAgentRuntimeProperties.Node configured) {
        Instant now = Instant.now();
        return new ExecutionNode(
                new ExecutionNodeId(configured.getId()),
                configured.getBaseUrl(),
                ExecutionNodeStatus.READY,
                0,
                configured.getMaxRuns(),
                configured.getWeight(),
                now,
                Set.copyOf(configured.getCapabilities()),
                now,
                now,
                "trace_healthcheck");
    }
}
