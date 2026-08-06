package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Artifact;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Asset;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetSummary;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetType;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.BuiltinPushedRevision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.BuiltinRevision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.BuiltinSnapshot;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Dependency;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushedAsset;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushedSnapshot;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Reference;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ReferenceConsumer;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ReferenceUpdate;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Revision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillCategory;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillSubcategory;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.UpdateOperation;
import com.enterprise.testagent.domain.hub.AgentSkillHubRepository;
import com.enterprise.testagent.persistence.mybatis.AgentSkillHubRows.ArtifactRow;
import com.enterprise.testagent.persistence.mybatis.AgentSkillHubRows.AssetRow;
import com.enterprise.testagent.persistence.mybatis.AgentSkillHubRows.AssetSummaryRow;
import com.enterprise.testagent.persistence.mybatis.AgentSkillHubRows.BuiltinRevisionRow;
import com.enterprise.testagent.persistence.mybatis.AgentSkillHubRows.DependencyRow;
import com.enterprise.testagent.persistence.mybatis.AgentSkillHubRows.ReferenceRow;
import com.enterprise.testagent.persistence.mybatis.AgentSkillHubRows.ReferenceConsumerRow;
import com.enterprise.testagent.persistence.mybatis.AgentSkillHubRows.ReferenceUpdateRow;
import com.enterprise.testagent.persistence.mybatis.AgentSkillHubRows.RevisionRow;
import com.enterprise.testagent.persistence.mybatis.AgentSkillHubRows.UpdateOperationRow;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Hub 领域端口的 MyBatis 实现。 */
@Repository
public class MyBatisAgentSkillHubRepository implements AgentSkillHubRepository {

    private static final String PUBLIC_SOURCE_KEY = "PUBLIC";

    private final AgentSkillHubMapper mapper;

