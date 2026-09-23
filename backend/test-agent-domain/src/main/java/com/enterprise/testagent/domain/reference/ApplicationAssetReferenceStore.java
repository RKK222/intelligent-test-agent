package com.enterprise.testagent.domain.reference;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import java.time.Instant;
import java.util.List;

/** 应用资产共享配置及旧个人配置迁移的持久化端口。 */
public interface ApplicationAssetReferenceStore {

    List<ApplicationAssetReference> list(ApplicationId appId);

    int insert(ApplicationAssetReference reference);

    int update(ApplicationAssetReference reference, long expectedVersion);

    int delete(ApplicationId appId, CodeRepositoryId repositoryId, String directoryPath, long expectedVersion);

    List<ImportSource> claimLocalSources(String linuxServerId, Instant now, Instant leaseUntil, int limit);

    void finishSource(ImportSource source, List<ImportCandidate> candidates, Instant now);

    void retrySource(ImportSource source, String safeError, Instant retryAt);

    List<ApplicationId> readyImportApplications();

    boolean claimImportApplication(ApplicationId appId, Instant now);

    List<ImportCandidate> candidates(ApplicationId appId);

    void recordConflict(ApplicationId appId, String alias, String reason, Instant now);

    void clearConflict(ApplicationId appId, String alias);

    List<ImportConflict> conflicts(ApplicationId appId);

    boolean importPending(ApplicationId appId);

    /** 迁移任务固定于升级时已有的管理员个人工作区。 */
    record ImportSource(String workspaceId, ApplicationId appId, String linuxServerId) {
    }

    /** 从已校验的旧 JSONC 提取的候选；不含正文、凭据或物理路径。 */
    record ImportCandidate(String workspaceId, ApplicationId appId, CodeRepositoryId repositoryId,
                           String directoryPath, String alias, boolean merge, String sddFolderName,
                           String description) {
    }

    record ImportConflict(String alias, String reason) {
    }
}
