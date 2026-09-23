package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 应用资产共享配置 MyBatis 接口；SQL 全部位于同名 XML。 */
@Mapper
public interface ApplicationAssetReferenceMapper {
    List<ConfigurationRow> list(@Param("appId") String appId);
    int insert(@Param("row") ConfigurationRow row);
    int update(@Param("row") ConfigurationRow row, @Param("expectedVersion") long expectedVersion);
    int delete(@Param("appId") String appId, @Param("repositoryId") String repositoryId,
               @Param("directoryPath") String directoryPath, @Param("expectedVersion") long expectedVersion);
    List<ImportSourceRow> claimLocalSources(@Param("linuxServerId") String linuxServerId,
                                            @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil,
                                            @Param("limit") int limit);
    void deleteCandidates(@Param("workspaceId") String workspaceId);
    void insertCandidate(@Param("row") ImportCandidateRow row);
    void finishSource(@Param("workspaceId") String workspaceId, @Param("now") Instant now);
    void retrySource(@Param("workspaceId") String workspaceId, @Param("safeError") String safeError,
                     @Param("retryAt") Instant retryAt);
    List<String> readyImportApplications();
    int claimImportApplication(@Param("appId") String appId, @Param("now") Instant now);
    List<ImportCandidateRow> candidates(@Param("appId") String appId);
    void recordConflict(@Param("appId") String appId, @Param("alias") String alias,
                        @Param("reason") String reason, @Param("now") Instant now);
    void clearConflict(@Param("appId") String appId, @Param("alias") String alias);
    List<ImportConflictRow> conflicts(@Param("appId") String appId);
    int countPendingImport(@Param("appId") String appId);

    record ConfigurationRow(String appId, String repositoryId, String directoryPath, String alias,
                            boolean mergeEnabled, String sddFolderName, String description,
                            long version, Instant updatedAt) { }
    record ImportSourceRow(String workspaceId, String appId, String linuxServerId) { }
    record ImportCandidateRow(String workspaceId, String appId, String repositoryId,
                              String directoryPath, String alias, boolean mergeEnabled,
                              String sddFolderName, String description) { }
    record ImportConflictRow(String alias, String reason) { }
}
