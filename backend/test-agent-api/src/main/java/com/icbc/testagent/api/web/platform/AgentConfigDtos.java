package com.icbc.testagent.api.web.platform;

import java.time.Instant;
import java.util.List;

/**
 * Agent 配置 HTTP/WS DTO，Controller 只做边界转换，不承载业务规则。
 */
final class AgentConfigDtos {

    private AgentConfigDtos() {
    }

    record BranchRequest(String branch, String operationId, Boolean discardLocalChanges) {
    }

    /**
     * 公共配置"更新+提交并推送"复合请求：先 fetch 远端，再 stage/commit 本地修改、merge 远端分支并 push。
     * discardLocalChanges 为 true 时才允许覆盖受控仓库中的已跟踪修改。
     */
    record UpdatePublicConfigAndPushRequest(
            String branch,
            String commitMessage,
            String operationId,
            Boolean discardLocalChanges) {
    }

    record WorktreeRequest(String baseName, String branch, String operationId, String linuxServerId) {
    }

    record FileContentRequest(String path, String content, String worktreeId) {
    }

    record FileRouteRequest(String scope, String workspaceId, String worktreeId, String linuxServerId) {
    }

    record FileRouteResponse(
            String scope,
            String workspaceId,
            String worktreeId,
            String linuxServerId,
            String baseUrl,
            String webSocketPath,
            boolean sameServer,
            String message) {
    }

    record StageRequest(List<String> files, String worktreeId) {
    }

    record GitConflictRequest(String path, String resolution, String content, String worktreeId, String linuxServerId) {
    }

    record ResolveAllGitConflictsRequest(String resolution, String worktreeId, String linuxServerId) {
    }

    record CommitRequest(String message, String worktreeId, String operationId) {
    }

    record PublishRequest(String worktreeId, String operationId) {
    }

    record TicketResponse(String ticket, Instant expiresAt, String webSocketUrl) {
    }
}
