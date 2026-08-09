package com.enterprise.testagent.memory;

import com.enterprise.testagent.agent.runtime.AgentRootRunTerminalObserver;
import com.enterprise.testagent.domain.memory.MemoryLearningJob;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 成功人工根 Run 落库后只写恢复定位 Outbox，禁止复制 prompt、回答或工具输出。 */
@Component
public class MemoryLearningOutboxObserver implements AgentRootRunTerminalObserver {
    private final RunRepository runs;
    private final QaMemoryRepository memories;
    private final QaMemoryProperties properties;
    private final Clock clock;

    @Autowired
    public MemoryLearningOutboxObserver(
            RunRepository runs, QaMemoryRepository memories, QaMemoryProperties properties) {
        this(runs, memories, properties, Clock.systemUTC());
    }

    MemoryLearningOutboxObserver(
            RunRepository runs, QaMemoryRepository memories, QaMemoryProperties properties, Clock clock) {
        this.runs = Objects.requireNonNull(runs);
        this.memories = Objects.requireNonNull(memories);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void onTerminal(RunId runId, RunStatus status, String traceId) {
        if (!properties.isEnabled() || status != RunStatus.SUCCEEDED) {
            return;
        }
        Run run = runs.findById(runId)
                .filter(value -> value.status() == RunStatus.SUCCEEDED)
                .filter(value -> value.sourceType() == ConversationSourceType.MANUAL)
                .filter(value -> value.triggeredByUserId() != null)
                .orElse(null);
        if (run == null || !memories.isWhitelisted(run.triggeredByUserId().value())) {
            return;
        }
        Instant now = clock.instant();
        String applicationId = memories.findApplicationIdByRuntimeWorkspace(run.workspaceId().value()).orElse(null);
        memories.enqueueLearningJob(new MemoryLearningJob(
                "mlj_" + UUID.randomUUID().toString().replace("-", ""),
                run.runId().value(), run.sessionId().value(), run.workspaceId().value(),
                run.triggeredByUserId().value(), applicationId,
                run.agentId() == null ? "opencode" : run.agentId(), run.modelId(),
                "PENDING", 0, now, null, null, null, now, now));
    }
}
