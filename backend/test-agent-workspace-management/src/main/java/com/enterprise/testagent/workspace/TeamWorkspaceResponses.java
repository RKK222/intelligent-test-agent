package com.enterprise.testagent.workspace;

import java.time.Instant;
import java.util.List;

/** 团队代码视图对 API 层暴露的只读模型；任何响应都不包含服务器物理路径。 */
public final class TeamWorkspaceResponses {

    private TeamWorkspaceResponses() {
    }

    public record ApplicationResponse(
            String appId,
            String appName,
            boolean enabled,
            long currentMemberCount,
            long historicalMemberCount,
            String membershipState) {
    }

    public record WorkspaceTemplateResponse(
            String workspaceId,
            String appId,
            String workspaceName,
            String branch,
            String directoryPath,
            boolean enabled,
            String membershipState) {
    }

    public record WorkspaceVersionResponse(
            String versionId,
            String applicationWorkspaceId,
            String appId,
            String version,
            String branch,
            String status,
            String targetCommitHash,
            Instant updatedAt,
            String membershipState) {
    }

    public record ContributionResponse(
            String userId,
            String unifiedAuthId,
            String username,
            String organization,
            String department,
            String membershipState,
            List<PersonalWorkspaceResponse> personalWorkspaces) {
    }

    public record PersonalWorkspaceResponse(
            String personalWorkspaceId,
            String workspaceId,
            String workspaceName,
            String branch,
            String linuxServerId,
            String baseCommit,
            String status,
            Instant updatedAt) {
    }

    public record GitStatusResponse(
            List<GitDiffFileResponse> files,
            int stagedCount,
            int unstagedCount,
            int untrackedCount) {
    }

    public record GitDiffFileResponse(
            String path,
            String rawStatus,
            String status,
            boolean staged,
            String patch,
            int additions,
            int deletions) {
    }

    public record CommitPageResponse(
            List<CommitResponse> items,
            int offset,
            int limit,
            boolean hasMore,
            boolean attributionConfirmed,
            String attributionMessage) {
    }

    public record CommitResponse(
            String commit,
            List<String> parents,
            String authorName,
            String authorEmail,
            String committerName,
            String committerEmail,
            Instant committedAt,
            String subject,
            String contributionType) {
    }

    public record CommitDetailResponse(CommitResponse commit, List<CommitFileResponse> files) {
    }

    public record CommitFileResponse(String status, String oldPath, String path) {
    }

    public record CommitDiffResponse(String commit, String path, String patch) {
    }

    public record ExportResponse(
            String exportId,
            String status,
            int totalItems,
            int completedItems,
            int succeededItems,
            int failedItems,
            int fileCount,
            long uncompressedBytes,
            Long archiveBytes,
            String archiveSha256,
            String artifactFileName,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant completedAt,
            Instant expiresAt,
            List<ExportItemResponse> items) {
    }

    public record ExportItemResponse(
            String exportItemId,
            String userId,
            String personalWorkspaceId,
            String sourceLinuxServerId,
            String status,
            int fileCount,
            long uncompressedBytes,
            String errorCode,
            String errorMessage) {
    }
}
