package com.icbc.testagent.api.web.platform;

import java.time.Instant;
import java.util.List;

/**
 * Agent 配置 HTTP/WS DTO，Controller 只做边界转换，不承载业务规则。
 */
final class AgentConfigDtos {

    private AgentConfigDtos() {
    }

    record BranchRequest(String branch, String operationId) {
    }

    record WorktreeRequest(String baseName, String branch, String operationId) {
    }

    record FileContentRequest(String path, String content, String worktreeId) {
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
