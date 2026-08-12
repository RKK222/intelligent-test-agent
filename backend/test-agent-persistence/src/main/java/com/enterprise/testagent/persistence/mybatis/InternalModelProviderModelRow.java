package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** internal_model_provider_models 的 MyBatis 行映射。 */
public record InternalModelProviderModelRow(
        String providerId,
        String modelId,
        String upstreamModelId,
        String displayName,
        Long contextLimit,
        Integer embeddingDimension,
        boolean enabled,
        boolean capabilityChat,
        boolean capabilityTools,
        boolean capabilityVision,
        boolean capabilityReasoning,
        boolean capabilityEmbedding,
        boolean capabilityRerank,
        boolean capabilityImage,
        boolean capabilitySpeech,
        boolean capabilityTranscription,
        Instant createdAt,
        Instant updatedAt) {
}
