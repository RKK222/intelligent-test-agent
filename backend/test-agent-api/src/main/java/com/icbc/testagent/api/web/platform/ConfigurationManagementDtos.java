package com.icbc.testagent.api.web.platform;

/**
 * 配置管理 HTTP 请求 DTO，Controller 只负责入口参数承载和边界转换。
 */
final class ConfigurationManagementDtos {

    private ConfigurationManagementDtos() {
    }

    record AddMemberRequest(String userId) {
    }

    record CreateRepositoryRequest(String gitUrl, String name, String englishName, Boolean standard, String repositoryType, String deploymentMode) {
    }

    record UpdateRepositoryRequest(String name, String englishName, Boolean standard) {
    }

    record LinkRepositoryRequest(String repositoryId) {
    }

    record LinkApplicationRequest(String appId) {
    }

    record CreateApplicationWorkspaceRequest(
            String repositoryId,
            String branch,
            String directoryPath,
            String workspaceName,
            Boolean directoryNew,
            String version,
            String operationId) {
    }

    record RenameApplicationWorkspaceRequest(String workspaceName) {
    }

    /**
     * 新增 SSH key 请求：前端已完成混合加密（AES 加密私钥 + RSA 加密 AES 密钥）。
     *
     * @param name                 SSH key 名称
     * @param encryptedPrivateKey  AES-256-GCM 加密后的 SSH 私钥（Base64）
     * @param encryptedAesKey      RSA-OAEP 加密后的临时 AES 密钥（Base64）
     * @param encryptionNonce      AES-GCM nonce（Base64）
     * @param fingerprint          SSH 私钥明文的 SHA-256 指纹（SHA256:base64）
     */
    record AddSshKeyRequest(
            String name,
            String encryptedPrivateKey,
            String encryptedAesKey,
            String encryptionNonce,
            String fingerprint) {
    }
}
