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

    /** Hub 资产来源；公共 Git 只承担内置 Agent，Skill 只来自平台推送或外部 SkillHub。 */
    public enum SourceKind {
        PLATFORM,
        SKILLHUB
    }

    /** push 对账时，待生效引用与远端提交内容之间的关系。 */
    public enum PushReferenceAction {
        KEEP_SOURCE,
        FORK_TO_PLATFORM,
        REMOVE
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
            Instant updatedAt,
            SourceKind sourceKind,
            boolean sourceAvailable,
            Long externalSkillId,
            String externalVersion,
            String externalSource,
            String externalTag,
            String externalPhase,
            String externalPhaseName,
            String externalContributor,
            Long externalDownloadCount,
            String catalogDisplayName,
            String catalogDescription,
            String forkedFromAssetId,
            String forkedFromRevisionId) {

        public Asset {
            Objects.requireNonNull(skillCategory, "skillCategory must not be null");
            sourceKind = sourceKind == null ? SourceKind.PLATFORM : sourceKind;
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
                    createdAt, updatedAt, SourceKind.PLATFORM, true, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        }

        /** 兼容分类能力引入后的平台资产构造方式。 */
        public Asset(
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
            this(assetId, sourceAppId, sourceApplicationWorkspaceId, assetType, technicalId,
                    skillCategory, skillSubcategory, latestPushedRevisionId, latestPublishedRevisionId,
                    createdAt, updatedAt, SourceKind.PLATFORM, true, null, null, null, null,
                    null, null, null, null, null, null, null, null);
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
            String publishedByUserId,
            Long externalSkillId,
            String externalVersion) {

        /** 兼容既有平台修订构造方式。 */
        public Revision(
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
            this(revisionId, assetId, sourceVersionId, sourceCommitHash, artifactSha256, contentSha256,
                    displayName, displayNameEn, description, deleted, pushedAt, publishedAt,
                    publishedByUserId, null, null);
        }
    }

    /** 外部 SkillHub /list 的目录元数据；正文必须等用户显式预览、引用或更新时再下载。 */
    public record ExternalSkill(
            long id,
            String name,
            String version,
            String displayName,
            String description,
            String source,
            String tag,
            String phase,
            String phaseName,
            String contributor,
            Instant createdAt,
            long downloadCount) {
    }

    /** 外部下载接口返回的原始 ZIP；解包和安全校验由应用服务负责。 */
    public record ExternalSkillPackage(long id, String version, byte[] content) {
        public ExternalSkillPackage {
            content = content == null ? new byte[0] : content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    /** SkillHub 上传接口的单个文件部分；协议字段名由 integration 适配器固定。 */
    public record SkillHubUploadFile(String filename, String contentType, byte[] content) {
        public SkillHubUploadFile {
            content = content == null ? new byte[0] : content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }

        public int size() {
            return content.length;
        }
    }

    /** SkillHub /upload 的完整业务输入；userId 只能取当前认证主体的统一认证号。 */
    public record SkillHubUploadRequest(
            String source,
            String phase,
            String userId,
            SkillHubUploadFile skillPackage,
            SkillHubUploadFile safetyReportPicture,
            SkillHubUploadFile directoryStructurePicture,
            SkillHubUploadFile runningEffectPicture) {
    }

    /** 异步上传提交成功后由 SkillHub 返回的任务标识。 */
    public record SkillHubUploadSubmission(String taskId) {
    }

    /** SkillHub /upload/progress 返回的当前处理状态。 */
    public record SkillHubUploadProgress(int progress, String message) {
    }

    /** push 快照和引用身份切换必须在同一个数据库事务中完成。 */
    public record PushReferenceDecision(
            String referenceId,
            PushReferenceAction action,
            AssetType assetType,
            String technicalId,
            String forkedFromAssetId,
            String forkedFromRevisionId) {
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
            String externalContributorName,
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
