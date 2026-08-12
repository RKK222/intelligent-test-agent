package com.enterprise.testagent.domain.memory;

import java.util.Optional;

/** 只读现有消息/终态摘要的学习证据端口，不建立第二份聊天存储。 */
public interface MemoryLearningEvidenceRepository {
    Optional<MemoryLearningEvidence> findByRunId(String runId);
}
