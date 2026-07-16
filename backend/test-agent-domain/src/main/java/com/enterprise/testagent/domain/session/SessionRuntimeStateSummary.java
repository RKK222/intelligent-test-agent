package com.enterprise.testagent.domain.session;

import java.time.Instant;
import java.util.List;

/**
 * 用户级会话运行态摘要，供历史入口 badge 和历史列表状态展示使用。
 */
public record SessionRuntimeStateSummary(
        int runningCount,
        int questionCount,
        List<SessionRuntimeState> sessions,
        Instant generatedAt) {

    public SessionRuntimeStateSummary {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
    }
}
