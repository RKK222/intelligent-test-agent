package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import java.util.Objects;

/**
 * 会话上下文签发租约，固定权威读取开始前的失效代次，防止旧快照在失效后迟到写入。
 */
public record ConversationContextIssueLease(
        UserId userId,
        SessionId sessionId,
        long issueGeneration,
        long userSessionGeneration,
        long userGeneration,
        long sessionGeneration,
        long contextGeneration) {

    public ConversationContextIssueLease {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (issueGeneration < 0
                || userSessionGeneration < 0
                || userGeneration < 0
                || sessionGeneration < 0
                || contextGeneration < 0) {
            throw new IllegalArgumentException("context issue generations must not be negative");
        }
    }

    /**
     * 兼容阶段一早期测试构造方式；未发生用户级或全局失效时两项代次均为零。
     */
    public ConversationContextIssueLease(
            UserId userId,
            SessionId sessionId,
            long issueGeneration,
            long userSessionGeneration,
            long sessionGeneration) {
        this(userId, sessionId, issueGeneration, userSessionGeneration, 0, sessionGeneration, 0);
    }

    /**
     * 租约只能保存同一用户与会话的上下文，禁止跨资源复用。
     */
    public boolean matches(ConversationRunContext context) {
        return context != null
                && userId.equals(context.userId())
                && sessionId.equals(context.sessionId());
    }
}
