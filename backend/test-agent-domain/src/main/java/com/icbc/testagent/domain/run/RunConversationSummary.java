package com.icbc.testagent.domain.run;

import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * PostgreSQL 中单条终态对话摘要；仅允许 USER/ASSISTANT 文本，不表达 parts、reasoning 或工具详情。
 */
public record RunConversationSummary(
        SessionMessageId messageId,
        SessionMessageRole role,
        String content,
        String summaryKey,
        int summaryVersion,
        RunSummaryStatus summaryStatus,
        Instant createdAt,
        String remoteMessageId) {

    public RunConversationSummary {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        if (role != SessionMessageRole.USER && role != SessionMessageRole.ASSISTANT) {
            throw new IllegalArgumentException("summary role must be USER or ASSISTANT");
        }
        content = DomainValidation.requireText(content, "content");
        int maxCodePoints = role == SessionMessageRole.USER ? 512 : 2_000;
        if (content.codePointCount(0, content.length()) > maxCodePoints) {
            throw new IllegalArgumentException("summary content exceeds " + maxCodePoints + " Unicode characters");
        }
        summaryKey = DomainValidation.requireText(summaryKey, "summaryKey");
        if (summaryKey.length() > 255) {
            throw new IllegalArgumentException("summaryKey must not exceed 255 characters");
        }
        if (summaryVersion < 1) {
            throw new IllegalArgumentException("summaryVersion must be positive");
        }
        Objects.requireNonNull(summaryStatus, "summaryStatus must not be null");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        remoteMessageId = optionalText(remoteMessageId, "remoteMessageId");
    }

    private static String optionalText(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        return DomainValidation.requireText(value, fieldName);
    }
}
