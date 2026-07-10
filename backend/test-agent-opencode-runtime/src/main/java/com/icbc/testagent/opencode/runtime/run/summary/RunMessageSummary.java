package com.icbc.testagent.opencode.runtime.run.summary;

import com.icbc.testagent.domain.session.SessionMessageRole;
import java.util.Objects;

/**
 * 一条可持久化的 Run 消息摘要，携带关系型历史读取所需的稳定元数据。
 */
public record RunMessageSummary(
        SessionMessageRole role,
        String content,
        RunSummaryContentKind contentKind,
        RunSummaryStatus summaryStatus,
        int summaryVersion) {

    /**
     * 摘要 record 不接受空内容或非正版本，避免下游把不完整投影写入历史表。
     */
    public RunMessageSummary {
        role = Objects.requireNonNull(role, "role must not be null");
        content = Objects.requireNonNull(content, "content must not be null");
        contentKind = Objects.requireNonNull(contentKind, "contentKind must not be null");
        summaryStatus = Objects.requireNonNull(summaryStatus, "summaryStatus must not be null");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (summaryVersion <= 0) {
            throw new IllegalArgumentException("summaryVersion must be positive");
        }
    }
}