    public MyBatisAgentSkillHubRepository(AgentSkillHubMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 原子替换来源工作空间的最新 push 快照。多节点重复执行通过内容摘要和 on-conflict 去重。
     */
    @Override
    @Transactional
    public void replacePushedSnapshot(PushedSnapshot snapshot) {
        Set<String> identities = new HashSet<>();
        for (PushedAsset pushed : snapshot.assets()) {
            identities.add(pushed.assetType().name() + ":" + pushed.technicalId());
            Artifact artifact = pushed.artifact();
            mapper.insertArtifact(toRow(artifact));
            AssetRow asset = mapper.findAssetByIdentity(
                    snapshot.sourceAppId(), snapshot.sourceApplicationWorkspaceId(),
                    pushed.assetType().name(), pushed.technicalId());
            if (asset == null) {
                String assetId = id("hub_asset_");
                mapper.insertAsset(new AssetRow(
                        assetId, snapshot.sourceAppId(), snapshot.sourceApplicationWorkspaceId(),
                        pushed.assetType().name(), pushed.technicalId(), SkillCategory.OTHER.name(), null,
                        null, null,
                        snapshot.pushedAt(), snapshot.pushedAt()));
                asset = mapper.findAssetByIdentity(
                        snapshot.sourceAppId(), snapshot.sourceApplicationWorkspaceId(),
                        pushed.assetType().name(), pushed.technicalId());
            }
            RevisionRow revision = mapper.findRevisionBySourceCommit(asset.assetId(), snapshot.sourceCommitHash());
            if (revision == null) {
                String revisionId = id("hub_rev_");
                mapper.insertRevision(new RevisionRow(
                        revisionId, asset.assetId(), snapshot.sourceVersionId(), snapshot.sourceCommitHash(),
                        artifact.sha256(), pushed.contentSha256(), pushed.displayName(), pushed.displayNameEn(),
                        pushed.description(), false, snapshot.pushedAt(), null, null));
                revision = mapper.findRevisionBySourceCommit(asset.assetId(), snapshot.sourceCommitHash());
            }
            mapper.updateLatestPushed(asset.assetId(), revision.revisionId(), snapshot.pushedAt());
        }

        // 完整扫描中消失的资产以 tombstone 修订表达；历史已发布修订和制品仍不可变保留。
        for (AssetRow asset : mapper.findAssetsBySource(snapshot.sourceAppId(), snapshot.sourceApplicationWorkspaceId())) {
            if (identities.contains(asset.assetType() + ":" + asset.technicalId())) {
                continue;
            }
            String deletedDigest = sha256("deleted:" + snapshot.sourceCommitHash());
            RevisionRow revision = mapper.findRevisionBySourceCommit(asset.assetId(), snapshot.sourceCommitHash());
            if (revision == null) {
                mapper.insertRevision(new RevisionRow(
                        id("hub_rev_"), asset.assetId(), snapshot.sourceVersionId(), snapshot.sourceCommitHash(),
                        null, deletedDigest, null, asset.technicalId(), null, true,
                        snapshot.pushedAt(), null, null));
                revision = mapper.findRevisionBySourceCommit(asset.assetId(), snapshot.sourceCommitHash());
            }
            mapper.updateLatestPushed(asset.assetId(), revision.revisionId(), snapshot.pushedAt());
        }
    }

    @Override
    public Optional<String> findBuiltinSnapshotCommit() {
        return Optional.ofNullable(mapper.findBuiltinState(PUBLIC_SOURCE_KEY));
    }

    /** 公共目录切换与修订写入处于同一事务，多节点重复对账不会产生半成品目录。 */
    @Override
    @Transactional
    public boolean replaceBuiltinSnapshot(String expectedSourceCommitHash, BuiltinSnapshot snapshot) {
        mapper.ensureBuiltinState(PUBLIC_SOURCE_KEY, snapshot.indexedAt());
        String currentCommit = mapper.lockBuiltinState(PUBLIC_SOURCE_KEY);
        if (!java.util.Objects.equals(currentCommit, expectedSourceCommitHash)) {
            return false;
        }
        for (BuiltinPushedRevision pushed : snapshot.revisions()) {
            mapper.insertArtifact(toRow(pushed.artifact()));
            mapper.insertBuiltinRevision(toRow(pushed.revision()));
            if (pushed.revision().assetType() == AssetType.SKILL) {
                mapper.ensureBuiltinClassification(
                        pushed.revision().assetId(), SkillCategory.OTHER.name(), snapshot.indexedAt());
            }
        }
        return mapper.updateBuiltinState(
                PUBLIC_SOURCE_KEY, snapshot.sourceCommitHash(), snapshot.indexedAt()) == 1;
    }

    @Override
    public List<BuiltinRevision> listCurrentBuiltinRevisions() {
        return mapper.listCurrentBuiltinRevisions(PUBLIC_SOURCE_KEY).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<BuiltinRevision> findCurrentBuiltinRevision(String assetId) {
        return Optional.ofNullable(mapper.findCurrentBuiltinRevision(PUBLIC_SOURCE_KEY, assetId))
                .map(this::toDomain);
    }

    @Override
    public Optional<BuiltinRevision> findBuiltinRevision(String revisionId) {
        return Optional.ofNullable(mapper.findBuiltinRevision(revisionId)).map(this::toDomain);
    }

    @Override
    @Transactional
    public void updateBuiltinSkillClassification(
            String assetId, SkillCategory category, SkillSubcategory subcategory,
            String classifiedByUserId, Instant classifiedAt) {
        mapper.ensureBuiltinClassification(assetId, SkillCategory.OTHER.name(), classifiedAt);
        if (mapper.updateBuiltinSkillClassification(assetId, category.name(),
                subcategory == null ? null : subcategory.name(), classifiedByUserId, classifiedAt) != 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "公共 Skill 分类更新失败，请刷新后重试");
        }
    }

    @Override
    public List<AssetSummary> listAssets(
            AssetType type, SkillCategory category, SkillSubcategory subcategory,
            String keyword, String currentUserId,
            String targetApplicationWorkspaceId, boolean referencedOnly,
            int offset, int limit) {
        return mapper.listAssets(type == null ? null : type.name(), keyword,
                        category == null ? null : category.name(),
                        subcategory == null ? null : subcategory.name(), currentUserId,
                        targetApplicationWorkspaceId, referencedOnly, offset, limit).stream()
                .map(this::toSummary).toList();
    }

    @Override
    public long countAssets(
            AssetType type, SkillCategory category, SkillSubcategory subcategory,
            String keyword, String targetApplicationWorkspaceId,
            boolean referencedOnly) {
        return mapper.countAssets(type == null ? null : type.name(), keyword,
                category == null ? null : category.name(),
                subcategory == null ? null : subcategory.name(),
                targetApplicationWorkspaceId, referencedOnly);
    }

    @Override
    @Transactional
    public void updateSkillClassification(
            String assetId, SkillCategory category, SkillSubcategory subcategory,
            String classifiedByUserId, Instant classifiedAt) {
        if (mapper.updateSkillClassification(assetId, category.name(),
                subcategory == null ? null : subcategory.name(), classifiedByUserId, classifiedAt) != 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "Skill 分类更新失败，请刷新后重试");
        }
    }

