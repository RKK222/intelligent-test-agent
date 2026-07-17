package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 使用 manifest 的进程快照 ID 定位恢复节点；不扫描 Redis 或自行选择其它服务器。 */
@Component
class RunRecoveryExecutionNodeResolver {

    private final Clock clock;

    RunRecoveryExecutionNodeResolver(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    Optional<ExecutionNode> resolve(String executionNodeId, String executionNodeBaseUrl) {
        if (executionNodeId == null || executionNodeId.isBlank()
                || executionNodeBaseUrl == null || executionNodeBaseUrl.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return Optional.of(new ExecutionNode(
                new ExecutionNodeId(executionNodeId.trim()), executionNodeBaseUrl.trim(),
                ExecutionNodeStatus.READY, 0, 1, 100, now, Set.of("opencode"),
                now, now, "trace_run_recovery"));
    }
}
