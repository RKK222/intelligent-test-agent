package com.example.testagent.app.config;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.node.ExecutionNodeRepository;
import com.example.testagent.domain.node.ExecutionNodeStatus;
import java.time.Instant;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时把配置中的 opencode 节点写入 execution_nodes 表，便于本地环境无需手工插入路由节点。
 */
@Component
public class ExecutionNodeSeeder implements ApplicationRunner {

    private final TestAgentRuntimeProperties properties;
    private final ExecutionNodeRepository executionNodeRepository;

    /**
     * 注入运行配置和执行节点 Repository，seed 数据来自 test-agent.opencode.nodes。
     */
    public ExecutionNodeSeeder(
            TestAgentRuntimeProperties properties,
            ExecutionNodeRepository executionNodeRepository) {
        this.properties = properties;
        this.executionNodeRepository = executionNodeRepository;
    }

    /**
     * 启动时写入配置化 opencode 节点，保证路由层有可用候选节点。
     */
    @Override
    public void run(ApplicationArguments args) {
        Instant now = Instant.now();
        for (TestAgentRuntimeProperties.Node configured : properties.getOpencode().getNodes()) {
            executionNodeRepository.save(new ExecutionNode(
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
                    "trace_startup"));
        }
    }
}
