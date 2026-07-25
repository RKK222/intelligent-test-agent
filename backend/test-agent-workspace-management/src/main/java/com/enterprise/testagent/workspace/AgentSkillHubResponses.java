package com.enterprise.testagent.workspace;

import java.time.Instant;
import java.util.List;

/** Agent & Skill Hub 对 API/文件 WebSocket 暴露的稳定响应。 */
public final class AgentSkillHubResponses {

    private AgentSkillHubResponses() {
    }

    public record PageResponse<T>(List<T> items, long total, int page, int size) {
    }

    public record AssetResponse(
            String assetId, String type, String technicalId, String displayName, String displayNameEn,
            String description, String sourceAppId, String sourceAppName, String sourceWorkspaceId,
            String sourceWorkspaceName, String pushedRevisionId, String publishedRevisionId,
            boolean published, boolean builtin, boolean updateAvailable, boolean referenced, boolean deleted,
            String referenceStatus, long referenceCount,
            Instant pushedAt, Instant publishedAt) {
    }

    public record AssetDetailResponse(
            AssetResponse asset, String selectedRevisionId, List<ArtifactFileResponse> files,
            List<DependencyResponse> dependencies, List<ReferenceConsumerResponse> consumers) {
    }

    public record ArtifactFileResponse(String path, long size, String sha256, String mediaType) {
    }

    public record DependencyResponse(String assetId, String revisionId, String type, String technicalId,
                                     String displayName) {
    }

    public record ReferenceConsumerResponse(
            String referenceId, String targetAppId, String targetAppName, String targetWorkspaceId,
            String targetWorkspaceName, String aliasTechnicalId, String targetPath, String status,
            Instant updatedAt) {
    }

    public record FileContentResponse(String revisionId, String path, String content, long size, String sha256) {
    }

    public record PublishResponse(String assetId, String revisionId, Instant publishedAt, int dependencyCount) {
    }

    public record ReferenceResponse(String referenceId, String assetId, String targetPath, String aliasTechnicalId,
                                    String activeRevisionId, String pendingRevisionId, String status,
                                    boolean runtimeReloadRequired, String message) {
    }

    public record UpdateResponse(String referenceId, String assetId, String technicalId, String displayName,
                                 String sourceAppName, String sourceWorkspaceName, String activeRevisionId,
                                 String latestRevisionId, String status, String targetPath, Instant publishedAt) {
    }

    public record UpdateOperationResponse(String operationId, String referenceId, String status,
                                          List<ConflictFileResponse> files, boolean worktreeChanged) {
    }

    public record ConflictFileResponse(String path, String kind, String baseContent, String currentContent,
                                       String incomingContent, String resultContent, boolean conflicted,
                                       String resolution) {
    }
}
