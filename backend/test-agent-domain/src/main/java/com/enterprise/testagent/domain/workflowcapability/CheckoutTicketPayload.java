package com.enterprise.testagent.domain.workflowcapability;

import java.time.Instant;
import java.util.Objects;

/** checkout ticket只保存范围和公钥绑定，不保存SSH明文。 */
public record CheckoutTicketPayload(
        String userId,
        String sessionDigest,
        String repositoryId,
        String taskId,
        String runId,
        String runnerId,
        String runnerPublicKey,
        String targetBranch,
        String baselineBranch,
        Instant expiresAt) {

    public CheckoutTicketPayload {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(sessionDigest);
        Objects.requireNonNull(repositoryId);
        Objects.requireNonNull(taskId);
        Objects.requireNonNull(runId);
        Objects.requireNonNull(runnerId);
        Objects.requireNonNull(runnerPublicKey);
        Objects.requireNonNull(targetBranch);
        Objects.requireNonNull(expiresAt);
    }
}
