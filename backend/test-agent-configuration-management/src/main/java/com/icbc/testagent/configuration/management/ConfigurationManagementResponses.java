package com.icbc.testagent.configuration.management;

import java.time.Instant;

/**
 * 配置管理对 API 层暴露的安全响应模型；SSH key 只包含元信息，不包含明文或密文。
 */
public final class ConfigurationManagementResponses {

    private ConfigurationManagementResponses() {
    }

    public record ApplicationResponse(String appId, String appName, boolean enabled) {
    }

    public record UserResponse(
            String userId,
            String username,
            String unifiedAuthId,
            String organization,
            String rdDepartment,
            String department) {
    }

    public record ApplicationMemberResponse(
            String userId,
            String username,
            String unifiedAuthId,
            String organization,
            String rdDepartment,
            String department) {
    }

    public record CodeRepositoryResponse(
            String repositoryId,
            String gitUrl,
            String name,
            String englishName,
            boolean standard,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ApplicationWorkspaceResponse(
            String workspaceId,
            String appId,
            String repositoryId,
            String branch,
            String directoryPath,
            String workspaceName,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record SshKeyResponse(String sshKeyId, String name, String fingerprint, Instant createdAt) {
    }
}
