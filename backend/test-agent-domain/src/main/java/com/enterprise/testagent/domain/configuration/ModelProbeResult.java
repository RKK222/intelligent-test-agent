package com.enterprise.testagent.domain.configuration;

import java.time.Instant;
import java.util.Objects;

/** 单个模型能力的最近探测结果，不持久化上游原始错误。 */
public record ModelProbeResult(
        String providerId,
        String modelId,
        ModelCapability capability,
        boolean succeeded,
        Instant probedAt) {

    public ModelProbeResult {
        if (providerId == null || providerId.isBlank() || modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("probe identity must not be blank");
        }
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(probedAt, "probedAt must not be null");
    }
}
