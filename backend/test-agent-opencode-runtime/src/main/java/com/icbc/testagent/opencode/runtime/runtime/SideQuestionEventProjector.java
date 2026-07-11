package com.icbc.testagent.opencode.runtime.runtime;

import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 把临时 fork 的 opencode 全局事件投影为宠物旁路问答可安全展示的进度和文本增量。
 */
public final class SideQuestionEventProjector {

    private final String temporarySessionId;
    private final Set<String> assistantMessageIds = new HashSet<>();
    private final Map<String, String> assistantTextPartOwners = new HashMap<>();

    /**
     * 每个临时 fork 使用独立投影器，避免不同旁路请求的 message/part 关联状态串扰。
     */
    public SideQuestionEventProjector(String temporarySessionId) {
        if (temporarySessionId == null || temporarySessionId.isBlank()) {
            throw new IllegalArgumentException("temporarySessionId must not be blank");
        }
        this.temporarySessionId = temporarySessionId.trim();
    }

    /**
     * 投影单个事件；无 session、非当前 fork 或不可展示事件统一返回空列表。
     */
    public synchronized List<RunEventDraft> project(RunEventDraft source) {
        Objects.requireNonNull(source, "source must not be null");
        Object rawEvent = source.payload().get("rawPayload");
        Set<String> actualSessionIds = new HashSet<>();
        collectSessionIds(rawEvent, actualSessionIds);
        // mapper 会在平台 payload 顶层补 scope sessionId；隔离判断只能信任原始事件中的实际 session 标识。
        if (actualSessionIds.isEmpty()
                || actualSessionIds.stream().anyMatch(sessionId -> !temporarySessionId.equals(sessionId))) {
            return List.of();
        }
        return switch (source.type()) {
            case MESSAGE_UPDATED -> registerAssistantMessage(rawEvent);
            case MESSAGE_PART_UPDATED -> registerAssistantTextPart(rawEvent);
            case MESSAGE_PART_DELTA -> projectAssistantTextDelta(source, rawEvent);
            case TOOL_STARTED -> projectToolProgress(source, rawEvent);
            default -> List.of();
        };
    }

    private List<RunEventDraft> registerAssistantMessage(Object rawEvent) {
        Map<?, ?> info = findMap(rawEvent, map -> text(map.get("role")) != null);
        if (info == null) {
            return List.of();
        }
        String messageId = firstText(info, "messageID", "messageId", "id");
        if (messageId != null && "assistant".equals(text(info.get("role")))) {
            assistantMessageIds.add(messageId);
        }
        return List.of();
    }

    private List<RunEventDraft> registerAssistantTextPart(Object rawEvent) {
        Map<?, ?> part = findMap(rawEvent, map -> {
            String type = text(map.get("type"));
            String messageId = directText(map, "messageID", "messageId");
            String partId = directText(map, "partID", "partId", "id");
            return "text".equals(type) && messageId != null && partId != null;
        });
        if (part == null) {
            return List.of();
        }
        String messageId = firstText(part, "messageID", "messageId");
        String partId = firstText(part, "partID", "partId", "id");
        if (assistantMessageIds.contains(messageId) && partId != null && "text".equals(text(part.get("type")))) {
            assistantTextPartOwners.put(partId, messageId);
        }
        return List.of();
    }

    private List<RunEventDraft> projectAssistantTextDelta(RunEventDraft source, Object rawEvent) {
        String partId = firstText(rawEvent, "partID", "partId");
        String ownerMessageId = partId == null ? null : assistantTextPartOwners.get(partId);
        String eventMessageId = firstText(rawEvent, "messageID", "messageId");
        if (ownerMessageId == null || (eventMessageId != null && !ownerMessageId.equals(eventMessageId))) {
            return List.of();
        }
        String delta = firstString(rawEvent, "delta", "text");
        if (delta == null || delta.isEmpty()) {
            return List.of();
        }
        return List.of(projected(source, RunEventType.SIDE_QUESTION_DELTA, Map.of("delta", delta)));
    }

    private List<RunEventDraft> projectToolProgress(RunEventDraft source, Object rawEvent) {
        String toolName = firstText(rawEvent, "toolName", "tool");
        if (toolName == null) {
            return List.of();
        }
        // 进度事件仅保留工具名，不把 command/path/input/output/rawPayload 等敏感参数传给浏览器。
        return List.of(projected(source, RunEventType.SIDE_QUESTION_PROGRESS, Map.of(
                "stage", "tool",
                "toolName", toolName)));
    }

    private RunEventDraft projected(RunEventDraft source, RunEventType type, Map<String, Object> payload) {
        return new RunEventDraft(
                source.runId(),
                type,
                source.traceId(),
                source.occurredAt(),
                payload,
                source.scopeContext());
    }

    private String firstText(Object value, String... keys) {
        if (value instanceof Map<?, ?> map) {
            for (String key : keys) {
                String direct = text(map.get(key));
                if (direct != null) {
                    return direct;
                }
            }
            for (Object nested : map.values()) {
                String candidate = firstText(nested, keys);
                if (candidate != null) {
                    return candidate;
                }
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) {
                String candidate = firstText(nested, keys);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private String firstString(Object value, String... keys) {
        if (value instanceof Map<?, ?> map) {
            for (String key : keys) {
                Object direct = map.get(key);
                if (direct instanceof String text) {
                    return text;
                }
            }
            for (Object nested : map.values()) {
                String candidate = firstString(nested, keys);
                if (candidate != null) {
                    return candidate;
                }
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) {
                String candidate = firstString(nested, keys);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private void collectSessionIds(Object value, Set<String> sessionIds) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (("sessionID".equals(key) || "sessionId".equals(key)) && entry.getValue() instanceof String id) {
                    String normalized = id.trim();
                    if (!normalized.isEmpty()) {
                        sessionIds.add(normalized);
                    }
                } else {
                    collectSessionIds(entry.getValue(), sessionIds);
                }
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) {
                collectSessionIds(nested, sessionIds);
            }
        }
    }

    private Map<?, ?> findMap(Object value, Predicate<Map<?, ?>> predicate) {
        if (value instanceof Map<?, ?> map) {
            if (predicate.test(map)) {
                return map;
            }
            for (Object nested : map.values()) {
                Map<?, ?> candidate = findMap(nested, predicate);
                if (candidate != null) {
                    return candidate;
                }
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) {
                Map<?, ?> candidate = findMap(nested, predicate);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private String text(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String directText(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            String candidate = text(map.get(key));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }
}
