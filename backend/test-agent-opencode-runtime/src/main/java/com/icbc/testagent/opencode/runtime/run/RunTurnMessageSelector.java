package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.agent.runtime.AgentSessionMessage;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 按 OpenCode user message 的直接父子关系筛选单个 Run 的 root 消息，禁止把同一 Session 的其它轮次投影到当前 Run。
 */
final class RunTurnMessageSelector {

    private static final Duration LEGACY_TIME_SKEW = Duration.ofSeconds(5);

    private RunTurnMessageSelector() {
    }

    /**
     * 优先按稳定 dispatch user 锚点选择；只有旧 Run 确实没有锚点时，才允许在时间窗内唯一匹配。
     */
    static Selection select(
            List<AgentSessionMessage> messages,
            String dispatchMessageId,
            Instant runCreatedAt,
            Instant runUpdatedAt) {
        List<AgentSessionMessage> source = messages == null ? List.of() : List.copyOf(messages);
        String userMessageId = text(dispatchMessageId);
        if (userMessageId != null) {
            return selectOwnedMessages(source, userMessageId);
        }
        if (runCreatedAt == null || runUpdatedAt == null || runUpdatedAt.isBefore(runCreatedAt)) {
            return Selection.unresolved();
        }

        Instant lowerBound = runCreatedAt.minus(LEGACY_TIME_SKEW);
        Instant upperBound = runUpdatedAt.plus(LEGACY_TIME_SKEW);
        List<String> candidates = source.stream()
                .filter(message -> isUser(message.message()))
                .filter(message -> isInside(createdAt(message.message()), lowerBound, upperBound))
                .map(message -> messageId(message.message()))
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (candidates.size() != 1) {
            return Selection.unresolved();
        }
        return selectOwnedMessages(source, candidates.get(0));
    }

    /** 明确 dispatch user 尚未出现在远端快照时返回空，不能猜测最后一轮。 */
    static List<AgentSessionMessage> selectByDispatchMessageId(
            List<AgentSessionMessage> messages,
            String dispatchMessageId) {
        return select(messages, dispatchMessageId, null, null).messages();
    }

    private static Selection selectOwnedMessages(List<AgentSessionMessage> source, String dispatchMessageId) {
        boolean dispatchUserPresent = source.stream()
                .anyMatch(message -> isUser(message.message())
                        && dispatchMessageId.equals(messageId(message.message())));
        if (!dispatchUserPresent) {
            return Selection.unresolved();
        }
        List<AgentSessionMessage> selected = new ArrayList<>();
        for (AgentSessionMessage message : source) {
            Map<String, Object> info = message.message();
            if ((isUser(info) && dispatchMessageId.equals(messageId(info)))
                    || (isAssistant(info) && dispatchMessageId.equals(parentMessageId(info)))) {
                selected.add(message);
            }
        }
        return Selection.resolved(dispatchMessageId, selected);
    }

    private static boolean isInside(Instant value, Instant lowerBound, Instant upperBound) {
        return value != null && !value.isBefore(lowerBound) && !value.isAfter(upperBound);
    }

    private static Instant createdAt(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        Object time = message.get("time");
        if (time instanceof Map<?, ?> timeMap) {
            Instant parsed = instant(timeMap.get("created"));
            if (parsed != null) {
                return parsed;
            }
        }
        return instant(firstValue(message, "createdAt", "created_at", "created"));
    }

    private static Instant instant(Object value) {
        if (value instanceof Number number) {
            long epoch = number.longValue();
            return Math.abs(epoch) > 10_000_000_000L
                    ? Instant.ofEpochMilli(epoch)
                    : Instant.ofEpochSecond(epoch);
        }
        String raw = text(value);
        if (raw == null) {
            return null;
        }
        try {
            long epoch = Long.parseLong(raw);
            return Math.abs(epoch) > 10_000_000_000L
                    ? Instant.ofEpochMilli(epoch)
                    : Instant.ofEpochSecond(epoch);
        } catch (NumberFormatException ignored) {
            try {
                return Instant.parse(raw);
            } catch (DateTimeException invalidTimestamp) {
                return null;
            }
        }
    }

    private static boolean isUser(Map<String, Object> message) {
        return "user".equals(role(message));
    }

    private static boolean isAssistant(Map<String, Object> message) {
        return "assistant".equals(role(message));
    }

    private static String role(Map<String, Object> message) {
        String role = text(message == null ? null : message.get("role"));
        if (role == null) {
            role = text(message == null ? null : message.get("type"));
        }
        return role == null ? null : role.toLowerCase(Locale.ROOT);
    }

    private static String messageId(Map<String, Object> message) {
        return firstText(message, "id", "messageID", "messageId");
    }

    private static String parentMessageId(Map<String, Object> message) {
        return firstText(message, "parentID", "parentId", "parent_id");
    }

    private static String firstText(Map<String, Object> source, String... keys) {
        return text(firstValue(source, keys));
    }

    private static Object firstValue(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    record Selection(boolean resolved, String userMessageId, List<AgentSessionMessage> messages) {

        Selection {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }

        static Selection unresolved() {
            return new Selection(false, null, List.of());
        }

        static Selection resolved(String userMessageId, List<AgentSessionMessage> messages) {
            return new Selection(true, userMessageId, messages);
        }
    }
}
