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
        String deploymentMode,
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
                CodeRepositoryDeploymentMode.EXTERNAL.value(),
                standard,
                createdAt,
                updatedAt);
    }

    public CodeRepository(
            CodeRepositoryId repositoryId,
            String gitUrl,
            String name,
            String englishName,
            String repositoryType,
            boolean standard,
            Instant createdAt,
            Instant updatedAt) {
        this(
                repositoryId,
                gitUrl,
                name,
                englishName,
                repositoryType,
                CodeRepositoryDeploymentMode.EXTERNAL.value(),
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
        deploymentMode = CodeRepositoryDeploymentMode.fromValue(deploymentMode).value();
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
        return new CodeRepository(repositoryId, gitUrl, name, englishName, repositoryType, deploymentMode, standard, createdAt, now);
    }

    /**
     * 判断当前版本库是否按内部 SCM 模式保存。
     */
    public boolean internalDeployment() {
        return CodeRepositoryDeploymentMode.INTERNAL.value().equals(deploymentMode);
    }

    /**
     * 根据当前操作人的统一认证号生成实际 Git URL。外部模式保持数据库保存值不变。
     */
    public String effectiveGitUrl(String unifiedAuthId) {
        if (!internalDeployment()) {
            return gitUrl;
        }
        if (unifiedAuthId == null || unifiedAuthId.isBlank()) {
            throw new IllegalArgumentException("unifiedAuthId must not be blank for internal repository");
        }
        return "ssh://" + unifiedAuthId.trim() + "@" + gitUrl;
    }

    /**
     * 内部模式比较 origin 时忽略 ssh://任意用户@ 前缀，仍严格比较数据库保存的 SCM 地址片段。
     */
    public boolean matchesStoredOrigin(String originUrl) {
        if (originUrl == null) {
            return false;
        }
        String value = originUrl.trim();
        if (internalDeployment()) {
            return gitUrl.equals(stripInternalSshUser(value));
        }
        return gitUrl.equals(value);
    }

    private static String stripInternalSshUser(String value) {
        String prefix = "ssh://";
        if (!value.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return value;
        }
        String rest = value.substring(prefix.length());
        int at = rest.indexOf('@');
        return at > 0 ? rest.substring(at + 1) : value;
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
