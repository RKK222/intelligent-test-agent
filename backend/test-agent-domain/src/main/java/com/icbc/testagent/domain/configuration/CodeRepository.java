package com.icbc.testagent.domain.configuration;

import java.time.Instant;
import java.util.Objects;

/**
 * 代码库配置。Git URL 创建后不可编辑，只允许改中文名称和标准仓库标记。
 */
public record CodeRepository(
        CodeRepositoryId repositoryId,
        String gitUrl,
        String name,
        boolean standard,
        Instant createdAt,
        Instant updatedAt) {

    public CodeRepository {
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(gitUrl, "gitUrl must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (gitUrl.isBlank()) {
            throw new IllegalArgumentException("gitUrl must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /**
     * 编辑可变元数据，保留不可变 gitUrl。
     */
    public CodeRepository editMetadata(String name, boolean standard, Instant now) {
        return new CodeRepository(repositoryId, gitUrl, name, standard, createdAt, now);
    }
}
