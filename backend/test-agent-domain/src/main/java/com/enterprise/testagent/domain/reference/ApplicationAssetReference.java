package com.enterprise.testagent.domain.reference;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import java.time.Instant;
import java.util.Objects;

/** 应用共享资产目录引用；路径始终是资产库内的相对目录，不保存服务器物理路径。 */
public record ApplicationAssetReference(
        ApplicationId appId,
        CodeRepositoryId repositoryId,
        String directoryPath,
        String alias,
        boolean merge,
        String sddFolderName,
        String description,
        long version,
        Instant updatedAt) {

    public ApplicationAssetReference {
        Objects.requireNonNull(appId);
        Objects.requireNonNull(repositoryId);
        Objects.requireNonNull(directoryPath);
        Objects.requireNonNull(alias);
        Objects.requireNonNull(sddFolderName);
        Objects.requireNonNull(description);
        Objects.requireNonNull(updatedAt);
    }
}
