package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.persistence.mybatis.AgentSkillHubRows.ArtifactRow;
import com.enterprise.testagent.persistence.mybatis.AgentSkillHubRows.AssetRow;
import com.enterprise.testagent.persistence.mybatis.AgentSkillHubRows.AssetSummaryRow;
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
    int insertAsset(AssetRow row);
    AssetRow findAsset(@Param("assetId") String assetId);
    AssetRow findAssetByIdentity(@Param("sourceAppId") String sourceAppId,
                                 @Param("sourceWorkspaceId") String sourceWorkspaceId,
                                 @Param("assetType") String assetType,
                                 @Param("technicalId") String technicalId);
    List<AssetRow> findAssetsBySource(@Param("sourceAppId") String sourceAppId,
                                      @Param("sourceWorkspaceId") String sourceWorkspaceId);
    int insertRevision(RevisionRow row);
    RevisionRow findRevision(@Param("revisionId") String revisionId);
    RevisionRow findRevisionBySourceCommit(@Param("assetId") String assetId,
                                           @Param("sourceCommitHash") String sourceCommitHash);
    int updateLatestPushed(@Param("assetId") String assetId, @Param("revisionId") String revisionId,
                           @Param("pushedAt") Instant pushedAt);
    List<AssetSummaryRow> listAssets(@Param("assetType") String assetType, @Param("keyword") String keyword,
                                     @Param("skillCategory") String skillCategory,
                                     @Param("skillSubcategory") String skillSubcategory,
                                     @Param("currentUserId") String currentUserId,
                                     @Param("targetWorkspaceId") String targetWorkspaceId,
                                     @Param("referencedOnly") boolean referencedOnly,
                                     @Param("offset") int offset, @Param("limit") int limit);
    long countAssets(@Param("assetType") String assetType, @Param("keyword") String keyword,
                     @Param("skillCategory") String skillCategory,
                     @Param("skillSubcategory") String skillSubcategory,
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
    int deleteReference(@Param("referenceId") String referenceId);
    List<ReferenceUpdateRow> listUpdates(@Param("userId") String userId,
                                         @Param("targetWorkspaceId") String targetWorkspaceId,
                                         @Param("offset") int offset, @Param("limit") int limit);
    long countUpdates(@Param("userId") String userId, @Param("targetWorkspaceId") String targetWorkspaceId);
    int insertUpdateOperation(UpdateOperationRow row);
    int updateUpdateOperation(UpdateOperationRow row);
    UpdateOperationRow findUpdateOperation(@Param("operationId") String operationId);
}
