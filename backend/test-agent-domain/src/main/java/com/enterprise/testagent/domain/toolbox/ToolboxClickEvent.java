package com.enterprise.testagent.domain.toolbox;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** 工具点击明细，点击时间只允许由服务端生成。 */
public record ToolboxClickEvent(
        String eventId,
        String toolId,
        String source,
        UserId userId,
        String traceId,
        Instant clickedAt,
        boolean counted) {

    /** 校验点击明细的稳定身份和审计字段。 */
    public ToolboxClickEvent {
        requireText(eventId, "eventId");
        requireText(toolId, "toolId");
        requireText(source, "source");
        Objects.requireNonNull(userId, "userId must not be null");
        requireText(traceId, "traceId");
        Objects.requireNonNull(clickedAt, "clickedAt must not be null");
    }

    /** 返回只改变计数标记的新明细，保留原始服务端点击时间。 */
    public ToolboxClickEvent withCounted(boolean nextCounted) {
        return new ToolboxClickEvent(eventId, toolId, source, userId, traceId, clickedAt, nextCounted);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
