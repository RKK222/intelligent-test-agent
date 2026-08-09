package com.enterprise.testagent.persistence.mybatis;

import java.math.BigDecimal;
import java.time.Instant;

/** 通用记忆治理表行模型；QA 命名是不可重命名的存量表兼容边界。 */
public final class QaMemoryRows {
    private QaMemoryRows() {
    }

    public record MemoryRow(
            String memoryId, String mem0MemoryId, String scope, String ownerUserId, String applicationId,
            String status, String source, String taskTypesCsv, String displaySummary, BigDecimal confidence,
            int distinctSessionCount, int distinctUserCount, Instant firstObservedAt, Instant lastObservedAt,
            Instant confirmedAt, String supersededByMemoryId, String createdByUserId, long version,
            String vectorSyncStatus, Instant createdAt, Instant updatedAt) {
    }

    public record EvidenceRow(
            String evidenceId, String memoryId, String runId, String sessionId,
            String sessionTitle, String sessionOwnerUserId, String observedUserId,
            String source, String evidenceSummary, Instant observedAt) {
    }

    public record ReviewRow(
            String reviewId, String memoryId, String applicationId, String submittedByUserId,
            String reviewedByUserId, String decision, String comment, Instant submittedAt, Instant reviewedAt) {
    }

    public record LearningJobRow(
            String jobId, String runId, String sessionId, String workspaceId, String userId,
            String applicationId, String agentId, String selectedModelId, String status, int attempts,
            Instant availableAt, String claimedBy, Instant leaseUntil, String lastErrorCode,
            Instant createdAt, Instant updatedAt) {
    }

    public record UsageRow(
            String runId, String memoryId, String userId, String applicationId, String scope,
            int rankIndex, int tokenCount, Instant injectedAt) {
    }

    public record SkillProposalRow(
            String proposalId, String memoryId, String applicationId, String title, String skillMdDraft,
            String status, String createdByUserId, String reviewedByUserId, String publishedAssetId,
            long version, Instant createdAt, Instant updatedAt) {
    }

    public record WhitelistRow(
            String userId, boolean enabled, String updatedByUserId, Instant createdAt, Instant updatedAt) {
    }

    public record SettingsRow(
            String primaryChatModelId, String primaryEmbeddingModelId, String cpuEmbeddingModelId, long version,
            String updatedByUserId, Instant updatedAt) {
    }
}
