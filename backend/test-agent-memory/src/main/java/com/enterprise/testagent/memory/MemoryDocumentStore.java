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
    List<StoredDocument> search(SearchQuery query);
    List<HistoryEntry> history(String mem0MemoryId);
    List<ExtractedCandidate> extract(ExtractCommand command);
    Health health();

    record AddDocument(
            String content,
            String userId,
            String agentId,
            String applicationId,
            List<QaTaskType> taskTypes,
            Map<String, Object> metadata) {
        @Override
        public String toString() {
            return "AddDocument[userId=" + userId + ", agentId=" + agentId
                    + ", applicationId=" + applicationId + ", taskTypes=" + taskTypes + "]";
        }
    }

    record StoredDocument(
            String id, String content, Map<String, Object> metadata, Instant updatedAt, Double score) {
        public StoredDocument(String id, String content, Map<String, Object> metadata, Instant updatedAt) {
            this(id, content, metadata, updatedAt, null);
        }

        @Override
        public String toString() {
            return "StoredDocument[id=" + id + ", metadataKeys="
                    + (metadata == null ? List.of() : metadata.keySet()) + ", updatedAt=" + updatedAt
                    + ", score=" + score + "]";
        }
    }

    record SearchQuery(
            String query, String userId, String agentId, String applicationId,
            String scope, int topK, double threshold) {
        @Override
        public String toString() {
            return "SearchQuery[userId=" + userId + ", agentId=" + agentId
                    + ", applicationId=" + applicationId + ", scope=" + scope + ", topK=" + topK + "]";
        }
    }

    record HistoryEntry(
            String id, String memoryId, String oldMemory, String newMemory,
            String event, Instant createdAt, Instant updatedAt, boolean deleted) {
        @Override
        public String toString() {
            return "HistoryEntry[id=" + id + ", memoryId=" + memoryId + ", event=" + event + "]";
        }
    }

    record ExtractionMessage(String role, String content) {
        @Override
        public String toString() {
            return "ExtractionMessage[role=" + role + ", contentLength="
                    + (content == null ? 0 : content.length()) + "]";
        }
    }

    record ExtractCommand(
            String model, String modelGrant, String userId, String runId, String sessionId,
            String applicationId, QaTaskType taskType, List<ExtractionMessage> messages) {
        @Override
        public String toString() {
            return "ExtractCommand[model=" + model + ", userId=" + userId + ", runId=" + runId
                    + ", sessionId=" + sessionId + ", applicationId=" + applicationId
                    + ", taskType=" + taskType + ", messageCount="
                    + (messages == null ? 0 : messages.size()) + "]";
        }
    }

    record ExtractedCandidate(
            String content, String scopeSuggestion, List<QaTaskType> taskTypes,
            boolean explicit, boolean temporary, boolean replacesExisting,
            double confidence, String reason) {
        @Override
        public String toString() {
            return "ExtractedCandidate[scopeSuggestion=" + scopeSuggestion + ", taskTypes=" + taskTypes
                    + ", explicit=" + explicit + ", temporary=" + temporary
                    + ", replacesExisting=" + replacesExisting + ", confidence=" + confidence + "]";
        }
    }

    record Health(boolean available, String status, String version) {
    }
}
