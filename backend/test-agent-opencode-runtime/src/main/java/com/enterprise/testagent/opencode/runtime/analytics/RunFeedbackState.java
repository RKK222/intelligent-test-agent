package com.enterprise.testagent.opencode.runtime.analytics;

import com.enterprise.testagent.domain.analytics.AiRunFeedback;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import java.util.Optional;

/** 历史对话批量读取的 Run 状态与当前用户评价。 */
public record RunFeedbackState(
        RunId runId,
        SessionId sessionId,
        RunStatus status,
        Optional<AiRunFeedback> feedback) {
}
