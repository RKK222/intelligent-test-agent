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
     * 公共配置"更新+提交并推送"复合请求：先按分支 fetch/reset/pull，再 stage 已跟踪/未跟踪修改，
     * 然后用 commitMessage 生成一次提交并 push 到远端。discardLocalChanges 为 true 时才允许覆盖受控仓库中的已跟踪修改。
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

    record CommitRequest(String message, String worktreeId, String operationId) {
    }

    record PublishRequest(String worktreeId, String operationId) {
    }

    record TicketResponse(String ticket, Instant expiresAt, String webSocketUrl) {
    }
}
