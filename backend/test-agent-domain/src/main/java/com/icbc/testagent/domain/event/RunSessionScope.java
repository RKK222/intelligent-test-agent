package com.icbc.testagent.domain.event;

import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 一个 Run 对应的 opencode root session scope，作为 SSE 恢复和历史查询的事实来源。
 */
public record RunSessionScope(
        RunId runId,
        String rootSessionId,
        long scopeVersion,
        String traceId,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> metadata) {

    /**
     * 校验 Run scope 主记录，metadata 仅存业务无关扩展信息。
     */
    public RunSessionScope {
        Objects.requireNonNull(runId, "runId must not be null");
        rootSessionId = DomainValidation.requireText(rootSessionId, "rootSessionId");
        if (scopeVersion <= 0) {
            throw new IllegalArgumentException("scopeVersion must be greater than 0");
        }
        traceId = DomainValidation.requireText(traceId, "traceId");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
