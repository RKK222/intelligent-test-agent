package com.icbc.testagent.domain.configuration;

import java.time.Instant;
import java.util.Objects;

/**
 * 代码库配置。Git URL 创建后不可编辑，只允许改中文名称、英文名称和兼容的标准仓库标记。
 */
public record CodeRepository(
        CodeRepositoryId repositoryId,
        String gitUrl,
        String name,
        String englishName,
        String repositoryType,
        boolean standard,
        Instant createdAt,
        Instant updatedAt) {

    public CodeRepository(
            CodeRepositoryId repositoryId,
            String gitUrl,
            String name,
            String englishName,
            boolean standard,
            Instant createdAt,
            Instant updatedAt) {
        this(
                repositoryId,
                gitUrl,
                name,
                englishName,
                CodeRepositoryType.fromStandard(standard).value(),
                standard,
                createdAt,
                updatedAt);
    }

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
        englishName = normalizeOptional(englishName);
        repositoryType = normalizeRepositoryType(repositoryType, standard);
        // 旧版 standard 字段只服务兼容逻辑，统一由 repositoryType 派生，避免两列语义分叉。
        standard = CodeRepositoryType.fromValue(repositoryType).standard();
    }

    /**
     * 编辑可变元数据，保留不可变 gitUrl。
     */
    public CodeRepository editMetadata(String name, String englishName, boolean standard, Instant now) {
        return editMetadata(name, englishName, CodeRepositoryType.fromStandard(standard).value(), now);
    }

    /**
     * 编辑可变元数据，保留不可变 gitUrl，并按类型派生旧版 standard 字段。
     */
    public CodeRepository editMetadata(String name, String englishName, String repositoryType, Instant now) {
        return new CodeRepository(repositoryId, gitUrl, name, englishName, repositoryType, standard, createdAt, now);
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeRepositoryType(String repositoryType, boolean standard) {
        if (repositoryType == null || repositoryType.isBlank()) {
            return CodeRepositoryType.fromStandard(standard).value();
        }
        return CodeRepositoryType.fromValue(repositoryType.trim()).value();
    }
}
