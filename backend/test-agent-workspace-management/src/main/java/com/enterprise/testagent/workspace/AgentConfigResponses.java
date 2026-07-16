package com.enterprise.testagent.workspace;

import com.enterprise.testagent.domain.configuration.AgentConfigOperation;
import com.enterprise.testagent.domain.configuration.AgentConfigWorktree;
import java.time.Instant;
import java.util.List;

/**
 * Agent 配置应用服务返回对象，避免 Controller 泄露领域对象和本地 Path 类型。
 */
public final class AgentConfigResponses {

    private AgentConfigResponses() {
    }

    public record AgentConfigStatusResponse(
            String scope,
            boolean enabled,
            boolean writable,
            String gitUrl,
            String gitRootPath,
            String agentDirectory,
            String currentBranch,
            String commitHash) {
    }

    public record PublicRepositoryStatusResponse(
            String linuxServerId,
            String serverName,
            String gitRootPath,
            String configDirPath,
            String worktreeRootPath,
            String status,
            boolean initialized,
            boolean initializationAllowed,
            String currentBranch,
            String commitHash,
            String message) {
    }

    public record AgentConfigWorktreeResponse(
            String worktreeId,
            String scope,
            String workspaceId,
            String linuxServerId,
            String worktreeName,
            String branch,
            String rootPath,
            String agentDirectory,
            String status,
            Instant createdAt,
            Instant updatedAt) {

        public static AgentConfigWorktreeResponse from(AgentConfigWorktree worktree, String agentDirectory) {
            return new AgentConfigWorktreeResponse(
                    worktree.worktreeId(),
                    worktree.scope().name(),
                    worktree.workspaceId() == null ? null : worktree.workspaceId().value(),
                    worktree.linuxServerId(),
                    worktree.worktreeName(),
                    worktree.branch(),
                    worktree.rootPath(),
                    agentDirectory,
                    worktree.status().name(),
                    worktree.createdAt(),
                    worktree.updatedAt());
        }
    }

    public record AgentConfigWorktreeOptionResponse(
            String worktreeId,
            String scope,
            String workspaceId,
            String linuxServerId,
            String worktreeName,
            String branch,
            String rootPath,
            String agentDirectory,
            String status,
            Instant createdAt,
            Instant updatedAt,
            String createdByUserId,
            String createdByUsername) {

        public static AgentConfigWorktreeOptionResponse from(
                AgentConfigWorktree worktree,
                String agentDirectory,
                String createdByUsername) {
            return new AgentConfigWorktreeOptionResponse(
                    worktree.worktreeId(),
                    worktree.scope().name(),
                    worktree.workspaceId() == null ? null : worktree.workspaceId().value(),
                    worktree.linuxServerId(),
                    worktree.worktreeName(),
                    worktree.branch(),
                    worktree.rootPath(),
                    agentDirectory,
                    worktree.status().name(),
                    worktree.createdAt(),
                    worktree.updatedAt(),
                    worktree.createdBy().value(),
                    createdByUsername);
        }
    }

    public record AgentConfigDiffFileResponse(
            String path,
            String status,
            boolean staged,
            String patch) {
    }

    public record AgentConfigDiffResponse(List<AgentConfigDiffFileResponse> files) {
    }

    public record AgentConfigOperationResponse(
            String operationId,
            String scope,
            String workspaceId,
            String action,
            String status,
            String currentStep,
            String errorCode,
            String errorMessage,
            String branch,
            String commitHash,
            String traceId,
            Instant createdAt,
            Instant updatedAt) {

        public static AgentConfigOperationResponse from(AgentConfigOperation operation) {
            return new AgentConfigOperationResponse(
                    operation.operationId(),
                    operation.scope().name(),
                    operation.workspaceId() == null ? null : operation.workspaceId().value(),
                    operation.action(),
                    operation.status().name(),
                    operation.currentStep().name(),
                    operation.errorCode(),
                    operation.errorMessage(),
                    operation.branch(),
                    operation.commitHash(),
                    operation.traceId(),
                    operation.createdAt(),
                    operation.updatedAt());
        }
    }
}
