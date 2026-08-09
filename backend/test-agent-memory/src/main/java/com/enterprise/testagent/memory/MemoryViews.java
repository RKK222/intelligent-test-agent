package com.enterprise.testagent.memory;

import com.enterprise.testagent.domain.memory.MemoryEvidence;
import com.enterprise.testagent.domain.memory.MemoryScope;
import com.enterprise.testagent.domain.memory.MemorySettings;
import com.enterprise.testagent.domain.memory.MemorySkillProposal;
import com.enterprise.testagent.domain.memory.MemorySkillProposalStatus;
import com.enterprise.testagent.domain.memory.MemorySource;
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.domain.memory.MemoryUsage;
import com.enterprise.testagent.domain.memory.MemoryWhitelistEntry;
import com.enterprise.testagent.domain.memory.QaMemory;
import java.time.Instant;
import java.util.List;

/** 记忆模块对 API 暴露的稳定应用层视图，不携带 Mem0 内部对象。 */
public final class MemoryViews {
    private MemoryViews() {
    }

    public record Page<T>(List<T> items, long total, int page, int size) {
    }

    public record MemoryView(
            String memoryId, MemoryScope scope, String ownerUserId, String applicationId,
            MemoryStatus status, MemorySource source,
            String content, boolean contentAvailable, String displaySummary, long version,
            Instant confirmedAt, Instant createdAt, Instant updatedAt) {
        static MemoryView from(QaMemory memory, String content, boolean contentAvailable) {
            return new MemoryView(
                    memory.memoryId().value(), memory.scope(), memory.ownerUserId(), memory.applicationId(),
                    memory.status(), publicSource(memory.source()), content, contentAvailable,
                    memory.displaySummary(), memory.version(), memory.confirmedAt(),
                    memory.createdAt(), memory.updatedAt());
        }
    }

    public record MemoryEvidenceView(
            String evidenceId, String memoryId, String runId, String sessionId, String sessionTitle,
            boolean transcriptAvailable,
            MemorySource source, String summary, Instant observedAt) {
        static MemoryEvidenceView from(MemoryEvidence evidence, String viewerUserId) {
            return new MemoryEvidenceView(
                    evidence.evidenceId(), evidence.memoryId().value(), evidence.runId(), evidence.sessionId(),
                    evidence.sessionTitle(), viewerUserId.equals(evidence.sessionOwnerUserId()),
                    publicSource(evidence.source()), evidence.summary(), evidence.observedAt());
        }
    }

    public record MemoryUsageView(
            String runId, String memoryId, MemoryScope scope, int rank, int tokenCount, Instant injectedAt) {
        static MemoryUsageView from(MemoryUsage usage) {
            return new MemoryUsageView(
                    usage.runId(), usage.memoryId().value(), usage.scope(), usage.rank(),
                    usage.tokenCount(), usage.injectedAt());
        }
    }

    public record SkillProposalView(
            String proposalId, String memoryId, String applicationId, String title, String skillMdDraft,
            MemorySkillProposalStatus status, String createdByUserId, String reviewedByUserId, String publishedAssetId,
            long version, Instant createdAt, Instant updatedAt) {
        static SkillProposalView from(MemorySkillProposal proposal) {
            return new SkillProposalView(
                    proposal.proposalId(), proposal.memoryId().value(), proposal.applicationId(), proposal.title(),
                    proposal.skillMdDraft(), proposal.status(), proposal.createdByUserId(),
                    proposal.reviewedByUserId(), proposal.publishedAssetId(), proposal.version(),
                    proposal.createdAt(), proposal.updatedAt());
        }
    }

    public record WhitelistView(
            String userId, boolean enabled, String updatedByUserId, Instant createdAt, Instant updatedAt) {
        static WhitelistView from(MemoryWhitelistEntry entry) {
            return new WhitelistView(entry.userId(), entry.enabled(), entry.updatedByUserId(),
                    entry.createdAt(), entry.updatedAt());
        }
    }

    public record AdminHealthView(
            boolean enabled, MemoryDocumentStore.Health memoryService,
            String primaryChatModelId, String primaryEmbeddingModelId, String cpuEmbeddingModelId,
            int queuePending, int queueProcessing, int queueDead) {
    }

    public record SettingsView(
            String primaryChatModelId, String primaryEmbeddingModelId, String cpuEmbeddingModelId,
            long version, String updatedByUserId, Instant updatedAt) {
        static SettingsView from(MemorySettings settings) {
            return new SettingsView(
                    settings.primaryChatModelId(), settings.primaryEmbeddingModelId(), settings.cpuEmbeddingModelId(),
                    settings.version(), settings.updatedByUserId(), settings.updatedAt());
        }
    }

    /** 升级前来源值只留在隐藏兼容表中，通用 API 不再暴露 QA 抽取分类。 */
    private static MemorySource publicSource(MemorySource source) {
        return switch (source) {
            case EXPLICIT, IMPLICIT -> MemorySource.NATIVE;
            case ADMIN_CREATED -> MemorySource.MANUAL;
            default -> source;
        };
    }
}
