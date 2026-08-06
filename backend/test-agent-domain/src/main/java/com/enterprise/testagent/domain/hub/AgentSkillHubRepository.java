package com.enterprise.testagent.domain.hub;

import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Artifact;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Asset;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetSummary;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetType;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Dependency;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushedSnapshot;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Reference;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ReferenceConsumer;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ReferenceUpdate;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Revision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillCategory;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillSubcategory;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.UpdateOperation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Hub 持久化端口；关系型 SQL 由 persistence 的 MyBatis XML 实现。 */
public interface AgentSkillHubRepository {

    void replacePushedSnapshot(PushedSnapshot snapshot);

    List<AssetSummary> listAssets(AssetType type, SkillCategory category, SkillSubcategory subcategory,
                                  String keyword, String currentUserId,
                                  String targetApplicationWorkspaceId, boolean referencedOnly,
                                  int offset, int limit);

    /** 兼容不需要事项筛选的既有领域调用。 */
    default List<AssetSummary> listAssets(AssetType type, String keyword, String currentUserId,
                                          String targetApplicationWorkspaceId, boolean referencedOnly,
                                          int offset, int limit) {
        return listAssets(type, null, null, keyword, currentUserId,
                targetApplicationWorkspaceId, referencedOnly, offset, limit);
    }

    long countAssets(AssetType type, SkillCategory category, SkillSubcategory subcategory,
                     String keyword, String targetApplicationWorkspaceId,
                     boolean referencedOnly);

    /** 兼容不需要事项筛选的既有领域调用。 */
    default long countAssets(AssetType type, String keyword, String targetApplicationWorkspaceId,
                             boolean referencedOnly) {
        return countAssets(type, null, null, keyword, targetApplicationWorkspaceId, referencedOnly);
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
