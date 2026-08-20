package com.enterprise.testagent.persistence.mybatis;

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
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Hub MyBatis mapper；所有 SQL 均维护在 XML。 */
@Mapper
public interface AgentSkillHubMapper {

    int insertArtifact(ArtifactRow row);
    ArtifactRow findArtifact(@Param("sha256") String sha256);
    int ensureBuiltinState(@Param("sourceKey") String sourceKey, @Param("indexedAt") Instant indexedAt);
    String findBuiltinState(@Param("sourceKey") String sourceKey);
    String lockBuiltinState(@Param("sourceKey") String sourceKey);
    int updateBuiltinState(@Param("sourceKey") String sourceKey,
                           @Param("sourceCommitHash") String sourceCommitHash,
                           @Param("indexedAt") Instant indexedAt);
    int insertBuiltinRevision(BuiltinRevisionRow row);
    int ensureBuiltinClassification(@Param("assetId") String assetId,
                                    @Param("skillCategory") String skillCategory,
                                    @Param("classifiedAt") Instant classifiedAt);
    int updateBuiltinSkillClassification(@Param("assetId") String assetId,
                                         @Param("skillCategory") String skillCategory,
                                         @Param("skillSubcategory") String skillSubcategory,
                                         @Param("classifiedByUserId") String classifiedByUserId,
                                         @Param("classifiedAt") Instant classifiedAt);
    List<BuiltinRevisionRow> listCurrentBuiltinRevisions(@Param("sourceKey") String sourceKey);
    BuiltinRevisionRow findCurrentBuiltinRevision(@Param("sourceKey") String sourceKey,
                                                  @Param("assetId") String assetId);
    BuiltinRevisionRow findBuiltinRevision(@Param("revisionId") String revisionId);
    int insertAsset(AssetRow row);
    AssetRow findAsset(@Param("assetId") String assetId);
    AssetRow findAssetByIdentity(@Param("sourceAppId") String sourceAppId,
                                 @Param("sourceWorkspaceId") String sourceWorkspaceId,
                                 @Param("assetType") String assetType,
                                 @Param("technicalId") String technicalId);
    AssetRow findAssetByExternalIdentity(@Param("externalIdentityKey") String externalIdentityKey);
    int upsertExternalAsset(AssetRow row);
    int markExternalAssetsUnavailable(@Param("synchronizedAt") Instant synchronizedAt);
    List<AssetRow> findAssetsBySource(@Param("sourceAppId") String sourceAppId,
                                      @Param("sourceWorkspaceId") String sourceWorkspaceId);
    int insertRevision(RevisionRow row);
    RevisionRow findRevision(@Param("revisionId") String revisionId);
    RevisionRow findRevisionBySourceCommit(@Param("assetId") String assetId,
                                           @Param("sourceCommitHash") String sourceCommitHash);
    RevisionRow findExternalRevision(@Param("assetId") String assetId,
                                     @Param("externalSkillId") long externalSkillId,
                                     @Param("externalVersion") String externalVersion);
    int updateLatestPushed(@Param("assetId") String assetId, @Param("revisionId") String revisionId,
                           @Param("pushedAt") Instant pushedAt);
    int updateExternalRevisionPointers(@Param("assetId") String assetId,
                                       @Param("revisionId") String revisionId,
                                       @Param("externalSkillId") long externalSkillId,
                                       @Param("externalVersion") String externalVersion,
                                       @Param("updatedAt") Instant updatedAt);
    int setForkLineage(@Param("assetId") String assetId,
                       @Param("forkedFromAssetId") String forkedFromAssetId,
                       @Param("forkedFromRevisionId") String forkedFromRevisionId,
                       @Param("updatedAt") Instant updatedAt);
    List<AssetSummaryRow> listAssets(@Param("assetType") String assetType, @Param("keyword") String keyword,
                                     @Param("skillCategory") String skillCategory,
                                     @Param("skillSubcategory") String skillSubcategory,
                                     @Param("sourceKind") String sourceKind,
                                     @Param("currentUserId") String currentUserId,
                                     @Param("targetWorkspaceId") String targetWorkspaceId,
                                     @Param("referencedOnly") boolean referencedOnly,
                                     @Param("offset") int offset, @Param("limit") int limit);
    long countAssets(@Param("assetType") String assetType, @Param("keyword") String keyword,
                     @Param("skillCategory") String skillCategory,
                     @Param("skillSubcategory") String skillSubcategory,
                     @Param("sourceKind") String sourceKind,
                     @Param("targetWorkspaceId") String targetWorkspaceId,
                     @Param("referencedOnly") boolean referencedOnly);
    int updateSkillClassification(@Param("assetId") String assetId,
                                  @Param("skillCategory") String skillCategory,
                                  @Param("skillSubcategory") String skillSubcategory,
                                  @Param("classifiedByUserId") String classifiedByUserId,
                                  @Param("classifiedAt") Instant classifiedAt);
    int markRevisionPublished(@Param("revisionId") String revisionId, @Param("userId") String userId,
                              @Param("now") Instant now);
    int updateLatestPublished(@Param("assetId") String assetId, @Param("revisionId") String revisionId,
                              @Param("now") Instant now);
    int deleteDependencies(@Param("revisionId") String revisionId);
    int insertDependency(DependencyRow row);
    List<DependencyRow> findDependencies(@Param("revisionId") String revisionId);
    ReferenceRow findReference(@Param("referenceId") String referenceId);
    ReferenceRow findReferenceByTargetPath(@Param("targetWorkspaceId") String targetWorkspaceId,
                                           @Param("targetPath") String targetPath);
    List<ReferenceRow> findReferencesByTargetAsset(@Param("targetWorkspaceId") String targetWorkspaceId,
                                                    @Param("assetId") String assetId);
    List<ReferenceConsumerRow> listReferenceConsumers(@Param("assetId") String assetId,
                                                       @Param("currentUserId") String currentUserId);
    List<ReferenceRow> findPendingReferences(@Param("targetWorkspaceId") String targetWorkspaceId);
    int insertReference(ReferenceRow row);
    int updateReference(ReferenceRow row);
    int activateReference(@Param("referenceId") String referenceId,
                          @Param("assetId") String assetId,
                          @Param("activeRevisionId") String activeRevisionId,
                          @Param("updatedAt") Instant updatedAt);
    int deleteReference(@Param("referenceId") String referenceId);
    List<ReferenceUpdateRow> listUpdates(@Param("userId") String userId,
                                         @Param("targetWorkspaceId") String targetWorkspaceId,
                                         @Param("offset") int offset, @Param("limit") int limit);
    long countUpdates(@Param("userId") String userId, @Param("targetWorkspaceId") String targetWorkspaceId);
    int insertUpdateOperation(UpdateOperationRow row);
    int updateUpdateOperation(UpdateOperationRow row);
    UpdateOperationRow findUpdateOperation(@Param("operationId") String operationId);
}
