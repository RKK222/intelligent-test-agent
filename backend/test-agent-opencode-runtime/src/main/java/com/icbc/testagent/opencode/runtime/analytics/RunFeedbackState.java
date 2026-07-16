package com.icbc.testagent.opencode.runtime.analytics;

import com.icbc.testagent.domain.analytics.AiRunFeedback;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.SessionId;
import java.util.Optional;

/** 历史对话批量读取的 Run 状态与当前用户评价。 */
public record RunFeedbackState(
        RunId runId,
        SessionId sessionId,
        RunStatus status,
        Optional<AiRunFeedback> feedback) {
}
