package com.enterprise.testagent.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 通用 Mem0 REST 窄接口；Java 不直连记忆 PostgreSQL，也不感知具体向量集合。 */
public interface MemoryDocumentStore {
    List<StoredDocument> add(AddMemories command);
    StoredDocument update(
            String logicalMemoryId,
            String content,
            Map<String, Object> metadata,
            String scope,
            String applicationId,
            RequestContext context);
    Optional<StoredDocument> get(String logicalMemoryId);
    void delete(String logicalMemoryId, RequestContext context);
    List<StoredDocument> search(SearchQuery query);
    List<HistoryEntry> history(String logicalMemoryId);
    Health health();

    record Message(String role, String content) {
        @Override
        public String toString() {
            return "Message[role=" + role + ", contentLength=" + (content == null ? 0 : content.length()) + "]";
        }
    }

    record RequestContext(
            String requesterUserId, String runId, String sessionId, String operationId) {
    }

    record OwnerScope(
            String scope, String userId, String agentId, String applicationId) {
    }

    record AddMemories(
            Object messages,
            boolean infer,
            String chatModelId,
            RequestContext context,
            OwnerScope owner,
            Map<String, Object> metadata) {
        @Override
        public String toString() {
            int count = messages instanceof List<?> values ? values.size() : 1;
            return "AddMemories[infer=" + infer + ", operationId=" + context.operationId()
                    + ", chatModelId=" + chatModelId + ", scope=" + owner.scope()
                    + ", messageCount=" + count + "]";
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
            String query,
            List<OwnerScope> scopes,
            int topK,
            double threshold,
            RequestContext context) {
        @Override
        public String toString() {
            return "SearchQuery[scopeCount=" + (scopes == null ? 0 : scopes.size())
                    + ", topK=" + topK + ", operationId=" + context.operationId() + "]";
        }
    }

    record HistoryEntry(
            String id, String memoryId, String oldMemory, String newMemory,
            String event, Instant createdAt, Instant updatedAt, boolean deleted) {
    }

    record EmbeddingProfileHealth(
            String profileKey, String provider, String model, int dimension,
            String fingerprint, String collection, boolean primary, boolean available) {
    }

    record ProjectionBacklog(int pending, int processing, int dead) {
    }

    record Health(
            boolean available,
            String status,
            String version,
            List<EmbeddingProfileHealth> profiles,
            ProjectionBacklog projectionBacklog) {
        public Health(boolean available, String status, String version) {
            this(available, status, version, List.of(), new ProjectionBacklog(0, 0, 0));
        }
    }
}
