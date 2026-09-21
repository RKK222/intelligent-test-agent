package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.workspace.TeamWorkspaceExportShardGateway;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** 团队导出跨节点 shard 的内部控制面协议。 */
final class TeamWorkspaceExportShardDtos {

    private TeamWorkspaceExportShardDtos() {
    }

    record BuildRequest(
            @NotBlank String exportId,
            @NotBlank String exportItemId,
            @NotBlank String sourceLinuxServerId,
            @NotBlank String userId,
            @NotBlank String personalWorkspaceId,
            @NotBlank String versionId,
            @Positive long maxUncompressedBytes,
            @Positive int maxFileCount,
            @NotBlank String coordinatorBaseUrl,
            @NotBlank String receiveTicket) {
    }

    record InspectRequest(
            @NotBlank String exportId,
            @NotBlank String exportItemId,
            @NotBlank String sourceLinuxServerId,
            @NotBlank String userId,
            @NotBlank String personalWorkspaceId,
            @NotBlank String versionId,
            @Positive long maxUncompressedBytes,
            @Positive int maxFileCount) {
    }

    record InspectResponse(int fileCount, long uncompressedBytes) {
    }

    record DeleteRevokedArtifactRequest(@NotBlank String exportId) {
    }

    record BuildResponse(
            int fileCount,
            long uncompressedBytes,
            long archiveBytes,
            String sha256,
            List<TeamWorkspaceExportShardGateway.Excluded> excluded) {
    }

    record ReceiveResponse(boolean success, String code, String message) {
        static ReceiveResponse accepted() {
            return new ReceiveResponse(true, null, null);
        }

        static ReceiveResponse failure(String code, String message) {
            return new ReceiveResponse(false, code, message);
        }
    }
}
