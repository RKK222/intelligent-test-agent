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
            boolean initialized,
            String branch,
            String targetCommitHash,
            long generation,
            String status,
            int targetServerCount,
            int readyServerCount,
            List<ServerStatus> servers,
            String traceId,
            String message) {

        public Status {
            servers = servers == null ? List.of() : List.copyOf(servers);
        }
    }

    public record ServerStatus(
            String linuxServerId,
            String status,
            String currentBranch,
            String currentCommitHash,
            String error) {
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
