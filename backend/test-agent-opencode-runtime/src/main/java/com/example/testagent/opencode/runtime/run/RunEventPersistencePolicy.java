package com.example.testagent.opencode.runtime.run;

import com.example.testagent.domain.event.RunEventDraft;
import com.example.testagent.domain.event.RunEventType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * opencode 运行事件持久化策略：高频内容投影只走实时通道，入库事件只保留可审计的轻量字段。
 */
@Component
public class RunEventPersistencePolicy {

    private static final Set<RunEventType> TRANSIENT_ONLY_TYPES = Set.of(
            RunEventType.ASSISTANT_MESSAGE_DELTA,
            RunEventType.MESSAGE_UPDATED,
            RunEventType.MESSAGE_REMOVED,
            RunEventType.MESSAGE_PART_UPDATED,
            RunEventType.MESSAGE_PART_REMOVED,
            RunEventType.MESSAGE_PART_DELTA);

    private static final Set<String> TOOL_SUMMARY_KEYS = Set.of(
            "tool",
            "callID",
            "callId",
            "messageID",
            "messageId",
            "partID",
            "partId",
            "sessionID",
            "sessionId",
            "status",
            "title",
            "error",
            "summary",
            "rawType",
            "rawEventId");

    public boolean shouldPersist(RunEventDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        return !TRANSIENT_ONLY_TYPES.contains(draft.type());
    }

    public RunEventDraft sanitizeForPersistence(RunEventDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        Map<String, Object> payload = draft.type() == RunEventType.TOOL_STARTED
                || draft.type() == RunEventType.TOOL_FINISHED
                ? toolSummaryPayload(draft.payload())
                : withoutRawPayload(draft.payload());
        return new RunEventDraft(draft.runId(), draft.type(), draft.traceId(), draft.occurredAt(), payload);
    }

    private Map<String, Object> toolSummaryPayload(Map<String, Object> payload) {
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        payload.forEach((key, value) -> {
            if (TOOL_SUMMARY_KEYS.contains(key) && value != null) {
                sanitized.put(key, value);
            }
        });
        return Map.copyOf(sanitized);
    }

    private Map<String, Object> withoutRawPayload(Map<String, Object> payload) {
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        payload.forEach((key, value) -> {
            if (!"rawPayload".equals(key) && value != null) {
                sanitized.put(key, value);
            }
        });
        return Map.copyOf(sanitized);
    }
}
