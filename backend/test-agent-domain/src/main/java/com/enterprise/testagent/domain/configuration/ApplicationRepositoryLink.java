package com.enterprise.testagent.domain.configuration;

import java.time.Instant;
import java.util.Objects;

/**
 * 应用与代码库的多对多关联。
 */
public record ApplicationRepositoryLink(
        ApplicationId appId,
        CodeRepositoryId repositoryId,
        Instant createdAt) {

    public ApplicationRepositoryLink {
        Objects.requireNonNull(appId, "appId must not be null");
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
