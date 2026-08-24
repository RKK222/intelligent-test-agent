package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** Agent & Skill Hub 的 MyBatis 行投影。 */
public final class AgentSkillHubRows {

    private AgentSkillHubRows() {
    }

    public record ArtifactRow(String artifactSha256, String encoding, byte[] content, String manifestJson,
                              long uncompressedSize, long compressedSize, int fileCount, Instant createdAt) {
    }

    public record AssetRow(String assetId, String sourceAppId, String sourceApplicationWorkspaceId,
                           String assetType, String technicalId, String skillCategory, String skillSubcategory,
                           String latestPushedRevisionId,
                           String latestPublishedRevisionId, Instant createdAt, Instant updatedAt,
                           String sourceKind, boolean sourceAvailable, String externalIdentityKey,
                           Long externalSkillId, String externalVersion, String externalSource,
                           String externalTag, String externalPhase, String externalPhaseName,
                           String externalContributor, Long externalDownloadCount,
                           String catalogDisplayName, String catalogDescription,
                           String forkedFromAssetId, String forkedFromRevisionId) {
    }

    public record RevisionRow(String revisionId, String assetId, String sourceVersionId,
                              String sourceCommitHash, String artifactSha256, String contentSha256,
                              String displayName, String displayNameEn, String description, boolean deleted,
                              Instant pushedAt, Instant publishedAt, String publishedByUserId,
                              Long externalSkillId, String externalVersion) {
    }

    public record BuiltinRevisionRow(
            String revisionId, String assetId, String assetType, String technicalId,
            String sourceCommitHash, String artifactSha256, String contentSha256,
            String displayName, String displayNameEn, String description,
            String skillCategory, String skillSubcategory, Instant pushedAt) {
    }

    public record DependencyRow(String revisionId, String dependencyAssetId,
                                String dependencyRevisionId, Instant createdAt) {
    }

    public record ReferenceRow(String referenceId, String assetId, String targetAppId,
                               String targetApplicationWorkspaceId, String targetPath, String aliasTechnicalId,
                               String activeRevisionId, String pendingRevisionId, String pendingContentSha256,
                               String status, String createdByUserId, Instant createdAt, Instant updatedAt) {
    }

    public record UpdateOperationRow(String operationId, String referenceId, String targetPersonalWorkspaceId,
                                     String fromRevisionId, String toRevisionId, String status,
                                     String conflictsJson, String createdByUserId, Instant createdAt, Instant updatedAt) {
    }

    public record AssetSummaryRow(
            String assetId, String sourceAppId, String sourceApplicationWorkspaceId, String assetType,
            String technicalId, String skillCategory, String skillSubcategory,
            String latestPushedRevisionId, String latestPublishedRevisionId,
            Instant assetCreatedAt, Instant assetUpdatedAt,
            String sourceKind, boolean sourceAvailable, Long externalSkillId, String externalVersion,
            String externalSource, String externalTag, String externalPhase, String externalPhaseName,
            String externalContributor, String externalContributorName, Long externalDownloadCount,
            String catalogDisplayName,
            String catalogDescription, String forkedFromAssetId, String forkedFromRevisionId,
            String pushedRevisionId, String pushedSourceVersionId, String pushedSourceCommitHash,
            String pushedArtifactSha256, String pushedContentSha256, String pushedDisplayName,
            String pushedDisplayNameEn, String pushedDescription, Boolean pushedDeleted,
            Instant pushedAt, Instant pushedPublishedAt, String pushedPublishedByUserId,
            String publishedRevisionId, String publishedSourceVersionId, String publishedSourceCommitHash,
            String publishedArtifactSha256, String publishedContentSha256, String publishedDisplayName,
            String publishedDisplayNameEn, String publishedDescription, Boolean publishedDeleted,
            Instant publishedPushedAt, Instant publishedAt, String publishedByUserId,
            String sourceAppName, String sourceWorkspaceName, boolean updateAvailable, String referenceStatus,
            long referenceCount) {
    }

    public record ReferenceConsumerRow(
            String referenceId, String assetId, String targetAppId, String targetApplicationWorkspaceId,
            String targetPath, String aliasTechnicalId, String activeRevisionId, String pendingRevisionId,
            String pendingContentSha256, String status, String createdByUserId, Instant referenceCreatedAt,
            Instant referenceUpdatedAt, String targetAppName, String targetWorkspaceName) {
    }

    public record ReferenceUpdateRow(
            String referenceId, String assetId, String targetAppId, String targetApplicationWorkspaceId,
            String targetPath, String aliasTechnicalId, String activeRevisionId, String pendingRevisionId,
            String pendingContentSha256, String status, String createdByUserId, Instant referenceCreatedAt,
            Instant referenceUpdatedAt, String sourceAppId, String sourceApplicationWorkspaceId,
            String assetType, String technicalId, String latestPushedRevisionId, String latestPublishedRevisionId,
            Instant assetCreatedAt, Instant assetUpdatedAt,
            String sourceKind, boolean sourceAvailable, Long externalSkillId, String externalVersion,
            String externalSource, String externalTag, String externalPhase, String externalPhaseName,
            String externalContributor, Long externalDownloadCount, String catalogDisplayName,
            String catalogDescription, String forkedFromAssetId, String forkedFromRevisionId,
            String activeSourceVersionId, String activeSourceCommitHash, String activeArtifactSha256,
            String activeContentSha256, String activeDisplayName, String activeDisplayNameEn,
            String activeDescription, Boolean activeDeleted, Instant activePushedAt, Instant activePublishedAt,
            String activePublishedByUserId, Long activeExternalSkillId, String activeExternalVersion,
            String latestSourceVersionId, String latestSourceCommitHash, String latestArtifactSha256,
            String latestContentSha256, String latestDisplayName, String latestDisplayNameEn,
            String latestDescription, boolean latestDeleted, Instant latestPushedAt, Instant latestPublishedAt,
            String latestPublishedByUserId, Long latestExternalSkillId, String latestExternalVersion,
            String sourceAppName, String sourceWorkspaceName) {
    }
}
