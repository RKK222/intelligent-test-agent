package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** 内部供应商的公开模型 ID、上游 ID 和已探测能力。 */
public record InternalModelProviderModel(
        String providerId,
        String modelId,
        String upstreamModelId,
        String displayName,
        Long contextLimit,
        Integer embeddingDimension,
        boolean enabled,
        Set<ModelCapability> declaredCapabilities,
        Set<ModelCapability> probedCapabilities,
        Instant lastProbedAt,
        Instant createdAt,
        Instant updatedAt) {

    public InternalModelProviderModel {
        providerId = DomainValidation.requireText(providerId, "providerId");
        modelId = DomainValidation.requireText(modelId, "modelId");
        upstreamModelId = DomainValidation.requireText(upstreamModelId, "upstreamModelId");
        displayName = DomainValidation.requireText(displayName, "displayName");
        if (contextLimit != null && contextLimit <= 0) {
            throw new IllegalArgumentException("contextLimit must be positive");
        }
        if (embeddingDimension != null && embeddingDimension <= 0) {
            throw new IllegalArgumentException("embeddingDimension must be positive");
        }
        declaredCapabilities = immutableCapabilities(declaredCapabilities);
        probedCapabilities = immutableCapabilities(probedCapabilities);
        if (!declaredCapabilities.containsAll(probedCapabilities)) {
            throw new IllegalArgumentException("probed capabilities must be declared");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public InternalModelProviderModel(
            String providerId,
            String modelId,
            String upstreamModelId,
            String displayName,
            Long contextLimit,
            boolean enabled,
            Set<ModelCapability> declaredCapabilities,
            Set<ModelCapability> probedCapabilities,
            Instant lastProbedAt,
            Instant createdAt,
            Instant updatedAt) {
        this(providerId, modelId, upstreamModelId, displayName, contextLimit, null, enabled,
                declaredCapabilities, probedCapabilities, lastProbedAt, createdAt, updatedAt);
    }

    /** 只有启用且至少一项已探测能力的模型才可进入网关目录。 */
    public boolean routable() {
        return enabled && !probedCapabilities.isEmpty();
    }

    private static Set<ModelCapability> immutableCapabilities(Set<ModelCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }
}
