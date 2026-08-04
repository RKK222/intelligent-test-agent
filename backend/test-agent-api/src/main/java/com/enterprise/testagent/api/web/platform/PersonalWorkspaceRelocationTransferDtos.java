package com.enterprise.testagent.api.web.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

/** 个人工作区搬迁内部控制面 DTO；HTTP 只签发票据，不承载文件字节。 */
final class PersonalWorkspaceRelocationTransferDtos {

    private PersonalWorkspaceRelocationTransferDtos() {
    }

    record TicketRequest(
            @NotBlank String relocationId,
            @NotBlank String sourceLinuxServerId,
            @NotBlank String targetLinuxServerId,
            @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String snapshotSha256,
            @PositiveOrZero long archiveSizeBytes) {
    }

    record TicketResponse(String ticket, Instant expiresAt, String webSocketPath) {
    }

    record TransferResponse(boolean success, String relocationId, String headCommit, String code, String message) {

        static TransferResponse success(String relocationId, String headCommit) {
            return new TransferResponse(true, relocationId, headCommit, null, null);
        }

        static TransferResponse failure(String code, String message) {
            return new TransferResponse(false, null, null, code, message);
        }
    }
}
