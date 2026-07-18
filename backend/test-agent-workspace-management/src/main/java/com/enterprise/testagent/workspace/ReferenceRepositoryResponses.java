package com.enterprise.testagent.workspace;

import java.util.List;

/** 引用资产库业务响应模型；API 层只做统一响应 envelope 和鉴权。 */
public final class ReferenceRepositoryResponses {

    private ReferenceRepositoryResponses() {
    }

    public record Status(
            String repositoryId,
            String name,
            String englishName,
            String gitUrl,
            String repositoryPath,
            boolean initialized,
            String branch,
            String targetCommitHash,
            long generation,
            String status,
            String operation,
            int targetServerCount,
            int readyServerCount,
            List<ServerStatus> servers,
            String traceId,
            String message) {

        public Status {
            servers = servers == null ? List.of() : List.copyOf(servers);
        }

        /** 兼容未包含服务器绝对路径字段的旧调用方。 */
        public Status(
                String repositoryId, String name, String englishName, String gitUrl, boolean initialized,
                String branch, String targetCommitHash, long generation, String status, String operation,
                int targetServerCount, int readyServerCount, List<ServerStatus> servers,
                String traceId, String message) {
            this(repositoryId, name, englishName, gitUrl, null, initialized, branch, targetCommitHash, generation,
                    status, operation, targetServerCount, readyServerCount, servers, traceId, message);
        }

        /** 兼容旧调用方构造；历史响应默认按同步操作展示。 */
        public Status(
                String repositoryId, String name, String englishName, String gitUrl, boolean initialized,
                String branch, String targetCommitHash, long generation, String status,
                int targetServerCount, int readyServerCount, List<ServerStatus> servers,
                String traceId, String message) {
            this(repositoryId, name, englishName, gitUrl, null, initialized, branch, targetCommitHash, generation,
                    status, "SYNCHRONIZE", targetServerCount, readyServerCount, servers, traceId, message);
        }
    }

    public record ServerStatus(
            String linuxServerId,
            String status,
            boolean online,
            String currentBranch,
            String currentCommitHash,
            Boolean matchesTarget,
            java.time.Instant verifiedAt,
            java.time.Instant syncedAt,
            String error) {

        /** 兼容旧调用方构造。 */
        public ServerStatus(
                String linuxServerId, String status, String currentBranch, String currentCommitHash, String error) {
            this(linuxServerId, status, false, currentBranch, currentCommitHash, null, null, null, error);
        }
    }

    public record TreeNode(
            String path,
            String name,
            boolean directory,
            long size,
            boolean highlighted,
            boolean selectable) {
    }
}
