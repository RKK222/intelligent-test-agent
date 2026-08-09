package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.domain.memory.MemoryScope;
import java.util.List;

/** 通用长期记忆 HTTP 请求 DTO；不暴露 QA 分类、候选置信度或 Mem0 内部 ID。 */
final class QaMemoryDtos {
    private QaMemoryDtos() {
    }

    record CreatePersonalRequest(MemoryScope scope, String applicationId, String content) {
    }

    record CreateTeamRequest(String applicationId, String content, String sourceMemoryId) {
    }

    record UpdateMemoryRequest(String content, long expectedVersion) {
    }

    record VersionRequest(long expectedVersion) {
    }

    record ReviewRequest(String decision, String comment, long expectedVersion) {
    }

    record UsageQueryRequest(List<String> runIds) {
    }

    record CreateSkillProposalRequest(String memoryId, String applicationId, String title) {
    }

    record UpdateSkillProposalRequest(String title, String skillMdDraft, long expectedVersion) {
    }

    record LinkPublishedSkillRequest(String publishedAssetId, long expectedVersion) {
    }

    record AvailabilityResponse(boolean enabled) {
    }

    record WhitelistRequest(String userId) {
    }

    record SettingsRequest(
            String primaryChatModelId, String primaryEmbeddingModelId, long expectedVersion) {
    }
}
