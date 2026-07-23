package com.enterprise.testagent.configuration.management;

import java.time.Instant;
import java.util.List;

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
            String deploymentMode,
            String repositoryType,
            String repositoryTypeLabel,
            boolean standard,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record RepositoryTypeOptionResponse(String typeCode, String typeLabel) {
    }

    public record RepositoryDeploymentOptionResponse(String mode, String label) {
    }

    public record RepositoryDeploymentOptionsResponse(
            String defaultDeploymentMode,
            String internalSshPrefix,
            java.util.List<RepositoryDeploymentOptionResponse> options) {
    }

    public record ApplicationWorkspaceResponse(
            String workspaceId,
            String appId,
            String repositoryId,
            String branch,
            String directoryPath,
            String workspaceName,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record RepositoryTreeNodeResponse(
            String name,
            String path,
            String type,
            List<RepositoryTreeNodeResponse> children) {
        public RepositoryTreeNodeResponse {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    public record RepositoryTreeResponse(List<RepositoryTreeNodeResponse> nodes) {
        public RepositoryTreeResponse {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    public record SshKeyResponse(String sshKeyId, String name, String fingerprint, Instant createdAt) {
    }
}
