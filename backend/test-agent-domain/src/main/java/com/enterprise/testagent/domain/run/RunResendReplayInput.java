package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Redis 中短期保存的精确重放输入。
 *
 * <p>该对象含 prompt/附件引用，禁止写入 PostgreSQL、日志、事件或 API 响应。
 */
public record RunResendReplayInput(
        RunId replacementRunId,
        String prompt,
        List<Map<String, Object>> parts,
        String messageId,
        String agent,
        String modelProviderId,
        String modelId,
        String variant,
        Instant createdAt,
        Instant expiresAt) {

    public RunResendReplayInput {
        Objects.requireNonNull(replacementRunId, "replacementRunId must not be null");
        prompt = DomainValidation.requireText(prompt, "prompt");
        parts = parts == null ? List.of() : parts.stream().map(Map::copyOf).toList();
        messageId = DomainValidation.requireText(messageId, "messageId");
        agent = optional(agent);
        modelProviderId = optional(modelProviderId);
        modelId = optional(modelId);
        variant = optional(variant);
        if ((modelProviderId == null) != (modelId == null)) {
            throw new IllegalArgumentException("modelProviderId and modelId must be provided together");
        }
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        expiresAt = DomainValidation.requireInstant(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
