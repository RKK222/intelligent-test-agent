package com.enterprise.testagent.memory;

import com.enterprise.testagent.domain.memory.QaTaskType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Mem0 窄接口；业务层不能访问 Mem0 原始服务 API 或上游对象。 */
public interface MemoryDocumentStore {
    StoredDocument add(AddDocument command);
    StoredDocument update(String mem0MemoryId, String content, Map<String, Object> metadata);
    Optional<StoredDocument> get(String mem0MemoryId);
    void delete(String mem0MemoryId);
    Health health();

    record AddDocument(
            String content,
            String userId,
            String agentId,
            String applicationId,
            List<QaTaskType> taskTypes,
            Map<String, Object> metadata) {
    }

    record StoredDocument(String id, String content, Map<String, Object> metadata, Instant updatedAt) {
    }

    record Health(boolean available, String status, String version) {
    }
}
