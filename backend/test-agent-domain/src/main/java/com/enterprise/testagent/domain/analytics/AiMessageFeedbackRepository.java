package com.enterprise.testagent.domain.analytics;

import com.enterprise.testagent.domain.session.SessionMessageId;
import com.enterprise.testagent.domain.user.UserId;
import java.util.Optional;

/**
 * AI 消息反馈持久化端口，具体 SQL 由 persistence MyBatis XML 实现。
 */
public interface AiMessageFeedbackRepository {

    AiMessageFeedback save(AiMessageFeedback feedback);

    Optional<AiMessageFeedback> findByUserIdAndMessageId(UserId userId, SessionMessageId messageId);
}
