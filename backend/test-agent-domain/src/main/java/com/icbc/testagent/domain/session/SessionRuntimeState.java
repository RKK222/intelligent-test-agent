package com.icbc.testagent.domain.session;

import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunStatus;
import java.time.Instant;

/**
 * 当前用户历史会话中的运行中 Run 状态，包含由 question 事件派生的待关注标记。
 */
public record SessionRuntimeState(
        SessionId sessionId,
        RunId runId,
        RunStatus runStatus,
        SessionRuntimeAttention attention,
        String attentionEventId,
        Instant attentionAt,
        Instant updatedAt) {
}
