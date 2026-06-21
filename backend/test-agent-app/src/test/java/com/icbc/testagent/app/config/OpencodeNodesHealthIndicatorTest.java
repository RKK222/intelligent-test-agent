package com.icbc.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.icbc.testagent.opencode.client.OpencodeClientFacade;
import com.icbc.testagent.opencode.client.OpencodeHealthResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import reactor.core.publisher.Mono;

class OpencodeNodesHealthIndicatorTest {

    @Test
    void healthReportsUpWhenAllConfiguredNodesAreAvailable() {
        TestAgentRuntimeProperties properties = propertiesWithNode("node_local_opencode", "http://127.0.0.1:4096");
        OpencodeClientFacade facade = org.mockito.Mockito.mock(OpencodeClientFacade.class);
        when(facade.health(any()))
                .thenReturn(Mono.just(new OpencodeHealthResult(true, "http://127.0.0.1:4096")));

        var health = new OpencodeNodesHealthIndicator(properties, facade).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(nodeDetails(health.getDetails())).singleElement()
                .satisfies(node -> {
                    assertThat(node).containsEntry("nodeId", "node_local_opencode");
                    assertThat(node).containsEntry("available", true);
                });
    }

    @Test
    void healthReportsDownAndSafeErrorClassWhenAnyNodeFails() {
        TestAgentRuntimeProperties properties = propertiesWithNode("node_local_opencode", "http://127.0.0.1:4096");
        OpencodeClientFacade facade = org.mockito.Mockito.mock(OpencodeClientFacade.class);
        when(facade.health(any()))
                .thenReturn(Mono.error(new IllegalStateException("connection failed")));

        var health = new OpencodeNodesHealthIndicator(properties, facade).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(nodeDetails(health.getDetails())).singleElement()
                .satisfies(node -> {
                    assertThat(node).containsEntry("nodeId", "node_local_opencode");
                    assertThat(node).containsEntry("available", false);
                    assertThat(node).containsEntry("error", "IllegalStateException");
                    assertThat(node).doesNotContainEntry("error", "connection failed");
                });
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> nodeDetails(Map<String, Object> details) {
        return (List<Map<String, Object>>) details.get("nodes");
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
