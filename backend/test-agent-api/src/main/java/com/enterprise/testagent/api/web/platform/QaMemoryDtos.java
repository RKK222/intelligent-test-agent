package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.domain.memory.MemoryScope;
import com.enterprise.testagent.domain.memory.QaTaskType;
import java.util.List;

/** QA 长期记忆 HTTP 请求 DTO；响应直接使用 memory 模块稳定视图。 */
final class QaMemoryDtos {
    private QaMemoryDtos() {
    }

    record CreatePersonalRequest(
            MemoryScope scope, String applicationId, String content, List<QaTaskType> taskTypes) {
    }

    record CreateTeamRequest(String applicationId, String content, List<QaTaskType> taskTypes) {
    }

    record UpdateMemoryRequest(
            String content, List<QaTaskType> taskTypes, long expectedVersion) {
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
            String primaryChatModelId, boolean currentRunModelFallbackEnabled, long expectedVersion) {
    }
}