    @Override
    public Optional<Asset> findAsset(String assetId) {
        return Optional.ofNullable(mapper.findAsset(assetId)).map(this::toDomain);
    }

    @Override
    public Optional<Revision> findRevision(String revisionId) {
        return Optional.ofNullable(mapper.findRevision(revisionId)).map(this::toDomain);
    }

    @Override
    public Optional<Artifact> findArtifact(String sha256) {
        return Optional.ofNullable(mapper.findArtifact(sha256)).map(this::toDomain);
    }

    @Override
    public List<Dependency> findDependencies(String revisionId) {
        return mapper.findDependencies(revisionId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void publish(String assetId, String revisionId, String userId, List<Dependency> dependencies, Instant now) {
        if (mapper.markRevisionPublished(revisionId, userId, now) != 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "Hub 修订已不可发布");
        }
        mapper.deleteDependencies(revisionId);
        dependencies.forEach(dependency -> mapper.insertDependency(toRow(dependency)));
        if (mapper.updateLatestPublished(assetId, revisionId, now) != 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "Hub 最新 push 已变化，请刷新后重试发布");
        }
    }

    @Override
    public Optional<Reference> findReference(String referenceId) {
        return Optional.ofNullable(mapper.findReference(referenceId)).map(this::toDomain);
    }

    @Override
    public Optional<Reference> findReferenceByTargetPath(String targetApplicationWorkspaceId, String targetPath) {
        return Optional.ofNullable(mapper.findReferenceByTargetPath(targetApplicationWorkspaceId, targetPath))
                .map(this::toDomain);
    }

