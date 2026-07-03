package com.icbc.testagent.domain.event;

import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Run 事件所属 opencode session scope。scope 独立于 payload 持久化，payload 只保留兼容字段给旧前端消费。
 */
public record RunEventScopeContext(
        RunId runId,
        String rootSessionId,
        String sessionId,
        String parentSessionId,
        boolean childSession,
        String taskMessageId,
        String taskPartId,
        String taskCallId,
        long scopeVersion,
        boolean discoveredDuringRun) {

    /**
     * 校验 root/current session 关系，允许任务 part 绑定字段为空。
     */
    public RunEventScopeContext {
        Objects.requireNonNull(runId, "runId must not be null");
        rootSessionId = DomainValidation.requireText(rootSessionId, "rootSessionId");
        sessionId = DomainValidation.requireText(sessionId, "sessionId");
        parentSessionId = normalizeNullableText(parentSessionId);
        taskMessageId = normalizeNullableText(taskMessageId);
        taskPartId = normalizeNullableText(taskPartId);
        taskCallId = normalizeNullableText(taskCallId);
        if (scopeVersion <= 0) {
            throw new IllegalArgumentException("scopeVersion must be greater than 0");
        }
    }

    /**
     * 创建仅包含 root session 的默认 scope，适用于兼容入口和旧单 session 调用。
     */
    public static RunEventScopeContext root(RunId runId, String rootSessionId) {
        return new RunEventScopeContext(
                runId,
                rootSessionId,
                rootSessionId,
                null,
                false,
                null,
                null,
                null,
                1L,
                true);
    }

    /**
     * 复制到 payload 的兼容 metadata，字段名保持 camelCase，避免改变现有 JSON 结构风格。
     */
    public Map<String, Object> toPayloadMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("rootSessionId", rootSessionId);
        metadata.put("sessionId", sessionId);
        putIfPresent(metadata, "parentSessionId", parentSessionId);
        metadata.put("isChildSession", childSession);
        putIfPresent(metadata, "taskMessageId", taskMessageId);
        putIfPresent(metadata, "taskPartId", taskPartId);
        putIfPresent(metadata, "taskCallId", taskCallId);
        metadata.put("scopeVersion", scopeVersion);
        metadata.put("discoveredDuringRun", discoveredDuringRun);
        return metadata;
    }

    private static String normalizeNullableText(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}
