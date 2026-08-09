package com.enterprise.testagent.domain.memory;

import java.time.Instant;
import java.util.Objects;

/** 从已生效记忆发起的 Skill 草稿；发布仍由既有 Workspace/Git/Hub 流程完成。 */
public record MemorySkillProposal(
        String proposalId,
        MemoryId memoryId,
        String applicationId,
        String title,
        String skillMdDraft,
        String status,
        String createdByUserId,
        String reviewedByUserId,
        String publishedAssetId,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public MemorySkillProposal {
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
