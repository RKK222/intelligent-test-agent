package com.icbc.testagent.app.config;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.opencode.client.OpencodeClientFacade;
import com.icbc.testagent.opencode.client.OpencodeHealthCommand;
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

    /**
     * 注入节点配置和 opencode facade，健康检查不直接访问 generated SDK。
     */
    public OpencodeNodesHealthIndicator(
            TestAgentRuntimeProperties properties,
            OpencodeClientFacade opencodeClientFacade) {
        this.properties = properties;
        this.opencodeClientFacade = opencodeClientFacade;
    }

    /**
     * 聚合所有配置节点健康状态；任一节点不可用时整体标记为 DOWN。
     */
    @Override
    public Health health() {
        if (usesManagerSocketProcesses()) {
            return Health.up()
                    .withDetail("mode", "manager-socket")
                    .withDetail("skipped", true)
                    .build();
        }
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

    /**
     * 将配置项转换为健康检查命令所需的临时执行节点对象。
     */
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

    /**
     * manager/socket 模式下用户进程由 manager 动态创建，配置节点只保留给历史兼容配置，
     * 不再用它决定整体 Actuator health，避免旧 IP 或空端口导致重启后健康检查误报 DOWN。
     */
    private boolean usesManagerSocketProcesses() {
        return true;
    }
}
