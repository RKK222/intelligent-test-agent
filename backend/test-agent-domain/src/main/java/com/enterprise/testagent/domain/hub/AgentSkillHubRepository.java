package com.enterprise.testagent.domain.hub;

import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Artifact;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Asset;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetSummary;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetType;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.BuiltinRevision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.BuiltinSnapshot;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Dependency;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ExternalSkill;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushedSnapshot;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushReferenceDecision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Reference;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ReferenceConsumer;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ReferenceUpdate;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Revision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillCategory;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillSubcategory;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SourceKind;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.UpdateOperation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Hub 持久化端口；关系型 SQL 由 persistence 的 MyBatis XML 实现。 */
public interface AgentSkillHubRepository {

    void replacePushedSnapshot(PushedSnapshot snapshot);

    /** 快照与引用来源身份切换原子提交，避免同一份 Skill 同时出现外部卡片和平台卡片。 */
    void replacePushedSnapshot(PushedSnapshot snapshot, List<PushReferenceDecision> referenceDecisions);

    /** 原子替换外部目录；本轮消失的条目只标记来源不可用，不删除历史修订和引用。 */
    void replaceExternalCatalog(List<ExternalSkill> skills, Instant synchronizedAt);

    Optional<Revision> findExternalRevision(String assetId, long externalSkillId, String externalVersion);

    /** 保存通过安全校验的外部正文，并把该修订设为外部资产当前可引用修订。 */
    Revision saveExternalRevision(String assetId, long externalSkillId, String externalVersion,
                                  Artifact artifact, String contentSha256,
                                  String displayName, String description, Instant materializedAt);

    /** 返回数据库已完成对账的公共配置提交；空表示尚未建立首个快照。 */
    Optional<String> findBuiltinSnapshotCommit();

    /**
     * 以 compare-and-set 方式切换公共快照，避免多节点用旧本地 HEAD 覆盖较新的数据库目录。
     *
     * @return 当前提交仍等于 expectedSourceCommitHash 且切换成功时返回 true
     */
    boolean replaceBuiltinSnapshot(String expectedSourceCommitHash, BuiltinSnapshot snapshot);

    List<BuiltinRevision> listCurrentBuiltinRevisions();

    Optional<BuiltinRevision> findCurrentBuiltinRevision(String assetId);

    Optional<BuiltinRevision> findBuiltinRevision(String revisionId);

    void updateBuiltinSkillClassification(String assetId, SkillCategory category, SkillSubcategory subcategory,
                                          String classifiedByUserId, Instant classifiedAt);

    List<AssetSummary> listAssets(AssetType type, SkillCategory category, SkillSubcategory subcategory,
                                  SourceKind sourceKind,
                                  String keyword, String currentUserId,
                                  String targetApplicationWorkspaceId, boolean referencedOnly,
                                  int offset, int limit);

    /** 兼容不需要事项筛选的既有领域调用。 */
    default List<AssetSummary> listAssets(AssetType type, String keyword, String currentUserId,
                                          String targetApplicationWorkspaceId, boolean referencedOnly,
                                          int offset, int limit) {
        return listAssets(type, null, null, SourceKind.PLATFORM, keyword, currentUserId,
                targetApplicationWorkspaceId, referencedOnly, offset, limit);
    }

    /** 兼容事项分类已上线后的平台来源调用。 */
    default List<AssetSummary> listAssets(AssetType type, SkillCategory category, SkillSubcategory subcategory,
                                          String keyword, String currentUserId,
                                          String targetApplicationWorkspaceId, boolean referencedOnly,
                                          int offset, int limit) {
        return listAssets(type, category, subcategory, SourceKind.PLATFORM, keyword, currentUserId,
                targetApplicationWorkspaceId, referencedOnly, offset, limit);
    }

    long countAssets(AssetType type, SkillCategory category, SkillSubcategory subcategory,
                     SourceKind sourceKind,
                     String keyword, String targetApplicationWorkspaceId,
                     boolean referencedOnly);

    /** 兼容不需要事项筛选的既有领域调用。 */
    default long countAssets(AssetType type, String keyword, String targetApplicationWorkspaceId,
                             boolean referencedOnly) {
        return countAssets(type, null, null, SourceKind.PLATFORM, keyword,
                targetApplicationWorkspaceId, referencedOnly);
    }

    /** 兼容事项分类已上线后的平台来源调用。 */
    default long countAssets(AssetType type, SkillCategory category, SkillSubcategory subcategory,
                             String keyword, String targetApplicationWorkspaceId,
                             boolean referencedOnly) {
        return countAssets(type, category, subcategory, SourceKind.PLATFORM, keyword,
                targetApplicationWorkspaceId, referencedOnly);
    }

    void updateSkillClassification(String assetId, SkillCategory category, SkillSubcategory subcategory,
                                   String classifiedByUserId, Instant classifiedAt);

    Optional<Asset> findAsset(String assetId);

    Optional<Revision> findRevision(String revisionId);

    Optional<Artifact> findArtifact(String sha256);

    List<Dependency> findDependencies(String revisionId);

    void publish(String assetId, String revisionId, String userId, List<Dependency> dependencies, Instant now);

    Optional<Reference> findReference(String referenceId);

    Optional<Reference> findReferenceByTargetPath(String targetApplicationWorkspaceId, String targetPath);

    List<Reference> findReferencesByTargetAsset(String targetApplicationWorkspaceId, String assetId);

    List<ReferenceConsumer> listReferenceConsumers(String assetId, String currentUserId);

    List<Reference> findPendingReferences(String targetApplicationWorkspaceId);

    Reference saveReference(Reference reference);

    void deleteReference(String referenceId);

    List<Reference> saveReferences(List<Reference> references);

    List<ReferenceUpdate> listUpdates(String userId, String targetApplicationWorkspaceId, int offset, int limit);

    long countUpdates(String userId, String targetApplicationWorkspaceId);

    UpdateOperation saveUpdateOperation(UpdateOperation operation);

    Reference saveReferenceAndUpdateOperation(Reference reference, UpdateOperation operation);

    Optional<UpdateOperation> findUpdateOperation(String operationId);
}
