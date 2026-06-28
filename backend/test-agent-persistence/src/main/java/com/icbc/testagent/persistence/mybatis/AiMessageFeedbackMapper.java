package com.icbc.testagent.persistence.mybatis;

import org.apache.ibatis.annotations.Param;

/**
 * AI 消息反馈 MyBatis mapper；SQL 必须维护在 XML 中。
 */
public interface AiMessageFeedbackMapper {

    AiMessageFeedbackRow findByUserIdAndMessageId(
            @Param("userId") String userId,
            @Param("messageId") String messageId);

    AiMessageFeedbackRow findByFeedbackId(@Param("feedbackId") String feedbackId);

    int insert(AiMessageFeedbackRow row);

    int update(AiMessageFeedbackRow row);
}
