package com.enterprise.testagent.domain.modelgateway;

import java.time.LocalDate;
import java.util.Objects;

/** 单次网关请求对每日聚合表的增量，不包含请求或回答内容。 */
public record ModelGatewayUsageDelta(
        LocalDate usageDate,
        String sourceClient,
        String userId,
        String providerId,
        String modelId,
        String endpoint,
        boolean succeeded,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        long durationMillis) {

    public ModelGatewayUsageDelta {
        Objects.requireNonNull(usageDate, "usageDate must not be null");
        requireText(sourceClient, "sourceClient");
        requireText(userId, "userId");
        requireText(providerId, "providerId");
        requireText(modelId, "modelId");
        requireText(endpoint, "endpoint");
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0 || durationMillis < 0) {
            throw new IllegalArgumentException("usage counters must not be negative");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
