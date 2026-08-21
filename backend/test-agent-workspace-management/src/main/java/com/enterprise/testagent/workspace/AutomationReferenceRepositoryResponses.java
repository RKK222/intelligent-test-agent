package com.enterprise.testagent.workspace;

import java.time.Instant;
import java.util.List;

/** 应用自动化引用响应；只返回逻辑路径和 Git 指针，不返回服务器物理目录。 */
public final class AutomationReferenceRepositoryResponses {

    private AutomationReferenceRepositoryResponses() {
    }

    public record Status(
            String appId,
            String repositoryId,
            String name,
            String englishName,
            String gitUrl,
            String status,
            String operation,
            long lockVersion,
            Long activeGeneration,
            Long pendingGeneration,
            Configuration currentConfiguration,
            Configuration pendingConfiguration,
            int targetServerCount,
            int readyServerCount,
            List<ServerStatus> servers,
            String traceId,
            String message) {

        public Status {
            servers = servers == null ? List.of() : List.copyOf(servers);
        }
    }

    public record Configuration(
            long generation,
            String branch,
            String directoryPath,
            String description,
            boolean merge,
            String targetCommitHash,
            String alias,
            String logicalPath,
            String directoryName,
            Instant activatedAt,
            String status) {
    }

    public record ServerStatus(
            String linuxServerId,
            String status,
            boolean online,
            String currentBranch,
            String currentCommitHash,
            Boolean matchesTarget,
            Instant verifiedAt,
            Instant syncedAt,
            String error) {
    }
}
