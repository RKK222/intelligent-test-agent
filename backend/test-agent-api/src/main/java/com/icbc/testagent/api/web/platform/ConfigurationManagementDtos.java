package com.icbc.testagent.api.web.platform;

/**
 * 配置管理 HTTP 请求 DTO，Controller 只负责入口参数承载和边界转换。
 */
final class ConfigurationManagementDtos {

    private ConfigurationManagementDtos() {
    }

    record AddMemberRequest(String userId) {
    }

    record CreateRepositoryRequest(String gitUrl, String name, Boolean standard) {
    }

    record UpdateRepositoryRequest(String name, Boolean standard) {
    }

    record LinkRepositoryRequest(String repositoryId) {
    }

    record LinkApplicationRequest(String appId) {
    }

    record CreateApplicationWorkspaceRequest(
            String repositoryId,
            String branch,
            String directoryPath,
            String workspaceName) {
    }

    record RenameApplicationWorkspaceRequest(String workspaceName) {
    }

    record AddSshKeyRequest(String name, String privateKey) {
    }
}
