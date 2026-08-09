package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.memory.MemoryLearningEvidence;
import com.enterprise.testagent.domain.memory.MemoryLearningEvidenceRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 从现有会话恢复表读取本轮消息，返回后不在本适配器缓存或复制正文。 */
@Repository
public class MyBatisMemoryLearningEvidenceRepository implements MemoryLearningEvidenceRepository {
    private final MemoryLearningEvidenceMapper mapper;

    public MyBatisMemoryLearningEvidenceRepository(MemoryLearningEvidenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<MemoryLearningEvidence> findByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return Optional.empty();
        }
        List<MemoryLearningEvidence.Message> messages = mapper.findByRunId(runId.trim()).stream()
                .map(row -> new MemoryLearningEvidence.Message(
                        row.role().toLowerCase(java.util.Locale.ROOT), row.content(), row.createdAt()))
                .toList();
        return messages.isEmpty() ? Optional.empty() : Optional.of(new MemoryLearningEvidence(runId, messages));
    }
}
