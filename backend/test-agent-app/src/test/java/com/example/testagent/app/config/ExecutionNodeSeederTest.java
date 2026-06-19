package com.example.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.node.ExecutionNodeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExecutionNodeSeederTest {

    @Test
    void seederUpsertsConfiguredOpencodeNodes() throws Exception {
        TestAgentRuntimeProperties properties = new TestAgentRuntimeProperties();
        TestAgentRuntimeProperties.Node node = new TestAgentRuntimeProperties.Node();
        node.setId("node_local_opencode");
        node.setBaseUrl("http://127.0.0.1:4096");
        properties.getOpencode().setNodes(List.of(node));
        FakeExecutionNodeRepository repository = new FakeExecutionNodeRepository();

        new ExecutionNodeSeeder(properties, repository).run(null);

        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.getFirst().executionNodeId().value()).isEqualTo("node_local_opencode");
        assertThat(repository.saved.getFirst().baseUrl()).isEqualTo("http://127.0.0.1:4096");
    }

    private static final class FakeExecutionNodeRepository implements ExecutionNodeRepository {
        private final List<ExecutionNode> saved = new ArrayList<>();

        @Override
        public ExecutionNode save(ExecutionNode executionNode) {
            saved.add(executionNode);
            return executionNode;
        }

        @Override
        public Optional<ExecutionNode> findById(ExecutionNodeId executionNodeId) {
            return Optional.empty();
        }

        @Override
        public List<ExecutionNode> findRoutableNodes(int limit) {
            return saved;
        }
    }
}
