package com.enterprise.testagent.domain.appsource;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** 用户最近打开的源码快照选择；物理副本在打开时按当前进程服务器解析。 */
public record AppSourceRecentSelection(
        UserId userId,
        ApplicationId appId,
        CodeRepositoryId repositoryId,
        long generation,
        Instant updatedAt) {

    public AppSourceRecentSelection {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(appId, "appId must not be null");
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (generation < 1L) {
            throw new IllegalArgumentException("generation must be positive");
        }
    }
}
