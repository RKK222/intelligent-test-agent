package com.icbc.testagent.api.web.platform;

import java.util.List;

/**
 * 应用版本工作区 HTTP 请求 DTO，入口层只承载参数，不包含业务规则。
 */
final class ManagedWorkspaceDtos {

    private ManagedWorkspaceDtos() {
    }

    record CreateVersionRequest(String version, String branch) {
    }

    record CreatePersonalWorkspaceRequest(String workspaceName) {
    }

    record SyncWorkspaceRequest(List<String> files, Boolean force) {
    }
}