    @Override
    public List<Reference> findReferencesByTargetAsset(String targetApplicationWorkspaceId, String assetId) {
        return mapper.findReferencesByTargetAsset(targetApplicationWorkspaceId, assetId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public List<ReferenceConsumer> listReferenceConsumers(String assetId, String currentUserId) {
        return mapper.listReferenceConsumers(assetId, currentUserId).stream()
                .map(row -> new ReferenceConsumer(toDomain(row), row.targetAppName(), row.targetWorkspaceName()))
                .toList();
    }

    @Override
    public List<Reference> findPendingReferences(String targetApplicationWorkspaceId) {
        return mapper.findPendingReferences(targetApplicationWorkspaceId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public Reference saveReference(Reference reference) {
        upsertReference(reference);
        return findReference(reference.referenceId()).orElseThrow();
    }

    @Override
    @Transactional
    public void deleteReference(String referenceId) {
        mapper.deleteReference(referenceId);
    }

    private void upsertReference(Reference reference) {
        ReferenceRow row = toRow(reference);
        if (mapper.findReference(reference.referenceId()) == null) {
            mapper.insertReference(row);
        } else {
            mapper.updateReference(row);
        }
    }

    @Override
    @Transactional
    public List<Reference> saveReferences(List<Reference> references) {
        for (Reference reference : references) {
            upsertReference(reference);
        }
        return references.stream().map(reference -> findReference(reference.referenceId()).orElseThrow()).toList();
    }

    @Override
    public List<ReferenceUpdate> listUpdates(
            String userId, String targetApplicationWorkspaceId, int offset, int limit) {
        return mapper.listUpdates(userId, targetApplicationWorkspaceId, offset, limit).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public long countUpdates(String userId, String targetApplicationWorkspaceId) {
        return mapper.countUpdates(userId, targetApplicationWorkspaceId);
    }

    @Override
    @Transactional
    public UpdateOperation saveUpdateOperation(UpdateOperation operation) {
        upsertUpdateOperation(operation);
        return findUpdateOperation(operation.operationId()).orElseThrow();
    }

    private void upsertUpdateOperation(UpdateOperation operation) {
        UpdateOperationRow row = toRow(operation);
        if (mapper.findUpdateOperation(operation.operationId()) == null) {
            mapper.insertUpdateOperation(row);
        } else {
            mapper.updateUpdateOperation(row);
        }
    }

    @Override
    @Transactional
    public Reference saveReferenceAndUpdateOperation(Reference reference, UpdateOperation operation) {
        upsertReference(reference);
        upsertUpdateOperation(operation);
        return findReference(reference.referenceId()).orElseThrow();
    }

    @Override
    public Optional<UpdateOperation> findUpdateOperation(String operationId) {
        return Optional.ofNullable(mapper.findUpdateOperation(operationId)).map(this::toDomain);
    }

    private AssetSummary toSummary(AssetSummaryRow row) {
        Asset asset = new Asset(row.assetId(), row.sourceAppId(), row.sourceApplicationWorkspaceId(),
                AssetType.valueOf(row.assetType()), row.technicalId(), SkillCategory.valueOf(row.skillCategory()),
                row.skillSubcategory() == null ? null : SkillSubcategory.valueOf(row.skillSubcategory()),
                row.latestPushedRevisionId(),
                row.latestPublishedRevisionId(), row.assetCreatedAt(), row.assetUpdatedAt());
        Revision pushed = revision(row.pushedRevisionId(), row.assetId(), row.pushedSourceVersionId(),
                row.pushedSourceCommitHash(), row.pushedArtifactSha256(), row.pushedContentSha256(),
                row.pushedDisplayName(), row.pushedDisplayNameEn(), row.pushedDescription(), row.pushedDeleted(),
                row.pushedAt(), row.pushedPublishedAt(), row.pushedPublishedByUserId());
        Revision published = row.publishedRevisionId() == null ? null : revision(
                row.publishedRevisionId(), row.assetId(), row.publishedSourceVersionId(), row.publishedSourceCommitHash(),
                row.publishedArtifactSha256(), row.publishedContentSha256(), row.publishedDisplayName(),
                row.publishedDisplayNameEn(), row.publishedDescription(), Boolean.TRUE.equals(row.publishedDeleted()),
                row.publishedPushedAt(), row.publishedAt(), row.publishedByUserId());
        return new AssetSummary(asset, pushed, published, row.sourceAppName(), row.sourceWorkspaceName(),
                row.updateAvailable(), row.referenceStatus(), row.referenceCount());
    }

    private ReferenceUpdate toDomain(ReferenceUpdateRow row) {
        Reference reference = new Reference(row.referenceId(), row.assetId(), row.targetAppId(),
                row.targetApplicationWorkspaceId(), row.targetPath(), row.aliasTechnicalId(),
                row.activeRevisionId(), row.pendingRevisionId(), row.pendingContentSha256(), row.status(),
                row.createdByUserId(), row.referenceCreatedAt(), row.referenceUpdatedAt());
        Asset asset = new Asset(row.assetId(), row.sourceAppId(), row.sourceApplicationWorkspaceId(),
                AssetType.valueOf(row.assetType()), row.technicalId(), row.latestPushedRevisionId(),
                row.latestPublishedRevisionId(), row.assetCreatedAt(), row.assetUpdatedAt());
        Revision active = row.activeRevisionId() == null ? null : revision(row.activeRevisionId(), row.assetId(),
                row.activeSourceVersionId(), row.activeSourceCommitHash(), row.activeArtifactSha256(),
                row.activeContentSha256(), row.activeDisplayName(), row.activeDisplayNameEn(), row.activeDescription(),
                Boolean.TRUE.equals(row.activeDeleted()), row.activePushedAt(), row.activePublishedAt(),
                row.activePublishedByUserId());
        Revision latest = revision(row.latestPublishedRevisionId(), row.assetId(), row.latestSourceVersionId(),
                row.latestSourceCommitHash(), row.latestArtifactSha256(), row.latestContentSha256(),
                row.latestDisplayName(), row.latestDisplayNameEn(), row.latestDescription(), row.latestDeleted(),
                row.latestPushedAt(), row.latestPublishedAt(), row.latestPublishedByUserId());
        return new ReferenceUpdate(reference, asset, active, latest, row.sourceAppName(), row.sourceWorkspaceName());
    }

    private ArtifactRow toRow(Artifact value) {
        return new ArtifactRow(value.sha256(), value.encoding(), value.content(), value.manifestJson(),
                value.uncompressedSize(), value.compressedSize(), value.fileCount(), value.createdAt());
    }

    private BuiltinRevisionRow toRow(BuiltinRevision value) {
        return new BuiltinRevisionRow(
                value.revisionId(), value.assetId(), value.assetType().name(), value.technicalId(),
                value.sourceCommitHash(), value.artifactSha256(), value.contentSha256(), value.displayName(),
                value.displayNameEn(), value.description(), value.skillCategory().name(),
                value.skillSubcategory() == null ? null : value.skillSubcategory().name(), value.pushedAt());
    }

    private BuiltinRevision toDomain(BuiltinRevisionRow row) {
        return new BuiltinRevision(
                row.revisionId(), row.assetId(), AssetType.valueOf(row.assetType()), row.technicalId(),
                row.sourceCommitHash(), row.artifactSha256(), row.contentSha256(), row.displayName(),
                row.displayNameEn(), row.description(), SkillCategory.valueOf(row.skillCategory()),
                row.skillSubcategory() == null ? null : SkillSubcategory.valueOf(row.skillSubcategory()),
                row.pushedAt());
    }

    private Artifact toDomain(ArtifactRow row) {
        return new Artifact(row.artifactSha256(), row.encoding(), row.content(), row.manifestJson(),
                row.uncompressedSize(), row.compressedSize(), row.fileCount(), row.createdAt());
    }

    private Asset toDomain(AssetRow row) {
        return new Asset(row.assetId(), row.sourceAppId(), row.sourceApplicationWorkspaceId(),
                AssetType.valueOf(row.assetType()), row.technicalId(), SkillCategory.valueOf(row.skillCategory()),
                row.skillSubcategory() == null ? null : SkillSubcategory.valueOf(row.skillSubcategory()),
                row.latestPushedRevisionId(),
                row.latestPublishedRevisionId(), row.createdAt(), row.updatedAt());
    }

    private Revision toDomain(RevisionRow row) {
        return revision(row.revisionId(), row.assetId(), row.sourceVersionId(), row.sourceCommitHash(),
                row.artifactSha256(), row.contentSha256(), row.displayName(), row.displayNameEn(), row.description(),
                row.deleted(), row.pushedAt(), row.publishedAt(), row.publishedByUserId());
    }

    private Revision revision(String revisionId, String assetId, String sourceVersionId, String sourceCommitHash,
                              String artifactSha256, String contentSha256, String displayName, String displayNameEn,
                              String description, boolean deleted, Instant pushedAt, Instant publishedAt,
                              String publishedByUserId) {
        return new Revision(revisionId, assetId, sourceVersionId, sourceCommitHash, artifactSha256,
                contentSha256, displayName, displayNameEn, description, deleted, pushedAt, publishedAt,
                publishedByUserId);
    }

    private DependencyRow toRow(Dependency value) {
        return new DependencyRow(value.revisionId(), value.dependencyAssetId(), value.dependencyRevisionId(), value.createdAt());
    }

    private Dependency toDomain(DependencyRow row) {
        return new Dependency(row.revisionId(), row.dependencyAssetId(), row.dependencyRevisionId(), row.createdAt());
    }

    private ReferenceRow toRow(Reference value) {
        return new ReferenceRow(value.referenceId(), value.assetId(), value.targetAppId(),
                value.targetApplicationWorkspaceId(), value.targetPath(), value.aliasTechnicalId(),
                value.activeRevisionId(), value.pendingRevisionId(), value.pendingContentSha256(), value.status(),
                value.createdByUserId(), value.createdAt(), value.updatedAt());
    }

    private Reference toDomain(ReferenceRow row) {
        return new Reference(row.referenceId(), row.assetId(), row.targetAppId(),
                row.targetApplicationWorkspaceId(), row.targetPath(), row.aliasTechnicalId(),
                row.activeRevisionId(), row.pendingRevisionId(), row.pendingContentSha256(), row.status(),
                row.createdByUserId(), row.createdAt(), row.updatedAt());
    }

    private Reference toDomain(ReferenceConsumerRow row) {
        return new Reference(row.referenceId(), row.assetId(), row.targetAppId(),
                row.targetApplicationWorkspaceId(), row.targetPath(), row.aliasTechnicalId(),
                row.activeRevisionId(), row.pendingRevisionId(), row.pendingContentSha256(), row.status(),
                row.createdByUserId(), row.referenceCreatedAt(), row.referenceUpdatedAt());
    }

    private UpdateOperationRow toRow(UpdateOperation value) {
        return new UpdateOperationRow(value.operationId(), value.referenceId(), value.targetPersonalWorkspaceId(),
                value.fromRevisionId(), value.toRevisionId(), value.status(), value.conflictsJson(),
                value.createdByUserId(), value.createdAt(), value.updatedAt());
    }

    private UpdateOperation toDomain(UpdateOperationRow row) {
        return new UpdateOperation(row.operationId(), row.referenceId(), row.targetPersonalWorkspaceId(),
                row.fromRevisionId(), row.toRevisionId(), row.status(), row.conflictsJson(),
                row.createdByUserId(), row.createdAt(), row.updatedAt());
    }

    private static String id(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
