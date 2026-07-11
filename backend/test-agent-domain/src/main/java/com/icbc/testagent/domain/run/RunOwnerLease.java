package com.icbc.testagent.domain.run;

import java.time.Instant;
import java.util.Objects;

/**
 * Redis Run owner 租约。fencing token 在同一 Run 内单调递增，下游副作用必须拒绝旧 token。
 */
public record RunOwnerLease(
        RunId runId,
        String ownerBackendProcessId,
        long fencingToken,
        Instant expiresAt) {

    public RunOwnerLease {
        Objects.requireNonNull(runId, "runId must not be null");
        if (ownerBackendProcessId == null || ownerBackendProcessId.isBlank()) {
            throw new IllegalArgumentException("ownerBackendProcessId must not be blank");
        }
        ownerBackendProcessId = ownerBackendProcessId.trim();
        if (fencingToken < 1L) {
            throw new IllegalArgumentException("fencingToken must be greater than 0");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
