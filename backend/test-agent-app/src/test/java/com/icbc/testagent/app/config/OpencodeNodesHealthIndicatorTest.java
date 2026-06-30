package com.icbc.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.opencode.client.OpencodeClientFacade;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class OpencodeNodesHealthIndicatorTest {

    @Test
    void socketManagerModeSkipsLegacyConfiguredNodeProbe() {
        TestAgentRuntimeProperties properties = propertiesWithNode("node_test_opencode", "http://127.0.0.1:4096");
        OpencodeClientFacade facade = org.mockito.Mockito.mock(OpencodeClientFacade.class);

        var health = new OpencodeNodesHealthIndicator(properties, facade).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("mode", "manager-socket");
        assertThat(health.getDetails()).containsEntry("skipped", true);
    }

    private static TestAgentRuntimeProperties propertiesWithNode(String nodeId, String baseUrl) {
        TestAgentRuntimeProperties properties = new TestAgentRuntimeProperties();
        TestAgentRuntimeProperties.Node node = new TestAgentRuntimeProperties.Node();
        node.setId(nodeId);
        node.setBaseUrl(baseUrl);
        properties.getOpencode().setNodes(List.of(node));
        return properties;
    }
}
