package com.enterprise.testagent.domain.hub;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Agent & Skill Hub 的领域数据。正文只以压缩制品存在，列表和引用状态不携带大内容。
 */
public final class AgentSkillHubModels {

    private AgentSkillHubModels() {
    }

    public enum AssetType {
        AGENT,
        SKILL
    }

    /** Skill Hub 一级事项分类；用户推送的 Skill 默认进入 OTHER。 */
    public enum SkillCategory {
        WORKER,
        TEST,
        CODE,
        OTHER
    }

    /** Skill Hub 二级具体事项；WORKER 与 OTHER 当前没有二级事项。 */
    public enum SkillSubcategory {
        TEST_DESIGN,
        TEST_DATA_CONSTRUCTION,
        TEST_EXECUTION,
        TEST_ANALYSIS,
        WHITE_BOX_ANALYSIS
    }

    public record Artifact(
            String sha256,
            String encoding,
            byte[] content,
            String manifestJson,
            long uncompressedSize,
            long compressedSize,
            int fileCount,
            Instant createdAt) {

        public Artifact {
            Objects.requireNonNull(sha256, "sha256 must not be null");
            Objects.requireNonNull(encoding, "encoding must not be null");
            content = content == null ? new byte[0] : content.clone();
            Objects.requireNonNull(manifestJson, "manifestJson must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    public record Asset(
            String assetId,
            String sourceAppId,
            String sourceApplicationWorkspaceId,
            AssetType assetType,
            String technicalId,
            SkillCategory skillCategory,
            SkillSubcategory skillSubcategory,
            String latestPushedRevisionId,
            String latestPublishedRevisionId,
            Instant createdAt,
            Instant updatedAt) {

        public Asset {
            Objects.requireNonNull(skillCategory, "skillCategory must not be null");
        }

        /** 兼容既有调用方；未显式分类的资产统一进入 OTHER。 */
        public Asset(
                String assetId,
                String sourceAppId,
                String sourceApplicationWorkspaceId,
                AssetType assetType,
                String technicalId,
                String latestPushedRevisionId,
                String latestPublishedRevisionId,
                Instant createdAt,
                Instant updatedAt) {
            this(assetId, sourceAppId, sourceApplicationWorkspaceId, assetType, technicalId,
                    SkillCategory.OTHER, null, latestPushedRevisionId, latestPublishedRevisionId,
                    createdAt, updatedAt);
        }
    }

    public record Revision(
            String revisionId,
            String assetId,
            String sourceVersionId,
            String sourceCommitHash,
            String artifactSha256,
            String contentSha256,
            String displayName,
            String displayNameEn,
            String description,
            boolean deleted,
            Instant pushedAt,
            Instant publishedAt,
            String publishedByUserId) {
    }

    public record Dependency(
            String revisionId,
            String dependencyAssetId,
            String dependencyRevisionId,
            Instant createdAt) {
    }

    public record Reference(
            String referenceId,
            String assetId,
            String targetAppId,
            String targetApplicationWorkspaceId,
            String targetPath,
            String aliasTechnicalId,
            String activeRevisionId,
            String pendingRevisionId,
            String pendingContentSha256,
            String status,
            String createdByUserId,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record UpdateOperation(
            String operationId,
            String referenceId,
            String targetPersonalWorkspaceId,
            String fromRevisionId,
            String toRevisionId,
            String status,
            String conflictsJson,
            String createdByUserId,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** 一次远端提交扫描所得的完整逻辑资产集合。 */
    public record PushedSnapshot(
            String sourceAppId,
            String sourceApplicationWorkspaceId,
            String sourceVersionId,
            String sourceCommitHash,
            Instant pushedAt,
            List<PushedAsset> assets) {
    }

    public record PushedAsset(
            AssetType assetType,
            String technicalId,
            Artifact artifact,
            String contentSha256,
            String displayName,
            String displayNameEn,
            String description) {
    }

    /** 公共配置仓库某个精确提交中的只读 Agent/Skill 修订。 */
    public record BuiltinRevision(
            String revisionId,
            String assetId,
            AssetType assetType,
            String technicalId,
            String sourceCommitHash,
            String artifactSha256,
            String contentSha256,
            String displayName,
            String displayNameEn,
            String description,
            SkillCategory skillCategory,
            SkillSubcategory skillSubcategory,
            Instant pushedAt) {
    }

    /** 公共修订与内容寻址制品的写入载荷；列表查询只读取 BuiltinRevision 元数据。 */
    public record BuiltinPushedRevision(BuiltinRevision revision, Artifact artifact) {
    }

    /** 一次公共配置 HEAD 对账所得的完整快照。 */
    public record BuiltinSnapshot(
            String sourceCommitHash,
            Instant indexedAt,
            List<BuiltinPushedRevision> revisions) {
    }

    /** Hub 浏览页所需的扁平投影，避免前端 N+1。 */
    public record AssetSummary(
            Asset asset,
            Revision pushedRevision,
            Revision publishedRevision,
            String sourceAppName,
            String sourceWorkspaceName,
            boolean updateAvailable,
            String referenceStatus,
            long referenceCount) {
    }

    /** Hub 资产的引用方投影；待推送引用只对目标应用成员可见。 */
    public record ReferenceConsumer(
            Reference reference,
            String targetAppName,
            String targetWorkspaceName) {
    }

    public record ReferenceUpdate(
            Reference reference,
            Asset asset,
            Revision activeRevision,
            Revision latestRevision,
            String sourceAppName,
            String sourceWorkspaceName) {
    }
}
