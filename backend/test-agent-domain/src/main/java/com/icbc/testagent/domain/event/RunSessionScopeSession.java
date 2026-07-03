package com.icbc.testagent.domain.event;

import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 当前 Run scope 内的 root 或 child session 绑定，child 只在本 Run 启动后发现或可绑定任务 part 时纳入。
 */
public record RunSessionScopeSession(
        RunId runId,
        String sessionId,
        String rootSessionId,
        String parentSessionId,
        boolean childSession,
        String discoverySource,
        String taskMessageId,
        String taskPartId,
        String taskCallId,
        String traceId,
        Instant discoveredAt,
        Instant updatedAt,
        Map<String, Object> metadata) {

    /**
     * 校验 session scope 行，允许 task 绑定字段为空以支持 session.created/bootstrap 来源。
     */
    public RunSessionScopeSession {
        Objects.requireNonNull(runId, "runId must not be null");
        sessionId = DomainValidation.requireText(sessionId, "sessionId");
        rootSessionId = DomainValidation.requireText(rootSessionId, "rootSessionId");
        parentSessionId = optionalText(parentSessionId);
        discoverySource = DomainValidation.requireText(discoverySource, "discoverySource");
        taskMessageId = optionalText(taskMessageId);
        taskPartId = optionalText(taskPartId);
        taskCallId = optionalText(taskCallId);
        traceId = DomainValidation.requireText(traceId, "traceId");
        discoveredAt = DomainValidation.requireInstant(discoveredAt, "discoveredAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
