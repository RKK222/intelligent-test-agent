package com.icbc.testagent.domain.analytics;

import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.user.UserId;
import java.util.Optional;

/**
 * AI 消息反馈持久化端口，具体 SQL 由 persistence MyBatis XML 实现。
 */
public interface AiMessageFeedbackRepository {

    AiMessageFeedback save(AiMessageFeedback feedback);

    Optional<AiMessageFeedback> findByUserIdAndMessageId(UserId userId, SessionMessageId messageId);
}
