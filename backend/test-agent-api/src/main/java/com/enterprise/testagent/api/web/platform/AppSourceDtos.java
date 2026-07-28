package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceStepStatus;
import com.enterprise.testagent.common.git.GitRemoteService;
import com.enterprise.testagent.workspace.AppSourceApplicationService;
import java.time.Instant;
import java.util.List;

/**
 * 应用源码 REST 与进度 WebSocket 共用的低敏 DTO。
 *
 * <p>DTO 只暴露逻辑身份、固定提交和安全摘要，不返回物理路径、凭据或原始 Git 错误。
 */
final class AppSourceDtos {

    private AppSourceDtos() {
    }

    record MaterializationRequest(
            String operationId,
            Long expectedGeneration,
            String branch,
            String expectedTreeCommit,
            List<SelectedPathRequest> selectedPaths,
            AppSourcePurpose purpose,
            int retentionHours,
            Boolean confirmReplace) {
    }

    record SelectedPathRequest(String path, AppSourcePathType type) {
    }

    record RetryRequest(String operationId, long expectedGeneration) {
    }

    record OpenRequest(long generation) {
    }

    record TicketResponse(String ticket, Instant expiresAt, String webSocketUrl) {
    }

    record TreeNodeResponse(
            String name,
            String path,
            String type,
            List<TreeNodeResponse> children) {
    }

    record RepositoryResponse(
            String repositoryId,
            String name,
            String englishName,
            AppSourceApplicationService.DownloadState downloadState,
            Long generation,
            AppSourcePurpose purpose,
            String ownerUserId,
            String ownerName,
            String ownerUnifiedAuthId,
            String branch,
            String targetCommit,
            List<SelectedPathResponse> selectedPaths,
            Instant expiresAt,
            boolean occupied,
            boolean openable,
            boolean manageable,
            String unavailableReason,
            OperationResponse latestOperation,
            List<ServerResponse> serverSummaries) {
    }

    record SelectedPathResponse(String path, AppSourcePathType type) {
    }

    record OperationResponse(
            String operationId,
            String appId,
            String repositoryId,
            Long sourceGeneration,
            long targetGeneration,
            AppSourceOperationType operationType,
            AppSourceOperationStatus status,
            AppSourcePurpose purpose,
            String branch,
            String targetCommit,
            List<SelectedPathResponse> selectedPaths,
            Instant expiresAt,
            String traceId,
            Instant acceptedAt,
            Instant completedAt,
            List<StepResponse> globalSteps,
            List<ServerResponse> serverSummaries) {
    }

    record ServerResponse(
            String linuxServerId,
            AppSourceReplicaStatus replicaStatus,
            int attemptCount,
            String safeErrorCode,
            String safeErrorMessage,
            String targetCommit,
            List<StepResponse> steps) {
    }

    record StepResponse(
            String stepCode,
            int sequence,
            AppSourceStepStatus status,
            String safeSummary,
            Instant startedAt,
            Instant completedAt,
            Long elapsedMillis,
            Instant updatedAt) {
    }

    record OpenResponse(
            String appId,
            String repositoryId,
            long generation,
            AppSourcePurpose purpose,
            String workspaceId,
            String linuxServerId,
            Instant expiresAt) {
    }

    static RepositoryResponse repository(AppSourceApplicationService.RepositorySummary source) {
        return new RepositoryResponse(
                source.repositoryId(),
                source.name(),
                source.englishName(),
                source.downloadState(),
                source.generation(),
                source.purpose(),
                source.ownerUserId() == null ? null : source.ownerUserId().value(),
                source.ownerName(),
                source.ownerUnifiedAuthId(),
                source.branch(),
                source.targetCommit(),
                selectedPaths(source.selectedPaths()),
                source.expiresAt(),
                source.occupied(),
                source.openable(),
                source.manageable(),
                source.unavailableReason(),
                source.latestOperation() == null ? null : operation(source.latestOperation()),
                servers(source.serverSummaries()));
    }

    static OperationResponse operation(AppSourceApplicationService.OperationSnapshot source) {
        return new OperationResponse(
                source.operationId(),
                source.appId(),
                source.repositoryId(),
                source.sourceGeneration(),
                source.targetGeneration(),
                source.operationType(),
                source.status(),
                source.purpose(),
                source.branch(),
                source.targetCommit(),
                selectedPaths(source.selectedPaths()),
                source.expiresAt(),
                source.traceId(),
                source.acceptedAt(),
                source.completedAt(),
                steps(source.globalSteps()),
                servers(source.serverSummaries()));
    }

    static OpenResponse open(AppSourceApplicationService.OpenResult source) {
        return new OpenResponse(
                source.appId(),
                source.repositoryId(),
                source.generation(),
                source.purpose(),
                source.workspaceId(),
                source.linuxServerId(),
                source.expiresAt());
    }

    static TreeNodeResponse treeNode(GitRemoteService.RemoteTreeNode source) {
        return new TreeNodeResponse(
                source.name(),
                source.path(),
                source.type(),
                source.children().stream().map(AppSourceDtos::treeNode).toList());
    }

    private static List<SelectedPathResponse> selectedPaths(
            List<com.enterprise.testagent.domain.appsource.AppSourceSelectedPath> source) {
        return source.stream()
                .map(path -> new SelectedPathResponse(path.path(), path.pathType()))
                .toList();
    }

    private static List<ServerResponse> servers(List<AppSourceApplicationService.ServerSummary> source) {
        return source.stream()
                .map(server -> new ServerResponse(
                        server.linuxServerId(),
                        server.replicaStatus(),
                        server.attemptCount(),
                        server.safeErrorCode(),
                        server.safeErrorMessage(),
                        server.targetCommit(),
                        steps(server.steps())))
                .toList();
    }

    private static List<StepResponse> steps(List<AppSourceApplicationService.StepSummary> source) {
        return source.stream()
                .map(step -> new StepResponse(
                        step.stepCode(),
                        step.sequence(),
                        step.status(),
                        step.safeSummary(),
                        step.startedAt(),
                        step.completedAt(),
                        step.elapsedMillis(),
                        step.updatedAt()))
                .toList();
    }
}
