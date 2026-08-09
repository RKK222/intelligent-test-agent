package com.enterprise.testagent.domain.memory;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/** `mfg_` 短期授权的可信摘要；Redis 只保存 Token SHA-256 寻址后的本对象。 */
public record MemoryModelGrantPayload(
        String grantId,
        String userId,
        String unifiedAuthId,
        String runId,
        String modelId,
        Instant issuedAt,
        Instant expiresAt) {

    public MemoryModelGrantPayload {
        grantId = DomainValidation.requireText(grantId, "grantId");
        userId = DomainValidation.requireText(userId, "userId");
        unifiedAuthId = DomainValidation.requireText(unifiedAuthId, "unifiedAuthId");
        runId = DomainValidation.requireText(runId, "runId");
        modelId = DomainValidation.requireText(modelId, "modelId");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }
}
